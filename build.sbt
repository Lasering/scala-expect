organization := "work.martins.simon"
name := "scala-expect"

//======================================================================================================================
//==== Compile Options =================================================================================================
//======================================================================================================================
javacOptions ++= Seq("-Xlint", "-encoding", "UTF-8", "-Dfile.encoding=utf-8")
scalaVersion := "2.13.1"

scalacOptions ++= Seq(
  "-encoding", "utf-8",            // Specify character encoding used by source files.
  "-explaintypes",                 // Explain type errors in more detail.
  "-feature",                      // Emit warning and location for usages of features that should be imported explicitly.
  "-language:implicitConversions", // Explicitly enables the implicit conversions feature
  "-Ybackend-parallelism", "8",    // Maximum worker threads for backend.
  "-Ybackend-worker-queue", "10",  // Backend threads worker queue size.
  "-Ymacro-annotations",           // Enable support for macro annotations, formerly in macro paradise.
  "-unchecked",                    // Enable additional warnings where generated code depends on assumptions.
  "-Xcheckinit",                   // Wrap field accessors to throw an exception on uninitialized access.
// Unfortunately this is causing to much false positives
//"-Xsource:2.14",                 // Treat compiler input as Scala source for the specified version.
  "-Xmigration:2.14",              // Warn about constructs whose behavior may have changed since version.
  "-Werror",                       // Fail the compilation if there are any warnings.
  "-Xlint:_",                      // Enables every warning. scalac -Xlint:help for a list and explanation
  "-Wunused:_",                    // Enables every warning of unused members/definitions/etc
  "-Wdead-code",                   // Warn when dead code is identified.
  "-Wextra-implicit",              // Warn when more than one implicit parameter section is defined.
  "-Wnumeric-widen",               // Warn when numerics are widened.
  "-Woctal-literal",               // Warn on obsolete octal syntax.
  "-Wself-implicit",               // Warn when an implicit resolves to an enclosing self-definition.
  "-Wvalue-discard",               // Warn when non-Unit expression results are unused.
  "-P:silencer:checkUnused",       // If a @silent annotation does not actually suppress any warnings, this option will report an error.
)

// These lines ensure that in sbt console or sbt test:console the -Ywarn* and the -Xfatal-warning are not bothersome.
// https://stackoverflow.com/questions/26940253/in-sbt-how-do-you-override-scalacoptions-for-console-in-all-configurations
scalacOptions in (Compile, console) ~= (_ filterNot { option =>
  option.startsWith("-W") || option.startsWith("-Xlint")
})
scalacOptions in (Test, console) := (scalacOptions in (Compile, console)).value
scalacOptions in Test ~= (_ filterNot(_ == "-Wself-implicit"))

initialCommands := s"""
  import ${organization.value}.expect._
  import ${organization.value}.expect.core._
  import ${organization.value}.expect.core.actions._
  """

//======================================================================================================================
//==== Dependencies ====================================================================================================
//======================================================================================================================
libraryDependencies ++= Seq(
  "com.typesafe" % "config" % "1.4.0",
  "com.zaxxer" % "nuprocess" % "2.0.0",
  "com.typesafe.scala-logging" %% "scala-logging" % "3.9.2",
  "ch.qos.logback" % "logback-classic" % "1.2.3" % Test,
  "org.scalatest" %% "scalatest" % "3.1.1" % Test,
  "com.github.ghik" % "silencer-lib" % "1.6.0" % Provided cross CrossVersion.full,
)

addCompilerPlugin("com.github.ghik" % "silencer-plugin" % "1.6.0" cross CrossVersion.full)

// Needed for scoverage snapshot
//resolvers += Opts.resolver.sonatypeSnapshots

//======================================================================================================================
//==== Scaladoc ========================================================================================================
//======================================================================================================================
val latestReleasedVersion = SettingKey[String]("latest released version")
git.useGitDescribe := true
latestReleasedVersion := git.gitDescribedVersion.value.getOrElse("0.1.0")

autoAPIMappings := true //Tell scaladoc to look for API documentation of managed dependencies in their metadata.
scalacOptions in (Compile, doc) ++= Seq(
  "-diagrams",    // Create inheritance diagrams for classes, traits and packages.
  "-groups",      // Group similar functions together (based on the @group annotation)
  "-implicits",   // Document members inherited by implicit conversions.
  "-doc-title", name.value.capitalize,
  "-doc-version", latestReleasedVersion.value,
  "-doc-source-url", s"${homepage.value.get}/tree/v${latestReleasedVersion.value}€{FILE_PATH}.scala",
  "-sourcepath", (baseDirectory in ThisBuild).value.getAbsolutePath
)
//Define the base URL for the Scaladocs for your library. This will enable clients of your library to automatically
//link against the API documentation using autoAPIMappings.
apiURL := Some(url(s"${homepage.value.get}/${latestReleasedVersion.value}/api/"))

enablePlugins(GhpagesPlugin)
siteSubdirName in SiteScaladoc := s"api/${version.value}"
envVars in ghpagesPushSite := Map("SBT_GHPAGES_COMMIT_MESSAGE" -> s"Add Scaladocs for version ${latestReleasedVersion.value}")
git.remoteRepo := s"git@github.com:Lasering/${name.value}.git"

//======================================================================================================================
//==== Deployment ======================================================================================================
//======================================================================================================================
//publishTo := Some(if (isSnapshot.value) Opts.resolver.sonatypeSnapshots else Opts.resolver.sonatypeStaging)
//sonatypeProfileName := "work.martins"
publishTo := sonatypePublishTo.value

licenses += "MIT" -> url("http://opensource.org/licenses/MIT")
homepage := Some(url(s"https://github.com/Lasering/${name.value}"))
scmInfo := Some(ScmInfo(homepage.value.get, git.remoteRepo.value))
developers += Developer("Lasering", "Simão Martins", "", url("https://github.com/Lasering"))

// Will fail the build/release if updates for the dependencies are found
dependencyUpdatesFailBuild := true

coverageFailOnMinimum := true
coverageMinimum := 90

releaseCrossBuild := true
releasePublishArtifactsAction := PgpKeys.publishSigned.value

import ReleaseTransformations._
releaseProcess := Seq[ReleaseStep](
  releaseStepTask(dependencyUpdates),
  checkSnapshotDependencies,
  inquireVersions,
  runClean,
  releaseStepTask(doc),
  runTest,
  setReleaseVersion,
  tagRelease,
  releaseStepTask(ghpagesPushSite),
  publishArtifacts,
  releaseStepTask(sonatypeBundleRelease),
  pushChanges,
  setNextVersion
)