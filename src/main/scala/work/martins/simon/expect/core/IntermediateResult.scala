package work.martins.simon.expect.core

final case class IntermediateResult[R](output: String, value: R, executionAction: ExecutionAction)
