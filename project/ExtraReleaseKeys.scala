import sbt.Keys.version
import sbt.{IO, SettingKey, State, ThisBuild}
import sbtrelease.ReleasePlugin.autoImport.{ReleaseStep, releaseUseGlobalVersion, releaseVersionFile}

object ExtraReleaseKeys {
  val latestReleasedVersion = SettingKey[String]("Latest released version")

  lazy val writeVersions: ReleaseStep = { st: State =>
    import sbtrelease.ReleasePlugin.autoImport.ReleaseKeys.versions
    import sbtrelease.Utilities._
    import sbtrelease.ReleaseStateTransformations.reapply

    val vs = st.get(versions).getOrElse(sys.error("No versions are set! Was this release part executed before inquireVersions?"))
    val (releasedVersion, nextVersion) = vs


    st.log.info("Setting version to '%s'." format nextVersion)

    val useGlobal = st.extract.get(releaseUseGlobalVersion)
    val global = if (useGlobal) "in ThisBuild " else ""
    val versionStr = s"""import ExtraReleaseKeys._
                        |version $global := "$nextVersion"
                        |latestReleasedVersion $global := "$releasedVersion"""".stripMargin

    val file = st.extract.get(releaseVersionFile)
    IO.writeLines(file, Seq(versionStr))

    reapply(Seq(
      if (useGlobal) {
        version in ThisBuild := nextVersion
      } else {
        version := nextVersion
      },
      if (useGlobal) {
        latestReleasedVersion in ThisBuild := releasedVersion
      } else {
        latestReleasedVersion := releasedVersion
      }
    ), st)
  }
}