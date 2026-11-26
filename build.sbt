ThisBuild / organization := "com.phasmidsoftware"

ThisBuild / version := "1.2.3"

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

// MiMa checks each published module for binary compatibility with the last release: `sbt mimaReportBinaryIssues`.
// The version scheme is PVP (see publish.sbt): a patch release must pass; a minor release may break compatibility,
// on purpose and with a release note, by listing the breaks in mimaBinaryIssueFilters (or, after the release, by moving previousRelease on).
// A filter is a permanent, unconditional exemption for the symbol it names: it stays in force until the line is deleted by hand.
// Moving previousRelease on does not retire it, and neither does the version in the name of a mima-filters file (sbt-mima reads
// every *.backwards.excludes in that directory whatever it is called). So deleting the filters for release N, once previousRelease
// has reached N, is a required step and not tidying. See rchillyard/Visitor#50. There are no filters here, and 1.2.2 needed none.
val previousRelease = "1.2.2"
lazy val mimaSettings = mimaPreviousArtifacts := Set(organization.value %% name.value % previousRelease)
// For the projects which aren't published, so there's nothing to compare.
lazy val noMimaSettings = mimaFailOnNoPrevious := false

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
    mimaSettings,
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
    mimaSettings,
    name := "xmldoc-kml",
    libraryDependencies += "com.phasmidsoftware" %% "args" % "2.1.0"
  )

lazy val kmlIt = (project in file("kml-it"))
  .dependsOn(kml)
  .settings(commonSettings)
  .settings(
    noMimaSettings,
    name := "xmldoc-kml-it",
    publish / skip := true
  )

lazy val idml = (project in file("idml"))
  .dependsOn(core)
  .settings(commonSettings)
  .settings(
    mimaSettings,
    name := "xmldoc-idml",
    libraryDependencies += "com.lihaoyi" %% "ujson" % "4.4.3" % Test
  )

lazy val circeVersion = "0.14.17"

// Records as JSON (circe), with the Spark schema which reads them. It needs nothing from core; its tests use kml.
lazy val json = (project in file("json"))
  .dependsOn(kml % "test->compile")
  .settings(commonSettings)
  .settings(
    mimaSettings,
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
    mimaSettings,
    name := "xmldoc-kafka-cats",
    libraryDependencies += "com.phasmidsoftware" %% "tableparser-kafka-cats" % tableParserVersion
  )

lazy val kafkaZio = (project in file("kafka-zio"))
  .dependsOn(core, json % "compile->compile;test->test")
  .settings(commonSettings)
  .settings(
    mimaSettings,
    name := "xmldoc-kafka-zio",
    libraryDependencies += "com.phasmidsoftware" %% "tableparser-kafka-zio" % tableParserVersion
  )

// Integration tests of kafka-cats and kafka-zio against a Kafka broker which they start (deliberately not aggregated
// by the root project, so `sbt test` stays fast): run them with `sbt kafkaIt/test`.
lazy val kafkaIt = (project in file("kafka-it"))
  .dependsOn(kafkaCats % "compile->compile;test->test", kafkaZio)
  .settings(commonSettings)
  .settings(
    noMimaSettings,
    name := "xmldoc-kafka-it",
    publish / skip := true,
    // `sendStations` (the example of KAFKA.md) runs in its own JVM, in the root of the build (where its default KML file is).
    Test / run / fork := true,
    Test / run / baseDirectory := (ThisBuild / baseDirectory).value,
    // The broker is built with Scala 2.13: it runs with core's Scala 3 build of scala-logging instead of its own.
    libraryDependencies += "io.github.embeddedkafka" %% "embedded-kafka" % "4.4.0" % Test exclude("com.typesafe.scala-logging", "scala-logging_2.13")
  )

lazy val root = (project in file("."))
  .enablePlugins(ScalaUnidocPlugin, GhpagesPlugin)
  .aggregate(core, kml, idml, json, kafkaCats, kafkaZio)
  .settings(
    testScalacOptions,
    noMimaSettings,
    name := "XmlDoc",
    publish / skip := true,
    // The unified Scaladoc is published to https://rchillyard.github.io/XmlDoc/latest/api/ by `sbt ghpagesPushSite`
    ScalaUnidoc / unidoc / unidocProjectFilter := inProjects(core, kml, idml, json, kafkaCats, kafkaZio),
    ScalaUnidoc / siteSubdirName := "latest/api",
    addMappingsToSiteDir(ScalaUnidoc / packageDoc / mappings, ScalaUnidoc / siteSubdirName),
    git.remoteRepo := "git@github.com:rchillyard/XmlDoc.git"
  )

// ============================================================================
// ONE GRAND TOTAL ACROSS EVERY MODULE (Issue #102)
// ============================================================================
// `sbt test` prints a summary per module and no overall total, so the modules have to be added up
// by hand. Nothing can sum them because `test` returns Unit: the counts are logged and discarded.
// `Test / executeTests`, the task sitting underneath `test`, returns a `Tests.Output`, so one per
// module can be collected and folded.
//
// The filter names kmlIt and kafkaIt as well as the aggregate. They are deliberately outside the
// root aggregate, so that `sbt test` stays fast, but CircleCI runs both of them explicitly
// (`sbt kmlIt/test`, `sbt kafkaIt/test`). A total built from `inAggregates` alone would therefore
// omit two modules while looking authoritative, which is the one thing a summary line must not do.
// The cost is that `sbt testAll` is the slow, complete run: kafkaIt starts a Kafka broker in the JVM.
//
// Note that `executeTests` does not fail the build - that is `test`'s job - so this task has to do
// it itself, or a red suite would exit 0.

lazy val testAll = taskKey[Unit]("Run every module's tests, the unaggregated integration ones included, and report one grand total.")

testAll := {
  val log = streams.value.log
  // includeRoot = false: the root aggregates but has no tests of its own, and counting it would
  // make the line say nine modules where there are eight.
  val outputs = (Test / executeTests).all(ScopeFilter(inAggregates(LocalRootProject, includeRoot = false) || inProjects(kmlIt, kafkaIt))).value
  // `testResultLogger` never runs for executeTests, so the per-module lines are printed here or not at all.
  outputs.flatMap(_.summaries).foreach(summary => log.info(s"${summary.name}: ${summary.summaryText}"))
  val total = outputs.flatMap(_.events.values).foldLeft(SuiteResult.Empty)(_ + _)
  val failed = total.failureCount + total.errorCount
  log.info(s"TOTAL over ${outputs.size} modules: ${total.passedCount} passed, ${total.failureCount} failed, " +
    s"${total.errorCount} errors, ${total.skippedCount} skipped, ${total.ignoredCount} ignored, " +
    s"${total.canceledCount} canceled, ${total.pendingCount} pending")
  if (failed > 0) sys.error(s"$failed test(s) did not pass")
}
