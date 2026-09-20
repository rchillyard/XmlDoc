# Notes toward a 3-way merge for `idml`

Research and empirical findings from 2026-08-15, gathering what's needed before designing `idml`'s
eventual 3-way merge (Kaining's research project - see [DESIGN.md](../DESIGN.md)). Nothing here is
implemented yet; this is groundwork.

See the illustrated [3-way merge case atlas](docs/merge-case-atlas.md) for the ordered-tree,
cross-parent graph, reference, and text scenarios. The original interactive version is preserved
alongside it as [HTML](docs/merge-case-atlas.html).

## Test corpus

The deterministic base/left/right fixtures for the structural, text, z-order, grouping, and
cross-reference scenarios are documented in
[`src/test/resources/com/phasmidsoftware/xmldoc/idml/three-way-corpus/README.md`](src/test/resources/com/phasmidsoftware/xmldoc/idml/three-way-corpus/README.md).
Each case also contains a machine-readable `expected.json` and reproducible operation notes.

**Scope decision (2026-08-17)**: all three inputs to a merge (base + two independently edited
copies) are assumed to have been produced by the *same* InDesign version. Cross-version migration
(see the `Magazine-2` finding below) is real and worth remembering, but treating it as in-scope
would force the primary design to assume ID-based matching might be wholesale unusable - which
punishes the common case to guard against an edge case nobody's asked to solve. The finding is kept
below as a documented, deliberately out-of-scope caveat, not a design requirement.

## Why tree/DAG-based

The problem is fundamentally structural, not textual: reconciling independent edits to a document
*tree* against a common ancestor - insertions, deletions, moves, and content changes to whole
subtrees, not line-based text diffing. `GenericElement` (tag, attributes, ordered children) is
already `idml`'s own generic tree representation, so "3-way merge two document versions" reduces to
"3-way merge two `GenericElement` trees."

## Prior art: Lindholm's 3DM ("A Three-way Merge for XML Documents", 2004)

The most directly relevant piece of prior work - a full copy is at `doc/Lindholm.pdf` in this repo.

**The formal model.** Trees are expressed as a set of `pcs(r, p, s)` relations ("s immediately
follows p in r's child list") plus a separate `c(n, content)` relation for each node's content.
Changes are expressed using the *same* relations as the tree itself - a changed tree is just a
different set of these tuples. This is a genuinely nice representation: a single insert/delete/move
touches only one or two relation triples, not a whole child-list rewrite. Worth adopting (or
adapting) as `idml`'s own change-representation, once it needs one, rather than inventing something
from scratch - though translating `GenericElement`'s children list into/out of this form should be
mechanical.

**The paper's biggest caveat, and the one that matters most for us**: Lindholm explicitly does
*not* solve tree matching. The merge algorithm assumes a matching relation between nodes of
different tree versions is handed to it as input, and says outright that constructing this matching
accurately (especially without unique identifiers) is "of large practical importance" but out of
scope for the paper. **IDML has a candidate stable identifier that the general XML case doesn't**
(see below) - so this is exactly where `idml` may be able to do better than the generic baseline.

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

Since this generic tool has to work without any assumption of stable IDs, and IDML actually
provides one (`Self`), `idml`'s own matcher should be simpler and more reliable for anything that
carries a `Self` - falling back to 3dm-style heuristics only where nothing else is available (most
obviously, the plain text runs inside a `Story`, which have no `Self` of their own).

## Empirical findings: what `Self` actually looks like and how stable it is

Investigated directly against real files (`HelloWorld.idml`, `HelloWorld2.idml`, and several
edited variants), not just assumed from the format spec.

### Three different shapes of `Self`

1. **Auto-generated, per-document identity** - `u` followed by a short lowercase base-36-ish
   string (`u81`, `u97`, `ud1`, `ue6`, `uf8`, `u106`...`u13e`, etc.). Looks like one single,
   monotonically-increasing counter shared across *every* kind of object in the document - colors,
   gradients, spreads, stories, and page items are all just entries in the same ID space. This is
   the shape that matters for content matching, and what `Story.self` is built on.
2. **Compound/derived IDs for sub-components of a parent** - e.g. `u97GradientStop0`,
   `u18ColorGroupSwatch3`, `ua1BuildingBlock2`. Literally `<parent Self><ComponentType><index>`
   concatenated - stability here is entirely derivative (parent's `Self` + positional index), not
   independently meaningful.
3. **Named/predefined resource references** - `Color/Black`, `StrokeStyle/$ID/Solid`,
   `Language/$ID/English%3a USA` (note the URL-escaped colon). Fixed, built-in vocabulary present
   in essentially every InDesign document, not really "identity" at all. Custom user-created
   resources of this kind fall back to shape 1, nested under the resource type (`Color/u96`).

### Stability experiments

Using `HelloWorld2.idml` as a base, with two independently edited copies (moved a `Rectangle` in
one, restyled it - different attributes - in the other):

- **Untouched objects kept identical `Self` values** across both independent edits.
- **The edited object also kept its own `Self`** in both cases - one side changed `ItemTransform`
  (position), the other changed fill/stroke attributes. Same identity, different content changed -
  exactly what a mergeable identity should look like, and exactly the case a real 3-way merge
  should be able to combine without conflict.
- **Independent insertions did not collide** - two independently added new objects (from a common
  base) got distinct `Self` values (`u154` vs `u141`).
- **Correction, from the `Mergeable` trio below: independent insertions *can* collide after all.**
  `u154`/`u141` came from edits that weren't very similar to each other; when the two edits *are*
  similar (both sides drawing one new `Rectangle`, from the same freshly-reopened base file),
  InDesign's ID allocator handed out the *identical* `Self` (`u11c`) to both. This suggests ID
  allocation is deterministic given the starting document state and the sequence of allocating
  actions taken, not random or collision-resistant - so a genuine `Self` collision between two
  independent inserts is a real possibility whenever both sides make similar edits, not just a
  theoretical case `Merger.reconcileInserts` happened to be written defensively for. Confirmed by
  `MergerSpec`'s `Mergeable`-based test: `Merger.merge` correctly reports this as a `Conflict`
  (the two rectangles differ in `FillColor`), not a silent match.

### The one gotcha: live sessions can drift from the saved file

An initial round of this testing showed *all* the base document's original `Self` values shifted
(consistently, identically, across two supposedly-independent edited copies) - which looked
alarming until traced to the cause: those copies were made by continuing to work in an InDesign
window that had been open for a while, never actually closed and reopened from the saved file in
between. A copy made via a genuine close-then-reopen of the saved `.indd` preserved every original
`Self` value exactly, byte-for-byte. **Conclusion**: `Self` stability across a save/reopen cycle is
real and clean; the earlier apparent instability was an artifact of comparing against a stale
in-memory session rather than what was actually saved to disk. Any future experiment (or any
real usage advice for a merge tool) should insist on a genuine close/reopen between independent
edit branches, not a continuously-open session.

**Practical consequence, already hit once**: `A`, `B`, and `C` (used in `MergerSpec`'s real 3-way
test) all came from that same drifted session, so the plain `HelloWorld2.idml` doesn't actually
share their `Self` lineage and can't serve as their base - `C` is used instead, as the closest real
stand-in for the session's true (never-saved) starting point. See the comment on that test for the
detail. **Done**: `Mergeable.idml`/`Mergeable-Left.idml`/`Mergeable-Right.idml` are exactly this - a
base plus two edits, each made from an explicit close-then-reopen of that same saved file, verified
to share the same `Self` lineage with no drift at all (see `MergerSpec`'s Mergeable-based test).

**Follow-up: one candidate cause ruled out.** A separate document (`Mergeable.indd`, not related to
this codebase's `Mergeable` trait) prompted InDesign to "save" after nothing more than an open and
an IDML export, which raised the question of whether opening-and-exporting alone could be causing
drift. Tested directly: exported before saving (`Mergeable.idml`) and again after saving
(`Mergeable-2.idml`, kept as a fixture even though no test currently needs it) - **every single
`Self` value is byte-for-byte identical between the two**. The only difference anywhere in the two
IDML packages is `META-INF/metadata.xml`'s XMP bookkeeping (a fresh `ModifyDate`/`InstanceID`, and a
new history entry recording the save) - ordinary version-tracking metadata InDesign updates on
every save regardless of whether content changed, unrelated to document structure. So: opening,
exporting, and saving a document does *not*, on its own, cause `Self` drift. Whatever actually
caused `A`/`B`/`C`'s drift is something more specific to that particular session's history, still
unexplained.

### Real-world case: cross-version migration can invalidate `Self` correspondence entirely

Tested against Kaining's own research data (`Magazine.idml`, a stock sample file from Adobe's own
InDesign site, plus `Magazine-1.idml`/`Magazine-2.idml`, two independent edited variants Kaining
produced from it using the latest InDesign) - a much larger, realistic document (~14 spreads,
~210 stories) rather than the synthetic `HelloWorld2` cases above.

The base file's `designmap.xml` reports `DOMVersion="8.0"` and was created by "Adobe InDesign CS6
(Windows)"; both edited variants report `DOMVersion="21.5"`, "Adobe InDesign 21.5 (Macintosh)" -
opening a 13-version-old file in a modern InDesign triggers a one-time format migration. The two
variants diverged completely in how that migration affected `Self`:

- **`Magazine-1.idml`** shares the base's exact `MasterSpread`/`Spread` `Self` values, and most of
  its 209 Stories correspond directly to the base's 211 (a handful inserted, a few removed) -
  consistent with the same kind of clean "reopen, edit, save" behavior confirmed with `HelloWorld2`.
- **`Magazine-2.idml`** has a **completely disjoint `Self` space** from the base - different
  `MasterSpread`/`Spread` IDs, and none of its 204 Story IDs match the base's at all (different
  length and pattern entirely). Consistent with a full internal ID regeneration happening during
  that particular migration.

**Out of scope by the 2026-08-17 scoping decision above** (all merge inputs assumed same InDesign
version), but real and worth remembering: this is Kaining's actual research data behaving this way,
not a hypothetical. If cross-version migration ever becomes something we need to handle, revisit
this - whether `Self` correspondence survives seems to depend on migration/session details not yet
understood (see "the one gotcha" above, which may well be the same underlying phenomenon).

Note that this means the current three files don't actually give us a valid same-version test case
as-is: the base (`Magazine.idml`) is the CS6/DOMVersion-8.0 outlier, and *both* edited variants are
21.5 - so under the new scope decision, neither pairing is a fair test against this particular base.
`Magazine-1.idml` and `Magazine-2.idml` are, however, both 21.5 - so a genuine same-version test
case would need a fresh base derived from one of them (e.g. re-save `Magazine-1.idml` itself as a
new starting point, then produce two fresh independent edits from *that*), rather than reusing the
original CS6 file as the base.

## Proposed design direction (not yet built)

- **Match by `Self` first**, wherever present and of the "auto-generated per-document identity"
  shape (category 1 above) - given the same-InDesign-version scope decision above, this should be
  cheap and reliable for the large majority of structural content (page items, spreads, stories,
  master spreads).
- **Fall back to 3dm-style content/structural heuristic matching** for whatever has no `Self` of
  its own - primarily the text and inline formatting inside a `Story`. (Cross-version migration
  breaking `Self` wholesale, per the `Magazine-2` finding, is out of scope for now - see above - so
  this fallback doesn't need to be sized for "the whole tree lost its identity," just for
  genuinely un-identified content.)
- **Adopt Lindholm's vocabulary** (node context, guards, the Update/Update and Position/Position
  conflict categories) as the target semantics for whatever merge logic gets built, rather than
  inventing new terminology.
- Represent changes using something in the spirit of the PCS relation model, translated to/from
  `GenericElement`'s own tag/attributes/children shape. **Built** - see below.

## PCS relation utilities (built 2026-09-18)

**Relocated 2026-09-20**: this whole layer - `TreeMatcher`/`NodePath`/`NodeRef`, `Pcs`,
`PcsEditDetector`, `PcsMerger`, `PcsTreeBuilder` - turned out to depend on nothing idml-specific
except one hardcoded string (`NodeRef.self`'s literal lookup of the `"Self"` attribute), everything
else operating purely on `GenericElement`. It now lives in `core`, package
`com.phasmidsoftware.xmldoc.merge`, so it's available to `kml` or any future module too, not just
`idml`. Each class's synthetic-tree tests moved with it (`core/src/test/.../merge/*Spec.scala`);
whatever needed a real `.idml` fixture (or the idml-specific `EditDetector`) stayed behind as a
same-named `*IdmlSpec` in this module (e.g. `PcsMergerSpec` in `core` plus `PcsMergerIdmlSpec` here).
`Merger`/`EditDetector`/`ThreeWayMerger` themselves stay in `idml`, since they're genuinely built
around `Self`-matching, not generic. This document stays in `idml/` too - it's fundamentally the
narrative of *this* research project, even where the code it describes now lives elsewhere.

There is now an illustrated walkthrough of the Lindholm paper, `doc/Three-way_XML_Merge.html`
(generated from the paper, not authoritative for its exact statements/formulas - `doc/Lindholm.pdf`
still is), section "§2.3 Ordered trees & PCS" in particular. It's what prompted actually building
the representation, in `Pcs.scala`:

- `Sibling` (`SiblingNode(label)` / `ListStart` / `ListEnd`) stands in for Lindholm's `⊣`/`⊢`
  boundary markers, so every real child has both a predecessor and a successor even at either end
  of the list.
- `Pcs(parent, predecessor, successor)` is his `pcs(r, p, s)`; `Content(label, tag, attributes)` is
  his `c(n, content)` - collapsed to the whole `(tag, attributes)` pair, since attribute-by-attribute
  detail is `EditDetector`'s job, not this relation's.
- `Pcs.relations(root): RelationSet` decomposes a whole tree into these two relation sets - the
  "T\* expressed as a set" step his algorithm's pseudocode starts from.
- **The one real design decision**: what identifies a node that has no `Self` at all (confirmed by
  direct inspection of real fixtures to be `Properties`, the `PathGeometry`/`GeometryPathType`/
  `PathPointArray` chain, and `ParagraphStyleRange`/`CharacterStyleRange`/`Content` inside a
  `Story` - never the page items themselves: `Rectangle`, `TextFrame`, `Group`, `Page`, `Spread`,
  `Story` all reliably carry `Self`). `Pcs.label` falls back to the node's `NodePath` (already used
  for exactly this in `TreeMatcher`) rather than inventing a new synthetic-ID scheme. A path-labelled
  node only ever matches itself, within one tree, never a node from another tree - same gap
  `TreeMatcher`/`EditDetector` already have (they skip `Self`-less nodes outright); this at least
  makes the relation set total, per Lindholm's assumption that every node has *some* label, without
  pretending path-based identity is reliable across independent edits.

Confirmed useful in `PcsSpec`: two trees where a `Spread`'s two children swap places, with neither
child's own attributes touched, produce *identical* `EditDetector.detectEdits` output (`Nil` -
attribute-only diffing is blind to reordering) but *different* `Pcs.relations(...).pcs` sets - the
concrete gap "Structural moves / z-order" below has been describing in the abstract.

