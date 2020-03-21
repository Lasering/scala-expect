organization := "work.martins.simon"
name := "scala-expect"

//======================================================================================================================
//==== Compile Options =================================================================================================
//======================================================================================================================
javacOptions ++= Seq("-Xlint", "-encoding", "UTF-8", "-Dfile.encoding=utf-8")
scalaVersion := "0.22.0-RC1"
crossScalaVersions := Seq(scalaVersion.value, "2.13.1")

scalacOptions ++= Seq(
  "-deprecation",                      // Emit warning and location for usages of deprecated APIs.
  "-encoding", "utf-8",                // Specify character encoding used by source files.
  "-explain-types",                    // Explain type errors in more detail.
  "-feature",                          // Emit warning and location for usages of features that should be imported explicitly.
  "-unchecked",                        // Enable additional warnings where generated code depends on assumptions.
  "-Xfatal-warnings",                  // Fail the compilation if there are any warnings.
) ++ (CrossVersion.partialVersion(scalaVersion.value) match {
  case Some((0, 22)) => Seq(
    "-explain",                          // Explain errors in more detail.
    "-migration",                        // Emit warning and location for migration issues from Scala 2.
    //"-rewrite",
    "-new-syntax",                       // Require `then` and `do` in control expressions.
    "-indent",                           // Allow significant indentation.
    "-strict",                           // Use strict type rules, which means some formerly legal code does not typecheck anymore.
    "-language:implicitConversions,strictEquality",     // Explicitly enables the implicit conversions feature
    "-Ykind-projector",                  // Enables a subset of kind-projector syntax (see https://github.com/lampepfl/dotty/pull/7775)
    "-Yerased-terms",                    // Allows the use of erased terms.
  //"-Yshow-suppressed-errors",          // Also show follow-on errors and warnings that are normally suppressed.
  //"-Ysemanticdb",                      // Store information in SemanticDB.
  //"-Yexplicit-nulls",                  // Make reference types non-nullable. Nullable types can be expressed with unions: e.g. String|Null.
  )
  case Some((2, 13)) => Seq(
    "-Ybackend-parallelism", "8",        // Maximum worker threads for backend
    "-Xcheckinit",                       // Wrap field accessors to throw an exception on uninitialized access.
    //"-Xlint",                            // Enables every warning. scalac -Xlint:help for a list and explanation 
    "-Xlint:adapted-args",               // An argument list was modified to match the receiver.
    "-Xlint:nullary-unit",               // `def f: Unit` looks like an accessor; add parens to look side-effecting.
    "-Xlint:inaccessible",               // Warn about inaccessible types in method signatures.
    "-Xlint:nullary-override",           // Non-nullary `def f()` overrides nullary `def f`.
    "-Xlint:infer-any",                  // A type argument was inferred as Any.
    "-Xlint:missing-interpolator",       // A string literal appears to be missing an interpolator id.
    "-Xlint:doc-detached",               // When running scaladoc, warn if a doc comment is discarded.
    "-Xlint:private-shadow",             // A private field (or class parameter) shadows a superclass field.
    "-Xlint:type-parameter-shadow",      // A local type parameter shadows a type already in scope.
    "-Xlint:poly-implicit-overload",     // Parameterized overloaded implicit methods are not visible as view bounds.
    "-Xlint:option-implicit",            // Option.apply used an implicit view.
    "-Xlint:delayedinit-select",         // Selecting member of DelayedInit.
    "-Xlint:package-object-classes",     // Class or object defined in package object.
    "-Xlint:stars-align",                // In a pattern, a sequence wildcard `_*` should match all of a repeated parameter.
    "-Xlint:constant",                   // Evaluation of a constant arithmetic expression resulted in an error.
    "-Xlint:unused",                     // Enable -Wunused:imports,privates,locals,implicits.
    "-Xlint:nonlocal-return",            // A return statement used an exception for flow control.
    "-Xlint:implicit-not-found",         // Check @implicitNotFound and @implicitAmbiguous messages.
    "-Xlint:serial",                     // @SerialVersionUID on traits and non-serializable classes.
    "-Xlint:valpattern",                 // Enable pattern checks in val definitions.
    "-Xlint:eta-zero",                   // Usage `f` of parameterless `def f()` resulted in eta-expansion, not empty application `f()`.
    "-Xlint:eta-sam",                    // The Java-defined target interface for eta-expansion was not annotated @FunctionalInterface.
    "-Xlint:deprecation",                // Enable -deprecation and also check @deprecated annotations.
  )
  case _ => Nil
})

//"-strict",                           // Use strict type rules, which means some formerly legal code does not typecheck anymore.

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
  "com.typesafe" % "config" % "1.3.4",
  "com.zaxxer" % "nuprocess" % "1.2.4",
  "ch.qos.logback" % "logback-classic" % "1.2.3" % Test,
  "org.scalatest" %% "scalatest" % "3.1.1" % Test,
  //compilerPlugin("com.github.ghik" %% "silencer-plugin" % silencerVersion),
  //"com.github.ghik" %% "silencer-lib" % silencerVersion % Compile,
  //"com.typesafe.scala-logging" %% "scala-logging" % "3.9.2"
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
