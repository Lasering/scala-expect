organization := "work.martins.simon"
name := "scala-expect"

//======================================================================================================================
//==== Compile Options =================================================================================================
//======================================================================================================================
javacOptions ++= Seq("-Xlint", "-encoding", "UTF-8", "-Dfile.encoding=utf-8")
scalaVersion := "3.1.1"
scalacOptions ++= Seq(
  //"-explain",                      // Explain errors in more detail.
  //"-explain-types",                // Explain type errors in more detail.
  "-indent",                       // Allow significant indentation.
  "-new-syntax",                   // Require `then` and `do` in control expressions.
  "-feature",                      // Emit warning and location for usages of features that should be imported explicitly.
  "-language:future",              // better-monadic-for
  "-language:implicitConversions", // Allow implicit conversions
  "-deprecation",                  // Emit warning and location for usages of deprecated APIs.
  "-Werror",                       // Fail the compilation if there are any warnings.
  "-source:future",
  "-Xsemanticdb",                  // Store information in SemanticDB.
  "-Ycook-comments",               // Cook the comments (type check `@usecase`, etc.)
  //"-Ysafe-init",                   // Ensure safe initialization of objects
  "-Yshow-suppressed-errors",      // Also show follow-on errors and warnings that are normally suppressed.
  // Compile code with classes specific to the given version of the Java platform available on the classpath and emit bytecode for this version.
  //"-release", "16",
  //"-project-url", git.remoteRepo.value,
)

Test / scalacOptions += "-Wconf:msg=is not declared `infix`:s,msg=is declared 'open':s"

// These lines ensure that in sbt console or sbt test:console the -Werror is not bothersome.
Compile / console / scalacOptions ~= (_.filterNot(_.startsWith("-Werror")))
Test / console / scalacOptions := (Compile / console / scalacOptions).value

//======================================================================================================================
//==== Dependencies ====================================================================================================
//======================================================================================================================
libraryDependencies ++= Seq(
  "com.typesafe" % "config" % "1.4.1",
  "com.zaxxer" % "nuprocess" % "2.0.2",
  "com.typesafe.scala-logging" %% "scala-logging" % "3.9.4",
  "ch.qos.logback" % "logback-classic" % "1.2.10" % Test,
  "org.scalatest" %% "scalatest" % "3.2.11" % Test,
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