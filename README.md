[![Maven Central (core)](https://img.shields.io/maven-central/v/com.phasmidsoftware/xmldoc-core_3?label=maven-central%3A%20core)](https://central.sonatype.com/artifact/com.phasmidsoftware/xmldoc-core_3)
[![Maven Central (kml)](https://img.shields.io/maven-central/v/com.phasmidsoftware/xmldoc-kml_3?label=maven-central%3A%20kml)](https://central.sonatype.com/artifact/com.phasmidsoftware/xmldoc-kml_3)
[![Codacy Badge](https://app.codacy.com/project/badge/Grade/245437d8d4004bbe8ed41198b6f57419)](https://app.codacy.com/gh/rchillyard/XmlDoc/dashboard?utm_source=gh&utm_medium=referral&utm_content=&utm_campaign=Badge_grade)
![GitHub Top Languages](https://img.shields.io/github/languages/top/rchillyard/XmlDoc)
![GitHub](https://img.shields.io/github/license/rchillyard/XmlDoc)
![GitHub last commit](https://img.shields.io/github/last-commit/rchillyard/XmlDoc)
![GitHub issues](https://img.shields.io/github/issues-raw/rchillyard/XmlDoc)
![GitHub issues by-label](https://img.shields.io/github/issues/rchillyard/XmlDoc/bug)

# XmlDoc

A Scala 3 toolkit for reading, writing, and round-tripping XML-based document formats. Originally
named KmlDoc and focused solely on KML, it's now split into a generic `core` module (typed
extraction and rendering, plus a generic fallback representation for content that isn't worth
modeling as its own type) and format-specific modules built on top of it.

See [DESIGN.md](DESIGN.md) for how the extraction/rendering framework actually works — the type
class hierarchy, the field-naming conventions, the combinator "arity ladder," and the escape
hatches for irregular real-world XML. This README covers what the modules are and how to use them.

## Modules

| Module | Artifact | Status |
|---|---|---|
| [`core`](core) | `xmldoc-core` | Published. Format-agnostic: the `Extractor[T]`/`Renderer[T]` framework, `GenericElement` (generic XML tree), `Zip` helpers. |
| [`kml`](kml) | `xmldoc-kml` | Published. A fairly complete typed model of the [KML reference](https://developers.google.com/kml/documentation/kmlreference) — extraction, rendering, and simple editing/merging of `.kml` files. |
| [`kml-it`](kml-it) | not published | Integration tests against real third-party `.kml` files (deliberately not aggregated by the root project, so `sbt test` stays fast). |
| [`idml`](idml) | not published | Early-stage support for Adobe InDesign's IDML format, built on `core`'s generic representation rather than `kml`'s fully-typed one. Not yet released. |

## Getting started

```scala
libraryDependencies ++= Seq(
  "com.phasmidsoftware" %% "xmldoc-core" % "<latest>",
  "com.phasmidsoftware" %% "xmldoc-kml" % "<latest>"
)
```

(see the Maven Central badges above for the current published version).

```scala
import com.phasmidsoftware.xmldoc.kml.*
import com.phasmidsoftware.xmldoc.xml.Extractor.extractMulti
import scala.xml.XML
import scala.util.Success

val xml = XML.loadFile("sample.kml")
extractMulti[Seq[KML]](xml) match {
  case Success(kmls) => kmls.foreach(println)
  case scala.util.Failure(x) => x.printStackTrace()
}
```

Round-tripping (extract, then render back to XML) is exercised extensively in the test suites —
see `kml/src/test/scala/.../KmlSpec.scala` for realistic examples, including editing/merging
`Placemark`s.

## Versions

Version 1.1.0 Split into `core`/`kml`/`kml-it` modules; renamed KmlDoc → XmlDoc; published to
Maven Central. `idml` module added (early stage, unreleased). The `kml` round-trip tests were
found to be silently non-functional (they never actually re-parsed rendered output), and fixing
that surfaced several genuine bugs, including a gap in the original #19 fix (an empty self-closing
element still crashed extraction) plus Issues #20, #21, #27, #29, #44, #51 — see the
[issue tracker](https://github.com/rchillyard/XmlDoc/issues?q=is%3Aissue+is%3Aclosed) for details
on each.

Version 1.0.5 Migrate to Scala 3, ...

Version 1.0.4 Many issues fixed. All non-gx entities are implemented now.

Version 1.0.3 Fixed Issue #19, various other mostly cosmetic changes.

Version 1.0.2 Improvements to rendering and extraction with more KML objects possible.

Version 1.0.1 Improvements: first round-trip extract/render of minisample.kml

Version 1.0 Preliminary work. Most of the functionality is working but there is much still to do, especially for the KML_Samples.kml file.
