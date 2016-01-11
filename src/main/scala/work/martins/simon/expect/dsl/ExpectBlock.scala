package work.martins.simon.expect.dsl

import work.martins.simon.expect.fluent.{EndOfFileWhen, ExpectBlock => FExpectBlock, RegexWhen, StringWhen, TimeoutWhen}
import work.martins.simon.expect.{EndOfFile, Timeout, core}

import scala.util.matching.Regex

class ExpectBlock[R](builder: Expect[R], target: FExpectBlock[R]) extends AbstractDefinition[R](builder) {
  override def when(pattern: String): When[R, StringWhen[R]] = new When(builder, target.when(pattern))
  override def when(pattern: Regex): When[R, RegexWhen[R]] = new When(builder, target.when(pattern))
  override def when(pattern: Timeout.type): When[R, TimeoutWhen[R]] = new When(builder, target.when(pattern))
  override def when(pattern: EndOfFile.type): When[R, EndOfFileWhen[R]] = new When(builder, target.when(pattern))

  def toCore: core.ExpectBlock[R] = target.toCore
}
