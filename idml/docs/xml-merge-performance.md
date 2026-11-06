# MR13 XML merge performance

Status: implementation plan; no measurements claimed. Branch: `V1_1_3-Kaining-2`.

## Protocol fixed before implementation (2026-11-06)

- Standalone command: `sbt 'idml/Test/runMain com.phasmidsoftware.xmldoc.idml.MergeBenchmark <output.json> <calibration|formal>'`.
- Candidate matrix: 32, 128, 512 element nodes; seeds 13, 37, 73; editing budgets 0, 4, 16
  (16 only for sizes 128/512). One separate 32-node, 64-KiB text-per-leaf experiment.
- Insert/delete/move/update are drawn with equal probability from a seeded PRNG.
  Each accepted operation owns a disjoint subtree and sibling context; illegal/reused contexts
  are rejected and counted. Operation counts and detected PCS edit counts are distinct.
- Calibration: one warmup, two measured repetitions. After recording it, freeze limits here
  before formal measurement. Formal: three warmups and five measured repetitions.
- Keep parsed input and matches ready before timing reconciliation/reconstruction. Report XML
  parse, matching, prematched relation merge/rebuild, package writing, total wall time and sampled
  JVM heap peak (plus memory-pool peak). Sampling is not an OS RSS measurement.
- Verify every output against an independent recipe before recording a successful measurement.
  Serialize candidate parts into a minimal ZIP and reopen/compare it; driver correctness remains
  separately covered by MR12/MR14. No implementation optimizations belong in this benchmark.
- Report all raw repetitions, min/median/p95/max, actual operation mix/rejections, environment,
  JVM parameters, seed, node counts and detected E. Fit merge time to c0+c1*n+c2*e using all
  measured inputs; report conditioning/rank failures and residuals. This does not prove complexity.
- Calibration and formal artifacts live under `idml/docs/evidence/`; default unit tests never run
  the benchmark. Formal thresholds and environment will be frozen after calibration, before formal.

## Calibration result and gates frozen before formal run

Initial calibration: 25 scenarios, two repetitions; maximum total 29.575 ms, pure merge/rebuild
21.810 ms, sampled JVM heap 719 MiB. Environment: macOS aarch64, 10 processors, Java
21.0.12.1, OpenJDK 64-Bit Server VM, `-Xms1024m -Xmx1024m -Xss4M`.

Formal limits fixed now: each repetition total <=250 ms, prematched merge/rebuild <=100 ms,
heap peak <=960 MiB; per-scenario median merge <=4 times calibrated median (denominator floor
1 ms). The floor avoids meaningless ratios on sub-millisecond trees. These are this runner's
regression limits, not production service guarantees. Formal failures remain blockers.

Add `idml/tools/summarize_merge_benchmark.py` for standard-library distribution reporting,
rank-checked least squares and gate verification. Raw JSON remains authoritative. Calibration
will be rerun with the final metadata/gate harness before its digest is frozen; limits above
will not be raised in response to formal results.
