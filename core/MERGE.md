# The generic merge layer (`com.phasmidsoftware.xmldoc.merge`)

This documents `core`'s `com.phasmidsoftware.xmldoc.merge` package: a 3-way merge over any
`GenericElement` tree, with no assumption of a stable per-node identity attribute (though it uses
one - IDML's `Self` - directly when a caller has it). It grew out of, and was originally documented
alongside, `idml`'s own 3-way merge research - split into its own file on 2026-09-20 once it became
clear this layer had grown into something genuinely independent of IDML, reusable by `kml` or any
other `GenericElement`-based document type.

See [idml/MERGE.md](../idml/MERGE.md) for the IDML-specific half of this story: empirical `Self`
stability findings, Kaining's conflict-condition catalogue, `EditDetector`/`Merger`/`ThreeWayMerger`,
and the eventual git-merge-driver plan. Written in the same research-log shape as that file, in the
order things were actually found and built; "Kaining's `NN`" case-number citations throughout refer
to that file's own catalogue - useful shorthand for structural patterns even here, since none of
this code actually cares whether the tree came from IDML.

## Why tree/DAG-based

The problem is fundamentally structural, not textual: reconciling independent edits to a document
*tree* against a common ancestor - insertions, deletions, moves, and content changes to whole
subtrees, not line-based text diffing. `GenericElement` (tag, attributes, ordered children) is
already a fully generic tree representation for any XML-derived document (`core`'s `xml` package),
so "3-way merge two document versions" reduces to "3-way merge two `GenericElement` trees."

## Prior art: Lindholm's 3DM ("A Three-way Merge for XML Documents", 2004)

The most directly relevant piece of prior work - a full copy is at `doc/Lindholm.pdf` in this repo.

**The formal model.** Trees are expressed as a set of `pcs(r, p, s)` relations ("s immediately
follows p in r's child list") plus a separate `c(n, content)` relation for each node's content.
Changes are expressed using the *same* relations as the tree itself - a changed tree is just a
different set of these tuples. This is a genuinely nice representation: a single insert/delete/move
touches only one or two relation triples, not a whole child-list rewrite. Adopted here (`Pcs.scala`,
below) rather than inventing something from scratch.

**The paper's biggest caveat, and the one that matters most here**: Lindholm explicitly does *not*
solve tree matching. The merge algorithm assumes a matching relation between nodes of different tree
versions is handed to it as input, and says outright that constructing this matching accurately
(especially without unique identifiers) is "of large practical importance" but out of scope for the
paper. Some document types have a candidate stable identifier the general XML case doesn't - IDML's
`Self` chief among them (see [idml/MERGE.md](../idml/MERGE.md)) - so a caller with one of those can
do better than the generic content/structural matching this layer falls back to (`ContentMatcher`,
below) when there isn't one.

**Merge rules** (derived from 37 hand-crafted use cases, 35 handled successfully): the two most
important ideas are *node context* (a node's parent + predecessor + successor, used to express
*where* it belongs) and *guards* (certain node contexts must be preserved specifically to prevent
merging two changes that are structurally "too close" to safely assume independent - e.g. `abcd`
vs `bacd` vs `abdc` reorderings of the same short list are conflicts, not silently combined). Moves
are *relative*: reordering paragraphs inside a section is preserved even if the whole section also
moved elsewhere.

**Conflict taxonomy**: Update/Update (both sides changed the same node's content differently) and
Position/Position (both sides positioned things incompatibly) are hard conflicts; Delete/Edit (one
side deletes a subtree the other edited inside) is optional - the basic merge discards it by default
unless you explicitly check for edits inside deleted subtrees.

## The actual 3dm implementation's matcher

Read directly (`HeuristicMatching.java`, `Measure.java`, via the unofficial mirror,
`github.com/Mikulas/3dm-mirror`, since the original project site is dead). One important finding
that isn't in the paper: **the real tool's matcher uses no identifiers at all** - there's an
`IdIndex` interface in the codebase, but it's used only to serialize an *already-computed* diff
compactly, not for matching. The actual matching pipeline is pure content/structure similarity:

1. Exact-content matching first (DFS scan for identical nodes, then greedily extend the match
   downward through identical children - one whole identical subtree matches in one shot).
2. Fuzzy fallback via a **q-gram distance** (Ukkonen 1992) on text/attribute values, plus a
   child-list similarity computed by hashing each child's content and taking string-edit-distance
   between the hash sequences.
3. A positional gap-filling pass for anything still unmatched (first/last child heuristics, and
   "if my matched neighbor's base-match has an unmatched adjacent sibling, match to that").
4. Copy resolution when one base node matches several branch nodes (pick the best "master" copy,
   discard small/ambiguous ones below a size threshold).

`ContentMatcher` (below) is a deliberately smaller version of steps 1 and 3 - exact-content matching,
then plain positional pairing for the remainder, no q-gram fuzzy fallback and no copy resolution.
A caller with a real stable identifier (IDML's `Self`) should prefer matching by that directly
instead (`TreeMatcher.matchBySelf` - also in this package, though its literal `"Self"` attribute
lookup is an IDML convention, not a generic one) and fall back to `ContentMatcher` only for whatever
has none.

## PCS relation utilities (built 2026-09-18)

There is an illustrated walkthrough of the Lindholm paper, `doc/Three-way_XML_Merge.html` (generated
from the paper, not authoritative for its exact statements/formulas - `doc/Lindholm.pdf` still is),
section "§2.3 Ordered trees & PCS" in particular. It's what prompted actually building the
representation, in `Pcs.scala`:

- `Sibling` (`SiblingNode(label)` / `ListStart` / `ListEnd`) stands in for Lindholm's `⊣`/`⊢`
  boundary markers, so every real child has both a predecessor and a successor even at either end
  of the list.
- `Pcs(parent, predecessor, successor)` is his `pcs(r, p, s)`; `Content(label, tag, attributes)` is
  his `c(n, content)` - collapsed to the whole `(tag, attributes)` pair, since attribute-by-attribute
  detail is `idml`'s own `EditDetector`'s job, not this relation's. (`Content` as originally built
  here; it later grew `text`/`textIsCData` too - see "Fixed: text-node blindness" below.)
- `Pcs.relations(root): RelationSet` decomposes a whole tree into these two relation sets - the
  "T\* expressed as a set" step his algorithm's pseudocode starts from.
- **The one real design decision**: what identifies a node that has no stable identity attribute at
  all (confirmed by direct inspection of real IDML fixtures to be `Properties`, the
  `PathGeometry`/`GeometryPathType`/`PathPointArray` chain, and `ParagraphStyleRange`/
  `CharacterStyleRange`/`Content` inside a `Story` - never the page items themselves, which reliably
  carry `Self`; real KML fixtures never populate any content element's identity attribute at all -
  see "First test of the generic layer outside idml" below). `Pcs.label` falls back to the node's
  `NodePath` rather than inventing a new synthetic-ID scheme. A path-labelled node only ever matches
  itself, within one tree, never a node from another tree - that's a known limitation, not a defect,
  addressed later by `ContentMatcher` (below) for exactly the cases where it matters.

Confirmed useful in `PcsSpec`: two trees where a parent's two children swap places, with neither
child's own attributes touched, produce *identical* `idml`'s `EditDetector.detectEdits` output (`Nil`
- attribute-only diffing is blind to reordering) but *different* `Pcs.relations(...).pcs` sets - the
concrete gap "Structural moves / z-order" (idml/MERGE.md's Kaining cross-check) had been describing
in the abstract.

**Built next (2026-09-18): the edit-set difference.** `PcsEditDetector.detectEdits(base, modified):
RelationSet` is exactly Lindholm's `E = T* - T*0` - the relations in `modified`'s set but not
`base`'s. Genuinely simpler than `idml`'s `EditDetector`: it doesn't classify *what kind* of edit
occurred (insert/delete/update/move), it just reports the raw relations a structural merger would
need to reconcile - `PcsEditDetectorSpec` checks that a pure reorder produces only `Pcs` edits, a
pure attribute change produces only a `Content` edit, and an insertion produces both (the new node's
own `Content` plus the `Pcs` links that splice it into its parent's chain).

**Built next (2026-09-19): the structural merger.** `PcsMerger.merge(base, left, right):
StructuralMergeResult` is the reconciliation half of §6's pseudocode: it unions `base`'s relations
with both sides' `PcsEditDetector` edits and resolves each contested "slot" (which content a label
has; which successor follows a given predecessor under a given parent; which predecessor precedes a
given successor) the same way for all three - untouched-by-both stands, one side's edit wins if the
other didn't touch it, agreeing edits win, and disagreeing edits are reported as a `StructuralConflict`
rather than guessed at. `PcsMergerSpec` checks a clean one-sided reorder and insertion apply with no
conflicts, an Insert/Insert `Self` collision with differing content conflicts (agreeing content
doesn't), and - the sharpest test - Kaining's `05-move-move-divergent` (`u1` moved to the end by one
side, to the middle by the other) produces exactly the two conflicts the "unique successor" and
"unique predecessor" rules each independently detect, with the exact base/left/right values hand-
derived and confirmed against the implementation. Also re-run against the real `Mergeable-Left`/
`-Right` trio (`idml`'s own fixtures): it independently flags the same `u11c` Insert/Insert collision
`idml`'s `MergerSpec` already catches at the attribute level - a useful cross-check that the two
mergers agree where their scopes overlap.

**Built next (2026-09-19): tree reconstruction.** `PcsTreeBuilder.build` is the other half of the
sentence in §6 the pseudocode itself doesn't spell out: "Tm can be reconstructed by traversing from
⊥0 level by level along the PCS relations in Δ." It walks a `RelationSet` from a given root label,
following each parent's `Pcs` chain from `ListStart` to `ListEnd` and looking up each label's
`Content`, to rebuild an actual `GenericElement`. `StructuralMergeResult` now carries the merge's
own root label (`base`/`left`/`right`'s shared root, since they're assumed to already correspond),
so `PcsTreeBuilder.build(result: StructuralMergeResult)` needs nothing else.

Two things worth noting:

- **`build(result: StructuralMergeResult)` refuses outright whenever `result.conflicts` is
  non-empty**, rather than relying only on the lower-level `build(relations, rootLabel)`'s own
  cycle/missing-content detection - see "the unique-parent rule" below for why that detection alone
  turned out not to be a complete safety net. The lower-level overload still exists and still does
  its own opportunistic checking (useful on its own, e.g. for the round-trip tests below, which
  don't go through `PcsMerger`/`StructuralMergeResult` at all): `05-move-move-divergent`, walked
  through by hand, resolves to `u1`'s successor still pointing at `u2` (the stale base value, since
  that slot conflicted) while `u2`'s successor now points back at `u1` (one side's winning edit) - a
  genuine 2-cycle, which it catches; so does a missing-content case, from an Insert/Insert content
  conflict.
- ~~**It's lossy in the same way the rest of this layer already is**: `Pcs.relations` never captured
  `GenericText`/`GenericCData` children, so a rebuilt tree never has any text content, even where the
  originals did~~ - true when this was written (confirmed at the time against a real file by
  comparing to a text-node-stripped copy of the expected tree), no longer true: see "Fixed:
  text-node blindness" below. A text-only leaf's text (and whether it was CDATA) now survives.

Round-tripped successfully: a plain tree through `Pcs.relations` and back unchanged; a one-sided
reorder and a one-sided insertion, each exactly as the modifying side arranged it; both sides'
independent, non-conflicting changes combined into one tree; and the real `HelloWorld2` ->
`HelloWorld2D` insertion, end to end through `PcsMerger` and back into a real `GenericElement`.

**Built next (2026-09-19): the unique-parent rule.** The third of Lindholm's three structural
consistency rules, and the one `PcsMerger` originally deferred:
`parent(r,n) ∧ parent(r′,n) → r=r′` - a node claimed as a child by two genuinely different parents.
The "unique successor"/"unique predecessor" rules already built can't see this at all: they only
ever compare two values sharing the same `(parent, ...)` key, but a node moved to a different parent
changes *which key it appears under in the first place*, not the value at a shared one - so a
genuine reparenting conflict (Kaining's `10-group-ungroup`: one side ungroups `g1`, the other moves
a new sibling *into* `g1`) can sail straight through both existing checks with zero overlap between
the two sides' touched keys, and get silently, wrongly resolved.

`PcsMerger.parentOf(pcs): Map[String, String]` computes each label's parent from a relation set (a
label appearing as anyone's predecessor or successor tells you its `parent`); reconciling
`base`/`left`/`right`'s three parent-maps through the same `reconcile` helper the other two rules
already use catches exactly this. `PcsMergerSpec`'s `10-group-ungroup` case confirms both affected
nodes (the ungrouped one and the one moved into the still-assumed-present group) are flagged, with
the exact base/left/right parent labels hand-derived and confirmed against the implementation - and
that a genuine single-sided reparenting (no disagreement) still isn't mistaken for a conflict.

This is also what motivated hardening `PcsTreeBuilder.build(result)` (above): the group/ungroup case
doesn't produce a cycle or missing content anywhere - both sides' chains individually look perfectly
consistent - so nothing about the walk itself would ever have caught it. Checking `conflicts` up
front is a real fix, not a redundant belt-and-braces check.

With all three structural consistency rules built, the remaining named gaps for this structural
(`Pcs`-level) path itself are: thread-reference repair, story-text-level diffing, and cross-reference
conflicts (`ParentStory`, `NextTextFrame`) - all `idml`-specific, see
[idml/MERGE.md](../idml/MERGE.md)'s Kaining cross-check. (Insertion ordering and z-order both turned
out to already fall out of the same successor/predecessor mechanism, with no extra code needed - see
there too.)

`idml` went on to combine this layer's `PcsMerger`/`PcsTreeBuilder` with its own attribute-level
`Merger` into `ThreeWayMerger` (idml-specific, built around `Self`-matching - see
[idml/MERGE.md](../idml/MERGE.md)), and separately found a real, unrelated `LinkImportTime`
false-conflict issue wiring it up against real files - also idml-specific in its actual fix
(`EditDetector.defaultIgnoredAttributes`), though this layer's own `PcsEditDetector`/`PcsMerger`
gained the same `ignoredAttributes` mechanism generically at the same time, since `ThreeWayMerger`
needed to forward it to both mergers to avoid one of them still reporting the conflict the other was
told to ignore.

## First test of the generic layer outside idml: `kml` (2026-09-20)

`kml/src/test/scala/com/phasmidsoftware/xmldoc/kml/KmlMergeSpec.scala` - the first real check of the
"relocated to `core` so it's reusable" claim above. `kml` has no `GenericElement`-based tree of its
own (its `KML.scala` is a specialist `Extractor`/`Renderer` model throughout), so this bridges
straight from raw `scala.xml` parsing via `GenericElement.fromElem`, bypassing that model entirely -
the same trick `IdmlPackage` uses for `idml`. It works, mechanically: `Pcs.relations`/`PcsMerger`/
`PcsTreeBuilder` run against real and synthetic KML trees with zero code changes. But it surfaced two
limitations that `idml` had been quietly hiding:

- ~~**Text-node blindness is a footnote for `idml`, not for `kml`.**~~ **Fixed (2026-09-20)**, see
  below - `Pcs.relations` originally captured element children only, never `GenericText`, which is
  fine for IDML (everything of interest is in attributes) but not for KML, whose actual content
  (`<name>`, `<description>`, `<coordinates>`) lives almost entirely in element-wrapped *text*.
- ~~**No stable per-node identity, unlike IDML's `Self`.**~~ **Fixed (2026-09-20)**, see below. Real
  KML fixtures only ever populate `id` on `Style`/`StyleMap` (confirmed by direct inspection of
  `KML_Samples.kml` and others), never on content elements (`Placemark`, `Folder`, `Document`).
  `Pcs.label` falls back to `NodePath` everywhere here, so identity across `base`/`left`/`right` was
  purely positional. Disjoint edits at fixed positions (nothing inserted/deleted anywhere) still
  merged cleanly - but a genuinely damning case did not: left deletes the first of three
  `Placemark`s (shifting the other two one position earlier); right, independently, only edits the
  third's content. `PcsMerger.merge` reports **no conflict at all**, yet right's edit is **silently
  absent** from the merged result - a false *negative*, arguably worse than a false conflict, since
  nothing signals that anything went wrong. This isn't a `PcsMerger` bug; it's the predicted
  consequence of handing Lindholm's algorithm a weak (purely positional) matching relation for a
  document type with nothing like `Self` - exactly the gap "the actual 3dm implementation's matcher"
  section (content/structural heuristic matching, no identifiers needed) describes. See below.

## Fixed: text-node blindness (2026-09-20)

Robin's own framing of the ask: "I would like to be able to merge KML documents, or even any XML
documents." `Content` gained a fourth field, `text: Option[String]`, alongside `label`/`tag`/
`attributes`; `Pcs.leafText(e: GenericElement): Option[String]` computes it - the concatenated text
of every `GenericText`/`GenericCData` child, but *only* when `e.childElements.isEmpty` (no element
children of its own at all). `Pcs.relations` calls it when building each node's `Content`;
`PcsTreeBuilder` puts it back as a plain `GenericText` child when rebuilding. Nothing about the `Pcs`
chain itself changed - a text-only leaf still gets the same childless `ListStart`-to-`ListEnd` chain
it always did; the text rides along as part of its `Content`, not as a chain member of its own.

**Why "text-only leaf," not "capture every text node everywhere"**: the tempting alternative - walk
*all* children (elements and text alike) into the `Pcs` chain uniformly - was tried first, mentally,
and rejected before writing any code: every real file `GenericElement.fromNode` parses is full of
whitespace `GenericText` between sibling elements (indentation), so a *structural* container's own
list of children would suddenly include dozens of whitespace nodes as full chain participants,
turning every existing structural test's implicit assumptions (a `Spread`'s children are its page
items, full stop) into something that would need auditing node by node. Restricting capture to
`childElements.isEmpty` sidesteps this entirely: a structural container's chain is completely
unaffected (still only ever built from `childElements`, exactly as before), and only genuine leaf
elements - precisely the shape of `<name>`/`<description>`/`<coordinates>`, or (found while fixing
this) an IDML `Link`'s base64-encoded embedded-preview data - pick up a `text` value at all.

**What's still not covered, on purpose, for now**: genuinely *mixed* content - text interleaved with
element children at the same level (`<p>Hello <b>world</b></p>`) - isn't captured at all; rare in
both IDML and KML, not demonstrated as a real problem yet, so not built preemptively.

~~`GenericCData` collapses to plain `GenericText` on rebuild~~ **Fixed (2026-09-20)**: `Content`
gained `textIsCData: Boolean`, set by `Pcs.leafIsCData` (true only when *every* text/CDATA child of
a text-only leaf was itself CDATA - a genuine mix, or a single plain-text child, stays plain
`GenericText`, same acceptable-for-now simplification `GenericElement.fromNode` already applies to
comments and entity references). `PcsTreeBuilder` picks `GenericCData` vs `GenericText` on rebuild
accordingly. Found to matter immediately: the real `HelloWorld2`/`HelloWorld2D` round-trip test
(`idml`'s `PcsTreeBuilderIdmlSpec`) actually has a CDATA leaf (a `Link`'s base64-encoded
embedded-preview data, seen directly in a test failure's diff output) - its own comparison helper
needed updating to preserve CDATA-ness too, once the real rebuild started doing so correctly.

**Verified**: `PcsSpec`/`PcsEditDetectorSpec`/`PcsMergerSpec`/`PcsTreeBuilderSpec` (`core`) each
gained direct unit tests (leaf capture, edit detection, clean merge, conflict, and round-trip, all
using plain text content); `idml`'s `PcsTreeBuilderIdmlSpec`'s real `HelloWorld2`/`HelloWorld2D`
round-trip test needed its own comparison helper updated to match (leaf text now kept, not stripped,
while inter-element whitespace still is). `KmlMergeSpec` is the direct confirmation this was
actually built for: a `Placemark`'s real `<name>`/`<description>` text now survives `PcsMerger`/
`PcsTreeBuilder` intact, and the "disjoint edits merge cleanly" test now edits real description text
directly rather than standing in with attributes.

## Fixed: weak/no-identity documents silently losing edits (2026-09-20)

`ContentMatcher` (fully generic - not `idml`-specific despite the motivating case being IDML's
`Self`-less counterpart in `kml`): matches `base` against one modified tree using content and
structure alone, loosely modeled on the real 3dm tool's own matcher ("the actual 3dm implementation's
matcher", above) - exact whole-subtree content equality first (a node, and everything below it, byte
for byte, is almost certainly the same node even if it moved), then positional pairing for whatever's
left among an already-matched parent's remaining children, recursively. Deliberately simpler than
3dm's own algorithm: no fuzzy content similarity (q-gram distance) for the positional fallback - just
pairs whatever's left, in document order - and no real "copy resolution" for duplicate content. It
also never looks *across* an unmatched parent, so a node moved to a genuinely different parent still
shows up as a plain delete-plus-insert, not a recognized move.

`Pcs.relations`, `PcsEditDetector.detectEdits`, and `PcsMerger.merge` each gained an optional
labeling parameter (`labelOf`/`modifiedLabel`/`leftLabel`+`rightLabel`, all defaulting to the
ordinary `Pcs.label`, so every existing call site and test is completely unaffected) - a way to say
"treat this node as if it were the base node the matcher found for it," instead of always falling
back to raw position. `PcsMerger.mergeByContent(base, left, right)` is the convenience entry point:
matches `left` and `right` against `base` independently via `ContentMatcher`, then delegates to the
ordinary `merge` with the resulting labels.

**Confirmed fixed, using the exact scenario that found the bug**: `KmlMergeSpec`'s deletion-shifts-
positions case, unchanged, now merges cleanly *and keeps the edit* through `mergeByContent` - `B`
(untouched by either side) and `C` (right's real edit to it) both survive, where plain `merge` on the
same inputs still silently drops `C`'s edit (kept as its own explicit test, for contrast).
`ContentMatcherSpec` covers the matcher directly: matching an untouched tree to itself, matching an
untouched child by content despite a position shift, the positional fallback for a single changed
remaining candidate, `matchedLabel`'s label reuse and its `+`-prefixed fallback for genuinely new
nodes, and (see below) `ignoredAttributes`. `PcsMergerSpec`/`KmlMergeSpec` also confirm
`mergeByContent` still correctly reports a *genuine* conflict (both sides really did change the same
matched node differently) rather than papering over every disagreement - matching by content fixes
misattribution, it doesn't relax what counts as a conflict.

~~`ignoredAttributes` isn't threaded through `ContentMatcher`'s own exact-content comparison~~
**Fixed (2026-09-20)**: `matchTrees`/`matchedLabel` both gained the same `ignoredAttributes`
parameter (default empty), applied via a `normalized` step that strips those attribute names from
an element *and every element nested inside it*, recursively, before comparing for exact-content
equality - a volatile attribute could sit on any descendant, not just the node being compared.
`PcsMerger.mergeByContent` forwards the same set to both `ContentMatcher` and (as before)
`PcsEditDetector`. Confirmed with a test constructed so a single-remaining-candidate fallback
couldn't accidentally paper over the difference: two nodes reorder *and* both have their volatile
attribute bumped at the same time, so without ignoring it neither exact-matches and the positional
fallback pairs them in the wrong relative order (each gets the *other*'s identity); with it, both
exact-match by their real content regardless of the reorder.

**What's still not addressed, on purpose**: reparenting (a node moved to a genuinely different
parent) isn't recognized as a move - `ContentMatcher` only ever matches within an already-matched
parent's own children, never searches globally across the tree. Several simultaneous changes among
the same unmatched sibling group can still be misattributed to each other, same as 3dm's own fuzzy
fallback would only partially help with - the positional fallback here is deliberately simpler
(no content-similarity scoring at all), correct for exactly one remaining ambiguous candidate on
each side (the case that motivated this), weaker with more.

## Open questions specific to this layer

- Mixed content (text interleaved with element children at the same level) is still entirely
  uncaptured - see "Fixed: text-node blindness" above. Not demonstrated as a real problem in either
  IDML or KML yet.
- Reparenting recognition and multi-way ambiguous-remainder matching for `ContentMatcher` (see
  "Fixed: weak/no-identity documents" above) - both need real algorithmic work (a global search for
  the former, fuzzy content similarity along the lines of 3dm's own q-gram distance for the latter),
  not just parameter threading.
- Nothing yet consumes this layer's output end to end for a non-`idml` document type - `kml` has
  only ever been exercised with hand-built synthetic fixtures plus one real-file smoke test
  (`Pcs.relations` on `KML_Samples.kml`'s Placemarks folder), never a real `mergeByContent` 3-way
  merge against genuinely independently-edited real `.kml` files.
