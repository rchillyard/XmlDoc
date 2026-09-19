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

One thing deliberately not attempted here, an already-tracked gap: "unique parent" (a node moved to
a genuinely different parent - Kaining's `10-group-ungroup`).

**Built next (2026-09-19): tree reconstruction.** `PcsTreeBuilder.build` is the other half of the
sentence in §6 the pseudocode itself doesn't spell out: "Tm can be reconstructed by traversing from
⊥0 level by level along the PCS relations in Δ." It walks a `RelationSet` from a given root label,
following each parent's `Pcs` chain from `ListStart` to `ListEnd` and looking up each label's
`Content`, to rebuild an actual `GenericElement`. `StructuralMergeResult` now carries the merge's
own root label (`base`/`left`/`right`'s shared root, since they're assumed to already correspond),
so `PcsTreeBuilder.build(result: StructuralMergeResult)` needs nothing else.

Two things worth noting:

- **It fails cleanly, on purpose, when handed an inconsistent relation set** - i.e. when
  `conflicts` was non-empty and got ignored anyway. This isn't just defensive: `PcsMergerSpec`'s own
  `05-move-move-divergent` case, walked through by hand, resolves to `u1`'s successor still pointing
  at `u2` (the stale base value, since that slot conflicted) while `u2`'s successor now points back
  at `u1` (one side's winning edit) - a genuine 2-cycle. `PcsTreeBuilderSpec` confirms `build` catches
  it (and a missing-content case, from an Insert/Insert content conflict) rather than looping forever
  or fabricating a tree.
- **It's lossy in the same way the rest of this layer already is**: `Pcs.relations` never captured
  `GenericText`/`GenericCData` children (see `Pcs.scala`), so a rebuilt tree never has any text
  content, even where the originals did - confirmed against a real file by comparing to a
  text-node-stripped copy of the expected tree, not the raw original.

Round-tripped successfully: a plain tree through `Pcs.relations` and back unchanged; a one-sided
reorder and a one-sided insertion, each exactly as the modifying side arranged it; both sides'
independent, non-conflicting changes combined into one tree; and the real `HelloWorld2` ->
`HelloWorld2D` insertion, end to end through `PcsMerger` and back into a real `GenericElement`.

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
- **Structural moves / z-order** (`03`, `05`, `09`) - "pinned to top" and z-order changes are
  almost certainly child-*order* changes within the Spread, not attribute changes at all.
  `EditDetector` currently only diffs attributes on matched nodes - it never compares whether a
  node's position among its siblings changed. This is the "Structural moves" item already in the
  open questions below, now with three concrete cases.
- **Insertion ordering** (`04`) - two independent inserts at different positions both get kept
  (each already shows up as its own `MergedInsert`), but *where* they land relative to each other
  in the merged order isn't resolved at all yet.
- **Story-text-level diffing** (`07`, `08`, `08b`) - entirely out of scope of what exists: only
  page-item *attributes* are diffed, never the actual paragraph/word content inside a `Story`.
  `08b` in particular pins down a real granularity decision not yet made: word-level diffing would
  accept it, whole-paragraph-as-one-atomic-unit would conflict.
- **Structural-parent-change conflicts** (`10`) - ungrouping while a sibling edit assumes the
  group still exists.
- **Cross-reference conflicts** (`13`) - redirecting `ParentStory` on one side while the other
  edits the story that used to feed that frame. A genuinely different *category* from anything
  handled now - not two edits to the same node, but two edits to two different nodes that
  reference each other.

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
- **Structural moves**: reordering page items (z-order/stacking, i.e. actual child-list position,
  not just `ItemTransform` coordinates) - does `Self` survive a real move, and how is the new
  position expressed in the IDML? `Pcs.relations` (above) can now represent and detect this; no
  structural merger consumes it yet.
- Whether `Self` stability holds up over *many* rounds of independent editing, not just one.
- **What actually determines whether a migration/reopen fully regenerates `Self` values or leaves
  them untouched** - `Magazine-1` came through clean, `Magazine-2` didn't, from the same starting
  file and the same InDesign version doing the editing. Not yet understood.
