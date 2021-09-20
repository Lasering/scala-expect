organization := "work.martins.simon"
name := "scala-expect"

//======================================================================================================================
//==== Compile Options =================================================================================================
//======================================================================================================================
javacOptions ++= Seq("-Xlint", "-encoding", "UTF-8", "-Dfile.encoding=utf-8")
scalaVersion := "3.1.0-RC1"

scalacOptions ++= Seq(
  //"-explain",                          // Explain errors in more detail.
  //"-explain-types",                    // Explain type errors in more detail.
  //"-source", "future",
  "-indent",                           // Allow significant indentation.
  "-new-syntax",                       // Require `then` and `do` in control expressions.
  "-feature",                          // Emit warning and location for usages of features that should be imported explicitly.
  "-language:future",                  // better-monadic-for
  "-language:implicitConversions",     // Allow implicit conversions
  "-deprecation",                      // Emit warning and location for usages of deprecated APIs.
  "-Xfatal-warnings",                  // Fail the compilation if there are any warnings.
  "-Xmigration:3.1",                   // Warn about constructs whose behavior may have changed since version.
  "-Xsemanticdb",                      // Store information in SemanticDB.
  "-Ycook-comments",                   // Cook the comments (type check `@usecase`, etc.)
  //"-Ysafe-init",                       // Ensure safe initialization of objects
  //"-Yexplicit-nulls",                  // Make reference types non-nullable. Nullable types can be expressed with unions: e.g. String|Null.
  "-Yshow-suppressed-errors",          // Also show follow-on errors and warnings that are normally suppressed.
  // Compile code with classes specific to the given version of the Java platform available on the classpath and emit bytecode for this version.
  //"-release", "15",
)

// These lines ensure that in sbt console or sbt test:console the -Ywarn* and the -Xfatal-warning are not bothersome.
// https://stackoverflow.com/questions/26940253/in-sbt-how-do-you-override-scalacoptions-for-console-in-all-configurations
Compile / console / scalacOptions ~= (_ filterNot { option =>
  option.startsWith("-Ywarn") || option == "-Xfatal-warnings"
})
Test / console / scalacOptions := (Compile / console / scalacOptions).value

//======================================================================================================================
//==== Dependencies ====================================================================================================
//======================================================================================================================
libraryDependencies ++= Seq(
  "com.typesafe" % "config" % "1.4.1",
  "com.zaxxer" % "nuprocess" % "2.0.2",
  "com.typesafe.scala-logging" %% "scala-logging" % "3.9.4",
  "ch.qos.logback" % "logback-classic" % "1.2.6" % Test,
  "org.scalatest" %% "scalatest" % "3.2.10" % Test,
)

// Needed for scoverage snapshot
resolvers += Opts.resolver.sonatypeSnapshots

//======================================================================================================================
//==== Scaladoc ========================================================================================================
//======================================================================================================================
val latestReleasedVersion = SettingKey[String]("latest released version")
git.useGitDescribe := true
latestReleasedVersion := git.gitDescribedVersion.value.getOrElse("0.1.0")

autoAPIMappings := true //Tell scaladoc to look for API documentation of managed dependencies in their metadata.
Compile / doc / scalacOptions ++= Seq(
  "-diagrams",    // Create inheritance diagrams for classes, traits and packages.
  "-groups",      // Group similar functions together (based on the @group annotation)
  "-implicits",   // Document members inherited by implicit conversions.
  "-doc-title", name.value.capitalize,
  "-doc-version", latestReleasedVersion.value,
  "-doc-source-url", s"${homepage.value.get}/tree/v${latestReleasedVersion.value}€{FILE_PATH}.scala",
  "-sourcepath", baseDirectory.value.getAbsolutePath
)
//Define the base URL for the Scaladocs for your library. This will enable clients of your library to automatically
//link against the API documentation using autoAPIMappings.
apiURL := Some(url(s"${homepage.value.get}/${latestReleasedVersion.value}/api/"))

enablePlugins(GhpagesPlugin)
SiteScaladoc / siteSubdirName := s"api/${version.value}"
ghpagesPushSite / envVars := Map("SBT_GHPAGES_COMMIT_MESSAGE" -> s"Add Scaladocs for version ${latestReleasedVersion.value}")
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
coverageMinimumStmtTotal := 90
coverageMinimumBranchTotal := 90

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
  releaseStepCommand("sonatypeReleaseAll"),
  pushChanges,
  setNextVersion
)