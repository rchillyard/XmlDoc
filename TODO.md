# TODO: open, extensible dispatch for unmodeled/future element types

Design discussion from 2026-08-12, not yet implemented. Recorded here so the reasoning survives
until it's actually built. See [DESIGN.md](DESIGN.md) for how the current (closed) dispatch works.

## Motivation

Two related problems, both stemming from the same root cause:

1. **Issue #52**: Google's `gx:` namespace adds several elements (`gx:Track`,
   `gx:MultiTrack`, `gx:Tour`, `gx:LatLonQuad`, ...) that aren't worth fully modeling as their own
   case classes (see the #29 discussion — no real file to verify a specialist implementation
   against, so `GenericElement` is the right default treatment for now).
2. **The general case**: KML (or any format) keeps evolving, and a consumer of this library will
   always encounter *some* element XmlDoc doesn't model yet. Today, an unrecognized tag inside a
   polymorphic `Seq[Feature]`/`Seq[Geometry]` is silently dropped — `orderedMultiExtractor`'s
   lookup table just filters it out (see the `KmlSamplesFuncSpec` test titled "...silently skip
   the unsupported Schema element"). That's lossy, and a library consumer has no way to intervene
   short of forking the library.

The fix for both is the same: make "not one of the built-in subtypes" a **first-class, non-lossy
outcome** (fall back to `GenericElement`, don't drop the data), and make that fallback **overridable**
by a consumer who wants a real, custom typed extractor/renderer for a specific tag instead — without
forking XmlDoc.

## Design

### 1. A catch-all wrapper per polymorphic hierarchy

Each closed, sealed-trait-like abstraction (`Feature`, `Geometry`, `StyleSelector`,
`AbstractView`, ...) gets one additional member wrapping a `GenericElement`, e.g.:

```scala
case class UnknownFeature(element: GenericElement) extends Feature
case class UnknownGeometry(element: GenericElement) extends Geometry
```

This is what an unrecognized tag becomes, instead of vanishing. **Behavior change, on purpose**:
double check `KmlSamplesFuncSpec`'s "silently skip the unsupported Schema element" test (and any
other test relying on today's silent-drop) still makes sense once "unknown" means "captured
generically," not "gone."

### 2. An open registry for extraction

Generalizes the `TagProperties` pattern (already used for field-level tag aliases/must-match) up
to whole-subtype dispatch: a mutable `tag -> Extractor[T]` registry, consulted for any tag not in
the built-in compile-time list, checked *before* falling back to `UnknownFeature`/`UnknownGeometry`.
A consumer registers their own `Extractor[GxTrack]` (or whatever) once, and it takes priority over
the generic fallback for that tag — no fork required.

### 3. An open registry for rendering — this is the harder half

Extraction dispatch is already just a runtime lookup by tag string, so opening it is
straightforward. Rendering (`rendererSuperN`) is fundamentally different today: it dispatches via
a **fixed, compile-time list of type parameters**, each checked with `instanceOf` — e.g.
`rendererSuper6[Feature, Placemark, Container, GroundOverlay, PhotoOverlay, ScreenOverlay,
NetworkLink]`. A type registered at runtime can't slot into that list. This needs a genuinely
different, class-keyed runtime lookup (`Map[Class[_], Renderer[_]]`), tried *after* the fixed
cases and the `UnknownFeature`/`UnknownGeometry` case.

## Recommended sequencing: do Issue #45 first

Issue #45 (build a real `scala.xml.Elem` tree during rendering, instead of assembling text
character-by-character) isn't required for the above, but it's the right thing to do *first*,
because it substantially simplifies exactly the code the open-registry design has to touch:

- Today's `StateR`/`Format.formatName`/`doNestedRender` machinery exists purely because rendering
  hand-assembles indented text. If every `Renderer[T]` instead produced a `scala.xml.Elem`/
  `NodeSeq`, combining a fixed-dispatch case with an open-registry fallback becomes "collect a
  `Seq[Elem]`," with `scala.xml`'s own tree composition and pretty-printer handling formatting —
  no bespoke padding/indent logic left to get wrong when adding a new fallback branch.
- Bigger structural win: `GenericElement`/`GenericText`/`GenericCData` currently exist as a
  hand-rolled tree *because* rendering works in strings — they're substantially reimplementing a
  slice of `scala.xml.Node`. With tree-based rendering, the "unknown element" fallback might not
  need `GenericElement` for the render direction at all — just hold onto the originally-parsed
  `scala.xml.Node` and splice it back into the output tree unchanged. That removes a whole layer
  of duplicated modeling, not just some plumbing.

So: #45 doesn't solve the open-dispatch design by itself, but the open-registry work should land
on top of it, not underneath it.

## Suggested order of work

1. Issue #45: rendering produces `scala.xml.Elem`/`NodeSeq` instead of `String`.
2. `UnknownFeature`/`UnknownGeometry` (and siblings for other hierarchies as needed) — verify
   against real files, and re-check the "silently skip" test's assumptions.
3. Open extraction registry (tag -> `Extractor[T]`), generalizing `TagProperties`.
4. Open render registry (`Class[_] -> Renderer[T]`), now straightforward given step 1.
5. Only then: implement `gx:Track`/`gx:MultiTrack`/`gx:Tour`/`gx:LatLonQuad` support as the first
   real consumer of this mechanism — generic by default, overridable by anyone who wants a real
   typed `gx:Track`.
