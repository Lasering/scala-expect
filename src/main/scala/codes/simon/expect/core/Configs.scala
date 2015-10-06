package codes.simon.expect.core

import java.nio.charset.{Charset => JCharset}
import java.util.concurrent.TimeUnit

import com.typesafe.config.ConfigFactory

import scala.concurrent.duration.FiniteDuration

object Configs {
  val defaultConfig = ConfigFactory.load().getConfig("scala-expect")

  val Timeout = FiniteDuration(defaultConfig.getDuration("timeout", TimeUnit.SECONDS), TimeUnit.SECONDS)
  val Charset = JCharset.forName(defaultConfig.getString("charset"))
  val BufferSize = defaultConfig.getBytes("buffer-size").toInt
  val RedirectStdErrToStdOut = defaultConfig.getBoolean("redirect-std-err-to-std-out")
}
