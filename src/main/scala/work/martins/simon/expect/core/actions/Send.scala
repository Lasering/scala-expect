package work.martins.simon.expect.core.actions

import work.martins.simon.expect.StringUtils._
import work.martins.simon.expect.core.{Expect, RunContext, When}
import scala.language.higherKinds
import scala.util.matching.Regex.Match

object Sendln
  def apply[R](text: String, sensitive: Boolean = false): Send[R] = Send(text + System.lineSeparator(), sensitive)
  def apply[R](text: Match => String): SendWithRegex[R] = SendWithRegex(text.andThen(_ + System.lineSeparator()))

object Send
  def apply[R](text: String, sensitive: Boolean = false): Send[R] = new Send(text, sensitive)
  def apply[R](text: Match => String): SendWithRegex[R] = SendWithRegex(text)

/**
  * When this action is executed `text` will be sent to the stdIn of the underlying process.
  *
  * @param text the text to send.
  * @param sensitive whether to ommit `text` in `toString`
  */
final case class Send[+R](text: String, sensitive: Boolean = false) extends Action[R, When]
  def run[RR >: R](when: When[RR], runContext: RunContext[RR]): RunContext[RR] =
    runContext.process.write(text)
    runContext

  //These methods just perform a cast because the type argument R is just used here,
  //so there isn't the need to allocate need objects.

  def map[T](f: R => T): Action[T, When] = this.asInstanceOf[Send[T]]
  def flatMap[T](f: R => Expect[T]): Action[T, When] = this.asInstanceOf[Send[T]]
  def transform[T](flatMapPF: R /=> Expect[T], mapPF: R /=> T): Action[T, When] = this.asInstanceOf[Send[T]]

  def structurallyEquals[RR >: R, W[+X] <: When[X]](other: Action[RR, W]): Boolean = other.isInstanceOf[Send[RR]]

  override def toString: String = s"Send(${if sensitive then "<omitted sensitive output>" else escape(text) })"
