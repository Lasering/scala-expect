package codes.simon.expect.fluent

import java.nio.charset.Charset

import codes.simon.expect.core.Constants

import scala.concurrent.{Future, ExecutionContext}
import scala.concurrent.duration.FiniteDuration

trait Runnable[R] {
  val runnableParent: Runnable[R]

  /**
   * Runs this entire Expect tree.
   * @param timeout the maximum time to wait for each read.
   * @param charset the charset to use when decoding/encoding the read/write text.
   * @param bufferSize the size of the buffer to use when performing reads.
   * @param redirectStdErrToStdOut whether to redirect the stdErr of the spawned process to the stdOut.
   * @param ex the ExecutionContext upon which this Expect will be run.
   * @return a Future with either a Some if this Expect contained a returning action in one of the executed paths,
   *         or a None otherwise. If an exception occurred during the execution of the future then that exception will be
   *         returned in the Failure of the Future.
   */
  def run(timeout: FiniteDuration = Constants.TIMEOUT, charset: Charset = Constants.CHARSET,
          bufferSize: Int = Constants.BUFFER_SIZE, redirectStdErrToStdOut: Boolean = Constants.REDIRECT_STDERR_TO_STDOUT)
         (implicit ex: ExecutionContext): Future[Option[R]] = {
    runnableParent.run(timeout, charset, bufferSize)(ex)
  }
}
