package codes.simon.expect.core

import scala.util.matching.Regex
import scala.util.matching.Regex.Match

object EndOfFile
object Timeout

trait When[R] {
  def actions: Seq[Action[this.type]]

  /**
   * @param output the String to match against.
   * @return whether this When matches against `output`.
   */
  def matches(output: String): Boolean
  /**
   * @param output the output to trim.
   * @return the text in `output` that remains after removing all the text up to the first occurrence of the text
   *         matched by this When and then removing the text matched by this When.
   */
  def trimToMatchedText(output: String): String

  /**
   * Executes all the actions of this When.
   */
  def execute(process: RichProcess, lastResult: (String, R, ExecutionAction)): (String, R, ExecutionAction) = {
    val (output, lastValue, _) = lastResult
    val trimmedOutput = trimToMatchedText(output)
    var result = lastValue
    actions foreach {
      case Send(text) =>
        process.print(text)
      case Returning(r) =>
        result = r()
      case ReturningExpect(r) =>
        //Preemptive exit to guarantee anything after this action does not get executed
        return (trimmedOutput, result, ChangeToNewExpect(r()))
      case Exit =>
        //Preemptive exit to guarantee anything after this action does not get executed
        return (trimmedOutput, result, Terminate)
    }
    (trimmedOutput, result, Continue)
  }

  override def toString: String =
    s"""when {
       |\t${actions.mkString("\n")}
       |}""".stripMargin
}
case class StringWhen[R](pattern: String, actions: Seq[Action[StringWhen[R]]]) extends When[R] {
  def matches(output: String): Boolean = output.contains(pattern)
  def trimToMatchedText(output: String): String = {
    output.substring(output.indexOf(pattern) + pattern.length)
  }
}
case class RegexWhen[R](pattern: Regex, actions: Seq[Action[RegexWhen[R]]]) extends When[R] {
  def matches(output: String): Boolean = pattern.findFirstIn(output).isDefined
  def trimToMatchedText(output: String): String = output.substring(getMatch(output).end(0))

  private def getMatch(output: String): Match = {
    //We have the guarantee that .get will be successful because this method
    //is only invoked if `matches` returned true.
    pattern.findFirstMatchIn(output).get
  }

  override def execute(process: RichProcess, lastResult: (String, R, ExecutionAction)): (String, R, ExecutionAction) = {
    //Would be nice not to duplicate most of this code here.
    val (output, lastValue, _) = lastResult
    val trimmedOutput = trimToMatchedText(output)
    val `match` = getMatch(output)
    var result = lastValue
    actions foreach {
      case Send(text) =>
        process.print(text)
      case SendWithRegex(text) =>
        process.print(text(`match`))
      case Returning(r) =>
        result = r()
      case ReturningWithRegex(r) =>
        result = r(`match`)
      case ReturningExpect(r) =>
        //Preemptive exit to guarantee anything after this action does not get executed
        return (trimmedOutput, result, ChangeToNewExpect(r()))
      case ReturningExpectWithRegex(r) =>
        val expect = r(`match`)
        //Preemptive exit to guarantee anything after this action does not get executed
        return (trimmedOutput, result, ChangeToNewExpect(expect))
      case Exit =>
        //Preemptive exit to guarantee anything after this action does not get executed
        return (trimmedOutput, result, Terminate)
    }
    (trimmedOutput, result, Continue)
  }
}
case class EndOfFileWhen[R](actions: Seq[Action[EndOfFileWhen[R]]]) extends When[R] {
  def matches(output: String): Boolean = false
  def trimToMatchedText(output: String): String = output
}
case class TimeoutWhen[R](actions: Seq[Action[TimeoutWhen[R]]]) extends When[R] {
  def matches(output: String): Boolean = false
  def trimToMatchedText(output: String): String = output
}