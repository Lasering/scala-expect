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
    * Add an arbitrary `When` to this `ExpectBlock`.
    *
    * This is helpful to refactor code. For example: imagine you have an error case you want to add to
    * multiple `ExpectBlock`s. You could leverage this method to do so in the following way:
    * {{{
    *   def errorCaseWhen: ExpectBlock[String] => When[String] = { expectBlock =>
    *     expectBlock
    *       .when("Some error")
    *         .returning("Got some error")
    *   }
    *
    *   def parseOutputA: Expect[String] = {
    *     val e = new Expect("some command", "")
    *     e.expect
    *       .when(...)
    *         .sendln(...)
    *     e.expect
    *       .addWhen(errorCaseWhen)
    *         .exit()
    *   }
    *
    *   def parseOutputB: Expect[String] = {
    *     val e = new Expect("some command", "")
    *     e.expect
    *       .when(...)
    *         .sendln(..)
    *         .returning(...)
    *       .addWhen(errorCaseWhen)
    *   }
    * }}}
    *
    * This function returns the added When which allows you to add further actions, see the exit action of the
    * `parseOutputA` method in the above code.
    *
    * It is possible to add more than one When using this method, however this is discouraged since it will make the
    * code somewhat more illegible because an "hidden" When will also be added.
    *
    * @param f function that adds the `When`.
    * @return the added `When`.
    */
  def addWhen[W <: When[R]](f: ExpectBlock[R] => W): W = f(this)
  /**
    * Add an arbitrary `When` to this `ExpectBlock`.
    *
    * This method is almost the same as its overloaded counterpart, the difference is that `f` has a more relaxed type
    * and it returns this ExpectBlock and not the added When.
    *
    * @param f function that adds the `When`.
    * @return this ExpectBlock.
    */
  def addWhen(f: ExpectBlock[R] => Unit): ExpectBlock[R] = {
    f(this)
    this
  }

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
