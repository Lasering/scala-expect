package work.martins.simon.expect.fluent

import java.nio.charset.Charset

import work.martins.simon.expect.Settings

import scala.concurrent.{Future, ExecutionContext}
import scala.concurrent.duration.FiniteDuration

trait Runnable[R] {
  protected val settings: Settings

  protected val runnableParent: Runnable[R]

  /**
   * Runs this Expect.
   * @param timeout the maximum time to wait for each read.
   * @param charset the charset to use when decoding/encoding the read/write text.
   * @param bufferSize the size of the buffer to use when performing reads.
   * @param redirectStdErrToStdOut whether to redirect the stdErr of the spawned process to the stdOut.
   * @param ex the ExecutionContext upon which this Expect will be run.
   * @return a Future with the value returned by a `ReturningAction`, if no `ReturningAction` exists
   *         `defaultValue` will be returned. If an exception occurred during the execution of the future
   *         then that exception will be returned in the Failure of the Future.
   */
  def run(timeout: FiniteDuration = settings.timeout, charset: Charset = settings.charset,
          bufferSize: Int = settings.bufferSize, redirectStdErrToStdOut: Boolean = settings.redirectStdErrToStdOut)
         (implicit ex: ExecutionContext): Future[R] = {
    runnableParent.run(timeout, charset, bufferSize, redirectStdErrToStdOut)(ex)
  }
}
