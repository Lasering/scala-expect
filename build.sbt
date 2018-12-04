organization := "work.martins.simon"
name := "scala-expect"

//======================================================================================================================
//==== Compile Options =================================================================================================
//======================================================================================================================
javacOptions ++= Seq("-Xlint", "-encoding", "UTF-8", "-Dfile.encoding=utf-8")
scalaVersion := "2.13.0-M5"
crossScalaVersions := Seq(scalaVersion.value, "2.12.8")

scalacOptions ++= Seq(
  "-deprecation",                      // Emit warning and location for usages of deprecated APIs.
  "-encoding", "utf-8",                // Specify character encoding used by source files.
  "-explaintypes",                     // Explain type errors in more detail.
  "-feature",                          // Emit warning and location for usages of features that should be imported explicitly.
  "-language:implicitConversions",     // Explicitly enables the implicit conversions feature
  "-unchecked",                        // Enable additional warnings where generated code depends on assumptions.
  "-Xcheckinit",                       // Wrap field accessors to throw an exception on uninitialized access.
  "-Xfatal-warnings",                  // Fail the compilation if there are any warnings.
  "-Xfuture",                          // Turn on future language features.
  "-Xsource:2.14",                     // Treat compiler input as Scala source for the specified version.
  "-Xmigration:2.14.0",                // Warn about constructs whose behavior may have changed since version.
  "-Xlint",                            // Enables every warning. scalac -Xlint:help for a list and explanation
  "-Ywarn-dead-code",                  // Warn when dead code is identified.
  "-Ywarn-numeric-widen",              // Warn when numerics are widened.
  "-Ywarn-value-discard",              // Warn when non-Unit expression results are unused.
  "-Ywarn-extra-implicit",             // Warn when more than one implicit parameter section is defined.
  "-Ywarn-unused:imports",             // Warn if an import selector is not referenced.
  "-Ywarn-unused:privates",            // Warn if a private member is unused.
  "-Ywarn-unused:locals",              // Warn if a local definition is unused.
  "-Ywarn-unused:params",              // Warn if a value parameter is unused. TODO this seams to not be working in 2.13
  "-Ywarn-unused:patvars",             // Warn if a variable bound in a pattern is unused.
  "-Ybackend-parallelism", "4",        // Maximum worker threads for backend
) ++ (CrossVersion.partialVersion(scalaVersion.value) match {
  case Some((2, 12)) => Seq(
    "-Xexperimental",                    // Enable experimental extensions.
    "-Xsource:2.13",                     // Treat compiler input as Scala source for the specified version.
    "-Xmigration:2.13.0",                // Warn about constructs whose behavior may have changed since version.
    "-Ypartial-unification",             // Enable partial unification in type constructor inference
    "-Yno-adapted-args",                 // Do not adapt an argument list (either by inserting () or creating a tuple) to match the receiver.
  )
  case _ => Nil
})

// These lines ensure that in sbt console or sbt test:console the -Ywarn* and the -Xfatal-warning are not bothersome.
// https://stackoverflow.com/questions/26940253/in-sbt-how-do-you-override-scalacoptions-for-console-in-all-configurations
scalacOptions in (Compile, console) ~= (_ filterNot { option =>
  option.startsWith("-Ywarn") || option == "-Xfatal-warnings"
})
scalacOptions in (Test, console) := (scalacOptions in (Compile, console)).value

//======================================================================================================================
//==== Dependencies ====================================================================================================
//======================================================================================================================
val silencerVersion = "1.3.0"
libraryDependencies ++= Seq(
  "com.typesafe" % "config" % "1.3.3",
  "com.zaxxer" % "nuprocess" % "1.2.4",
  "ch.qos.logback" % "logback-classic" % "1.2.3" % Test,
  "org.scalatest" %% "scalatest" % "3.0.6-SNAP5" % Test,
  compilerPlugin("com.github.ghik" %% "silencer-plugin" % silencerVersion),
  "com.github.ghik" %% "silencer-lib" % silencerVersion % Compile
) ++ (CrossVersion.partialVersion(scalaVersion.value) match {
  case Some((2, 13)) => Seq("com.typesafe.scala-logging" %% "scala-logging" % "3.9.1")
  case Some((2, 12)) => Seq("com.typesafe.scala-logging" %% "scala-logging" % "3.9.0")
  case _ => Nil
})

// Needed for scoverage snapshot
resolvers += Opts.resolver.sonatypeSnapshots

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
import xerial.sbt.Sonatype.SonatypeCommand
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
  releaseStepCommand(SonatypeCommand.sonatypeReleaseAll),
  pushChanges,
  setNextVersion
)