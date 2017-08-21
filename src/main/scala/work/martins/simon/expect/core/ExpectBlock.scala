package work.martins.simon.expect.core

import java.io.EOFException

import com.typesafe.scalalogging.LazyLogging
import work.martins.simon.expect.StringUtils._
import work.martins.simon.expect.{StdErr, StdOut}

import scala.concurrent.duration.Deadline
import scala.concurrent.{ExecutionContext, Future, TimeoutException}

/**
  * @define type ExpectBlock
  */
final case class ExpectBlock[+R](whens: When[R]*) extends LazyLogging {
  require(whens.nonEmpty, "ExpectBlock must have at least a When.")
  
  private def timeout[RR >: R](runContext: RunContext[RR]): (When[RR], RunContext[RR]) = {
    logger.info(runContext.withId(s"Read timed out after ${runContext.settings.timeout}."))
    whens.collectFirst { case when @ TimeoutWhen() => (when, runContext) }
      .getOrElse(throw new TimeoutException)
  }

  private def read[RR >: R](runContext: RunContext[RR])(implicit deadline: Deadline): RunContext[RR] = {
    if (runContext.output.nonEmpty) {
      logger.debug(runContext.withId(s"Did not match with last ${runContext.readFrom} output. Going to read more."))
    }
    val readText = runContext.process.read(runContext.readFrom)
    val newRunContext = runContext.withOutput(_ + readText)
    logger.info(runContext.withId(s"Newly read text from ${runContext.readFrom}:\n$readText"))
    logger.debug(runContext.withId(s"New ${runContext.readFrom} output:\n${newRunContext.output}"))
    newRunContext
  }

  private def obtainMatchingWhen[RR >: R](runContext: RunContext[RR])(readFunction: RunContext[RR] => RunContext[RR])
                                (implicit ex: ExecutionContext, deadline: Deadline): Future[(When[RR], RunContext[RR])] = {
    // Try to match against the output we have so far
    whens.find(w => w.matches(runContext.output) && w.readFrom == runContext.readFrom) match {
      case Some(when) =>
        // The existing output was enough to get a matching when
        Future.successful((when, runContext))
      case None if deadline.hasTimeLeft() =>
        // We need more output and since we still have time left we are going to try and read more output.
        Future {
          readFunction(runContext)
        } flatMap { newRunContext =>
          obtainMatchingWhen(newRunContext)(readFunction)
        } recover {
          case e: EOFException =>
            logger.info(runContext.withId(s"Read returned EndOfFile."))
            whens.collectFirst {
              case when @ EndOfFileWhen(from) if from == runContext.readFrom => (when, runContext)
            }.getOrElse(throw e)
          case _: TimeoutException =>
            timeout(runContext)
        }
      case _ =>
        // We have no match nor time to read some more output, so we just timeout.
        Future(timeout(runContext))
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
  def run[RR >: R](runContext: RunContext[RR])(implicit ex: ExecutionContext): Future[RunContext[RR]] = {
    implicit val deadline: Deadline = runContext.settings.timeout.fromNow

    val matchingWhen = whens.map(_.readFrom).distinct match {
      case Seq(from) =>
        logger.debug(runContext.withId(s"Now running (reading from $from):\n$this"))
        obtainMatchingWhen(runContext.readingFrom(from))(read)
      case _ =>
        logger.debug(runContext.withId(s"Now running (reading from StdOut and StdErr concurrently):\n$this"))
        logger.debug(runContext.withId("Going to try and match with last StdErr output."))
        whens.find(w => w.matches(runContext.stdErrOutput) && w.readFrom == StdErr) match {
          case Some(when) => Future.successful((when, runContext))
          case None =>
            logger.debug(runContext.withId("Did not match with last StdErr output. Going to try and match with last StdOut output."))
            obtainMatchingWhen(runContext.readingFrom(StdOut)) { innerContext =>
              logger.debug(innerContext.withId(s"Did not match with last ${innerContext.readFrom} output." +
                s" Going to concurrently read from StdOut and StdErr."))
              logger.trace(innerContext.withId(s"Time left in deadline ${deadline.timeLeft}"))

              val (from, text) = innerContext.process.readOnFirstInputStream()
              val newRunContext = innerContext.readingFrom(from).withOutput(_ + text)
              logger.info(innerContext.withId(s"Newly read text from $from:\n$text"))
              logger.debug(innerContext.withId(s"New $from output:\n${newRunContext.output}"))
              newRunContext
            }
        }
    }

    matchingWhen map { case (when, newRunContext) =>
      logger.info(newRunContext.withId(s"Matched with:\n$when"))
      when.run(newRunContext)
    }
  }

  private[core] def map[T](f: R => T): ExpectBlock[T] = {
    ExpectBlock(whens.map(_.map(f)):_*)
  }
  private[core] def flatMap[T](f: R => Expect[T]): ExpectBlock[T] = {
    ExpectBlock(whens.map(_.flatMap(f)):_*)
  }
  private[core] def transform[T](flatMapPF: PartialFunction[R, Expect[T]], mapPF: PartialFunction[R, T]): ExpectBlock[T] = {
    ExpectBlock(whens.map(_.transform(flatMapPF, mapPF)):_*)
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
  def structurallyEquals[RR >: R](other: ExpectBlock[RR]): Boolean = {
    whens.size == other.whens.size && whens.zip(other.whens).forall{ case (a, b) => a.structurallyEquals(b) }
  }
  
  override def hashCode(): Int = whens.hashCode()
}