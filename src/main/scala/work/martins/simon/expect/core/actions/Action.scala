package work.martins.simon.expect.core.actions

import work.martins.simon.expect./=>
import work.martins.simon.expect.core.{Expect, RunContext, When}

/**
  * @define regexWhen This Action can only be added to a RegexWhen.
  * @define returningAction When this Action is executed the result of evaluating `result` is returned by the current run of Expect.
  * @define moreThanOne If more than one returning action is added to a When only the last `result` will be returned.
  *                     Note however that every returning action will still be executed.
  * @tparam W the concrete When type constructor to which this action can be applied.
  *           For example `SendWithRegex` can only be used in `RegexWhen`s.
  */
trait Action[+R, -W[+X] <: When[X]] derives CanEqual:
  def run[RR >: R](when: W[RR], runContext: RunContext[RR]): RunContext[RR]
  
  def map[T](f: R => T): Action[T, W]
  def flatMap[T](f: R => Expect[T]): Action[T, W]
  def transform[T](flatMapPF: R /=> Expect[T], mapPF: R /=> T): Action[T, W]
  
  protected def pfNotDefined[RR >: R, T](r: RR): T =
    throw new NoSuchElementException(s"Expect.transform neither flatMapPF nor mapPF are defined at $r (from ${this.getClass.getSimpleName})")
  
  /**
    * Compares two actions structurally.
    *
    * @example {{{
    *   Exit() structurallyEquals Send("done") //returns false
    *   Exit() structurallyEquals Exit() //returns true
    *   Send("AA") structurallyEquals Send("BB") //returns true
    *   Sendln(m => s"${m.group(1)} + 3") structurallyEquals Sendln(_ => "random string") //returns true
    *   Sendln(m => s"${m.group(1)} + 3") structurallyEquals Sendln("random string") //returns false
    *   Returning(5) structurallyEquals Returning { complexFunctionReturningAnInt } //returns true
    *   Returning("AA") structurallyEquals Returning(5) //won't compile
    * }}}
    *
    * `Action.equals` is misleading for actions that return a value by invoking a function (eg: SendWithRegex, Returning), since equality
    * on functions is undecidable. This function allows extracting some useful information about the "equality" of two actions.
    *
    * @param other the other action to campare this action to.
    */
  def structurallyEquals[RR >: R](other: Action[RR, ?]): Boolean

/** An action which does not produce any values but its rather executed for its side-effects. */
trait NonProducingAction[-W[+X] <: When[X]] extends Action[Nothing, W] derives CanEqual:
  def run[RR >: Nothing](when: W[RR], runContext: RunContext[RR]): RunContext[RR]
  
  def map[T](f: Nothing => T): Action[T, W] = this
  def flatMap[T](f: Nothing => Expect[T]): Action[T, W] = this
  def transform[T](flatMapPF: Nothing /=> Expect[T], mapPF: Nothing /=> T): Action[T, W] = this