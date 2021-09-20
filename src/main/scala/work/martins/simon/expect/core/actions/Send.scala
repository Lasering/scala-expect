package work.martins.simon.expect.core.actions

import scala.util.matching.Regex.Match
import work.martins.simon.expect./=>
import work.martins.simon.expect.StringUtils.*
import work.martins.simon.expect.core.{Expect, RunContext, When, RegexWhen}

object Sendln:
  def apply(text: String, sensitive: Boolean = false): Send = Send(text + System.lineSeparator(), sensitive)
  def apply(text: Match => String): SendWithRegex = SendWithRegex(text.andThen(_ + System.lineSeparator()))

object Send:
  def apply(text: String, sensitive: Boolean = false): Send = new Send(text, sensitive)
  def apply(text: Match => String): SendWithRegex = SendWithRegex(text)

/**
  * When this action is executed `text` will be sent to the stdIn of the underlying process.
  *
  * @param text the text to send.
  * @param sensitive whether to ommit `text` in `toString`
  */
final case class Send(text: String, sensitive: Boolean = false) extends NonProducingAction[When]:
  def run[RR >: Nothing](when: When[RR], runContext: RunContext[RR]): RunContext[RR] =
    runContext.process.write(text)
    runContext
  
  def structurallyEquals[RR >: Nothing](other: Action[RR, ?]): Boolean = other.isInstanceOf[Send]
  
  override def toString: String = s"Send(${if sensitive then "<omitted sensitive output>" else escape(text) })"

/**
  * When this action is executed the result of evaluating `text` will be sent to the stdIn of the underlying process.
  * This allows to send data to the process based on the regex Match.
  * $regexWhen
  *
  * @param text the text to send.
  */
final case class SendWithRegex(text: Match => String) extends NonProducingAction[RegexWhen]:
  def run[RR >: Nothing](when: RegexWhen[RR], runContext: RunContext[RR]): RunContext[RR] =
    val regexMatch = when.regexMatch(runContext.output)
    runContext.process.write(text(regexMatch))
    runContext
  
  def structurallyEquals[RR >: Nothing](other: Action[RR, ?]): Boolean = other.isInstanceOf[SendWithRegex]