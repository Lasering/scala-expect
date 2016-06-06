package work.martins.simon.expect.core.actions

import work.martins.simon.expect.StringUtils._
import work.martins.simon.expect.core.{Expect, IntermediateResult, RichProcess, When}

import scala.language.higherKinds

object Sendln {
  def apply[R](text: String): Send[R] = new Send(text + System.lineSeparator())
}

/**
  * When this action is executed `text` will be sent to the stdIn of the underlying process.
  *
  * @param text the text to send.
  */
case class Send[R](text: String) extends Action[R, When] {
  def execute(when: When[R], process: RichProcess, intermediateResult: IntermediateResult[R]): IntermediateResult[R] = {
    process.print(text)
    intermediateResult
  }

  //These methods just perform a cast because the type argument R is just used here,
  //so there isn't the need to allocate need objects.

  protected[expect] def map[T](f: R => T): Action[T, When] = {
    this.asInstanceOf[Send[T]]
  }
  protected[expect] def flatMap[T](f: R => Expect[T]): Action[T, When] = {
    this.asInstanceOf[Send[T]]
  }
  protected[expect] def transform[T](flatMapPF: PartialFunction[R, Expect[T]])(mapPF: PartialFunction[R, T]): Action[T, When] = {
    this.asInstanceOf[Send[T]]
  }

  def structurallyEquals[WW[X] <: When[X]](other: Action[R, WW]): Boolean = other.isInstanceOf[Send[_]]

  override def toString: String = s"Send(${escape(text)})"
}
