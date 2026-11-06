# XmlDoc design notes

This document explains how the extraction/rendering framework in `core` actually works, and the
conventions that `kml` (and, more sparingly, `idml`) build on top of it. It's aimed at someone
adding a new element type or format module, not at an end user of the library (see
[README.md](README.md) for that).

## Goals

- **Typed, round-trippable models for the parts of a format worth modeling** — a `case class` per
  real element, with an `Extractor[T]` to build it from XML and a `Renderer[T]` to serialize it
  back, such that `render(extract(xml))` is semantically equal to the original.
- **A generic fallback for everything else** — most real-world document formats have far more
  structure than any one project needs to model precisely. Rather than a giant sealed hierarchy
  covering the entire spec, unmodeled content falls through to `GenericElement`, a lossless,
  untyped tree (see below). `kml` currently models almost everything explicitly (it predates this
  approach); `idml` deliberately does not, and models new element types incrementally as they turn
  out to matter (see "Mixed parsing" below).
- **Verify against real files, not just synthetic ones.** Every non-trivial bug found in this
  codebase (across several sessions) was found by round-tripping *actual* third-party documents,
  not by inventing test XML by hand. Synthetic tests are still useful for pinning down a specific
  case once you know what it is, but they don't reliably *find* cases you didn't think of. See
  "Testing philosophy" below.

## Module layout

```
core   - format-agnostic: Extractor[T]/Renderer[T], GenericElement, Zip, misc utilities.
kml    - a typed model of KML (developers.google.com/kml/documentation/kmlreference), on core.
kml-it - integration tests for kml against real third-party .kml files. Not aggregated by the
         root project, so plain `sbt test` stays fast; run explicitly (`sbt kmlIt/test`).
idml   - early-stage support for Adobe InDesign's IDML format, on core. Uses the generic
         (GenericElement-first) approach rather than kml's fully-typed one.
json   - records as JSON (circe) and the Spark SQL schema which reads them. Needs nothing
         from core (its tests use kml).
kafka-cats, kafka-zio
       - send records to Kafka as JSON messages (fs2-kafka, zio-kafka), on json and
         TableParser's Kafka modules.
kafka-it - integration tests of the Kafka modules against a broker started in the JVM
         (embedded-kafka). Not aggregated by the root project; run `sbt kafkaIt/test`.
```

`core` has no knowledge of KML or IDML; everything format-specific lives in `kml`/`idml`.

## JSON and the Spark schema (`json`)

The pipeline is XML → XmlDoc → Kafka (JSON) → Spark, where the Spark job (Scala 2.13 or PySpark: Spark isn't
published for Scala 3) reads each message with `from_json(col("value"), schema)`. `SparkSchema[T]` derives that schema
from the same case class as circe's `Encoder[T]`, so the producer and the Spark job can't drift apart.

- **Plain records only.** Both circe's derivation and `SparkSchema.derived` use a `Mirror`, which Scala 3 doesn't
  synthesize for a case class with a second parameter list; so the `kml` model (`Placemark` and so on) isn't written
  directly. Students build their own flat(ish) record case classes from KML values. (Our own macro, as in `core`'s
  derivation, could cover the aux pattern, but it would mean a JSON shape of our own to maintain.)
- **No Spark dependency.** The schema is a `SparkType` (a small model of Spark SQL's types) rendered as a DDL string;
  every field name is quoted with backticks, so it never depends on Spark's reserved words (with ANSI mode on, by
  default in Spark 4, names like `order` must be quoted).
