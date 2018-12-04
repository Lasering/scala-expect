package work.martins.simon.expect.core.actions

import work.martins.simon.expect.core.RunContext.ChangeToNewExpect
import work.martins.simon.expect.core._

import scala.language.higherKinds
import scala.util.matching.Regex.Match

sealed trait AbstractReturning[+WR] extends Action[WR, When] {
  override def map[T](f: WR => T): AbstractReturning[T]
  override def flatMap[T](f: WR => Expect[T]): AbstractReturning[T]
  override def transform[T](flatMapPF: WR /=> Expect[T], mapPF: WR /=> T): AbstractReturning[T]
}

// Returning cannot be declared:
//   case class Returning(result: => R) extends AbstractReturning[R]
// because `val' parameters may not be call-by-name. To solve this problem we declared Returning with result
// type as Unit => R. This implies we do not need to implement the sensitive flag like in Send.

object Returning {
  // Scala auto generates applys of private constructors of case classes. We do not want that.
  // So we define it just to make it private. We use it just for the coverage.
  private def apply[R](result: Unit => R): Returning[R] = new Returning(result)
  def apply[R](result: => R): Returning[R] = Returning(_ => result)
  def apply[R](result: Match => R): ReturningWithRegex[R] = ReturningWithRegex(result)
}

/**
  * $returningAction
  * $moreThanOne
  **/
final case class Returning[+R] private (result: Unit => R) extends AbstractReturning[R] {
  // The constructor is Unit => R so it does not collide with the apply.
  
  def run[RR >: R](when: When[RR], runContext: RunContext[RR]): RunContext[RR] = {
    runContext.copy(value = result(()))
  }

  def map[T](f: R => T): AbstractReturning[T] = {
    this.copy(result andThen f)
  }
  def flatMap[T](f: R => Expect[T]): AbstractReturning[T] = {
    ReturningExpect(f(result(())))
  }
  def transform[T](flatMapPF: R /=> Expect[T], mapPF: R /=> T): AbstractReturning[T] = {
    val computeAction: R => AbstractReturning[T] = {
      // We cannot use the map/flatMap because if we did the returning result would be ran twice in the ActionReturningAction:
      //   · once inside the execute which invokes parent.result
      //   · and another when the action returned by the ActionReturningAction is ran
      case r if flatMapPF.isDefinedAt(r) => ReturningExpect(flatMapPF(r))
      case r if mapPF.isDefinedAt(r) => this.copy(_ => mapPF(r))
      case r => pfNotDefined[R, AbstractReturning[T]](r)
    }
    ActionReturningAction(this, computeAction)
  }

  def structurallyEquals[RR >: R, W[+X] <: When[X]](other: Action[RR, W]): Boolean = other.isInstanceOf[Returning[RR]]
}


object ReturningExpect {
  // See the explanation in object Returning as to why this apply is here.
  private def apply[R](result: Unit => Expect[R]): ReturningExpect[R] = new ReturningExpect(result)
  def apply[R](result: => Expect[R]): ReturningExpect[R] = ReturningExpect(_ => result)
  def apply[R](result: Match => Expect[R]): ReturningExpectWithRegex[R] = ReturningExpectWithRegex(result)
}

/**
  * When this action is executed:
  *
  * 1. The current run of Expect is terminated (like with an `Exit`) but its return value is discarded.
  * 2. `result` is evaluated to obtain the expect.
  * 3. The obtained expect is run.
  * 4. The result obtained in the previous step becomes the result of the current expect (the terminated one).
  *
  * This works out as a special combination of `Exit` and `Returning`. Where the exit deallocates the
  * resources allocated by the current expect. And the result of the `Returning` is obtained from the result of
  * executing the received expect.
  *
  * Any action or expect block added after this will not be executed.
  */
final case class ReturningExpect[+R] private (result: Unit => Expect[R]) extends AbstractReturning[R] {
  // The constructor is Unit => R so it does not collide with the apply.

  def run[RR >: R](when: When[RR], runContext: RunContext[RR]): RunContext[RR] = {
    val newExpect = result(())
    runContext.copy(executionAction = ChangeToNewExpect(newExpect))
  }

  def map[T](f: R => T): AbstractReturning[T] = {
    this.copy(result.andThen(_.map(f)))
  }
  def flatMap[T](f: R => Expect[T]): AbstractReturning[T] = {
    this.copy(result.andThen(_.flatMap(f)))
  }
  def transform[T](flatMapPF: R /=> Expect[T], mapPF: R /=> T): AbstractReturning[T] = {
    this.copy(result.andThen(_.transform(flatMapPF, mapPF)))
  }

  def structurallyEquals[RR >: R, W[+X] <: When[X]](other: Action[RR, W]): Boolean = this.isInstanceOf[ReturningExpect[RR]]
}

final case class ActionReturningAction[R, +T](parent: Returning[R], resultAction: R => AbstractReturning[T]) extends AbstractReturning[T] {
  def run[TT >: T](when: When[TT], runContext: RunContext[TT]): RunContext[TT] = {
    val parentResult: R = parent.result(())
    resultAction(parentResult).run(when, runContext)
  }

  def map[U](f: T => U): AbstractReturning[U] = {
    this.copy(parent, resultAction.andThen(_.map(f)))
  }
  def flatMap[U](f: T => Expect[U]): AbstractReturning[U] = {
    this.copy(parent, resultAction.andThen(_.flatMap(f)))
  }
  def transform[U](flatMapPF: T /=> Expect[U], mapPF: T /=> U): AbstractReturning[U] = {
    this.copy(parent, resultAction.andThen(_.transform(flatMapPF, mapPF)))
  }

  def structurallyEquals[TT >: T, W[+X] <: When[X]](other: Action[TT, W]): Boolean = other.isInstanceOf[ActionReturningAction[R, TT]]
}