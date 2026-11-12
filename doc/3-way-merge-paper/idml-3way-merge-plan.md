# IDML Three-Way Merge — Implementation Plan (English)

## Phase 0 (scope decisions)

- The first version only accepts three files with identical DOMVersion.
- Clarify the same-gap insertion ordering strategy, and whether left/right symmetry is required.
- Clarify whether, when an anchor is moved, the new node follows the anchor or stays in its original slot.
- On conflict, the first version does not produce a half-valid document: `document=None`, returning only structured conflicts.
- The first version explicitly rejects signed and encrypted IDML.
- Unmodified XML entries preserve their original uncompressed content bytes; the whole ZIP is not required to be byte-identical.
- For Story text, the first version handles only ordinary paragraphs and Content; tables, footnotes, and inline objects are deferred to later extensions.

A runnable environment must also be restored first: the project uses Scala 3.8.4 and sbt 1.11.7; the current machine has no Java/sbt, so the existing 41 Scala tests cannot run.

---

## Phase 0: Contract, documentation, and test environment

### PR 0: merge contract

Add an ADR or update `idml/MERGE.md`, spelling out:

**Input conditions**

`base.idml`, `left.idml`, `right.idml` must satisfy:

- All are openable IDML ZIPs.
- `mimetype` is correct.
- `DOMVersion` is identical.
- The three share a correspondable document lineage.
- `Self` is unique within the package.
- Contains no signed or encrypted content unsupported by the first version.

**Output semantics**

```scala
enum MergeStatus:
  case Clean, Conflict, InvalidInput, Unsupported

case class MergeResult(
  status: MergeStatus,
  document: Option[IdmlDocument],
  appliedEdits: Vector[DocumentEdit],
  conflicts: Vector[MergeConflict],
  diagnostics: Vector[Diagnostic]
)
```

Recommended for the first version:

- Clean: `document=Some(...)`
- Conflict: `document=None`
- InvalidInput, Unsupported: do not enter merge
- Once the resolver matures, add support for a best-effort document under the conflict state.

**Policies that must be decided**

Establish a `MergePolicy`:

```scala
case class MergePolicy(
  sameGapOrdering: SameGapOrdering,
  movedAnchorBehavior: MovedAnchorBehavior,
  missingAnchorBehavior: MissingAnchorBehavior,
  deleteModify: DeleteModifyPolicy,
  textGranularity: TextGranularity
)
```

Recommended defaults:

- same gap: keep each branch's internal block order, then use a stable, reproducible branch tie-break.
- missing anchor: fall back to the nearest surviving predecessor/successor in base.
- moved anchor: keep the original slot by default; do not let an inserted node unexpectedly follow an object moved by the other branch.
- delete/modify: conflict.
- text: token-aware, falling back to character diff when necessary.

**PR 0 acceptance**

- Documentation no longer claims "nothing has been implemented yet".
- Fix the 15/16 test-case count error.
- Fix the missing `doc/Lindholm.pdf` path description.
- Change the check command in the corpus documentation to `--verify-only`.
- CI can run `sbt idml/test`.

---

## Phase 1: Package-level model and test entry point

The existing `IdmlPackage` (line 31) only stores file paths and the designmap, reading XML on demand; it is not yet suitable as the document model for merging.

### PR 1: Implement IdmlDocument

**Entry model**

```scala
sealed trait IdmlEntry:
  def path: String
  def originalBytes: Array[Byte]
  def dirty: Boolean

case class RawEntry(
  path: String,
  originalBytes: Array[Byte],
  zipMetadata: ZipEntryMetadata
) extends IdmlEntry

case class XmlEntry(
  path: String,
  originalBytes: Array[Byte],
  envelope: XmlEnvelope,
  root: GenericElement,
  zipMetadata: ZipEntryMetadata,
  dirty: Boolean = false
) extends IdmlEntry
```

All entries retain their bytes; only XML entries get a tree. Binary entries such as images and thumbnails cannot be read as UTF-8.

