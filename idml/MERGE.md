# Notes toward a 3-way merge for `idml`

Started 2026-08-15 as research and empirical findings gathered before designing `idml`'s eventual
3-way merge (Kaining's research project - see [DESIGN.md](../DESIGN.md)). No longer just groundwork:
a real, working (if still incomplete) 3-way merge exists now - `ThreeWayMerger.merge`.

**Split 2026-09-20**: the generic relation/merge layer this research led to (`Pcs`,
`PcsEditDetector`, `PcsMerger`, `PcsTreeBuilder`, `ContentMatcher`, `TreeMatcher`/`NodePath`/
`NodeRef`) turned out to depend on nothing idml-specific except one hardcoded string, and now lives
in `core` (`com.phasmidsoftware.xmldoc.merge`), reusable by `kml` or any other `GenericElement`-based
document type. Its own documentation moved with it, to [core/MERGE.md](../core/MERGE.md) - the PCS
relation model, the structural merger's three consistency rules, tree reconstruction, `ContentMatcher`,
and the `kml` experiment all live there now. This file keeps the IDML-specific half: empirical `Self`
stability findings, Kaining's conflict-condition catalogue, `EditDetector`/`Merger`/`ThreeWayMerger`
(genuinely built around `Self`-matching, not generic), and the eventual git-merge-driver plan. Written
in the order things were actually found and built, rather than rewritten into a clean top-down spec
each time something changes - the "Kaining's 13 conflict conditions" and "Built next" sections are
the fastest way to see current state; the rest is how it got there.

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

## Proposed design direction (not yet built, as of 2026-08-17)

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
  `GenericElement`'s own tag/attributes/children shape. **Built** - see
  [core/MERGE.md](../core/MERGE.md).

## Built next (2026-09-20): wiring `Merger` and `PcsMerger` together - `ThreeWayMerger`

The two mergers had been developed and tested entirely in parallel up to this point - neither had
ever been run *together* against the same three trees. `ThreeWayMerger.merge` is the first thing in
this codebase that produces an actual, real, merged `GenericElement` combining both kinds of edit.

**The division of labor, and why it isn't arbitrary**: structure (which nodes exist, their order,
their parent) comes entirely from `PcsMerger`/`PcsTreeBuilder` (both generic, `core` - see
[core/MERGE.md](../core/MERGE.md)); a matched node's own tag/attributes come from `Merger` wherever
`Merger` has an opinion (i.e. the node has a `Self`). This isn't just "pick one merger per concern"
for its own sake - `PcsMerger`'s own `Content` relation only ever compares a node's *whole*
tag+attributes as one indivisible blob (it has to; `Pcs` has no concept of individual attributes at
all), so on its own it cannot tell "both sides changed *different* attributes of the same node"
(safe to combine, per `Merger`) apart from "both sides changed the *same* thing differently" (a
genuine conflict) - it would (wrongly) flag the first as a conflict too. `ThreeWayMergerSpec`'s first
test confirms this directly: the `HelloWorld2A`/`HelloWorld2B`-style disjoint-attribute-change case
that `Merger`/`MergerSpec` already merge cleanly is confirmed to make *plain* `PcsMerger` conflict on
its own, then confirms `ThreeWayMerger` merges it cleanly anyway.

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
  rules (`EditDetector`/`Merger` alone never compared child order at all) - see
  [core/MERGE.md](../core/MERGE.md). `09` in particular turned out not to need anything special:
  "move r1 forward, after r2" and "move r2 backward, before r1" produce the *identical* resulting
  order, so both sides' edits agree at every touched relation slot and merge with no conflict -
  z-order is just a plain reorder wearing different vocabulary, and a genuinely contradictory
  z-order change is caught by the same mechanism as `05` (`PcsMergerSpec`).
