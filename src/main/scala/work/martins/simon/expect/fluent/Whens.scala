package work.martins.simon.expect.fluent

import scala.reflect.ClassTag
import scala.util.matching.Regex
import scala.util.matching.Regex.Match

import work.martins.simon.expect.core
import work.martins.simon.expect.core._
import work.martins.simon.expect.core.StringUtils._

trait When[R] extends Runnable[R] with Expectable[R] with Whenable[R] {
  type W <: core.When[R]

  def parent: ExpectBlock[R]

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
   * @return this When.
   */
  def returning(result: => R): When[R] = newAction(Returning(() => result))

  def returning(result: Expect[R]): When[R] = newAction(ReturningExpect(() => result))

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
    *   //Then in your expects
    *   def parseOutputA: Expect[String] = {
    *     val e = new Expect("some command", "")
    *     e.expect(...)
    *     e.expect
    *       .when(...)
    *         .action1
    *       .when(...)
    *         .addActions(preemtiveExit)
    *   }
    *
    *   def parseOutputB: Expect[String] = {
    *     val e = new Expect("some command", "")
    *     e.expect
    *       .when(...)
    *         .action1
    *         .action2
    *       .when(...)
    *         .action1
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
   * Terminates the current run of Expect causing it to return the last returned value.
   * Any action added after this one will not be executed.
   * @return this When.
   */
  def exit(): When[R] = newAction(Exit)

  /***
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
case class RegexWhen[R: ClassTag](parent: ExpectBlock[R], pattern: Regex) extends When[R] {
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
   * @return this When.
   */
  def returning(result: Match => R): When[R] = newAction{
    //Tricky business to overcome type erasure that we would arise if these two methods were to exist
    //  def returning(result: Match => R): When[R]
    //  def returning(result: Match => CExpect[R]): When[R]
    import scala.reflect.classTag
    if (classTag[R].runtimeClass == classOf[core.Expect[_]]) {
      ReturningExpectWithRegex(result.asInstanceOf[Match => core.Expect[R]])
    } else {
      ReturningWithRegex(result)
    }
  }

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
