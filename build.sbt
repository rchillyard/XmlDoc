ThisBuild / organization := "com.phasmidsoftware"

ThisBuild / version := "1.2.0"

ThisBuild / scalaVersion := "3.9.0" // the current LTS

lazy val scalaModules = "org.scala-lang.modules"

// The compiler options that all the Phasmid libraries share.
ThisBuild / scalacOptions ++= Seq(
  "-encoding", "UTF-8",
  "-deprecation",
  "-feature",
  "-unchecked",
  "-Wunused:all",
  "-Wvalue-discard",
  "-Wnonunit-statement",
  "-Werror"
)
// Tests may use a value as a statement (for example, in a matcher), so each project's tests skip -Wnonunit-statement.
lazy val testScalacOptions = Test / scalacOptions ~= (_.filterNot(_ == "-Wnonunit-statement"))

lazy val commonSettings = Seq(
  testScalacOptions,
  libraryDependencies ++= Seq(
    "ch.qos.logback" % "logback-classic" % "1.6.2" % Test,
    "org.scalatest" %% "scalatest" % "3.2.20" % Test
  )
)

lazy val core = (project in file("core"))
  .settings(commonSettings)
  .settings(
    name := "xmldoc-core",
    libraryDependencies ++= Seq(
      scalaModules %% "scala-xml" % "2.4.0",
      scalaModules %% "scala-parser-combinators" % "2.4.0",
      "org.typelevel" %% "cats-effect" % "3.7.0",
      "com.phasmidsoftware" %% "flog" % "1.1.0",
      "com.typesafe.scala-logging" %% "scala-logging" % "3.9.6"
    )
  )

lazy val kml = (project in file("kml"))
  .dependsOn(core)
  .settings(commonSettings)
  .settings(
    name := "xmldoc-kml",
    libraryDependencies += "com.phasmidsoftware" %% "args" % "2.1.0"
  )

lazy val kmlIt = (project in file("kml-it"))
  .dependsOn(kml)
  .settings(commonSettings)
  .settings(
    name := "xmldoc-kml-it",
    publish / skip := true
  )

lazy val idml = (project in file("idml"))
  .dependsOn(core)
  .settings(commonSettings)
  .settings(
    name := "xmldoc-idml"
  )

lazy val circeVersion = "0.14.17"

// Records as JSON (circe), with the Spark schema which reads them. It needs nothing from core; its tests use kml.
lazy val json = (project in file("json"))
  .dependsOn(kml % "test->compile")
  .settings(commonSettings)
  .settings(
    name := "xmldoc-json",
    libraryDependencies ++= Seq(
      "io.circe" %% "circe-core" % circeVersion,
      "io.circe" %% "circe-parser" % circeVersion % Test
    )
  )

lazy val root = (project in file("."))
  .enablePlugins(ScalaUnidocPlugin, GhpagesPlugin)
  .aggregate(core, kml, idml, json)
  .settings(
    testScalacOptions,
    name := "XmlDoc",
    publish / skip := true,
    // The unified Scaladoc is published to https://rchillyard.github.io/XmlDoc/latest/api/ by `sbt ghpagesPushSite`
    ScalaUnidoc / unidoc / unidocProjectFilter := inProjects(core, kml, idml, json),
    ScalaUnidoc / siteSubdirName := "latest/api",
    addMappingsToSiteDir(ScalaUnidoc / packageDoc / mappings, ScalaUnidoc / siteSubdirName),
    git.remoteRepo := "git@github.com:rchillyard/XmlDoc.git"
  )