**Built next (2026-09-18): the edit-set difference.** `PcsEditDetector.detectEdits(base, modified):
RelationSet` is exactly Lindholm's `E = T* - T*0` - the relations in `modified`'s set but not
`base`'s. Genuinely simpler than `EditDetector`: it doesn't classify *what kind* of edit occurred
(insert/delete/update/move), it just reports the raw relations a structural merger would need to
reconcile - `PcsEditDetectorSpec` checks that a pure reorder produces only `Pcs` edits, a pure
attribute change produces only a `Content` edit, and an insertion produces both (the new node's own
`Content` plus the `Pcs` links that splice it into its parent's chain).

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
`-Right` trio: it independently flags the same `u11c` Insert/Insert collision `MergerSpec` already
catches at the attribute level - a useful cross-check that the two mergers agree where their scopes
overlap.

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
- **It's lossy in the same way the rest of this layer already is**: `Pcs.relations` never captured
  `GenericText`/`GenericCData` children (see `Pcs.scala`), so a rebuilt tree never has any text
  content, even where the originals did - confirmed against a real file by comparing to a
  text-node-stripped copy of the expected tree, not the raw original.

Round-tripped successfully: a plain tree through `Pcs.relations` and back unchanged; a one-sided
reorder and a one-sided insertion, each exactly as the modifying side arranged it; both sides'
independent, non-conflicting changes combined into one tree; and the real `HelloWorld2` ->
`HelloWorld2D` insertion, end to end through `PcsMerger` and back into a real `GenericElement`.

**Built next (2026-09-19): the unique-parent rule.** The third of Lindholm's three structural
consistency rules, and the one `PcsMerger` originally deferred:
`parent(r,n) ∧ parent(r′,n) → r=r′` - a node claimed as a child by two genuinely different parents. The "unique successor"/"unique predecessor" rules already built can't see this at all:
they only ever compare two values sharing the same `(parent, ...)` key, but a node moved to a
different parent changes *which key it appears under in the first place*, not the value at a shared
one - so a genuine reparenting conflict (Kaining's `10-group-ungroup`: one side ungroups `g1`, the
other moves a new sibling *into* `g1`) can sail straight through both existing checks with zero
overlap between the two sides' touched keys, and get silently, wrongly resolved.

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

With all three structural consistency rules built, the remaining named gaps for the structural
(`Pcs`-level) path itself are: thread-reference repair, story-text-level diffing, and cross-reference
conflicts (`ParentStory`, `NextTextFrame`) - see Kaining's cross-check below. (Insertion ordering and
z-order both turned out to already fall out of the same successor/predecessor mechanism, with no
extra code needed - see there too.)

