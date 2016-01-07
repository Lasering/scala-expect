package work.martins.simon.expect.dsl

import work.martins.simon.expect.fluent.{EndOfFileWhen, TimeoutWhen, RegexWhen, StringWhen}
import work.martins.simon.expect.{core, Timeout, EndOfFile}

import scala.util.matching.Regex
import scala.util.matching.Regex.Match

abstract class AbstractDefinition[R](builder: Expect[R]) extends DSL[R] {
  def apply(block: => DSL[R]): this.type = {
    builder.build(this, block)
    this
  }

  def expect: ExpectBlockDefinition[R] = builder.expect

  def when(pattern: String): WhenDefinition[R, StringWhen[R]] = builder.when(pattern)
  def when(pattern: Regex): WhenDefinition[R, RegexWhen[R]] = builder.when(pattern)
  def when(pattern: Timeout.type): WhenDefinition[R, TimeoutWhen[R]] = builder.when(pattern)
  def when(pattern: EndOfFile.type): WhenDefinition[R, EndOfFileWhen[R]] = builder.when(pattern)

  def withBlock(block: DSL[R] => Unit): DSL[R] = builder.withBlock(block)

  def send(text: String): DSL[R] = builder.send(text)
  def send(text: Match => String): DSL[R] = builder.send(text)
  def sendln(text: String): DSL[R] = builder.sendln(text)
  def sendln(text: Match => String): DSL[R] = builder.sendln(text)

  def returning(result: => R): DSL[R] = builder.returning(result)
  def returning(result: Match => R): DSL[R] = builder.returning(result)
  def returningExpect(result: => core.Expect[R]): DSL[R] = builder.returningExpect(result)
  def returningExpect(result: Match => core.Expect[R]): DSL[R] = builder.returningExpect(result)

  def exit(): DSL[R] = builder.exit()
}