**The XML envelope must be preserved**

The current `GenericElement.fromElem` keeps only the root element and may lose:

- XML declaration
- `<?aid ...?>` processing instruction
- whitespace before and after the root element
- other prolog/epilog content

Therefore add:

```scala
case class XmlEnvelope(
  declaration: Option[String],
  processingInstructions: Vector[ProcessingInstruction],
  root: GenericElement,
  trailingContent: String
)
```

Unmodified XML uses `originalBytes` directly; only dirty XML is re-serialized.

**Document model**

```scala
case class IdmlDocument(
  source: Path,
  entries: Vector[IdmlEntry],
  designmap: XmlEntry,
  parts: Vector[IdmlPart],
  partsByKey: Map[PartKey, IdmlPart],
  selfIndex: SelfIndex,
  references: ReferenceGraph,
  properties: DocumentProperties
)

case class DocumentProperties(
  self: String,
  domVersion: String,
  storyList: Vector[String]
)
```

### PR 2: Correct Part identity

"Part type + root Self" needs a fallback, because in the existing fixtures:

- Story, Spread, MasterSpread, and BackingStory have a Self on their actual content child node.
- Graphic, Fonts, Styles, Preferences, and Tags have no Self on their wrapper root.

Therefore use:

```scala
sealed trait PartKey

case class IdentifiedPart(
  partType: String,
  rootSelf: String
) extends PartKey

case class SingletonPart(
  partType: String
) extends PartKey
```

Matching rules:

1. Derive `partType` from `designmap.xml`'s `idPkg:* @src`.
2. Open the corresponding entry.
3. For Story, Spread, MasterSpread, and BackingStory, find the Self of the actual content root.
4. For Graphic, Fonts, Styles, Preferences, and Tags, use `SingletonPart(partType)`.
5. Treat the file path only as the current location, not as identity.
6. If one `PartKey` maps to two entries, report a duplicate-part error.

This handles the case where, after IDML re-export, the `Story_xxx.xml` filename changes but the internal Story Self is preserved.

### PR 3: Validator

Build a composable validator:

```scala
trait IdmlValidator:
  def validate(document: IdmlDocument): Vector[Diagnostic]
```

First set of rules:

**ZIP/UCF**

- `mimetype` is the first entry.
- Uncompressed.
- Content is `application/vnd.adobe.indesign-idml-package`.
- No corrupted ZIP entries.
- All designmap `@src` exist.

**Part and manifest**

- No duplicate `src` in the designmap.
- The actual part type of each `src` is correct.
- The internal Self of Story, Spread, and MasterSpread matches the index.
- `StoryList` is consistent with the set of `idPkg:Story` references.
- No duplicates or missing Stories in `StoryList`.

**Self**

```scala
case class SelfLocation(
  self: String,
  part: PartKey,
  entryPath: String,
  nodePath: NodePath,
  tag: String
)
```

- Each defining Self is unique in the package-level index.
- Duplicate Self is an error; the current `toMap` "silently overwrite with the last one" behavior is dropped.
- named Self and derived Self are, for now, also included in the uniqueness check, but their category is recorded in diagnostics.

**References**

Do not guess all references from attribute names; build an explicit, extensible registry:

```scala
case class ReferenceRule(
  ownerTag: String,
  attribute: String,
  targetKinds: Set[String],
  cardinality: Cardinality
)
```

The first version covers at least:

- `idPkg:* @src`
- `Document @StoryList`
- `TextFrame @ParentStory`
- `TextFrame @PreviousTextFrame`
- `TextFrame @NextTextFrame`
- `Page @AppliedMaster`
- `PageItem @ItemLayer`
- common style/color/object-style references

Handle sentinel/reference vocabulary such as `"n"` and built-in `$ID` resources separately; they must not be falsely reported as dangling references.

### PR 4: Parameterized corpus test entry point

Add:

- `IdmlCorpusSpec.scala`
- `CorpusCase.scala`
- `ExpectedCase.scala`

