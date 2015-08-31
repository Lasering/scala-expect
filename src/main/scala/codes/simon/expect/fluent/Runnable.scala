package codes.simon.expect.fluent

import java.nio.charset.Charset

import codes.simon.expect.core.Constants

import scala.concurrent.{Future, ExecutionContext}
import scala.concurrent.duration.FiniteDuration

trait Runnable[R] {
  val runnableParent: Runnable[R]

  def run(timeout: FiniteDuration = Constants.TIMEOUT, charset: Charset = Constants.CHARSET, bufferSize: Int = Constants.BUFFER_SIZE)
         (implicit ex: ExecutionContext): Future[Option[R]] = {
    runnableParent.run(timeout, charset, bufferSize)(ex)
  }
}
