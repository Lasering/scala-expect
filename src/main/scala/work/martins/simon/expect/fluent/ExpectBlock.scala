package work.martins.simon.expect.fluent

import scala.util.matching.Regex

import work.martins.simon.expect.core
import work.martins.simon.expect.core.{Timeout, EndOfFile}

class ExpectBlock[R](val parent: Expect[R]) extends Runnable[R] with Expectable[R] with Whenable[R] {
  val settings = parent.settings
  protected val runnableParent: Runnable[R] = parent
  protected val expectableParent: Expectable[R] = parent

  //The value we set here is irrelevant since we override the implementation of all the 'when' methods.
  //We decided to set whenableParent to 'this' to make it obvious that this is the root of all Whenables.
  protected val whenableParent: Whenable[R] = this
  protected var whens = Seq.empty[When[R]]
  protected def newWhen[W <: When[R]](when: W): W = {
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
    * code somewhat more illegible because "hidden" Whens will also be added.
    *
    * If you need to add more than one When you have two options:
    *
    *  1. {{{
    *    e.expect
    *      .when(...)
    *         .sendln(..)
    *         .returning(...)
    *      .addWhen(errorCaseWhen)
    *      .addWhen(anotherWhen)
    *  }}}
    *  1. {{{
    *    e.expect
    *      .when(...)
    *         .sendln(..)
    *         .returning(...)
    *      .addWhens(multipleWhens)
    *  }}}
    *
    * @param f function that adds the `When`.
    * @return the added `When`.
    */
  def addWhen[W <: When[R]](f: ExpectBlock[R] => W): W = f(this)
  /**
    * Add arbitrary `When`s to this `ExpectBlock`.
    *
    * This method is very similar to the `addWhen` with the following differences:
    *  1. `f` has a more relaxed type.
    *  1. It returns this ExpectBlock. Which effectively prohibits you from invoking When methods.
    *  1. Has a more semantic name when it comes to add multiple When's.
    *
    * @param f function that adds `When`s.
    * @return this ExpectBlock.
    */
  def addWhens(f: ExpectBlock[R] => Unit): ExpectBlock[R] = {
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
  override def equals(other: Any): Boolean = other match {
    case that: ExpectBlock[R] => whens == that.whens
    case _ => false
  }
  override def hashCode(): Int = whens.hashCode()
}
