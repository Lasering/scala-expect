import sbt.SettingKey

object ExtraReleaseKeys {
  val latestReleasedVersion = SettingKey[String]("Latest released version")
}