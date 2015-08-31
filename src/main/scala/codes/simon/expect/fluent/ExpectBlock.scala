package codes.simon.expect.fluent

import codes.simon.expect.core.{ExpectBlock => CExpectBlock}

import scala.util.matching.Regex

class ExpectBlock[R](val parent: Expect[R]) extends Runnable[R] with Expectable[R] with Whenable[R] {
  val runnableParent: Runnable[R] = parent

  val expectableParent: Expectable[R] = parent

  //The value we set here is irrelevant since we override the implementation of all the 'when' methods.
  //We decided to set to 'this' to make it obvious that this is the root of all Whenables.
  val whenableParent: Whenable[R] = this
  private var whens = Seq.empty[When[R]]
  private def newWhen[W <: When[R]](when: W): W = {
    whens :+= when
    when
  }
  override def when(pattern: String): StringWhen[R] = newWhen(new StringWhen[R](this, pattern))
  override def when(pattern: Regex): RegexWhen[R] = newWhen(new RegexWhen[R](this, pattern))
  override def when(pattern: EndOfFile.type): EndOfFileWhen[R] = newWhen(new EndOfFileWhen[R](this))
  override def when(pattern: Timeout.type): TimeoutWhen[R] = newWhen(new TimeoutWhen[R](this))

  protected[fluent] def toCoreExpectBlock = new CExpectBlock(whens.map(_.toCoreWhen))
}
