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
// PVP (the Haskell Package Versioning Policy): the first two numbers (x.y) make the major version, so a patch release
// (x.y.z+1) must be binary compatible with x.y.z, while a minor release (x.y+1.0) may break compatibility, with a release
// note. MiMa (see build.sbt) checks it.
ThisBuild / versionScheme := Some("pvp")
ThisBuild / publishConfiguration := publishConfiguration.value.withOverwrite(true)
ThisBuild / publishLocalConfiguration := publishLocalConfiguration.value.withOverwrite(true)


// Remove all additional repository other than Maven Central from POM
ThisBuild / pomIncludeRepository := { _ => false }

// These are read directly by sbt's publish/makePom machinery rather than via the settings
// dependency graph, so lintUnused can't see the usage and flags them as unused.
Global / excludeLintKeys ++= Set(
  publishMavenStyle,
  pomIncludeRepository
)
