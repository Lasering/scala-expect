package work.martins.simon.expect.core.actions

import work.martins.simon.expect.StringUtils._
import work.martins.simon.expect.core.{RunContext, Expect, When}

import scala.language.higherKinds

object Sendln {
  def apply[R](text: String, sensitive: Boolean = false): Send[R] = Send(text + System.lineSeparator(), sensitive)
}

/**
  * When this action is executed `text` will be sent to the stdIn of the underlying process.
  *
  * @param text the text to send.
  */
final case class Send[+R](text: String, sensitive: Boolean = false) extends Action[R, When] {
  def run[RR >: R](when: When[RR], runContext: RunContext[RR]): RunContext[RR] = {
    runContext.process.write(text)
    runContext
  }

  //These methods just perform a cast because the type argument R is just used here,
  //so there isn't the need to allocate need objects.

  protected[expect] def map[T](f: R => T): Action[T, When] = this.asInstanceOf[Send[T]]
  protected[expect] def flatMap[T](f: R => Expect[T]): Action[T, When] = this.asInstanceOf[Send[T]]
  protected[expect] def transform[T](flatMapPF: R =/> Expect[T], mapPF: R =/> T): Action[T, When] = this.asInstanceOf[Send[T]]

  def structurallyEquals[RR >: R, W[+X] <: When[X]](other: Action[RR, W]): Boolean = other.isInstanceOf[Send[RR]]

  override def toString: String = if (sensitive) {
    s"Send(<omitted sensitive output>)"
  } else {
    s"Send(${escape(text)})"
  }
}
