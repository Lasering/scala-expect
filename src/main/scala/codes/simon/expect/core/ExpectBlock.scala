package codes.simon.expect.core

import java.io.EOFException

import com.typesafe.scalalogging.LazyLogging
import scala.concurrent.{TimeoutException, Future, ExecutionContext}

class ExpectBlock[R](whens: When[R]*) extends LazyLogging {
  private def runWithMoreOutput(process: RichProcess, lastResult: (String, R, ExecutionAction))(implicit ex: ExecutionContext): Future[(String, R, ExecutionAction)] = {
    case class NoMatchingPatternException(output: String) extends Exception
    Future {
      val readText = process.read()
      logger.info(s"""Read:
          |----------------------------------------
          |$readText
          |----------------------------------------""".stripMargin)
      val newOutput = lastResult._1 + readText
      logger.info(s"NewOutput: $newOutput")
      whens.find(_.matches(newOutput)) match {
        case None => throw new NoMatchingPatternException(newOutput)
        case Some(when) =>
          logger.info(s"Matched with $when")
          when.execute(process, lastResult.copy(_1 = newOutput))
      }
    } recoverWith {
      case NoMatchingPatternException(output) if process.deadLineHasTimeLeft() =>
        logger.info(s"Did not match. Going to read more.")
        runWithMoreOutput(process, lastResult.copy(_1 = output))
      case e: TimeoutException =>
        logger.info(s"Read timed out after ${process.timeout}. Going to try and execute a TimeoutWhen.")
        tryExecuteWhen(_.isInstanceOf[TimeoutWhen[R]], process, lastResult, e)
      case e: EOFException =>
        logger.info(s"Read returned EndOfFile. Going to try and execute a EndOFFileWhen.")
        tryExecuteWhen(_.isInstanceOf[EndOfFileWhen[R]], process, lastResult, e)
    }
  }
  private def tryExecuteWhen(filter: When[R] => Boolean, process: RichProcess, lastResult: (String, R, ExecutionAction), e: Exception): Future[(String, R, ExecutionAction)] = {
    whens.find(filter) match {
      case None =>
        //Now we really failed. So we must destroy the running process and the streams.
        process.destroy()
        Future.failed(e)
      case Some(when) =>
        Future.successful(when.execute(process, lastResult))
    }
  }
  /**
   * First checks if any of the Whens of this ExpectBlock matches against the last output.
   * If one such When exists then the result of executing it is returned.
   * Otherwise continuously reads text from `process` until one of the Whens of this ExpectBlock matches against it.
   * If it is not able to do so before the timeout expires a TimeoutException will be thrown inside the Future.
   * @param process the underlying process of Expect.
   * @param ex the ExecutionContext upon which the internal future is ran.
   * @return the result of executing the When that matches either `lastOutput` or the text read from `process`.
   *         Or a TimeoutException.
   */
  def run(process: RichProcess, lastResult: (String, R, ExecutionAction))(implicit ex: ExecutionContext): Future[(String, R, ExecutionAction)] = {
    whens.find(_.matches(lastResult._1)) match {
      case Some(when) =>
        //A When matches with lastOutput so we can execute it directly.
        logger.info("Matched with lastOutput")
        Future.successful(when.execute(process, lastResult))
      case None =>
        //We need to read more lines to find a matching When. Or lastOutput was None.
        logger.info("Need more output. Going to read...")
        process.resetDeadline()
        runWithMoreOutput(process, lastResult)
    }
  }

  override def toString: String =
    s"""expect {
       |\t${whens.mkString("\n\t")}
       |}""".stripMargin
}
