package codes.simon.expect.core

import scala.util.matching.Regex
import scala.util.matching.Regex.Match

trait When {
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
   * @param process the underlying process of Expect.
   * @param output the output where this When matched.
   * @tparam R
   * @return A tuple (A, B) where:
   * A. is the `output` trimmed with `trimToMatchedText`.
   * B. is
   *  - None - if this When did not contain any `ReturningAction`
   *  - Some(r) - where r is the value returned by the `ReturningAction`.
   */
  def execute[R](process: RichProcess, output: Option[String]): (Option[String], Option[R]) = {
    val trimmedOutput = output.map(trimToMatchedText)
    var result = Option.empty[R]
    actions foreach {
      case SendAction(text) =>
        process.print(text)
      case ReturningAction(r) =>
        result = Some(r.asInstanceOf[R])
      case ExitAction =>
        process.destroy()
        //Preemptive exit to guarantee anything after the Exit does not get executed
        return (trimmedOutput, result)
    }
    (trimmedOutput, result)
  }
}
case class StringWhen(pattern: String, actions: Seq[Action[StringWhen]]) extends When {
  /** @inheritdoc */
  def matches(output: String): Boolean = output.contains(pattern)
  /** @inheritdoc */
  def trimToMatchedText(output: String): String = {
    output.substring(output.indexOf(pattern) + pattern.length)
  }
}
case class RegexWhen(pattern: Regex, actions: Seq[Action[RegexWhen]]) extends When {
  /** @inheritdoc */
  def matches(output: String): Boolean = pattern.findFirstIn(output).isDefined
  /** @inheritdoc */
  def trimToMatchedText(output: String): String = output.substring(getMatch(output).end(0))

  private def getMatch(output: String): Match = {
    //We have the guarantee that .get will be successful because this method
    //is only invoked if `matches` returned true.
    pattern.findFirstMatchIn(output).get
  }

  //TODO: would be nice not to duplicate most of this code here.
  /** @inheritdoc */
  override def execute[R](process: RichProcess, output: Option[String]): (Option[String], Option[R]) = {
    val trimmedOutput = output.map(trimToMatchedText)
    var result = Option.empty[R]
    //We have the guarantee that we can invoke get here because execute is only invoke when `matches` returned true.
    val `match` = getMatch(output.get)
    actions foreach {
      case SendAction(text) =>
        process.print(text)
      case SendWithRegexAction(text) =>
        process.print(text(`match`))
      case ReturningAction(r) =>
        result = Some(r.asInstanceOf[R])
      case ReturningWithRegexAction(text) =>
        val m = getMatch(output.get)
        result = Some(text.asInstanceOf[Match => R](`match`))
      case ExitAction =>
        process.destroy()
        //Preemptive exit to guarantee anything after the Exit does not get executed
        return (trimmedOutput, result)
    }
    (trimmedOutput, result)
  }
}
case class EndOfFileWhen(actions: Seq[Action[EndOfFileWhen]]) extends When {
  /** @inheritdoc */
  def matches(output: String): Boolean = false
  /** @inheritdoc */
  def trimToMatchedText(output: String): String = output
}
case class TimeoutWhen(actions: Seq[Action[TimeoutWhen]]) extends When {
  /** @inheritdoc */
  def matches(output: String): Boolean = false
  /** @inheritdoc */
  def trimToMatchedText(output: String): String = output
}