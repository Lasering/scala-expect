package codes.simon.expect.core

case class IntermediateResult[R](output: String, value: R, executionAction: ExecutionAction)
