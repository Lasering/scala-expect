package work.martins.simon.expect.dsl

import work.martins.simon.expect.{Timeout, EndOfFile}

import scala.util.matching.Regex
import work.martins.simon.expect.fluent.ExpectBlock

class ExpectDefinition[R](builder: Expect[R], target: ExpectBlock[R]) extends AbstractDefinition[R](builder) {
  override def when(pattern: String): DSLWithBlock = new WhenDefinition(builder, target.when(pattern))
  override def when(pattern: Regex): DSLWithBlock = new WhenDefinition(builder, target.when(pattern))
  override def when(pattern: EndOfFile.type): DSLWithBlock = new WhenDefinition(builder, target.when(pattern))
  override def when(pattern: Timeout.type): DSLWithBlock = new WhenDefinition(builder, target.when(pattern))
}
