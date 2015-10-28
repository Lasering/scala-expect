package codes.simon.expect.dsl

import codes.simon.expect.core.{EndOfFile, Timeout}

import scala.util.matching.Regex
import scala.util.matching.Regex.Match

abstract class AbstractDefinition[R](builder: Expect[R]) extends DSL[R] with Block[R] {
  def apply(block: => DSL[R]): this.type = {
    builder.build(this, block)
    this
  }

  def expect: DSL[R] with Block[R] = builder.expect

  def expect(pattern: String): DSL[R] with Block[R] = builder.expect(pattern)
  def expect(pattern: Regex): DSL[R] with Block[R] = builder.expect(pattern)
  def expect(pattern: Timeout.type): DSL[R] with Block[R] = builder.expect(pattern)
  def expect(pattern: EndOfFile.type): DSL[R] with Block[R] = builder.expect(pattern)

  def when(pattern: String): DSL[R] with Block[R] = builder.when(pattern)
  def when(pattern: Regex): DSL[R] with Block[R] = builder.when(pattern)
  def when(pattern: EndOfFile.type): DSL[R] with Block[R] = builder.when(pattern)
  def when(pattern: Timeout.type): DSL[R] with Block[R] = builder.when(pattern)

  def withBlock(block: DSL[R] => Unit): DSL[R] = builder.withBlock(block)

  def send(text: String): DSL[R] = builder.send(text)
  def send(text: Match => String): DSL[R] = builder.send(text)
  def sendln(text: String): DSL[R] = builder.sendln(text)
  def sendln(text: Match => String): DSL[R] = builder.sendln(text)

  def returning(result: => R): DSL[R] = builder.returning(result)
  def returning(result: Match => R): DSL[R] = builder.returning(result)

  def exit(): DSL[R] = builder.exit()
}
