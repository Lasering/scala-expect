package codes.simon.expect.core

import scala.util.matching.Regex.Match

/**
 * @define regexWhen This action can only be added to a RegexWhen.
 * @define returningAction When this action is executed the result of evaluating `result` is returned by
 *         the current run of Expect.
 * @define moreThanOne If more than one returning action is added to a When only the last `result` will be returned.
 *                     Note however that every `ReturningAction` will be executed.
 * @tparam W the type of When to which this action can be applied.
 */
trait Action[-W <: When[_]]

/**
 * When this action is executed `text` will be sent to the stdIn of the underlying process.
 * @param text the text to send.
 */
case class Send(text: String) extends Action[When[_]]
object Sendln {
  def apply(text: String) = new Send(text + System.lineSeparator())
}

/**
 * When this action is executed the result of evaluating `text` will be sent to the stdIn of the underlying process.
 * This allows to send data to the process based on the regex Match.
 * $regexWhen
 * @param text the text to send.
 */
case class SendWithRegex(text: Match => String) extends Action[RegexWhen[_]]
object SendlnWithRegex {
  def apply(text: Match => String) = new SendWithRegex(text.andThen(_ + System.lineSeparator()))
}

/**
 * $returningAction
 * $moreThanOne
 **/
case class Returning[R](result: () => R) extends Action[When[R]]
/**
 * returningAction
 * This allows to return data based on the regex Match.
 * $regexWhen
 * $moreThanOne
 */
case class ReturningWithRegex[R](result: Match => R) extends Action[RegexWhen[R]]

/**
 * When this action is executed:
 *
 * 1. The current run of Expect is terminated (like with an `Exit`) but its return value is discarded.
 * 2. `result` is evaluated to obtain the expect.
 * 3. The obtained `expect` is run with the same run context (timeout, charset, etc) as the terminated expect.
 * 4. The result obtained in the previous step becomes the result of the current expect (the terminated one).
 *
 * This works out as a special combination of an `Exit` with a `Returning`. Where the exit deallocates the
 * resources allocated by the current expect. And the result of the `Returning` is obtained from the result of
 * executing the received expect.
 *
 * Any action added after this one will not be executed.
 */
case class ReturningExpect[R](result: () => Expect[R]) extends Action[When[R]]
/**
 * When this action is executed:
 *
 * 1. The current run of Expect is terminated (like with an `Exit`) but its return value is discarded.
 * 2. `result` is evaluated to obtain the expect.
 * 3. The obtained `expect` is run with the same run context (timeout, charset, etc) as the terminated expect.
 * 4. The result obtained in the previous step becomes the result of the current expect (the terminated one).
 *
 * This works out as a special combination of an `Exit` with a `Returning`. Where the exit deallocates the
 * resources allocated by the current expect. And the result of the `Returning` is obtained from the result of
 * executing the received expect.
 *
 * This allows to construct the Expect based on the regex Match.
 * $regexWhen
 * Any action added after this one will not be executed.
 */
case class ReturningExpectWithRegex[R](result: Match => Expect[R]) extends Action[RegexWhen[R]]

/**
 * When this action is executed the current run of Expect is terminated causing it to return the
 * last value, if there is a ReturningAction, or the default value otherwise.
 * Any action added after this one will not be executed.
 */
case object Exit extends Action[When[_]]