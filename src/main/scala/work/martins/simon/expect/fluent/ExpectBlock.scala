package work.martins.simon.expect.fluent

import scala.util.matching.Regex
import work.martins.simon.expect.*
import work.martins.simon.expect.StringUtils.*
import work.martins.simon.expect.FromInputStream.StdOut

final case class ExpectBlock[R](parent: Expect[R]) extends Whenable[R]:
  // Unfortunately we cannot enforce a fluent ExpectBlock to have whens.
  
  protected val whenableParent: ExpectBlock[R] = this
  
  protected var whens = Seq.empty[When[R]]
  protected def newWhen[W <: When[R]](when: W): W =
    whens :+= when
    when
  
  override def when(pattern: String): StringWhen[R] = newWhen(StringWhen(this, pattern))
  override def when(pattern: String, readFrom: FromInputStream): StringWhen[R] = newWhen(StringWhen(this, pattern, readFrom))
  override def when(pattern: Regex): RegexWhen[R] = newWhen(RegexWhen(this, pattern))
  override def when(pattern: Regex, readFrom: FromInputStream): RegexWhen[R] = newWhen(RegexWhen(this, pattern, readFrom))
  override def when(pattern: EndOfFile.type, readFrom: FromInputStream = StdOut): EndOfFileWhen[R] = newWhen(EndOfFileWhen(this, readFrom))
  override def when(pattern: Timeout.type): TimeoutWhen[R] = newWhen(TimeoutWhen(this))
  override def addWhen[W <: When[R]](f: ExpectBlock[R] => W): W = f(this)
  override def addWhens(f: ExpectBlock[R] => When[R]): ExpectBlock[R] =
    f(this)
    this
  
  def containsWhens(): Boolean = whens.nonEmpty
  
  /** @return the core ExpectBlock equivalent of this ExpectBlock. */
  def toCore: core.ExpectBlock[R] = core.ExpectBlock(whens.map(_.toCore)*)
  
  override def toString: String =
    s"""expect {
        |${whens.mkString("\n").indent()}
        |}""".stripMargin
  override def equals(other: Any): Boolean = other match
    case that: ExpectBlock[?] => whens == that.whens
    case _ => false
  
  override def hashCode(): Int = whens.hashCode()