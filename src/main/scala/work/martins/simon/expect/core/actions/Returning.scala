package work.martins.simon.expect.core.actions

import scala.util.matching.Regex.Match
import work.martins.simon.expect./=>
import work.martins.simon.expect.core.*
import work.martins.simon.expect.core.ExecutionAction.ChangeToNewExpect

object Returning:
  def apply[R](result: => R): Returning[R] = Returning(_ => result)
  def apply[R](result: Match => R): ReturningWithRegex[R] = ReturningWithRegex(result)

object ReturningExpect:
  def apply[R](result: => Expect[R]): ReturningExpect[R] = ReturningExpect(_ => result)
  def apply[R](result: Match => Expect[R]): ReturningExpectWithRegex[R] = ReturningExpectWithRegex(result)

// `result` is not by-name because "val parameters may not be call-by-name", to solve this problem:
//  1. `apply` in the companion object has `result` by-name.
//  2. The constructor is private and `result` has type `Unit => R` (notice its not `() => R`), this solves multiple problems:
//    · Having the constructor private disambiguates between the overloaded alternatives `Match => R` and `=> R` in the companion object.
//    · `() => R` and `=> R` have the same type after erasure (Function0), whereas `Unit => R` has type `Function1[Unit, R]`
//      this way the compiler does not complain about a double definition.
//    · The sensitive flag comes for free.
/**
  * $returningAction
  * $moreThanOne
  **/
final case class Returning[+R] private (result: Unit => R) extends Action[R, When] derives CanEqual:
  def run[RR >: R](when: When[RR], runContext: RunContext[RR]): RunContext[RR] =
    runContext.copy(value = result(()))
  
  def map[T](f: R => T): Returning[T] = copy(result andThen f)
  def flatMap[T](f: R => Expect[T]): ReturningExpect[T] = ReturningExpect(f(result(())))
  def transform[T](flatMapPF: R /=> Expect[T], mapPF: R /=> T): ActionReturningAction[T] =
    ActionReturningAction(result andThen {
      // We cannot use the map/flatMap because if we did `result` would be ran twice:
      //   · once inside ActionReturningAction.run which invokes `result(())`
      //   · and another when the action returned by the ActionReturningAction is ran
      case r if flatMapPF.isDefinedAt(r) => ReturningExpect(flatMapPF(r))
      case r if mapPF.isDefinedAt(r) => copy(_ => mapPF(r))
      case r => pfNotDefined(r)
    })
  
  def structurallyEquals[RR >: R](other: Action[RR, ?]): Boolean = other.isInstanceOf[Returning[RR]]
private final case class ActionReturningAction[+R](result: Unit => Action[R, When]) extends Action[R, When] derives CanEqual:
  def run[RR >: R](when: When[RR], runContext: RunContext[RR]): RunContext[RR] =
    result(()).run(when, runContext)
  
  def map[T](f: R => T): ActionReturningAction[T] = copy(result.andThen(_.map(f)))
  def flatMap[T](f: R => Expect[T]): ActionReturningAction[T] = copy(result.andThen(_.flatMap(f)))
  def transform[T](flatMapPF: R /=> Expect[T], mapPF: R /=> T): ActionReturningAction[T] = copy(result.andThen(_.transform(flatMapPF, mapPF)))
  
  def structurallyEquals[RR >: R](other: Action[RR, ?]): Boolean = other.isInstanceOf[ActionReturningAction[RR]]

/**
  * $returningAction
  * This allows to return data based on the regex Match.
  * $regexWhen
  * $moreThanOne
  */
