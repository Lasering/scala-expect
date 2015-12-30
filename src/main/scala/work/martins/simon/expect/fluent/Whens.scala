package work.martins.simon.expect.fluent

import work.martins.simon.expect.StringUtils._
import work.martins.simon.expect.core._
import work.martins.simon.expect.core

import scala.util.matching.Regex
import scala.util.matching.Regex.Match

trait When[R] extends Runnable[R] with Expectable[R] with Whenable[R] {
  type W <: core.When[R]

  def parent: ExpectBlock[R]

  val settings = parent.settings

  protected val runnableParent: Runnable[R] = parent
  protected val expectableParent: Expectable[R] = parent
  protected val whenableParent: Whenable[R] = parent

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
  def send(text: String): When[R] = newAction(Send(text))
  /**
   * Sends `text` terminated with `System.lineSeparator()` to the stdIn of the underlying process.
   * Send will only occur when Expect is run.
   * @return this When.
   */
  def sendln(text: String): When[R] = newAction(Sendln(text))
  /**
   * Returns `result` when this Expect is run.
   * If this method is invoked more than once only the last `result` will be returned.
   * Note however that the previous returning actions will also be executed.
   * @return this When.
   */
  def returning(result: => R): When[R] = newAction(Returning(() => result))

  def returningExpect(result: => core.Expect[R]): When[R] = newAction(ReturningExpect(() => result))

  /**
   * Add arbitrary `Action`s to this `When`.
   *
   * This is helpful to refactor code. For example: imagine you want to perform the same actions whenever an error
   * occurs. You could leverage this method to do so in the following way:
   * {{{
   *   def preemtiveExit: When[String] => Unit = { when =>
   *     when
   *       .returning("Got some error")
   *       .exit()
   *   }
   *
   *   def parseOutputA: Expect[String] = {
   *     val e = new Expect("some command", "")
   *     e.expect
   *       .when(...)
   *         .action1
   *       .when(...)
   *         .addActions(preemtiveExit)
   *   }
   *
   *   def parseOutputB: Expect[String] = {
   *     val e = new Expect("some command", "")
   *     e.expect(...)
   *       .addActions(preemtiveExit)
   *   }
   * }}}
   *
   * @param f function that adds `Action`s.
   * @return this `When`.
   */
  def addActions(f: this.type => Unit): this.type = {
    f(this)
    this
  }

  /**
   * Shortcut to invoke `addWhen` to the parent of this `When`.
   */
  def addWhen[WW <: When[R]](f: ExpectBlock[R] => WW): WW = parent.addWhen(f)
  /**
    * Shortcut to invoke `addWhens` to the parent of this `When`.
    */
  def addWhens(f: ExpectBlock[R] => Unit): ExpectBlock[R] = parent.addWhens(f)

  /**
   * Terminates the current run of Expect causing it to return the last returned value.
   * Any action or expect block added after this Exit will not be executed.
   * @return this When.
   */
  def exit(): When[R] = newAction(Exit)

  /**
   * @return the core.When equivalent of this fluent.When.
   */
  def toCore: W

  def toString(pattern: String): String =
    s"""when $pattern {
       |\t\t\t${actions.mkString("\n\t\t\t")}
       |\t\t}""".stripMargin
}
case class StringWhen[R](parent: ExpectBlock[R], pattern: String) extends When[R]{
  type W = core.StringWhen[R]
  def toCore: W = new core.StringWhen[R](pattern)(actions:_*)
  override def toString: String = toString(escape(pattern))
}
case class RegexWhen[R](parent: ExpectBlock[R], pattern: Regex) extends When[R] {
  type W = core.RegexWhen[R]
  /**
   * Send the result of invoking `text` with the `Match` of the regex used, to the stdIn of the underlying process.
   * Send will only occur when Expect is run.
   * @return this When.
   */
  def send(text: Match => String): When[R] = newAction(SendWithRegex(text))
  /**
   * Send the result of invoking `text` with the `Match` of the regex used with a `System.lineSeparator()` appended
   * to the end, to the stdIn of the underlying process.
   * Send will only occur when Expect is run.
   * @return this When.
   */
  def sendln(text: Match => String): When[R] = newAction(SendlnWithRegex(text))
  /**
   * Returns the result of invoking `result` with the `Match` of the regex used, when this Expect is run.
   * If this method is invoked more than once only the last `result` will be returned.
   * Note however that the previous returning actions will also be executed.
   *
   * @return this When.
   */
  def returning(result: Match => R): When[R] = newAction(ReturningWithRegex(result))

  def returningExpect(result: Match => core.Expect[R]): When[R] = newAction(ReturningExpectWithRegex(result))

  def toCore: W = new core.RegexWhen[R](pattern)(actions:_*)
  override def toString: String = toString(escape(pattern.regex) + ".r")
}
case class TimeoutWhen[R](parent: ExpectBlock[R]) extends When[R] {
  type W = core.TimeoutWhen[R]
  def toCore: W = new core.TimeoutWhen[R](actions:_*)
  override def toString: String = toString("EndOfFile")
}
case class EndOfFileWhen[R](parent: ExpectBlock[R]) extends When[R] {
  type W = core.EndOfFileWhen[R]
  def toCore: W = new core.EndOfFileWhen[R](actions:_*)
  override def toString: String = toString("Timeout")
}
