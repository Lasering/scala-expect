package codes.simon.expect.dsl

import codes.simon.expect.fluent.{RegexWhen, When}

import scala.util.matching.Regex.Match

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
      throw new IllegalArgumentException("$action can only be invoked for RegexWhen")
  }

  override def send(text: String) = addAction(_.send(text))
  override def send(text: Match => String) = addRegexAction(_.send(text))

  override def sendln(text: String) = addAction(_.sendln(text))
  override def sendln(text: Match => String) = addRegexAction(_.sendln(text))

  override def returning(result: => R) = addAction(_.returning(result))
  override def returning(result: Match => R) = addRegexAction(_.returning(result))

  override def exit() = addAction(_.exit())
}