## Built next (2026-09-20): wiring `Merger` and `PcsMerger` together - `ThreeWayMerger`

The two mergers had been developed and tested entirely in parallel up to this point - neither had
ever been run *together* against the same three trees. `ThreeWayMerger.merge` is the first thing in
this codebase that produces an actual, real, merged `GenericElement` combining both kinds of edit.

**The division of labor, and why it isn't arbitrary**: structure (which nodes exist, their order,
their parent) comes entirely from `PcsMerger`/`PcsTreeBuilder`; a matched node's own tag/attributes
come from `Merger` wherever `Merger` has an opinion (i.e. the node has a `Self`). This isn't just
"pick one merger per concern" for its own sake - `PcsMerger`'s own `Content` relation only ever
compares a node's *whole* tag+attributes as one indivisible blob (it has to; `Pcs` has no concept of
individual attributes at all), so on its own it cannot tell "both sides changed *different*
attributes of the same node" (safe to combine, per `Merger`) apart from "both sides changed the
*same* thing differently" (a genuine conflict) - it would (wrongly) flag the first as a conflict too.
`ThreeWayMergerSpec`'s first test confirms this directly: the `HelloWorld2A`/`HelloWorld2B`-style
disjoint-attribute-change case that `Merger`/`MergerSpec` already merge cleanly is confirmed to make
*plain* `PcsMerger` conflict on its own, then confirms `ThreeWayMerger` merges it cleanly anyway.