The project currently has no JSON library; it is recommended to first add a lightweight test-scope JSON decoder and map `expected.json` to strong types:

```scala
case class ExpectedCase(
  schemaVersion: Int,
  name: String,
  expectedStatus: String,
  acceptedEdits: Vector[ExpectedEdit],
  conflicts: Vector[ExpectedConflict],
  invariants: ExpectedInvariants
)
```

The first-stage tests are responsible only for:

- Automatically discovering exactly 15 cases.
- Each directory containing the five prescribed files.
- The JSON being parseable.
- All three IDMLs being constructible as `IdmlDocument`.
- Artifact hash and size being correct.
- base/left/right all passing package-level validation.
- `commonBase` and `unchangedSelfValuesStable` holding.
- Each case's `expectedStatus` and `conflicts` structure being valid.

Do not, for now, pretend the merge result has already been tested.

**Phase 1 completion criteria**

- All 15 × 3 IDMLs load and pass validation.
- On an edit-free load → write round-trip, every entry's content bytes are unchanged.
- `PartKey` can match Story/Spread across the three versions without relying on paths.
- Artificially created duplicate Self, missing `src`, wrong `StoryList`, and dangling references all return structured errors.
- Corpus tests do not hardcode the 15 directory names; new cases are discovered automatically.

---

## Phase 2: Structural edit model

The existing `EditDetector` (line 55) can only compare attributes and needs to be upgraded to a document-level state differ.

### PR 5: Identity and location model

```scala
case class NodeId(self: String)

case class NodeLocator(
  part: PartKey,
  nodeId: NodeId,
  tag: String
)

case class NodeContext(
  parent: ParentLocator,
  predecessor: Option[NodeId],
  successor: Option[NodeId]
)
```

Parent may be:

```scala
sealed trait ParentLocator
case class IdentifiedParent(id: NodeId) extends ParentLocator
case class PartRoot(part: PartKey) extends ParentLocator
```

Phase 2 handles only Self-bearing nodes and their Self/part-root parent. Paragraphs and CharacterStyleRanges without Self are left to Phase 5.

When computing context:

- Ignore indentation whitespace.
- predecessor/successor use the nearest Self-bearing sibling.
- Keep both the base and branch contexts.
- Do not use child ordinal as persistent identity.

### PR 6: Unified edit types

```scala
sealed trait DocumentEdit:
  def target: EditTarget
  def side: BranchSide

case class Insert(
  node: NodeSnapshot,
  context: NodeContext,
  side: BranchSide
) extends DocumentEdit

case class Delete(
  node: NodeLocator,
  oldContext: NodeContext,
  side: BranchSide
) extends DocumentEdit

case class UpdateAttribute(
  node: NodeLocator,
  key: String,
  oldValue: Option[String],
  newValue: Option[String],
  side: BranchSide
) extends DocumentEdit

case class Move(
  node: NodeLocator,
  oldContext: NodeContext,
  newContext: NodeContext,
  side: BranchSide
) extends DocumentEdit

case class Reparent(
  node: NodeLocator,
  oldContext: NodeContext,
  newContext: NodeContext,
  side: BranchSide
) extends DocumentEdit

case class UpdateText(...) extends DocumentEdit
```

The `UpdateText` type can be defined now, but is only produced in Phase 5.

**Insert/Insert Self collision**

If base lacks the Self and both left and right have it:

- tag, attributes, subtree, and context all equal: treat as a convergent insert.
- content or position differs: `InsertIdentityCollision`.
- Do not match directly just because the Self is the same.

### PR 7: Order-difference detection

Compute, per parent, separately:

- base Self sequence
- left Self sequence
- right Self sequence

Use LCS/Myers to identify:

- insertions
- deletions
- order preserved
- candidate moves pushed out of the LCS

Note: this is a state-based merge and cannot always infer which node the user actually moved. For example, in case 09 the final left and right files are identical; all that can be known is the final constraint `r2 < r1`, without proving that one side "moved r1" and the other "moved r2". Therefore:

