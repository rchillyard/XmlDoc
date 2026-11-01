# Reliable Three-Way IDML Merge Corpus

This directory maps all 15 requested merge scenarios (13 main numbers plus the `06b` and `08b` variants) to concrete `base / left / right / expected` fixtures. The wording below is the English version of the source table.

## Reliability invariant

For every case, both branches are created from an exact copy of the same closed-state `base.idml`. Existing `Self` values are never regenerated. A branch may remove a node or add a new unique node, but all unchanged nodes retain their base identity. This avoids the live-session `Self` drift observed in the old `HelloWorld2A/B/C` samples.

The fixtures are generated from the XML in `../HelloWorld2.idml`; they model the listed InDesign operations directly in IDML and are not represented as hand-exported GUI artifacts. From the repository root, run `python3 idml/tools/generate_three_way_corpus.py --verify` to rebuild and verify the corpus.

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
| `08b-same-para-disjoint` | Disjoint edits in one paragraph | Replace quick with swift. | Replace lazy with sleepy. | Accept at token granularity; conflict at paragraph-atom granularity. |
| `09-zorder` | Z-order changes | Move r1 one step forward (after r2). | Move r2 one step backward (before r1). | Accept both; report a conflict only if constraints contradict. |
| `10-group-ungroup` | Group/ungroup and child movement | Ungroup g1. | Move child r3 inside g1. | Report structural-parent-change. |
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
```

`expected.json` declares accepted edits and conflicts independently. A case with no conflicts has status `accept`; a case with at least one conflict has status `conflict`. The per-case README records reproducible InDesign-equivalent steps and the actual XML-derived construction method.

## Merge policy clarified by the table

- Independent inserts and edits to different paragraphs are accepted.
- Compatible z-order constraints are accepted and must produce deterministic order.
- Divergent moves, delete/modify pairs, overlapping text edits, structural reparenting, and incompatible references are reported as conflicts.
- Disjoint edits in one paragraph are accepted by a token-aware merger; a paragraph-atomic merger may report a conflict instead.
- Thread changes must update both ends of `PreviousTextFrame` / `NextTextFrame` references.
