package work.martins.simon.expect.dsl

import scala.util.matching.Regex
import scala.util.matching.Regex.Match

import work.martins.simon.expect.core.{EndOfFile, Timeout}

trait DSL[R] {
  type DSLWithBlock = DSL[R] with Block[R]

  def expect: DSLWithBlock

  def expect(pattern: String): DSLWithBlock
  def expect(pattern: Regex): DSLWithBlock
  def expect(pattern: Timeout.type): DSLWithBlock
  def expect(pattern: EndOfFile.type): DSLWithBlock

  def when(pattern: String): DSLWithBlock
  def when(pattern: Regex): DSLWithBlock
  def when(pattern: EndOfFile.type): DSLWithBlock
  def when(pattern: Timeout.type): DSLWithBlock

  def withBlock(block: DSL[R] => Unit): DSL[R]

  def send(text: String): DSL[R]
  def send(text: Match => String): DSL[R]
  def sendln(text: String): DSL[R]
  def sendln(text: Match => String): DSL[R]

  def returning(result: => R): DSL[R]
  def returning(result: Match => R): DSL[R]

  def exit(): DSL[R]
}
