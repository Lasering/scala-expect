package work.martins.simon.expect.core

import java.io.EOFException
import java.nio.charset.Charset

import scala.concurrent._
import scala.concurrent.duration.FiniteDuration
import scala.util.Try

/**
 * Augments [[java.lang.Process]] with methods to read and print from its stdout and stdin respectively.
 * @param timeout how much time to wait when performing a read.
 * @param charset the charset used for encoding and decoding the Strings.
 * @param bufferSize how many bytes to read.
 */
case class RichProcess(command: Seq[String], timeout: FiniteDuration, charset: Charset, bufferSize: Int,
                       redirectStdErrToStdOut: Boolean) {
  val processBuilder = new ProcessBuilder(command:_*)
  processBuilder.redirectErrorStream(redirectStdErrToStdOut)
  val process = processBuilder.start()

  val stdout = process.getInputStream
  val stdin = process.getOutputStream
  private var deadline = timeout.fromNow

  /**
   * Resets the underlying deadline used when performing a `read`.
   * The new deadline is `timeout.fromNow`.
   */
  def resetDeadline(): Unit = deadline = timeout.fromNow
  /**
   * @return whether the current deadline has any time left.
   */
  def deadLineHasTimeLeft(): Boolean = deadline.hasTimeLeft()

  /**
   * Tries to read `bufferSize` bytes from the underlying `InputStream`.
   * If no bytes are read within `timeout` a `TimeoutException` will be thrown.
   * If the end of file is reached an `EOFException` is thrown.
   * Otherwise, a String encoded with `charset` is created from the read bytes.
   * This method awaits for the result of a future, aka, is blocking.
   * @param ex the `ExecutionContext` upon the internal `Future` will run.
   * @return a String created from the read bytes encoded with `charset`.
   */
  def read()(implicit ex: ExecutionContext): String = {
    Await.result(Future {
      val array = Array.ofDim[Byte](bufferSize)
      blocking {
        stdout.read(array)
      } match {
        case -1 => throw new EOFException()
        case n => new String(array, 0, n, charset)
      }
    }, deadline.timeLeft)
  }

  /**
   * Writes to the underlying `OutputStream` the bytes obtained from decoding `text` using `charset`.
   * Followed by a flush of the `OutputStream`.
   * @param text the text to write to the `OutputStream`.
   */
  def print(text: String): Unit = {
    stdin.write(text.getBytes(charset))
    stdin.flush()
  }

  /**
   * If the underlying process is still alive it's destroy method is invoked and
   * the input and output streams are closed.
   */
  def destroy(): Unit = if (process.isAlive) {
    process.destroy()
    Try {
      stdin.close()
      stdout.close()
    }
  }
}