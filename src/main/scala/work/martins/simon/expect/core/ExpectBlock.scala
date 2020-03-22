package work.martins.simon.expect.core

import java.io.EOFException
import scala.annotation.unchecked.uncheckedVariance
import scala.concurrent.duration.Deadline
import scala.concurrent.{ExecutionContext, Future, TimeoutException}
import com.typesafe.scalalogging.LazyLogging
import work.martins.simon.expect.{FromInputStream, StdErr, StdOut}
import work.martins.simon.expect.StringUtils._

/**
  * @define type ExpectBlock
  */
final case class ExpectBlock[+R](whens: When[R]*) extends LazyLogging {
  require(whens.nonEmpty, "ExpectBlock must have at least a When.")
  
  private[this] def timeoutWhen(runContext: RunContext[R])(implicit deadline: Deadline): (When[R], RunContext[R]) = {
    logger.info(runContext.withId(s"Read timed out after ${deadline.time}."))
    whens.collectFirst {
      case when @ TimeoutWhen() => (when, runContext)
    }.getOrElse(throw new TimeoutException)
  }
  private[this] def endOfFileWhen(runContext: RunContext[R]): (When[R], RunContext[R]) = {
    logger.info(runContext.withId(s"Read returned EndOfFile."))
    whens.collectFirst {
      case when @ EndOfFileWhen(runContext.readFrom) => (when, runContext)
    }.getOrElse(throw new EOFException)
  }

  private[this] def findMatchingWhen(runContext: RunContext[R]): Option[(When[R], RunContext[R])] =
    whens.find(w => w.readFrom == runContext.readFrom && w.matches(runContext.output)).map(_ -> runContext)

  private[this] def obtainMatchingWhen(runContext: RunContext[R], read: RichProcess => (FromInputStream, String))
                                      (implicit ex: ExecutionContext, deadline: Deadline): Future[(When[R], RunContext[R])] =
    findMatchingWhen(runContext) match {
      case Some(value) => Future.successful(value)
      case None if deadline.isOverdue() => Future.successful(timeoutWhen(runContext))
      case _ =>
        Future {
          logger.debug(runContext.withId(s"Did not match with last ${runContext.readFrom} output. Going to read more."))
          logger.trace(runContext.withId(s"Time left in deadline ${deadline.timeLeft}"))
          (runContext.withNewOutput _).tupled(read(runContext.process))
        }.flatMap(obtainMatchingWhen(_, read))
          .recover {
            case _: EOFException => endOfFileWhen(runContext)
            case _: TimeoutException => timeoutWhen(runContext)
          }
    }

  /**
    * First checks if any of the Whens of this $type matches against the last output.
    * If one such When exists then the result of executing it is returned.
    * Otherwise continuously reads text from `process` until one of the Whens of this $type matches against it.
    * If it is not able to do so before the timeout expires a TimeoutException will be thrown inside the Future.
    *
    * @param runContext the current run context of this expect execution.
    * @param ex the ExecutionContext upon which the internal future is ran.
    * @return the result of executing the When that matches either `lastOutput` or the text read from `process`.
    *         Or a TimeoutException.
    */
  private[core] def run(runContext: RunContext[R @uncheckedVariance])(implicit ex: ExecutionContext): Future[RunContext[R]] = {
    val froms = whens.map(_.readFrom).distinct
    logger.debug(runContext.withId(s"Now running (reading from ${froms.mkString(" and ")}):\n$this"))
    implicit val deadline: Deadline = runContext.settings.scaledTimeout.fromNow
    val matchingWhen = froms match {
      case Seq(from) =>
        logger.debug(runContext.withId(s"Going to try and match with last $from output."))
        obtainMatchingWhen(runContext.readingFrom(from), process => (from, process.read(from)))
      case _ =>
        logger.debug(runContext.withId("Going to try and match with last StdErr output."))
        findMatchingWhen(runContext.readingFrom(StdErr)).map(Future.successful).getOrElse {
          logger.debug(runContext.withId(s"Did not match with last StdErr output. Going to try and match with last StdOut output."))
          obtainMatchingWhen(runContext.readingFrom(StdOut), _.readOnFirstInputStream)
        }
    }
    matchingWhen.map{ case (when, newRunContext) =>
      logger.info(newRunContext.withId(s"Matched with:\n$when"))
      when.run(newRunContext)
    }
  }

  def map[T](f: R => T): ExpectBlock[T] = ExpectBlock(whens.map(_.map(f)):_*)
  def flatMap[T](f: R => Expect[T]): ExpectBlock[T] = ExpectBlock(whens.map(_.flatMap(f)):_*)
  def transform[T](flatMapPF: PartialFunction[R, Expect[T]], mapPF: PartialFunction[R, T]): ExpectBlock[T] =
    ExpectBlock(whens.map(_.transform(flatMapPF, mapPF)):_*)
  
  override def toString: String =
    s"""expect {
        |${whens.mkString("\n").indent()}
        |}""".stripMargin

  /**
    * @define subtypes whens
    * Returns whether the other $type has the same number of $subtypes as this $type and
    * that each pair of $subtypes is structurally equal.
    *
    * @param other the other $type to campare this $type to.
    */
  def structurallyEquals[RR >: R](other: ExpectBlock[RR]): Boolean =
    whens.size == other.whens.size && whens.zip(other.whens).forall{ case (a, b) => a.structurallyEquals(b) }
}