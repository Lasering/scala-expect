package work.martins.simon.expect.core.actions

import scala.language.higherKinds
import scala.util.matching.Regex.Match

import work.martins.simon.expect.core._

object SendlnWithRegex {
  def apply[R](text: Match => String): SendWithRegex[R] = new SendWithRegex(text.andThen(_ + System.lineSeparator()))
}

/**
  * When this action is executed the result of evaluating `text` will be sent to the stdIn of the underlying process.
  * This allows to send data to the process based on the regex Match.
  * $regexWhen
  *
  * @param text the text to send.
  */
final case class SendWithRegex[R](text: Match => String) extends Action[R, RegexWhen] {
  def execute(when: RegexWhen[R], process: RichProcess, context: Context[R]): Context[R] = {
    val regexMatch = when.regexMatch(context.output)
    process.print(text(regexMatch))
    context
  }

  //These methods just perform a cast because the type argument R is just used here,
  //so there isn't the need to allocate need objects.

  protected[expect] def map[T](f: R => T): Action[T, RegexWhen] = this.asInstanceOf[SendWithRegex[T]]
  protected[expect] def flatMap[T](f: R => Expect[T]): Action[T, RegexWhen] = this.asInstanceOf[SendWithRegex[T]]
  protected[expect] def transform[T](flatMapPF: R =/> Expect[T], mapPF: R =/> T): Action[T, RegexWhen] = this.asInstanceOf[SendWithRegex[T]]

  def structurallyEquals[WW[X] <: RegexWhen[X]](other: Action[R, WW]): Boolean = other.isInstanceOf[SendWithRegex[R]]
}
