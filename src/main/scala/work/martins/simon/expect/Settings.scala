package work.martins.simon.expect

import java.nio.charset.{Charset, StandardCharsets}
import java.util.concurrent.TimeUnit
import com.typesafe.config.{Config, ConfigFactory}
import scala.concurrent.duration.{DurationLong, FiniteDuration}

object Settings:
  /**
    * Instantiate a `Settings` from a Typesafe Config.
    * @param config The Config from which to parse the settings.
    */
  def fromConfig(config: Config = ConfigFactory.load()): Settings =
    val scalaExpectConfig: Config =
      val reference = ConfigFactory.defaultReference()
      val finalConfig = config.withFallback(reference)
      finalConfig.checkValid(reference, "scala-expect")
      finalConfig.getConfig("scala-expect")
    
    import scalaExpectConfig.*
    
    Settings(
      getDuration("timeout", TimeUnit.SECONDS).seconds,
      getDouble("time-factor"),
      Charset.forName(getString("charset")))

/**
  * This class holds all the settings that parameterize expect.
  *
  * If you would like to create an instance of settings from a Typesafe Config invoke `Settings.fromConfig`.
  *
  * @param timeout       How much time to wait when performing a read.
  * @param timeoutFactor Factor by which to scale timeout, e.g. to account for shared build system load.
  * @param charset       The charset used for encoding and decoding the read text and the to be printed text.
  */
case class Settings(timeout: FiniteDuration = 1.second, timeoutFactor: Double = 1.0, charset: Charset = StandardCharsets.UTF_8) derives CanEqual:
  require(timeoutFactor >= 1.0 && !timeoutFactor.isInfinite && !timeoutFactor.isNaN,
    "Time factor must be >=1 and not Infinity or NaN.")
  
  /** The timeout scaled by the timeout factor.*/
  val scaledTimeout: FiniteDuration = (timeout * timeoutFactor).asInstanceOf[FiniteDuration]
// The default values declared here are the authoritative ones. But its nice to have the same default values in reference.conf.
// If you change any of them make sure they are the same.