**Combining the conflict reports**: both mergers independently detect an Insert/Insert collision
with differing content (`Merger` via `reconcileInserts`'s whole-attribute-map comparison, `PcsMerger`
via its own whole-content-blob comparison) - reporting both would just be the same finding twice in
different words. Since a path label always starts with `/` (`NodePath.toString`) and no real `Self`
ever does, `ThreeWayMerger` keeps `PcsMerger`'s own content conflicts only for path-labelled
(`Self`-less) nodes - `Merger`'s finer-grained report always wins for anything with a real `Self`.
Confirmed against the real `Mergeable-Left`/`-Right` trio: exactly one conflict for `u11c`, not two.

**A real, unplanned finding from wiring this up against actual files - fixed 2026-09-20**: merging
the *whole* `HelloWorld2A`/`HelloWorld2B`/`HelloWorld2C` spread end to end did *not* come back clean
- `Merger` reported a genuine, unrelated `LinkImportTime` conflict on a Link (`u10d`) that both A and
B happened to touch independently. This was a real instance of the paper's own §7 caveat ("document
metadata often changes inconsistently on both sides"), not a bug - `MergerSpec`'s and
`PcsMergerSpec`'s own real-file tests never noticed it because each only ever inspected `u13d`'s own
outcomes, never asked "does the *whole* merge succeed?" See "volatile/auto-stamped attributes" below
for the fix, now built: `ThreeWayMergerSpec` confirms the whole real Spread merges cleanly with it in
place, and that the conflict reappears if a caller explicitly opts out (`ignoredAttributes =
Set.empty`) - so the fix is doing something, not just coincidentally passing.

