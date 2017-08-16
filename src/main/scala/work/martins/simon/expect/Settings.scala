package work.martins.simon.expect

import java.nio.charset.{Charset, StandardCharsets}
import java.util.concurrent.TimeUnit

import scala.concurrent.duration.{DurationLong, FiniteDuration}
import com.typesafe.config.{Config, ConfigFactory}

object Settings {
  /**
    * Instantiate a `Settings` from a [[com.typesafe.config.Config]].
    * @param config The [[com.typesafe.config.Config]] from which to parse the settings.
    */
  def fromConfig(config: Config = ConfigFactory.load()): Settings = {
    val scalaExpectConfig: Config = {
      val reference = ConfigFactory.defaultReference()
      val finalConfig = config.withFallback(reference)
      finalConfig.checkValid(reference, "scala-expect")
      finalConfig.getConfig("scala-expect")
    }
    import scalaExpectConfig._

    Settings(
      getDuration("timeout", TimeUnit.SECONDS).seconds,
      Charset.forName(getString("charset")),
      getBoolean("redirect-std-err-to-std-out"))
  }
}

/**
  * This class holds all the settings that parameterize expect.
  *
  * If you would like to create an instance of settings from a [[com.typesafe.config.Config]] invoke `Settings.fromConfig`.
  * The `expect` class facilitates this by receiving the [[com.typesafe.config.Config]] directly in an auxiliary constructor.
  *
  * @param timeout How much time to wait when performing a read.
  * @param charset The charset used for encoding and decoding the read text and the to be printed text.
  * @param redirectStdErrToStdOut Whether to redirect stdErr to stdOut.
  */
case class Settings(timeout: FiniteDuration = 1.second, charset: Charset = StandardCharsets.UTF_8,
                    redirectStdErrToStdOut: Boolean = false)
