# Reliable Three-Way IDML Merge Corpus

This directory maps all 16 merge scenarios (the original 15 cases plus `10b-divergent-parent`) to concrete `base / left / right / expected` fixtures. The wording below is the English version of the source table.

## Authoritative specification

The target contract is [IDML XML Merge Test Specification](../../../../../../../../docs/xml-merge-test-spec.md). This corpus validates test data, not current merger conformance. Run Scala contract checks with `sbt "idml/testOnly *CorpusContractSpec"`; run verifier corruption tests with `python3 -m unittest discover -s idml/tools -p 'test_three_way_corpus.py'`.

## Reliability invariant

For every case, both branches are created from an exact copy of the same closed-state `base.idml`. Existing `Self` values are never regenerated. A branch may remove a node or add a new unique node, but all unchanged nodes retain their base identity. This avoids the live-session `Self` drift observed in the old `HelloWorld2A/B/C` samples.

The fixtures are generated from the XML in `../HelloWorld2.idml`; they model the listed InDesign operations directly in IDML and are not represented as hand-exported GUI artifacts. From the repository root, run `python3 idml/tools/generate_three_way_corpus.py --verify` for **read-only** verification. Use `--generate` explicitly to rebuild, or `--generate --output /tmp/idml-corpus` for isolated regeneration. `--verify-only` remains a read-only alias; no arguments also verifies. Generation and verification flags cannot be combined.

## Case mapping

| Directory | Requirement | Left branch | Right branch | Expected merge result |
|---|---|---|---|---|
| `01-single-add` | Single-sided insertion | Insert r5 after r2. | No edit. | Accept the insertion. |
| `02-single-delete` | Single-sided deletion | Delete r2. | No edit. | Accept the deletion; repair references if the target participates in a thread. |
| `03-single-move` | Single-sided move | Move r1 to the top of the page-item stack. | No edit. | Accept the move. |
| `04-both-add-diff-pos` | Both sides insert at different positions | Insert rL after r1. | Insert rR after r2. | Accept both insertions in deterministic order. |
| `05-move-move-divergent` | Both sides move the same node | Move r1 to the top. | Move r1 to the bottom. | Report move-move. |
| `06-delete-vs-modify` | One side deletes; the other modifies | Delete r2. | Change r2 fill to red. | Report delete-modify. |
| `06b-delete-thread-vs-content` | Thread deletion versus story content | Delete tf2 and repair tf1.NextTextFrame. | Edit story1 paragraph 2. | Report a structural/content conflict. |
| `07-same-story-diff-para` | Different paragraphs in the same Story | Edit paragraph 2. | Edit paragraph 4. | Accept both edits. |
| `08-same-para-overlap` | Overlapping edits in one paragraph | Replace quick with swift. | Replace quick brown with slow red. | Report text-overlap. |
| `08b-same-para-disjoint` | Disjoint edits in one paragraph | Replace quick with swift. | Replace lazy with sleepy. | Report text-node update/update under strict-text-node-v1. |
| `09-zorder` | Z-order changes | Move r1 one step forward (after r2). | Move r2 one step backward (before r1). | Accept both; report a conflict only if constraints contradict. |
| `10-group-ungroup` | Reparenting with an independent content update | Ungroup g1. | Change r3 ItemTransform while it remains inside g1. | Accept both reparenting and the r3 attribute update (generic XML only). |
| `10b-divergent-parent` | Two genuine edits assign different new parents | Ungroup g1; promote r3 and r4 to Spread. | Move r3 from g1 into g2, after r6. | Report Position/Position: r3 has two incompatible new parents. |
| `11-thread-insert` | Cross-node reference insertion | Insert tfX between tf1 and tf2. | No edit. | Accept and update reciprocal references. |
| `12-next-divergent` | Divergent NextTextFrame references | Set tf1.NextTextFrame=tfA. | Set tf1.NextTextFrame=tfB. | Report reference-divergence. |
| `13-parentstory-vs-content` | ParentStory reference versus story content | Set tf2.ParentStory=story2 and detach it from tf1. | Edit story1 content that previously flowed into tf2. | Report reference-divergence. |

## Directory contract

Each case contains:

```text
case-name/
  base.idml
  left.idml
  right.idml
  expected.json
  README.md
  expected/  # changed XML snapshots, success cases only
```

