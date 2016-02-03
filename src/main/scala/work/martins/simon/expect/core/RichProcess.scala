package work.martins.simon.expect.core

import java.io.EOFException
import java.nio.charset.Charset

import scala.concurrent._
import scala.concurrent.duration.FiniteDuration
import scala.util.Try

/**
 * Launches a `java.lang.Process` with methods to read and print from its stdout and stdin respectively.
 * @param command the command to launch and its arguments.
 * @param timeout how much time to wait when performing a read.
 * @param charset the charset used for encoding and decoding the Strings.
 * @param bufferSize how many bytes to read.
 * @param redirectStdErrToStdOut whether to redirect stdErr to stdOut.
 */
case class RichProcess(command: Seq[String], timeout: FiniteDuration, charset: Charset, bufferSize: Int,
                       redirectStdErrToStdOut: Boolean) {
  val processBuilder = new ProcessBuilder(command:_*)
  processBuilder.redirectErrorStream(redirectStdErrToStdOut)
  val process = processBuilder.start()

  val stdout = process.getInputStream
  val stdin = process.getOutputStream
  private var deadline = timeout.fromNow

  private val array = Array.ofDim[Byte](bufferSize)

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
      blocking {
        stdout.read(array)
      } match {
        case -1 => throw new EOFException()
        case n =>
          val s = new String(array, 0, n, charset)
          //Re-zeros the array to ensure we don't garble the next output
          (0 until n).foreach(array(_) = 0)
          s
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