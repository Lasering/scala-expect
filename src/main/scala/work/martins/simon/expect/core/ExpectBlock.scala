package work.martins.simon.expect.core

import java.io.EOFException
import scala.concurrent.duration.Deadline
import scala.concurrent.{ExecutionContext, Future, TimeoutException}
import com.typesafe.scalalogging.LazyLogging
import work.martins.simon.expect.{/=>, FromInputStream}
import work.martins.simon.expect.StringUtils.*
import work.martins.simon.expect.FromInputStream.*
import work.martins.simon.expect.core.ExecutionAction.*

final case class ExpectBlock[+R](whens: When[R]*) extends LazyLogging:
  require(whens.nonEmpty, "ExpectBlock must have at least a When.")
  
  private def timeoutWhen[RR >: R](runContext: RunContext[RR])(using deadline: Deadline): (When[RR], RunContext[RR]) =
    logger.info(runContext.withId(s"Read timed out after ${deadline.time}."))
    whens.collectFirst {
      case when: TimeoutWhen[R] => (when, runContext)
    }.getOrElse(throw new TimeoutException)
  
  private def endOfFileWhen[RR >: R](runContext: RunContext[RR])(using deadline: Deadline): (When[RR], RunContext[RR]) =
    logger.info(runContext.withId(s"Read returned EndOfFile."))
    whens.collectFirst {
      case when @ EndOfFileWhen(runContext.readFrom, _) => (when, runContext)
    }.getOrElse(throw new EOFException)
  
  private def obtainMatchingWhen[RR >: R](runContext: RunContext[RR])(read: RunContext[RR] => (FromInputStream, String))
                                         (using ex: ExecutionContext, deadline: Deadline): Future[(When[RR], RunContext[RR])] =
    // Try to match against the output we have so far
    whens.find(w => w.readFrom == runContext.readFrom && w.matches(runContext.output)) match
      case Some(when) =>
        // The existing output was enough to get a matching when
        Future.successful((when, runContext))
      case None if deadline.hasTimeLeft() =>
        // We need more output and since we still have time left we are going to try and read more output.
        Future {
          if runContext.output.nonEmpty then logger.debug(runContext.withId(s"Did not match with last ${runContext.readFrom} output."))
          logger.trace(runContext.withId(s"Time left in deadline ${deadline.timeLeft}"))
          val (from, text) = read(runContext)
          val newRunContext = runContext.readingFrom(from).withOutput(_ + text)
          logger.info(runContext.withId(s"Newly read text from $from:\n$text"))
          logger.debug(runContext.withId(s"New $from output:\n${newRunContext.output}"))
          newRunContext
        } flatMap { newRunContext =>
          obtainMatchingWhen(newRunContext)(read)
        } recover {
          case _: EOFException => endOfFileWhen(runContext)
          case _: TimeoutException => timeoutWhen(runContext)
        }
      case _ =>
        // We have no match nor time to read more output, so we just timeout.
        Future(timeoutWhen(runContext))
  
  /**
    * First checks if any of the Whens of this ExpectBlock matches against the last output.
    * If one such When exists then the result of executing it is returned.
    * Otherwise continuously reads text from `process` until one of the Whens of this ExpectBlock matches against it.
    * If it is not able to do so before the timeout expires a TimeoutException will be thrown inside the Future.
    *
    * @param runContext the current run context of this expect execution.
    * @param ex the ExecutionContext upon which the internal future is ran.
    * @return the result of executing the When that matches either `lastOutput` or the text read from `process`.
    *         Or a TimeoutException.
    */
  private[core] def run[RR >: R](runContext: RunContext[RR])(using ex: ExecutionContext): Future[RunContext[RR]] =
    given Deadline = runContext.settings.scaledTimeout.fromNow
    
    val matchingWhen = whens.map(_.readFrom).distinct match
      case Seq(from) =>
        logger.debug(runContext.withId(s"Now running (reading from $from):\n$this"))
        obtainMatchingWhen(runContext.readingFrom(from)) { innerContext =>
          logger.debug(innerContext.withId(s"Going to read more from $from."))
          (from, innerContext.process.read(from))
        }
      case _ =>
        logger.debug(runContext.withId(s"Now running (reading from StdOut and StdErr concurrently):\n$this"))
        logger.debug(runContext.withId("Going to try and match with last StdErr output."))
        whens.find(w => w.matches(runContext.stdErrOutput) && w.readFrom == StdErr) match
          case Some(when) => Future.successful((when, runContext))
          case None =>
            if runContext.stdErrOutput.nonEmpty then logger.debug(runContext.withId("Did not match with last StdErr output."))
            logger.debug(runContext.withId("Going to try and match with last StdOut output."))
            obtainMatchingWhen(runContext.readingFrom(StdOut)) { innerContext =>
              logger.debug(runContext.withId(s"Going to concurrently read from StdOut and StdErr."))
              innerContext.process.readOnFirstInputStream()
            }
    
    matchingWhen map { case (when, newRunContext) =>
      logger.info(newRunContext.withId(s"Matched with:\n$when"))
      when.run(newRunContext)
    }
  
  def map[T](f: R => T): ExpectBlock[T] = ExpectBlock(whens.map(_.map(f))*)
  def flatMap[T](f: R => Expect[T]): ExpectBlock[T] = ExpectBlock(whens.map(_.flatMap(f))*)
  def transform[T](flatMapPF: R /=> Expect[T], mapPF: R /=> T): ExpectBlock[T] = ExpectBlock(whens.map(_.transform(flatMapPF, mapPF))*)
  
  override def toString: String =
    s"""expect {
        |${whens.mkString("\n").indent()}
        |}""".stripMargin
  
  /**
    * Returns whether the `other` ExpectBlock has structurally the same whens as this ExpectBlock.
    *
    * @param other the other ExpectBlock to campare this ExpectBlock to.
    */
  def structurallyEquals[RR >: R](other: ExpectBlock[RR]): Boolean =
    whens.size == other.whens.size && whens.zip(other.whens).forall(_ structurallyEquals _)