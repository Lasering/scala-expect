package work.martins.simon.expect.dsl

import work.martins.simon.expect.fluent.{EndOfFileWhen, RegexWhen, StringWhen, TimeoutWhen, When => FWhen}
import work.martins.simon.expect.{EndOfFile, Timeout, core}

import scala.util.matching.Regex
import scala.util.matching.Regex.Match

abstract class AbstractDefinition[R](builder: Expect[R]) extends DSL[R] {
  def apply(block: => DSL[R]): this.type = {
    builder.build(this, block)
    this
  }

  def expect: ExpectBlock[R] = builder.expect
  def addExpectBlock(f: DSL[R] => ExpectBlock[R]): ExpectBlock[R] = builder.addExpectBlock(f)

  def when(pattern: String): When[R, StringWhen[R]] = builder.when(pattern)
  def when(pattern: Regex): When[R, RegexWhen[R]] = builder.when(pattern)
  def when(pattern: Timeout.type): When[R, TimeoutWhen[R]] = builder.when(pattern)
  def when(pattern: EndOfFile.type): When[R, EndOfFileWhen[R]] = builder.when(pattern)
  def addWhen[W <: FWhen[R]](f: ExpectBlock[R] => When[R, W]): When[R, W] = builder.addWhen(f)
  def addWhens(f: ExpectBlock[R] => DSL[R]): ExpectBlock[R] = builder.addWhens(f)

  def send(text: String): DSL[R] = builder.send(text)
  def send(text: Match => String): DSL[R] = builder.send(text)
  def sendln(text: String): DSL[R] = builder.sendln(text)
  def sendln(text: Match => String): DSL[R] = builder.sendln(text)
  def returning(result: => R): DSL[R] = builder.returning(result)
  def returning(result: Match => R): DSL[R] = builder.returning(result)
  def returningExpect(result: => core.Expect[R]): DSL[R] = builder.returningExpect(result)
  def returningExpect(result: Match => core.Expect[R]): DSL[R] = builder.returningExpect(result)
  def exit(): DSL[R] = builder.exit()
  def addActions(f: When[R, _] => DSL[R]): DSL[R] = builder.addActions(f)
 }