- edit diagnostics may pick a deterministic minimal script.
- merge-correctness tests should assert the final sibling order.
- They should not force assertions about the original GUI operation history.

### PR 8: Structural constraint merging

Turn sibling order into directed constraints:

```
A before B
B before C
```

Process:

1. Build the default ordering constraints from base.
2. Remove base edges negated by each branch's edit script.
3. Add the new constraints from the left and right branches.
4. Remove deleted nodes.
5. Add anchor constraints from inserts/moves.
6. Run a stable topological sort.
7. If there is a cycle, return `PositionConflict`.
8. For multiple zero-in-degree nodes, use `MergePolicy.sameGapOrdering` to determine a stable order.

Nodes inserted consecutively by the same branch must keep their order as a block and must not be interleaved by the other branch's inserts.

### PR 9: Structural conflicts

Add typed conflicts:

```scala
sealed trait MergeConflict

case class AttributeConflict(...)
case class DeleteModifyConflict(...)
case class DeleteMoveConflict(...)
case class MoveMoveConflict(...)
case class ParentConflict(...)
case class PositionConflict(cycle: Vector[NodeId], ...)
case class MissingAnchorConflict(...)
case class InsertIdentityCollision(...)
```

Deleted subtrees must be checked recursively:

- left deletes a container
- right modifies, moves, or inserts into its descendant
- return cascade delete/modify or target-parent-deleted
- do not check only the deleted node's own attributes

**Phase 2 corresponding test cases**

- 01: identify an anchored Insert
- 02: identify a Delete
- 03: identify a Move
- 04: two Inserts keep their respective anchors
- 05: Move/Move conflict
- 06: Delete/Modify conflict
- 09: obtain the definite sibling order r2, r1
- 10: identify a reparent/parent-removal conflict
- atlas N1–N7, N9, N10

**Phase 2 completion criteria**

- Every structural edit carries old/new context.
- Sibling counts from 1 to 30 use the same algorithm.
- A constraint cycle can return the nodes forming the cycle and their source edges.
- The same input yields the same edit script and topological order every time.
- A pure sibling shift no longer yields "no change".

---

## Phase 3: Produce the actual merged document

### PR 10: Separate Reconciler from Materializer

Do not let the Merger both judge conflicts and modify the tree at once. Split into:

```
DocumentDiffer
    ↓
left edits + right edits
    ↓
EditReconciler
    ↓
ReconciliationPlan
    ↓
DocumentMaterializer
    ↓
IdmlDocument
    ↓
IdmlValidator
```

```scala
case class ReconciliationPlan(
  accepted: Vector[DocumentEdit],
  conflicts: Vector[MergeConflict],
  diagnostics: Vector[Diagnostic]
)
```

Materialization runs only when `conflicts.isEmpty`.

**Materializer order**

Recommended fixed order:

1. delete
2. reparent/move
3. insert
4. attribute modification
5. sibling topological sort
6. manifest/part index update
7. reference repair
8. full-document validation

This avoids deleting an anchor first and then being unable to locate a move/insert.

**Immutable updates**

Add tree-operation APIs:

```scala
GenericTree.replaceNode(...)
GenericTree.deleteNode(...)
GenericTree.insertNode(...)
GenericTree.moveNode(...)
GenericTree.updateAttribute(...)
GenericTree.reorderChildren(...)
```

Operations use `NodeLocator`; each failure returns a diagnostic; no `.get` and no silent skipping.

**Compatibility with the existing API**

Do not delete immediately:

```scala
Merger.merge(...): Seq[MergeOutcome]
```

Recommended:

- Add `IdmlMerger.merge(base, left, right, policy): MergeResult`.
- Mark the old API deprecated.
- Remove the old API only after the corpus and real IDML are fully migrated.

**Phase 3 completion criteria**

- 01, 02, 03, 04, 09 can produce a complete in-memory `IdmlDocument`.
- 05, 06, 10 return precise typed conflicts.
- Rebuilding the `SelfIndex` on the produced document shows no duplicates and no structural cycles.
- After a clean merge, diffing again should not reproduce the accepted edits.
- `merge(base, base, base)` is semantically equal to base.

