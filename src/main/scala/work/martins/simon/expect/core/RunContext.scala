package work.martins.simon.expect.core

import com.typesafe.scalalogging.LazyLogging
import work.martins.simon.expect.core.RunContext.ExecutionAction
import work.martins.simon.expect.{FromInputStream, StdErr, StdOut}

import scala.util.Random

object RunContext {
  sealed trait ExecutionAction
  case object Terminate extends ExecutionAction
  case object Continue extends ExecutionAction
  case class ChangeToNewExpect[R](expect: Expect[R]) extends ExecutionAction
}
// The id is not the hashCode of the expect we are running because the user could be running the same expect twice
// and at the same time. And by using the hash code the logs wouldn't be very helpful since it would not be possible
// to distinguish between the two expects.
final case class RunContext[+R](process: RichProcess, value: R, executionAction: ExecutionAction,
                               readFrom: FromInputStream = StdOut, stdOutOutput: String = "", stdErrOutput: String = "",
                               id: String = Random.nextInt.toString) extends LazyLogging {

  //Shortcut, because it makes sense
  val settings = process.settings

  /**
    * When running multiple Expects simultaneously its hard to differentiate them. The `id` helps to do so.
    * Currently the `id` is computed as follows s"$${hashCode}-$${randomNumber}".
    * @param message
    * @return
    */
  def withId(message: String): String = s"[ID:$id] $message"

  def output: String = outputOf(readFrom)
  def outputOf(from: FromInputStream): String = from match {
    case StdErr if !settings.redirectStdErrToStdOut => stdErrOutput
    case _ => stdOutOutput
  }

  def withOutput(f: String => String): RunContext[R] = readFrom match {
    case StdErr if !settings.redirectStdErrToStdOut => this.copy(stdErrOutput = f(stdErrOutput))
    case _ => this.copy(stdOutOutput = f(stdOutOutput))
  }

  def readingFrom(readFrom: FromInputStream): RunContext[R] = this.copy(readFrom = readFrom)
}
