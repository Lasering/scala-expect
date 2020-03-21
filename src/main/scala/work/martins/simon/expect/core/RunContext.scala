package work.martins.simon.expect.core

import scala.util.Random
import com.typesafe.scalalogging.LazyLogging
import work.martins.simon.expect.core.RunContext.ExecutionAction
import work.martins.simon.expect.{FromInputStream, StdErr, StdOut}

object RunContext {
  sealed trait ExecutionAction
  case object Terminate extends ExecutionAction
  case object Continue extends ExecutionAction
  case class ChangeToNewExpect[R](expect: Expect[R]) extends ExecutionAction
}
final case class RunContext[+R](process: RichProcess, value: R, executionAction: ExecutionAction,
                               readFrom: FromInputStream = StdOut, stdOutOutput: String = "", stdErrOutput: String = "",
                               id: String = Random.nextInt().toString) extends LazyLogging {

  //Shortcut, because it makes sense
  val settings = process.settings

  /**
    * When running multiple Expects simultaneously its hard to differentiate them. The `id` helps to do so.
    * Currently the `id` is just a random number. It's not the Expect hashCode because we could be running the same
    * expect simultaneously.
    * @param message The messate to which the ID will be added.
    * @return
    */
  def withId(message: String): String = s"[ID:$id] $message"

  def output: String = outputOf(readFrom)
  def outputOf(from: FromInputStream): String = from match {
    case StdErr => stdErrOutput
    case StdOut => stdOutOutput
  }

  def withOutput(f: String => String): RunContext[R] = readFrom match {
    case StdErr => this.copy(stdErrOutput = f(stdErrOutput))
    case StdOut => this.copy(stdOutOutput = f(stdOutOutput))
  }

  def readingFrom(readFrom: FromInputStream): RunContext[R] = this.copy(readFrom = readFrom)
}
