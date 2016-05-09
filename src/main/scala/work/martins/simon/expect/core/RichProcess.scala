package work.martins.simon.expect.core

import java.io.{BufferedInputStream, BufferedOutputStream, EOFException}
import java.nio.charset.Charset
import java.util.concurrent.{LinkedBlockingDeque, TimeUnit, TimeoutException}

import scala.concurrent.blocking
import scala.concurrent.duration.FiniteDuration
import scala.util.Try

/**
 * Launches a `java.lang.Process` with methods to read and print from its stdout and stdin respectively.
  *
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

  val stdOut = new BufferedInputStream(process.getInputStream)
  val stdIn = new BufferedOutputStream(process.getOutputStream)

  val blockingQueue = new LinkedBlockingDeque[Try[String]]()

  def spawnDaemonThread(f: Thread => Unit): Thread = {
    val thread = new Thread() { override def run() = { f(this) } }
    thread.setDaemon(true)
    thread.start()
    thread
  }

  val stdOutThread = spawnDaemonThread { thread =>
    val array = Array.ofDim[Byte](bufferSize)

    var readEOF = false
    while (!thread.isInterrupted) {
      while (!readEOF) {
        val t = Try {
          stdOut.read(array) match {
            case -1 =>
              readEOF = true
              throw new EOFException()
            case n =>
              val s = new String(array, 0, n, charset)
              //Re-zeros the array to ensure we don't garble the next output
              (0 until n).foreach(array(_) = 0)
              s
          }
        }
        blockingQueue.put(t)
      }
    }
  }

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
   * Tries to read `bufferSize` bytes from the process stdOut.
   * If no bytes are read within `timeout` a `TimeoutException` will be thrown.
   * If the end of file is reached an `EOFException` is thrown.
   * Otherwise, a String encoded with `charset` is created from the read bytes.
   * This method awaits for the result of a future, aka, is blocking.
    *
   * @return a String created from the read bytes encoded with `charset`.
   */
  def read(): String = {
    val data = blocking {
      blockingQueue.poll(deadline.timeLeft.toMillis, TimeUnit.MILLISECONDS)
    }
    Option(data) match {
      case None => throw new TimeoutException
      case Some(t) => t.get // This will throw the EOFException if the output has already reached the end
    }
  }

  /**
   * Writes to the underlying `OutputStream` the bytes obtained from decoding `text` using `charset`.
   * Followed by a flush of the `OutputStream`.
    *
    * @param text the text to write to the `OutputStream`.
   */
  def print(text: String): Unit = {
    stdIn.write(text.getBytes(charset))
    stdIn.flush()
  }

  /**
   * If the underlying process is still alive it's destroy method is invoked and
   * the input and output streams are closed.
   */
  def destroy(): Unit = if (process.isAlive) {
    process.destroy()
    Try {
      stdOutThread.interrupt() //This also closes the stdOutSink
      stdOut.close()
      stdIn.close()
    }
  }
}
