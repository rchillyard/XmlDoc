ThisBuild / organization := "com.phasmidsoftware"

ThisBuild / version := "1.0.7"

ThisBuild / scalaVersion := "3.8.4"

lazy val scalaModules = "org.scala-lang.modules"

lazy val commonSettings = Seq(
  Compile / doc / scalacOptions ++= Seq("-explaintypes", "-Vimplicits", "-implicits-debug", "-implicits-show-all", "-unchecked", "-feature", "-Xcheckinit", "-deprecation", "-Ywarn-dead-code", "-Ywarn-value-discard", "-Ywarn-unused", "-Xsource:3", "-deprecation"),
  libraryDependencies ++= Seq(
    "ch.qos.logback" % "logback-classic" % "1.6.1" % "runtime",
    "org.scalatest" %% "scalatest" % "3.2.20" % Test
  )
)

lazy val core = (project in file("core"))
  .settings(commonSettings)
  .settings(
    name := "kmldoc-core",
    libraryDependencies ++= Seq(
      scalaModules %% "scala-xml" % "2.4.0",
      scalaModules %% "scala-parser-combinators" % "2.4.0",
      "org.typelevel" %% "cats-effect" % "3.7.0",
      "com.phasmidsoftware" %% "flog" % "1.0.15",
      "com.typesafe.scala-logging" %% "scala-logging" % "3.9.6"
    )
  )

lazy val kml = (project in file("kml"))
  .dependsOn(core)
  .configs(IntegrationTest)
  .settings(Defaults.itSettings)
  .settings(commonSettings)
  .settings(
    name := "kmldoc-kml",
    libraryDependencies ++= Seq(
      "com.phasmidsoftware" %% "args" % "2.0.0",
      "org.scalatest" %% "scalatest" % "3.2.20" % IntegrationTest
    )
  )

lazy val root = (project in file("."))
  .aggregate(core, kml)
  .settings(
    name := "KMLDoc",
    publish / skip := true
  )
