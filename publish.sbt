import xerial.sbt.Sonatype.sonatypeCentralHost

// Target the new Sonatype Central Portal explicitly, rather than legacy OSSRH.
ThisBuild / sonatypeCredentialHost := sonatypeCentralHost

// Optional but useful
ThisBuild / description := "Scala libraries for reading, writing, and manipulating XML-based document formats, including KML."
ThisBuild / homepage := Some(url("https://github.com/rchillyard/XmlDoc"))
ThisBuild / organizationName := "Phasmid Software"
ThisBuild / organizationHomepage := Some(url("https://phasmidsoftware.com/"))

// Publishing configuration
ThisBuild / publishTo := {
  val centralSnapshots = "https://central.sonatype.com/repository/maven-snapshots/"
  if (isSnapshot.value) Some("central-snapshots" at centralSnapshots)
  else localStaging.value
}
ThisBuild / publishMavenStyle := true

// Credentials
ThisBuild / credentials += Credentials(Path.userHome / ".sbt" / "sonatype_credentials")

// Required POM metadata for Maven Central
ThisBuild / licenses := List("MIT" -> url("https://opensource.org/licenses/MIT"))
ThisBuild / scmInfo := Some(
  ScmInfo(
    url("https://github.com/rchillyard/XmlDoc"),
    "scm:git@github.com:rchillyard/XmlDoc.git"
  )
)
ThisBuild / developers := List(
  Developer(
    id    = "rchillyard",
    name  = "Robin Hillyard",
    email = "rchillyard@phasmidsoftware.com",
    url   = url("https://phasmidsoftware.com")
  )
)

// Optional

// Publishing settings
ThisBuild / versionScheme := Some("early-semver")
ThisBuild / publishConfiguration := publishConfiguration.value.withOverwrite(true)
ThisBuild / publishLocalConfiguration := publishLocalConfiguration.value.withOverwrite(true)

// Optional: Skip publishing of docs and sources for snapshot versions
ThisBuild / publishArtifact := true

// Remove all additional repository other than Maven Central from POM
ThisBuild / pomIncludeRepository := { _ => false }

// These are read directly by sbt's publish/makePom machinery rather than via the settings
// dependency graph, so lintUnused can't see the usage and flags them as unused.
Global / excludeLintKeys ++= Set(
  publishMavenStyle,
  pomIncludeRepository
)
