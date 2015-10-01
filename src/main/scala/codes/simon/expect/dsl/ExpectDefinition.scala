package codes.simon.expect.dsl

import codes.simon.expect.core.{EndOfFile, Timeout}
import codes.simon.expect.fluent.{ExpectBlock => FExpectBlock, When}

import scala.util.matching.Regex

class ExpectDefinition[R](builder: Expect[R], target: FExpectBlock[R]) extends AbstractDefinition[R](builder) {
  override def when(pattern: String): DSLWithBlock = new WhenDefinition(builder, target.when(pattern))
  override def when(pattern: Regex): DSLWithBlock = new WhenDefinition(builder, target.when(pattern))
  override def when(pattern: EndOfFile.type): DSLWithBlock = new WhenDefinition(builder, target.when(pattern))
  override def when(pattern: Timeout.type): DSLWithBlock = new WhenDefinition(builder, target.when(pattern))
}
