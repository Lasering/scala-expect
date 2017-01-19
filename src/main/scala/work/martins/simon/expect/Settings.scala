package work.martins.simon.expect

import java.nio.charset.Charset
import java.util.concurrent.TimeUnit

import scala.concurrent.duration.{DurationLong, FiniteDuration}

import com.typesafe.config.{Config, ConfigFactory}

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
  *      }
  *      new Expect(yourCommand, yourDefaultValue, YourSettings)
  *    }}}
  * @param config
  */
class Settings(config: Config = ConfigFactory.load()) {
  val scalaExpectConfig: Config = {
    val reference = ConfigFactory.defaultReference()
    val finalConfig = config.withFallback(reference)
    finalConfig.checkValid(reference, "scala-expect")
    finalConfig.getConfig("scala-expect")
  }
  import scalaExpectConfig._

  /** How much time to wait when performing a read. */
  val timeout: FiniteDuration = getDuration("timeout", TimeUnit.SECONDS).seconds
  /** The charset used for encoding and decoding the read text and the to be printed text. */
  val charset: Charset = Charset.forName(getString("charset"))
  /** Whether to redirect stdErr to stdOut. */
  val redirectStdErrToStdOut: Boolean = getBoolean("redirect-std-err-to-std-out")

  override def equals(other: Any): Boolean = other match {
    case that: Settings =>timeout == that.timeout &&
        charset == that.charset &&
        redirectStdErrToStdOut == that.redirectStdErrToStdOut
    case _ => false
  }
  override def hashCode(): Int = {
    val state: Seq[Any] = Seq(timeout, charset, redirectStdErrToStdOut)
    state.map(_.hashCode()).foldLeft(0)((a, b) => 31 * a + b)
  }
  override def toString: String =
    s"""Scala-expect settings:
       |\tTimeout: $timeout
       |\tCharset: $charset
       |\tRedirect StdErr to StdOut: $redirectStdErrToStdOut
     """.stripMargin
}
