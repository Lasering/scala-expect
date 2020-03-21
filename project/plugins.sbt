logLevel := Level.Warn

addSbtPlugin("com.github.gseitz" % "sbt-release" % "1.0.9")
addSbtPlugin("com.timushev.sbt" % "sbt-updates" % "0.3.4")
addSbtPlugin("com.typesafe.sbt" % "sbt-ghpages" % "0.6.2")
addSbtPlugin("com.jsuereth" % "sbt-pgp" % "1.1.2")
addSbtPlugin("org.xerial.sbt" % "sbt-sonatype" % "2.3")
addSbtPlugin("org.scoverage" % "sbt-scoverage" % "1.6.0-M5")
addSbtPlugin("com.codacy" % "sbt-codacy-coverage" % "2.112")

addSbtPlugin("ch.epfl.lamp" % "sbt-dotty" % "0.4.0")

//addCompilerPlugin("io.tryp" % "splain" % "0.3.4" cross CrossVersion.patch)
