organization := "codes.simon"

name := "scala-expect"

version := "1.1"

scalaVersion := "2.11.7"

libraryDependencies ++= Seq(
  "com.typesafe.scala-logging" %% "scala-logging" % "3.1.0",
  "ch.qos.logback" % "logback-classic" % "1.1.3",
  "org.scalatest" %% "scalatest" % "2.2.4" % "test",
  "com.typesafe" % "config" % "1.3.0"
)

scalacOptions ++= Seq("-deprecation")

initialize := {
  val required = "1.8"
  val current  = sys.props("java.specification.version")
  assert(current == required, s"Unsupported JDK: java.specification.version $current != $required")
}

useGpg := true

publishTo := {
  val nexus = "https://oss.sonatype.org/"
  if (isSnapshot.value)
    Some("snapshots" at s"${nexus}content/repositories/snapshots")
  else
    Some("releases"  at s"${nexus}service/local/staging/deploy/maven2")
}

publishMavenStyle := true

publishArtifact in Test := false

pomIncludeRepository := { _ => false }

licenses := Seq("MIT License" -> url("https://opensource.org/licenses/MIT"))

homepage := Some(url("https://github.com/Lasering/scala-expect"))

pomExtra :=
  <scm>
    <url>git@github.com:Lasering/scala-expect.git</url>
    <connection>scm:git:git@github.com:Lasering/scala-expect.git</connection>
  </scm>
  <developers>
    <developer>
      <id>Lasering</id>
      <name>Simão Martins</name>
    </developer>
  </developers>
