package work.martins.simon.expect.core

import scala.util.matching.Regex.Match
import work.martins.simon.expect.StringUtils._

/**
 * @define regexWhen This action can only be added to a RegexWhen.
 * @define returningAction When this action is executed the result of evaluating `result` is returned by
 *         the current run of Expect.
 * @define moreThanOne If more than one returning action is added to a When only the last `result` will be returned.
 *                     Note however that every `ReturningAction` will still be executed.
 * @tparam W the type of When to which this action can be applied.
 */
trait Action[R, -W <: When[R]] {
  def structurallyEquals(other: Action[_, _]): Boolean = this match {
    case _: Send[_] => other.isInstanceOf[Send[_]]
    case _: SendWithRegex[_] => other.isInstanceOf[SendWithRegex[_]]
    case _: Returning[_] => other.isInstanceOf[Returning[_]]
    case _: ReturningWithRegex[_] => other.isInstanceOf[ReturningWithRegex[_]]
    case _: ReturningExpect[_] => other.isInstanceOf[ReturningExpect[_]]
    case _: ReturningExpectWithRegex[_] => other.isInstanceOf[ReturningExpectWithRegex[_]]
    case _: Exit[_] => other.isInstanceOf[Exit[_]]
    case _ => false
  }

  def map[T](f: R => T): Action[T, _ <: When[T]]
  def flatMap[T](f: R => Expect[T]): Action[T, _ <: When[T]]
}

/**
 * When this action is executed `text` will be sent to the stdIn of the underlying process.
 * @param text the text to send.
 */
case class Send[R](text: String) extends Action[R, When[R]] {
  override def toString: String = s"Send(${escape(text)})"

  def map[T](f: R => T): Action[T, When[T]] = new Send[T](text)
  def flatMap[T](f: (R) => Expect[T]): Action[T, When[T]] = new Send[T](text)
}
object Sendln {
  def apply[R](text: String): Send[R] = new Send(text + System.lineSeparator())
}

/**
 * When this action is executed the result of evaluating `text` will be sent to the stdIn of the underlying process.
 * This allows to send data to the process based on the regex Match.
 * $regexWhen
 * @param text the text to send.
 */
case class SendWithRegex[R](text: Match => String) extends Action[R, RegexWhen[R]] {
  def map[T](f: R => T): Action[T, RegexWhen[T]] = new SendWithRegex[T](text)
  def flatMap[T](f: R => Expect[T]): Action[T, RegexWhen[T]] = new SendWithRegex[T](text)
}
object SendlnWithRegex {
  def apply[R](text: Match => String): SendWithRegex[R] = new SendWithRegex(text.andThen(_ + System.lineSeparator()))
}

/**
 * $returningAction
 * $moreThanOne
 **/
case class Returning[R](result: () => R) extends Action[R, When[R]] {
  def map[T](f: R => T): Action[T, When[T]] = this.copy(() => f(result.apply()))
  def flatMap[T](f: R => Expect[T]): Action[T, When[T]] = new ReturningExpect[T](() => f(result.apply()))
}
/**
 * $returningAction
 * This allows to return data based on the regex Match.
 * $regexWhen
 * $moreThanOne
 */
case class ReturningWithRegex[R](result: Match => R) extends Action[R, RegexWhen[R]] {
  def map[T](f: R => T): Action[T, RegexWhen[T]] = this.copy(result andThen f)
  def flatMap[T](f: R => Expect[T]): Action[T, RegexWhen[T]] = new ReturningExpectWithRegex[T](result andThen f)
}

/**
 * When this action is executed:
 *
 * 1. The current run of Expect is terminated (like with an `Exit`) but its return value is discarded.
 * 2. `result` is evaluated to obtain the expect.
 * 3. The obtained expect is run with the same run context (timeout, charset, etc) as the terminated expect.
 * 4. The result obtained in the previous step becomes the result of the current expect (the terminated one).
 *
 * This works out as a special combination of an `Exit` with a `Returning`. Where the exit deallocates the
 * resources allocated by the current expect. And the result of the `Returning` is obtained from the result of
 * executing the received expect.
 *
 * Any action or expect block added after this will not be executed.
 */
case class ReturningExpect[R](result: () => Expect[R]) extends Action[R, When[R]] {
  def map[T](f: R => T): Action[T, When[T]] = this.copy(() => result.apply().map(f))
  def flatMap[T](f: R => Expect[T]): Action[T, When[T]] = this.copy(() => result.apply().flatMap(f))
}
/**
 * When this action is executed:
 *
 * 1. The current run of Expect is terminated (like with an `Exit`) but its return value is discarded.
 * 2. `result` is evaluated to obtain the expect.
 * 3. The obtained expect is run with the same run context (timeout, charset, etc) as the terminated expect.
 * 4. The result obtained in the previous step becomes the result of the current expect (the terminated one).
 *
 * This works out as a special combination of an `Exit` with a `Returning`. Where the exit deallocates the
 * resources allocated by the current expect. And the result of the `Returning` is obtained from the result of
 * executing the received expect.
 *
 * This allows to construct the Expect based on the regex Match.
 * $regexWhen
 * Any action or expect block added after this will not be executed.
 */
case class ReturningExpectWithRegex[R](result: Match => Expect[R]) extends Action[R, RegexWhen[R]] {
  def map[T](f: R => T): Action[T, RegexWhen[T]] = this.copy(result.andThen(_.map(f)))
  def flatMap[T](f: R => Expect[T]): Action[T, RegexWhen[T]] = this.copy(result.andThen(_.flatMap(f)))
}

/**
 * When this action is executed the current run of Expect is terminated causing it to return the
 * last value, if there is a ReturningAction, or the default value otherwise.
 * Any action or expect block added after this will not be executed.
 */
case class Exit[R]() extends Action[R, When[R]] {
  def map[T](f: R => T): Action[T, When[T]] = new Exit[T]
  def flatMap[T](f: R => Expect[T]): Action[T, When[T]] = new Exit[T]
}