package work.martins.simon.expect.core.actions

import scala.language.higherKinds

import work.martins.simon.expect.core.Context.ChangeToNewExpect
import work.martins.simon.expect.core._

sealed trait AbstractReturning[WR] extends Action[WR, When] {
  protected[expect] override def map[T](f: WR => T): AbstractReturning[T]
  protected[expect] override def flatMap[T](f: WR => Expect[T]): AbstractReturning[T]
  protected[expect] override def transform[T](flatMapPF: WR =/> Expect[T], mapPF: WR =/> T): AbstractReturning[T]
}

object Returning {
  //def apply[R](result: () => R): Returning[R] = new Returning((u: Unit) => result())
  def apply[R](result: => R): Returning[R] = new Returning(_ => result)
}
/**
  * $returningAction
  * $moreThanOne
  **/
case class Returning[R](result: Unit => R) extends AbstractReturning[R] {
  def execute(when: When[R], process: RichProcess, context: Context[R]): Context[R] = {
    context.copy(value = result(()))
  }

  protected[expect] def map[T](f: R => T): AbstractReturning[T] = {
    this.copy(result andThen f)
  }
  protected[expect] def flatMap[T](f: R => Expect[T]): AbstractReturning[T] = {
    ReturningExpect(result andThen f)
  }
  protected[expect] def transform[T](flatMapPF: R =/> Expect[T], mapPF: R =/> T): AbstractReturning[T] = {
    val computeAction: R => AbstractReturning[T] = {
      // We cannot use the map/flatMap because if we did the returning result would be ran twice in the ActionReturningAction:
      //   · once inside the execute which invokes parent.result
      //   · and another when the action returned by the ActionReturningAction is ran
      case r if flatMapPF.isDefinedAt(r) => ReturningExpect(_ => flatMapPF(r))
      case r if mapPF.isDefinedAt(r) => this.copy(_ => mapPF(r))
      case r => pfNotDefined[AbstractReturning[T]](r)
    }
    ActionReturningAction(this, computeAction)
  }

  def structurallyEquals[WW[X] <: When[X]](other: Action[R, WW]): Boolean = other.isInstanceOf[Returning[R]]
}


object ReturningExpect {
  //def apply[R](result: () => Expect[R]): ReturningExpect[R] = new ReturningExpect((u: Unit) => result())
  def apply[R](result: => Expect[R]): ReturningExpect[R] = new ReturningExpect(_ => result)
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
case class ReturningExpect[R](result: Unit => Expect[R]) extends AbstractReturning[R] {
  def execute(when: When[R], process: RichProcess, context: Context[R]): Context[R] = {
    val newExpect = result(())
    context.copy(executionAction = ChangeToNewExpect(newExpect))
  }

  protected[expect] def map[T](f: R => T): AbstractReturning[T] = {
    this.copy(result.andThen(_.map(f)))
  }
  protected[expect] def flatMap[T](f: R => Expect[T]): AbstractReturning[T] = {
    this.copy(result.andThen(_.flatMap(f)))
  }
  protected[expect] def transform[T](flatMapPF: R =/> Expect[T], mapPF: R =/> T): AbstractReturning[T] = {
    this.copy(result.andThen(_.transform(flatMapPF, mapPF)))
  }

  def structurallyEquals[WW[X] <: When[X]](other: Action[R, WW]): Boolean = this.isInstanceOf[ReturningExpect[R]]
}

case class ActionReturningAction[R, T](parent: Returning[R], resultAction: R => AbstractReturning[T]) extends AbstractReturning[T] {
  def execute(when: When[T], process: RichProcess, context: Context[T]): Context[T] = {
    val parentResult: R = parent.result(())
    resultAction(parentResult).execute(when, process, context)
  }

  protected[expect] def map[U](f: T => U): AbstractReturning[U] = {
    this.copy(parent, resultAction.andThen(_.map(f)))
  }
  protected[expect] def flatMap[U](f: T => Expect[U]): AbstractReturning[U] = {
    this.copy(parent, resultAction.andThen(_.flatMap(f)))
  }
  protected[expect] def transform[U](flatMapPF: T =/> Expect[U], mapPF: T =/> U): AbstractReturning[U] = {
    this.copy(parent, resultAction.andThen(_.transform(flatMapPF, mapPF)))
  }

  def structurallyEquals[WW[X] <: When[X]](other: Action[T, WW]): Boolean = other.isInstanceOf[ActionReturningAction[R, T]]
}