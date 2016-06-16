package work.martins.simon.expect.fluent

import scala.util.matching.Regex
import work.martins.simon.expect.{Timeout, EndOfFile, core}
import work.martins.simon.expect.StringUtils._

/**
  * @define type ExpectBlock
  */
final class ExpectBlock[R](val parent: Expect[R]) extends Whenable[R] {
  val settings = parent.settings

  protected val whenableParent: Whenable[R] = this

  //We decided to set whenableParent to 'this' to make it obvious that this is the root of all Whenables.
  protected override val expectableParent: Expectable[R] = parent
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

  protected[expect] def containsWhens(): Boolean = whens.nonEmpty

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
