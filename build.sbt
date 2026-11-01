ThisBuild / organization := "com.phasmidsoftware"

ThisBuild / version := "1.1.2"

ThisBuild / scalaVersion := "3.9.0" // the current LTS

lazy val scalaModules = "org.scala-lang.modules"

lazy val commonSettings = Seq(
  scalacOptions ++= Seq("-deprecation", "-feature", "-unchecked", "-Wunused:all"),
  // JUnit-style reports, collected by CircleCI
  Test / testOptions += Tests.Argument(TestFrameworks.ScalaTest, "-u", "target/test-reports"),
  libraryDependencies ++= Seq(
    "ch.qos.logback" % "logback-classic" % "1.6.2" % "runtime",
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
      "com.phasmidsoftware" %% "flog" % "1.0.15",
      "com.typesafe.scala-logging" %% "scala-logging" % "3.9.6"
    )
  )

lazy val kml = (project in file("kml"))
  .dependsOn(core)
  .settings(commonSettings)
  .settings(
    name := "xmldoc-kml",
    libraryDependencies += "com.phasmidsoftware" %% "args" % "2.0.0"
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

lazy val root = (project in file("."))
  .enablePlugins(ScalaUnidocPlugin, GhpagesPlugin)
  .aggregate(core, kml, idml)
  .settings(
    name := "XmlDoc",
    publish / skip := true,
    // The unified Scaladoc is published to https://rchillyard.github.io/XmlDoc/latest/api/ by `sbt ghpagesPushSite`
    ScalaUnidoc / unidoc / unidocProjectFilter := inProjects(core, kml, idml),
    ScalaUnidoc / siteSubdirName := "latest/api",
    addMappingsToSiteDir(ScalaUnidoc / packageDoc / mappings, ScalaUnidoc / siteSubdirName),
    git.remoteRepo := "git@github.com:rchillyard/XmlDoc.git"
  )