- **The mapping follows circe's default encoders**: `Option[X]` is a nullable `X` (circe writes `None` as `null`),
  collections are `ARRAY`, `Map[String, X]` is `MAP<STRING, X>` (circe writes a JSON object), a nested case class is a
  `STRUCT`, an enum without parameters is a `STRING` (with circe's `ConfiguredEnumEncoder`). A value class has no
  `Mirror`, so both its encoder and its schema are given by hand (`SparkSchema[Double].as`). `java.time.Instant` maps to
  `TIMESTAMP` (circe writes ISO-8601); if instants have digits beyond milliseconds, check them against Spark's
  `timestampFormat` option (not tested here, since there's no Spark).
- **Tested** by checking that the JSON written for a record (including records built from a real `.kml` file) has exactly
  the fields and kinds of value that its schema describes.

## Kafka (`kafka-cats`, `kafka-zio`)

Each module is one method, `toKafka(topic, settings)(key)`, for any record type with a circe `Encoder`: it's
TableParser's `toKafkaWith` with `JsonLines.line` as the message value. Depending on TableParser's modules (rather than
copying their code, or splitting it into a shared library) was Robin's choice on #65: the cost is that these two modules
bring in `tableparser-core`; the rest of XmlDoc doesn't. The unit tests use the Kafka client's `MockProducer`; `kafka-it`
sends the same records to a real broker (embedded-kafka, a Scala 2.13 build, with its `scala-logging_2.13` excluded)
and reads them back. The records in both are built from a real `.kml` file (`KmlRecords`, in `json`'s tests).

## The `Extractor[T]` / `Renderer[T]` framework

### The type classes

```scala
trait Extractor[T] { def extract(node: scala.xml.Node): Try[T] }
trait Renderer[T]   { def render(t: T, format: Format, state: StateR): Try[String] }
```

Both are ordinary type classes with an implicit instance per type, resolved by the compiler the
usual way, and normally derived (see "Derivation" below). `MultiExtractor[T]` is the analogous type class for extracting a `Seq[T]` out of a
`NodeSeq]` (used for polymorphic dispatch — see below).

### Field-naming conventions

A KML element is almost always modeled as one case class per real element, and the *case class
field names themselves* drive extraction/rendering — there's no separate schema or annotation
layer. The conventions (all in `core`'s `Extractor.scala`, in `doExtractField`) are:

| Field name shape | Meaning |
|---|---|
| `_foo: T` (leading underscore) | an XML *attribute* named `foo`, not a child element |
| `__foo: T` (double underscore) | an *optional* XML attribute |
| `` `$`: T `` | the element's own text content |
| `foo: T` | a required child element named `foo` |
| `maybeFoo: Option[T]` | an optional child element, tag derived by lowercasing `Foo`'s first letter (i.e. `foo`) — with a fallback to the literal `Foo` form if that's not found (some real KML tags, like `ExtendedData`, are capitalized compounds that don't fit the usual lowerCamelCase convention) |
| `Foos: Seq[T]` (capitalized, plural-looking) | a sequence of child elements — see `Plural`/`extractChildren` for the literal-tag-first, type-based-fallback logic |

If a field's real tag doesn't derive from its name at all (its tag is always something else
entirely, or one of *several* possible tags depending on which concrete subtype shows up), don't
fight the naming convention — see `TagProperties` below.

### Derivation

Each case class's `Extractor`/`Renderer` is derived at compile time:

```scala
case class Altitude($: Double) derives Extractor, Renderer
case class Placemark(Geometry: Seq[Geometry])(val featureData: FeatureData) extends Feature derives Extractor, Renderer
```

(or `given Extractor[T] = Extractor.derived` where `derives` doesn't fit). `Extractor.derived` reads each field according
to its name, as above: a field whose type is a `Seq` with `extractChildren` (using a `MultiExtractor`), any other field
with `fieldExtractor` (using an `Extractor`). `Renderer.derived` renders exactly as the old `rendererN`/`rendererNSuper`
methods did. For each field, a given instance is used if there is one (e.g. `Coordinates`' own `MultiExtractor`, which
requires at least one element, or `InnerBoundaryIs`' named sequence renderer); otherwise an `Option[X]` or `Seq[X]` field
lifts `X`'s instance, and a case class with no instance of its own is derived in turn.

The companions of `Extractor`, `MultiExtractor` and `Renderer` also provide the `Option[T]`/`Seq[T]` instances for any `T`
that has an instance (`optionExtractor`, `seqMultiExtractor`, `optionRenderer`, `seqRenderer`), and `Renderer`'s companion
forwards the standard renderers of `Renderers` (`Double`, `Text`, ...), so derived instances need no imports. A more
specific instance (e.g. one in the element type's own companion) always wins over these.

**Why a macro, not a `Mirror`.** Scala 3 synthesizes no `Mirror` for a case class with a second parameter list, and so
none for a sealed trait with such a subtype. The auxiliary-parameter pattern (below) is everywhere in `kml`, so the
derivation (`core`'s `Derivation`, `DerivedExtractors`, `DerivedRenderers`) is a small macro over the primary
constructor instead: the names and types of the first parameter list, and the one parameter of the second, if any.
The resulting instances are ordinary classes (`ProductExtractor`, `ProductRenderer`, `SumMultiExtractor`, `SumRenderer`).
NOTE: don't call these macros from `core`'s own main sources: a file that expands a macro defined in the same project
is compiled in a second ("suspended") run, which can drop some warnings (so `-Werror` would behave inconsistently).
`Text`'s instances, in `core`, are therefore built from `ProductExtractor`/`ProductRenderer` directly.

One difference from the ladder: when a type with an auxiliary parameter has an attribute as its first field
(e.g. `Square(_side: Double)(val meta: Meta)`), `rendererNSuper` silently dropped the auxiliary content; the derived
renderer doesn't. No `kml` type has that shape, so `kml`'s output is unchanged.

**The old arity ladder** (`extractor10` ... `extractorPartial72`, `multiExtractorN`, `rendererN`, `rendererNSuper`,
`rendererSuperN`, `renderer1Special`) is deprecated since 1.2.0 and will be removed in a later release. It built each
instance from one combinator per field, chosen by the count and kind of fields, with the field names found by
reflection (`Reflection.extractFieldNames`). Derivation has no limit on the number of fields.

### Polymorphic dispatch: `MultiExtractor`

Many KML elements are really abstractions — `Feature`, `Geometry`, `StyleSelector`, `AbstractView` — where a
`Seq[Feature]` in the XML is actually a mix of concrete subtypes (`Placemark`, `Folder`, `Document`, ...), each
identified by its own tag. These traits are `sealed`, and their companions derive both sides:

```scala
object Feature {
  implicit val extractorSeq: MultiExtractor[Seq[Feature]] = MultiExtractor.derived
  implicit val renderer: Renderer[Feature] = Renderer.derived
}
```

`MultiExtractor.derived` finds the case classes which extend the trait (flattening sealed sub-traits such as
`Container` and `Overlay`, and the sealed abstract class `BaseOverlay`), uses each one's class name as its tag, and
extracts each node of the `NodeSeq` whose label is one of those tags with that subtype's `Extractor`, in a single pass,
preserving document order (nodes with other labels are ignored). `Renderer.derived` for a sealed trait renders each value
with the `Renderer` of its own subtype. To add a subtype, just declare it in `KML.scala` (a sealed trait's subtypes must
be in the same file): nothing needs to be listed.

**Circular dependencies are common and expected** here — e.g. `Geometry`'s own `MultiExtractor` needs `MultiGeometry`
(one of `Geometry`'s subtypes), and `MultiGeometry` itself contains `Seq[Geometry]`. Derived instances look up the
instances they depend on lazily, the first time they're used, by which point every object is constructed; so no
special care is needed. (Hand-written instances still need `MultiExtractor.createLazy` / `Renderer.createLazy`, a
by-name wrapper: an eager `val` NPEs, and a naive `lazy val` can deadlock.)

### The auxiliary-parameter pattern

Most KML elements share a chunk of common data with their siblings — every `Feature` has a `name`,
`maybeVisibility`, etc. (`FeatureData`); every `Geometry` has `maybeExtrude`/`maybeAltitudeMode`
(`GeometryData`). Rather than duplicating those fields on every subtype, the subtype's case class
declares the shared data as an **auxiliary parameter in its own (second) parameter list**:

```scala
case class Placemark(Geometry: Seq[Geometry])(val featureData: FeatureData) extends Feature derives Extractor, Renderer
```

The derived `Extractor` extracts the aux (here `FeatureData`) from the same node, with its own `Extractor`; the derived
`Renderer` renders it first, inside the same element. (Note that a case class's equality ignores its second parameter
list.) A few types (`ContainerData`, `AbstractViewData`, `StyleSelectorData`, `SubStyleData`) are nothing but a wrapper
for another aux; they use `Extractor.embedded` / `Renderer.embedded`.

**A subtype must never redeclare one of its aux's own fields as its own field** — e.g. don't give `Model` its own
`maybeAltitudeMode`, since `GeometryData` already provides it to every `Geometry` subtype; doing so renders the same
value twice. This mistake is easy to make when copying an existing type as a template — double-check the aux's own
field list before adding a same-named field.

### Escape hatches for irregular real-world XML

Two mechanisms exist specifically for XML that doesn't fit the field-naming convention cleanly:

- **`TagProperties.addMustMatch(tag)`** — forces the *strict* (literal-tag-only, no type-based
  fallback) extraction path for a specific field name, registered in that type's own companion
  object. Needed when a field's generic structural fallback (`extractAll`) would otherwise
  wrongly match a *different*, structurally-identical sibling field (e.g. `InnerBoundaryIs` and
  `OuterBoundaryIs` are both just a `LinearRing` wrapper, distinguished only by tag — without
  `addMustMatch`, a `Polygon` with no `<innerBoundaryIs>` at all can spuriously "find" one by
  matching its own `<outerBoundaryIs>`'s `LinearRing` instead).
- **`TagProperties.addAliases(field, tags)`** — registers one or more alternate tag names to try,
  as a last resort, when a field's usual name-derived tag (and its capitalized fallback) both come
  up empty. Needed when a field's real tag *never* derives from its name at all — e.g.
  `maybeTimePrimitive`'s real tag is `TimeStamp` or `TimeSpan`, never literally "TimePrimitive".

Both are registered once, in the owning type's own companion object, so they take effect
automatically the first time anything touches that type's extractor — callers never need to opt
in explicitly.

### `GenericElement`: the generic fallback

`GenericElement(tag, attributes, children)` (`core`'s `xml` package) is a fully generic,
lossless representation of an XML element — used two different ways:

1. **As the *entire* content model for a format** (`idml`'s current approach): parse everything
   into `GenericElement` first, then add specialist types (like `idml`'s `Story`) incrementally,
   each with its own `fromGenericElement`/`toGenericElement` pair that peels out just the fields
   worth having real types for, leaving the rest generic. This avoids ever needing to model a
   whole spec up front just to read one part of it.
2. **As an ordinary field type within an otherwise fully-typed element** (`kml`'s approach for
   `TimePrimitive`): when a field is abstract and its concrete shape isn't worth modeling (e.g. its
   two possible real tags, `TimeStamp`/`TimeSpan`, would otherwise need their own case classes plus
   a `multiExtractor2`/`rendererSuper2` dispatch pair), just type the field as `Option[GenericElement]`
   instead. Its `Extractor`/`Renderer` instances make this a drop-in field type like any other.

`GenericElement`'s own `Renderer` is a *leaf* — it renders the value's own `tag`, ignoring
whatever field name it's nested under, so there's no double-tagging risk regardless of what the
containing field happens to be called.

## Testing philosophy: round-trip against real files

The single most valuable test in either `kml` or `idml` is: parse a **real** third-party document,
render it back out, re-parse *that* with `scala.xml.XML.loadString` (a genuine parse — not
`Utilities.parseUnparsed`, which just wraps its argument as literal unparsed text and never
actually re-parses anything, and silently made every round-trip test that used it pass regardless
of correctness), and assert the re-extracted value equals the original extracted value.

This methodology found essentially every real bug fixed in this codebase across every session so
far — ordering bugs, phantom-match bugs, missing-field crashes, mis-cased tags, attribute-vs-element
rendering confusion — none of which a hand-written synthetic test happened to exercise. When adding
a new element type, prefer testing it against a real file over inventing one, and when a synthetic
test is unavoidable, keep the assertions genuinely round-trip (`extract; render; re-parse; compare
equal`), not just "did it not throw."

## Adding a new element type: a checklist

Distilled from adding `Model`/`Link`/`NetworkLink`/`ExtendedData`/etc. in `kml`:

1. Define the `case class`, with fields following the naming conventions above. Check whether it
   needs an auxiliary parameter (is it a subtype of an existing abstraction like `Feature` or
   `Geometry`?) and, if so, that it doesn't redeclare any of that aux's own fields.
2. Add `derives Extractor, Renderer`. Write a companion object only for what derivation can't infer: a
   `TagProperties` registration, or an instance that differs from the default (e.g. a `MultiExtractor`
   requiring at least one element, or a sequence rendered under a fixed name).
3. If it's a new concrete subtype of an existing (sealed) abstraction (`Feature`, `Geometry`, ...), declare it
   in `KML.scala`: the abstraction's derived `MultiExtractor`/`Renderer` pick it up, with its class name as its tag.
4. Watch for the two most common per-field surprises: a bare `Double` field used as a plain child
   element (the shared `doubleRenderer` always renders attribute-shaped text — wrap it in its own
   dedicated single-field class, matching `Rotation`/`Range`/`Heading`/etc.), and a field name that
   happens to look plural but is actually singular (`Plural.unapply`'s `singularEndsInS` exception
   list; add your case there rather than renaming the field away from its real tag). Note also that a
   plural field (`Pairs: Seq[Pair]`) requires at least one child unless its tag is registered with
   `TagProperties.addMustMatch`.
5. Test it against a real file if one exists (or is easy to construct) before trusting a synthetic
   one — see "Testing philosophy" above.
