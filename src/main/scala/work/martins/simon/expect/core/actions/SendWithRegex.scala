package work.martins.simon.expect.core.actions

import work.martins.simon.expect.core._

import scala.language.higherKinds
import scala.util.matching.Regex.Match

object SendlnWithRegex {
  def apply[R](text: Match => String): SendWithRegex[R] = SendWithRegex(text.andThen(_ + System.lineSeparator()))
}

/**
  * When this action is executed the result of evaluating `text` will be sent to the stdIn of the underlying process.
  * This allows to send data to the process based on the regex Match.
  * $regexWhen
  *
  * @param text the text to send.
  */
final case class SendWithRegex[+R](text: Match => String) extends Action[R, RegexWhen] {
  def run[RR >: R](when: RegexWhen[RR], runContext: RunContext[RR]): RunContext[RR] = {
    val regexMatch = when.regexMatch(runContext.output)
    runContext.process.write(text(regexMatch))
    runContext
  }

  //These methods just perform a cast because the type argument R is just used here,
  //so there isn't the need to allocate need objects.

  protected[expect] def map[T](f: R => T): Action[T, RegexWhen] = this.asInstanceOf[SendWithRegex[T]]
  protected[expect] def flatMap[T](f: R => Expect[T]): Action[T, RegexWhen] = this.asInstanceOf[SendWithRegex[T]]
  protected[expect] def transform[T](flatMapPF: R =/> Expect[T], mapPF: R =/> T): Action[T, RegexWhen] = this.asInstanceOf[SendWithRegex[T]]

  def structurallyEquals[RR >: R, W[+X] <: RegexWhen[X]](other: Action[RR, W]): Boolean = other.isInstanceOf[SendWithRegex[RR]]
}
