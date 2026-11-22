# 04-both-add-diff-pos

## Purpose

Both sides insert at different positions. This fixture is expected to **accept**: Accept both insertions in deterministic order.

## Contract and evidence

Profile: `strict-text-node-v1`. Schema: `3`. Sources: paper.
Basis: §3.1 rule 5; §5, p6.
Structured targets, identity matching and exact business conflict objects (including base/left/right) are in `expected.json`.
Successful cases compare the complete package entry inventory: changed XML uses the listed `expected/` snapshots; unchanged entries use the exact base bytes. No expected output is obtained from a merger. Domain reports are not claimed to exist in the current implementation.

## Baseline document

The case uses the normalized corpus page: `r1`, `r2`, and group `g1` (children `r3` and `r4`), plus text frames `tf1`, `tf2`, `tfA`, and `tfB`. `tf1` and `tf2` initially form a thread over the four-paragraph `story1`.

## Expected XML snapshots

- `expected/Spreads/Spread_ud1.xml`

## InDesign-equivalent operations

1. Close the common base document.
2. Reopen the base and perform the left operation: Insert rL after r1.
3. Save/export that branch as `left.idml`, then close it.
4. Reopen the unchanged base and perform the right operation: Insert rR after r2.
5. Save/export that branch as `right.idml`, then close it.

## Fixture construction record

The checked-in files are deterministic XML-derived fixtures. `base.idml` is built from `HelloWorld2.idml`; `left.idml` and `right.idml` begin as exact in-memory copies of that case base before the operations above are expressed in the relevant IDML XML parts. No InDesign GUI automation was executed. The package-level hashes and expected edits/conflicts are recorded in `expected.json`.

Case note: The merged sibling order must be deterministic and preserve both anchors.
