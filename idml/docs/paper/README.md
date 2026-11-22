# English paper draft

Prepared on 12 November 2026 from the research material in `origin/V1_1_3-Kaining-2` at `aa698b2`, plus the project's earlier commit history. Work is kept in the existing repository on `codex/three-way-merge-paper`.

## Files

- [Editable Word manuscript](three-way-merge-paper-en.docx)
- [Markdown manuscript](three-way-merge-paper-en.md)
- [Figure and Word builder](build_paper.py)
- [Recomputed descriptive statistics](paper-statistics.json)
- `figures/`: original SVG diagrams/charts and PNGs embedded in Word

The manuscript includes the research sequence, method, evaluation design, findings, limitations, references, MR01–MR14 trace, and reproduction notes. Author names, affiliations, acknowledgements, and a venue template have deliberately not been invented.

## Claim and source map

| Manuscript claim | Primary project evidence | Interpretation |
|---|---|---|
| Identity observations, collisions, session drift, cross-version scope | [IDML research log](../../MERGE.md), especially the HelloWorld2, Mergeable, and Magazine observations | Historical case observations, not a general identifier guarantee |
| PCS development and KML-driven corrections | [Core research log](../../../core/MERGE.md) and dated commits in Appendix A | Earlier log passages may be superseded by later fixes |
| Strict text policy, case 10 correction, new 10b, comparison and failure policies | [Frozen specification](../xml-merge-test-spec.md) | The final schema/profile governs the manuscript; old token-merge expectations are not current capability |
| Sixteen scenarios, eight accept and eight conflict | [Corpus README](../../src/test/resources/com/phasmidsoftware/xmldoc/idml/three-way-corpus/README.md) and its expected.json files | Generated XML-derived fixtures; not sixteen manually exported editing sessions |
| 946 Scala and 22 Python passes | [MR14 validation JSON](../evidence/mr14-validation.json), matched against the three archived log digests | Historical clean-run results at f2bca0a; one ignored Scala test separate; six acceptance tests are a subset |
| 25 scenarios and 125 formal observations | [Formal raw JSON](../evidence/mr13-formal.json), calibration and frozen gates | Historical source 9696666; Scala 3.8.4 and sbt 1.11.7; not a new measurement on the later Scala 3.9 branch |
| Table 2 and Figure 2 | [paper-statistics.json](paper-statistics.json), recomputed by the builder | Seeds pooled within workload groups; nearest-rank p95; observations are repeated timings |
| No completed real application acceptance | [Readiness evidence](../evidence/mr14-real-world-readiness.json) and [manifest](../../src/test/resources/com/phasmidsoftware/xmldoc/idml/real-world-corpus/manifest.json) | Zero supplied of four planned; exploratory exports elsewhere do not satisfy this gate |
| Archived remote regression success and release-evidence failure | [CI evidence JSON](../evidence/mr14-github-ci.json) | Observation of 84343cb, not an assertion about the moving branch today |

## Checks performed while preparing the manuscript

- Read implementation, research logs, frozen policy, corpus contracts, performance records, and relevant earlier project-chat decisions.
- Re-ran read-only corpus verification: 16 cases, schema 3, strict-text-node-v1.
- Re-ran the Python regression suite: 22 tests passed. Scala suites and timing experiments were not rerun for this writing task.
- Verified all three archived MR14 log hashes against mr14-validation.json.
- Verified the historical benchmark source hash, formal gate hash, and calibration-file hash.
- Rechecked all 125 formal observations and per-scenario median ratios against the frozen limits: no violations.
- Recomputed the archived summary: scenario statistics agree exactly; regression coefficients differ only at floating-point rounding precision, below 1e-12.
- Created native editable Word equations, repeated table headers, and figure alternative text. Rendered the Word file and inspected all pages.

## Before submission

1. Confirm authorship, affiliations, contributions, and the target venue's format and length.
2. Supply the four authorized same-version real triples and the required InDesign application records. Revise the results only after that evidence exists.
3. Expand related work for the chosen venue. The current reference set is focused on the sources directly used by this project; it is not a systematic literature review.
4. Add comparative baselines, ablations, and broader document sampling if the submission makes comparative algorithm or practical effectiveness claims.
5. Obtain permission for any externally supplied documents and publish a reproducible artifact release with the intended availability statement.

## Rebuilding the document

Run `build_paper.py` using the Codex bundled Python interpreter containing python-docx and reportlab. The script resolves bundled Node and sharp through `CODEX_DOCUMENT_RUNTIME`, defaulting to the desktop dependency directory. It reads archived measurements and does not execute a benchmark.

The generated Word document must be rendered and visually reviewed after any content or layout change. QA PDFs and page images are temporary inspection files, not additional paper deliverables.
