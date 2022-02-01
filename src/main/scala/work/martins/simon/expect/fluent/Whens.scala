package work.martins.simon.expect.fluent

import scala.language.higherKinds
import scala.util.matching.Regex
import scala.util.matching.Regex.Match

import work.martins.simon.expect.StringUtils.*
import work.martins.simon.expect.core.actions.*
import work.martins.simon.expect.{FromInputStream, core}
import work.martins.simon.expect.FromInputStream.StdOut

sealed trait When[R] extends Whenable[R]:
  /** The concrete core When type constructor which this When is a builder for. */
  type CoreWhen[+X] <: core.When[X]
  
  /** The concrete When type constructor to which the actions will be applied. */
  type This[X] <: When[X]
  
  def readFrom: FromInputStream
  
  val parent: ExpectBlock[R]
  
  protected val whenableParent: ExpectBlock[R] = parent
  
  protected var actions = Seq.empty[Action[R, CoreWhen]]
  protected def newAction(action: Action[R, CoreWhen]): this.type =
    actions :+= action
    this
  
  /**
    * Send `text` to the stdIn of the underlying process.
    * Send will only occur when Expect is run.
    *
    * @return this When.
    */
  def send(text: String, sensitive: Boolean = false): this.type = newAction(Send(text, sensitive))
  /**
    * Sends `text` terminated with `System.lineSeparator()` to the stdIn of the underlying process.
    * Send will only occur when Expect is run.
    *
    * @return this When.
    */
  def sendln(text: String, sensitive: Boolean = false): this.type = newAction(Sendln(text, sensitive))
  /**
    * Returns `result` when this Expect is run.
    * If this method is invoked more than once only the last `result` will be returned.
    * Note however that the previous returning actions will also be executed.
    *
    * @return this When.
    */
  def returning(result: => R): this.type = newAction(Returning(result))
  
  def returningExpect(result: => core.Expect[R]): this.type = newAction(ReturningExpect(result))
  
  /**
    * Terminates the current run of Expect causing it to return the last returned value.
    * Any action or expect block added after this Exit will not be executed.
    *
    * @return this When.
    */
  def exit(): this.type = newAction(Exit())
  
  /**
    * Add arbitrary `Action`s to this `When`.
    *
    * This is helpful to refactor code. For example: imagine you want to perform the same actions whenever an error
    * occurs. You could leverage this method to do so in the following way:
    * {{{
    *   def preemtiveExit(when: When[String]): Unit =
    *     when
    *       .returning("Got some error")
    *       .exit()
    *
    *   def parseOutputA: Expect[String] =
    *     val e = new Expect("some command", "")
    *     e.expect
    *       .when(...)
    *         .action1
    *       .when(...)
    *         .addActions(preemtiveExit)
    *
    *   def parseOutputB: Expect[String] =
    *     val e = new Expect("some command", "")
    *     e.expect(...)
    *       .addActions(preemtiveExit)
    * }}}
    *
    * @param f function that adds `Action`s.
    * @return this `When`.
    */
  def addActions(f: This[R] => When[R]): this.type =
    f(this.asInstanceOf[This[R]])
    this
  
  /**
   * @return the core.When equivalent of this fluent.When.
   */
  def toCore: CoreWhen[R]
  
  def toString(pattern: String): String =
    s"""when($pattern, readFrom = $readFrom) {
       |${actions.mkString("\n").indent()}
       |}""".stripMargin

case class StringWhen[R](parent: ExpectBlock[R], pattern: String, readFrom: FromInputStream = StdOut) extends When[R]:
  type CoreWhen[+X] = core.StringWhen[X]
  type This[X] = StringWhen[X]
  
  def toCore: core.StringWhen[R] = core.StringWhen[R](pattern, readFrom, actions*)
  
  override def toString: String = toString(escape(pattern))
  override def equals(other: Any): Boolean = other.asInstanceOf[Matchable] match
    case that: StringWhen[?] => pattern == that.pattern && readFrom == that.readFrom && actions == that.actions
    case _ => false
  override def hashCode(): Int =
    Seq(pattern, readFrom, actions)
      .map(_.hashCode())
      .foldLeft(0)((a, b) => 31 * a + b)

case class RegexWhen[R](parent: ExpectBlock[R], pattern: Regex, readFrom: FromInputStream = StdOut) extends When[R]:
  type CoreWhen[+X] = core.RegexWhen[X]
  type This[X] = RegexWhen[X]
  
  /**
    * Send the result of invoking `text` with the `Match` of the regex used, to the stdIn of the underlying process.
    * Send will only occur when Expect is run.
    *
    * @return this When.
    */
  def send(text: Match => String): RegexWhen[R] = newAction(Send(text))
  /**
    * Send the result of invoking `text` with the `Match` of the regex used with a `System.lineSeparator()` appended
    * to the end, to the stdIn of the underlying process.
    * Send will only occur when Expect is run.
    *
    * @return this When.
    */
  def sendln(text: Match => String): RegexWhen[R] = newAction(Sendln(text))
  /**
   * Returns the result of invoking `result` with the `Match` of the regex used, when this Expect is run.
   * If this method is invoked more than once only the last `result` will be returned.
   * Note however that the previous returning actions will also be executed.
   *
   * @return this When.
   */
  def returning(result: Match => R): RegexWhen[R] = newAction(ReturningWithRegex(result))
  
  def returningExpect(result: Match => core.Expect[R]): RegexWhen[R] = newAction(ReturningExpect(result))
  
  def toCore: core.RegexWhen[R] = core.RegexWhen[R](pattern, readFrom, actions*)
  
  override def toString: String = toString(escape(pattern.regex) + ".r")
  override def equals(other: Any): Boolean = other.asInstanceOf[Matchable] match
    case that: RegexWhen[?] => pattern.regex == that.pattern.regex && readFrom == that.readFrom && actions == that.actions
    case _ => false
  override def hashCode(): Int =
    Seq(pattern, readFrom, actions)
      .map(_.hashCode())
      .foldLeft(0)((a, b) => 31 * a + b)

case class EndOfFileWhen[R](parent: ExpectBlock[R], readFrom: FromInputStream = StdOut) extends When[R]:
  type CoreWhen[+X] = core.EndOfFileWhen[X]
  type This[X] = EndOfFileWhen[X]
  
  def toCore: core.EndOfFileWhen[R] = core.EndOfFileWhen[R](readFrom, actions*)
  
  override def toString: String = toString("EndOfFile")
  override def equals(other: Any): Boolean = other.asInstanceOf[Matchable] match
    case that: EndOfFileWhen[?] => readFrom == that.readFrom && actions == that.actions
    case _ => false
  override def hashCode(): Int =
    Seq(readFrom, actions)
      .map(_.hashCode())
      .foldLeft(0)((a, b) => 31 * a + b)

case class TimeoutWhen[R](parent: ExpectBlock[R]) extends When[R]:
  type CoreWhen[+X] = core.TimeoutWhen[X]
  type This[X] = TimeoutWhen[X]
  
  def toCore: core.TimeoutWhen[R] = core.TimeoutWhen[R](actions*)
  
  /** The readFrom of a TimeoutWhen is not used but to keep the implementation simple we set its value to StdOut. */
  override val readFrom: FromInputStream = StdOut
  
  override def toString: String =
    s"""when(Timeout) {
       |${actions.mkString("\n").indent()}
       |}""".stripMargin
  override def equals(other: Any): Boolean = other.asInstanceOf[Matchable] match
    case that: TimeoutWhen[?] => actions == that.actions
    case _ => false
  override def hashCode(): Int = actions.hashCode()