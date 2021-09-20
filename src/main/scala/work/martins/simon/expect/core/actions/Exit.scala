package work.martins.simon.expect.core.actions

import work.martins.simon.expect./=>
import work.martins.simon.expect.core.*
import work.martins.simon.expect.core.ExecutionAction.Terminate

/**
  * When this action is executed the current run of Expect is terminated causing it to return the
  * last value if there is a ReturningAction, or the default value otherwise.
  *
  * Any action or expect block added after this will not be executed.
  */
final case class Exit() extends NonProducingAction[When]:
  def run[RR >: Nothing](when: When[RR], runContext: RunContext[RR]): RunContext[RR] =
    runContext.copy(executionAction = Terminate)
  
  def structurallyEquals[RR >: Nothing](other: Action[RR, ?]): Boolean = other.isInstanceOf[Exit]