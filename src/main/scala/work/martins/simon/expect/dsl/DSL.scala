package work.martins.simon.expect.dsl

import work.martins.simon.expect.fluent.{StringWhen, RegexWhen, TimeoutWhen, EndOfFileWhen}
import work.martins.simon.expect.{core, Timeout, EndOfFile}

import scala.util.matching.Regex
import scala.util.matching.Regex.Match

trait DSL[R] {
  def expect: ExpectBlockDefinition[R]

  // These expects are just shortcuts
  def expect(pattern: String): WhenDefinition[R, StringWhen[R]] = expect.when(pattern)
  def expect(pattern: Regex): WhenDefinition[R, RegexWhen[R]] = expect.when(pattern)
  def expect(pattern: Timeout.type): WhenDefinition[R, TimeoutWhen[R]] = expect.when(pattern)
  def expect(pattern: EndOfFile.type): WhenDefinition[R, EndOfFileWhen[R]] = expect.when(pattern)

  def when(pattern: String): WhenDefinition[R, StringWhen[R]]
  def when(pattern: Regex): WhenDefinition[R, RegexWhen[R]]
  def when(pattern: Timeout.type): WhenDefinition[R, TimeoutWhen[R]]
  def when(pattern: EndOfFile.type): WhenDefinition[R, EndOfFileWhen[R]]

  def withBlock(block: DSL[R] => Unit): DSL[R]

  def send(text: String): DSL[R]
  def send(text: Match => String): DSL[R]
  def sendln(text: String): DSL[R]
  def sendln(text: Match => String): DSL[R]

  def returning(result: => R): DSL[R]
  def returning(result: Match => R): DSL[R]
  def returningExpect(result: => core.Expect[R]): DSL[R]
  def returningExpect(result: Match => core.Expect[R]): DSL[R]

  def exit(): DSL[R]
}