Not yet done: nothing consumes `ThreeWayMerger.merge`'s `GenericElement` result end to end (writing
it back into a real `.idml` package, or the git-merge-driver integration below) - it's the merge
logic itself, tested down to real files, not yet a runnable tool.

## First test of the generic layer outside idml: `kml` (2026-09-20)

`kml/src/test/scala/com/phasmidsoftware/xmldoc/kml/KmlMergeSpec.scala` - the first real check of the
"relocated to `core` so it's reusable" claim from the previous section. `kml` has no
`GenericElement`-based tree of its own (its `KML.scala` is a specialist `Extractor`/`Renderer` model
throughout), so this bridges straight from raw `scala.xml` parsing via `GenericElement.fromElem`,
bypassing that model entirely - the same trick `IdmlPackage` uses for `idml`. It works, mechanically:
`Pcs.relations`/`PcsMerger`/`PcsTreeBuilder` run against real and synthetic KML trees with zero code
changes. But it surfaced two limitations that `idml` had been quietly hiding:

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
both IDML and KML, not demonstrated as a real problem yet, so not built preemptively. `GenericCData`
collapses to plain `GenericText` on rebuild, same kind of accepted gap `GenericElement.fromNode`
already documents for comments and entity references. Neither distinguishes a genuine `Self`-style
identity problem (still open, above) from this one - they're independent limitations that happened
to surface together.

