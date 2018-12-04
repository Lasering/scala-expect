package work.martins.simon.expect.core

import java.io.{EOFException, IOException}
import java.nio.ByteBuffer
import java.util.concurrent.{BlockingDeque, LinkedBlockingDeque, TimeUnit}

import com.typesafe.scalalogging.LazyLogging
import com.zaxxer.nuprocess.{NuAbstractProcessHandler, NuProcess, NuProcessBuilder}
import work.martins.simon.expect.{FromInputStream, Settings, StdErr, StdOut}

import scala.concurrent.duration.Deadline
import scala.concurrent.{TimeoutException, blocking}
import scala.collection.JavaConverters._

trait RichProcess {
  val command: Seq[String]
  val settings: Settings

  /**
    * Tries to read from the selected InputStream of the process.
    * If no bytes are read within `deadline` a `TimeoutException` should be thrown.
    * If the end of file is reached an `EOFException` should be thrown.
    * Otherwise, a String encoded with `settings.charset` is created from the read bytes.
    *
    * This method may block until the deadline expires. However when doing so its implementation
    * should be wrapped inside a `scala.concurrent.blocking`.
    *
    * Only one read operation - read or readOnFirstInputStream - is performed at the same time.
    * No concurrent reads will be made.
    *
    * @return a String created from the read bytes encoded with `charset`.
    */
  def read(from: FromInputStream = StdOut)(implicit deadline: Deadline): String
  /**
    * Tries to read from `StdOut` or `StdErr` of the process, whichever has output to offer first.
    * If no bytes are read within `timeout` a `TimeoutException` should be thrown.
    * If the end of file is reached an `EOFException` should be thrown.
    * Otherwise, a String encoded with `settings.charset` is created from the read bytes.
    *
    * This method may block up until the deadline expires.
    *
    * Only one read operation - read or readOnFirstInputStream - is performed at the same time.
    * No concurrent reads will be made.
    *
    * @return from which `InputStream` the output read from, and a String created from the read bytes encoded with `charset`.
    */
  def readOnFirstInputStream()(implicit deadline: Deadline): (FromInputStream, String)

  /**
    * Writes to the `StdIn` of the process the bytes obtained from decoding `text` using `settings.charset`.
    * @param text the text to write to the `OutputStream`.
    */
  def write(text: String): Unit

  /** Destroys the process if it's still alive. */
  def destroy(): Unit
}

case class NuProcessRichProcess(command: Seq[String], settings: Settings) extends RichProcess with LazyLogging {
  protected val stdInQueue = new LinkedBlockingDeque[String]()
  protected val stdOutQueue = new LinkedBlockingDeque[Either[EOFException, String]]()
  protected val stdErrQueue = new LinkedBlockingDeque[Either[EOFException, String]]()
  protected val readAvailableOnQueue = new LinkedBlockingDeque[FromInputStream]()

  protected val handler = new ProcessHandler()
  val process: NuProcess = new NuProcessBuilder(handler, command:_*).start()

  // See https://github.com/brettwooldridge/NuProcess/issues/67 as to why this code exists
  if (!handler.startedNormally) throw new IOException()

  class ProcessHandler extends NuAbstractProcessHandler with LazyLogging {
    var nuProcess: NuProcess = _
    var startedNormally = false

    override def onStart(nuProcess: NuProcess): Unit = {
      this.nuProcess = nuProcess
      startedNormally = true
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
        write(buffer, stdInQueue.remove().getBytes(settings.charset))
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
      read(buffer, closed, StdOut)
    }
    override def onStderr(buffer: ByteBuffer, closed: Boolean): Unit = {
      read(buffer, closed, StdErr)
    }
    private def read(buffer: ByteBuffer, closed: Boolean, readFrom: FromInputStream): Unit = {
      val toQueue = queueOf(readFrom)

      readAvailableOnQueue.putLast(readFrom)
      logger.trace(s"Read available on $readFrom. DequeReadAvailableOn: ${readAvailableOnQueue.asScala.mkString(", ")}")

      if (closed) {
        toQueue.put(Left(new EOFException()))
      } else if (buffer.hasRemaining) {
        val bytes = Array.ofDim[Byte](buffer.remaining())
        buffer.get(bytes)
        toQueue.put(Right(new String(bytes, settings.charset)))
      }
    }
  }

  /**
    * The corresponding queue for `from`.
    * @param from which InputStream to get the queue of.
    */
  def queueOf(from: FromInputStream = StdOut): BlockingDeque[Either[EOFException, String]] = from match {
    case StdErr => stdErrQueue
    case _ => stdOutQueue
  }

  def read(from: FromInputStream = StdOut)(implicit deadline: Deadline): String = {
    Option {
      blocking {
        queueOf(from).pollFirst(deadline.timeLeft.toMillis, TimeUnit.MILLISECONDS)
      }
    }.map { result =>
      // Because we were able to get an element from `queueOf(from)` we know
      // `readAvailableOnQueue` will have a `from`. We need to remove to ensure that if a
      // later `readOnFirstInputStream` is performed it will get the correct value.
      readAvailableOnQueue.removeFirstOccurrence(from)
      logger.trace(s"Read: took ReadAvailableOn $from. ReadAvailableOnQueue: ${readAvailableOnQueue.asScala.mkString(", ")}")

      result.fold(throw _, identity)
    }.getOrElse(throw new TimeoutException())
  }
  def readOnFirstInputStream()(implicit deadline: Deadline): (FromInputStream, String) = {
    Option{
      blocking {
        readAvailableOnQueue.pollFirst(deadline.timeLeft.toMillis, TimeUnit.MILLISECONDS)
      }
    }.flatMap { from =>
      logger.trace(s"ReadOnFirstInputStream: took ReadAvailableOn $from. ReadAvailableOnQueue: ${readAvailableOnQueue.asScala.mkString(", ")}")
      Option {
        blocking {
          queueOf(from).pollFirst(deadline.timeLeft.toMillis, TimeUnit.MILLISECONDS)
        }
      }.map(_.fold(throw _, (from, _)))
    }.getOrElse(throw new TimeoutException())
  }

  def write(text: String): Unit = if (process.isRunning) {
    stdInQueue.put(text)
    process.wantWrite()
  }

  def destroy(): Unit = if (process.isRunning) {
    try {
      // First allow the process to terminate gracefully
      process.destroy(false)
      // Check whether it terminated or not
      val returnCode = blocking(process.waitFor(settings.timeout.toMillis, TimeUnit.MILLISECONDS))
      if (returnCode == Integer.MIN_VALUE) {
        // The waitFor timed out. Ensure the process terminates by forcing it.
        process.destroy(true)
      }
    } catch {
      case e: RuntimeException if e.getMessage.contains("Sending signal failed") =>
        // There is a bug in NuProcess where sometimes `process.destroy(false)` throws a RuntimeException
        // with the message "Sending signal failed, return code: -1, last error: 3".
        // In this case we assume the process already finished
    }
  }
}
