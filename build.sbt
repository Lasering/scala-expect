import ExtraReleaseKeys._
import java.net.URL

organization := "work.martins.simon"
name := "scala-expect"

//======================================================================================================================
//==== Compile Options =================================================================================================
//======================================================================================================================
javacOptions ++= Seq("-Xlint", "-encoding", "UTF-8", "-Dfile.encoding=utf-8")
scalaVersion := "2.12.3"
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
  "-Ypartial-unification",             // Enable partial unification in type constructor inference
  "-Yno-adapted-args",                 // Do not adapt an argument list (either by inserting () or creating a tuple) to match the receiver.
  "-Xlint",                            // Enables every warning. scala -Xlint:help for a list and explanation
  //"-Xlint:-unused,_",                  // Enables every warning except "unused"
  "-Ywarn-dead-code",                  // Warn when dead code is identified.
  "-Ywarn-extra-implicit",             // Warn when more than one implicit parameter section is defined.
  "-Ywarn-numeric-widen",              // Warn when numerics are widened.
  "-Ywarn-unused:imports",             // Warn if an import selector is not referenced.
  "-Ywarn-unused:patvars",             // Warn if a variable bound in a pattern is unused.
  "-Ywarn-unused:privates",            // Warn if a private member is unused.
  "-Ywarn-unused:locals",              // Warn if a local definition is unused.
  "-Ywarn-unused:params",              // Warn if a value parameter is unused.
  "-Ywarn-unused:implicits",           // Warn if an implicit parameter is unused.
  //"-Ywarn-value-discard",              // Warn when non-Unit expression results are unused.
)
// These lines ensure that in sbt console or sbt test:console the -Ywarn* and the -Xfatal-warning are not bothersome.
// https://stackoverflow.com/questions/26940253/in-sbt-how-do-you-override-scalacoptions-for-console-in-all-configurations
scalacOptions in (Compile, console) ~= (_ filterNot { option =>
  option.startsWith("-Ywarn") || option == "-Xfatal-warnings"
})
scalacOptions in (Test, console) := (scalacOptions in (Compile, console)).value

//======================================================================================================================
//==== Dependencies ====================================================================================================
//======================================================================================================================
libraryDependencies ++= Seq(
  "com.typesafe.scala-logging" %% "scala-logging" % "3.7.2",
  "com.typesafe" % "config" % "1.3.1",
  "com.zaxxer" % "nuprocess" % "1.1.2",
  "ch.qos.logback" % "logback-classic" % "1.2.3" % Test,
  "org.scalatest" %% "scalatest" % "3.0.4" % Test,
)
// Needed for scoverage snapshot
resolvers += Opts.resolver.sonatypeSnapshots

//======================================================================================================================
//==== Scaladoc ========================================================================================================
//======================================================================================================================
autoAPIMappings := true //Tell scaladoc to look for API documentation of managed dependencies in their metadata.
scalacOptions in (Compile, doc) ++= Seq(
  "-diagrams",    // Create inheritance diagrams for classes, traits and packages.
  "-groups",      // Group similar functions together (based on the @group annotation)
  "-implicits",   // Document members inherited by implicit conversions.
  "-doc-source-url", s"${homepage.value.get}/tree/v${latestReleasedVersion.value}€{FILE_PATH}.scala",
  "-sourcepath", (baseDirectory in ThisBuild).value.getAbsolutePath,
  "-no-link-warnings", // Suppresses problems with Scaladoc not finding any member to link
)
//Define the base URL for the Scaladocs for your library. This will enable clients of your library to automatically
//link against the API documentation using autoAPIMappings.
apiURL := Some(url(s"${homepage.value.get}/${latestReleasedVersion.value}/api/"))

enablePlugins(GhpagesPlugin)
enablePlugins(SiteScaladocPlugin)
git.remoteRepo := s"git@github.com:Lasering/${name.value}.git"

//======================================================================================================================
//==== Deployment ======================================================================================================
//======================================================================================================================
publishTo := Some(if (isSnapshot.value) Opts.resolver.sonatypeSnapshots else Opts.resolver.sonatypeStaging)
sonatypeProfileName := "work.martins"

homepage := Some(url(s"https://github.com/Lasering/${name.value}"))
licenses += "MIT" -> url("http://opensource.org/licenses/MIT")
scmInfo := Some(ScmInfo(homepage.value.get, s"git@github.com:Lasering/${name.value}.git"))
developers += Developer("Lasering", "Simão Martins", "", new URL("https://github.com/Lasering"))

//Will fail a build if updates for the dependencies are found
dependencyUpdatesFailBuild := true

import ReleaseTransformations._
releaseProcess := Seq[ReleaseStep](
  releaseStepCommand("dependencyUpdates"),
  checkSnapshotDependencies,
  inquireVersions,
  runClean,
  releaseStepCommand("doc"),
  runTest,
  setReleaseVersion,
  tagRelease,
  releaseStepCommand("ghpagesPushSite"),
  releaseStepCommand("publishSigned"),
  releaseStepCommand("sonatypeRelease"),
  pushChanges,
  writeVersions
)
