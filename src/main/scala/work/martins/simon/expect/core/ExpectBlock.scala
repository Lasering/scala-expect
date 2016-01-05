package work.martins.simon.expect.core

import java.io.EOFException

import com.typesafe.scalalogging.LazyLogging
import scala.concurrent.{TimeoutException, Future, ExecutionContext}
import work.martins.simon.expect.StringUtils._

class ExpectBlock[R](val whens: When[R]*) extends LazyLogging {
  require(whens.nonEmpty, "ExpectBlock must have at least a When.")

  protected def runWithMoreOutput(process: RichProcess, intermediateResult: IntermediateResult[R])
                               (implicit ex: ExecutionContext): Future[IntermediateResult[R]] = {
    case class NoMatchingPatternException(output: String) extends Exception
    Future {
      val readText = process.read()
      val newOutput = intermediateResult.output + readText
      logger.info(s"""New output:
                     |----------------------------------------
                     |$newOutput
                     |----------------------------------------""".stripMargin)
      whens.find(_.matches(newOutput)) match {
        case None => throw new NoMatchingPatternException(newOutput)
        case Some(when) =>
          logger.info(s"Matched with:\n$when")
          when.execute(process, intermediateResult.copy(output = newOutput))
      }
    } recoverWith {
      case NoMatchingPatternException(newOutput) if process.deadLineHasTimeLeft() =>
        logger.info(s"Did not match. Going to read more.")
        runWithMoreOutput(process, intermediateResult.copy(output = newOutput))
      case e: TimeoutException =>
        logger.info(s"Read timed out after ${process.timeout}. Going to try and execute a TimeoutWhen.")
        tryExecuteWhen(_.isInstanceOf[TimeoutWhen[R]], process, intermediateResult, e)
      case e: EOFException =>
        logger.info(s"Read returned EndOfFile. Going to try and execute a EndOfFileWhen.")
        tryExecuteWhen(_.isInstanceOf[EndOfFileWhen[R]], process, intermediateResult, e)
    }
  }
  protected def tryExecuteWhen(filter: When[R] => Boolean, process: RichProcess,
                             intermediateResult: IntermediateResult[R], e: Exception)
                            (implicit ex: ExecutionContext): Future[IntermediateResult[R]] = {
    whens.find(filter) match {
      case Some(when) =>
        Future {
          when.execute(process, intermediateResult)
        }
      case None =>
        //Now we really failed. So we must destroy the running process and the streams.
        process.destroy()
        Future.failed(e)
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
  def run(process: RichProcess, intermediateResult: IntermediateResult[R])
         (implicit ex: ExecutionContext): Future[IntermediateResult[R]] = {
    whens.find(_.matches(intermediateResult.output)) match {
      case Some(when) =>
        logger.info("Matched with lastOutput")
        Future {
          when.execute(process, intermediateResult)
        }
      case None =>
        logger.info("Need more output. Going to read...")
        process.resetDeadline()
        runWithMoreOutput(process, intermediateResult)
    }
  }

  override def toString: String =
    s"""expect {
        |${whens.mkString("\n").indent()}
        |}""".stripMargin

  override def equals(other: Any): Boolean = other match {
    case that: ExpectBlock[R] => whens == that.whens
    case _ => false
  }

  def structurallyEquals(other: ExpectBlock[R]): Boolean = {
    whens.size == other.whens.size && whens.zip(other.whens).forall{ case (a, b) => a.structurallyEquals(b) }
  }
  override def hashCode(): Int = whens.hashCode()
}