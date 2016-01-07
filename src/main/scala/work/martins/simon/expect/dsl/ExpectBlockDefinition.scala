package work.martins.simon.expect.dsl

import work.martins.simon.expect.fluent._
import work.martins.simon.expect.{core, Timeout, EndOfFile}

import scala.util.matching.Regex

class ExpectBlockDefinition[R](builder: Expect[R], target: ExpectBlock[R]) extends AbstractDefinition[R](builder) {
  override def when(pattern: String): WhenDefinition[R, StringWhen[R]] = new WhenDefinition(builder, target.when(pattern))
  override def when(pattern: Regex): WhenDefinition[R, RegexWhen[R]] = new WhenDefinition(builder, target.when(pattern))
  override def when(pattern: Timeout.type): WhenDefinition[R, TimeoutWhen[R]] = new WhenDefinition(builder, target.when(pattern))
  override def when(pattern: EndOfFile.type): WhenDefinition[R, EndOfFileWhen[R]] = new WhenDefinition(builder, target.when(pattern))

  def toCore: core.ExpectBlock[R] = target.toCore
}
