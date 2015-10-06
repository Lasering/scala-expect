package codes.simon.expect.core

import java.nio.charset.Charset
import com.typesafe.scalalogging.LazyLogging
import scala.concurrent._
import scala.concurrent.duration.FiniteDuration


class Expect[R](command: String, defaultValue: R)(expects: ExpectBlock[R]*) extends LazyLogging {
  require(command.nonEmpty, "Expect must have a command to run.")

  def run(timeout: FiniteDuration = Constants.Timeout, charset: Charset = Constants.Charset,
          bufferSize: Int = Constants.BufferSize, redirectStdErrToStdOut: Boolean = Constants.RedirectStdErrToStdOut)
         (implicit ex: ExecutionContext): Future[R] = {
    val richProcess = RichProcess(command, timeout, charset, bufferSize, redirectStdErrToStdOut)
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
      //Return just the value to the user.
      val value = lastResult._2
      Future.successful(value)
    case headExpectBlock :: remainingExpectBlocks =>
      logger.info("Starting a new ExpectBlock.run")
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