# MR feature branches against V1_1_3-Kaining-2

Assessed 2026-11-06 against `V1_1_3-Kaining-2` at 6e192a4. Only trial merges were run
(`git merge-tree --write-tree`), so no branch was changed.

## Summary

Every `feature/IDML-XML-Merge-Tests-MR*` branch's content is already in `V1_1_3-Kaining-2`,
so there is nothing left to merge.

MR04, MR05, MR08, MR10 and MR12 were merged directly. MR02, MR03, MR06, MR07, MR09 and
MR11 were rebased onto the trunk under new commit IDs:

| Work | Original commit | Commit on trunk |
|---|---|---|
| MR02 | 7ea26fb | 9b57e53 |
| MR03 | 0ebbafc, d047951 | ebaea3e, c954320 |
| MR06 | 56d5aba | 868d791 |
| MR07 | 4ec2414 | 6927463 |
| MR11 | ce5ae98 | e138474 |
| MR09 | a552d2d | 5f7fdd8 |

The other branches still hold the pre-rebase originals, and that is where the conflicts
come from. Each original is either the same patch as its trunk commit or an older version
that the trunk has since replaced.

## Branch by branch

| Branch | What it is | Merging into trunk | What it would still add |
|---|---|---|---|
| MR04, MR05, MR08, MR10, MR12 | Node correspondence tests, PCS encoding tests, mixed content, generated merge invariants, atomic package writes | Already merged | Nothing |
| MR03 (MR02 and MR01 contain it) | MR02 semantic XML assertions and MR03 corpus schema 3. MR02 and MR01 add only the PR #78 and #81 merge commits. | Conflict: `xml-merge-test-spec.md` | An older status line |
| MR11, MR0311 | MR11 reference validation (MR0311 = MR03 + MR11) | Conflict: `xml-merge-test-spec.md`, `IdmlPackageMergerSpec.scala` | Older status wording |
| MR06 | Original MR06 and MR07 commits | Conflict: `PcsMerger`, `PcsMergerSpec`, `ThreeWayMerger`, `ThreeWayMergerSpec` | MR06's `resolvedPcs` link pruning, which the trunk replaced with MR08's `withoutObsoleteBaseLinks`; older `ThreeWayMerger` signatures |
| MR030708 | MR03 + MR06 + MR07 + MR08 | Conflict: `PcsMerger`, `PcsMergerSpec`, `ThreeWayMerger`, `xml-merge-test-spec.md`, plus the hidden duplicate below | Same as MR06 |
| MR09 | MR030708 + the original MR09 commit | Conflict: `PcsMerger`, `ThreeWayMerger`, `xml-merge-test-spec.md`, corpus `README.md`, `CorpusExpectations`, `ThreeWayCorpusSpec`, `generate_three_way_corpus.py`, plus the hidden duplicate below | Older "blocked on MR11/MR12" wording and an oracle filter that keeps only generic assertions; 5f7fdd8 replaced both |

Between the branches themselves, MR11 conflicts with MR01, MR02, MR03, MR030708 and MR09, all
in `xml-merge-test-spec.md`. Every other pair merges cleanly or one branch already contains the
other.

## Hidden problem in MR030708 and MR09

Git merges `idml/src/test/scala/.../ThreeWayMergerSpec.scala` without reporting a conflict.
The result, though, has a second copy of the "MR06 compatible edits with complete results"
test block near line 140. The trunk already has that block at line 252. The duplicate
`private def group` and `assertCompatible` definitions would stop the tests compiling.

## Recommendation

Don't do a normal merge of any of these branches. Either:

- **Delete the 13 branches**, or
- **Keep them in history** with an `-s ours` merge of MR01, MR0311, MR06 and MR09, run on
  `V1_1_3-Kaining-2`. Those four tips contain all the other unmerged branches. An
  `-s ours` merge marks a branch as merged without changing any files:

  ```bash
  git merge -s ours origin/feature/IDML-XML-Merge-Tests-MR01 origin/feature/IDML-XML-Merge-Tests-MR0311 origin/feature/IDML-XML-Merge-Tests-MR06 origin/feature/IDML-XML-Merge-Tests-MR09
  ```

If you merge for real anyway, go MR01, then MR0311, MR06 and MR09. Resolve every conflict by
keeping the trunk's version, then delete the duplicated block in `ThreeWayMergerSpec.scala`
by hand.
