package work.martins.simon.expect.dsl

import scala.util.matching.Regex.Match

import work.martins.simon.expect.fluent.{RegexWhen, When}

class WhenDefinition[R, W <: When[R]](builder: Expect[R], when: W) extends AbstractDefinition[R](builder) {
  def addAction(block: W => Unit): DSL[R] = {
    block(when)
    this
  }

  def addRegexAction(block: RegexWhen[R] => Unit): DSL[R] = when match {
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

  override def exit(): DSL[R] = addAction(_.exit())
}
