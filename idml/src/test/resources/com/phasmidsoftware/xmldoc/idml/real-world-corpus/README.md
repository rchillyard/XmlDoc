# Real exported IDML acceptance corpus

Status: **no authorized real triples or InDesign validation records supplied** (user confirmed
2026-11-06). `manifest.json` freezes four planned groups, not four completed tests. No synthetic
fixture is counted as real-world evidence. Release readiness must remain false until the strict
manifest check and actual application validation pass.

Follow [制作与验收指南](../../../../../../../../docs/idml-real-sample-guide.md).

For each planned directory supply base.idml, left.idml, right.idml, independent expected.idml
(for success) or expected-conflicts.json (for conflict), provenance.json and application.json.
The manifest must record input/oracle hashes, source authorization, exact InDesign version,
operation history, warning review and open/save/close/reopen artifacts. Use original work or
explicitly authorized assets; recording a filename is not evidence of authorization.

Run `python3 idml/tools/verify_real_world_manifest.py` as the strict release gate. An empty or
pending inventory fails that gate. `--allow-pending` only checks the planning contract and emits
releaseReady=false; it must never be presented as application acceptance.
