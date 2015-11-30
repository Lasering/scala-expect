package work.martins.simon.expect.fluent

import java.nio.charset.Charset

import scala.concurrent.{Future, ExecutionContext}
import scala.concurrent.duration.FiniteDuration

import scala.reflect.ClassTag

import work.martins.simon.expect.core
import work.martins.simon.expect.core.Configs

class Expect[R: ClassTag](val command: Seq[String], val defaultValue: R) extends Runnable[R] with Expectable[R] {
  def this(command: String, defaultValue: R = Unit) = {
    this(command.split("""\s+""").filter(_.nonEmpty).toSeq, defaultValue)
  }

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
  def toCore: core.Expect[R] = new core.Expect[R](command, defaultValue)(expects.map(_.toCore):_*)

  //The value we set here is irrelevant since we override the implementation of 'run'.
  //We decided to set runnableParent to 'this' to make it obvious that this is the root of all Runnables.
  protected val runnableParent: Runnable[R] = this
  override def run(timeout: FiniteDuration = Configs.timeout, charset: Charset = Configs.charset,
                   bufferSize: Int = Configs.bufferSize,
                   redirectStdErrToStdOut: Boolean = Configs.redirectStdErrToStdOut)
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