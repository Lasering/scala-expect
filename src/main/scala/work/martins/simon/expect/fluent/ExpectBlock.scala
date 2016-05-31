package work.martins.simon.expect.fluent

import scala.util.matching.Regex
import work.martins.simon.expect.{Timeout, EndOfFile, core}
import work.martins.simon.expect.StringUtils._

/**
  * @define type ExpectBlock
  */
class ExpectBlock[R](val parent: Expect[R]) extends Runnable[R] with Expectable[R] with Whenable[R] {
  val settings = parent.settings
  protected val runnableParent: Runnable[R] = parent
  protected val expectableParent: Expectable[R] = parent

  //We decided to set whenableParent to 'this' to make it obvious that this is the root of all Whenables.
  protected val whenableParent: Whenable[R] = this
  private var whens = Seq.empty[When[R]]
  protected def newWhen[W <: When[R]](when: W): W = {
    whens :+= when
    when
  }
  override def when(pattern: String): StringWhen[R] = newWhen(new StringWhen[R](this, pattern))
  override def when(pattern: Regex): RegexWhen[R] = newWhen(new RegexWhen[R](this, pattern))
  override def when(pattern: EndOfFile.type): EndOfFileWhen[R] = newWhen(new EndOfFileWhen[R](this))
  override def when(pattern: Timeout.type): TimeoutWhen[R] = newWhen(new TimeoutWhen[R](this))
  override def addWhen[W <: When[R]](f: ExpectBlock[R] => W): W = f(this)
  override def addWhens(f: ExpectBlock[R] => Unit): ExpectBlock[R] = {
    f(this)
    this
  }

  private[fluent] def map[T](parent: Expect[T], f: R => T): ExpectBlock[T] = {
    val newExpectBlock = new ExpectBlock(parent)
    newExpectBlock.whens = whens.map(_.map(newExpectBlock, f))
    newExpectBlock
  }
  private[fluent] def flatMap[T](parent: Expect[T], f: R => core.Expect[T]): ExpectBlock[T] = {
    val newExpectBlock = new ExpectBlock(parent)
    newExpectBlock.whens = whens.map(_.flatMap(newExpectBlock, f))
    newExpectBlock
  }
  private[fluent] def transform[T](parent: Expect[T])(mapPF: PartialFunction[R, T])(flatMapPF: PartialFunction[R, core.Expect[T]]): ExpectBlock[T] = {
    val newExpectBlock = new ExpectBlock(parent)
    newExpectBlock.whens = whens.map(_.transform(newExpectBlock)(mapPF)(flatMapPF))
    newExpectBlock
  }

  /***
    * @return the core.ExpectBlock equivalent of this fluent.ExpectBlock.
    */
  def toCore: core.ExpectBlock[R] = new core.ExpectBlock[R](whens.map(_.toCore):_*)

  override def toString: String = {
    s"""expect {
        |${whens.mkString("\n").indent()}
        |}""".stripMargin
  }
  override def equals(other: Any): Boolean = other match {
    case that: ExpectBlock[R] => whens == that.whens
    case _ => false
  }
  override def hashCode(): Int = whens.hashCode()
}
