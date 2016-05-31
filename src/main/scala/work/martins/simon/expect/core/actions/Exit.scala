package work.martins.simon.expect.core.actions

import work.martins.simon.expect.core._

import scala.language.higherKinds

/**
  * When this action is executed the current run of Expect is terminated causing it to return the
  * last value, if there is a ReturningAction, or the default value otherwise.
  *
  * Any action or expect block added after this will not be executed.
  */
case class Exit[R]() extends Action[R, When] {
  def execute(when: When[R], process: RichProcess, intermediateResult: IntermediateResult[R]): IntermediateResult[R] = {
    intermediateResult.copy(executionAction = Terminate)
  }

  //These methods just perform a cast because the type argument R is just used here,
  //so there isn't the need to allocate need objects.

  protected[expect] def map[T](f: R => T): Action[T, When] = this.asInstanceOf[Exit[T]]
  protected[expect] def flatMap[T](f: R => Expect[T]): Action[T, When] = this.asInstanceOf[Exit[T]]
  protected[expect] def transform[T](mapPF: PartialFunction[R, T])(flatMapPF: PartialFunction[R, Expect[T]]): Action[T, When] = {
    this.asInstanceOf[Exit[T]]
  }

  def structurallyEquals[WW[X] <: When[X]](other: Action[R, WW]): Boolean = other.isInstanceOf[Exit[_]]
}
