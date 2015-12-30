package work.martins.simon.expect.core

import java.nio.charset.Charset

import com.typesafe.config.Config
import com.typesafe.scalalogging.LazyLogging
import work.martins.simon.expect.StringUtils._
import work.martins.simon.expect.Settings

import scala.concurrent._
import scala.concurrent.duration.FiniteDuration

class Expect[R](val command: Seq[String], val defaultValue: R, val settings: Settings = new Settings())
               (expects: ExpectBlock[R]*) extends LazyLogging {
  def this(command: Seq[String], defaultValue: R, config: Config)(expects: ExpectBlock[R]*) = {
    this(command, defaultValue, new Settings(config))(expects:_*)
  }
  def this(command: String, defaultValue: R, settings: Settings)(expects: ExpectBlock[R]*) = {
    this(splitBySpaces(command), defaultValue, settings)(expects:_*)
  }
  def this(command: String, defaultValue: R, config: Config)(expects: ExpectBlock[R]*) = {
    this(command, defaultValue, new Settings(config))(expects:_*)
  }
  def this(command: String, defaultValue: R)(expects: ExpectBlock[R]*) = {
    this(command, defaultValue, new Settings())(expects:_*)
  }

  require(command.nonEmpty, "Expect must have a command to run.")
  import settings._

  def run(timeout: FiniteDuration = timeout, charset: Charset = charset,
          bufferSize: Int = bufferSize, redirectStdErrToStdOut: Boolean = redirectStdErrToStdOut)
         (implicit ex: ExecutionContext): Future[R] = {
    val richProcess = RichProcess(command, timeout, charset, bufferSize, redirectStdErrToStdOut)
    innerRun(richProcess, IntermediateResult("", defaultValue, Continue), expects.toList)
  }

  protected def innerRun(richProcess: RichProcess, intermediateResult: IntermediateResult[R],
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
  override def equals(other: Any): Boolean = other match {
    case that: Expect[R] =>
        command == that.command &&
        defaultValue == that.defaultValue &&
        settings == that.settings
    case _ => false
  }
  override def hashCode(): Int = {
    val state: Seq[Any] = Seq(command, defaultValue, settings)
    state.map(_.hashCode()).foldLeft(0)((a, b) => 31 * a + b)
  }
}