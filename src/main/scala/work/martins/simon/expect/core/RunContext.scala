package work.martins.simon.expect.core

import work.martins.simon.expect.FromInputStream
import work.martins.simon.expect.FromInputStream.*

import scala.util.Random

enum ExecutionAction derives CanEqual:
  case Terminate
  case Continue
  case ChangeToNewExpect[R](expect: Expect[R])

final case class RunContext[+R](process: RichProcess, value: R, executionAction: ExecutionAction,
                               readFrom: FromInputStream = StdOut, stdOutOutput: String = "", stdErrOutput: String = "",
                               id: String = Random.nextInt.toString):
  
  // Shortcut, because it makes sense
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
  def outputOf(from: FromInputStream): String = from match
    case StdErr => stdErrOutput
    case StdOut => stdOutOutput
  
  def withOutput(f: String => String): RunContext[R] = readFrom match
    case StdErr => copy(stdErrOutput = f(stdErrOutput))
    case StdOut => copy(stdOutOutput = f(stdOutOutput))
  
  def readingFrom(readFrom: FromInputStream): RunContext[R] = copy(readFrom = readFrom)