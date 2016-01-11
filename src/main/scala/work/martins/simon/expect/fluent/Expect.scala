package work.martins.simon.expect.fluent

import java.nio.charset.Charset

import com.typesafe.config.Config
import work.martins.simon.expect.StringUtils._
import work.martins.simon.expect.{Settings, core}

import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{ExecutionContext, Future}

class Expect[R](val command: Seq[String], val defaultValue: R, val settings: Settings = new Settings())
  extends Runnable[R] with Expectable[R] {

  def this(command: Seq[String], defaultValue: R, config: Config) = {
    this(command, defaultValue, new Settings(config))
  }
  def this(command: String, defaultValue: R, settings: Settings) = {
    this(splitBySpaces(command), defaultValue, settings)
  }
  def this(command: String, defaultValue: R, config: Config) = {
    this(command, defaultValue, new Settings(config))
  }
  def this(command: String, defaultValue: R) = {
    this(command, defaultValue, new Settings())
  }

  require(command.nonEmpty, "Expect must have a command to run.")
  import settings._

  //The value we set here is irrelevant since we override the implementation of 'expect'.
  //We decided to set expectableParent to 'this' to make it obvious that this is the root of all Expectables.
  protected val expectableParent: Expectable[R] = this
  protected[fluent] var expects = Seq.empty[ExpectBlock[R]]
  override def expect: ExpectBlock[R] = {
    val block = new ExpectBlock(this)
    expects :+= block
    block
  }
  override def addExpectBlock(f: Expect[R] => Unit): Expect[R] = {
    f(this)
    this
  }

  /**
    * @return the core.Expect equivalent of this fluent.Expect.
    */
  def toCore: core.Expect[R] = fluentExpectToCoreExpect(this)

  //The value we set here is irrelevant since we override the implementation of 'run'.
  //We decided to set runnableParent to 'this' to make it obvious that this is the root of all Runnables.
  protected val runnableParent: Runnable[R] = this
  override def run(timeout: FiniteDuration = timeout, charset: Charset = charset,
                   bufferSize: Int = bufferSize, redirectStdErrToStdOut: Boolean = redirectStdErrToStdOut)
                  (implicit ex: ExecutionContext): Future[R] = {
    toCore.run(timeout, charset, bufferSize, redirectStdErrToStdOut)(ex)
  }

  override def toString: String =
    s"""Expect:
        |\tCommand: $command
        |\tDefaultValue: $defaultValue
        |${expects.mkString("\n").indent()}
     """.stripMargin
  override def equals(other: Any): Boolean = other match {
    case that: Expect[R] =>
        command == that.command &&
        defaultValue == that.defaultValue &&
        settings == that.settings &&
        expects == that.expects
    case _ => false
  }
  override def hashCode(): Int = {
    val state: Seq[Any] = Seq(command, defaultValue, settings, expects)
    state.map(_.hashCode()).foldLeft(0)((a, b) => 31 * a + b)
  }
}