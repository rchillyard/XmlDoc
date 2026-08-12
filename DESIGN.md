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
```

`core` has no knowledge of KML or IDML; everything format-specific lives in `kml`/`idml`.

## The `Extractor[T]` / `Renderer[T]` framework

### The type classes

```scala
trait Extractor[T] { def extract(node: scala.xml.Node): Try[T] }
trait Renderer[T]   { def render(t: T, format: Format, state: StateR): Try[String] }
```

Both are ordinary type classes with an implicit instance per type, resolved by the compiler the
usual way. `MultiExtractor[T]` is the analogous type class for extracting a `Seq[T]` out of a
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

### The arity ladder

Each case class's `Extractor`/`Renderer` is built by combining one combinator per field, using a
family of methods named by the count and kind of their fields:

- `extractorN0` / `rendererN` — `N` single-valued (`Extractor`/`Renderer`) fields, no `MultiExtractor` fields, no auxiliary parameter.
- `extractorNM` / `rendererN` — `N` single-valued fields plus `M` `MultiExtractor`(`Seq[_]`)-valued fields (e.g. `extractorPartial62` = 6 single + 2 `Seq`).
- `extractorPartialNM` / `rendererNSuper` — as above, plus one *auxiliary* parameter in the case class's second parameter list (see "The auxiliary-parameter pattern" below).

These are generated per-arity rather than via a single generic (e.g. shapeless-style) mechanism —
deliberately: it's more boilerplate, but every step is visible, debuggable, and doesn't require a
macro or a dependency to understand. The ladder currently goes up to arity 9 (`FeatureData`, the
widest case class in `kml`, needed it) — extending it further is mechanical: add one more
`extractorPartialN`/`renderer N`/`FP.uncurried` overload, each modeled exactly on the one below it
in the file. Don't reach for a novel approach when one more rung of the existing ladder is needed;
every arity increase so far has been "copy the previous one, bump the number."

### Polymorphic dispatch: `MultiExtractor`

Many KML elements are really *sealed-trait-like* abstractions — `Feature`, `Geometry`,
`StyleSelector`, `AbstractView` — where a `Seq[Feature]` in the XML is actually a mix of concrete
subtypes (`Placemark`, `Folder`, `Document`, ...), each identified by its own tag. `multiExtractorN`
builds a `MultiExtractor[Seq[T]]` for exactly this case: given `N` subtypes and their tags, it
builds a `{tag -> Extractor}` lookup table and applies it once over the `NodeSeq`, preserving
document order (`Extractors.orderedMultiExtractor` is the shared implementation underneath every
`multiExtractorN`). `rendererSuperN` is the render-side counterpart, dispatching on the runtime
type via `instanceOf` checks.

**Circular dependencies are common and expected** here — e.g. `Geometry`'s own `MultiExtractor`
needs `MultiGeometry` (one of `Geometry`'s subtypes), and `MultiGeometry` itself contains
`Seq[Geometry]`. An eager `val` for either side NPEs (whichever initializes second sees the other
as `null`); a naive `lazy val` deadlocks (Scala's reentrant lazy-val locking blocks a thread on
itself if the two both happen to be forced on the same thread during their own initialization). The
fix used everywhere in this codebase is `MultiExtractor.createLazy` / `Renderer.createLazy` — a
by-name, deferred wrapper that breaks the initialization cycle. If you add a new mutually-recursive
pair of types, use the same pattern; don't reinvent it.

### The auxiliary-parameter pattern

Most KML elements share a chunk of common data with their siblings — every `Feature` has a `name`,
`maybeVisibility`, etc. (`FeatureData`); every `Geometry` has `maybeExtrude`/`maybeAltitudeMode`
(`GeometryData`). Rather than duplicating those fields on every subtype, the subtype's case class
declares the shared data as an **auxiliary parameter in its own (second) parameter list**:

```scala
case class Placemark(Geometry: Seq[Geometry])(val featureData: FeatureData) extends Feature
```

`extractorPartial[B, T](extractorPartialNM(apply))` extracts the aux `B` (here `FeatureData`)
first via its own `Extractor[B]`, then applies the resulting `B => T` function; `rendererNSuper`
is the render-side counterpart. **A subtype must never redeclare one of its aux's own fields as
its own field** — e.g. don't give `Model` its own `maybeAltitudeMode`, since `GeometryData`
already provides it to every `Geometry` subtype; doing so renders the same value twice. This
mistake is easy to make when copying an existing type as a template — double-check the aux's own
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
2. In its companion object, wire up `extractor`/`renderer` using the smallest combinator that
   matches its field shape (count of single-valued fields, count of `Seq`-valued fields, aux or
   not) — if the exact arity/shape doesn't exist yet, extend the ladder by one rung rather than
   inventing something new.
3. If it's a new concrete subtype of an existing polymorphic abstraction (`Feature`, `Geometry`,
   ...), add it to that abstraction's `multiExtractorN`/`rendererSuperN` call — bump `N` by one and
   add its tag to the label list, in document order.
4. Watch for the two most common per-field surprises: a bare `Double` field used as a plain child
   element (the shared `doubleRenderer` always renders attribute-shaped text — wrap it in its own
   dedicated single-field class, matching `Rotation`/`Range`/`Heading`/etc.), and a field name that
   happens to look plural but is actually singular (`Plural.unapply`'s `singularEndsInS` exception
   list; add your case there rather than renaming the field away from its real tag).
5. Test it against a real file if one exists (or is easy to construct) before trusting a synthetic
   one — see "Testing philosophy" above.
