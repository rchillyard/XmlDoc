# 03-single-move

## Purpose

Single-sided move. This fixture is expected to **accept**: Accept the move.

## Baseline document

The case uses the normalized corpus page: `r1`, `r2`, and group `g1` (children `r3` and `r4`), plus text frames `tf1`, `tf2`, `tfA`, and `tfB`. `tf1` and `tf2` initially form a thread over the four-paragraph `story1`.

## InDesign-equivalent operations

1. Close the common base document.
2. Reopen the base and perform the left operation: Move r1 to the top of the page-item stack.
3. Save/export that branch as `left.idml`, then close it.
4. Reopen the unchanged base and perform the right operation: No edit.
5. Save/export that branch as `right.idml`, then close it.

## Fixture construction record

The checked-in files are deterministic XML-derived fixtures. `base.idml` is built from `HelloWorld2.idml`; `left.idml` and `right.idml` begin as exact in-memory copies of that case base before the operations above are expressed in the relevant IDML XML parts. No InDesign GUI automation was executed. The package-level hashes and expected edits/conflicts are recorded in `expected.json`.