**Verified**: `PcsSpec`/`PcsEditDetectorSpec`/`PcsMergerSpec`/`PcsTreeBuilderSpec` (`core`) each gained
direct unit tests (leaf capture, edit detection, clean merge, conflict, and round-trip, all using
plain text content); `PcsTreeBuilderIdmlSpec`'s real `HelloWorld2`/`HelloWorld2D` round-trip test
needed its own comparison helper updated to match (leaf text now kept, not stripped, while
inter-element whitespace still is). `KmlMergeSpec` is the direct confirmation this was actually
built for: a `Placemark`'s real `<name>`/`<description>` text now survives `PcsMerger`/
`PcsTreeBuilder` intact, and the "disjoint edits merge cleanly" test now edits real description text
directly rather than standing in with attributes.

## Fixed: weak/no-identity documents silently losing edits (2026-09-20)

`ContentMatcher` (`core`, fully generic - not `idml`-specific despite the motivating case being
IDML's `Self`-less counterpart in `kml`): matches `base` against one modified tree using content and
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
untouched child by content despite a position shift, and the positional fallback for a single
changed remaining candidate. `PcsMergerSpec`/`KmlMergeSpec` also confirm `mergeByContent` still
correctly reports a *genuine* conflict (both sides really did change the same matched node
differently) rather than papering over every disagreement - matching by content fixes misattribution,
it doesn't relax what counts as a conflict.

**What's still not addressed, on purpose**: reparenting (a node moved to a genuinely different
parent) isn't recognized as a move - `ContentMatcher` only ever matches within an already-matched
parent's own children, never searches globally across the tree. Several simultaneous changes among
the same unmatched sibling group can still be misattributed to each other, same as 3dm's own fuzzy
fallback would only partially help with - the positional fallback here is deliberately simpler
(no content-similarity scoring at all), correct for exactly one remaining ambiguous candidate on
each side (the case that motivated this), weaker with more. Neither the `EditDetector`/`Merger`
side of `ThreeWayMerger` nor the `ignoredAttributes` mechanism are threaded through
`ContentMatcher`'s own exact-content comparison yet - two subtrees differing only in a value that
would otherwise be ignored won't exact-match, though they may still recover via the positional
fallback.

## Kaining's 13 conflict conditions, cross-checked against what's built (2026-08-24)

Kaining independently catalogued 13 IDML use cases (16 counting the "b" variants) as left/right
edits plus an expected merge outcome. Recorded here cross-referenced against `TreeMatcher`/
`EditDetector`/`Merger` as they actually stand, not just restated.

**Already correctly handled:**

- **01** (one-sided add) -> `MergedInsert`.
- **02** (one-sided delete) -> `MergedDelete`, for the "accept" half. The "fix references if
  threads are involved" half is a real gap - see below.
- **12** (`tf1.Next=tfA` vs `tf1.Next=tfB`) -> `NextTextFrame` is a plain XML attribute (confirmed
  against real files, e.g. `NextTextFrame="n"`), so this is just the existing "same attribute,
  different value" case -> `Conflict`. A useful confirmation that attribute-level conflict
  detection already generalizes to thread-linking attributes, not just visual ones.

**A real semantic mismatch, now fixed**: **06** (delete-vs-modify) - Kaining's expected outcome is
a conflict. `Merger` previously let deletion win silently, following Lindholm's stated *default*
(he treats Delete/Edit as an optional, separately-checked category, not a core conflict). Changed
`Merger` to report a `Conflict` for this case instead (sentinel key `"(deleted vs updated)"`,
carrying `"(deleted)"` on one side and a summary of the other side's changed attributes on the
other) - Kaining's own catalogue is the more authoritative source for what this tool actually needs
to do, and it disagrees with Lindholm's default here.

