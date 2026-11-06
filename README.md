[![Maven Central (core)](https://img.shields.io/maven-central/v/com.phasmidsoftware/xmldoc-core_3?label=maven-central%3A%20core)](https://central.sonatype.com/artifact/com.phasmidsoftware/xmldoc-core_3)
[![Maven Central (kml)](https://img.shields.io/maven-central/v/com.phasmidsoftware/xmldoc-kml_3?label=maven-central%3A%20kml)](https://central.sonatype.com/artifact/com.phasmidsoftware/xmldoc-kml_3)
[![API Docs](https://img.shields.io/badge/api-docs-blue)](https://rchillyard.github.io/XmlDoc/latest/api/)
[![Codacy Badge](https://app.codacy.com/project/badge/Grade/245437d8d4004bbe8ed41198b6f57419)](https://app.codacy.com/gh/rchillyard/XmlDoc/dashboard)
[![Codacy Coverage](https://app.codacy.com/project/badge/Coverage/245437d8d4004bbe8ed41198b6f57419)](https://app.codacy.com/gh/rchillyard/XmlDoc/dashboard)
[![CircleCI](https://dl.circleci.com/status-badge/img/gh/rchillyard/XmlDoc/tree/main.svg?style=shield)](https://dl.circleci.com/status-badge/redirect/gh/rchillyard/XmlDoc/tree/main)
[![GitHub Top Languages](https://img.shields.io/github/languages/top/rchillyard/XmlDoc)](https://github.com/rchillyard/XmlDoc)
[![GitHub](https://img.shields.io/github/license/rchillyard/XmlDoc)](https://github.com/rchillyard/XmlDoc/blob/main/LICENSE)
[![GitHub last commit](https://img.shields.io/github/last-commit/rchillyard/XmlDoc)](https://github.com/rchillyard/XmlDoc/commits/main)
[![GitHub issues](https://img.shields.io/github/issues-raw/rchillyard/XmlDoc)](https://github.com/rchillyard/XmlDoc/issues)
[![GitHub issues by-label](https://img.shields.io/github/issues/rchillyard/XmlDoc/bug)](https://github.com/rchillyard/XmlDoc/issues?q=is%3Aissue+is%3Aopen+label%3Abug)

# XmlDoc

A Scala 3 toolkit for reading, writing, and round-tripping XML-based document formats. Originally
named KmlDoc and focused solely on KML, it's now split into a generic `core` module (typed
extraction and rendering, plus a generic fallback representation for content that isn't worth
modeling as its own type) and format-specific modules built on top of it.

See [DESIGN.md](DESIGN.md) for how the extraction/rendering framework actually works — the type
class hierarchy, the field-naming conventions, derivation (`derives Extractor, Renderer`), and the escape
hatches for irregular real-world XML. This README covers what the modules are and how to use them.

Full Scaladoc is at <https://rchillyard.github.io/XmlDoc/latest/api/>.

## Modules

| Module | Artifact | Status |
|---|---|---|
| [`core`](core) | `xmldoc-core` | Published. Format-agnostic: the `Extractor[T]`/`Renderer[T]` framework, streaming extraction (`XmlStream`), `GenericElement` (generic XML tree), `Zip` helpers. |
| [`kml`](kml) | `xmldoc-kml` | Published. A fairly complete typed model of the [KML reference](https://developers.google.com/kml/documentation/kmlreference) — extraction, rendering, and simple editing/merging of `.kml` files. |
| [`kml-it`](kml-it) | not published | Integration tests against real third-party `.kml` files (deliberately not aggregated by the root project, so `sbt test` stays fast). |
| [`json`](json) | `xmldoc-json` | New in 1.2.0. Records as JSON (circe), one per line, and the Spark SQL schema which reads them (see [Records as JSON, for Spark](#records-as-json-for-spark)). |
| [`kafka-cats`](kafka-cats), [`kafka-zio`](kafka-zio) | `xmldoc-kafka-cats`, `xmldoc-kafka-zio` | New in 1.2.0. Send records to Kafka, one JSON message per record, with fs2-kafka or zio-kafka (see [Sending records to Kafka](#sending-records-to-kafka)). |
| [`kafka-it`](kafka-it) | not published | Integration tests of the Kafka modules against a broker which they start (not aggregated by the root project: run `sbt kafkaIt/test`). |
| [`idml`](idml) | not published | Early-stage support for Adobe InDesign's IDML format, built on `core`'s generic representation rather than `kml`'s fully-typed one. Not yet released. |

## Getting started

This version of the README.md file refers to version: 1.2.0.

Add the modules you need to your _build.sbt_,
where `x.y.z` is the latest version on Maven Central: it's shown on the [Sonatype Central](https://central.sonatype.com/artifact/com.phasmidsoftware/xmldoc-core_3) badge at the top of this page.

```scala
libraryDependencies ++= Seq(
    "com.phasmidsoftware" %% "xmldoc-core" % "x.y.z"
  , "com.phasmidsoftware" %% "xmldoc-kml" % "x.y.z"
)
```

XmlDoc 1.1.3 and later need Scala 3.9 or later; for Scala 3.8, use 1.1.2. (No release supports Scala 3.3.)
XmlDoc doesn't include a logging backend (it logs through SLF4J): add one, such as `logback-classic`, to your own application if you want its log messages.

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

### Your own types

To read (and write) XML of your own, describe each element as a case class and derive its `Extractor` and `Renderer`.
The field names say where each value is: `_foo` is the attribute `foo`, `` `$` `` the element's text, `foo` the child element `foo`,
`maybeFoo: Option[...]` an optional child `foo`, and a `Seq` field the repeated children (see [DESIGN.md](DESIGN.md)).

```scala
import com.phasmidsoftware.xmldoc.core.Text
import com.phasmidsoftware.xmldoc.render.Renderer
import com.phasmidsoftware.xmldoc.xml.Extractor

case class Reading(_hour: Int, $: Double) derives Extractor, Renderer
case class Elevation($: Double) // derived where it's needed
case class Station(_id: String, name: Text, maybeElevation: Option[Elevation], Readings: Seq[Reading]) derives Extractor, Renderer

val station: scala.util.Try[Station] =
  Extractor.extract[Station](<station id="S1"><name>Logan</name><Reading hour="1">12.5</Reading></station>)
```

A field whose type is another case class without instances of its own is derived in turn.
A case class may also have one _auxiliary_ parameter, in a second parameter list, which is read from the same element
(e.g. `kml`'s `case class Placemark(Geometry: Seq[Geometry])(val featureData: FeatureData)`).
For a sealed trait, derive the `MultiExtractor` of its sequences and its `Renderer`:
`given MultiExtractor[Seq[Shape]] = MultiExtractor.derived` and `given Renderer[Shape] = Renderer.derived`;
each subtype's tag is its class name.

Round-tripping (extract, then render back to XML) is exercised extensively in the test suites —
see `kml/src/test/scala/.../KmlSpec.scala` for realistic examples, including editing/merging
`Placemark`s.

### Records as JSON, for Spark

The `json` module (`"com.phasmidsoftware" %% "xmldoc-json" % "x.y.z"`) writes records as JSON, one object per line
(for a Kafka message or a `.jsonl` file), and derives from the same case class the Spark schema which reads that JSON,
so the two can't drift apart.
Build plain record case classes from the KML values you need (a type with a second parameter list, such as `Placemark`, can't be written directly),
and derive circe's `Encoder` and XmlDoc's `SparkSchema` for each:

```scala
import com.phasmidsoftware.xmldoc.json.{JsonLines, SparkSchema}
import io.circe.Encoder

case class Point(lon: Double, lat: Double) derives Encoder.AsObject, SparkSchema
case class Station(name: String, point: Point, elevation: Option[Double]) derives Encoder.AsObject, SparkSchema

JsonLines.line(Station("Logan", Point(-71.01, 42.37), Some(6.0)))
// {"name":"Logan","point":{"lon":-71.01,"lat":42.37},"elevation":6.0}
SparkSchema.ddl[Station]
// `name` STRING, `point` STRUCT<`lon`: DOUBLE, `lat`: DOUBLE>, `elevation` DOUBLE
```

On the Spark side (Scala 2.13 or PySpark), paste the schema string in and parse each message with `from_json`:

```scala
val schema = "`name` STRING, `point` STRUCT<`lon`: DOUBLE, `lat`: DOUBLE>, `elevation` DOUBLE"
val stations = spark.readStream.format("kafka").option("subscribe", "stations") /* ... */ .load()
  .select(from_json(col("value").cast("string"), schema, Map.empty[String, String]).as("station"))
  .select("station.*")
```

`Option[X]` maps to a nullable `X`; `Seq`, `List`, `Vector` and `Set` to `ARRAY`; `Map[String, X]` to `MAP<STRING, X>`;
a nested case class to `STRUCT`; and an enum without parameters to `STRING` (derive its encoder with circe's `ConfiguredEnumEncoder`).
A value class has no `Mirror`, so give it the schema of what it's written as: `given SparkSchema[Meters] = SparkSchema[Double].as`.

### Sending records to Kafka

`xmldoc-kafka-cats` (fs2-kafka) and `xmldoc-kafka-zio` (zio-kafka) send each record to a topic as one JSON message
(its `JsonLines.line`), with a key of your choice. They use TableParser's Kafka modules (`tableparser-kafka-cats`/`-zio`).

```scala
import cats.effect.IO
import com.phasmidsoftware.xmldoc.kafka.cats.IOKafka
import fs2.Stream
import fs2.kafka.ProducerSettings

val settings = ProducerSettings[IO, String, String].withBootstrapServers("localhost:9092")
Stream.emits(stations).through(IOKafka.toKafka("stations", settings)(_.name)).compile.drain
```

```scala
import com.phasmidsoftware.xmldoc.kafka.zio.ZIOKafka
import zio.ZLayer
import zio.kafka.producer.{Producer, ProducerSettings}
import zio.kafka.serde.Serde
import zio.stream.ZStream

val producer = ZLayer.scoped(Producer.make(ProducerSettings(List("localhost:9092"))))
ZStream.fromIterable(stations).via(ZIOKafka.toKafka("stations", Serde.string)(_.name)).runDrain.provideLayer(producer)
```

For messages without a key, use a `Unit` key: `ProducerSettings[IO, Unit, String]` with `(_ => ())`, or `ZIOKafka.unitSerializer`.
The Spark job then reads the topic with `from_json` and `SparkSchema.ddl` (above).

### Streaming a large file

To avoid loading a whole document before extracting from it, `XmlStream` (in `core`) reads it with a pull parser (StAX)
and extracts each element which matches a selector as soon as that element has been read; nothing else is kept.
A selector is a tag (`Placemark`, at any depth), a path of tags (`Folder/Placemark`), or a path from the root (`/kml/Document/Placemark`);
tags are matched by their local names, whatever their namespace. Each element's extraction succeeds or fails on its own (a `Try`).

```scala
import com.phasmidsoftware.xmldoc.xml.XmlStream
import java.io.FileInputStream

XmlStream.extractFrom[Placemark, Int]("Placemark", new FileInputStream("big.kml"))(_.count(_.isSuccess))
```

The Kafka modules stream the same way, so records go to Kafka as they are read (`IOXmlStream` for fs2, `ZIOXmlStream` for ZIO):

```scala
import com.phasmidsoftware.xmldoc.kafka.cats.{IOKafka, IOXmlStream}
import scala.util.Success

IOXmlStream.extractFile[Placemark]("big.kml", "Placemark")
  .collect { case Success(p) => p }
  .flatMap(p => Stream.emits(toStations(p)))
  .through(IOKafka.toKafka("stations", settings)(_.name))
  .compile.drain
```

A record which needs something from outside its element (e.g. a `Style` defined at the document level) must be completed separately.

## Versions

Version 1.2.0 Derives `Extractor` and `Renderer` from case classes (`derives Extractor, Renderer`, or `Extractor.derived`/`Renderer.derived`),
including the auxiliary-parameter pattern, and `MultiExtractor`/`Renderer` for sealed traits (`MultiExtractor.derived`): Issue #63.
This replaces the arity ladder (`extractor10` ... `extractorPartial72`, `multiExtractorN`, `rendererN`, `rendererNSuper`, `rendererSuperN`),
which is deprecated, and `kml` now uses derivation throughout; its abstract types (`Feature`, `Geometry`, ...) are sealed.
`logback-classic` is no longer a (runtime) dependency.
A new module, `json` (`xmldoc-json`), writes records as JSON Lines with circe, and derives the Spark SQL schema which reads them (`SparkSchema.ddl`): Issue #64.
Two more, `kafka-cats` and `kafka-zio` (`xmldoc-kafka-cats`, `xmldoc-kafka-zio`), send records to Kafka as JSON messages, using TableParser's Kafka modules: Issue #65.
`XmlStream` (in `core`) extracts the selected elements of a large document as they are read, with a StAX pull parser,
and `IOXmlStream`/`ZIOXmlStream` (in the Kafka modules) do so as fs2/ZIO streams: Issue #66.

Version 1.1.3 Adds 3-way merging of XML trees to `core` (package `merge`): a PCS representation of
trees, after Lindholm (`Pcs`), edit detection (`PcsEditDetector`), reconciliation of two edit sets
(`PcsMerger`), and reconstruction of the merged tree (`PcsTreeBuilder`). The `idml` module wires
these together end to end (`ThreeWayMerger`) and adds a runnable git merge driver for `.idml`
files (`IdmlMergeDriver`); `kml` gets a full real-document 3-way merge test. Built with Scala 3.9.0
(the current LTS), so it needs Scala 3.9 or later (1.1.2 needs 3.8). Uses Flog 1.1.0 and Args 2.1.0.

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
