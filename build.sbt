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

// The sending is TableParser's (tableparser-kafka-cats and tableparser-kafka-zio); the message value is a record's JSON.
// They also stream extraction (core's XmlStream) as fs2/ZIO streams.
// The tests use the Kafka client's MockProducer, so they need no broker; they use json's test records (from a KML file).
lazy val tableParserVersion = "2.3.0"

lazy val kafkaCats = (project in file("kafka-cats"))
  .dependsOn(core, json % "compile->compile;test->test")
  .settings(commonSettings)
  .settings(
    name := "xmldoc-kafka-cats",
    libraryDependencies += "com.phasmidsoftware" %% "tableparser-kafka-cats" % tableParserVersion
  )

lazy val kafkaZio = (project in file("kafka-zio"))
  .dependsOn(core, json % "compile->compile;test->test")
  .settings(commonSettings)
  .settings(
    name := "xmldoc-kafka-zio",
    libraryDependencies += "com.phasmidsoftware" %% "tableparser-kafka-zio" % tableParserVersion
  )

// Integration tests of kafka-cats and kafka-zio against a Kafka broker which they start (deliberately not aggregated
// by the root project, so `sbt test` stays fast): run them with `sbt kafkaIt/test`.
lazy val kafkaIt = (project in file("kafka-it"))
  .dependsOn(kafkaCats % "compile->compile;test->test", kafkaZio)
  .settings(commonSettings)
  .settings(
    name := "xmldoc-kafka-it",
    publish / skip := true,
    // The broker is built with Scala 2.13: it runs with core's Scala 3 build of scala-logging instead of its own.
    libraryDependencies += "io.github.embeddedkafka" %% "embedded-kafka" % "4.4.0" % Test exclude("com.typesafe.scala-logging", "scala-logging_2.13")
  )

lazy val root = (project in file("."))
  .enablePlugins(ScalaUnidocPlugin, GhpagesPlugin)
  .aggregate(core, kml, idml, json, kafkaCats, kafkaZio)
  .settings(
    testScalacOptions,
    name := "XmlDoc",
    publish / skip := true,
    // The unified Scaladoc is published to https://rchillyard.github.io/XmlDoc/latest/api/ by `sbt ghpagesPushSite`
    ScalaUnidoc / unidoc / unidocProjectFilter := inProjects(core, kml, idml, json, kafkaCats, kafkaZio),
    ScalaUnidoc / siteSubdirName := "latest/api",
    addMappingsToSiteDir(ScalaUnidoc / packageDoc / mappings, ScalaUnidoc / siteSubdirName),
    git.remoteRepo := "git@github.com:rchillyard/XmlDoc.git"
  )