**Real, unbuilt gaps this table makes concrete** (previously abstract "future work," now with
actual test cases to build toward):

- **Thread-reference repair** (02's parenthetical, `06b`, `11`) - deleting or inserting a linked
  `TextFrame` needs to fix up `Previous`/`NextTextFrame` on its neighbors, not just vanish/appear
  and leave dangling references.
- ~~**Structural moves / z-order** (`03`, `05`, `09`)~~ **Done**: `PcsMerger`'s successor/predecessor
  rules (`EditDetector`/`Merger` alone never compared child order at all). `09` in particular turned
  out not to need anything special: "move r1 forward, after r2" and "move r2 backward, before r1"
  produce the *identical* resulting order, so both sides' edits agree at every touched relation slot
  and merge with no conflict - z-order is just a plain reorder wearing different vocabulary, and a
  genuinely contradictory z-order change is caught by the same mechanism as `05` (`PcsMergerSpec`).
- ~~**Insertion ordering** (`04`)~~ **Done**: also falls out of the same mechanism with no extra
  code - two inserts at different anchors touch different `(parent, predecessor)` keys, so both
  apply independently and the resulting order is whatever following the chain from `ListStart`
  produces (`PcsMergerSpec`'s `04-both-add-diff-pos`).
- **Story-text-level diffing** (`07`, `08`, `08b`) - entirely out of scope of what exists: only
  page-item *attributes* are diffed, never the actual paragraph/word content inside a `Story`.
  `08b` in particular pins down a real granularity decision not yet made: word-level diffing would
  accept it, whole-paragraph-as-one-atomic-unit would conflict.
- ~~**Structural-parent-change conflicts** (`10`) - ungrouping while a sibling edit assumes the
  group still exists.~~ **Done**: `PcsMerger`'s "unique parent" rule (above) reports exactly this.
- **Cross-reference conflicts** (`13`) - redirecting `ParentStory` on one side while the other
  edits the story that used to feed that frame. A genuinely different *category* from anything
  handled now - not two edits to the same node, but two edits to two different nodes that
  reference each other.

~~**A new gap, not from Kaining's table** - **volatile/auto-stamped attributes**~~ **Done
(2026-09-20)**: InDesign re-stamps some attributes on every export regardless of user action
(`LinkImportTime` on a `Link`, confirmed directly against `HelloWorld2A`/`B`/`C`; the `ModifyDate`/
`InstanceID`/history bookkeeping in `META-INF/metadata.xml`, per the earlier `Mergeable`/
`Mergeable-2` finding, is the same phenomenon one level up, in a different part). Both `Merger`'s
attribute diff and `Pcs`'s whole-blob `Content` comparison treated these exactly like any other
attribute - so two independently re-exported copies, with *no* real user edit overlap at all, could
still surface a false conflict purely from this bookkeeping.

Fixed with an `ignoredAttributes: Set[String]` parameter, threaded through `EditDetector.detectEdits`/
`Merger.merge` (idml-specific, so it carries a real default - `EditDetector.defaultIgnoredAttributes
= Set("LinkImportTime")`) and `PcsEditDetector.detectEdits`/`PcsMerger.merge` (generic, so their
default is empty - a caller decides). `ThreeWayMerger.merge` takes the same parameter and forwards it
to *both* mergers, not just one: a node whose only difference is an ignored attribute must look
unchanged to both, since if only one merger ignores it, the other still reports a conflict that
`ThreeWayMerger`'s dedup logic can't catch (it only defers to the *other* merger's report when that
merger had *some* opinion on the node - none, if it never even saw an edit there). The generic
`PcsEditDetector` fix compares two `Content` values ignoring specified keys, without stripping those
keys from what's actually stored - an ignored attribute never causes a false edit, but its real value
is still carried along intact wherever a genuine edit *is* recorded, so nothing is ever silently
dropped from a real IDML file. `ThreeWayMergerSpec` confirms both directions against the real trio:
the whole Spread merges cleanly with the default in place, and the `LinkImportTime` conflict
reappears if a caller explicitly opts out (`ignoredAttributes = Set.empty`).

## Eventual integration: a git merge driver

The natural "endgame" for this work, once the merge logic itself is further along: git has a
built-in extension point for exactly this - a **merge driver** - the same mechanism tools like
`nbdime` use for Jupyter notebooks.

- **`.gitattributes`** declares which driver applies to which files, e.g. `*.idml merge=idml3way`.
- **Git config** maps that driver name to an actual command, e.g.
  `git config merge.idml3way.driver "idml-merge %O %A %B"`. Git substitutes `%O`/`%A`/`%B` with real
  file paths for the base/ours/theirs versions before invoking it (`%L`/`%P` are also available:
  a conflict-marker-size hint, and the original pathname).
- **The driver is invoked as an ordinary shell subprocess** - there's no special git API, no
  linking against `libgit2`, nothing beyond "git runs this command with these file-path arguments."
  It can be anything executable: a compiled binary, a shell script, or - for us - a packaged JVM
  program (e.g. an assembled runnable JAR via `sbt-assembly`, invoked as `java -jar idml-merge.jar
  %O %A %B`, optionally wrapped in a thin shell script to keep the git config line simple).
- **The contract**: exit code `0` means "merged successfully, use whatever's now in the `%A` file";
  nonzero means "conflict." Unlike git's own line-based merge, it does *not* insert `<<<<<<<`/
  `=======`/`>>>>>>>` markers for a custom driver - it's entirely up to the driver to leave `%A` in
  a sensible state (or invent its own conflict-marking convention) when it reports a conflict.
- **Practical catch**: the driver *definition* (the actual command) lives in git config, which
  isn't versioned or distributed with the repo. `.gitattributes` can travel with the repo and name
  the driver, but each collaborator still has to separately run the `git config merge.idml3way...`
  command (or a setup script has to do it for them) before it takes effect - git deliberately
  doesn't let a repo silently make a clone run an arbitrary command.

**Conflict representation and resolution UX (Robin's proposal, 2026-08-24)**: since a custom
driver doesn't get git's own `<<<<<<<`/`=======`/`>>>>>>>` text markers, and an IDML file isn't
text anyone would want to hand-edit anyway, the driver could instead write *both* the "ours" and
"theirs" versions of each conflicted node directly into the `%A` file when it reports a conflict
(rather than picking one, or leaving something unresolved). A separate dedicated editor/tool would
then let the user resolve each recorded conflict with one of four choices:

- `0` - neither (drop it)
- `1` - ours
- `2` - theirs
- `3` - both

This fits naturally with `Merger`'s own `Conflict` type, which already carries both sides' values -
the driver's job would just be to serialize every `Conflict` it gets back from `Merger.merge` into
the `%A` file in a form that resolver tool can read, rather than trying to guess a resolution or
force the user into git's usual inline text-conflict workflow.

## Open questions / untested cases

Recorded so they aren't lost, not yet investigated:

- ~~**Genuine conflicts**: both sides editing the *same* attribute of the same object
  differently.~~ **Done**: `Merger.reconcileUpdates` handles this, tested both synthetically and
  against the real `Mergeable` Insert/Insert collision (`u11c`'s differing `FillColor`).
- **Deletion**: does a deleted page item just vanish cleanly, with no dangling references left in
  sibling `Previous`/`NextTextFrame`-style attributes or elsewhere? Kaining's catalogue (above)
  confirms this is a real requirement, not a hypothetical - thread-reference repair is still
  unbuilt.
- ~~**Structural moves**: reordering page items...~~ **Done**: `PcsMerger`'s successor/predecessor
  rules, per real IDML moves - does `Self` survive a real move, and how is the new position
  expressed? Still genuinely open, though: all structural-move testing so far is against synthetic
  trees or `HelloWorld2`-scale files; not yet checked against a real, larger-scale reorder.
- ~~**Weak/no-identity documents lose edits silently, not just conflict wrongly**~~ **Done**:
  `ContentMatcher`/`PcsMerger.mergeByContent` (above, "Fixed: weak/no-identity documents silently
  losing edits") - for the demonstrated case; reparenting and multi-way ambiguous remainders are
  real, separately-tracked scope limits of the fix itself, not this open question re-opened.
- Whether `Self` stability holds up over *many* rounds of independent editing, not just one.
- **What actually determines whether a migration/reopen fully regenerates `Self` values or leaves
  them untouched** - `Magazine-1` came through clean, `Magazine-2` didn't, from the same starting
  file and the same InDesign version doing the editing. Not yet understood.