---

## Phase 4: Reference graph

### PR 11: ReferenceGraph

```scala
case class ReferenceEdge(
  source: NodeLocator,
  attribute: String,
  target: ReferenceTarget,
  rule: ReferenceRule
)

case class ReferenceGraph(
  outgoing: Map[NodeLocator, Vector[ReferenceEdge]],
  incoming: Map[ReferenceTarget, Vector[ReferenceEdge]]
)
```

The reference graph must be built across entries, not checked only within a single Spread.

### PR 12: Thread repair

For TextFrame threads, define:

- `A.Next=B` must correspond to `B.Previous=A`.
- `"n"` means no predecessor/successor.
- When inserting a frame, update all four endpoints.
- When deleting a middle frame, connect its surviving predecessor and successor.
- Conflict on multiple predecessors, multiple successors, self-loops, or long cycles.

Handle case 11:

```
tf1 → tf2
```

becomes:

```
tf1 → tfX → tf2
```

and validate all bidirectional attributes.

### PR 13: Story and cross-node conflicts

Handle:

- `TextFrame.ParentStory`
- `Document.StoryList`
- `idPkg:Story @src`
- addition, deletion, and rename of Story parts
- reparenting a frame to another Story
- incoming frame references when deleting a Story

Rule for case 13:

- in base, tf2 → story1
- left points tf2 to story2
- right modifies the content in story1 that originally flowed into tf2
- return `ReferenceContentConflict`

This is a dependency conflict, not a same-node attribute conflict.

**Phase 4 corresponding test cases**

- the thread-delete extension of case 02
- 06b: frame deletion vs Story content
- 11: thread insert
- 12: NextTextFrame divergence
- 13: ParentStory vs Story content
- atlas N8
- graph cycle, dangling target, non-bidirectional thread

**Phase 4 completion criteria**

- Every deletion queries incoming edges.
- A clean result has no dangling references.
- Each node in the thread graph has at most one previous and one next.
- `StoryList`, Story parts, and `ParentStory` are mutually consistent.
- Reference changes no longer degrade into ordinary string-attribute merges.

---

## Phase 5: Story text

### PR 14: Story logical model

Convert the raw XML into a logical representation:

```scala
case class StoryText(
  story: NodeId,
  paragraphs: Vector[LogicalParagraph]
)

case class LogicalParagraph(
  key: ParagraphKey,
  text: String,
  styleSpans: Vector[StyleSpan],
  inlineObjects: Vector[InlineObject]
)

case class StyleSpan(
  start: Int,
  end: Int,
  attributes: Map[String, String]
)
```

`ParagraphKey` must not use the XML path directly; it is recommended to derive it jointly from:

- base paragraph alignment
- neighboring paragraph fingerprints
- normalized text hash
- branch position

### PR 15: paragraph alignment

First do a three-way LCS at the paragraph level:

- base ↔ left
- base ↔ right

Support:

- paragraph insert
- paragraph delete
- paragraph move
- independent modification of different paragraphs
- delete/modify conflict

Case 07 should merge the independent modifications to P2 and P4.

### PR 16: Intra-paragraph diff3

The tokenizer must:

- preserve whitespace
- support Unicode
- avoid splitting combining characters by UTF-16 code unit
- be able to fall back from token granularity to character/grapheme granularity

Rules:

- the two sides' edit spans do not intersect: merge
- spans overlap and the replacement is the same: converge
- spans overlap and the replacement differs: `TextOverlapConflict`
- one side deletes and the other modifies the same range: conflict

Corresponds to:

- 08: conflict
- 08b: merge into swift + sleepy

### PR 17: style-span reprojection

After the text merge:

