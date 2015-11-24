package work.martins.simon.expect.core

import java.nio.charset.{Charset => JCharset}
import java.util.concurrent.TimeUnit

import com.typesafe.config.ConfigFactory

import scala.concurrent.duration.DurationLong

object Configs {
  val defaultConfig = ConfigFactory.load().getConfig("scala-expect")

  val timeout = defaultConfig.getDuration("timeout", TimeUnit.SECONDS).seconds
  val charset = JCharset.forName(defaultConfig.getString("charset"))
  val bufferSize = defaultConfig.getBytes("buffer-size").toInt
  val redirectStdErrToStdOut = defaultConfig.getBoolean("redirect-std-err-to-std-out")
}
