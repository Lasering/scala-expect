package work.martins.simon.expect.core

import work.martins.simon.expect.StringUtils._

import scala.util.matching.Regex
import scala.util.matching.Regex.Match

trait When[R] extends {
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

  def toString(pattern: String): String =
    s"""when $pattern {
       |${actions.mkString("\n").indent()}
       |}""".stripMargin

  def structurallyEquals(other: When[R]): Boolean
}
case class StringWhen[R](pattern: String)(val actions: Action[StringWhen[R]]*) extends When[R] {
  def matches(output: String): Boolean = output.contains(pattern)
  def trimToMatchedText(output: String): String = {
    output.substring(output.indexOf(pattern) + pattern.length)
  }

  override def toString: String = toString(escape(pattern))
  override def equals(other: Any): Boolean = other match {
    case that: StringWhen[R] => pattern == that.pattern && actions == that.actions
    case _ => false
  }
  def structurallyEquals(other: When[R]): Boolean = other match {
    case that: StringWhen[R] =>
      pattern == that.pattern &&
      actions.size == that.actions.size && actions.zip(that.actions).forall { case (a, b) => a.structurallyEquals(b) }
    case _ => false
  }
  override def hashCode(): Int = {
    val state = Seq(pattern, actions)
    state.map(_.hashCode()).foldLeft(0)((a, b) => 31 * a + b)
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

  override def toString: String = toString(escape(pattern.regex) + ".r")
  override def equals(other: Any): Boolean = other match {
    case that: RegexWhen[R] => pattern.regex == that.pattern.regex && actions == that.actions
    case _ => false
  }
  def structurallyEquals(other: When[R]): Boolean = other match {
    case that: RegexWhen[R] =>
      pattern.regex == that.pattern.regex &&
      actions.size == that.actions.size && actions.zip(that.actions).forall { case (a, b) => a.structurallyEquals(b) }
    case _ => false
  }
  override def hashCode(): Int = {
    val state = Seq(pattern, actions)
    state.map(_.hashCode()).foldLeft(0)((a, b) => 31 * a + b)
  }
}
case class EndOfFileWhen[R](actions: Action[EndOfFileWhen[R]]*) extends When[R] {
  def matches(output: String): Boolean = false
  def trimToMatchedText(output: String): String = output

  override def toString: String = toString("EndOfFile")
  override def equals(other: Any): Boolean = other match {
    case that: EndOfFileWhen[R] => actions == that.actions
    case _ => false
  }
  def structurallyEquals(other: When[R]): Boolean = other match {
    case that: EndOfFileWhen[R] =>
      actions.size == that.actions.size && actions.zip(that.actions).forall { case (a, b) => a.structurallyEquals(b) }
    case _ => false
  }
  override def hashCode(): Int = actions.hashCode()
}
case class TimeoutWhen[R](actions: Action[TimeoutWhen[R]]*) extends When[R] {
  def matches(output: String): Boolean = false
  def trimToMatchedText(output: String): String = output

  override def toString: String = toString("Timeout")
  override def equals(other: Any): Boolean = other match {
    case that: TimeoutWhen[R] => actions == that.actions
    case _ => false
  }
  def structurallyEquals(other: When[R]): Boolean = other match {
    case that: TimeoutWhen[R] =>
      actions.size == that.actions.size && actions.zip(that.actions).forall { case (a, b) => a.structurallyEquals(b) }
    case _ => false
  }
  override def hashCode(): Int = actions.hashCode()
}