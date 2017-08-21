package work.martins.simon.expect.core.actions

import scala.language.higherKinds

import work.martins.simon.expect.core.RunContext.Terminate
import work.martins.simon.expect.core._

/**
  * When this action is executed the current run of Expect is terminated causing it to return the
  * last value, if there is a ReturningAction, or the default value otherwise.
  *
  * Any action or expect block added after this will not be executed.
  */
final case class Exit[+R]() extends Action[R, When] {
  def run[RR >: R](when: When[RR], runContext: RunContext[RR]): RunContext[RR] = {
    runContext.copy(executionAction = Terminate)
  }

  //These methods just perform a cast because the type argument R is just used here,
  //so there isn't the need to allocate need objects.

  protected[expect] def map[T](f: R => T): Action[T, When] = this.asInstanceOf[Exit[T]]
  protected[expect] def flatMap[T](f: R => Expect[T]): Action[T, When] = this.asInstanceOf[Exit[T]]
  protected[expect] def transform[T](flatMapPF: R =/> Expect[T], mapPF: R =/> T): Action[T, When] = this.asInstanceOf[Exit[T]]

  def structurallyEquals[RR >: R, W[+X] <: When[X]](other: Action[RR, W]): Boolean = other.isInstanceOf[Exit[RR]]
}
