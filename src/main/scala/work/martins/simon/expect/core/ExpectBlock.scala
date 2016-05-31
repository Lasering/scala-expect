package work.martins.simon.expect.core

import java.io.EOFException

import com.typesafe.scalalogging.LazyLogging
import scala.concurrent.{TimeoutException, Future, ExecutionContext}
import work.martins.simon.expect.StringUtils._

/**
  * @define type ExpectBlock
  */
class ExpectBlock[R](val whens: When[R]*) extends LazyLogging {
  require(whens.nonEmpty, "ExpectBlock must have at least a When.")

  /**
    * First checks if any of the Whens of this $type matches against the last output.
    * If one such When exists then the result of executing it is returned.
    * Otherwise continuously reads text from `process` until one of the Whens of this $type matches against it.
    * If it is not able to do so before the timeout expires a TimeoutException will be thrown inside the Future.
    *
    * @param process the underlying process of Expect.
    * @param ex the ExecutionContext upon which the internal future is ran.
    * @return the result of executing the When that matches either `lastOutput` or the text read from `process`.
    *         Or a TimeoutException.
    */
  def run(process: RichProcess, intermediateResult: IntermediateResult[R], expectID: String)
         (implicit ex: ExecutionContext): Future[IntermediateResult[R]] = {
    def tryExecuteWhen(filter: When[R] => Boolean, result: IntermediateResult[R])
                      (onFailure: => Future[IntermediateResult[R]]): Future[IntermediateResult[R]] = {
      whens.find(filter).map { when =>
        logger.info(s"$expectID Matched with:\n$when")
        Future {
          when.execute(process, result)
        }
      }.getOrElse(onFailure)
    }

    def runWithMoreOutput(intermediateResult: IntermediateResult[R]): Future[IntermediateResult[R]] = {
      Future {
        val readText = process.read()
        val newOutput = intermediateResult.output + readText
        logger.info(s"$expectID Newly read text:\n$readText")
        logger.debug(s"$expectID New output:\n$newOutput")
        intermediateResult.copy[R](output = newOutput)
      } flatMap { result =>
        tryExecuteWhen(_.matches(result.output), result) {
          if (process.deadLineHasTimeLeft()) {
            logger.info(s"$expectID Didn't match with last output + newly read text. Going to read more.")
            runWithMoreOutput(result)
          } else {
            throw new TimeoutException()
          }
        }
      }
    }

    logger.info(s"$expectID Now running:\n$this")
    tryExecuteWhen(_.matches(intermediateResult.output), intermediateResult) {
      logger.info(s"$expectID Did not match with last output. Going to read more.")
      process.resetDeadline()
      runWithMoreOutput(intermediateResult)
    } recoverWith {
      case e: TimeoutException =>
        logger.info(s"$expectID Read timed out after ${process.timeout}.")
        tryExecuteWhen(_.isInstanceOf[TimeoutWhen[R]], intermediateResult)(Future.failed(e))
      case e: EOFException =>
        logger.info(s"$expectID Read returned EndOfFile.")
        tryExecuteWhen(_.isInstanceOf[EndOfFileWhen[R]], intermediateResult)(Future.failed(e))
    }
  }

  private[core] def map[T](f: R => T): ExpectBlock[T] = new ExpectBlock(whens.map(_.map(f)):_*)
  private[core] def flatMap[T](f: R => Expect[T]): ExpectBlock[T] = new ExpectBlock(whens.map(_.flatMap(f)):_*)
  private[core] def transform[T](mapPF: PartialFunction[R, T])(flatMapPF: PartialFunction[R, Expect[T]]): ExpectBlock[T] = {
    new ExpectBlock(whens.map(_.transform(mapPF)(flatMapPF)):_*)
  }

  override def toString: String =
    s"""expect {
        |${whens.mkString("\n").indent()}
        |}""".stripMargin

  override def equals(other: Any): Boolean = other match {
    case that: ExpectBlock[R] => whens == that.whens
    case _ => false
  }

  /**
    * @define subtypes whens
    * Returns whether the other $type has the same number of $subtypes as this $type and
    * that each pair of $subtypes is structurally equal.
    *
    * @param other the other $type to campare this $type to.
    */
  def structurallyEquals(other: ExpectBlock[R]): Boolean = {
    whens.size == other.whens.size && whens.zip(other.whens).forall{ case (a, b) => a.structurallyEquals(b) }
  }
  override def hashCode(): Int = whens.hashCode()
}