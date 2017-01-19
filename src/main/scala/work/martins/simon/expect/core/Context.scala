package work.martins.simon.expect.core

import work.martins.simon.expect.core.Context.ExecutionAction
import work.martins.simon.expect.{FromInputStream, StdErr, StdOut}

object Context {
  sealed trait ExecutionAction
  case object Terminate extends ExecutionAction
  case object Continue extends ExecutionAction
  case class ChangeToNewExpect[R](expect: Expect[R]) extends ExecutionAction
}
final case class Context[R](id: Int, value: R, executionAction: ExecutionAction, redirectStdErrToStdOut: Boolean,
                            readFrom: FromInputStream = StdOut, stdOutOutput: String = "", stdErrOutput: String = "") {
  
  def withId(message: String): String = s"[ID:$id] $message"
  
  def output: String = outputOf(readFrom)
  def outputOf(from: FromInputStream): String = from match {
    case StdErr if !redirectStdErrToStdOut => stdErrOutput
    case _ => stdOutOutput
  }
  
  def withOutput(f: String => String): Context[R] = readFrom match {
    case StdErr if !redirectStdErrToStdOut => this.copy(stdErrOutput = f(stdErrOutput))
    case _ => this.copy(stdOutOutput = f(stdOutOutput))
  }
}
