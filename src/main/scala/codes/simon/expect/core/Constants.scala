package codes.simon.expect.core

import java.nio.charset.Charset

import scala.concurrent.duration.DurationInt

object Constants {
  val TIMEOUT = 10.seconds
  val CHARSET = Charset.forName("UTF-8")
  val BUFFER_SIZE = 1024
  val REDIRECT_STDERR_TO_STDOUT = true
}
