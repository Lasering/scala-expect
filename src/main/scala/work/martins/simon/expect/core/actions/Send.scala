package work.martins.simon.expect.core.actions

import scala.language.higherKinds

import work.martins.simon.expect.StringUtils._
import work.martins.simon.expect.core.{Context, Expect, RichProcess, When}

object Sendln {
  def apply[R](text: String): Send[R] = new Send(text + System.lineSeparator())
}

/**
  * When this action is executed `text` will be sent to the stdIn of the underlying process.
  *
  * @param text the text to send.
  */
final case class Send[R](text: String) extends Action[R, When] {
  def execute(when: When[R], process: RichProcess, context: Context[R]): Context[R] = {
    process.print(text)
    context
  }

  //These methods just perform a cast because the type argument R is just used here,
  //so there isn't the need to allocate need objects.

  protected[expect] def map[T](f: R => T): Action[T, When] = this.asInstanceOf[Send[T]]
  protected[expect] def flatMap[T](f: R => Expect[T]): Action[T, When] = this.asInstanceOf[Send[T]]
  protected[expect] def transform[T](flatMapPF: R =/> Expect[T], mapPF: R =/> T): Action[T, When] = this.asInstanceOf[Send[T]]

  def structurallyEquals[WW[X] <: When[X]](other: Action[R, WW]): Boolean = other.isInstanceOf[Send[R]]

  override def toString: String = s"Send(${escape(text)})"
}
