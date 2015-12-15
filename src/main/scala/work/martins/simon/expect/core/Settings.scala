package work.martins.simon.expect.core

import java.nio.charset.Charset
import java.util.concurrent.TimeUnit

import com.typesafe.config.{Config, ConfigFactory}

import scala.concurrent.duration.{FiniteDuration, DurationLong}

class Settings(config: Config) {
  config.checkValid(ConfigFactory.defaultReference(), "scala-expect")

  val defaultConfig: Config = config.getConfig("scala-expect")

  val timeout: FiniteDuration = defaultConfig.getDuration("timeout", TimeUnit.SECONDS).seconds
  val charset: Charset = Charset.forName(defaultConfig.getString("charset"))
  val bufferSize: Int = defaultConfig.getBytes("buffer-size").toInt
  val redirectStdErrToStdOut: Boolean = defaultConfig.getBoolean("redirect-std-err-to-std-out")
}
