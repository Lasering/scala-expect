package work.martins.simon.expect.dsl

import work.martins.simon.expect.core
import work.martins.simon.expect.fluent.{RegexWhen, When => FWhen}

import scala.util.matching.Regex.Match

class When[R, FW <: FWhen[R]](builder: Expect[R], when: FW) extends AbstractDefinition[R](builder) {
  private def addAction(block: FW => Unit): DSL[R] = {
    block(when)
    this
  }

  private def addRegexAction(block: RegexWhen[R] => Unit): DSL[R] = when match {
    case regexWhen: RegexWhen[R] =>
      block(regexWhen)
      this
    case _ =>
      throw new IllegalArgumentException("This action can only be invoked for RegexWhen")
  }

  override def send(text: String): DSL[R] = addAction(_.send(text))
  override def send(text: Match => String): DSL[R] = addRegexAction(_.send(text))

  override def sendln(text: String): DSL[R] = addAction(_.sendln(text))
  override def sendln(text: Match => String): DSL[R] = addRegexAction(_.sendln(text))

  override def returning(result: => R): DSL[R] = addAction(_.returning(result))
  override def returning(result: Match => R): DSL[R] = addRegexAction(_.returning(result))
  override def returningExpect(result: => core.Expect[R]): DSL[R] = addAction(_.returningExpect(result))
  override def returningExpect(result: Match => core.Expect[R]): DSL[R] = addRegexAction(_.returningExpect(result))

  override def exit(): DSL[R] = addAction(_.exit())

  def toCore: FW#CW[R] = when.toCore
}
