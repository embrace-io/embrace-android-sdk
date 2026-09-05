# Shadow run follow-up (2026-09-05)

What was done with the (c) findings in [REPORT.md](REPORT.md), plus the two device checks that could
not be shadow-run offline. Every change is in `startup-tools` (and, where the Python still had a
counterpart, mirrored there) and recorded in `startup-tools/PORT-LOG.md`.

| finding | disposition | port log |
|---|---|---|
| C1 `probe` hint line names Kotlin commands | kept; the #7 decision applied to the hint | #31 |
| C2 `outlier-factors` wrote `null` for absent scalars | **fixed**: every scalar on `OutlierFactors.Record` is `@EncodeDefault(NEVER)`; re-run over the mid-b fixture pass: 20 records, 0 key-set differences, 0 value differences against the Python artifact | #28 |
| C3 4-space JSON in `reference-set --show` / submit preview | **fixed**: one-space indent like `json.dumps(indent=1)` | #30 |
| C4 `ingest` device_profile shape | **decided**: `{}` when absent (was seven nulls); a nested cell-state `profile` is unwrapped and `tier`/`cool_gate_c` are not stored | #29 |
| C5 Python fixed temp SQL paths lose windows under concurrency | Python defect; Kotlin already unaffected | #32 |
| C6 `parse_errors` 0 vs 5 under `composed` | consequence of #5; the Kotlin count is the true one | #33 |

Device checks run the same day (`device/`, `paired.log`, `summary.json`):

- One `fleet-campaign` pass each with the Python and the Kotlin runner on the Pixel 3 (`coldStartup`,
  50 launches each): same artefact set (traces, gradle log, benchmark data, EmbVerify capture, cohorts),
  identical cohort classification (50 launches, 1 created, 48 restored, 1 unknown, violation at
  iteration 1 on both), and the Kotlin additionally writes `run-metadata.json` (#16). Cross-ingest: both
  toolchains ingest both runs to the same summary (`n=50 median=30.7 p90=31.4 max=33.1` for the Python
  run, `n=50 median=30.3 p90=31.1 max=31.6` for the Kotlin run).
- That cross-ingest exposed **#27**: the Kotlin run-metadata labelled `coldStartup` as compile state `full`;
  it is now `default` (CompilationMode.DEFAULT), `full` is reserved for `coldStartupFullAot`, and the two
  user-session arms map to `profile`. `FleetCampaignTest` pins the mapping.
- `cohorts --method coldStartup` over the Kotlin run's EmbVerify capture: Python and Kotlin output
  byte-identical, both exit 1 (violations present). Both classifiers now also read the 9.3.0 attribute
  names `art-compile-filter` / `app-image-at-init`, falling back to `init-compile-filter` for older
  captures.
- Live trace-parity gate (`-PtraceParity=1`), run alone: PASS, 5 tests, 1045 s (trace-goldens
  MANIFEST). The first attempt was voided by a concurrent `:startup-tools:test` run sharing the
  results directory - do not overlap them.

Still requiring a human: the review of PORT-LOG departures #1-#33 as the contract the Kotlin now
carries alone, and the deletion commit itself (scripts, SQL under the skills, `_shared/*.py`, the
`.gitignore` Python rules, `reseed-august-2026.py` → documented command list, the MAXIMS.md renderer
sentence naming `_shared/maxims.py`, and the goldens' "regenerate with `dump_golden.py`" instructions
becoming a frozen-spec note).
