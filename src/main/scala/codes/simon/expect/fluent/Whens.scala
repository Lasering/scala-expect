package codes.simon.expect.fluent

import codes.simon.expect.core.{
  When => CWhen,
  StringWhen => CStringWhen,
  RegexWhen => CRegexWhen,
  TimeoutWhen => CTimeoutWhen,
  EndOfFileWhen => CEndOfFileWhen,
  _
}
import scala.util.matching.Regex
import scala.util.matching.Regex.Match

abstract class When[R](parent: ExpectBlock[R]) extends Runnable[R] with Expectable[R] with Whenable[R] {
  type W <: CWhen

  val runnableParent: Runnable[R] = parent
  val expectableParent: Expectable[R] = parent
  val whenableParent: Whenable[R] = parent

  protected var actions = Seq.empty[Action[W]]
  protected def newAction(action: Action[W]): When[R] = {
    actions :+= action
    this
  }

  /**
   * Send `text` to the stdIn of the underlying process.
   * Send will only occur when Expect is run.
   * @return this When.
   */
  def send(text: String): When[R] = newAction(SendAction(text))
  /**
   * Sends `text` terminated with `System.lineSeparator()` to the stdIn of the underlying process.
   * Send will only occur when Expect is run.
   * @return this When.
   */
  def sendln(text: String): When[R] = newAction(SendAction(text + System.lineSeparator()))
  /**
   * Returns `result` when this Expect is run.
   * If this method is invoked more than once only the last `result` will be returned.
   * @return this When.
   */
  def returning(result: R): When[R] = newAction(ReturningAction(result))
  /**
   * Terminates the current run of Expect causing it to return the last returned value.
   * Any action added after this one will not be executed.
   * @return this When.
   */
  def exit(): When[R] = newAction(ExitAction)

  protected[fluent] def toCoreWhen: W
}
case class StringWhen[R](parent: ExpectBlock[R], pattern: String) extends When[R](parent) {
  type W = CStringWhen
  protected[fluent] def toCoreWhen(): CStringWhen = new CStringWhen(pattern, actions)
}
case class RegexWhen[R](parent: ExpectBlock[R], pattern: Regex) extends When[R](parent) {
  type W = CRegexWhen
  /**
   * Send the result of invoking `text` with the `Match` of the regex used, to the stdIn of the underlying process.
   * Send will only occur when Expect is run.
   * @return this When.
   */
  def send(text: Match => String): When[R] = newAction(SendWithRegexAction(text))
  /**
   * Send the result of invoking `text` with the `Match` of the regex used with a `System.lineSeparator()` appended
   * to the end, to the stdIn of the underlying process.
   * Send will only occur when Expect is run.
   * @return this When.
   */
  def sendln(text: Match => String): When[R] = newAction(SendWithRegexAction(text.andThen(_ + System.lineSeparator())))
  /**
   * Returns the result of invoking `result` with the `Match` of the regex used, when this Expect is run.
   * If this method is invoked more than once only the last `result` will be returned.
   * @return this When.
   */
  def returning(result: Match => R): When[R] = newAction(ReturningWithRegexAction(result))

  protected[fluent] def toCoreWhen(): CRegexWhen = new CRegexWhen(pattern, actions)
}
case class TimeoutWhen[R](parent: ExpectBlock[R]) extends When[R](parent) {
  type W = CTimeoutWhen
  protected[fluent] def toCoreWhen(): CTimeoutWhen = new CTimeoutWhen(actions)
}
case class EndOfFileWhen[R](parent: ExpectBlock[R]) extends When[R](parent) {
  type W = CEndOfFileWhen
  protected[fluent] def toCoreWhen(): CEndOfFileWhen = new CEndOfFileWhen(actions)
}
