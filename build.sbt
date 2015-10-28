organization := "codes.simon"
name := "scala-expect"
version := "1.1"

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
  "-deprecation",
  "-encoding", "UTF-8",
  "-feature",
  "-language:implicitConversions",
  "-unchecked",
  "-Xfatal-warnings",
  "-Xlint",
  "-Yinline-warnings",
  "-Yno-adapted-args",
  "-Ywarn-dead-code",
  //"-Ywarn-numeric-widen",     // issue in 2.10
  //"-Ywarn-value-discard",
  //"-Ywarn-unused-import",     // 2.11 only
  "-Xfuture"
)

homepage := Some(url("https://github.com/Lasering/scala-expect"))
licenses += "MIT" -> url("http://opensource.org/licenses/MIT")
scmInfo := Some(ScmInfo(url("https://github.com/Lasering/scala-expect"), "git@github.com:Lasering/scala-expect.git"))
licenses := Seq("MIT License" -> url("https://opensource.org/licenses/MIT"))


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
