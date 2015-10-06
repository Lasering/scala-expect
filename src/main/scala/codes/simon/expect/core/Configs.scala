package codes.simon.expect.core

import java.nio.charset.{Charset => JCharset}
import java.util.concurrent.TimeUnit

import com.typesafe.config.ConfigFactory

import scala.concurrent.duration.FiniteDuration

object Configs {
  val defaultConfig = ConfigFactory.load().getConfig("scala-expect")

  val timeout = FiniteDuration(defaultConfig.getDuration("timeout", TimeUnit.SECONDS), TimeUnit.SECONDS)
  val charset = JCharset.forName(defaultConfig.getString("charset"))
  val bufferSize = defaultConfig.getBytes("buffer-size").toInt
  val redirectStdErrToStdOut = defaultConfig.getBoolean("redirect-std-err-to-std-out")
}
