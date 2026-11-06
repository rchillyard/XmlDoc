# 08-same-para-overlap

## Purpose

Overlapping edits in one paragraph. This fixture is expected to **conflict**: Report text-overlap.

## Contract and evidence

Profile: `strict-text-node-v1`. Schema: `2`. Sources: paper, project.
Basis: §2.3 text nodes, p3; §6.3 Update/Update, p8.
Structured targets, identity matching and exact business conflict objects (including base/left/right) are in `expected.json`.
Successful cases compare the complete package entry inventory: changed XML uses the listed `expected/` snapshots; unchanged entries use the exact base bytes. No expected output is obtained from a merger. Domain reports are not claimed to exist in the current implementation.

## Baseline document

The case uses the normalized corpus page: `r1`, `r2`, and group `g1` (children `r3` and `r4`), plus text frames `tf1`, `tf2`, `tfA`, and `tfB`. `tf1` and `tf2` initially form a thread over the four-paragraph `story1`.

## Expected XML snapshots

None: conflict cases have no successful XML output.

## InDesign-equivalent operations

1. Close the common base document.
2. Reopen the base and perform the left operation: Replace quick with swift.
3. Save/export that branch as `left.idml`, then close it.
4. Reopen the unchanged base and perform the right operation: Replace quick brown with slow red.
5. Save/export that branch as `right.idml`, then close it.

## Fixture construction record

The checked-in files are deterministic XML-derived fixtures. `base.idml` is built from `HelloWorld2.idml`; `left.idml` and `right.idml` begin as exact in-memory copies of that case base before the operations above are expressed in the relevant IDML XML parts. No InDesign GUI automation was executed. The package-level hashes and expected edits/conflicts are recorded in `expected.json`.
