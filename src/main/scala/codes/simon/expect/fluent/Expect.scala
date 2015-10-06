package codes.simon.expect.fluent

import java.nio.charset.Charset

import scala.concurrent.{Future, ExecutionContext}
import scala.concurrent.duration.FiniteDuration
import codes.simon.expect.core
import codes.simon.expect.core.Configs

import scala.reflect.ClassTag

class Expect[R: ClassTag](val command: String, val defaultValue: R) extends Runnable[R] with Expectable[R] {
  //The value we set here is irrelevant since we override the implementation of 'expect'.
  //We decided to set to 'this' to make it obvious that this is the root of all Expectables.
  val expectableParent: Expectable[R] = this
  protected[fluent] var expects = Seq.empty[ExpectBlock[R]]
  override def expect: ExpectBlock[R] = {
    val block = new ExpectBlock(this)
    expects :+= block
    block
  }

  //The value we set here is irrelevant since we override the implementation of 'run'.
  //We decided to set to 'this' to make it obvious that this is the root of all Runnables.
  val runnableParent: Runnable[R] = this
  override def run(timeout: FiniteDuration = Configs.timeout, charset: Charset = Configs.charset,
                   bufferSize: Int = Configs.bufferSize, redirectStdErrToStdOut: Boolean = Configs.redirectStdErrToStdOut)
                  (implicit ex: ExecutionContext): Future[R] = {
    new core.Expect[R](command, defaultValue)(expects.map(_.toCore):_*).run(timeout, charset, bufferSize)(ex)
  }

  override def toString =
    s"""Expect:
        |\tCommand: $command
        |\tDefaultValue: $defaultValue
        |\t${expects.mkString("\n\t")}
     """.stripMargin
}