1. Project the base style spans onto the new text according to the edit mapping.
2. Apply the style spans added or modified by left and right.
3. Merge identical adjacent styles.
4. Regenerate `CharacterStyleRange`.
5. Do not require preserving the original range slice boundaries.
6. When style ranges overlap with conflicting attributes, produce a typed conflict.

This is exactly the behavior atlas `n11-range-resplit` requires.

**Phase 5 MVP scope**

The first version supports:

- `ParagraphStyleRange`
- `CharacterStyleRange`
- `Content`
- ordinary line breaks, spaces, and Unicode text

Not auto-merged for now:

- table
- footnote/endnote
- note/change tracking
- inline/anchored page item
- complex field/special character

When such content is encountered and both sides modify the same area, conservatively conflict.

**Phase 5 completion criteria**

- 07, 08, 08b meet expectations.
- Re-splitting `CharacterStyleRange` does not create false conflicts.
- extract → materialize → extract yields the same logical story.
- Unsupported Story content produces `UnsupportedStoryConstruct`, not silent loss.

---

## Phase 6: Package writing, CLI, and Git driver

### PR 18: Safe Writer

```scala
IdmlWriter.write(document, target, options): Try[WriteReport]
```

Process:

1. Create a temporary file in the target directory.
2. `mimetype` first, stored, no extra field.
3. Write unmodified entries as their original content bytes.
4. For dirty XML, use `XmlEnvelope` to restore the declaration/PI.
5. Preserve binary entries.
6. After finishing the ZIP, reopen and check.
7. Run `IdmlValidator`.
8. Run Relax NG when available.
9. On success, atomically replace the target.
10. On failure, keep the original file.

The current `Zip.writeEntry` (line 59) cannot be used to modify the final file entry by entry, because it is not a transactional writer.

**Signing and encryption**

Check on open:

```
META-INF/signatures.xml
META-INF/encryption.xml
META-INF/rights.xml
```

First version:

- signed: `UnsupportedSignedPackage`
- encrypted: `UnsupportedEncryptedPackage`
- do not auto-remove signatures
- do not emit a file that looks successful but whose signature is already invalidated

### PR 19: CLI

```
idml-merge inspect file.idml
idml-merge validate file.idml
idml-merge diff base.idml modified.idml
idml-merge merge base.idml left.idml right.idml --output merged.idml
idml-merge git-driver %O %A %B %P
```

Recommended exit codes:

- 0: clean merge
- 1: a merge conflict was normally found
- 2: invalid input
- 3: unsupported version, signing, or encryption
- >128: used only for a genuine program crash

### PR 20: Conflict sidecar / resolver contract

Define versioned JSON:

```scala
case class ConflictReport(
  schemaVersion: Int,
  inputHashes: InputHashes,
  conflicts: Vector[MergeConflict],
  diagnostics: Vector[Diagnostic]
)
```

Do not insert unknown conflict elements into standard IDML XML, and do not duplicate two nodes with the same Self.

The first-version CLI can produce:

```
document.idml.conflicts.json
```

The sidecar location for the Git driver needs a separate ADR:

- next to the working tree: easy to find, but Git will not auto-stage it
- `.git/idml-merge/`: does not pollute the working tree, but the resolver must know the location
- system temp directory: easily lost

It is recommended that the first version of the Git driver write to `.git/idml-merge/<path-hash>/conflicts.json` and print the resolver command to stderr.

### PR 21: Git integration

`.gitattributes`:

```
*.idml binary merge=idml3way
```

Installer script configuration:

```
[merge "idml3way"]
    name = IDML structural three-way merge
    driver = idml-merge git-driver %O %A %B %P
    recursive = binary
```

Tests must use a real temporary Git repository rather than calling the CLI directly as a substitute for Git.

**Phase 6 completion criteria**

- A clean merge atomically overwrites `%A` and returns 0.
- A conflict does not corrupt the original `%A`, returns 1, and produces a conflict report.
- When the driver is not installed, Git treats the file as a binary conflict rather than a text merge.
- The output ZIP satisfies UCF/IDML structural requirements.
- InDesign can open and re-export all clean corpus outputs.

