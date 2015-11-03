package work.martins.simon.expect.core

import scala.util.matching.Regex
import scala.util.matching.Regex.Match

object EndOfFile
object Timeout

trait When[R] extends AddBlock {
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
  def execute(process: RichProcess, intermediateResult: IntermediateResult[R]): IntermediateResult[R] = {
    val trimmedOutput = trimToMatchedText(intermediateResult.output)
    var result = intermediateResult.copy[R](output = trimmedOutput)
    actions foreach {
      case Send(text) =>
        process.print(text)
      case Returning(r) =>
        result = result.copy(value = r())
      case ReturningExpect(r) =>
        //Preemptive exit to guarantee anything after this action does not get executed
        return result.copy(executionAction = ChangeToNewExpect(r()))
      case Exit =>
        //Preemptive exit to guarantee anything after this action does not get executed
        return result.copy(executionAction = Terminate)
    }
    result
  }

  override def toString: String =
    s"""when {
       |\t${actions.mkString("\n")}
       |}""".stripMargin
}
case class StringWhen[R](pattern: String)(val actions: Action[StringWhen[R]]*) extends When[R] {
  def matches(output: String): Boolean = output.contains(pattern)
  def trimToMatchedText(output: String): String = {
    output.substring(output.indexOf(pattern) + pattern.length)
  }
}
case class RegexWhen[R](pattern: Regex)(val actions: Action[RegexWhen[R]]*) extends When[R] {
  def matches(output: String): Boolean = pattern.findFirstIn(output).isDefined
  def trimToMatchedText(output: String): String = output.substring(getMatch(output).end(0))

  private def getMatch(output: String): Match = {
    //We have the guarantee that .get will be successful because this method
    //is only invoked if `matches` returned true.
    pattern.findFirstMatchIn(output).get
  }

  override def execute(process: RichProcess, intermediateResult: IntermediateResult[R]): IntermediateResult[R] = {
    //Would be nice not to duplicate most of this code here.
    val trimmedOutput = trimToMatchedText(intermediateResult.output)
    var result = intermediateResult.copy[R](output = trimmedOutput)
    val regexMatch = getMatch(intermediateResult.output)
    actions foreach {
      case Send(text) =>
        process.print(text)
      case SendWithRegex(text) =>
        process.print(text(regexMatch))
      case Returning(r) =>
        result = result.copy(value = r())
      case ReturningWithRegex(r) =>
        result = result.copy(value = r(regexMatch))
      case ReturningExpect(r) =>
        val expect = r()
        //Preemptive exit to guarantee anything after this action does not get executed
        return result.copy(executionAction = ChangeToNewExpect(expect))
      case ReturningExpectWithRegex(r) =>
        val expect = r(regexMatch)
        //Preemptive exit to guarantee anything after this action does not get executed
        return result.copy(executionAction = ChangeToNewExpect(expect))
      case Exit =>
        //Preemptive exit to guarantee anything after this action does not get executed
        return result.copy(executionAction = Terminate)
    }
    result
  }
}
case class EndOfFileWhen[R](actions: Action[EndOfFileWhen[R]]*) extends When[R] {
  def matches(output: String): Boolean = false
  def trimToMatchedText(output: String): String = output
}
case class TimeoutWhen[R](actions: Action[TimeoutWhen[R]]*) extends When[R] {
  def matches(output: String): Boolean = false
  def trimToMatchedText(output: String): String = output
}