package work.martins.simon.expect.dsl

import work.martins.simon.expect.fluent.{EndOfFileWhen, RegexWhen, StringWhen, TimeoutWhen, When => FWhen}
import work.martins.simon.expect.{EndOfFile, Timeout, core}

import scala.util.matching.Regex
import scala.util.matching.Regex.Match

trait DSL[R] {
  def expect: ExpectBlock[R]
  def addExpectBlock(f: DSL[R] => ExpectBlock[R]): ExpectBlock[R]

  // These expects are just shortcuts
  def expect(pattern: String): When[R, StringWhen[R]] = expect.when(pattern)
  def expect(pattern: Regex): When[R, RegexWhen[R]] = expect.when(pattern)
  def expect(pattern: Timeout.type): When[R, TimeoutWhen[R]] = expect.when(pattern)
  def expect(pattern: EndOfFile.type): When[R, EndOfFileWhen[R]] = expect.when(pattern)

  def when(pattern: String): When[R, StringWhen[R]]
  def when(pattern: Regex): When[R, RegexWhen[R]]
  def when(pattern: Timeout.type): When[R, TimeoutWhen[R]]
  def when(pattern: EndOfFile.type): When[R, EndOfFileWhen[R]]
  //TODO: fix this as its intended purpose is not working correctly. Same for addActions
  def addWhen[W <: FWhen[R]](f: ExpectBlock[R] => When[R, W]): When[R, W]
  def addWhens(f: ExpectBlock[R] => DSL[R]): ExpectBlock[R]

  def send(text: String): DSL[R]
  def send(text: Match => String): DSL[R]
  def sendln(text: String): DSL[R]
  def sendln(text: Match => String): DSL[R]
  def returning(result: => R): DSL[R]
  def returning(result: Match => R): DSL[R]
  def returningExpect(result: => core.Expect[R]): DSL[R]
  def returningExpect(result: Match => core.Expect[R]): DSL[R]
  def addActions(f: When[R, _] => DSL[R]): DSL[R]

  def exit(): DSL[R]
}
