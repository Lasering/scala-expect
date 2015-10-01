package codes.simon.expect.core

import java.nio.charset.Charset
import com.typesafe.scalalogging.LazyLogging
import scala.concurrent._
import scala.concurrent.duration.FiniteDuration


class Expect[R](command: String, defaultValue: R, expects: Seq[ExpectBlock[R]]) extends LazyLogging {
  require(command.isEmpty == false, "Expect must have a command to run.")

  def run(timeout: FiniteDuration = Constants.TIMEOUT, charset: Charset = Constants.CHARSET,
          bufferSize: Int = Constants.BUFFER_SIZE, redirectStdErrToStdOut: Boolean = Constants.REDIRECT_STDERR_TO_STDOUT)
         (implicit ex: ExecutionContext): Future[R] = {
    val richProcess = RichProcess(command, timeout, charset, bufferSize)
    //_1 = last read output
    //_2 = last value
    //_3 = last execution action
    val lastResult = ("", defaultValue, Continue)
    innerRun(richProcess, lastResult, expects.toList)
  }

  private def innerRun(richProcess: RichProcess, lastResult: (String, R, ExecutionAction), expectsStack: List[ExpectBlock[R]])
                      (implicit ec: ExecutionContext): Future[R] = expectsStack match {
    case List() =>
      //We have no more expect blocks. So we can finish the execution.
      //Make sure to destroy the process and close the streams.
      richProcess.destroy()
      //Return just the result to the user.
      Future.successful(lastResult._2)
    case headExpectBlock :: remainingExpectBlocks =>
      headExpectBlock.run(richProcess, lastResult).flatMap { case result @ (_, _, action) =>
        action match {
          case Continue =>
            innerRun(richProcess, result, remainingExpectBlocks)
          case Terminate =>
            innerRun(richProcess, result, List.empty[ExpectBlock[R]])
          case ChangeToNewExpect(newExpect) =>
            richProcess.destroy()
            newExpect.asInstanceOf[Expect[R]].run(richProcess.timeout, richProcess.charset, richProcess.bufferSize)
        }
      }
  }

  override def toString =
    s"""Expect:
       |\tCommand: $command
       |\tDefaultValue: $defaultValue
       |\t${expects.mkString("\n\t")}
     """.stripMargin
}