package work.martins.simon.expect.fluent

import work.martins.simon.expect.StringUtils._
import work.martins.simon.expect._

import scala.util.matching.Regex

/**
  * @define type ExpectBlock
  */
final case class ExpectBlock[R](parent: Expect[R]) extends Whenable[R] {
  protected val whenableParent: ExpectBlock[R] = this

  protected var whens = Seq.empty[When[R]]
  protected def newWhen[W <: When[R]](when: W): W = {
    whens :+= when
    when
  }
  
  override def when(pattern: String, readFrom: FromInputStream): StringWhen[R] = newWhen(StringWhen(this)(pattern, readFrom))
  override def when(pattern: Regex, readFrom: FromInputStream): RegexWhen[R] = newWhen(RegexWhen(this)(pattern, readFrom))
  override def when(pattern: EndOfFile.type, readFrom: FromInputStream): EndOfFileWhen[R] = newWhen(EndOfFileWhen(this)(readFrom))
  override def when(pattern: Timeout.type): TimeoutWhen[R] = newWhen(TimeoutWhen(this))
  override def addWhen[W <: When[R]](f: ExpectBlock[R] => W): W = f(this)
  override def addWhens(f: ExpectBlock[R] => When[R]): ExpectBlock[R] = {
    f(this)
    this
  }

  protected[expect] def containsWhens(): Boolean = whens.nonEmpty

  /***
    * @return the core.ExpectBlock equivalent of this fluent.ExpectBlock.
    */
  def toCore: core.ExpectBlock[R] = core.ExpectBlock(whens.map(_.toCore):_*)

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
