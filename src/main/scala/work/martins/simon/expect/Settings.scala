package work.martins.simon.expect

import java.nio.charset.Charset
import java.util.concurrent.TimeUnit

import com.typesafe.config.{Config, ConfigFactory}

import scala.concurrent.duration.DurationLong

/**
  * This class holds all the settings that parameterize expect.
  *
  * By default these settings are read from the Config obtained with `ConfigFactory.load()`.
  *
  * You can change the settings in multiple ways:
  *
  *  - Change them in the default configuration file (e.g. application.conf)
  *  - Pass a different config holding your configurations: {{{
  *       new Settings(yourConfig)
  *     }}}
  *     However it will be more succinct to pass your config directly to expect: {{{
  *       new Expect(yourCommand, yourDefaultValue, yourConfig)
  *     }}}
  *  - Extend this class overriding the settings you want to redefine {{{
  *      object YourSettings extends Settings() {
  *        override val timeout: FiniteDuration = 2.minutes
  *        override val bufferSize: Int = 4096
  *      }
  *      new Expect(yourCommand, yourDefaultValue, YourSettings)
  *    }}}
  * @param config
  */
final class Settings(config: Config = ConfigFactory.load()) {
  val scalaExpectConfig: Config = {
    val reference = ConfigFactory.defaultReference()
    val finalConfig = config.withFallback(reference)
    finalConfig.checkValid(reference, "scala-expect")
    finalConfig.getConfig("scala-expect")
  }
  import scalaExpectConfig._

  /** How much time to wait when performing a read. */
  val timeout = getDuration("timeout", TimeUnit.SECONDS).seconds
  /** The charset used for encoding and decoding the read text and the to be printed text. */
  val charset = Charset.forName(getString("charset"))
  /** How many bytes to read in each read operation. */
  val bufferSize = getBytes("buffer-size").toInt
  /** Whether to redirect stdErr to stdOut. */
  val redirectStdErrToStdOut = getBoolean("redirect-std-err-to-std-out")

  override def equals(other: Any): Boolean = other match {
    case that: Settings =>timeout == that.timeout &&
        charset == that.charset &&
        bufferSize == that.bufferSize &&
        redirectStdErrToStdOut == that.redirectStdErrToStdOut
    case _ => false
  }
  override def hashCode(): Int = {
    val state: Seq[Any] = Seq(timeout, charset, bufferSize, redirectStdErrToStdOut)
    state.map(_.hashCode()).foldLeft(0)((a, b) => 31 * a + b)
  }
  override def toString: String =
    s"""Scala-expect settings:
       |\tTimeout: $timeout
       |
       |\tCharset: $charset
       |
       |\tBuffer size: $bufferSize bytes (${getString("buffer-size")})
       |
       |\tRedirect StdErr to StdOut: $redirectStdErrToStdOut
     """.stripMargin
}