- ~~**Insertion ordering** (`04`)~~ **Done**: also falls out of the same mechanism with no extra
  code - two inserts at different anchors touch different `(parent, predecessor)` keys, so both
  apply independently and the resulting order is whatever following the chain from `ListStart`
  produces (`PcsMergerSpec`'s `04-both-add-diff-pos`).
- **Story-text-level diffing** (`07`, `08`, `08b`) - entirely out of scope of what exists: only
  page-item *attributes* are diffed, never the actual paragraph/word content inside a `Story`.
  `08b` in particular pins down a real granularity decision not yet made: word-level diffing would
  accept it, whole-paragraph-as-one-atomic-unit would conflict.
- ~~**Structural-parent-change conflicts** (`10`) - ungrouping while a sibling edit assumes the
  group still exists.~~ **Done**: `PcsMerger`'s "unique parent" rule reports exactly this - see
  [core/MERGE.md](../core/MERGE.md).
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
= Set("LinkImportTime")`) and `core`'s generic `PcsEditDetector.detectEdits`/`PcsMerger.merge` (whose
default is empty - a caller decides; see [core/MERGE.md](../core/MERGE.md)). `ThreeWayMerger.merge`
takes the same parameter and forwards it to *both* mergers, not just one: a node whose only
difference is an ignored attribute must look unchanged to both, since if only one merger ignores it,
the other still reports a conflict that `ThreeWayMerger`'s dedup logic can't catch (it only defers to
the *other* merger's report when that merger had *some* opinion on the node - none, if it never even
saw an edit there). The generic `PcsEditDetector` fix compares two `Content` values ignoring
specified keys, without stripping those keys from what's actually stored - an ignored attribute never
causes a false edit, but its real value is still carried along intact wherever a genuine edit *is*
recorded, so nothing is ever silently dropped from a real IDML file. `ThreeWayMergerSpec` confirms
both directions against the real trio: the whole Spread merges cleanly with the default in place,
and the `LinkImportTime` conflict reappears if a caller explicitly opts out (`ignoredAttributes =
Set.empty`).

## A git merge driver

The "endgame" for this work: git has a built-in extension point for exactly this - a **merge
driver** - the same mechanism tools like `nbdime` use for Jupyter notebooks.

**Built (2026-09-20)**: `IdmlMergeDriver.merge(base, ours, theirs)` (mutating `ours` in place, git's
own `%O`/`%A`/`%B` contract exactly) and its `main` method, the actual runnable driver. It's a thin
orchestration layer, not new merge logic: `IdmlPackageMerger` reconciles every part `IdmlPackage`
lists (Spreads, Stories, MasterSpreads, Resources, ...) - one `ThreeWayMerger.merge` per part still
present on all three sides, plus the same present/absent case analysis `Merger`/`PcsMerger` already
use for a single node, one level up (a whole part added, removed, or both-sides-added-differently).

**`designmap.xml` is deliberately not part of that reconciliation**, for two real reasons found
along the way, not assumed up front:

- It's a much richer element than "a list of part references" - it has its own `Self`, and its own
  other `Self`-bearing children (e.g. `Language` resource definitions) that a real 3-way merge would
  need to handle on their own terms. Out of scope for this first cut.
- Worse: `scala.xml`'s own parser silently drops the leading `<?aid style="50" type="document" ...?>`
  processing instruction every real `designmap.xml` starts with (confirmed directly - it never even
  reaches `elem.child`), and `GenericElement` has no representation for a processing instruction at
  all, so a parse-then-rebuild round trip would lose it outright.

Both are avoided the same way: `ours`'s `designmap.xml` is left **completely untouched** unless a
part was actually added or removed, in which case its raw text is **surgically patched** - one
`<idPkg:Type src="..." />` line inserted or removed, via a regex, not a parse - so everything else
about the file, including that processing instruction, survives byte for byte. `IdmlMergeDriverSpec`
covers all three shapes against real files: a clean one-sided insertion (no part added/removed, so
`designmap.xml` isn't touched at all), a whole part removed (patched), and a genuine conflict
(`ours` left byte-for-byte untouched, confirmed by comparing file bytes before and after).

**A second real finding from testing a whole package for the first time, not just one Spread**:
`Resources/Styles.xml`'s `StyleUniqueId` (on built-in styles like `CharacterStyle/$ID/[No character
style]`) turned out to be a second volatile, auto-regenerated attribute, exactly like
`LinkImportTime` - a UUID that differed across `base`/`HelloWorld2A`/`HelloWorld2B` *simultaneously*
(three different UUIDs for the same style). Added to `EditDetector.defaultIgnoredAttributes`
alongside it. Once that stopped masking things, testing the real `HelloWorld2A`/`B`/`C` trio as a
*whole package* surfaced a **genuine** conflict no prior test (all scoped to one Spread) could have
found: `Stories/Story_ued.xml`'s own text ("Hello World!" in the base) was independently changed to
two different strings by A and B - a real Update/Update, correctly left blocking the merge.

**Not yet built**: reading `%O`/`%A`/`%B` as real `.idml` files is exactly what `IdmlMergeDriver`
does, so the merge logic itself is no longer the gap - what's left is *installing* it:

- **`.gitattributes`** declares which driver applies to which files, e.g. `*.idml merge=idml3way`.
- **Git config** maps that driver name to an actual command, e.g.
  `git config merge.idml3way.driver "idml-merge %O %A %B"`. Git substitutes `%O`/`%A`/`%B` with real
  file paths for the base/ours/theirs versions before invoking it (`%L`/`%P` are also available:
  a conflict-marker-size hint, and the original pathname).
- **The driver is invoked as an ordinary shell subprocess** - there's no special git API, no
  linking against `libgit2`, nothing beyond "git runs this command with these file-path arguments."
  `IdmlMergeDriver.main` already matches that contract exactly (three file-path args, exit code
  `0`/`1`/`2`); today it's runnable via
  `sbt "idml/runMain com.phasmidsoftware.xmldoc.idml.IdmlMergeDriver.main %O %A %B"` (or a JVM
  classpath invocation against the compiled classes directly) - **not yet built**: packaging it as a
  single portable executable (an `sbt-assembly` fat JAR, `java -jar idml-merge.jar %O %A %B`,
  optionally wrapped in a thin shell script) so the `git config` line doesn't need `sbt`/a project
  checkout at merge time. `sbt-assembly` isn't a project plugin yet either.
- **The contract**: exit code `0` means "merged successfully, use whatever's now in the `%A` file";
  nonzero means "conflict." Unlike git's own line-based merge, it does *not* insert `<<<<<<<`/
  `=======`/`>>>>>>>` markers for a custom driver - it's entirely up to the driver to leave `%A` in
  a sensible state (or invent its own conflict-marking convention) when it reports a conflict.
  `IdmlMergeDriver` leaves `%A` completely untouched on conflict (confirmed byte-for-byte in
  `IdmlMergeDriverSpec`) and prints each conflicting part and slot to stderr - not yet Robin's
  ours/theirs-embedding proposal below.
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

This fits naturally with `PartConflict`/`StructuralConflict`, which already carry both sides' values
for every conflicting part and slot `IdmlPackageMerger` finds - the driver's job would just be to
serialize each one into the `%A` file in a form a resolver tool can read, rather than trying to
guess a resolution or force the user into git's usual inline text-conflict workflow. Not yet built -
`IdmlMergeDriver` only prints them to stderr today.

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
  rules (generic, `core` - see [core/MERGE.md](../core/MERGE.md)), per real IDML moves - does `Self`
  survive a real move, and how is the new position expressed? Still genuinely open, though: all
  structural-move testing so far is against synthetic trees or `HelloWorld2`-scale files; not yet
  checked against a real, larger-scale reorder.
- ~~**Weak/no-identity documents lose edits silently, not just conflict wrongly**~~ **Done**: fixed
  generically, not idml-specific - see [core/MERGE.md](../core/MERGE.md)'s "Fixed: weak/no-identity
  documents silently losing edits" (`ContentMatcher`/`PcsMerger.mergeByContent`), found via `kml`.
- Whether `Self` stability holds up over *many* rounds of independent editing, not just one.
- **What actually determines whether a migration/reopen fully regenerates `Self` values or leaves
  them untouched** - `Magazine-1` came through clean, `Magazine-2` didn't, from the same starting
  file and the same InDesign version doing the editing. Not yet understood.
