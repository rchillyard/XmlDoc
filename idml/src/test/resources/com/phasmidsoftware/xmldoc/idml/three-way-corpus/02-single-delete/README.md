# 02-single-delete

## Purpose

Single-sided deletion. This fixture is expected to **accept**: Accept the deletion; repair references if the target participates in a thread.

## Contract and evidence

Profile: `strict-text-node-v1`. Schema: `2`. Sources: paper, idml.
Basis: §3.1 rule 6, p4; reference integrity is IDML policy.
Structured targets, identity matching and exact business conflict objects (including base/left/right) are in `expected.json`.
Successful cases compare the complete package entry inventory: changed XML uses the listed `expected/` snapshots; unchanged entries use the exact base bytes. No expected output is obtained from a merger. Domain reports are not claimed to exist in the current implementation.

## Baseline document

The case uses the normalized corpus page: `r1`, `r2`, and group `g1` (children `r3` and `r4`), plus text frames `tf1`, `tf2`, `tfA`, and `tfB`. `tf1` and `tf2` initially form a thread over the four-paragraph `story1`.

## Expected XML snapshots

- `expected/Spreads/Spread_ud1.xml`

## InDesign-equivalent operations

1. Close the common base document.
2. Reopen the base and perform the left operation: Delete r2.
3. Save/export that branch as `left.idml`, then close it.
4. Reopen the unchanged base and perform the right operation: No edit.
5. Save/export that branch as `right.idml`, then close it.

## Fixture construction record

The checked-in files are deterministic XML-derived fixtures. `base.idml` is built from `HelloWorld2.idml`; `left.idml` and `right.idml` begin as exact in-memory copies of that case base before the operations above are expressed in the relevant IDML XML parts. No InDesign GUI automation was executed. The package-level hashes and expected edits/conflicts are recorded in `expected.json`.
