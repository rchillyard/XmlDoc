organization := "com.phasmidsoftware"

name := "KMLDoc"

version := "1.0.7"

scalaVersion := "3.8.4"

Compile / doc / scalacOptions ++= Seq("-explaintypes", "-Vimplicits", "-implicits-debug", "-implicits-show-all", "-unchecked", "-feature", "-Xcheckinit", "-deprecation", "-Ywarn-dead-code", "-Ywarn-value-discard", "-Ywarn-unused", "-Xsource:3", "-deprecation")

lazy val scalaModules = "org.scala-lang.modules"

libraryDependencies += scalaModules %% "scala-xml" % "2.4.0"

libraryDependencies += "org.typelevel" %% "cats-effect" % "3.7.0"

libraryDependencies ++= Seq(
  "com.phasmidsoftware" %% "flog" % "1.0.15",
  "com.phasmidsoftware" %% "args" % "2.0.0",
  "ch.qos.logback" % "logback-classic" % "1.6.1" % "runtime",
  "com.typesafe.scala-logging" %% "scala-logging" % "3.9.6",
  "org.scalatest" %% "scalatest" % "3.2.20" % Test
)