package work.martins.simon.expect.fluent

import scala.reflect.ClassTag
import scala.util.matching.Regex
import scala.util.matching.Regex.Match

import work.martins.simon.expect.core
import work.martins.simon.expect.core._

abstract class When[R](parent: ExpectBlock[R]) extends Runnable[R] with Expectable[R] with Whenable[R] with AddBlock {
  type W <: core.When[R]

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
   * Terminates the current run of Expect causing it to return the last returned value.
   * Any action added after this one will not be executed.
   * @return this When.
   */
  def exit(): When[R] = newAction(Exit)

  def toCore: W

  override def toString: String =
    s"""when {
       |\t\t\t${actions.mkString("\n\t\t\t")}
       |\t\t}""".stripMargin
}
case class StringWhen[R](parent: ExpectBlock[R], pattern: String) extends When[R](parent) {
  type W = core.StringWhen[R]
  def toCore: W = new core.StringWhen[R](pattern)(actions:_*)
}
case class RegexWhen[R: ClassTag](parent: ExpectBlock[R], pattern: Regex) extends When[R](parent) {
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
}
case class TimeoutWhen[R](parent: ExpectBlock[R]) extends When[R](parent) {
  type W = core.TimeoutWhen[R]
  def toCore: W = new core.TimeoutWhen[R](actions:_*)
}
case class EndOfFileWhen[R](parent: ExpectBlock[R]) extends When[R](parent) {
  type W = core.EndOfFileWhen[R]

  def toCore: W = new core.EndOfFileWhen[R](actions:_*)
}
