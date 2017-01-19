package work.martins.simon.expect.core

import java.io.EOFException

import scala.concurrent.{ExecutionContext, Future, Promise, TimeoutException}
import scala.util.{Failure, Success, Try}

import com.typesafe.scalalogging.LazyLogging
import work.martins.simon.expect.StringUtils._
import work.martins.simon.expect.{FromInputStream, StdErr, StdOut}

/**
  * @define type ExpectBlock
  */
final case class ExpectBlock[R](whens: When[R]*) extends LazyLogging {
  require(whens.nonEmpty, "ExpectBlock must have at least a When.")
  
  private def obtainMatchingWhen(process: RichProcess, context: Context[R], readFrom: FromInputStream, matchReadFrom: Boolean = true)
                                (implicit ex: ExecutionContext): Future[(When[R], Context[R])] = {
    def timeout(innerContext: Context[R]): (When[R], Context[R]) = {
      logger.info(innerContext.withId(s"Read timed out after ${process.timeout}."))
      whens.collectFirst { case when @ TimeoutWhen() => (when, innerContext) }
        .getOrElse(throw new TimeoutException)
    }
    
    def matchFrom(from: FromInputStream): Boolean = if (matchReadFrom) from == readFrom else true
    
    def obtainMatchingWhenInner(innerContext: Context[R]): Future[(When[R], Context[R])] = {
      // Try to match against the output we have so far
      whens.find(w => w.matches(innerContext.output) && matchFrom(w.readFrom)) match {
        case Some(when) =>
          // The existing output was enough to get a matching when
          Future.successful((when, innerContext))
        case None if process.deadLineHasTimeLeft() =>
          // We need more output and since we still have time left we are going to try and read more output.
          Future {
            logger.info(innerContext.withId(s"Did not match with last $readFrom output. Going to read more."))
            val readText = process.read(readFrom)
            val newContext = innerContext.withOutput(_ + readText)
            logger.info(innerContext.withId(s"Newly read text from $readFrom:\n$readText"))
            logger.debug(innerContext.withId(s"New $readFrom output:\n${newContext.output}"))
            newContext
          } flatMap {
            obtainMatchingWhenInner
          } recover {
            case e: EOFException =>
              logger.info(innerContext.withId(s"Read returned EndOfFile."))
              whens.collectFirst {
                case when @ EndOfFileWhen(from) if matchFrom(from) => (when, innerContext)
              }.getOrElse(throw e)
            case _: TimeoutException =>
              timeout(innerContext)
          }
        case _ =>
          // We could not obtain a matching when and we have no more time to try and read more output.
          Future(timeout(innerContext))
      }
    }
    
    obtainMatchingWhenInner(context.copy(readFrom = readFrom))
  }
  
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
  def run(process: RichProcess, context: Context[R])(implicit ex: ExecutionContext): Future[Context[R]] = {
    logger.info(context.withId(s"Now running:\n$this"))
  
    process.resetDeadline()
    val matchingWhen = whens.map(_.readFrom).distinct match {
      case Seq(from) => // From a single InputStream
        obtainMatchingWhen(process, context, from)
      case _ if process.redirectStdErrToStdOut => // From StdOut and StdErr, but StdErr is being redirected to StdOut
        // FIXME: this might lead to some unexpected results
        // In this case we cannot match the readFroms. Because if there exists a when with readFrom = StdErr,
        // then that when would never be returned since we are reading explicitly from StdOut.
        obtainMatchingWhen(process, context, StdOut, matchReadFrom = false)
      case _ => // From StdOut and StdErr
        val p = Promise[(When[R], Context[R])]()
        
        def onCompleteHandler(readFrom: FromInputStream)(t: Try[(When[R], Context[R])]): Unit = {
          val failedToComplete = !p.tryComplete(t)
          if (failedToComplete) {
            // The other future terminated first. So its result (whether an EOF, a string or a timeout) is the winner
            t match {
              case Failure(_: EOFException) | Success((EndOfFileWhen(`readFrom`), _)) =>
                // This future read an EOF from `readFrom` after the other future terminated.
                // So we just want to add the EOF back to the queue.
                process.queueOf(readFrom).addFirst(Left(new EOFException()))
              case Success((_, newContext)) =>
                // This future read new text after the other future terminated.
                // So we just want to add the text back to the queue.
                val oldOutput = context.outputOf(readFrom)
                val newOutput = newContext.outputOf(readFrom)
                process.queueOf(readFrom).addFirst(Right(newOutput.substring(oldOutput.length)))
              case _ => // Do nothing in any other case aka the other future wins
            }
          }
        }
  
        // This is a slightly more complicated Future.firstCompletedOf since the onCompleteHandler
        // tries to complete the promise but it also changes the process in some cases.
        obtainMatchingWhen(process, context, StdOut) onComplete onCompleteHandler(StdOut)
        obtainMatchingWhen(process, context, StdErr) onComplete onCompleteHandler(StdErr)
        
        p.future
    }
    matchingWhen map { case (when, newContext) =>
      logger.info(newContext.withId(s"Matched with:\n$when"))
      when.execute(process, newContext)
    }
  }

  private[core] def map[T](f: R => T): ExpectBlock[T] = ExpectBlock(whens.map(_.map(f)):_*)
  private[core] def flatMap[T](f: R => Expect[T]): ExpectBlock[T] = ExpectBlock(whens.map(_.flatMap(f)):_*)
  private[core] def transform[T](flatMapPF: PartialFunction[R, Expect[T]])(mapPF: PartialFunction[R, T]): ExpectBlock[T] = {
    ExpectBlock(whens.map(_.transform(flatMapPF)(mapPF)):_*)
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