---

## Detailed test plan

### 1. The corpus oracle needs extending

The current `expected.json` describes only operations and conflicts and cannot validate the final document. It is recommended that `schemaVersion` 2 add:

```json
{
  "expectedStatus": "accept",
  "expectedState": {
    "siblingOrders": {
      "Spread/r1": ["r1", "rL", "r2", "rR", "g1"]
    },
    "attributes": {
      "tf1@NextTextFrame": "tfX"
    },
    "storyText": {
      "story1/P2": "The swift brown fox jumps over the sleepy dog."
    },
    "presentSelf": ["rL", "rR"],
    "absentSelf": []
  },
  "expectedConflicts": []
}
```

Conflict cases should assert:

- conflict type
- target
- left/right/base state
- the relevant part and node locator
- not merely assert `expectedStatus=conflict`

### 2. Enable the 15 cases in phases

| Phase | Cases that must turn green |
| --- | --- |
| Phase 1 | all 15 load and validate |
| Phase 2–3 | 01, 02, 03, 04, 05, 06, 09, 10 |
| Phase 4 | 06b, 11, 12, 13 |
| Phase 5 | 07, 08, 08b |
| Phase 6 | all 8 accept cases produce openable IDML; all 7 conflict cases produce structured reports |

### 3. Merge algebra

Must include:

```
merge(B, B, B) = B
merge(B, L, B) = L
merge(B, B, R) = R
merge(B, L, L) = L
```

- repeated runs on the same input give the same result
- merging a clean result again produces no new edits

If the policy claims symmetry:

```
merge(B, L, R) ≡ merge(B, R, L)
```

If same-gap uses left-before-right, it must be explicitly registered as an asymmetry exception, and tests must verify the exception affects only the ordering of equivalent inserts, not conflict judgment.

### 4. Property tests

Introduce ScalaCheck in Phase 2:

- Randomly generate ordered trees with unique Self.
- Randomly generate mutually disjoint insert/delete/update/move.
- A clean merge must preserve all non-conflicting edits from both sides.
- Each node in the output has exactly one parent.
- The parent graph is acyclic.
- Siblings are not duplicated.
- All reference targets exist.
- n=1, 3, 10, 30 use the same properties.

### 5. Real InDesign acceptance

A synthetic corpus cannot replace real exports. Build a second `indesign-corpus`:

- the same InDesign version
- each branch reopened from the same closed base
- record the InDesign build, DOMVersion, OS, and operation steps
- cover at least move, group/ungroup, thread insert, and paragraph/style edit
- open the merge output in InDesign
- re-export IDML
- compare the logical model, not the ZIP bytes
- export PDF for visual regression
- check preflight, overset text, missing link/font, and similar states

---

## Recommended PR order

```
PR 0   documentation, policy ADR, CI/JDK/sbt
PR 1   ArchiveEntry + XmlEnvelope + IdmlDocument
PR 2   PartKey + SelfIndex + package validation
PR 3   parameterized corpus loader + expected.json decoder
PR 4   NodeContext + new DocumentEdit types
PR 5   sibling diff + insert/delete/move/reparent
PR 6   constraint graph + stable topological sort
PR 7   typed conflicts + ReconciliationPlan
PR 8   DocumentMaterializer + MergeResult
PR 9   ReferenceGraph + thread repair
PR 10  StoryList/ParentStory + cross-node conflicts
PR 11  Story logical extraction + paragraph alignment
PR 12  token/character diff3 + style span projection
PR 13  transactional IDML writer + validation
PR 14  CLI + conflict JSON
PR 15  Git merge driver + temporary-repository tests
```

Final Definition of Done:

- 15/15 corpus behaviors correct.
- 8 accept cases produce valid IDML.
- 7 conflict cases return accurate, serializable typed conflicts.
- No duplicate Self, no dangling references, no parent/thread cycles.
- The same input gives a deterministic result.
- All clean outputs can be opened and re-exported by the same InDesign version and pass semantic and visual acceptance.
