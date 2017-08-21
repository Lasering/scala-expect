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
    * If no bytes are read within `deadline` a `TimeoutException` will be thrown.
    * If the end of file is reached an `EOFException` is thrown.
    * Otherwise, a String encoded with `settings.charset` is created from the read bytes.
    *
    * This method may block up until the deadline expires.
    *
    * Only one read operation, read or readOnFirstInputStream, is performed at the same time.
    * No concurrent reads will be made.
    *
    * @return a String created from the read bytes encoded with `charset`.
    */
  def read(from: FromInputStream = StdOut)(implicit deadline: Deadline): String
  /**
    * Tries to read from `StdOut` or `StdErr` of the process, whichever has output to offer first.
    * If no bytes are read within `timeout` a `TimeoutException` will be thrown.
    * If the end of file is reached an `EOFException` is thrown.
    * Otherwise, a String encoded with `settings.charset` is created from the read bytes.
    *
    * This method may block up until the deadline expires.
    *
    * Only one read operation, read or readOnFirstInputStream, is performed at the same time.
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

  def withCommand(command: Seq[String] = this.command): RichProcess
}

case class NuProcessRichProcess(command: Seq[String], settings: Settings) extends RichProcess with LazyLogging {
  protected val stdInQueue = new LinkedBlockingDeque[String]()
  protected val stdOutQueue = new LinkedBlockingDeque[Either[EOFException, String]]()
  protected val stdErrQueue = new LinkedBlockingDeque[Either[EOFException, String]]()
  protected val dequeReadAvailableOn = new LinkedBlockingDeque[FromInputStream]()

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

      dequeReadAvailableOn.putLast(readFrom)
      logger.trace(s"Read available on $readFrom. DequeReadAvailableOn: ${dequeReadAvailableOn.asScala.mkString(", ")}")

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
    * The corresponding queue for `from`. In the case the `StdErr` is being redirected to `StdOut`
    * this method will always return the queue of `StdOut`.
    * @param from which InputStream to get the queue of.
    */
  def queueOf(from: FromInputStream = StdOut): BlockingDeque[Either[EOFException, String]] = from match {
    case StdErr if !settings.redirectStdErrToStdOut => stdErrQueue
    case _ => stdOutQueue
  }

  /** @inheritdoc */
  def read(from: FromInputStream = StdOut)(implicit deadline: Deadline): String = {
    Option {
      blocking {
        queueOf(from).pollFirst(deadline.timeLeft.toMillis, TimeUnit.MILLISECONDS)
      }
    }.map { result =>
      // Because we were able to get an element from `queueOf(from)` we know
      // `dequeReadAvailableOn` will have a `from`. We need to remove to ensure that if a
      // later `readOnFirstInputStream` is performed it will get the correct value.
      dequeReadAvailableOn.removeFirstOccurrence(from)
      logger.trace(s"Read: took ReadAvailableOn $from. DequeReadAvailableOn: ${dequeReadAvailableOn.asScala.mkString(", ")}")

      result.fold(throw _, identity)
    }.getOrElse(throw new TimeoutException())
  }
  /** @inheritdoc */
  def readOnFirstInputStream()(implicit deadline: Deadline): (FromInputStream, String) = {
    Option{
      blocking {
        dequeReadAvailableOn.pollFirst(deadline.timeLeft.toMillis, TimeUnit.MILLISECONDS)
      }
    }.flatMap { from =>
      logger.trace(s"ReadOnFirstInputStream: took ReadAvailableOn $from. DequeReadAvailableOn: ${dequeReadAvailableOn.asScala.mkString(", ")}")
      Option {
        blocking {
          queueOf(from).pollFirst(deadline.timeLeft.toMillis, TimeUnit.MILLISECONDS)
        }
      }.map(_.fold(throw _, (from, _)))
    }.getOrElse(throw new TimeoutException())
  }

  /** @inheritdoc */
  def write(text: String): Unit = {
    stdInQueue.put(text)
    process.wantWrite()
  }

  /** @inheritdoc */
  def destroy(): Unit = {
    // NuProcess already closes the streams for us.
    blocking {
      process.destroy(false)
      val returnCode = process.waitFor(settings.timeout.toMillis, TimeUnit.MILLISECONDS)
      if (returnCode == Integer.MIN_VALUE) {
        process.destroy(true)
      }
    }
  }

  def withCommand(command: Seq[String] = this.command): RichProcess = this.copy(command = command)
}
