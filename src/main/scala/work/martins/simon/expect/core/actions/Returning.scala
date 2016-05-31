package work.martins.simon.expect.core.actions

import work.martins.simon.expect.core._
import scala.language.higherKinds

sealed trait AbstractReturning[R, WR] extends Action[WR, When] {
  def result: Unit => R

  protected[expect] override def map[T](f: WR => T): AbstractReturning[_, T]
  protected[expect] override def flatMap[T](f: WR => Expect[T]): AbstractReturning[_, T]
  protected[expect] override def transform[T](mapPF: PartialFunction[WR, T])(flatMapPF: PartialFunction[WR, Expect[T]]): AbstractReturning[_, T]
}

object Returning {
  //def apply[R](result: () => R): Returning[R] = new Returning((u: Unit) => result())
  def apply[R](result: => R): Returning[R] = new Returning((u: Unit) => result)
}
/**
  * $returningAction
  * $moreThanOne
  **/
case class Returning[R](result: Unit => R) extends AbstractReturning[R, R] {
  def execute(when: When[R], process: RichProcess, intermediateResult: IntermediateResult[R]): IntermediateResult[R] = {
    intermediateResult.copy(value = result(()))
  }

  protected[expect] def map[T](f: R => T): AbstractReturning[_, T] = {
    this.copy(result andThen f)
  }
  protected[expect] def flatMap[T](f: R => Expect[T]): AbstractReturning[_, T] = {
    ReturningExpect(result andThen f)
  }
  protected[expect] def transform[T](mapPF: PartialFunction[R, T])(flatMapPF: PartialFunction[R, Expect[T]]): AbstractReturning[_, T] = {
    val computeAction: R => AbstractReturning[_, T] = {
      //FIXME: is there any way of implementing this without the double evaluation of pattern matchers and guards?
      //the double evaluation occurs in isDefinedAt and the apply

      //We cannot invoke map/flatMap, because if we did the returning result would be ran twice in the ActionReturningAction:
      //Once inside the execute which invokes parent.result
      //And another when the action returned by the ActionReturningAction is ran

      case r if mapPF.isDefinedAt(r) => this.copy(_ => mapPF(r))
      case r if flatMapPF.isDefinedAt(r) => ReturningExpect(_ => flatMapPF(r))
      case r => pfNotDefined[AbstractReturning[_, T]]("transform")(r)
    }
    new ActionReturningAction(this, computeAction)
  }

  def structurallyEquals[WW[X] <: When[X]](other: Action[R, WW]): Boolean = other.isInstanceOf[Returning[_]]
}


object ReturningExpect {
  //def apply[R](result: () => Expect[R]): ReturningExpect[R] = new ReturningExpect((u: Unit) => result())
  def apply[R](result: => Expect[R]): ReturningExpect[R] = new ReturningExpect((u: Unit) => result)
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
case class ReturningExpect[R](result: Unit => Expect[R]) extends AbstractReturning[Expect[R], R] {
  def execute(when: When[R], process: RichProcess, intermediateResult: IntermediateResult[R]): IntermediateResult[R] = {
    val newExpect = result(())
    intermediateResult.copy(executionAction = ChangeToNewExpect(newExpect))
  }

  protected[expect] def map[T](f: R => T): AbstractReturning[_, T] = {
    this.copy(result.andThen(_.map(f)))
  }
  protected[expect] def flatMap[T](f: R => Expect[T]): AbstractReturning[_, T] = {
    this.copy(result.andThen(_.flatMap(f)))
  }
  protected[expect] def transform[T](mapPF: PartialFunction[R, T])(flatMapPF: PartialFunction[R, Expect[T]]): AbstractReturning[_, T] = {
    this.copy(result.andThen(_.transform(mapPF)(flatMapPF)))
  }

  def structurallyEquals[WW[X] <: When[X]](other: Action[R, WW]): Boolean = this.isInstanceOf[ReturningExpect[_]]
}

case class ActionReturningAction[R, T](parent: Returning[R], resultAction: R => AbstractReturning[_, T]) extends AbstractReturning[Nothing, T] {
  def result: Unit => Nothing = _ => throw new IllegalArgumentException("no can do")

  def execute(when: When[T], process: RichProcess, intermediateResult: IntermediateResult[T]): IntermediateResult[T] = {
    val parentResult: R = parent.result(())
    resultAction(parentResult).execute(when, process, intermediateResult)
  }

  protected[expect] def map[U](f: T => U): AbstractReturning[_, U] = {
    this.copy(parent, resultAction.andThen(_.map(f)))
  }
  protected[expect] def flatMap[U](f: T => Expect[U]): AbstractReturning[_, U] = {
    this.copy(parent, resultAction.andThen(_.flatMap(f)))
  }
  protected[expect] def transform[U](mapPF: PartialFunction[T, U])(flatMapPF: PartialFunction[T, Expect[U]]): AbstractReturning[_, U] = {
    def toU(r: AbstractReturning[_, T]): AbstractReturning[_, U] = r match {
      case r: Returning[T] => r.transform(mapPF)(flatMapPF) //stop case
      case r: ReturningExpect[T] => r.transform(mapPF)(flatMapPF) //stop case
      case r => r.transform(mapPF)(flatMapPF) //recursive call
    }
    this.copy(parent, resultAction andThen toU)
  }

  def structurallyEquals[WW[X] <: When[X]](other: Action[T, WW]): Boolean = other.isInstanceOf[ActionReturningAction[_, _]]
}