final case class ReturningWithRegex[+R](result: Match => R) extends Action[R, RegexWhen] derives CanEqual:
  def run[RR >: R](when: RegexWhen[RR], runContext: RunContext[RR]): RunContext[RR] =
    val newValue = result(when.regexMatch(runContext.output))
    runContext.copy(value = newValue)
  
  def map[T](f: R => T): ReturningWithRegex[T] = copy(result andThen f)
  def flatMap[T](f: R => Expect[T]): ReturningExpectWithRegex[T] = ReturningExpectWithRegex(result andThen f)
  def transform[T](flatMapPF: R /=> Expect[T], mapPF: R /=> T): ActionReturningActionWithRegex[T] =
    ActionReturningActionWithRegex(result andThen {
      //We cannot invoke map/flatMap because if we did `result` would be ran twice:
      //   · once inside ActionReturningActionWithRegex.run which invokes `result(())`
      //  · and another when the action returned by the ActionReturningActionWithRegex is ran
      case r if flatMapPF.isDefinedAt(r) => ReturningExpectWithRegex(_ => flatMapPF(r))
      case r if mapPF.isDefinedAt(r) => copy(_ => mapPF(r))
      case r => pfNotDefined(r)
    })
  
  def structurallyEquals[RR >: R](other: Action[RR, ?]): Boolean = other.isInstanceOf[ReturningWithRegex[RR]]
private final case class ActionReturningActionWithRegex[+R](result: Match => Action[R, RegexWhen]) extends Action[R, RegexWhen] derives CanEqual:
  def run[RR >: R](when: RegexWhen[RR], runContext: RunContext[RR]): RunContext[RR] =
    val regexMatch = when.regexMatch(runContext.output)
    result(regexMatch).run(when, runContext)
  
  def map[T](f: R => T): ActionReturningActionWithRegex[T] = copy(result.andThen(_.map(f)))
  def flatMap[T](f: R => Expect[T]): ActionReturningActionWithRegex[T] = copy(result.andThen(_.flatMap(f)))
  def transform[T](flatMapPF: R /=> Expect[T], mapPF: R /=> T): ActionReturningActionWithRegex[T] = copy(result.andThen(_.transform(flatMapPF, mapPF)))
  
  def structurallyEquals[RR >: R](other: Action[RR, ?]): Boolean = other.isInstanceOf[ActionReturningActionWithRegex[RR]]

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
final case class ReturningExpect[+R] private (result: Unit => Expect[R]) extends Action[R, When] derives CanEqual:
  def run[RR >: R](when: When[RR], runContext: RunContext[RR]): RunContext[RR] =
    val newExpect = result(())
    runContext.copy(executionAction = ChangeToNewExpect(newExpect))
  
  def map[T](f: R => T): ReturningExpect[T] = copy(result.andThen(_.map(f)))
  def flatMap[T](f: R => Expect[T]): ReturningExpect[T] = copy(result.andThen(_.flatMap(f)))
  def transform[T](flatMapPF: R /=> Expect[T], mapPF: R /=> T): ReturningExpect[T] = copy(result.andThen(_.transform(flatMapPF, mapPF)))
  
  def structurallyEquals[RR >: R](other: Action[RR, ?]): Boolean = other.isInstanceOf[ReturningExpect[RR]]

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
  * This allows to construct the Expect based on the regex Match.
  * $regexWhen
  * Any action or expect block added after this will not be executed.
  */
final case class ReturningExpectWithRegex[+R](result: Match => Expect[R]) extends Action[R, RegexWhen] derives CanEqual:
  def run[RR >: R](when: RegexWhen[RR], runContext: RunContext[RR]): RunContext[RR] =
    val newExpect = result(when.regexMatch(runContext.output))
    runContext.copy(executionAction = ChangeToNewExpect(newExpect))
  
  def map[T](f: R => T): ReturningExpectWithRegex[T] = copy(result.andThen(_.map(f)))
  def flatMap[T](f: R => Expect[T]): ReturningExpectWithRegex[T] = copy(result.andThen(_.flatMap(f)))
  def transform[T](flatMapPF: R /=> Expect[T], mapPF: R /=> T): ReturningExpectWithRegex[T] = copy(result.andThen(_.transform(flatMapPF, mapPF)))
  
  def structurallyEquals[RR >: R](other: Action[RR, ?]): Boolean = other.isInstanceOf[ReturningExpectWithRegex[RR]]