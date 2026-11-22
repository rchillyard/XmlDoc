# 10b-divergent-parent

## Purpose

Two genuine edits assign different new parents. This fixture is expected to **conflict**: Report Position/Position: r3 has two incompatible new parents.

## Contract and evidence

Profile: `strict-text-node-v1`. Schema: `3`. Sources: paper.
Basis: §5 unique parent, p6; §6.3 Position/Position, p8: two incompatible parent edits.
Structured targets, identity matching and exact business conflict objects (including base/left/right) are in `expected.json`.
Successful cases compare the complete package entry inventory: changed XML uses the listed `expected/` snapshots; unchanged entries use the exact base bytes. No expected output is obtained from a merger. Domain reports are not claimed to exist in the current implementation.

## Baseline document

The case uses the normalized corpus page: `r1`, `r2`, and group `g1` (children `r3` and `r4`), plus text frames `tf1`, `tf2`, `tfA`, and `tfB`. `tf1` and `tf2` initially form a thread over the four-paragraph `story1`. For 10b only, g2 (containing r6) is inserted immediately after g1 and before tf1 in the common base. Left removes g1 and promotes r3/r4 at its position; right leaves r4 in g1 and appends r3 after r6 in g2.

## Expected XML snapshots

None: conflict cases have no successful XML output.

## InDesign-equivalent operations

1. Close the common base document.
2. Reopen the base and perform the left operation: Ungroup g1; promote r3 and r4 to Spread.
3. Save/export that branch as `left.idml`, then close it.
4. Reopen the unchanged base and perform the right operation: Move r3 from g1 into g2, after r6.
5. Save/export that branch as `right.idml`, then close it.

## Fixture construction record

The checked-in files are deterministic XML-derived fixtures. `base.idml` is built from `HelloWorld2.idml`; `left.idml` and `right.idml` begin as exact in-memory copies of that case base before the operations above are expressed in the relevant IDML XML parts. No InDesign GUI automation was executed. The package-level hashes and expected edits/conflicts are recorded in `expected.json`.

Case note: g2 and r6 exist in the common base. Required parent conflict is base=g1, left=ud1, right=g2; any additional PCS contradictions must be derived, not suppressed to force one low-level report.
