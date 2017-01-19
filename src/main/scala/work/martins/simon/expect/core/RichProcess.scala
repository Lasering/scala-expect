package work.martins.simon.expect.core

import java.io.{EOFException, IOException}
import java.nio.ByteBuffer
import java.nio.charset.Charset
import java.util.concurrent.{BlockingDeque, LinkedBlockingDeque, TimeUnit, TimeoutException}

import scala.concurrent.blocking
import scala.concurrent.duration.FiniteDuration

import com.typesafe.scalalogging.LazyLogging
import com.zaxxer.nuprocess.{NuAbstractProcessHandler, NuProcess, NuProcessBuilder}
import work.martins.simon.expect.{FromInputStream, Settings, StdErr, StdOut}

object RichProcess {
  def apply(command: Seq[String], settings: Settings): RichProcess = {
    new RichProcess(command, settings.timeout, settings.charset, settings.redirectStdErrToStdOut)
  }
  def apply(command: Seq[String], timeout: FiniteDuration, charset: Charset, redirectStdErrToStdOut: Boolean): RichProcess = {
    new RichProcess(command, timeout, charset, redirectStdErrToStdOut)
  }
}

/**
  * Launches a `java.lang.Process` with methods to read and print from its stdout and stdin respectively.
  *
  * @param command the command to launch and its arguments.
  * @param timeout how much time to wait when performing a read.
  * @param charset the charset used for encoding and decoding the Strings.
  */
class RichProcess(val command: Seq[String], val timeout: FiniteDuration, val charset: Charset, val redirectStdErrToStdOut: Boolean) {
  protected val stdInQueue = new LinkedBlockingDeque[String]()
  protected val stdOutQueue = new LinkedBlockingDeque[Either[EOFException, String]]()
  protected val stdErrQueue = new LinkedBlockingDeque[Either[EOFException, String]]()
  
  protected val handler = new ProcessHandler()
  val process: NuProcess = new NuProcessBuilder(handler, command:_*).start()
  if (handler.failedAtStart) throw new IOException()
  
  class ProcessHandler extends NuAbstractProcessHandler with LazyLogging {
    var nuProcess: NuProcess = _
  
    override def onStart(nuProcess: NuProcess): Unit = {
      this.nuProcess = nuProcess
    }
  
    private var unwrittenBytes = Array.emptyByteArray
    override def onStdinReady(buffer: ByteBuffer): Boolean = {
      if (unwrittenBytes.length > 0) {
        // We have unwritten bytes which take precedence over "normal" ones
        write(buffer, unwrittenBytes)
      } else {
        // We know the remove will not throw an exception because onStdinReady will only be called when
        // process.wantWrite() is invoked. And wantWrite is only invoked inside the method print which:
        //  · First puts a string to the stdInQueue
        //  · Then invokes wantWrite.
        write(buffer, stdInQueue.remove().getBytes(charset))
      }
    }
    private def write(buffer: ByteBuffer, bytes: Array[Byte]): Boolean = {
      if (bytes.length <= buffer.capacity()) {
        buffer.put(bytes)
        buffer.flip()
        unwrittenBytes = Array.emptyByteArray
        false
      } else {
        val (bytesWritableRightNow, bytesToBeWritten) = bytes.splitAt(buffer.capacity())
        buffer.put(bytesWritableRightNow)
        buffer.flip()
        unwrittenBytes = bytesToBeWritten
        true
      }
    }
    
    override def onStdout(buffer: ByteBuffer, closed: Boolean): Unit = {
      read(buffer, closed, queueOf(StdOut))
    }
    override def onStderr(buffer: ByteBuffer, closed: Boolean): Unit = {
      read(buffer, closed, queueOf(StdErr))
    }
    private def read(buffer: ByteBuffer, closed: Boolean, toQueue: BlockingDeque[Either[EOFException, String]]): Unit = {
      if (closed) {
        toQueue.put(Left(new EOFException()))
      } else if (buffer.hasRemaining) {
        val bytes = Array.ofDim[Byte](buffer.remaining())
        buffer.get(bytes)
        toQueue.put(Right(new String(bytes, charset)))
      }
    }
  
    var failedAtStart = false
    override def onExit(statusCode: Int): Unit = {
      if (Option(nuProcess).isEmpty) {
        // We do not have an nuProcess but the process already terminated signalling there has some kind of
        // problem starting the process (eg. command not found)
        // See https://github.com/brettwooldridge/NuProcess/issues/67 as to why we implemented this in this horrific way
        failedAtStart = true
      }
    }
  }
  
  protected var deadline = timeout.fromNow
  
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
    * Tries to read from the selected InputStream of the process.
    * If no bytes are read within `timeout` a `TimeoutException` will be thrown.
    * If the end of file is reached an `EOFException` is thrown.
    * Otherwise, a String encoded with `charset` is created from the read bytes.
    * This method awaits for the result of a future, aka, is blocking.
    *
    * @return a String created from the read bytes encoded with `charset`.
    */
  def read(from: FromInputStream = StdOut): String = {
    val data = blocking {
      queueOf(from).pollFirst(deadline.timeLeft.toMillis, TimeUnit.MILLISECONDS)
    }
    Option(data) match {
      case None => throw new TimeoutException
      case Some(Left(eof)) => throw eof
      case Some(Right(s)) => s
    }
  }

  def queueOf(from: FromInputStream = StdOut): BlockingDeque[Either[EOFException, String]] = from match {
    case StdErr if !redirectStdErrToStdOut => stdErrQueue
    case _ => stdOutQueue
  }
  
  /**
    * Writes to the underlying `OutputStream` the bytes obtained from decoding `text` using `charset`.
    * Followed by a flush of the `OutputStream`.
    *
    * @param text the text to write to the `OutputStream`.
    */
  def print(text: String): Unit = {
    stdInQueue.put(text)
    process.wantWrite()
  }

  /**
    * If the underlying process is still alive it's destroy method is invoked and
    * the input and output streams are closed.
    */
  def destroy(): Unit = if (process.isRunning) process.destroy(true)

  def withCommand(newCommand: Seq[String]): RichProcess = {
    new RichProcess(newCommand, timeout, charset, redirectStdErrToStdOut)
  }
}
