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
    val expectID = s"[ID:${hashCode()}]"
    logger.info(s"""$expectID Launched: "${command.mkString(" ")}"""")

    def successful(intermediateResult: IntermediateResult[R]): Future[R] = {
      logger.info(s"$expectID Finished returning: ${intermediateResult.value}")
      richProcess.destroy()
      Future.successful(intermediateResult.value)
    }

    def innerRun(intermediateResult: IntermediateResult[R], expectBlocks: Seq[ExpectBlock[R]]): Future[R] = {
      expectBlocks.headOption.map { headExpectBlock =>
        //We still have expect blocks to run
        val result = headExpectBlock.run(richProcess, intermediateResult, expectID).flatMap { innerResult =>
          innerResult.executionAction match {
            case Continue =>
              //Continue with the remaining expect blocks
              innerRun(innerResult, expectBlocks.tail)
            case Terminate =>
              //Simply terminate with the innerResult
              successful(innerResult)
            case ChangeToNewExpect(newExpect) =>
              richProcess.destroy()
              newExpect.asInstanceOf[Expect[R]].run(richProcess.timeout, richProcess.charset, richProcess.bufferSize)
          }
        }
        //If we get an exception while running the head expect block we want to make sure the rich process is destroyed.
        result onFailure { case _ => richProcess.destroy() }
        result
      } getOrElse {
        //No more expect blocks. We just return the current intermediateResult
        successful(intermediateResult)
      }
    }

    innerRun(IntermediateResult(output = "", defaultValue, Continue), expectBlocks)
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
    * @param other the other Expect to campare this Expect to.
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
    * @param other the other Expect to campare this Expect to.
    */
  def structurallyEquals(other: Expect[R]): Boolean = {
    command == other.command &&
      defaultValue == other.defaultValue &&
      settings == other.settings &&
      expectBlocks.size == other.expectBlocks.size &&
      expectBlocks.zip(other.expectBlocks).forall{ case (a, b) => a.structurallyEquals(b) }
  }
  override def hashCode(): Int = {
    val state: Seq[Any] = Seq(command, defaultValue, settings, expectBlocks)
    state.map(_.hashCode()).foldLeft(0)((a, b) => 31 * a + b)
  }
}