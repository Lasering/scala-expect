package codes.simon.expect.core

import java.io.EOFException

import com.typesafe.scalalogging.LazyLogging
import scala.concurrent.{TimeoutException, Future, ExecutionContext}

class ExpectBlock(whens: Seq[When]) extends LazyLogging {
  private case class NoMatchingPatternException(output: String) extends Exception
  private def runWithMoreOutput[R](process: RichProcess, lastOutput: Option[String])(implicit ex: ExecutionContext): Future[(Option[String], Option[R])] = Future {
    val readText = process.read()
    logger.info(s"""Read:
      |----------------------------------------
      |$readText
      |----------------------------------------""".stripMargin)
    val newOutput = lastOutput.getOrElse("") + readText
    logger.info(s"NewOutput: $newOutput")
    whens.find(_.matches(newOutput)) match {
      case None => throw new NoMatchingPatternException(newOutput)
      case Some(when) =>
        logger.info(s"Matched with $when")
        when.execute[R](process, Some(newOutput))
    }
  } recoverWith {
    case NoMatchingPatternException(output) if process.deadLineHasTimeLeft() =>
      logger.info(s"Did not match. Going to read more.")
      runWithMoreOutput(process, Some(output))
    case e: TimeoutException => tryExecuteWhen(_.isInstanceOf[TimeoutWhen], process, lastOutput, e)
    case e: EOFException => tryExecuteWhen(_.isInstanceOf[EndOfFileWhen], process, lastOutput, e)
  }
  private def tryExecuteWhen[R](filter: When => Boolean, process: RichProcess, output: Option[String], e: Exception): Future[(Option[String], Option[R])] = {
    whens.find(filter) match {
      case None =>
        //Now we really failed. So we must destroy the running process and the streams.
        process.destroy()
        Future.failed(e)
      case Some(when) =>
        Future.successful(when.execute[R](process, output))
    }
  }
  /**
   * First checks if any of the Whens of this ExpectBlock matches against `lastOutput`.
   * If one such When exists then the result of executing it is returned.
   * Otherwise continuously reads text from `process` until one of the Whens of this ExpectBlock matches against it.
   * If it is not able to do so before the timeout expires a TimeoutException will be thrown inside the Future.
   * @param process the underlying process of Expect.
   * @param lastOutput the output resulting from the execution of the previous ExpectBlock.
   *                   For the first ExpectBlock this will be None.
   * @param ex the ExecutionContext upon which the internal future is ran.
   * @tparam R
   * @return the result of executing the When that matches either `lastOutput` or the text read from `process`.
   *         Or a TimeoutException.
   */
  def run[R](process: RichProcess, lastOutput: Option[String])(implicit ex: ExecutionContext): Future[(Option[String], Option[R])] = {
    lastOutput.flatMap{ output =>
      whens.find(_.matches(output))
    } match {
      case Some(when) =>
        //A When matches with lastOutput so we can execute it directly.
        logger.info("Matched with lastOutput")
        Future.successful(when.execute[R](process, lastOutput))
      case None =>
        //We need to read more lines to find a matching When. Or lastOutput was None.
        process.resetDeadline()
        runWithMoreOutput(process, lastOutput)
    }
  }
}
