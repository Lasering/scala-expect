package codes.simon.expect.core

import scala.util.matching.Regex.Match

trait Action[-W <: When]

/**
 * When this action is executed `text` will be sent to the stdIn of the underlying process.
 * @param text the text to send.
 */
case class SendAction(text: String) extends Action[When]

/**
 * When this action is executed the result of executing `text` will be sent to the stdIn of the underlying process.
 * This allows to send data to the process based on the regex Match.
 * This action can only be added to a RegexWhen.
 * @param text the text to send.
 */
case class SendWithRegexAction(text: Match => String) extends Action[RegexWhen]

/**
 * When this action is executed `result` is returned.
 * If more than one `ReturningAction` is executed only the last `result` will be returned.
 * @param result the result to return.
 */
case class ReturningAction[R](result: R) extends Action[When]

/**
 * When this action is executed the result of executing `result` is returned.
 * If more than one `ReturningAction` is executed only the last `result` will be returned.
 * This allows to return data based on the regex Match.
 * This action can only be added to a RegexWhen.
 * @param result the result to return.
 */
case class ReturningWithRegexAction[R](result: Match => R) extends Action[RegexWhen]

/**
 * When this action is executed the current run of Expect is terminated causing it to return the
 * last value (if there is a ReturningAction).
 * Any action added after this one will not be executed.
 */
case object ExitAction extends Action[When]