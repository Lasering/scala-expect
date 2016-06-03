package work.martins.simon.expect.core.actions

import work.martins.simon.expect.core.{RegexWhen, _}
import scala.util.matching.Regex.Match
import scala.language.higherKinds


trait AbstractReturningWithRegex[R, WR] extends Action[WR, RegexWhen] {
  def result: Match => R

  protected[expect] override def map[T](f: WR => T): AbstractReturningWithRegex[_, T]
  protected[expect] override def flatMap[T](f: WR => Expect[T]): AbstractReturningWithRegex[_, T]
  protected[expect] override def transform[T](flatMapPF: PartialFunction[WR, Expect[T]])(mapPF: PartialFunction[WR, T]): AbstractReturningWithRegex[_, T]
}


/**
  * $returningAction
  * This allows to return data based on the regex Match.
  * $regexWhen
  * $moreThanOne
  */
case class ReturningWithRegex[R](result: Match => R) extends AbstractReturningWithRegex[R, R] {
  def execute(when: RegexWhen[R], process: RichProcess, intermediateResult: IntermediateResult[R]): IntermediateResult[R] = {
    val regexMatch = when.regexMatch(intermediateResult.output)
    intermediateResult.copy(value = result(regexMatch))
  }

  protected[expect] def map[T](f: R => T): AbstractReturningWithRegex[_, T] = {
    this.copy(result andThen f)
  }
  protected[expect] def flatMap[T](f: R => Expect[T]): AbstractReturningWithRegex[_, T] = {
    ReturningExpectWithRegex(result andThen f)
  }
  protected[expect] def transform[T](flatMapPF: PartialFunction[R, Expect[T]])(mapPF: PartialFunction[R, T]): AbstractReturningWithRegex[_, T] = {
    val computeAction: R => AbstractReturningWithRegex[_, T] = {
      //FIXME: is there any way of implementing this without the double evaluation of pattern matchers and guards?
      //the double evaluation occurs in isDefinedAt and the apply

      //We cannot invoke map/flatMap, because if we did the returning result would be ran twice in the ActionReturningAction:
      //Once inside the execute which invokes parent.result
      //And another when the action returned by the ActionReturningAction is ran
      case r if flatMapPF.isDefinedAt(r) => ReturningExpectWithRegex(_ => flatMapPF(r))
      case r if mapPF.isDefinedAt(r) => this.copy(_ => mapPF(r))
      case r => pfNotDefined[AbstractReturningWithRegex[_, T]](r)
    }

    new ActionReturningActionWithRegex(this, computeAction)
  }

  def structurallyEquals[WW[X] <: RegexWhen[X]](other: Action[R, WW]): Boolean = other.isInstanceOf[ReturningWithRegex[_]]
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
case class ReturningExpectWithRegex[R](result: Match => Expect[R]) extends AbstractReturningWithRegex[Expect[R], R] {
  def execute(when: RegexWhen[R], process: RichProcess, intermediateResult: IntermediateResult[R]): IntermediateResult[R] = {
    val regexMatch = when.regexMatch(intermediateResult.output)
    val expect = result(regexMatch)
    intermediateResult.copy(executionAction = ChangeToNewExpect(expect))
  }

  protected[expect] def map[T](f: R => T): AbstractReturningWithRegex[_, T] = {
    this.copy(result.andThen(_.map(f)))
  }
  protected[expect] def flatMap[T](f: R => Expect[T]): AbstractReturningWithRegex[_, T] = {
    this.copy(result.andThen(_.flatMap(f)))
  }
  protected[expect] def transform[T](flatMapPF: PartialFunction[R, Expect[T]])(mapPF: PartialFunction[R, T]): AbstractReturningWithRegex[_, T] = {
    this.copy(result.andThen(_.transform(flatMapPF)(mapPF)))
  }

  def structurallyEquals[WW[X] <: RegexWhen[X]](other: Action[R, WW]): Boolean = other.isInstanceOf[ReturningExpectWithRegex[_]]
}


case class ActionReturningActionWithRegex[R, T](parent: ReturningWithRegex[R], resultAction: R => AbstractReturningWithRegex[_, T])
  extends AbstractReturningWithRegex[Nothing, T] {
  def result: Match => Nothing = _ => throw new IllegalArgumentException("no can do")

  def execute(when: RegexWhen[T], process: RichProcess, intermediateResult: IntermediateResult[T]): IntermediateResult[T] = {
    val regexMatch = when.regexMatch(intermediateResult.output)
    val parentResult: R = parent.result(regexMatch)
    resultAction(parentResult).execute(when, process, intermediateResult)
  }

  protected[expect] def map[U](f: T => U): AbstractReturningWithRegex[_, U] = {
    this.copy(parent, resultAction.andThen(_.map(f)))
  }
  protected[expect] def flatMap[U](f: T => Expect[U]): AbstractReturningWithRegex[_, U] = {
    this.copy(parent, resultAction.andThen(_.flatMap(f)))
  }
  protected[expect] def transform[U](flatMapPF: PartialFunction[T, Expect[U]])(mapPF: PartialFunction[T, U]): AbstractReturningWithRegex[_, U] = {
    def toU(r: AbstractReturningWithRegex[_, T]): AbstractReturningWithRegex[_, U] = r match {
      case r: ReturningWithRegex[T] => r.transform(flatMapPF)(mapPF) //stop case
      case r: ReturningExpectWithRegex[T] => r.transform(flatMapPF)(mapPF) //stop case
      case r => r.transform(flatMapPF)(mapPF) //recursive call
    }
    this.copy(parent, resultAction andThen toU)
  }

  def structurallyEquals[WW[X] <: RegexWhen[X]](other: Action[T, WW]): Boolean = this.isInstanceOf[ActionReturningActionWithRegex[_, _]]
}