`expected.json` uses schemaVersion 3 and the only enabled profile `strict-text-node-v1`. Schemas 1 and 2 are rejected with an explicit migration error. Schema 3 corrects case 10 to accept (no input bytes changed) and adds the genuine parent-conflict case 10b. The fixed status inventory is 8 accept and 8 conflict. `08b` was migrated from historical token-aware accept to text-node conflict as frozen in MR01. Each document records provenance, structured targets, base identities, explicit selfless matches, hashes, migration and the executable oracle. `oracle.entries` is the complete successful ZIP entry inventory; snapshot paths resolve relative to the case directory. `oracle.assertions` contains exact business conflict objects; generic slot mapping is MR09, compatible edits and divergent-parent checks are MR06/MR07, and cross-part domain implementation is MR11/MR12. Unknown profiles, incomplete fields and inconsistent status are rejected.

`expected.json` declares accepted edits and conflicts independently. A case with no conflicts has status `accept`; a case with at least one conflict has status `conflict`. The per-case README records reproducible InDesign-equivalent steps and the actual XML-derived construction method.

## Merge policy clarified by the table

- Independent inserts and edits to different paragraphs are accepted.
- Compatible z-order constraints are accepted and must produce deterministic order.
- Divergent moves, delete/modify pairs, overlapping text edits, incompatible structural reparenting, and incompatible references are reported as conflicts.
- Different edits to the same text node conflict, even at disjoint word ranges. Token-aware merging is deferred; a runner must not accept either result interchangeably.
- `10` accepts reparenting plus the independent ItemTransform update, per paper §2.1 and §3.1 rules 4/7. Its old forced conflict rule has been withdrawn. XML success does not prove the intended visual position in InDesign.
- `10b` requires the genuine parent conflict for r3: base=g1, left=ud1 (Spread), right=g2. Other low-level relation contradictions are not ruled out or silently discarded.
- `06b` and `13` use MR01 Story-level dependency rules, not inferred character-to-frame layout.
- Thread changes must update both ends of `PreviousTextFrame` / `NextTextFrame` references.

## MR09 executable regression

Run `sbt "idml/testOnly *ThreeWayCorpusSpec"`. All 16 directories are required;
32 named tests execute normal and swapped orders in independent temporary copies.
The integrated MR11/MR12 domain reports are now compared exactly as well;
`ThreeWayPackageCorpusSpec` independently verifies all 32 driver/ZIP permutations.
For one case, use `sbt 'idml/testOnly *ThreeWayCorpusSpec -- -z 10b-divergent-parent'`.
Two additional negative tests reject an incomplete directory inventory, damaged JSON
and an unexpected conflict. Existing contract tests cover hashes, profiles and missing fields.

| Case | Generic expectation | Domain acceptance |
|---|---|---|
| 01-single-add | complete candidate package | not required |
| 02-single-delete | complete candidate package | not required |
| 03-single-move | complete candidate package | not required |
| 04-both-add-diff-pos | complete candidate package, including indentation | not required |
| 05-move-move-divergent | complete predecessor/successor conflict set | not required |
| 06-delete-vs-modify | exact delete/update report | not required |
| 06b-delete-thread-vs-content | exact integrated domain conflict | implemented by MR11/MR12 |
| 07-same-story-diff-para | complete candidate package | not required |
| 08-same-para-overlap | exact atomic text conflict | not required |
| 08b-same-para-disjoint | exact atomic text conflict | not required |
| 09-zorder | complete candidate package | not required |
| 10-group-ungroup | complete candidate package, reparenting and transform retained | not required |
| 10b-divergent-parent | complete parent conflict set, g1/ud1/g2 | not required |
| 11-thread-insert | complete candidate package | integrated MR11/MR12 reference validation |
| 12-next-divergent | exact NextTextFrame attribute conflict | integrated MR11/MR12 reference validation |
| 13-parentstory-vs-content | exact integrated domain conflict | implemented by MR11/MR12 |

`CorpusContract` validates schema 3 and input/snapshot digests before test registration.
`CorpusExpectations` maps the reviewed business assertions to generic report slots;
it never calls a merger to derive expected results. For case 05 the page-item-only
context omits Page `ud6` and formatting text, so the adapter explicitly includes
those actual PCS boundaries. All generic reports are compared as full multisets,
including slots, base and branch values; swapping changes only left/right values.

For accepted cases, every declared part is compared in full, along with the complete
logical ZIP entry inventory and unchanged entries' bytes. No fixture or temporary
input may change. Part outcomes are overlaid in memory; writing/reopening ZIPs,
patching designmap and failure atomicity are also exercised by the integrated MR12
package/driver suite. Full generic and domain reports are checked for 06b/13.
InDesign application evidence remains MR14 work.

Formatting-node identity uses its following matched element only for strictly
alternating whitespace/element lists. Whitespace payloads are preserved and compared;
body/mixed text keeps the ordinary text-node identity and strict atomic policy.
