organization := "work.martins.simon"
name := "scala-expect"
version := "1.2.1"

scalaVersion := "2.11.7"
initialize := {
  val required = "1.8"
  val current  = sys.props("java.specification.version")
  assert(current == required, s"Unsupported JDK: java.specification.version $current != $required")
}
javacOptions ++= Seq("-source", "1.8", "-target", "1.8", "-Xlint")
//crossScalaVersions := Seq("2.10.5", "2.11.7")
//crossVersion := CrossVersion.binary

libraryDependencies ++= Seq(
  "com.typesafe.scala-logging" %% "scala-logging" % "3.1.0",
  "ch.qos.logback" % "logback-classic" % "1.1.3",
  "org.scalatest" %% "scalatest" % "2.2.4" % "test",
  "com.typesafe" % "config" % "1.3.0"
)

scalacOptions ++= Seq(
  "-deprecation", //Emit warning and location for usages of deprecated APIs.
  "-encoding", "UTF-8",
  "-feature", //Emit warning and location for usages of features that should be imported explicitly.
  "-language:implicitConversions", //Explicitly enables the implicit conversions feature
  "-unchecked", //Enable detailed unchecked (erasure) warnings
  "-Xfatal-warnings", //Fail the compilation if there are any warnings.
  "-Xlint", //Enable recommended additional warnings.
  "-Yinline-warnings", //Emit inlining warnings.
  "-Yno-adapted-args", //Do not adapt an argument list (either by inserting () or creating a tuple) to match the receiver.
  "-Ywarn-dead-code" //Warn when dead code is identified.
)

licenses += "MIT" -> url("http://opensource.org/licenses/MIT")
homepage := Some(url(s"https://github.com/Lasering/${name.value}"))
scmInfo := Some(ScmInfo(homepage.value.get, s"git@github.com:Lasering/${name.value}.git"))


publishMavenStyle := true
publishTo := Some(if (isSnapshot.value) Opts.resolver.sonatypeSnapshots else Opts.resolver.sonatypeStaging)
publishArtifact in Test := false
//releaseCrossBuild := true
//releasePublishArtifactsAction := PgpKeys.publishSigned.value
pomIncludeRepository := { _ => false }
pomExtra :=
  <developers>
    <developer>
      <id>Lasering</id>
      <name>Simão Martins</name>
      <url>https://github.com/Lasering</url>
    </developer>
  </developers>
