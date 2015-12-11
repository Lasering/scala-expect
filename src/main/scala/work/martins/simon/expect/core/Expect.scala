package work.martins.simon.expect.core

import java.nio.charset.Charset
import com.typesafe.scalalogging.LazyLogging
import scala.concurrent._
import scala.concurrent.duration.FiniteDuration

class Expect[R](command: Seq[String], val defaultValue: R)(expects: ExpectBlock[R]*) extends LazyLogging {
  def this(command: String, defaultValue: R = Unit)(expects: ExpectBlock[R]*) = {
    this(command.split("""\s+""").filter(_.nonEmpty).toSeq, defaultValue)(expects:_*)
  }
  require(command.nonEmpty, "Expect must have a command to run.")

  def run(timeout: FiniteDuration = Configs.timeout, charset: Charset = Configs.charset,
          bufferSize: Int = Configs.bufferSize, redirectStdErrToStdOut: Boolean = Configs.redirectStdErrToStdOut)
         (implicit ex: ExecutionContext): Future[R] = {
    val richProcess = RichProcess(command, timeout, charset, bufferSize, redirectStdErrToStdOut)
    innerRun(richProcess, IntermediateResult("", defaultValue, Continue), expects.toList)
  }

  private def innerRun(richProcess: RichProcess, intermediateResult: IntermediateResult[R],
                       expectsStack: List[ExpectBlock[R]])
                      (implicit ec: ExecutionContext): Future[R] = expectsStack match {
    case headExpectBlock :: remainingExpectBlocks =>
      logger.info("Starting a new ExpectBlock.run")
      val result = headExpectBlock.run(richProcess, intermediateResult).flatMap { case result @ IntermediateResult(_, _, action) =>
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

      //If in the process of running the head expect block we get an exception we want to make sure the
      //rich process is destroyed.
      result.onFailure {
        case e: Throwable => richProcess.destroy()
      }

      result
    case _ =>
      //We have no more expect blocks. So we can finish the execution.
      //Make sure to destroy the process and close the streams.
      richProcess.destroy()
      //Return just the value to the user.
      Future.successful(intermediateResult.value)
  }

  override def toString: String =
    s"""Expect:
       |\tCommand: $command
       |\tDefaultValue: $defaultValue
       |\t${expects.mkString("\n\t")}
     """.stripMargin
}