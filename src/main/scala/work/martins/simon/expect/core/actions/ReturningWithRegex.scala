package work.martins.simon.expect.core.actions

import scala.language.higherKinds
import scala.util.matching.Regex.Match

import work.martins.simon.expect.core.RunContext.ChangeToNewExpect
import work.martins.simon.expect.core.{RegexWhen, _}

sealed trait AbstractReturningWithRegex[+WR] extends Action[WR, RegexWhen] {
  protected[expect] override def map[T](f: WR => T): AbstractReturningWithRegex[T]
  protected[expect] override def flatMap[T](f: WR => Expect[T]): AbstractReturningWithRegex[T]
  protected[expect] override def transform[T](flatMapPF: WR =/> Expect[T], mapPF: WR =/> T): AbstractReturningWithRegex[T]
}

/**
  * $returningAction
  * This allows to return data based on the regex Match.
  * $regexWhen
  * $moreThanOne
  */
case class ReturningWithRegex[+R](result: Match => R) extends AbstractReturningWithRegex[R] {
  def run[RR >: R](when: RegexWhen[RR], runContext: RunContext[RR]): RunContext[RR] = {
    val regexMatch = when.regexMatch(runContext.output)
    runContext.copy(value = result(regexMatch))
  }

  protected[expect] def map[T](f: R => T): AbstractReturningWithRegex[T] = {
    this.copy(result andThen f)
  }
  protected[expect] def flatMap[T](f: R => Expect[T]): AbstractReturningWithRegex[T] = {
    ReturningExpectWithRegex(result andThen f)
  }
  protected[expect] def transform[T](flatMapPF: R =/> Expect[T], mapPF: R =/> T): AbstractReturningWithRegex[T] = {
    val computeAction: R => AbstractReturningWithRegex[T] = {
      //We cannot invoke map/flatMap, because if we did the returning result would be ran twice in the ActionReturningAction:
      //Once inside the execute which invokes parent.result
      //And another when the action returned by the ActionReturningAction is ran
      case r if flatMapPF.isDefinedAt(r) => ReturningExpectWithRegex(_ => flatMapPF(r))
      case r if mapPF.isDefinedAt(r) => this.copy(_ => mapPF(r))
      case r => pfNotDefined[R, AbstractReturningWithRegex[T]](r)
    }
    ActionReturningActionWithRegex(this, computeAction)
  }

  def structurallyEquals[RR >: R, WW[X] <: RegexWhen[X]](other: Action[RR, WW]): Boolean = other.isInstanceOf[ReturningWithRegex[RR]]
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
  * This allows to construct the Expect based on the regex Match.
  * $regexWhen
  * Any action or expect block added after this will not be executed.
  */
case class ReturningExpectWithRegex[+R](result: Match => Expect[R]) extends AbstractReturningWithRegex[R] {
  def run[RR >: R](when: RegexWhen[RR], runContext: RunContext[RR]): RunContext[RR] = {
    val regexMatch = when.regexMatch(runContext.output)
    val expect = result(regexMatch)
    runContext.copy(executionAction = ChangeToNewExpect(expect))
  }

  protected[expect] def map[T](f: R => T): AbstractReturningWithRegex[T] = {
    this.copy(result.andThen(_.map(f)))
  }
  protected[expect] def flatMap[T](f: R => Expect[T]): AbstractReturningWithRegex[T] = {
    this.copy(result.andThen(_.flatMap(f)))
  }
  protected[expect] def transform[T](flatMapPF: R =/> Expect[T], mapPF: R =/> T): AbstractReturningWithRegex[T] = {
    this.copy(result.andThen(_.transform(flatMapPF, mapPF)))
  }

  def structurallyEquals[RR >: R, WW[X] <: RegexWhen[X]](other: Action[RR, WW]): Boolean = other.isInstanceOf[ReturningExpectWithRegex[RR]]
}

case class ActionReturningActionWithRegex[R, +T](parent: ReturningWithRegex[R], resultAction: R => AbstractReturningWithRegex[T])
  extends AbstractReturningWithRegex[T] {

  def run[TT >: T](when: RegexWhen[TT], runContext: RunContext[TT]): RunContext[TT] = {
    val regexMatch = when.regexMatch(runContext.stdOutOutput)
    val parentResult: R = parent.result(regexMatch)
    resultAction(parentResult).run(when, runContext)
  }

  protected[expect] def map[U](f: T => U): AbstractReturningWithRegex[U] = {
    this.copy(parent, resultAction.andThen(_.map(f)))
  }
  protected[expect] def flatMap[U](f: T => Expect[U]): AbstractReturningWithRegex[U] = {
    this.copy(parent, resultAction.andThen(_.flatMap(f)))
  }
  protected[expect] def transform[U](flatMapPF: T =/> Expect[U], mapPF: T =/> U): AbstractReturningWithRegex[U] = {
    this.copy(parent, resultAction.andThen(_.transform(flatMapPF, mapPF)))
  }

  def structurallyEquals[TT >: T, WW[X] <: RegexWhen[X]](other: Action[TT, WW]): Boolean = other.isInstanceOf[ActionReturningActionWithRegex[R, TT]]
}