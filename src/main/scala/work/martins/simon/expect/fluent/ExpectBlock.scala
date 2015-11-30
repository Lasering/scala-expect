package work.martins.simon.expect.fluent

import scala.reflect.ClassTag
import scala.util.matching.Regex

import work.martins.simon.expect.core
import work.martins.simon.expect.core.{Timeout, EndOfFile}

class ExpectBlock[R: ClassTag](val parent: Expect[R]) extends Runnable[R] with Expectable[R] with Whenable[R] {
  protected val runnableParent: Runnable[R] = parent
  protected val expectableParent: Expectable[R] = parent

  //The value we set here is irrelevant since we override the implementation of all the 'when' methods.
  //We decided to set whenableParent to 'this' to make it obvious that this is the root of all Whenables.
  protected val whenableParent: Whenable[R] = this
  private var whens = Seq.empty[When[R]]
  private def newWhen[W <: When[R]](when: W): W = {
    whens :+= when
    when
  }
  override def when(pattern: String): StringWhen[R] = newWhen(new StringWhen[R](this, pattern))
  override def when(pattern: Regex): RegexWhen[R] = newWhen(new RegexWhen[R](this, pattern))
  override def when(pattern: EndOfFile.type): EndOfFileWhen[R] = newWhen(new EndOfFileWhen[R](this))
  override def when(pattern: Timeout.type): TimeoutWhen[R] = newWhen(new TimeoutWhen[R](this))

  /**
    * Add arbitrary `When`s to this `ExpectBlock`.
    *
    * This is helpful to refactor code. For example: imagine you have an error case you want to add to
    * multiple `ExpectBlock`s. You could leverage this method to do so in the following way:
    * {{{
    *   def errorCaseWhen: ExpectBlock[String] => Unit = { expectBlock =>
    *     expectBlock
    *       .when("Some error")
    *         .returning("Got some error")
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
    *     e.expect
    *       .addWhen(errorCaseWhen)
    *   }
    *
    *   def parseOutputB: Expect[String] = {
    *     val e = new Expect("some command", "")
    *     e.expect
    *       .addWhen(errorCaseWhen)
    *       .when(...)
    *         .action1
    *         .action2
    *       .when(...)
    *         .action1
    *     e.expect(...)
    *       .returning(...)
    *   }
    * }}}
    *
    * @param f function that adds `When`s.
    * @return the added `When`.
    */
  def addWhen[W <: When[R]](f: ExpectBlock[R] => W): W = f(this)

  /***
    * @return the core.ExpectBlock equivalent of this fluent.ExpectBlock.
    */
  def toCore: core.ExpectBlock[R] = new core.ExpectBlock[R](whens.map(_.toCore):_*)

  override def toString: String = {
    s"""expect {
        |\t\t${whens.mkString("\n\t\t")}
        |\t}""".stripMargin
  }
}
