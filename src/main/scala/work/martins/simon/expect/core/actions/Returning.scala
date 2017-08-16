package work.martins.simon.expect.core.actions

import scala.language.higherKinds

import work.martins.simon.expect.core.RunContext.ChangeToNewExpect
import work.martins.simon.expect.core._

sealed trait AbstractReturning[+WR] extends Action[WR, When] {
  protected[expect] override def map[T](f: WR => T): AbstractReturning[T]
  protected[expect] override def flatMap[T](f: WR => Expect[T]): AbstractReturning[T]
  protected[expect] override def transform[T](flatMapPF: WR =/> Expect[T], mapPF: WR =/> T): AbstractReturning[T]
}

// Returning cannot be declared:
//   case class Returning(result: => R) extends AbstractReturning[R]
// because `val' parameters may not be call-by-name. To solve this problem we declared Returning with result
// type as Unit => R. This implies we do not need to implement the sensitive flag like in Send.

object Returning {
  // => R and () => R have the same type after erasure: Function0. Which means this apply cannot be declared
  //def apply[R](result: () => R): Returning[R] = new Returning((u: Unit) => result())
  def apply[R](result: => R): Returning[R] = new Returning(_ => result)
}
/**
  * $returningAction
  * $moreThanOne
  **/
case class Returning[+R](result: Unit => R) extends AbstractReturning[R] {
  def run[RR >: R](when: When[RR], runContext: RunContext[RR]): RunContext[RR] = {
    runContext.copy(value = result(()))
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
      case r => pfNotDefined[R, AbstractReturning[T]](r)
    }
    ActionReturningAction(this, computeAction)
  }

  def structurallyEquals[RR >: R, WW[X] <: When[X]](other: Action[RR, WW]): Boolean = other.isInstanceOf[Returning[RR]]
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
case class ReturningExpect[+R](result: Unit => Expect[R]) extends AbstractReturning[R] {
  def run[RR >: R](when: When[RR], runContext: RunContext[RR]): RunContext[RR] = {
    val newExpect = result(())
    runContext.copy(executionAction = ChangeToNewExpect(newExpect))
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

  def structurallyEquals[RR >: R, WW[X] <: When[X]](other: Action[RR, WW]): Boolean = this.isInstanceOf[ReturningExpect[RR]]
}

case class ActionReturningAction[R, +T](parent: Returning[R], resultAction: R => AbstractReturning[T]) extends AbstractReturning[T] {
  def run[TT >: T](when: When[TT], runContext: RunContext[TT]): RunContext[TT] = {
    val parentResult: R = parent.result(())
    resultAction(parentResult).run(when, runContext)
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

  def structurallyEquals[TT >: T, WW[X] <: When[X]](other: Action[TT, WW]): Boolean = other.isInstanceOf[ActionReturningAction[R, TT]]
}