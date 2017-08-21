package work.martins.simon.expect.fluent

import scala.util.matching.Regex

import work.martins.simon.expect._
import work.martins.simon.expect.StringUtils._

/**
  * @define type ExpectBlock
  */
final class ExpectBlock[R](val parent: Expect[R], val readFrom: FromInputStream = StdOut) extends Whenable[R] {
  protected val whenableParent: ExpectBlock[R] = this

  protected var whens = Seq.empty[When[R]]
  protected def newWhen[W <: When[R]](when: W): W = {
    whens :+= when
    when
  }
  
  override def when(pattern: String, readFrom: FromInputStream): StringWhen[R] = newWhen(new StringWhen[R](this, pattern, readFrom))
  override def when(pattern: Regex, readFrom: FromInputStream): RegexWhen[R] = newWhen(new RegexWhen[R](this, pattern, readFrom))
  override def when(pattern: EndOfFile.type, readFrom: FromInputStream): EndOfFileWhen[R] = newWhen(new EndOfFileWhen[R](this, readFrom))
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
