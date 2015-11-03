package work.martins.simon.expect.core

sealed trait ExecutionAction
case object Terminate extends ExecutionAction
case object Continue extends ExecutionAction
case class ChangeToNewExpect[R](expect: Expect[R]) extends ExecutionAction