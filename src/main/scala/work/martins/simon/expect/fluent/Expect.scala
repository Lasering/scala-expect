package work.martins.simon.expect.fluent

import java.nio.charset.Charset

import com.typesafe.config.Config
import work.martins.simon.expect.StringUtils._
import work.martins.simon.expect.{Settings, core}

import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{ExecutionContext, Future}
import scala.reflect.ClassTag

class Expect[R: ClassTag](val command: Seq[String], val defaultValue: R, val settings: Settings = new Settings())
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

  /**
    * Add arbitrary `ExpectBlock`s to this `Expect`.
    *
    * This is helpful to refactor code. For example: imagine you have an error case you want to add to multiple expects.
    * You could leverage this method to do so in the following way:
    * {{{
    *   def errorCaseExpectBlock: Expect[String] => Unit = { expect =>
    *     expect.expect
    *       .when("Some error")
    *         .returning("Got some error")
    *   }
    *
    *   //Then in your expects
    *   def parseOutputA: Expect[String] = {
    *     val e = new Expect("some command", "")
    *     e.expect(...)
    *     e.expect
    *       .when(...)
    *         .action1
    *       .when(...)
    *         .action2
    *     e.addExpectBlock(errorCaseExpectBlock)
    *   }
    *
    *   def parseOutputB: Expect[String] = {
    *     val e = new Expect("some command", "")
    *     e.expect
    *       .when(...)
    *         .action1
    *         .action2
    *       .when(...)
    *         .action1
    *     e.expect(...)
    *       .action2
    *     e.addExpectBlock(errorCaseExpectBlock)
    *   }
    * }}}
    *
    * @param f function that adds `ExpectBlock`s.
    * @return this `Expect`.
    */
  def addExpectBlock(f: Expect[R] => Unit): Expect[R] = {
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
        |\t${expects.mkString("\n\t")}
     """.stripMargin
}