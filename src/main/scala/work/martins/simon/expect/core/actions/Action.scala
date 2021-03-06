package work.martins.simon.expect.core.actions

import work.martins.simon.expect.core.{Expect, RunContext, When}

/**
  * @define type Action
  * @define regexWhen This $type can only be added to a RegexWhen.
  * @define returningAction When this $type is executed the result of evaluating `result` is returned by
  *         the current run of Expect.
  * @define moreThanOne If more than one returning action is added to a When only the last `result` will be returned.
  *                     Note however that every returning action will still be executed.
  * @tparam W the concrete When type constructor to which this action can be applied.
  */
trait Action[+R, -W[+X] <: When[X]] {
  def run[RR >: R](when: W[RR], runContext: RunContext[RR]): RunContext[RR]

  def map[T](f: R => T): Action[T, W]
  def flatMap[T](f: R => Expect[T]): Action[T, W]
  type /=>[-A, +B] = PartialFunction[A, B]
  def transform[T](flatMapPF: R /=> Expect[T], mapPF: R /=> T): Action[T, W]

  protected def pfNotDefined[RR >: R, T](r: RR): T = {
    throw new NoSuchElementException(s"Expect.transform neither flatMapPF nor mapPF are defined at $r (from ${this.getClass.getSimpleName})")
  }

  /**
    * Returns whether the other $type is structurally equal to this $type.
    *
    * @example {{{
    *   Exit() structurallyEquals Exit() //returns true
    *   Exit() structurallyEquals Send() //returns false
    *   Send("AA") structurallyEquals Send("BB") //returns true
    *   Returning(5) structurallyEquals Returning { complexFunctionReturningAnInt } //returns true
    *   Returning("AA") structurallyEquals Returning(5) //won't compile
    * }}}
    *
    * We cannot test that an $type is equal to another $type because some actions return a value by invoking a function.
    * And equality on a function is not defined. So in other to extract some useful information about the "equality" of
    * two actions we created this function.
    *
    * @param other the other $type to campare this $type to.
    */
  def structurallyEquals[RR >: R, WW[+X] <: W[X]](other: Action[RR, WW]): Boolean
}
