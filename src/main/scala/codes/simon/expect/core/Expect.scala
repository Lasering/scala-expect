package codes.simon.expect.core

import java.nio.charset.Charset
import com.typesafe.scalalogging.LazyLogging
import scala.concurrent._
import scala.concurrent.duration.FiniteDuration

class Expect[R](command: String, expects: Seq[ExpectBlock]) extends LazyLogging {
  def run(timeout: FiniteDuration = Constants.TIMEOUT, charset: Charset = Constants.CHARSET, bufferSize: Int = Constants.BUFFER_SIZE)(implicit ex: ExecutionContext): Future[Option[R]] = {
    require(expects.nonEmpty, "There must exist at least one expect block")

    val processBuilder = new ProcessBuilder(command.split("""\s+"""):_*)
    processBuilder.redirectErrorStream(true)
    val richProcess = RichProcess(processBuilder.start(), timeout, charset, bufferSize)

    //The first Option is the last read output
    //The second option is the last value (that might have been "returned" by a ReturningAction)
    var result: Future[(Option[String], Option[R])] = Future.successful((None, None))
    for (expect ← expects) {
      //This `if` is a cheat. But I can't find any other way to do it. Suggestions are welcome.
      result = result.flatMap{ case (lastOutput, lastValue) if richProcess.isAlive =>
        logger.info(s"LastValue: $lastValue. About to start another future")
        val newResult = expect.run(richProcess, lastOutput)
        //Keep lastValue if newValue is empty
        newResult.map{ case (newOutput, newValue) =>
          (newOutput, if (newValue.isEmpty) lastValue else newValue)
        }
      }
    }
    //"Trim" the last read output before returning the result to the user.
    val endResult = result.map{ case (lastOutput, value) => value }
    //Make sure to destroy the process and close the streams.
    endResult onComplete (_ => richProcess.destroy())
    endResult
  }
}