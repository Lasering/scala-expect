package work.martins.simon.expect.core

import java.nio.charset.Charset

import com.typesafe.config.Config
import com.typesafe.scalalogging.LazyLogging
import work.martins.simon.expect.StringUtils._
import work.martins.simon.expect.Settings

import scala.concurrent._
import scala.concurrent.duration.FiniteDuration

class Expect[R](val command: Seq[String], val defaultValue: R, val settings: Settings = new Settings())
               (val expectBlocks: ExpectBlock[R]*) extends LazyLogging {
  def this(command: Seq[String], defaultValue: R, config: Config)(expects: ExpectBlock[R]*) = {
    this(command, defaultValue, new Settings(config))(expects: _*)
  }

  def this(command: String, defaultValue: R, settings: Settings)(expectBlocks: ExpectBlock[R]*) = {
    this(splitBySpaces(command), defaultValue, settings)(expectBlocks: _*)
  }

  def this(command: String, defaultValue: R, config: Config)(expectBlocks: ExpectBlock[R]*) = {
    this(command, defaultValue, new Settings(config))(expectBlocks: _*)
  }

  def this(command: String, defaultValue: R)(expectBlocks: ExpectBlock[R]*) = {
    this(command, defaultValue, new Settings())(expectBlocks: _*)
  }

  require(command.nonEmpty, "Expect must have a command to run.")

  import settings._

  def run(timeout: FiniteDuration = timeout, charset: Charset = charset,
          bufferSize: Int = bufferSize, redirectStdErrToStdOut: Boolean = redirectStdErrToStdOut)
         (implicit ex: ExecutionContext): Future[R] = {
    val richProcess = RichProcess(command, timeout, charset, bufferSize, redirectStdErrToStdOut)
    innerRun(richProcess, IntermediateResult(output = "", defaultValue, Continue), expectBlocks.toList)
  }

  private val expectID = s"[ID:${hashCode()}]"

  protected def innerRun(richProcess: RichProcess, intermediateResult: IntermediateResult[R],
                         expectBlocks: Seq[ExpectBlock[R]])
                        (implicit ec: ExecutionContext): Future[R] = expectBlocks match {
    case headExpectBlock :: remainingExpectBlocks =>
      logger.info(s"$expectID Now running:\n$headExpectBlock")
      val result = headExpectBlock.run(richProcess, intermediateResult, expectID).flatMap {
        case result @ IntermediateResult(_, _, action) => action match {
          case Continue =>
            innerRun(richProcess, result, remainingExpectBlocks)
          case Terminate =>
            innerRun(richProcess, result, List.empty)
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

  //TODO: make expect composable by implementing:
  // map[T](f: R => T): Expect[T]
  // flatMap[T](f: R => Expect[T]): Expect[T]

  override def toString: String =
    s"""Expect:
       |\tHashCode: ${hashCode()}
       |\tCommand: $command
       |\tDefaultValue: $defaultValue
       |${expectBlocks.mkString("\n").indent()}
     """.stripMargin

  /**
    * Returns whether `other` is an Expect with the same `command`, the same `defaultValue`, the same `settings` and
    * the same `expects` as this `Expect`.
    *
    * In the cases that `expects` contains an Action with a function, eg. Returning, this method will return false,
    * because equality is not defined for functions.
    *
    * The method `structurallyEqual` can be used to test that two expects contain the same structure.
 *
    * @param other
    */
  override def equals(other: Any): Boolean = other match {
    case that: Expect[R] =>
      command == that.command &&
      defaultValue == that.defaultValue &&
      settings == that.settings &&
      expectBlocks == that.expectBlocks
    case _ => false
  }

  /**
    * Returns whether the other expect has the same command, the same defaultValue, the same settings and
    * the same expects structurally. The expects are structurally equal if there are the same number of expects and each
    * expect has the same number of Whens with the same structure.
 *
    * @param other
    */
  def structurallyEquals(other: Expect[R]): Boolean = {
    command == other.command &&
      defaultValue == other.defaultValue &&
      settings == other.settings &&
      expectBlocks.size == other.expectBlocks.size && expectBlocks.zip(other.expectBlocks).forall{ case (a, b) => a.structurallyEquals(b) }
  }
  override def hashCode(): Int = {
    val state: Seq[Any] = Seq(command, defaultValue, settings, expectBlocks)
    state.map(_.hashCode()).foldLeft(0)((a, b) => 31 * a + b)
  }
}