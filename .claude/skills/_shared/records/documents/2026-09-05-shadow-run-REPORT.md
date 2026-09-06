# Shadow run: Python startup skills vs `startup-tools` (Kotlin), 2026-09-04/05

Every command that can run offline (plus the read-only device commands) was executed with BOTH
implementations over every real input on this machine, and the outputs were diffed. This is the
pre-cutover differential PORT-LOG.md asks for; nothing here was committed and no tracked file was
modified. Raw outputs: `raw/<command>/<input>.{py,kt}.txt` (stdout), `.{py,kt}.stderr.txt`, and every
file a command wrote under `raw/<command>/artifacts/`. Diffs: `compare/<command>/<input>.*`. Run
metadata (exact argv, env, exit code, wall time per side): `raw/manifest.jsonl`. The runner and the
comparer are one script, `tooling/shadow.py` (`python3 shadow.py probe|plan|run|compare`); `tooling/report.py`
assembled this file from `compare/summary.json` plus the hand-written `tooling/verdicts.json`.

## Setup

* Repo `hho/startup-tools-kotlin` at `a0b645545`, WORKING TREE as of 2026-09-04 19:30 local - which was not clean:
  `git status` during the run showed uncommitted (partly staged) changes to `.claude/skills/_shared/{cohorts.py,maxims.py,
  maxims/*}`, several SKILL.md/reference docs, `startup-tools/{README,PORT-LOG}.md`, `Main.kt`, `Cohorts.kt`,
  `FleetCampaign.kt`, `PyJson.kt`, the new `analysis/Maxims*.kt`, `cli/MaximsCommand.kt`, `store/MaximsLedger.kt` and the
  maxims fixtures. Both sides were exercised in that state (the shadow run never modified a tracked file), so the `maxims`
  and `cohorts` cells compare the uncommitted Python against the uncommitted Kotlin. Kotlin built once via
  `tools/startup --help`, then every call ran as `env STARTUP_TOOLS_NO_BUILD=1 tools/startup <command> ...` from the repo root.
* Python: `python3` = CPython 3.14.2 (Homebrew); scripts under `.claude/skills/`. The Python launcher
  `~/.cache/embrace-startup-tools/trace_processor/v46.0/trace_processor` (which runs the native
  `~/.local/share/perfetto/prebuilts/trace_processor_shell-98a41b80e9f60da0`, Perfetto v57.2) was passed
  explicitly as `--trace-processor` / positional `tp` wherever a script takes one. The Kotlin used its own pinned
  `~/.cache/embrace-startup-tools/trace_processor_shell/v57.2/trace_processor_shell` (also v57.2).
* Devices attached during the run (read-only adb only): Pixel 7 Pro `flagship-a` (flagship-a), Pixel 3 `mid-b`
  (mid-b), SM-A145M `mid-a` (mid-a), SM-A013G `entry-a` (entry-a).
* `--little-cpus` / `LITTLE_CPUS`: taken from the Kotlin `probe` topology JSON written at the start of the run
  (`little_cpus` = `0,1,2,3` on all four devices; the Python probe wrote the identical JSON). Both sides got the
  same value for every input.
* Both sides ran concurrently in a 6-worker pool (162 cells + 4 probes, 19:35-20:15 local); nothing in any output
  depends on wall-clock except the timestamps that are masked. EXCEPTION found during the run: `ingest_run.py` and
  `matrix_report.py` use fixed temp-file names and corrupt each other when run concurrently (finding C5 below), so every
  `ingest` and `matrix-report` cell was re-run strictly sequentially afterwards and the table reports the sequential results.

## Headline

* 166 cells (162 command x input pairs + 4 probes). 134 are identical after masking (stdout, exit code and every artifact).
* 5 cells differ only by a departure already in PORT-LOG.md (#3, #9, #20).
* 27 cells carry a difference NOT in the port log (23 verdict `c`, plus the 4 `ingest --force` cells whose verdict is `b+c`
  because they also carry the listed #5 and #8), all of six kinds (C1-C6
  below): a reworded probe hint, `null` keys in `passN-factors.json`, JSON indentation, the stored `device_profile` shape,
  a Python concurrency defect, and the `parse_errors` count under the composed instrument. None changes a number in any
  report, table, dataset value or window.
* One one-sided error (the Python `variance_analysis.py` crash on the Pixel 7 Pro fixtures, PORT-LOG #3). Every refusal
  (30 cells) was refused identically by both sides, exit code, stdout and stderr.
* Every report body over every trace directory and campaign - `analyze`, `variance` (11/12), `trace-health`,
  `hypothesis-tests`, `factors-report`, `cross-device-sections`, `maxims score`, `maxims render`, `trend`, `reproducibility`,
  `cohorts`, `matrix-report` (same slice), `reference-set --probe --check`, `artifact-sync list` - is line-for-line
  identical, and every JSON dataset (`passN.json` x 12, `passN-factors.json` x 12 values, trend verdicts x 8, matrix-plan
  cells x 2, matrix-report cells, cohorts x 2, probe topology x 4, probed reference set) is value-identical.

## Inputs

Trace directories (perfetto traces; 510 traces in 12 dirs, all SDK 9.2.0 cold launches):

| label | directory | device | traces |
|---|---|---|---|
| fx-flagship-a | claude-output/2026-08-26-kotlin-port/fixtures/traces/flagship-a/pass1 | flagship-a | 20 |
| fx-mid-a | .../fixtures/traces/mid-a/pass1 | mid-a | 20 |
| fx-mid-b | .../fixtures/traces/mid-b/pass1 | mid-b | 20 |
| val-mid-b-pass1 | .../2026-08-26-kotlin-port/validation/mid-b/pass1 | mid-b | 50 |
| sbs-mid-b-pass1..3 | .../validation/side-by-side/mid-b/pass{1,2,3} | mid-b | 3 x 50 |
| vfm-mid-b-pass1 | .../validation/vfm/mid-b__9.2.0__reference/pass1 | mid-b | 50 |
| fsf-arm-a..d | claude-output/2026-09-03-first-session-fix/arm-{a-9.2.0,b-fix,c-expired-hook,d-expired-hook-fix}/pass1 | mid-b | 4 x 50 |

Campaign directories (`passN.json`, `passN-factors.json`, `campaign.log`): the seven named in the task
(`campaign-2026-08-11`, `a14-campaign2-2026-08-11`, `pixel-campaign-2026-08-11`, `a01-campaign-2026-08-11`,
`p7p-campaign-2026-08-12`, `p7p-cooldown-2026-08-12`, `cooldown-2026-08-12/{a01,a14,pixel}`) plus, as extras,
`a01-mini-2026-08-12`, the trace-golden one-pass campaigns `2026-08-26-kotlin-port/goldens/trace/_campaign/{mid-b,mid-a,flagship-a}`,
and the golden inputs `goldens/inputs/campaign-{a,b}` (hypothesis-tests / factors-report only). Device keys for
`maxims score`: campaign-2026-08-11, a14-*, cooldown/a14 -> mid-a; pixel*, cooldown/pixel -> mid-b; a01*, cooldown/a01 -> entry-a;
p7p* -> flagship-a; every `maxims score` got `--sdk-version 9.2.0-SNAPSHOT-0811 --arm default --ledger none`.

Stores for `trend`: `claude-output/longitudinal/{store,sweep-store}.jsonl`, `startup-tools/.../goldens/inputs/trend-store.jsonl`,
`2026-08-26-kotlin-port/validation/store.jsonl`, `validation/side-by-side/store-{python,kotlin-final}.jsonl`,
`2026-08-16-x25-artifacts/{store,sweep-store}.jsonl`. Reference sets: `longitudinal/reference-set.json` (the one used for
ingest/maxims), the fixture copy, the two x25 reference sets, the maxims fixture reference set. Corpus:
`goldens/inputs/corpus.jsonl`. Plan: `plan-example.json` (skill copy and fixture copy). Matrix cell: `validation/vfm`
(the only directory on this machine holding a `cell-state.json` with traces). EmbVerify captures: the two
`pass1-embverify.log` under `2026-09-03-first-session-fix/arm-{c,d}-*` (method
`coldStartupBaselineProfileExpiredUserSession` from their `run-metadata.json`).

## Masking rules

Applied to stdout and to text artifacts before diffing; JSON artifacts are compared as trees with the same masks on string leaves.

1. Absolute paths: the scratch directory, the `raw/` directory, the repo root and `$HOME` are replaced by
   `<SCRATCH>`, `<RAW>`, `<REPO>`, `<HOME>`; the per-side artifact suffix `.py.<name>` / `.kt.<name>` and the
   per-side `analyze-out/{py,kt}/` directory become `<SIDE>`.
2. Timestamps: `YYYY-MM-DD HH:MM:SS`, `YYYY-MM-DDTHH:MM:SS[Z|.fff]` and the `YYYY-MM-DD-HHMMSS` file-name stamp become `<TS>`.
3. Nothing else. Numbers, table layout, whitespace and ordering are compared verbatim. JSON numbers are compared
   exactly (a float differing by less than 1e-9 relative would be reported separately as `FLOAT-ULP`).


## Command x input matrix

Verdicts: **a** identical (after the masking below); **b** a departure already listed in PORT-LOG.md (row cited); **c** a difference NOT in the port log; **err** one side errored. Cells marked `a` had identical stdout, equal exit codes and tree-identical artifacts.

| command | input | exit codes | comparison | verdict | note |
|---|---|---|---|---|---|
| probe | flagship-a | py=0 kt=0 | stdout 2 diff lines; topology.json=identical | c | C1: stdout hint sentence reworded (`pass --little-cpus 0,1,2,3 to variance_analysis.py, or set LITTLE_CPUS=0,1,2,3 for hypothesis_tests.py / factors_report.py` -> `pass --little-cpus 0,1,2,3 to variance, hypothesis-tests and factors-report`). Follows from PORT-LOG #7 but the probe's advice line is not listed there. The topology JSON is tree-identical. |
| probe | mid-b | py=0 kt=0 | stdout 2 diff lines; topology.json=identical | c | C1 (same rewording as flagship-a); topology JSON tree-identical. |
| probe | mid-a | py=0 kt=0 | stdout 2 diff lines; topology.json=identical | c | C1 (same rewording); topology JSON tree-identical. |
| probe | entry-a | py=0 kt=0 | stdout 2 diff lines; topology.json=identical | c | C1 (same rewording); topology JSON tree-identical. |
| analyze | fx-flagship-a | py=0 kt=0 | stdout identical; summary-dir=identical | a |  |
| analyze-all-sections | fx-flagship-a | py=0 kt=0 | stdout identical | a |  |
| variance | fx-flagship-a | py=1 kt=0 | stdout 92 diff lines; pass.json=identical; report.txt=py=MISSING kt=ok | b (#3) | ONE-SIDED ERROR, expected: variance_analysis.py dies with `statistics.StatisticsError: no median for empty data` (emb-record-startup emitted by no Pixel 7 Pro trace); the Kotlin renders the report with NaN for that section. The --json dataset written before the crash is tree-identical to the Kotlin's; the Kotlin --out report exists, the Python's does not. |
| outlier-factors | fx-flagship-a | py=0 kt=0 | stdout identical; factors.json=DIFF | c | C2: passN-factors.json key presence. The Kotlin writes `"freq_limit_cl0": null, "freq_limit_cl1": null` on all 20 iterations; the Python omits the key when the SQL row is [NULL]. No value differs. Root cause: core/json/Codecs `explicitNulls = true` + `encodeDefaults = true` (PORT-LOG #26, which scopes the change to store records and the two Derived fields; OutlierFactors.Record is not mentioned and its own doc comment says absence must stay absence). |
| trace-health | fx-flagship-a | py=0 kt=0 | stdout identical | a |  |
| trace-health-show-meta | fx-flagship-a | py=0 kt=0 | stdout identical | a |  |
| analyze | fx-mid-a | py=0 kt=0 | stdout identical; summary-dir=identical | a |  |
| analyze-all-sections | fx-mid-a | py=0 kt=0 | stdout identical | a |  |
| variance | fx-mid-a | py=0 kt=0 | stdout identical; pass.json=identical; report.txt=identical | a |  |
| outlier-factors | fx-mid-a | py=0 kt=0 | stdout identical; factors.json=identical | a |  |
| trace-health | fx-mid-a | py=0 kt=0 | stdout identical | a |  |
| trace-health-show-meta | fx-mid-a | py=0 kt=0 | stdout identical | a |  |
| analyze | fx-mid-b | py=0 kt=0 | stdout identical; summary-dir=identical | a |  |
| analyze-all-sections | fx-mid-b | py=0 kt=0 | stdout identical | a |  |
| variance | fx-mid-b | py=0 kt=0 | stdout identical; pass.json=identical; report.txt=identical | a |  |
| outlier-factors | fx-mid-b | py=0 kt=0 | stdout identical; factors.json=DIFF | c | C2: `"freq_cl0_mhz": null` written by the Kotlin on 19/20 iterations (Pixel 3 little-cluster clock counter absent inside the window); key absent in the Python. No value differs. |
| trace-health | fx-mid-b | py=0 kt=0 | stdout identical | a |  |
| trace-health-show-meta | fx-mid-b | py=0 kt=0 | stdout identical | a |  |
| analyze | val-mid-b-pass1 | py=0 kt=0 | stdout identical; summary-dir=identical | a |  |
| variance | val-mid-b-pass1 | py=0 kt=0 | stdout identical; pass.json=identical; report.txt=identical | a |  |
| outlier-factors | val-mid-b-pass1 | py=0 kt=0 | stdout identical; factors.json=DIFF | c | C2: `freq_cl0_mhz: null` on 30/50 iterations (Kotlin only); no value differs. |
| trace-health | val-mid-b-pass1 | py=0 kt=0 | stdout identical | a |  |
| analyze | sbs-mid-b-pass1 | py=0 kt=0 | stdout identical; summary-dir=identical | a |  |
| variance | sbs-mid-b-pass1 | py=0 kt=0 | stdout identical; pass.json=identical; report.txt=identical | a |  |
| outlier-factors | sbs-mid-b-pass1 | py=0 kt=0 | stdout identical; factors.json=DIFF | c | C2: `freq_cl0_mhz: null` on 35/50 iterations (Kotlin only); no value differs. |
| trace-health | sbs-mid-b-pass1 | py=0 kt=0 | stdout identical | a |  |
| analyze | sbs-mid-b-pass2 | py=0 kt=0 | stdout identical; summary-dir=identical | a |  |
| variance | sbs-mid-b-pass2 | py=0 kt=0 | stdout identical; pass.json=identical; report.txt=identical | a |  |
| outlier-factors | sbs-mid-b-pass2 | py=0 kt=0 | stdout identical; factors.json=DIFF | c | C2: `freq_cl0_mhz: null` on 40/50 iterations (Kotlin only); no value differs. |
| trace-health | sbs-mid-b-pass2 | py=0 kt=0 | stdout identical | a |  |
| analyze | sbs-mid-b-pass3 | py=0 kt=0 | stdout identical; summary-dir=identical | a |  |
| variance | sbs-mid-b-pass3 | py=0 kt=0 | stdout identical; pass.json=identical; report.txt=identical | a |  |
| outlier-factors | sbs-mid-b-pass3 | py=0 kt=0 | stdout identical; factors.json=DIFF | c | C2: `freq_cl0_mhz: null` on 36/50 iterations (Kotlin only); no value differs. |
| trace-health | sbs-mid-b-pass3 | py=0 kt=0 | stdout identical | a |  |
| analyze | vfm-mid-b-pass1 | py=0 kt=0 | stdout identical; summary-dir=identical | a |  |
| variance | vfm-mid-b-pass1 | py=0 kt=0 | stdout identical; pass.json=identical; report.txt=identical | a |  |
| outlier-factors | vfm-mid-b-pass1 | py=0 kt=0 | stdout identical; factors.json=DIFF | c | C2: `freq_cl0_mhz: null` on 29/50 iterations (Kotlin only); no value differs. |
| trace-health | vfm-mid-b-pass1 | py=0 kt=0 | stdout identical | a |  |
| analyze | fsf-arm-a | py=0 kt=0 | stdout identical; summary-dir=identical | a |  |
| variance | fsf-arm-a | py=0 kt=0 | stdout identical; pass.json=identical; report.txt=identical | a |  |
| outlier-factors | fsf-arm-a | py=0 kt=0 | stdout identical; factors.json=DIFF | c | C2: `freq_cl0_mhz: null` on 29/50 iterations (Kotlin only); no value differs. |
| trace-health | fsf-arm-a | py=0 kt=0 | stdout identical | a |  |
| analyze | fsf-arm-b | py=0 kt=0 | stdout identical; summary-dir=identical | a |  |
| variance | fsf-arm-b | py=0 kt=0 | stdout identical; pass.json=identical; report.txt=identical | a |  |
| outlier-factors | fsf-arm-b | py=0 kt=0 | stdout identical; factors.json=DIFF | c | C2: `freq_cl0_mhz: null` on 30/50 iterations (Kotlin only); no value differs. |
| trace-health | fsf-arm-b | py=0 kt=0 | stdout identical | a |  |
| analyze | fsf-arm-c | py=0 kt=0 | stdout identical; summary-dir=identical | a |  |
| variance | fsf-arm-c | py=0 kt=0 | stdout identical; pass.json=identical; report.txt=identical | a |  |
| outlier-factors | fsf-arm-c | py=0 kt=0 | stdout identical; factors.json=DIFF | c | C2: `freq_cl0_mhz: null` on 36/50 iterations (Kotlin only); no value differs. |
| trace-health | fsf-arm-c | py=0 kt=0 | stdout identical | a |  |
| analyze | fsf-arm-d | py=0 kt=0 | stdout identical; summary-dir=identical | a |  |
| variance | fsf-arm-d | py=0 kt=0 | stdout identical; pass.json=identical; report.txt=identical | a |  |
| outlier-factors | fsf-arm-d | py=0 kt=0 | stdout identical; factors.json=DIFF | c | C2: `freq_cl0_mhz: null` on 37/50 iterations (Kotlin only); no value differs. |
| trace-health | fsf-arm-d | py=0 kt=0 | stdout identical | a |  |
| hypothesis-tests | campaign-2026-08-11 | py=0 kt=0 | stdout identical | a |  |
| factors-report | campaign-2026-08-11 | py=0 kt=0 | stdout identical | a |  |
| maxims-score | campaign-2026-08-11 | py=0 kt=0 | stdout identical | a |  |
| hypothesis-tests | a14-campaign2-2026-08-11 | py=0 kt=0 | stdout identical | a |  |
| factors-report | a14-campaign2-2026-08-11 | py=1 kt=1 | stdout identical | a |  |
| maxims-score | a14-campaign2-2026-08-11 | py=0 kt=0 | stdout identical | a |  |
| hypothesis-tests | pixel-campaign-2026-08-11 | py=0 kt=0 | stdout identical | a |  |
| factors-report | pixel-campaign-2026-08-11 | py=0 kt=0 | stdout identical | a |  |
| maxims-score | pixel-campaign-2026-08-11 | py=0 kt=0 | stdout identical | a |  |
| hypothesis-tests | a01-campaign-2026-08-11 | py=0 kt=0 | stdout identical | a |  |
| factors-report | a01-campaign-2026-08-11 | py=0 kt=0 | stdout identical | a |  |
| maxims-score | a01-campaign-2026-08-11 | py=0 kt=0 | stdout identical | a |  |
| hypothesis-tests | p7p-campaign-2026-08-12 | py=0 kt=0 | stdout identical | a |  |
| factors-report | p7p-campaign-2026-08-12 | py=0 kt=0 | stdout identical | a |  |
| maxims-score | p7p-campaign-2026-08-12 | py=0 kt=0 | stdout identical | a |  |
| hypothesis-tests | p7p-cooldown-2026-08-12 | py=0 kt=0 | stdout identical | a |  |
| factors-report | p7p-cooldown-2026-08-12 | py=0 kt=0 | stdout identical | a |  |
| maxims-score | p7p-cooldown-2026-08-12 | py=0 kt=0 | stdout identical | a |  |
| hypothesis-tests | cooldown-a01 | py=0 kt=0 | stdout identical | a |  |
| factors-report | cooldown-a01 | py=1 kt=1 | stdout identical | a |  |
| maxims-score | cooldown-a01 | py=0 kt=0 | stdout identical | a |  |
| hypothesis-tests | cooldown-a14 | py=0 kt=0 | stdout identical | a |  |
| factors-report | cooldown-a14 | py=1 kt=1 | stdout identical | a |  |
| maxims-score | cooldown-a14 | py=0 kt=0 | stdout identical | a |  |
| hypothesis-tests | cooldown-pixel | py=0 kt=0 | stdout identical | a |  |
| factors-report | cooldown-pixel | py=0 kt=0 | stdout identical | a |  |
| maxims-score | cooldown-pixel | py=0 kt=0 | stdout identical | a |  |
| hypothesis-tests | a01-mini-2026-08-12 | py=0 kt=0 | stdout identical | a |  |
| factors-report | a01-mini-2026-08-12 | py=1 kt=1 | stdout identical | a |  |
| maxims-score | a01-mini-2026-08-12 | py=0 kt=0 | stdout identical | a |  |
| hypothesis-tests | tg-mid-b | py=0 kt=0 | stdout identical | a |  |
| factors-report | tg-mid-b | py=0 kt=0 | stdout identical | a |  |
| maxims-score | tg-mid-b | py=0 kt=0 | stdout identical | a |  |
| hypothesis-tests | tg-mid-a | py=0 kt=0 | stdout identical | a |  |
| factors-report | tg-mid-a | py=0 kt=0 | stdout identical | a |  |
| maxims-score | tg-mid-a | py=0 kt=0 | stdout identical | a |  |
| hypothesis-tests | tg-flagship-a | py=0 kt=0 | stdout identical | a |  |
| factors-report | tg-flagship-a | py=0 kt=0 | stdout identical | a |  |
| maxims-score | tg-flagship-a | py=0 kt=0 | stdout identical | a |  |
| hypothesis-tests | goldens-campaign-a | py=0 kt=0 | stdout identical | a |  |
| factors-report | goldens-campaign-a | py=0 kt=0 | stdout identical | a |  |
| hypothesis-tests | goldens-campaign-b | py=0 kt=0 | stdout identical | a |  |
| factors-report | goldens-campaign-b | py=1 kt=1 | stdout identical | a |  |
| maxims-score | fixture-campaign-a | py=0 kt=0 | stdout identical | a |  |
| maxims-score | fixture-campaign-b | py=0 kt=0 | stdout identical | a |  |
| maxims-render | real-ledger | py=0 kt=0 | stdout identical | a |  |
| maxims-render | fixture-ledger | py=0 kt=0 | stdout identical | a |  |
| cross-device-sections | campaigns-by-device | py=0 kt=0 | stdout identical | a |  |
| cross-device-sections | cooldowns | py=0 kt=0 | stdout identical | a |  |
| cross-device-sections | trace-goldens | py=0 kt=0 | stdout identical | a |  |
| trend | longitudinal-store | py=0 kt=0 | stdout identical; verdicts.json=identical | a |  |
| trend | longitudinal-sweep-store | py=0 kt=0 | stdout identical; verdicts.json=identical | a |  |
| trend | goldens-trend-store | py=0 kt=0 | stdout identical; verdicts.json=identical | a |  |
| trend | validation-store | py=0 kt=0 | stdout identical; verdicts.json=identical | a |  |
| trend | sbs-store-python | py=0 kt=0 | stdout identical; verdicts.json=identical | a |  |
| trend | sbs-store-kotlin-final | py=0 kt=0 | stdout identical; verdicts.json=identical | a |  |
| trend | x25-store | py=0 kt=0 | stdout identical; verdicts.json=identical | a |  |
| trend | x25-sweep-store | py=0 kt=0 | stdout identical; verdicts.json=identical | a |  |
| ingest-dry-run | fx-flagship-a | py=1 kt=1 | stdout identical | a |  |
| ingest-dry-run | fx-mid-a | py=1 kt=1 | stdout identical | a |  |
| ingest-dry-run | fx-mid-b | py=1 kt=1 | stdout identical | a |  |
| ingest-dry-run | val-mid-b | py=1 kt=1 | stdout identical | a |  |
| ingest-dry-run | sbs-mid-b | py=1 kt=1 | stdout identical | a |  |
| ingest-dry-run | vfm-cell | py=1 kt=1 | stdout identical | a |  |
| ingest-dry-run | fsf-arm-a | py=1 kt=1 | stdout identical | a |  |
| ingest-dry-run | fsf-arm-c | py=1 kt=1 | stdout identical | a | In the CONCURRENT first pass the Python reported 49 windows vs the Kotlin's 50 (see finding C5: ingest_run.py writes a fixed temp SQL file and the eight concurrent ingests clobbered each other). Re-run strictly sequentially: identical refusal (`truncated run: 50 windows against a declared shape of 10x20=200`) on both sides. |
| ingest-force | fx-flagship-a | py=0 kt=0 | stdout identical; store.jsonl=DIFF | b+c (#8, #5) | Sequential re-run. Expected departures: recipe.trace_processor_version v46.0 vs v57.2 and the launcher path (#8); signals_present [] vs 60 names and signals_from_clean_trace false vs true (#5). NEW (C4): device_profile is `{}` in the Python record (no provenance in the fixture dir) but `{api_level:null, release:null, vendor:null, soc_family:null, clusters:null, ram_class:null, storage_class:null}` in the Kotlin one (DeviceProfile encoded with explicitNulls). NEW (C6): trace_health.parse_errors 0 vs 5 - a consequence of #5 not listed there: with canary `composed` the Python's check_trace verdict is `missing-canary` for EVERY trace, which pre-empts the `slices-incomplete` branch, so the Python never counts parse errors under the composed instrument, while the Kotlin (canary emb-modules-init) counts the 5 out-of-order traces. The first concurrent pass also showed n=19 vs 20 (C5, temp-file race), gone after the sequential re-run. |
| ingest-force | fx-mid-a | py=0 kt=0 | stdout identical; store.jsonl=DIFF | b+c (#8, #5) | Sequential re-run. #8 (engine label/path), #5 (signals_present 0 vs 61, signals_from_clean_trace), C4 (device_profile {} vs all-null keys). Windows and derived identical. |
| ingest-force | fx-mid-b | py=0 kt=0 | stdout identical; store.jsonl=DIFF | b+c (#8, #5) | Sequential re-run. #8, #5, C4 as above. Windows and derived identical. |
| ingest-force | vfm-cell | py=0 kt=0 | stdout identical; store.jsonl=DIFF | b+c (#8, #5) | Sequential re-run. #8, #5, and C4 in its second form: the cell-state.json provenance carries a NESTED device_profile (`{api_level, tier, vendor, cool_gate_c, profile:{...}}`, written by the Python cell_runner shape); the Python stores that object verbatim, the Kotlin stores `{api_level:31, vendor:"Google", release:null, soc_family:null, clusters:null, ram_class:null, storage_class:null}` - it reads the flat DeviceProfile fields and drops `tier`, `cool_gate_c` and the nested `profile`. Windows and derived identical. |
| reference-set-show | longitudinal | py=0 kt=0 | stdout 178 diff lines | c | C3: JSON pretty-print indentation. Python `json.dumps(doc, indent=1)` (1 space per level) vs Kotlin 4 spaces. After stripping leading whitespace the two outputs are byte-identical (compare/summary.json `stdout_differs_only_in_leading_whitespace`), including the COVERAGE GAP lines. |
| reference-set-show | fixture | py=0 kt=0 | stdout 178 diff lines | c | C3 (indent 1 vs 4); identical after stripping leading whitespace. |
| reference-set-show | x25-live | py=0 kt=0 | stdout 178 diff lines | c | C3 (indent 1 vs 4); identical after stripping leading whitespace. |
| reference-set-show | x25-archive | py=0 kt=0 | stdout 180 diff lines | c | C3 (indent 1 vs 4); identical after stripping leading whitespace. |
| reference-set-show | maxims-fixture | py=0 kt=0 | stdout 86 diff lines | c | C3 (indent 1 vs 4); identical after stripping leading whitespace. |
| reference-set-probe-check | longitudinal | py=0 kt=0 | stdout identical | a |  |
| reference-set-probe-out | attached | py=0 kt=0 | stdout identical; ref.json=identical | a |  |
| reproducibility | goldens-corpus | py=0 kt=0 | stdout identical; cells.json=identical | a |  |
| submit-dry-run | longitudinal-store__9.1.0__mid-b | py=1 kt=1 | stdout identical | a |  |
| submit-dry-run | longitudinal-store__9.1.0__mid-a | py=1 kt=1 | stdout identical | a |  |
| submit-dry-run | longitudinal-store__9.0.0__flagship-a | py=1 kt=1 | stdout identical | a |  |
| submit-dry-run | longitudinal-store__9.0.0__mid-b | py=1 kt=1 | stdout identical | a |  |
| submit-dry-run | longitudinal-store__9.0.0__mid-a | py=1 kt=1 | stdout identical | a |  |
| submit-dry-run | longitudinal-store__9.0.0__entry-a | py=1 kt=1 | stdout identical | a |  |
| submit-dry-run | longitudinal-store__9.1.0__flagship-a | py=1 kt=1 | stdout identical | a |  |
| submit-dry-run | longitudinal-store__9.2.0__flagship-a | py=1 kt=1 | stdout identical | a |  |
| submit-dry-run | longitudinal-store__9.2.0__mid-b | py=1 kt=1 | stdout identical | a |  |
| submit-dry-run | longitudinal-store__9.2.0__mid-a | py=1 kt=1 | stdout identical | a |  |
| submit-dry-run | longitudinal-store__9.2.0__entry-a | py=1 kt=1 | stdout identical | a |  |
| submit-dry-run | longitudinal-store__9.1.0__entry-a | py=1 kt=1 | stdout identical | a |  |
| submit-dry-run | longitudinal-sweep-store__8.3.0__flagship-a | py=1 kt=1 | stdout identical | a |  |
| submit-dry-run | longitudinal-sweep-store__8.3.0__mid-b | py=1 kt=1 | stdout identical | a |  |
| submit-dry-run | longitudinal-sweep-store__8.3.0__mid-a | py=1 kt=1 | stdout identical | a |  |
| submit-dry-run | longitudinal-sweep-store__8.3.0__entry-a | py=1 kt=1 | stdout identical | a |  |
| submit-dry-run | validation-store__mid-b | py=1 kt=1 | stdout identical | a |  |
| submit-dry-run | sbs-store-kotlin-final__mid-b | py=0 kt=0 | stdout 196 diff lines | c | C3: the redacted submission is printed with indent 1 (Python) vs 4 (Kotlin); identical after stripping leading whitespace (same fields, same 61 signals, same derived values). Both sides ADMIT this record (rc 0) - it is the Kotlin-ingested record, whose signals came from a clean trace. |
| submit-dry-run | sbs-store-python__mid-b | py=0 kt=0 | stdout 196 diff lines | c | C3 (indent only). NOTE: this is the store the PYTHON ingest wrote on 2026-09-02 and both sides admit it, so its signals_present is populated - see the side-by-side notes in PORT-LOG; the twelve records in longitudinal/store.jsonl and the four in sweep-store.jsonl are refused identically by both sides (PORT-LOG #5: `the signal inventory did not come from a clean trace`). |
| submit-dry-run-serial | sbs-store-kotlin-final__mid-b | py=0 kt=0 | stdout 228 diff lines | c | C3 (indent only). With --serial mid-b both sides collected the same device provenance from the Pixel 3 (model, os_build, security_patch, kernel, soc, storage 20-40%, battery_health 3, app count 50-150, settings) and derived the same unit_id 23ed8346bca11944 from the shared .unit-salt the Python wrote first. |
| matrix-plan | skill-plan-example | py=0 kt=0 | stdout 2 diff lines; cells.json=identical | b (#9) | --emit hint names `startup-tools cell-runner --cells` instead of `cell_runner.py --cells`; emitted cells.json tree-identical. |
| matrix-plan | fixture-plan-example | py=0 kt=0 | stdout 2 diff lines; cells.json=identical | b (#9) | same as above; cells.json tree-identical. |
| matrix-report | vfm-emb-sdk-start | py=0 kt=0 | stdout identical; cells.json=identical | a | Sequential re-run: identical (n=50, med 26.4, p90 27.5, max 28.6) and cells.json tree-identical. The concurrent first pass showed the Python at n=3 - finding C5: matrix_report.py writes its SQL to the fixed path `$TMPDIR/vfm_q.sql`, and the three concurrent Python matrix-report processes overwrote each other's query. |
| matrix-report | vfm-default-slice | py=0 kt=0 | stdout 4 diff lines | b (#20) | Python default --slice is the phantom `app-embrace-start` (prints `SKIP mid-b\|9.2.0\|reference: no window values (app-embrace-start missing?)` after the sequential re-run); Kotlin default is emb-sdk-start and renders the row. |
| matrix-report | vfm-composed | py=0 kt=0 | stdout 2 diff lines | b (#20) | `--slice composed`: the Kotlin treats it as the composed-window sentinel (n=50, med 26.0); the Python looks for a slice literally named `composed` and SKIPs the cell. |
| cohorts | fsf-arm-c | py=0 kt=0 | stdout identical; cohorts.json=identical | a |  |
| cohorts-no-method | fsf-arm-c | py=0 kt=0 | stdout identical | a |  |
| cohorts | fsf-arm-d | py=0 kt=0 | stdout identical; cohorts.json=identical | a |  |
| cohorts-no-method | fsf-arm-d | py=0 kt=0 | stdout identical | a |  |
| artifact-sync-list | repo | py=0 kt=0 | stdout identical | a |  |

## Every non-identical cell, quoted

### `probe` / `flagship-a` - verdict c

C1: stdout hint sentence reworded (`pass --little-cpus 0,1,2,3 to variance_analysis.py, or set LITTLE_CPUS=0,1,2,3 for hypothesis_tests.py / factors_report.py` -> `pass --little-cpus 0,1,2,3 to variance, hypothesis-tests and factors-report`). Follows from PORT-LOG #7 but the probe's advice line is not listed there. The topology JSON is tree-identical.

```
py: python3 .claude/skills/startup-multi-device-analysis/scripts/device_probe.py flagship-a flagship-a <scratch>/probe/py
kt: tools/startup probe flagship-a flagship-a <scratch>/probe/kt
```

rc: py=0 kt=0; wall: py 0.6 s, kt 1.1 s

stdout diff (after masking):

```diff
--- py.stdout
+++ kt.stdout
@@ -1,2 +1,2 @@
-flagship-a: Google Pixel 7 Pro, Android 15 (API 35); soc Google GS201; tier flagship; 11471 MB (high); storage ufs-class; 3 cpufreq policy(ies); thermal sensors: smpl_gm, vdroop2, BCL_AUDIO_BAACL, BCL_BATOILO_TPU_LOW_TEMP, BCL_TPU_LOW_TEMP, soc, FLASH_LED_REDUCE, TPU, G3D, BIG, BCL_BATOILO_TPU, BCL_GPU_LOW_TEMP, LITTLE, BCL_BATOILO_GPU_LOW_TEMP, MID, neutral_therm, cellular-emergency, BCL_BATOILO_GPU, battery_cycle, VIRTUAL-SKIN-CHARGE, gnss_tcxo_therm, critical-battery-cell, USB-MINUS-NEUTRAL, VIRTUAL-SKIN, VIRTUAL-USB-THROTTLING, ocp_gpu, disp_therm, battery, ocp_tpu, usb_pwr_therm2, usb_pwr_therm, VIRTUAL-USB-UI, USB-MINUS-USB2, vdroop1, qi_therm, batoilo, quiet_therm. Wrote <SCRATCH> — pass --little-cpus 0,1,2,3 to variance_analysis.py, or set LITTLE_CPUS=0,1,2,3 for hypothesis_tests.py / factors_report.py, when analyzing this device's traces.
+flagship-a: Google Pixel 7 Pro, Android 15 (API 35); soc Google GS201; tier flagship; 11471 MB (high); storage ufs-class; 3 cpufreq policy(ies); thermal sensors: smpl_gm, vdroop2, BCL_AUDIO_BAACL, BCL_BATOILO_TPU_LOW_TEMP, BCL_TPU_LOW_TEMP, soc, FLASH_LED_REDUCE, TPU, G3D, BIG, BCL_BATOILO_TPU, BCL_GPU_LOW_TEMP, LITTLE, BCL_BATOILO_GPU_LOW_TEMP, MID, neutral_therm, cellular-emergency, BCL_BATOILO_GPU, battery_cycle, VIRTUAL-SKIN-CHARGE, gnss_tcxo_therm, critical-battery-cell, USB-MINUS-NEUTRAL, VIRTUAL-SKIN, VIRTUAL-USB-THROTTLING, ocp_gpu, disp_therm, battery, ocp_tpu, usb_pwr_therm2, usb_pwr_therm, VIRTUAL-USB-UI, USB-MINUS-USB2, vdroop1, qi_therm, batoilo, quiet_therm. Wrote <SCRATCH> — pass --little-cpus 0,1,2,3 to variance, hypothesis-tests and factors-report when analyzing this device's traces.
 Record this profile with the campaign output; report tier / vendor / api_level coverage for the whole device set before drawing cross-device conclusions.
```

### `probe` / `mid-b` - verdict c

C1 (same rewording as flagship-a); topology JSON tree-identical.

```
py: python3 .claude/skills/startup-multi-device-analysis/scripts/device_probe.py mid-b mid-b <scratch>/probe/py
kt: tools/startup probe mid-b mid-b <scratch>/probe/kt
```

rc: py=0 kt=0; wall: py 0.5 s, kt 0.8 s

stdout diff (after masking):

```diff
--- py.stdout
+++ kt.stdout
@@ -1,2 +1,2 @@
-mid-b: Google Pixel 3, Android 12 (API 31); soc Qualcomm SDM845; tier mid; 3579 MB (mid); storage ufs-class; 2 cpufreq policy(ies); thermal sensors: usbc-therm-monitor, gpu1-usr, maxfg, cpu1-gold-usr, cpu2-gold-usr, cpu2-silver-usr, cpu0-gold-usr, cpu1-silver-usr, cpu3-silver-usr, gpu0-usr, cpu3-gold-usr, fps-therm-monitor, cpu0-silver-usr. Wrote <SCRATCH> — pass --little-cpus 0,1,2,3 to variance_analysis.py, or set LITTLE_CPUS=0,1,2,3 for hypothesis_tests.py / factors_report.py, when analyzing this device's traces.
+mid-b: Google Pixel 3, Android 12 (API 31); soc Qualcomm SDM845; tier mid; 3579 MB (mid); storage ufs-class; 2 cpufreq policy(ies); thermal sensors: usbc-therm-monitor, gpu1-usr, maxfg, cpu1-gold-usr, cpu2-gold-usr, cpu2-silver-usr, cpu0-gold-usr, cpu1-silver-usr, cpu3-silver-usr, gpu0-usr, cpu3-gold-usr, fps-therm-monitor, cpu0-silver-usr. Wrote <SCRATCH> — pass --little-cpus 0,1,2,3 to variance, hypothesis-tests and factors-report when analyzing this device's traces.
 Record this profile with the campaign output; report tier / vendor / api_level coverage for the whole device set before drawing cross-device conclusions.
```

### `probe` / `mid-a` - verdict c

C1 (same rewording); topology JSON tree-identical.

```
py: python3 .claude/skills/startup-multi-device-analysis/scripts/device_probe.py mid-a mid-a <scratch>/probe/py
kt: tools/startup probe mid-a mid-a <scratch>/probe/kt
```

rc: py=0 kt=0; wall: py 1.0 s, kt 1.3 s

stdout diff (after masking):

```diff
--- py.stdout
+++ kt.stdout
@@ -1,2 +1,2 @@
-mid-a: samsung SM-A145M, Android 15 (API 35); soc Samsung Exynos 850; tier mid; 3683 MB (mid); storage emmc-class; 2 cpufreq policy(ies) (homogeneous — no DVFS cluster split detected, using policy0's cpus); thermal sensors: AP, BAT, PA, SKIN, SUBBAT, USB. Wrote <SCRATCH> — pass --little-cpus 0,1,2,3 to variance_analysis.py, or set LITTLE_CPUS=0,1,2,3 for hypothesis_tests.py / factors_report.py, when analyzing this device's traces.
+mid-a: samsung SM-A145M, Android 15 (API 35); soc Samsung Exynos 850; tier mid; 3683 MB (mid); storage emmc-class; 2 cpufreq policy(ies) (homogeneous — no DVFS cluster split detected, using policy0's cpus); thermal sensors: AP, BAT, PA, SKIN, SUBBAT, USB. Wrote <SCRATCH> — pass --little-cpus 0,1,2,3 to variance, hypothesis-tests and factors-report when analyzing this device's traces.
 Record this profile with the campaign output; report tier / vendor / api_level coverage for the whole device set before drawing cross-device conclusions.
```

### `probe` / `entry-a` - verdict c

C1 (same rewording); topology JSON tree-identical.

```
py: python3 .claude/skills/startup-multi-device-analysis/scripts/device_probe.py entry-a entry-a <scratch>/probe/py
kt: tools/startup probe entry-a entry-a <scratch>/probe/kt
```

rc: py=0 kt=0; wall: py 0.5 s, kt 0.8 s

stdout diff (after masking):

```diff
--- py.stdout
+++ kt.stdout
@@ -1,2 +1,2 @@
-entry-a: samsung SM-A013G, Android 10 (API 29); soc mt6739 ; tier entry; 890 MB (go); storage emmc-class; 1 cpufreq policy(ies) (homogeneous — no DVFS cluster split detected, using policy0's cpus); thermal sensors: CPU_T, GPU_T, SKIN_T. Wrote <SCRATCH> — pass --little-cpus 0,1,2,3 to variance_analysis.py, or set LITTLE_CPUS=0,1,2,3 for hypothesis_tests.py / factors_report.py, when analyzing this device's traces.
+entry-a: samsung SM-A013G, Android 10 (API 29); soc mt6739 ; tier entry; 890 MB (go); storage emmc-class; 1 cpufreq policy(ies) (homogeneous — no DVFS cluster split detected, using policy0's cpus); thermal sensors: CPU_T, GPU_T, SKIN_T. Wrote <SCRATCH> — pass --little-cpus 0,1,2,3 to variance, hypothesis-tests and factors-report when analyzing this device's traces.
 Record this profile with the campaign output; report tier / vendor / api_level coverage for the whole device set before drawing cross-device conclusions.
```

### `variance` / `fx-flagship-a` - verdict b (#3)

ONE-SIDED ERROR, expected: variance_analysis.py dies with `statistics.StatisticsError: no median for empty data` (emb-record-startup emitted by no Pixel 7 Pro trace); the Kotlin renders the report with NaN for that section. The --json dataset written before the crash is tree-identical to the Kotlin's; the Kotlin --out report exists, the Python's does not.

```
py: python3 .claude/skills/startup-multi-device-analysis/scripts/variance_analysis.py ~/.cache/embrace-startup-tools/trace_processor/v46.0/trace_processor claude-output/2026-08-26-kotlin-port/fixtures/traces/flagship-a/pass1 --little-cpus 0,1,2,3 --json raw/variance/artifacts/fx-flagship-a.py.pass.json --out raw/variance/artifacts/fx-flagship-a.py.report.txt
kt: tools/startup variance claude-output/2026-08-26-kotlin-port/fixtures/traces/flagship-a/pass1 --little-cpus 0,1,2,3 --json raw/variance/artifacts/fx-flagship-a.kt.pass.json --out raw/variance/artifacts/fx-flagship-a.kt.report.txt
```

rc: py=1 kt=0; wall: py 81.5 s, kt 80.7 s

stdout diff (after masking):

```diff
--- py.stdout
+++ kt.stdout
@@ -0,0 +1,92 @@
+A. per-iteration durations, ms
+  it   window     impl     boot  modules  cfgload   cfgsvc  spansvc   tracer   essent    deliv  payload  postini  postsvc  loadins  sighand   natlib recstart    power     snap
+    0     16.1     1.16     0.86    14.93     8.11     0.27     3.90     1.50     0.11     0.68     0.64     0.20     0.74     0.52     4.88     1.97      nan     2.21     2.77
+    1     22.7     1.28     0.86     8.96     2.20     0.39     2.50     1.77     0.17     0.67     1.02    12.49     0.94     0.83     1.99     1.19      nan     1.73     0.95
+    2     11.1     1.73     1.39     9.97     2.41     0.26     3.80     2.53     0.24     0.90     0.95     0.15     0.73     0.68     5.00     3.59      nan     2.18     0.91
+    3     10.9     0.87     0.60     9.74     4.37     0.30     2.11     1.46     0.13     0.65     0.91     0.18     0.74     0.67     5.79     4.48      nan     1.70     1.25
+    4     11.8     1.02     0.71    10.67     3.08     0.24     2.62     1.90     0.14     0.83     1.75     0.19     0.68     0.62     6.19     5.08      nan     1.80     0.99
+    5     12.4     0.96     0.65    10.65     3.00     0.28     1.92     1.23     0.13     1.23     1.69     0.27     1.01     0.91     4.68     2.27      nan     4.41     2.43
+    6     16.9     1.32     1.01    12.31     2.58     0.28     5.21     4.15     0.21     0.85     1.39     1.49     2.42     2.21     3.89     3.28      nan     2.11     0.43
+    7     16.1     1.00     0.64    13.53     4.77     0.30     2.13     1.40     0.16     2.06     1.72     0.28     1.96     1.86     5.01     3.89      nan     3.28     1.27
+    8     11.4     0.93     0.67    10.16     2.58     0.29     4.45     3.55     0.17     0.58     0.83     0.15     0.82     0.76     4.23     3.07      nan     1.02     0.98
+    9     10.1     2.29     1.96     9.09     3.38     0.30     2.39     1.66     0.15     0.66     0.86     0.15     0.66     0.61     5.51     2.55      nan     2.00     2.63
+   10     24.2     1.59     1.16    22.46    10.40     1.08     5.74     4.80     0.20     0.94     1.56     0.31     1.15     1.06     4.51     3.42      nan     2.47     1.29
+   11     14.8     1.04     0.75    13.26     3.16     0.28     2.01     1.35     0.14     2.67     2.28     0.27     0.95     0.87     7.07     4.87      nan     1.70     0.80
+   12     15.5     0.97     0.68    12.62     3.37     0.25     6.16     4.30     0.14     0.68     0.79     0.15     1.15     1.08     6.07     5.24      nan     1.31     3.07
+   13     10.5     2.02     1.71     9.44     3.96     0.32     2.02     1.31     0.15     0.53     0.95     0.17     0.67     0.61     4.90     3.87      nan     1.50     0.52
+   14     10.5     1.13     0.86     9.66     4.09     0.33     2.06     1.43     0.20     0.61     0.83     0.13     0.53     0.48     5.54     3.76      nan     1.92     1.28
+   15     10.3     0.92     0.64     9.37     3.49     0.35     2.24     1.57     0.14     0.59     0.82     0.14     0.58     0.53     4.83     3.27      nan     0.73     0.90
+   16     11.0     1.07     0.70     9.91     3.49     0.25     2.67     1.92     0.18     0.68     1.19     0.19     0.70     0.64     5.42     2.58      nan     2.18     1.16
+   17     11.1     2.30     1.98    10.15     3.51     0.41     2.20     1.51     0.14     0.61     0.93     0.16     0.55     0.50     8.75     7.38      nan     1.60     0.57
+   18     11.0     2.37     2.03     9.89     3.21     0.27     2.13     1.49     0.13     1.60     0.97     0.17     0.64     0.59     4.62     3.45      nan     0.90     0.69
+   19     15.6     2.11     1.70    14.57     6.39     0.47     4.35     2.14     0.15     0.62     0.84     0.16     0.61     0.56     6.20     3.75      nan     0.86     1.27
+
+B. per-section fluctuation (n=20), ms
+  section                                       min    med    max  spread  stdev  r_win
+  emb-embrace-impl-init                        0.87   1.14   2.37    1.50   0.53  -0.11
+  emb-bootstrapper-init                        0.60   0.86   2.03    1.44   0.52  -0.18
+  emb-modules-init                             8.96  10.16  22.46   13.50   3.17   0.71
+  emb-persisted-config-load                    2.20   3.43  10.40    8.20   2.03   0.50
+  emb-config-service-init                      0.24   0.29   1.08    0.84   0.18   0.64
+  emb-span-service-init                        1.92   2.45   6.16    4.24   1.37   0.51
+  emb-otel-tracer-init                         1.23   1.61   4.80    3.56   1.11   0.50
+  emb-essential-service-init                   0.11   0.15   0.24    0.12   0.03   0.21
+  emb-delivery-init                            0.53   0.68   2.67    2.13   0.56   0.14
+  emb-payload-source-init                      0.64   0.95   2.28    1.63   0.43   0.27
+  emb-post-init                                0.13   0.18  12.49   12.36   2.75   0.55
+  emb-post-services-setup                      0.53   0.73   2.42    1.89   0.48   0.47
+  emb-load-instrumentation                     0.48   0.66   2.21    1.74   0.45   0.45
+  emb-install-native-crash-signal-handlers     1.99   5.01   8.75    6.76   1.33  -0.43
+  emb-load-embrace-native-lib                  1.19   3.52   7.38    6.19   1.35  -0.28
+  emb-power-service-registration               0.73   1.77   4.41    3.68   0.85   0.19
+  emb-snapshot-session                         0.43   1.07   3.07    2.64   0.78   0.07
+
+C. slowest iterations: section excess vs median (window Δ = window - median window)
+  iter010  window 24.2 (Δ +12.6): modules +12.3, cfgload +7.0, spansvc +3.3, tracer +3.2, cfgsvc +0.8, power +0.7
+  iter001  window 22.7 (Δ +11.1): postini +12.3
+  iter006  window 16.9 (Δ +5.3): spansvc +2.8, tracer +2.5, modules +2.2, postsvc +1.7, loadins +1.6, postini +1.3
+  iter007  window 16.1 (Δ +4.5): modules +3.4, power +1.5, deliv +1.4, cfgload +1.3, postsvc +1.2, loadins +1.2
+
+D. thread-state inside each section, ms (median across iters | mean over 4 slowest iters)
+  section                                       run  sleep   io/D    rq  |    run  sleep   io/D    rq
+  emb-embrace-impl-init                        1.11   0.00   0.00  0.02  |   1.24   0.00   0.00  0.05
+  emb-bootstrapper-init                        0.79   0.00   0.00  0.00  |   0.89   0.00   0.00  0.03
+  emb-modules-init                             9.07   0.00   0.60  0.66  |  11.95   0.06   0.74  1.57
+  emb-persisted-config-load                    2.57   0.00   0.60  0.14  |   3.65   0.00   0.74  0.59
+  emb-config-service-init                      0.29   0.00   0.00  0.00  |   0.49   0.00   0.00  0.02
+  emb-span-service-init                        2.28   0.00   0.00  0.05  |   3.18   0.00   0.00  0.72
+  emb-otel-tracer-init                         1.53   0.00   0.00  0.03  |   2.31   0.00   0.00  0.72
+  emb-essential-service-init                   0.15   0.00   0.00  0.00  |   0.18   0.00   0.00  0.00
+  emb-delivery-init                            0.68   0.00   0.00  0.00  |   1.13   0.00   0.00  0.00
+  emb-payload-source-init                      0.92   0.00   0.00  0.01  |   1.38   0.00   0.00  0.04
+  emb-post-init                                0.18   0.00   0.00  0.00  |   2.49   0.77   0.00  0.39
+  emb-post-services-setup                      0.69   0.00   0.00  0.00  |   1.60   0.00   0.00  0.02
+  emb-load-instrumentation                     0.62   0.00   0.00  0.00  |   1.48   0.00   0.00  0.02
+  emb-install-native-crash-signal-handlers     2.93   0.00   1.39  0.57  |   2.31   0.00   1.11  0.42
+  emb-load-embrace-native-lib                  2.32   0.00   0.90  0.15  |   1.81   0.00   0.81  0.33
+  emb-power-service-registration               1.23   0.46   0.00  0.02  |   1.27   1.02   0.00  0.11
+  emb-snapshot-session                         0.65   0.34   0.00  0.08  |   0.64   0.19   0.01  0.15
+
+E. main-thread CPU residency inside window, ms per cpu
+  it   window cpu   0 cpu   1 cpu   2 cpu   3 cpu   4 cpu   5 cpu   6 cpu   7   little-share
+    0     16.1     0.0     2.7     0.7     0.0     0.0     0.0     0.0     8.6    28.3%
+    1     22.7     0.0     0.0     0.0     9.3     0.0     0.0     9.3     0.0    50.0%
+    2     11.1     0.0     0.0     0.0     0.0     5.4     0.0     2.0     3.2     0.0%
+    3     10.9     0.0     0.0     0.0     0.4     0.0     0.0     8.9     0.3     4.6%
+    4     11.8     0.0     0.5     0.0     0.0     2.2     1.2     7.1     0.0     4.4%
+    5     12.4     0.0     0.9     0.0     0.0     5.2     0.0     5.3     0.0     8.2%
... (15 more lines; full diff in compare/variance/fx-flagship-a.stdout.diff)
```

artifact `report.txt`: py=MISSING kt=ok

py stderr (rc=1):

```
Traceback (most recent call last):
  File "<repo>/.claude/skills/startup-multi-device-analysis/scripts/variance_analysis.py", line 243, in <module>
    sys.exit(main())
             ~~~~^^
  File "<repo>/.claude/skills/startup-multi-device-analysis/scripts/variance_analysis.py", line 106, in main
    report(data, little_cpus)
    ~~~~~~^^^^^^^^^^^^^^^^^^^
  File "<repo>/.claude/skills/startup-multi-device-analysis/scripts/variance_analysis.py", line 142, in report
    med = {c: statistics.median([m["dur"][c] for _, m in data if c in m["dur"]]) for c in cols}
              ~~~~~~~~~~~~~~~~~^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
  File "/opt/homebrew/Cellar/python@3.14/3.14.2_1/Frameworks/Python.framework/Versions/3.14/lib/python3.14/statistics.py", line 343, in median
    raise StatisticsError("no median for empty data")
statistics.StatisticsError: no median for empty data
```

### `outlier-factors` / `fx-flagship-a` - verdict c

C2: passN-factors.json key presence. The Kotlin writes `"freq_limit_cl0": null, "freq_limit_cl1": null` on all 20 iterations; the Python omits the key when the SQL row is [NULL]. No value differs. Root cause: core/json/Codecs `explicitNulls = true` + `encodeDefaults = true` (PORT-LOG #26, which scopes the change to store records and the two Derived fields; OutlierFactors.Record is not mentioned and its own doc comment says absence must stay absence).

```
py: python3 .claude/skills/startup-multi-device-analysis/scripts/outlier_factors.py ~/.cache/embrace-startup-tools/trace_processor/v46.0/trace_processor claude-output/2026-08-26-kotlin-port/fixtures/traces/flagship-a/pass1 raw/outlier-factors/artifacts/fx-flagship-a.py.factors.json
kt: tools/startup outlier-factors claude-output/2026-08-26-kotlin-port/fixtures/traces/flagship-a/pass1 raw/outlier-factors/artifacts/fx-flagship-a.kt.factors.json
```

rc: py=0 kt=0; wall: py 603.1 s, kt 603.1 s

artifact `factors.json`: DIFF

```
== key-set differences ==
[0]/freq_limit_cl0: only in kt
[0]/freq_limit_cl1: only in kt
[1]/freq_limit_cl0: only in kt
[1]/freq_limit_cl1: only in kt
[2]/freq_limit_cl0: only in kt
[2]/freq_limit_cl1: only in kt
[3]/freq_limit_cl0: only in kt
[3]/freq_limit_cl1: only in kt
[4]/freq_limit_cl0: only in kt
[4]/freq_limit_cl1: only in kt
[5]/freq_limit_cl0: only in kt
[5]/freq_limit_cl1: only in kt
[6]/freq_limit_cl0: only in kt
[6]/freq_limit_cl1: only in kt
[7]/freq_limit_cl0: only in kt
[7]/freq_limit_cl1: only in kt
[8]/freq_limit_cl0: only in kt
[8]/freq_limit_cl1: only in kt
[9]/freq_limit_cl0: only in kt
[9]/freq_limit_cl1: only in kt
[10]/freq_limit_cl0: only in kt
[10]/freq_limit_cl1: only in kt
[11]/freq_limit_cl0: only in kt
[11]/freq_limit_cl1: only in kt
[12]/freq_limit_cl0: only in kt
[12]/freq_limit_cl1: only in kt
[13]/freq_limit_cl0: only in kt
[13]/freq_limit_cl1: only in kt
[14]/freq_limit_cl0: only in kt
[14]/freq_limit_cl1: only in kt
[15]/freq_limit_cl0: only in kt
[15]/freq_limit_cl1: only in kt
[16]/freq_limit_cl0: only in kt
[16]/freq_limit_cl1: only in kt
[17]/freq_limit_cl0: only in kt
[17]/freq_limit_cl1: only in kt
[18]/freq_limit_cl0: only in kt
[18]/freq_limit_cl1: only in kt
[19]/freq_limit_cl0: only in kt
[19]/freq_limit_cl1: only in kt

== value differences ==
```

### `outlier-factors` / `fx-mid-b` - verdict c

C2: `"freq_cl0_mhz": null` written by the Kotlin on 19/20 iterations (Pixel 3 little-cluster clock counter absent inside the window); key absent in the Python. No value differs.

```
py: python3 .claude/skills/startup-multi-device-analysis/scripts/outlier_factors.py ~/.cache/embrace-startup-tools/trace_processor/v46.0/trace_processor claude-output/2026-08-26-kotlin-port/fixtures/traces/mid-b/pass1 raw/outlier-factors/artifacts/fx-mid-b.py.factors.json
kt: tools/startup outlier-factors claude-output/2026-08-26-kotlin-port/fixtures/traces/mid-b/pass1 raw/outlier-factors/artifacts/fx-mid-b.kt.factors.json
```

rc: py=0 kt=0; wall: py 145.3 s, kt 144.3 s

artifact `factors.json`: DIFF

```
== key-set differences ==
[1]/freq_cl0_mhz: only in kt
[2]/freq_cl0_mhz: only in kt
[3]/freq_cl0_mhz: only in kt
[4]/freq_cl0_mhz: only in kt
[5]/freq_cl0_mhz: only in kt
[6]/freq_cl0_mhz: only in kt
[7]/freq_cl0_mhz: only in kt
[8]/freq_cl0_mhz: only in kt
[9]/freq_cl0_mhz: only in kt
[10]/freq_cl0_mhz: only in kt
[11]/freq_cl0_mhz: only in kt
[12]/freq_cl0_mhz: only in kt
[13]/freq_cl0_mhz: only in kt
[14]/freq_cl0_mhz: only in kt
[15]/freq_cl0_mhz: only in kt
[16]/freq_cl0_mhz: only in kt
[17]/freq_cl0_mhz: only in kt
[18]/freq_cl0_mhz: only in kt
[19]/freq_cl0_mhz: only in kt

== value differences ==
```

### `outlier-factors` / `val-mid-b-pass1` - verdict c

C2: `freq_cl0_mhz: null` on 30/50 iterations (Kotlin only); no value differs.

```
py: python3 .claude/skills/startup-multi-device-analysis/scripts/outlier_factors.py ~/.cache/embrace-startup-tools/trace_processor/v46.0/trace_processor claude-output/2026-08-26-kotlin-port/validation/mid-b/pass1 raw/outlier-factors/artifacts/val-mid-b-pass1.py.factors.json
kt: tools/startup outlier-factors claude-output/2026-08-26-kotlin-port/validation/mid-b/pass1 raw/outlier-factors/artifacts/val-mid-b-pass1.kt.factors.json
```

rc: py=0 kt=0; wall: py 305.2 s, kt 303.6 s

artifact `factors.json`: DIFF

```
== key-set differences ==
[1]/freq_cl0_mhz: only in kt
[4]/freq_cl0_mhz: only in kt
[5]/freq_cl0_mhz: only in kt
[6]/freq_cl0_mhz: only in kt
[7]/freq_cl0_mhz: only in kt
[8]/freq_cl0_mhz: only in kt
[9]/freq_cl0_mhz: only in kt
[13]/freq_cl0_mhz: only in kt
[14]/freq_cl0_mhz: only in kt
[15]/freq_cl0_mhz: only in kt
[16]/freq_cl0_mhz: only in kt
[17]/freq_cl0_mhz: only in kt
[19]/freq_cl0_mhz: only in kt
[20]/freq_cl0_mhz: only in kt
[21]/freq_cl0_mhz: only in kt
[23]/freq_cl0_mhz: only in kt
[24]/freq_cl0_mhz: only in kt
[27]/freq_cl0_mhz: only in kt
[29]/freq_cl0_mhz: only in kt
[31]/freq_cl0_mhz: only in kt
[34]/freq_cl0_mhz: only in kt
[35]/freq_cl0_mhz: only in kt
[36]/freq_cl0_mhz: only in kt
[37]/freq_cl0_mhz: only in kt
[38]/freq_cl0_mhz: only in kt
[40]/freq_cl0_mhz: only in kt
[41]/freq_cl0_mhz: only in kt
[44]/freq_cl0_mhz: only in kt
[48]/freq_cl0_mhz: only in kt
[49]/freq_cl0_mhz: only in kt

== value differences ==
```

### `outlier-factors` / `sbs-mid-b-pass1` - verdict c

C2: `freq_cl0_mhz: null` on 35/50 iterations (Kotlin only); no value differs.

```
py: python3 .claude/skills/startup-multi-device-analysis/scripts/outlier_factors.py ~/.cache/embrace-startup-tools/trace_processor/v46.0/trace_processor claude-output/2026-08-26-kotlin-port/validation/side-by-side/mid-b/pass1 raw/outlier-factors/artifacts/sbs-mid-b-pass1.py.factors.json
kt: tools/startup outlier-factors claude-output/2026-08-26-kotlin-port/validation/side-by-side/mid-b/pass1 raw/outlier-factors/artifacts/sbs-mid-b-pass1.kt.factors.json
```

rc: py=0 kt=0; wall: py 280.2 s, kt 279.2 s

artifact `factors.json`: DIFF

```
== key-set differences ==
[0]/freq_cl0_mhz: only in kt
[1]/freq_cl0_mhz: only in kt
[2]/freq_cl0_mhz: only in kt
[3]/freq_cl0_mhz: only in kt
[4]/freq_cl0_mhz: only in kt
[5]/freq_cl0_mhz: only in kt
[6]/freq_cl0_mhz: only in kt
[8]/freq_cl0_mhz: only in kt
[11]/freq_cl0_mhz: only in kt
[12]/freq_cl0_mhz: only in kt
[14]/freq_cl0_mhz: only in kt
[15]/freq_cl0_mhz: only in kt
[16]/freq_cl0_mhz: only in kt
[17]/freq_cl0_mhz: only in kt
[18]/freq_cl0_mhz: only in kt
[19]/freq_cl0_mhz: only in kt
[21]/freq_cl0_mhz: only in kt
[22]/freq_cl0_mhz: only in kt
[23]/freq_cl0_mhz: only in kt
[26]/freq_cl0_mhz: only in kt
[28]/freq_cl0_mhz: only in kt
[29]/freq_cl0_mhz: only in kt
[30]/freq_cl0_mhz: only in kt
[31]/freq_cl0_mhz: only in kt
[32]/freq_cl0_mhz: only in kt
[33]/freq_cl0_mhz: only in kt
[34]/freq_cl0_mhz: only in kt
[37]/freq_cl0_mhz: only in kt
[38]/freq_cl0_mhz: only in kt
[39]/freq_cl0_mhz: only in kt
[40]/freq_cl0_mhz: only in kt
[41]/freq_cl0_mhz: only in kt
[43]/freq_cl0_mhz: only in kt
[46]/freq_cl0_mhz: only in kt
[49]/freq_cl0_mhz: only in kt

== value differences ==
```

### `outlier-factors` / `sbs-mid-b-pass2` - verdict c

C2: `freq_cl0_mhz: null` on 40/50 iterations (Kotlin only); no value differs.

```
py: python3 .claude/skills/startup-multi-device-analysis/scripts/outlier_factors.py ~/.cache/embrace-startup-tools/trace_processor/v46.0/trace_processor claude-output/2026-08-26-kotlin-port/validation/side-by-side/mid-b/pass2 raw/outlier-factors/artifacts/sbs-mid-b-pass2.py.factors.json
kt: tools/startup outlier-factors claude-output/2026-08-26-kotlin-port/validation/side-by-side/mid-b/pass2 raw/outlier-factors/artifacts/sbs-mid-b-pass2.kt.factors.json
```

rc: py=0 kt=0; wall: py 285.2 s, kt 283.6 s

artifact `factors.json`: DIFF

```
== key-set differences ==
[0]/freq_cl0_mhz: only in kt
[3]/freq_cl0_mhz: only in kt
[4]/freq_cl0_mhz: only in kt
[5]/freq_cl0_mhz: only in kt
[7]/freq_cl0_mhz: only in kt
[8]/freq_cl0_mhz: only in kt
[10]/freq_cl0_mhz: only in kt
[11]/freq_cl0_mhz: only in kt
[13]/freq_cl0_mhz: only in kt
[14]/freq_cl0_mhz: only in kt
[15]/freq_cl0_mhz: only in kt
[17]/freq_cl0_mhz: only in kt
[18]/freq_cl0_mhz: only in kt
[19]/freq_cl0_mhz: only in kt
[20]/freq_cl0_mhz: only in kt
[21]/freq_cl0_mhz: only in kt
[22]/freq_cl0_mhz: only in kt
[23]/freq_cl0_mhz: only in kt
[25]/freq_cl0_mhz: only in kt
[26]/freq_cl0_mhz: only in kt
[27]/freq_cl0_mhz: only in kt
[28]/freq_cl0_mhz: only in kt
[30]/freq_cl0_mhz: only in kt
[31]/freq_cl0_mhz: only in kt
[32]/freq_cl0_mhz: only in kt
[33]/freq_cl0_mhz: only in kt
[34]/freq_cl0_mhz: only in kt
[35]/freq_cl0_mhz: only in kt
[37]/freq_cl0_mhz: only in kt
[38]/freq_cl0_mhz: only in kt
[39]/freq_cl0_mhz: only in kt
[40]/freq_cl0_mhz: only in kt
[41]/freq_cl0_mhz: only in kt
[42]/freq_cl0_mhz: only in kt
[43]/freq_cl0_mhz: only in kt
[44]/freq_cl0_mhz: only in kt
[46]/freq_cl0_mhz: only in kt
[47]/freq_cl0_mhz: only in kt
[48]/freq_cl0_mhz: only in kt
[49]/freq_cl0_mhz: only in kt

== value differences ==
```

### `outlier-factors` / `sbs-mid-b-pass3` - verdict c

C2: `freq_cl0_mhz: null` on 36/50 iterations (Kotlin only); no value differs.

```
py: python3 .claude/skills/startup-multi-device-analysis/scripts/outlier_factors.py ~/.cache/embrace-startup-tools/trace_processor/v46.0/trace_processor claude-output/2026-08-26-kotlin-port/validation/side-by-side/mid-b/pass3 raw/outlier-factors/artifacts/sbs-mid-b-pass3.py.factors.json
kt: tools/startup outlier-factors claude-output/2026-08-26-kotlin-port/validation/side-by-side/mid-b/pass3 raw/outlier-factors/artifacts/sbs-mid-b-pass3.kt.factors.json
```

rc: py=0 kt=0; wall: py 276.2 s, kt 273.9 s

artifact `factors.json`: DIFF

```
== key-set differences ==
[0]/freq_cl0_mhz: only in kt
[2]/freq_cl0_mhz: only in kt
[3]/freq_cl0_mhz: only in kt
[4]/freq_cl0_mhz: only in kt
[5]/freq_cl0_mhz: only in kt
[6]/freq_cl0_mhz: only in kt
[8]/freq_cl0_mhz: only in kt
[9]/freq_cl0_mhz: only in kt
[10]/freq_cl0_mhz: only in kt
[14]/freq_cl0_mhz: only in kt
[15]/freq_cl0_mhz: only in kt
[16]/freq_cl0_mhz: only in kt
[17]/freq_cl0_mhz: only in kt
[19]/freq_cl0_mhz: only in kt
[20]/freq_cl0_mhz: only in kt
[23]/freq_cl0_mhz: only in kt
[25]/freq_cl0_mhz: only in kt
[26]/freq_cl0_mhz: only in kt
[28]/freq_cl0_mhz: only in kt
[29]/freq_cl0_mhz: only in kt
[30]/freq_cl0_mhz: only in kt
[32]/freq_cl0_mhz: only in kt
[33]/freq_cl0_mhz: only in kt
[34]/freq_cl0_mhz: only in kt
[36]/freq_cl0_mhz: only in kt
[37]/freq_cl0_mhz: only in kt
[38]/freq_cl0_mhz: only in kt
[39]/freq_cl0_mhz: only in kt
[40]/freq_cl0_mhz: only in kt
[41]/freq_cl0_mhz: only in kt
[42]/freq_cl0_mhz: only in kt
[43]/freq_cl0_mhz: only in kt
[44]/freq_cl0_mhz: only in kt
[45]/freq_cl0_mhz: only in kt
[46]/freq_cl0_mhz: only in kt
[49]/freq_cl0_mhz: only in kt

== value differences ==
```

### `outlier-factors` / `vfm-mid-b-pass1` - verdict c

C2: `freq_cl0_mhz: null` on 29/50 iterations (Kotlin only); no value differs.

```
py: python3 .claude/skills/startup-multi-device-analysis/scripts/outlier_factors.py ~/.cache/embrace-startup-tools/trace_processor/v46.0/trace_processor claude-output/2026-08-26-kotlin-port/validation/vfm/mid-b__9.2.0__reference/pass1 raw/outlier-factors/artifacts/vfm-mid-b-pass1.py.factors.json
kt: tools/startup outlier-factors claude-output/2026-08-26-kotlin-port/validation/vfm/mid-b__9.2.0__reference/pass1 raw/outlier-factors/artifacts/vfm-mid-b-pass1.kt.factors.json
```

rc: py=0 kt=0; wall: py 264.2 s, kt 263.0 s

artifact `factors.json`: DIFF

```
== key-set differences ==
[0]/freq_cl0_mhz: only in kt
[1]/freq_cl0_mhz: only in kt
[2]/freq_cl0_mhz: only in kt
[3]/freq_cl0_mhz: only in kt
[4]/freq_cl0_mhz: only in kt
[8]/freq_cl0_mhz: only in kt
[11]/freq_cl0_mhz: only in kt
[12]/freq_cl0_mhz: only in kt
[14]/freq_cl0_mhz: only in kt
[16]/freq_cl0_mhz: only in kt
[17]/freq_cl0_mhz: only in kt
[20]/freq_cl0_mhz: only in kt
[22]/freq_cl0_mhz: only in kt
[23]/freq_cl0_mhz: only in kt
[24]/freq_cl0_mhz: only in kt
[25]/freq_cl0_mhz: only in kt
[26]/freq_cl0_mhz: only in kt
[27]/freq_cl0_mhz: only in kt
[28]/freq_cl0_mhz: only in kt
[29]/freq_cl0_mhz: only in kt
[31]/freq_cl0_mhz: only in kt
[32]/freq_cl0_mhz: only in kt
[33]/freq_cl0_mhz: only in kt
[35]/freq_cl0_mhz: only in kt
[36]/freq_cl0_mhz: only in kt
[40]/freq_cl0_mhz: only in kt
[42]/freq_cl0_mhz: only in kt
[44]/freq_cl0_mhz: only in kt
[46]/freq_cl0_mhz: only in kt

== value differences ==
```

### `outlier-factors` / `fsf-arm-a` - verdict c

C2: `freq_cl0_mhz: null` on 29/50 iterations (Kotlin only); no value differs.

```
py: python3 .claude/skills/startup-multi-device-analysis/scripts/outlier_factors.py ~/.cache/embrace-startup-tools/trace_processor/v46.0/trace_processor claude-output/2026-09-03-first-session-fix/arm-a-9.2.0/pass1 raw/outlier-factors/artifacts/fsf-arm-a.py.factors.json
kt: tools/startup outlier-factors claude-output/2026-09-03-first-session-fix/arm-a-9.2.0/pass1 raw/outlier-factors/artifacts/fsf-arm-a.kt.factors.json
```

rc: py=0 kt=0; wall: py 306.8 s, kt 306.0 s

artifact `factors.json`: DIFF

```
== key-set differences ==
[0]/freq_cl0_mhz: only in kt
[1]/freq_cl0_mhz: only in kt
[2]/freq_cl0_mhz: only in kt
[5]/freq_cl0_mhz: only in kt
[6]/freq_cl0_mhz: only in kt
[7]/freq_cl0_mhz: only in kt
[9]/freq_cl0_mhz: only in kt
[10]/freq_cl0_mhz: only in kt
[11]/freq_cl0_mhz: only in kt
[12]/freq_cl0_mhz: only in kt
[13]/freq_cl0_mhz: only in kt
[15]/freq_cl0_mhz: only in kt
[17]/freq_cl0_mhz: only in kt
[21]/freq_cl0_mhz: only in kt
[23]/freq_cl0_mhz: only in kt
[25]/freq_cl0_mhz: only in kt
[27]/freq_cl0_mhz: only in kt
[28]/freq_cl0_mhz: only in kt
[30]/freq_cl0_mhz: only in kt
[33]/freq_cl0_mhz: only in kt
[34]/freq_cl0_mhz: only in kt
[35]/freq_cl0_mhz: only in kt
[37]/freq_cl0_mhz: only in kt
[38]/freq_cl0_mhz: only in kt
[39]/freq_cl0_mhz: only in kt
[43]/freq_cl0_mhz: only in kt
[44]/freq_cl0_mhz: only in kt
[46]/freq_cl0_mhz: only in kt
[47]/freq_cl0_mhz: only in kt

== value differences ==
```

### `outlier-factors` / `fsf-arm-b` - verdict c

C2: `freq_cl0_mhz: null` on 30/50 iterations (Kotlin only); no value differs.

```
py: python3 .claude/skills/startup-multi-device-analysis/scripts/outlier_factors.py ~/.cache/embrace-startup-tools/trace_processor/v46.0/trace_processor claude-output/2026-09-03-first-session-fix/arm-b-fix/pass1 raw/outlier-factors/artifacts/fsf-arm-b.py.factors.json
kt: tools/startup outlier-factors claude-output/2026-09-03-first-session-fix/arm-b-fix/pass1 raw/outlier-factors/artifacts/fsf-arm-b.kt.factors.json
```

rc: py=0 kt=0; wall: py 284.1 s, kt 283.0 s

artifact `factors.json`: DIFF

```
== key-set differences ==
[0]/freq_cl0_mhz: only in kt
[3]/freq_cl0_mhz: only in kt
[6]/freq_cl0_mhz: only in kt
[7]/freq_cl0_mhz: only in kt
[9]/freq_cl0_mhz: only in kt
[10]/freq_cl0_mhz: only in kt
[12]/freq_cl0_mhz: only in kt
[13]/freq_cl0_mhz: only in kt
[16]/freq_cl0_mhz: only in kt
[17]/freq_cl0_mhz: only in kt
[21]/freq_cl0_mhz: only in kt
[22]/freq_cl0_mhz: only in kt
[25]/freq_cl0_mhz: only in kt
[26]/freq_cl0_mhz: only in kt
[28]/freq_cl0_mhz: only in kt
[29]/freq_cl0_mhz: only in kt
[30]/freq_cl0_mhz: only in kt
[31]/freq_cl0_mhz: only in kt
[33]/freq_cl0_mhz: only in kt
[34]/freq_cl0_mhz: only in kt
[36]/freq_cl0_mhz: only in kt
[37]/freq_cl0_mhz: only in kt
[38]/freq_cl0_mhz: only in kt
[39]/freq_cl0_mhz: only in kt
[41]/freq_cl0_mhz: only in kt
[42]/freq_cl0_mhz: only in kt
[44]/freq_cl0_mhz: only in kt
[45]/freq_cl0_mhz: only in kt
[46]/freq_cl0_mhz: only in kt
[48]/freq_cl0_mhz: only in kt

== value differences ==
```

### `outlier-factors` / `fsf-arm-c` - verdict c

C2: `freq_cl0_mhz: null` on 36/50 iterations (Kotlin only); no value differs.

```
py: python3 .claude/skills/startup-multi-device-analysis/scripts/outlier_factors.py ~/.cache/embrace-startup-tools/trace_processor/v46.0/trace_processor claude-output/2026-09-03-first-session-fix/arm-c-expired-hook/pass1 raw/outlier-factors/artifacts/fsf-arm-c.py.factors.json
kt: tools/startup outlier-factors claude-output/2026-09-03-first-session-fix/arm-c-expired-hook/pass1 raw/outlier-factors/artifacts/fsf-arm-c.kt.factors.json
```

rc: py=0 kt=0; wall: py 278.6 s, kt 277.6 s

artifact `factors.json`: DIFF

```
== key-set differences ==
[3]/freq_cl0_mhz: only in kt
[4]/freq_cl0_mhz: only in kt
[5]/freq_cl0_mhz: only in kt
[6]/freq_cl0_mhz: only in kt
[7]/freq_cl0_mhz: only in kt
[8]/freq_cl0_mhz: only in kt
[9]/freq_cl0_mhz: only in kt
[10]/freq_cl0_mhz: only in kt
[11]/freq_cl0_mhz: only in kt
[12]/freq_cl0_mhz: only in kt
[13]/freq_cl0_mhz: only in kt
[17]/freq_cl0_mhz: only in kt
[19]/freq_cl0_mhz: only in kt
[20]/freq_cl0_mhz: only in kt
[21]/freq_cl0_mhz: only in kt
[23]/freq_cl0_mhz: only in kt
[24]/freq_cl0_mhz: only in kt
[26]/freq_cl0_mhz: only in kt
[27]/freq_cl0_mhz: only in kt
[29]/freq_cl0_mhz: only in kt
[31]/freq_cl0_mhz: only in kt
[33]/freq_cl0_mhz: only in kt
[34]/freq_cl0_mhz: only in kt
[35]/freq_cl0_mhz: only in kt
[36]/freq_cl0_mhz: only in kt
[37]/freq_cl0_mhz: only in kt
[39]/freq_cl0_mhz: only in kt
[40]/freq_cl0_mhz: only in kt
[41]/freq_cl0_mhz: only in kt
[42]/freq_cl0_mhz: only in kt
[43]/freq_cl0_mhz: only in kt
[44]/freq_cl0_mhz: only in kt
[45]/freq_cl0_mhz: only in kt
[46]/freq_cl0_mhz: only in kt
[48]/freq_cl0_mhz: only in kt
[49]/freq_cl0_mhz: only in kt

== value differences ==
```

### `outlier-factors` / `fsf-arm-d` - verdict c

C2: `freq_cl0_mhz: null` on 37/50 iterations (Kotlin only); no value differs.

```
py: python3 .claude/skills/startup-multi-device-analysis/scripts/outlier_factors.py ~/.cache/embrace-startup-tools/trace_processor/v46.0/trace_processor claude-output/2026-09-03-first-session-fix/arm-d-expired-hook-fix/pass1 raw/outlier-factors/artifacts/fsf-arm-d.py.factors.json
kt: tools/startup outlier-factors claude-output/2026-09-03-first-session-fix/arm-d-expired-hook-fix/pass1 raw/outlier-factors/artifacts/fsf-arm-d.kt.factors.json
```

rc: py=0 kt=0; wall: py 257.5 s, kt 257.4 s

artifact `factors.json`: DIFF

```
== key-set differences ==
[1]/freq_cl0_mhz: only in kt
[3]/freq_cl0_mhz: only in kt
[6]/freq_cl0_mhz: only in kt
[7]/freq_cl0_mhz: only in kt
[8]/freq_cl0_mhz: only in kt
[9]/freq_cl0_mhz: only in kt
[10]/freq_cl0_mhz: only in kt
[11]/freq_cl0_mhz: only in kt
[12]/freq_cl0_mhz: only in kt
[14]/freq_cl0_mhz: only in kt
[15]/freq_cl0_mhz: only in kt
[17]/freq_cl0_mhz: only in kt
[18]/freq_cl0_mhz: only in kt
[19]/freq_cl0_mhz: only in kt
[20]/freq_cl0_mhz: only in kt
[21]/freq_cl0_mhz: only in kt
[22]/freq_cl0_mhz: only in kt
[23]/freq_cl0_mhz: only in kt
[24]/freq_cl0_mhz: only in kt
[25]/freq_cl0_mhz: only in kt
[26]/freq_cl0_mhz: only in kt
[27]/freq_cl0_mhz: only in kt
[30]/freq_cl0_mhz: only in kt
[31]/freq_cl0_mhz: only in kt
[32]/freq_cl0_mhz: only in kt
[33]/freq_cl0_mhz: only in kt
[34]/freq_cl0_mhz: only in kt
[35]/freq_cl0_mhz: only in kt
[36]/freq_cl0_mhz: only in kt
[39]/freq_cl0_mhz: only in kt
[41]/freq_cl0_mhz: only in kt
[42]/freq_cl0_mhz: only in kt
[43]/freq_cl0_mhz: only in kt
[44]/freq_cl0_mhz: only in kt
[45]/freq_cl0_mhz: only in kt
[46]/freq_cl0_mhz: only in kt
[47]/freq_cl0_mhz: only in kt

== value differences ==
```

### `ingest-force` / `fx-flagship-a` - verdict b+c (#8, #5)

Sequential re-run. Expected departures: recipe.trace_processor_version v46.0 vs v57.2 and the launcher path (#8); signals_present [] vs 60 names and signals_from_clean_trace false vs true (#5). NEW (C4): device_profile is `{}` in the Python record (no provenance in the fixture dir) but `{api_level:null, release:null, vendor:null, soc_family:null, clusters:null, ram_class:null, storage_class:null}` in the Kotlin one (DeviceProfile encoded with explicitNulls). NEW (C6): trace_health.parse_errors 0 vs 5 - a consequence of #5 not listed there: with canary `composed` the Python's check_trace verdict is `missing-canary` for EVERY trace, which pre-empts the `slices-incomplete` branch, so the Python never counts parse errors under the composed instrument, while the Kotlin (canary emb-modules-init) counts the 5 out-of-order traces. The first concurrent pass also showed n=19 vs 20 (C5, temp-file race), gone after the sequential re-run.

```
py: python3 .claude/skills/startup-longitudinal-tracking/scripts/ingest_run.py claude-output/2026-08-26-kotlin-port/fixtures/traces/flagship-a --reference-set claude-output/longitudinal/reference-set.json --store raw/ingest-force/artifacts/fx-flagship-a.py.store.jsonl --device-key flagship-a --trace-processor ~/.cache/embrace-startup-tools/trace_processor/v46.0/trace_processor --force
kt: tools/startup ingest claude-output/2026-08-26-kotlin-port/fixtures/traces/flagship-a --reference-set claude-output/longitudinal/reference-set.json --store raw/ingest-force/artifacts/fx-flagship-a.kt.store.jsonl --device-key flagship-a --force
```

rc: py=0 kt=0; wall: py 8.2 s, kt 4.4 s

artifact `store.jsonl`: DIFF

```
== key-set differences ==
[0]/device_profile/api_level: only in kt
[0]/device_profile/clusters: only in kt
[0]/device_profile/ram_class: only in kt
[0]/device_profile/release: only in kt
[0]/device_profile/soc_family: only in kt
[0]/device_profile/storage_class: only in kt
[0]/device_profile/vendor: only in kt

== value differences ==
[0]/recipe/trace_processor_path: py='<home>/.cache/embrace-startup-tools/trace_processor/v46.0/trace_processor' kt='<home>/.cache/embrace-startup-tools/trace_processor_shell/v57.2/trace_processor_shell'
[0]/recipe/trace_processor_version: py='v46.0' kt='v57.2'
[0]/signals_present: list length py=0 kt=60
[0]/trace_health/parse_errors: py=0 kt=5
[0]/trace_health/signals_from_clean_trace: py=False (bool) kt=True (bool)
```

### `ingest-force` / `fx-mid-a` - verdict b+c (#8, #5)

Sequential re-run. #8 (engine label/path), #5 (signals_present 0 vs 61, signals_from_clean_trace), C4 (device_profile {} vs all-null keys). Windows and derived identical.

```
py: python3 .claude/skills/startup-longitudinal-tracking/scripts/ingest_run.py claude-output/2026-08-26-kotlin-port/fixtures/traces/mid-a --reference-set claude-output/longitudinal/reference-set.json --store raw/ingest-force/artifacts/fx-mid-a.py.store.jsonl --device-key mid-a --trace-processor ~/.cache/embrace-startup-tools/trace_processor/v46.0/trace_processor --force
kt: tools/startup ingest claude-output/2026-08-26-kotlin-port/fixtures/traces/mid-a --reference-set claude-output/longitudinal/reference-set.json --store raw/ingest-force/artifacts/fx-mid-a.kt.store.jsonl --device-key mid-a --force
```

rc: py=0 kt=0; wall: py 5.2 s, kt 3.0 s

artifact `store.jsonl`: DIFF

```
== key-set differences ==
[0]/device_profile/api_level: only in kt
[0]/device_profile/clusters: only in kt
[0]/device_profile/ram_class: only in kt
[0]/device_profile/release: only in kt
[0]/device_profile/soc_family: only in kt
[0]/device_profile/storage_class: only in kt
[0]/device_profile/vendor: only in kt

== value differences ==
[0]/recipe/trace_processor_path: py='<home>/.cache/embrace-startup-tools/trace_processor/v46.0/trace_processor' kt='<home>/.cache/embrace-startup-tools/trace_processor_shell/v57.2/trace_processor_shell'
[0]/recipe/trace_processor_version: py='v46.0' kt='v57.2'
[0]/signals_present: list length py=0 kt=61
[0]/trace_health/signals_from_clean_trace: py=False (bool) kt=True (bool)
```

### `ingest-force` / `fx-mid-b` - verdict b+c (#8, #5)

Sequential re-run. #8, #5, C4 as above. Windows and derived identical.

```
py: python3 .claude/skills/startup-longitudinal-tracking/scripts/ingest_run.py claude-output/2026-08-26-kotlin-port/fixtures/traces/mid-b --reference-set claude-output/longitudinal/reference-set.json --store raw/ingest-force/artifacts/fx-mid-b.py.store.jsonl --device-key mid-b --trace-processor ~/.cache/embrace-startup-tools/trace_processor/v46.0/trace_processor --force
kt: tools/startup ingest claude-output/2026-08-26-kotlin-port/fixtures/traces/mid-b --reference-set claude-output/longitudinal/reference-set.json --store raw/ingest-force/artifacts/fx-mid-b.kt.store.jsonl --device-key mid-b --force
```

rc: py=0 kt=0; wall: py 4.3 s, kt 2.4 s

artifact `store.jsonl`: DIFF

```
== key-set differences ==
[0]/device_profile/api_level: only in kt
[0]/device_profile/clusters: only in kt
[0]/device_profile/ram_class: only in kt
[0]/device_profile/release: only in kt
[0]/device_profile/soc_family: only in kt
[0]/device_profile/storage_class: only in kt
[0]/device_profile/vendor: only in kt

== value differences ==
[0]/recipe/trace_processor_path: py='<home>/.cache/embrace-startup-tools/trace_processor/v46.0/trace_processor' kt='<home>/.cache/embrace-startup-tools/trace_processor_shell/v57.2/trace_processor_shell'
[0]/recipe/trace_processor_version: py='v46.0' kt='v57.2'
[0]/signals_present: list length py=0 kt=61
[0]/trace_health/signals_from_clean_trace: py=False (bool) kt=True (bool)
```

### `ingest-force` / `vfm-cell` - verdict b+c (#8, #5)

Sequential re-run. #8, #5, and C4 in its second form: the cell-state.json provenance carries a NESTED device_profile (`{api_level, tier, vendor, cool_gate_c, profile:{...}}`, written by the Python cell_runner shape); the Python stores that object verbatim, the Kotlin stores `{api_level:31, vendor:"Google", release:null, soc_family:null, clusters:null, ram_class:null, storage_class:null}` - it reads the flat DeviceProfile fields and drops `tier`, `cool_gate_c` and the nested `profile`. Windows and derived identical.

```
py: python3 .claude/skills/startup-longitudinal-tracking/scripts/ingest_run.py claude-output/2026-08-26-kotlin-port/validation/vfm/mid-b__9.2.0__reference --reference-set claude-output/longitudinal/reference-set.json --store raw/ingest-force/artifacts/vfm-cell.py.store.jsonl --device-key mid-b --trace-processor ~/.cache/embrace-startup-tools/trace_processor/v46.0/trace_processor --force
kt: tools/startup ingest claude-output/2026-08-26-kotlin-port/validation/vfm/mid-b__9.2.0__reference --reference-set claude-output/longitudinal/reference-set.json --store raw/ingest-force/artifacts/vfm-cell.kt.store.jsonl --device-key mid-b --force
```

rc: py=0 kt=0; wall: py 9.7 s, kt 5.2 s

artifact `store.jsonl`: DIFF

```
== key-set differences ==
[0]/device_profile/cool_gate_c: only in py
[0]/device_profile/profile: only in py
[0]/device_profile/tier: only in py
[0]/device_profile/clusters: only in kt
[0]/device_profile/ram_class: only in kt
[0]/device_profile/release: only in kt
[0]/device_profile/soc_family: only in kt
[0]/device_profile/storage_class: only in kt

== value differences ==
[0]/recipe/trace_processor_path: py='<home>/.cache/embrace-startup-tools/trace_processor/v46.0/trace_processor' kt='<home>/.cache/embrace-startup-tools/trace_processor_shell/v57.2/trace_processor_shell'
[0]/recipe/trace_processor_version: py='v46.0' kt='v57.2'
[0]/signals_present: list length py=0 kt=61
[0]/trace_health/signals_from_clean_trace: py=False (bool) kt=True (bool)
```

### `reference-set-show` / `longitudinal` - verdict c

C3: JSON pretty-print indentation. Python `json.dumps(doc, indent=1)` (1 space per level) vs Kotlin 4 spaces. After stripping leading whitespace the two outputs are byte-identical (compare/summary.json `stdout_differs_only_in_leading_whitespace`), including the COVERAGE GAP lines.

```
py: python3 .claude/skills/startup-longitudinal-tracking/scripts/reference_set.py --show claude-output/longitudinal/reference-set.json
kt: tools/startup reference-set --show claude-output/longitudinal/reference-set.json
```

rc: py=0 kt=0; wall: py 0.1 s, kt 0.5 s

stdout diff (after masking):

```diff
--- py.stdout
+++ kt.stdout
@@ -1,91 +1,91 @@
 {
- "declared_at": "<TS>",
- "recipe": {
-  "run_shape": {
-   "passes": 10,
-   "iterations": 20
-  },
-  "build_type": "benchmark",
-  "compile_state": "profile",
-  "instrument": "composed",
-  "_comment": "Changing any of these starts a NEW comparable series; see references/store.md"
- },
- "devices": {
-  "flagship-a": {
-   "serial": "flagship-a",
-   "tier": "flagship",
-   "profile": {
-    "api_level": 35,
-    "release": "15",
-    "vendor": "Google",
-    "soc_family": "GS201",
-    "clusters": [
-     1803000,
-     2348000,
-     2850000
-    ],
-    "ram_class": ">=8GB",
-    "storage_class": "unknown"
-   },
-   "cool_gate_c": 32.0,
-   "retired": false
-  },
-  "mid-b": {
-   "serial": "mid-b",
-   "tier": "entry-mid",
-   "profile": {
-    "api_level": 31,
-    "release": "12",
-    "vendor": "Google",
-    "soc_family": "SDM845",
-    "clusters": [
-     1766400,
-     2803200
-    ],
-    "ram_class": "3-4GB",
-    "storage_class": "unknown"
-   },
-   "cool_gate_c": 32.0,
-   "retired": false
-  },
-  "mid-a": {
-   "serial": "mid-a",
-   "tier": "entry-mid",
-   "profile": {
-    "api_level": 35,
-    "release": "15",
-    "vendor": "samsung",
-    "soc_family": "Exynos 850",
-    "clusters": [
-     2002000
-    ],
-    "ram_class": "3-4GB",
-    "storage_class": "unknown"
-   },
-   "cool_gate_c": 32.0,
-   "retired": false
-  },
-  "entry-a": {
-   "serial": "entry-a",
-   "tier": "entry",
-   "profile": {
-    "api_level": 29,
-    "release": "10",
-    "vendor": "samsung",
-    "soc_family": "mt6739",
-    "clusters": [
-     1495000
... (103 more lines; full diff in compare/reference-set-show/longitudinal.stdout.diff)
```

### `reference-set-show` / `fixture` - verdict c

C3 (indent 1 vs 4); identical after stripping leading whitespace.

```
py: python3 .claude/skills/startup-longitudinal-tracking/scripts/reference_set.py --show startup-tools/src/test/resources/fixtures/longitudinal/reference-set.json
kt: tools/startup reference-set --show startup-tools/src/test/resources/fixtures/longitudinal/reference-set.json
```

rc: py=0 kt=0; wall: py 0.1 s, kt 0.5 s

stdout diff (after masking):

```diff
--- py.stdout
+++ kt.stdout
@@ -1,91 +1,91 @@
 {
- "declared_at": "<TS>",
- "recipe": {
-  "run_shape": {
-   "passes": 10,
-   "iterations": 20
-  },
-  "build_type": "benchmark",
-  "compile_state": "profile",
-  "instrument": "composed",
-  "_comment": "Changing any of these starts a NEW comparable series; see references/store.md"
- },
- "devices": {
-  "flagship-a": {
-   "serial": "flagship-a",
-   "tier": "flagship",
-   "profile": {
-    "api_level": 35,
-    "release": "15",
-    "vendor": "Google",
-    "soc_family": "GS201",
-    "clusters": [
-     1803000,
-     2348000,
-     2850000
-    ],
-    "ram_class": ">=8GB",
-    "storage_class": "unknown"
-   },
-   "cool_gate_c": 32.0,
-   "retired": false
-  },
-  "mid-b": {
-   "serial": "mid-b",
-   "tier": "entry-mid",
-   "profile": {
-    "api_level": 31,
-    "release": "12",
-    "vendor": "Google",
-    "soc_family": "SDM845",
-    "clusters": [
-     1766400,
-     2803200
-    ],
-    "ram_class": "3-4GB",
-    "storage_class": "unknown"
-   },
-   "cool_gate_c": 32.0,
-   "retired": false
-  },
-  "mid-a": {
-   "serial": "mid-a",
-   "tier": "entry-mid",
-   "profile": {
-    "api_level": 35,
-    "release": "15",
-    "vendor": "samsung",
-    "soc_family": "Exynos 850",
-    "clusters": [
-     2002000
-    ],
-    "ram_class": "3-4GB",
-    "storage_class": "unknown"
-   },
-   "cool_gate_c": 32.0,
-   "retired": false
-  },
-  "entry-a": {
-   "serial": "entry-a",
-   "tier": "entry",
-   "profile": {
-    "api_level": 29,
-    "release": "10",
-    "vendor": "samsung",
-    "soc_family": "mt6739",
-    "clusters": [
-     1495000
... (103 more lines; full diff in compare/reference-set-show/fixture.stdout.diff)
```

### `reference-set-show` / `x25-live` - verdict c

C3 (indent 1 vs 4); identical after stripping leading whitespace.

```
py: python3 .claude/skills/startup-longitudinal-tracking/scripts/reference_set.py --show claude-output/2026-08-16-x25-artifacts/reference-set-LIVE-10x20.json
kt: tools/startup reference-set --show claude-output/2026-08-16-x25-artifacts/reference-set-LIVE-10x20.json
```

rc: py=0 kt=0; wall: py 0.1 s, kt 0.5 s

stdout diff (after masking):

```diff
--- py.stdout
+++ kt.stdout
@@ -1,91 +1,91 @@
 {
- "declared_at": "<TS>",
- "recipe": {
-  "run_shape": {
-   "passes": 10,
-   "iterations": 20
-  },
-  "build_type": "benchmark",
-  "compile_state": "profile",
-  "instrument": "composed",
-  "_comment": "Changing any of these starts a NEW comparable series; see references/store.md"
- },
- "devices": {
-  "flagship-a": {
-   "serial": "flagship-a",
-   "tier": "flagship",
-   "profile": {
-    "api_level": 35,
-    "release": "15",
-    "vendor": "Google",
-    "soc_family": "GS201",
-    "clusters": [
-     1803000,
-     2348000,
-     2850000
-    ],
-    "ram_class": ">=8GB",
-    "storage_class": "unknown"
-   },
-   "cool_gate_c": 32.0,
-   "retired": false
-  },
-  "mid-b": {
-   "serial": "mid-b",
-   "tier": "entry-mid",
-   "profile": {
-    "api_level": 31,
-    "release": "12",
-    "vendor": "Google",
-    "soc_family": "SDM845",
-    "clusters": [
-     1766400,
-     2803200
-    ],
-    "ram_class": "3-4GB",
-    "storage_class": "unknown"
-   },
-   "cool_gate_c": 32.0,
-   "retired": false
-  },
-  "mid-a": {
-   "serial": "mid-a",
-   "tier": "entry-mid",
-   "profile": {
-    "api_level": 35,
-    "release": "15",
-    "vendor": "samsung",
-    "soc_family": "Exynos 850",
-    "clusters": [
-     2002000
-    ],
-    "ram_class": "3-4GB",
-    "storage_class": "unknown"
-   },
-   "cool_gate_c": 32.0,
-   "retired": false
-  },
-  "entry-a": {
-   "serial": "entry-a",
-   "tier": "entry",
-   "profile": {
-    "api_level": 29,
-    "release": "10",
-    "vendor": "samsung",
-    "soc_family": "mt6739",
-    "clusters": [
-     1495000
... (103 more lines; full diff in compare/reference-set-show/x25-live.stdout.diff)
```

### `reference-set-show` / `x25-archive` - verdict c

C3 (indent 1 vs 4); identical after stripping leading whitespace.

```
py: python3 .claude/skills/startup-longitudinal-tracking/scripts/reference_set.py --show claude-output/2026-08-16-x25-artifacts/reference-set-ARCHIVE-4x50-reconstructed.json
kt: tools/startup reference-set --show claude-output/2026-08-16-x25-artifacts/reference-set-ARCHIVE-4x50-reconstructed.json
```

rc: py=0 kt=0; wall: py 0.1 s, kt 0.5 s

stdout diff (after masking):

```diff
--- py.stdout
+++ kt.stdout
@@ -1,92 +1,92 @@
 {
- "declared_at": null,
- "_reconstructed": {
-  "why": "the original was overwritten when the campaign restarted and re-archived the live reference set on top of it",
-  "recipe_and_profiles_from": "<scratch>",
-  "serial_tier_coolgate_from": "<scratch>",
-  "declared_at": "NOT RECOVERABLE - the original timestamp was lost with the file"
- },
- "recipe": {
-  "build_type": "benchmark",
-  "compile_state": "profile",
-  "instrument": "composed",
-  "run_shape": {
-   "iterations": 50,
-   "passes": 4
-  },
-  "_comment": "Changing any of these starts a NEW comparable series; see references/store.md"
- },
- "devices": {
-  "flagship-a": {
-   "serial": "flagship-a",
-   "tier": "flagship",
-   "profile": {
-    "api_level": 35,
-    "release": "15",
-    "vendor": "Google",
-    "soc_family": "GS201",
-    "clusters": [
-     1803000,
-     2348000,
-     2850000
-    ],
-    "ram_class": ">=8GB",
-    "storage_class": "unknown"
-   },
-   "cool_gate_c": 32.0,
-   "retired": false
-  },
-  "mid-b": {
-   "serial": "mid-b",
-   "tier": "entry-mid",
-   "profile": {
-    "api_level": 31,
-    "release": "12",
-    "vendor": "Google",
-    "soc_family": "SDM845",
-    "clusters": [
-     1766400,
-     2803200
-    ],
-    "ram_class": "3-4GB",
-    "storage_class": "unknown"
-   },
-   "cool_gate_c": 32.0,
-   "retired": false
-  },
-  "entry-a": {
-   "serial": "entry-a",
-   "tier": "entry",
-   "profile": {
-    "api_level": 29,
-    "release": "10",
-    "vendor": "samsung",
-    "soc_family": "mt6739",
-    "clusters": [
-     1495000
-    ],
-    "ram_class": "<=2GB",
-    "storage_class": "unknown"
-   },
-   "cool_gate_c": 32.0,
-   "retired": false
-  },
-  "mid-a": {
-   "serial": "mid-a",
-   "tier": "entry-mid",
-   "profile": {
... (105 more lines; full diff in compare/reference-set-show/x25-archive.stdout.diff)
```

### `reference-set-show` / `maxims-fixture` - verdict c

C3 (indent 1 vs 4); identical after stripping leading whitespace.

```
py: python3 .claude/skills/startup-longitudinal-tracking/scripts/reference_set.py --show startup-tools/src/test/resources/fixtures/maxims/reference-set.json
kt: tools/startup reference-set --show startup-tools/src/test/resources/fixtures/maxims/reference-set.json
```

rc: py=0 kt=0; wall: py 0.1 s, kt 0.6 s

stdout diff (after masking):

```diff
--- py.stdout
+++ kt.stdout
@@ -1,45 +1,45 @@
 {
- "recipe": {
-  "run_shape": {
-   "passes": 4,
-   "iterations": 20
-  },
-  "build_type": "benchmark",
-  "compile_state": "profile",
-  "instrument": "emb-sdk-start"
- },
- "devices": {
-  "flagship-a": {
-   "serial": "flagship-a",
-   "tier": "flagship",
-   "profile": {
-    "api_level": 35,
-    "release": "15",
-    "vendor": "Google",
-    "soc_family": "GS201",
-    "clusters": [
-     1803000,
-     2348000,
-     2850000
-    ],
-    "ram_class": ">=8GB",
-    "storage_class": "unknown"
-   }
-  },
-  "mid-a": {
-   "serial": "mid-a",
-   "tier": "entry-mid",
-   "profile": {
-    "api_level": 35,
-    "release": "15",
-    "vendor": "samsung",
-    "soc_family": "Exynos 850",
-    "clusters": [
-     2002000
-    ],
-    "ram_class": "3-4GB",
-    "storage_class": "unknown"
-   }
-  }
- }
+    "recipe": {
+        "run_shape": {
+            "passes": 4,
+            "iterations": 20
+        },
+        "build_type": "benchmark",
+        "compile_state": "profile",
+        "instrument": "emb-sdk-start"
+    },
+    "devices": {
+        "flagship-a": {
+            "serial": "flagship-a",
+            "tier": "flagship",
+            "profile": {
+                "api_level": 35,
+                "release": "15",
+                "vendor": "Google",
+                "soc_family": "GS201",
+                "clusters": [
+                    1803000,
+                    2348000,
+                    2850000
+                ],
+                "ram_class": ">=8GB",
+                "storage_class": "unknown"
+            }
+        },
+        "mid-a": {
+            "serial": "mid-a",
+            "tier": "entry-mid",
+            "profile": {
+                "api_level": 35,
+                "release": "15",
... (11 more lines; full diff in compare/reference-set-show/maxims-fixture.stdout.diff)
```

### `submit-dry-run` / `sbs-store-kotlin-final__mid-b` - verdict c

C3: the redacted submission is printed with indent 1 (Python) vs 4 (Kotlin); identical after stripping leading whitespace (same fields, same 61 signals, same derived values). Both sides ADMIT this record (rc 0) - it is the Kotlin-ingested record, whose signals came from a clean trace.

```
py: python3 .claude/skills/startup-global-corpus/scripts/submit_run.py --store claude-output/2026-08-26-kotlin-port/validation/side-by-side/store-kotlin-final.jsonl --run-id mid-b --corpus <scratch>/submit/sbs-store-kotlin-final__mid-b/corpus.jsonl --contributor shadow --dry-run
kt: tools/startup submit --store claude-output/2026-08-26-kotlin-port/validation/side-by-side/store-kotlin-final.jsonl --run-id mid-b --corpus <scratch>/submit/sbs-store-kotlin-final__mid-b/corpus.jsonl --contributor shadow --dry-run
```

rc: py=0 kt=0; wall: py 0.1 s, kt 0.6 s

stdout diff (after masking):

```diff
--- py.stdout
+++ kt.stdout
@@ -1,100 +1,100 @@
 {
- "schema_version": 1,
- "submission_id": "shadow-mid-b",
- "submitted_at": "<TS>",
- "contributor": "shadow",
- "sdk_version": "9.2.0",
- "app_build_id": null,
- "recipe": {
-  "build_type": "benchmark",
-  "compile_state": "profile",
-  "instrument": "emb-sdk-start",
-  "run_shape": {
-   "passes": 3,
-   "iterations": 50
-  },
-  "trace_processor_version": "v57.2",
-  "trace_processor_path": "<HOME>/.cache/embrace-startup-tools/trace_processor_shell/v57.2/trace_processor_shell"
- },
- "conditions": {
-  "compile": "profile"
- },
- "derived": {
-  "n": 150,
-  "median": 30.609847000000002,
-  "p90": 31.521253,
-  "p95": 31.650681,
-  "max": 33.30417,
-  "iqr": 0.9954169999999998
- },
- "signals_present": [
-  "emb-anr-snapshot",
-  "emb-behavior-check",
-  "emb-bootstrapper-init",
-  "emb-config-init",
-  "emb-config-service-init",
-  "emb-core-init",
-  "emb-create-new-session",
-  "emb-data-capture-service-init",
-  "emb-delivery-init",
-  "emb-deviceImpl",
-  "emb-embrace-impl-init",
-  "emb-essential-service-init",
-  "emb-feature-init",
-  "emb-init-module",
-  "emb-initiate-periodic-caching",
-  "emb-install-native-crash-signal-handlers",
-  "emb-instrumentation-init",
-  "emb-key-value-store-init",
-  "emb-load-embrace-native-lib",
-  "emb-load-instrumentation",
-  "emb-load-user-info-from-pref",
-  "emb-log-init",
-  "emb-metadata-service-init",
-  "emb-metadata-source",
-  "emb-modules-init",
-  "emb-native-install-handlers",
-  "emb-network-connectivity-service-init",
-  "emb-okhttp-client-init",
-  "emb-on-session-cache",
-  "emb-otel-external-export",
-  "emb-otel-logger-init",
-  "emb-otel-module",
-  "emb-otel-sdk-wrapper-init",
-  "emb-otel-tracer-init",
-  "emb-payload-source-init",
-  "emb-persisted-config-load",
-  "emb-post-init",
-  "emb-post-services-setup",
-  "emb-power-service-registration",
-  "emb-prefs-first-read",
-  "emb-prepare-new-session",
-  "emb-process-identifier-init",
-  "emb-process-state-service-init",
-  "emb-record-startup",
-  "emb-resource-source",
-  "emb-sdk-disable-check",
-  "emb-sdk-start",
... (121 more lines; full diff in compare/submit-dry-run/sbs-store-kotlin-final__mid-b.stdout.diff)
```

### `submit-dry-run` / `sbs-store-python__mid-b` - verdict c

C3 (indent only). NOTE: this is the store the PYTHON ingest wrote on 2026-09-02 and both sides admit it, so its signals_present is populated - see the side-by-side notes in PORT-LOG; the twelve records in longitudinal/store.jsonl and the four in sweep-store.jsonl are refused identically by both sides (PORT-LOG #5: `the signal inventory did not come from a clean trace`).

```
py: python3 .claude/skills/startup-global-corpus/scripts/submit_run.py --store claude-output/2026-08-26-kotlin-port/validation/side-by-side/store-python.jsonl --run-id mid-b --corpus <scratch>/submit/sbs-store-python__mid-b/corpus.jsonl --contributor shadow --dry-run
kt: tools/startup submit --store claude-output/2026-08-26-kotlin-port/validation/side-by-side/store-python.jsonl --run-id mid-b --corpus <scratch>/submit/sbs-store-python__mid-b/corpus.jsonl --contributor shadow --dry-run
```

rc: py=0 kt=0; wall: py 0.1 s, kt 0.5 s

stdout diff (after masking):

```diff
--- py.stdout
+++ kt.stdout
@@ -1,100 +1,100 @@
 {
- "schema_version": 1,
- "submission_id": "shadow-mid-b",
- "submitted_at": "<TS>",
- "contributor": "shadow",
- "sdk_version": "9.2.0",
- "app_build_id": null,
- "recipe": {
-  "build_type": "benchmark",
-  "compile_state": "profile",
-  "instrument": "emb-sdk-start",
-  "run_shape": {
-   "passes": 3,
-   "iterations": 50
-  },
-  "trace_processor_version": "v46.0",
-  "trace_processor_path": "<HOME>/.cache/embrace-startup-tools/trace_processor/v46.0/trace_processor"
- },
- "conditions": {
-  "compile": "profile"
- },
- "derived": {
-  "n": 150,
-  "median": 30.609847000000002,
-  "p90": 31.521253,
-  "p95": 31.650681,
-  "max": 33.30417,
-  "iqr": 0.9954169999999998
- },
- "signals_present": [
-  "emb-anr-snapshot",
-  "emb-behavior-check",
-  "emb-bootstrapper-init",
-  "emb-config-init",
-  "emb-config-service-init",
-  "emb-core-init",
-  "emb-create-new-session",
-  "emb-data-capture-service-init",
-  "emb-delivery-init",
-  "emb-deviceImpl",
-  "emb-embrace-impl-init",
-  "emb-essential-service-init",
-  "emb-feature-init",
-  "emb-init-module",
-  "emb-initiate-periodic-caching",
-  "emb-install-native-crash-signal-handlers",
-  "emb-instrumentation-init",
-  "emb-key-value-store-init",
-  "emb-load-embrace-native-lib",
-  "emb-load-instrumentation",
-  "emb-load-user-info-from-pref",
-  "emb-log-init",
-  "emb-metadata-service-init",
-  "emb-metadata-source",
-  "emb-modules-init",
-  "emb-native-install-handlers",
-  "emb-network-connectivity-service-init",
-  "emb-okhttp-client-init",
-  "emb-on-session-cache",
-  "emb-otel-external-export",
-  "emb-otel-logger-init",
-  "emb-otel-module",
-  "emb-otel-sdk-wrapper-init",
-  "emb-otel-tracer-init",
-  "emb-payload-source-init",
-  "emb-persisted-config-load",
-  "emb-post-init",
-  "emb-post-services-setup",
-  "emb-power-service-registration",
-  "emb-prefs-first-read",
-  "emb-prepare-new-session",
-  "emb-process-identifier-init",
-  "emb-process-state-service-init",
-  "emb-record-startup",
-  "emb-resource-source",
-  "emb-sdk-disable-check",
-  "emb-sdk-start",
... (121 more lines; full diff in compare/submit-dry-run/sbs-store-python__mid-b.stdout.diff)
```

### `submit-dry-run-serial` / `sbs-store-kotlin-final__mid-b` - verdict c

C3 (indent only). With --serial mid-b both sides collected the same device provenance from the Pixel 3 (model, os_build, security_patch, kernel, soc, storage 20-40%, battery_health 3, app count 50-150, settings) and derived the same unit_id 23ed8346bca11944 from the shared .unit-salt the Python wrote first.

```
py: python3 .claude/skills/startup-global-corpus/scripts/submit_run.py --store claude-output/2026-08-26-kotlin-port/validation/side-by-side/store-kotlin-final.jsonl --run-id mid-b --corpus <scratch>/submit/with-serial/corpus.jsonl --contributor shadow --serial mid-b --dry-run
kt: tools/startup submit --store claude-output/2026-08-26-kotlin-port/validation/side-by-side/store-kotlin-final.jsonl --run-id mid-b --corpus <scratch>/submit/with-serial/corpus.jsonl --contributor shadow --serial mid-b --dry-run
```

rc: py=0 kt=0; wall: py 1.0 s, kt 1.3 s

stdout diff (after masking):

```diff
--- py.stdout
+++ kt.stdout
@@ -1,116 +1,116 @@
 {
- "schema_version": 1,
- "submission_id": "shadow-mid-b",
- "submitted_at": "<TS>",
- "contributor": "shadow",
- "sdk_version": "9.2.0",
- "app_build_id": null,
- "recipe": {
-  "build_type": "benchmark",
-  "compile_state": "profile",
-  "instrument": "emb-sdk-start",
-  "run_shape": {
-   "passes": 3,
-   "iterations": 50
-  },
-  "trace_processor_version": "v57.2",
-  "trace_processor_path": "<HOME>/.cache/embrace-startup-tools/trace_processor_shell/v57.2/trace_processor_shell"
- },
- "conditions": {
-  "compile": "profile"
- },
- "derived": {
-  "n": 150,
-  "median": 30.609847000000002,
-  "p90": 31.521253,
-  "p95": 31.650681,
-  "max": 33.30417,
-  "iqr": 0.9954169999999998
- },
- "signals_present": [
-  "emb-anr-snapshot",
-  "emb-behavior-check",
-  "emb-bootstrapper-init",
-  "emb-config-init",
-  "emb-config-service-init",
-  "emb-core-init",
-  "emb-create-new-session",
-  "emb-data-capture-service-init",
-  "emb-delivery-init",
-  "emb-deviceImpl",
-  "emb-embrace-impl-init",
-  "emb-essential-service-init",
-  "emb-feature-init",
-  "emb-init-module",
-  "emb-initiate-periodic-caching",
-  "emb-install-native-crash-signal-handlers",
-  "emb-instrumentation-init",
-  "emb-key-value-store-init",
-  "emb-load-embrace-native-lib",
-  "emb-load-instrumentation",
-  "emb-load-user-info-from-pref",
-  "emb-log-init",
-  "emb-metadata-service-init",
-  "emb-metadata-source",
-  "emb-modules-init",
-  "emb-native-install-handlers",
-  "emb-network-connectivity-service-init",
-  "emb-okhttp-client-init",
-  "emb-on-session-cache",
-  "emb-otel-external-export",
-  "emb-otel-logger-init",
-  "emb-otel-module",
-  "emb-otel-sdk-wrapper-init",
-  "emb-otel-tracer-init",
-  "emb-payload-source-init",
-  "emb-persisted-config-load",
-  "emb-post-init",
-  "emb-post-services-setup",
-  "emb-power-service-registration",
-  "emb-prefs-first-read",
-  "emb-prepare-new-session",
-  "emb-process-identifier-init",
-  "emb-process-state-service-init",
-  "emb-record-startup",
-  "emb-resource-source",
-  "emb-sdk-disable-check",
-  "emb-sdk-start",
... (153 more lines; full diff in compare/submit-dry-run-serial/sbs-store-kotlin-final__mid-b.stdout.diff)
```

### `matrix-plan` / `skill-plan-example` - verdict b (#9)

--emit hint names `startup-tools cell-runner --cells` instead of `cell_runner.py --cells`; emitted cells.json tree-identical.

```
py: python3 .claude/skills/startup-version-factor-matrix/scripts/matrix_plan.py .claude/skills/startup-version-factor-matrix/plan-example.json --emit raw/matrix-plan/artifacts/skill-plan-example.py.cells.json
kt: tools/startup matrix-plan .claude/skills/startup-version-factor-matrix/plan-example.json --emit raw/matrix-plan/artifacts/skill-plan-example.kt.cells.json
```

rc: py=0 kt=0; wall: py 0.1 s, kt 0.6 s

stdout diff (after masking):

```diff
--- py.stdout
+++ kt.stdout
@@ -37,2 +37,2 @@
 
-wrote <RAW> - run cells with: cell_runner.py --cells <RAW> --cell <id>
+wrote <RAW> - run cells with: startup-tools cell-runner --cells <RAW> --cell <id>
```

### `matrix-plan` / `fixture-plan-example` - verdict b (#9)

same as above; cells.json tree-identical.

```
py: python3 .claude/skills/startup-version-factor-matrix/scripts/matrix_plan.py startup-tools/src/test/resources/fixtures/goldens/inputs/plan-example.json --emit raw/matrix-plan/artifacts/fixture-plan-example.py.cells.json
kt: tools/startup matrix-plan startup-tools/src/test/resources/fixtures/goldens/inputs/plan-example.json --emit raw/matrix-plan/artifacts/fixture-plan-example.kt.cells.json
```

rc: py=0 kt=0; wall: py 0.1 s, kt 0.6 s

stdout diff (after masking):

```diff
--- py.stdout
+++ kt.stdout
@@ -37,2 +37,2 @@
 
-wrote <RAW> - run cells with: cell_runner.py --cells <RAW> --cell <id>
+wrote <RAW> - run cells with: startup-tools cell-runner --cells <RAW> --cell <id>
```

### `matrix-report` / `vfm-default-slice` - verdict b (#20)

Python default --slice is the phantom `app-embrace-start` (prints `SKIP mid-b|9.2.0|reference: no window values (app-embrace-start missing?)` after the sequential re-run); Kotlin default is emb-sdk-start and renders the row.

```
py: python3 .claude/skills/startup-version-factor-matrix/scripts/matrix_report.py claude-output/2026-08-26-kotlin-port/validation/vfm --trace-processor ~/.cache/embrace-startup-tools/trace_processor/v46.0/trace_processor
kt: tools/startup matrix-report claude-output/2026-08-26-kotlin-port/validation/vfm
```

rc: py=0 kt=0; wall: py 4.8 s, kt 4.0 s

stdout diff (after masking):

```diff
--- py.stdout
+++ kt.stdout
@@ -1,6 +1,6 @@
-SKIP mid-b|9.2.0|reference: no window values (app-embrace-start missing?)
 
-instrument: app-embrace-start   (absolute values are build-type specific)
+instrument: emb-sdk-start   (absolute values are build-type specific)
 
 cell                                                          n      med      p90      max  pass medians
+mid-b|9.2.0|reference                                        50     26.4     27.5     28.6  26
 
```

### `matrix-report` / `vfm-composed` - verdict b (#20)

`--slice composed`: the Kotlin treats it as the composed-window sentinel (n=50, med 26.0); the Python looks for a slice literally named `composed` and SKIPs the cell.

```
py: python3 .claude/skills/startup-version-factor-matrix/scripts/matrix_report.py claude-output/2026-08-26-kotlin-port/validation/vfm --slice composed --trace-processor ~/.cache/embrace-startup-tools/trace_processor/v46.0/trace_processor
kt: tools/startup matrix-report claude-output/2026-08-26-kotlin-port/validation/vfm --slice composed
```

rc: py=0 kt=0; wall: py 4.9 s, kt 4.0 s

stdout diff (after masking):

```diff
--- py.stdout
+++ kt.stdout
@@ -1,2 +1 @@
-SKIP mid-b|9.2.0|reference: no window values (composed missing?)
 
@@ -5,2 +4,3 @@
 cell                                                          n      med      p90      max  pass medians
+mid-b|9.2.0|reference                                        50     26.0     27.2     28.2  26
 
```


## The (c) list: differences NOT in PORT-LOG.md

* **C1 `probe` stdout hint.** Python: `... Wrote <path> — pass --little-cpus 0,1,2,3 to variance_analysis.py, or set LITTLE_CPUS=0,1,2,3 for hypothesis_tests.py / factors_report.py, when analyzing this device's traces.`
  Kotlin: `... Wrote <path> — pass --little-cpus 0,1,2,3 to variance, hypothesis-tests and factors-report when analyzing this device's traces.`
  All four devices; the `<name>-topology.json` files are tree-identical. Consistent with #7 but the probe's advice line is not listed.
* **C2 `outlier-factors` writes `null` for scalars the query returned NULL; the Python omits the key.** Seen as
  `"freq_cl0_mhz": null` on 291 of 420 Pixel 3 iterations (every mid-b dir) and `"freq_limit_cl0": null, "freq_limit_cl1": null`
  on all 20 Pixel 7 Pro iterations; zero value differences in any `passN-factors.json`. Cause: `core/json/Codecs` has
  `explicitNulls = true` + `encodeDefaults = true` (introduced by #26 for STORE records) and `analysis/OutlierFactors.Record`
  declares every scalar as `Double? = null`, whose own doc comment says "the Python only emitted the rows the query returned
  non-NULL ... Downstream code must keep treating absence as 'not exported', never as zero". The Kotlin readers treat null and
  absent alike (every `factors-report` / `hypothesis-tests` / `maxims score` over Python-written files was identical), and the
  Python readers use `.get()`, so no report changes - but the dataset on disk is not the Python's, and #26 does not cover it.
* **C3 JSON pretty-print indentation.** `reference-set --show` and the `submit` dry-run print JSON with 4-space indentation;
  the Python uses `json.dumps(..., indent=1)`. Identical after stripping leading whitespace (all 5 `--show` inputs, all 3
  admitted submit dry-runs).
* **C4 `ingest` stores `device_profile` differently.** (i) With no provenance in the run dir the Python stores `{}`; the
  Kotlin stores `{"api_level":null,"release":null,"vendor":null,"soc_family":null,"clusters":null,"ram_class":null,"storage_class":null}`.
  (ii) With the nested `cell-state.json` shape (`{api_level, tier, vendor, cool_gate_c, profile:{...}}`, which is what the
  Python `cell_runner` wrote and the validation cell carries) the Python stores that object verbatim; the Kotlin stores
  `{"api_level":31,"vendor":"Google","release":null,...}` - the flat fields it recognises, with `tier`, `cool_gate_c` and
  the nested `profile` dropped. Records on disk therefore differ in shape for every run without a flat `device_profile`
  (every run not produced by the Kotlin `fleet-campaign`). Not in #26 (which is about null presence for a complete dict) nor #22.
* **C5 (robustness, not a parity diff) `ingest_run.py` and `matrix_report.py` are not safe to run concurrently.** They write
  their SQL to FIXED temp paths (`$TMPDIR/longitudinal_window.sql`, `longitudinal_signals.sql`, `vfm_q.sql`), so in the
  first, 6-way-concurrent pass the Python lost windows (fx-flagship-a n=19 vs 20, fsf-arm-c 49 vs 50) and `matrix_report.py`
  reported n=3 / 40 / 6 for the same 50-trace cell depending on which sibling process had last rewritten the file. The
  Kotlin uses a unique temp file per query (`startup-tools-<random>.sql`) and was unaffected. All ingest and matrix-report
  cells were re-run strictly sequentially (`shadow.py runseq`; flagged `sequential_rerun` in `raw/manifest.jsonl`) and the
  verdicts above are from that re-run. `trace_health.py` uses `mkstemp` and was never affected.
* **C6 `ingest` `trace_health.parse_errors` differs under the `composed` instrument** (a consequence of #5 that #5 does not
  list). With canary `composed` the Python's `check_trace` verdict is `missing-canary` for EVERY trace (no such slice), and that
  branch pre-empts `slices-incomplete`, so the Python never counts parse errors under the composed recipe; the Kotlin (canary
  `emb-modules-init`) counted the 5 out-of-order Pixel 7 Pro traces (`parse_errors` 0 vs 5 on fx-flagship-a; 0 vs 0 elsewhere).

## One-sided errors

* `variance` / `fx-flagship-a`: Python exit 1 with `statistics.StatisticsError: no median for empty data` (traceback in
  `raw/variance/fx-flagship-a.py.stderr.txt`); Kotlin exit 0 with the full report. PORT-LOG #3 - expected, and the Python's
  `--json` dataset (written before the crash) is tree-identical to the Kotlin's.

No other cell had a one-sided error. Matching refusals (same exit code, same stdout, same stderr on both sides):
`factors-report` on the five dirs without `pass1-factors.json` (a14-campaign2, cooldown/a01, cooldown/a14, a01-mini,
goldens/campaign-b: `no passN-factors.json found`, exit 1); `ingest --dry-run` on all 8 run dirs (`truncated run: N windows
against a declared shape of 10x20=200`, exit 1 - none of the dirs on this machine is a 10x20 run); `submit --dry-run` on all
12 `longitudinal/store.jsonl` records, all 4 `sweep-store.jsonl` records and the `validation/store.jsonl` record
(`INADMISSIBLE: the signal inventory did not come from a clean trace`, exit 1 - PORT-LOG #5's consequence, identical on both sides).

## Exact command lines

Every argv (both sides), the env each side received and its exit code and wall time are in `raw/manifest.jsonl`
(one JSON object per cell: `{"cmd","label","py":{"rc","seconds","argv","env"},"kt":{...}}`). The generic shapes,
with `<TP>` = `~/.cache/embrace-startup-tools/trace_processor/v46.0/trace_processor`, `<SK>` = `.claude/skills`,
`KT` = `env STARTUP_TOOLS_NO_BUILD=1 tools/startup`, `<A>` = `raw/<command>/artifacts/<input>.<side>`:

```
analyze               python3 <SK>/startup-analysis/scripts/analyze_startup.py --trace-processor <TP> --output-dir <scratch>/analyze-out/py/<input> <dir>
                      KT analyze --output-dir <scratch>/analyze-out/kt/<input> <dir>            (+ --all-sections variant on the fixture dirs)
variance              python3 <SK>/startup-multi-device-analysis/scripts/variance_analysis.py <TP> <dir> --little-cpus 0,1,2,3 --json <A>.pass.json --out <A>.report.txt
                      KT variance <dir> --little-cpus 0,1,2,3 --json <A>.pass.json --out <A>.report.txt
outlier-factors       python3 .../outlier_factors.py <TP> <dir> <A>.factors.json
                      KT outlier-factors <dir> <A>.factors.json
trace-health          python3 <SK>/_shared/trace_health.py <dir> --canary emb-sdk-start --trace-processor <TP>     (+ --show-meta variant)
                      KT trace-health --canary emb-sdk-start <dir>
hypothesis-tests      LITTLE_CPUS=0,1,2,3 python3 .../hypothesis_tests.py <campaign>      KT hypothesis-tests --little-cpus 0,1,2,3 <campaign>
factors-report        LITTLE_CPUS=0,1,2,3 python3 .../factors_report.py <campaign>        KT factors-report --little-cpus 0,1,2,3 <campaign>
cross-device-sections python3 .../cross_device_sections.py <label>=<dir> ...              KT cross-device-sections <label>=<dir> ...
trend                 python3 <SK>/startup-longitudinal-tracking/scripts/trend_report.py --store <store> --baseline-runs 3 --json <A>.verdicts.json
                      KT trend --store <store> --baseline-runs 3 --json <A>.verdicts.json
ingest (dry-run)      python3 .../ingest_run.py <run-dir> --reference-set claude-output/longitudinal/reference-set.json --store <A>.store.jsonl --device-key <key> --trace-processor <TP> --dry-run
                      KT ingest <run-dir> --reference-set ... --store <A>.store.jsonl --device-key <key> --dry-run
ingest (--force)      same with --force instead of --dry-run, into <A>.store.jsonl (scratch stores; EXTRA, so the records themselves could be tree-compared)
reference-set         python3 .../reference_set.py --show <ref> | --probe --check <ref> | --probe --out <A>.ref.json      KT reference-set (same flags)
reproducibility       python3 <SK>/startup-global-corpus/scripts/reproducibility_report.py --corpus <corpus> --json <A>.cells.json      KT reproducibility --corpus ... --json ...
submit (dry-run)      python3 .../submit_run.py --store <store> --run-id <id> --corpus <scratch>/submit/<label>/corpus.jsonl --contributor shadow --dry-run [--serial mid-b]
                      KT submit (same flags)
matrix-plan           python3 <SK>/startup-version-factor-matrix/scripts/matrix_plan.py <plan> --emit <A>.cells.json      KT matrix-plan <plan> --emit <A>.cells.json
matrix-report         python3 .../matrix_report.py claude-output/2026-08-26-kotlin-port/validation/vfm [--slice emb-sdk-start|composed] --trace-processor <TP> [--json <A>.cells.json]
                      KT matrix-report .../validation/vfm [--slice ...] [--json ...]
cohorts               python3 <SK>/_shared/cohorts.py <log> [--method coldStartupBaselineProfileExpiredUserSession --json <A>.cohorts.json]      KT cohorts <log> [...]
maxims score          python3 <SK>/_shared/maxims.py score <campaign> --reference-set <ref> --device-key <key> --sdk-version 9.2.0-SNAPSHOT-0811 --arm default --ledger none
                      KT maxims score (same flags)
maxims render         python3 <SK>/_shared/maxims.py render --ledger <ledger> -o - --now 2026-09-05T00:00:00Z      KT maxims render (same flags)
probe                 python3 .../device_probe.py <serial> <key> <scratch>/probe/py      KT probe <serial> <key> <scratch>/probe/kt
artifact-sync list    python3 <SK>/_shared/artifact_sync.py list      KT artifact-sync list
```

## What could NOT be shadow-run offline, and why

* `fleet-campaign` / `fleet_campaign.py` and `cell-runner` / `cell_runner.py`: they drive a device through a real
  benchmark (install, launches, thermal gate); not offline, and the Python `cell_runner.py` could never complete a
  cell anyway (PORT-LOG #14/#15/#17). The Kotlin side of both was device-validated separately on 2026-09-02 (PORT-LOG).
* `compat-patch` / `compat_patch.py`: neither implementation has a dry-run mode; `--apply` rewrites
  `examples/ExampleApp` and `--revert-all` restores from a journal, both mutate the tree, so neither was run.
* `verify-arms` / `verify_ab_arms.py`: needs two (three) built APKs; none exist on this machine outside a build.
* `serve-trace` / `serve_trace.py`: a long-running HTTP server; nothing to diff.
* `artifact-sync check` / `record`: `check` needs a tracked local HTML path plus the published copy; `record` writes
  the manifest. Only `list` (read-only) was run.
* `reference-set --probe --out`: was run, but into the raw dir only (the probe is read-only on the device).
* `matrix-report` over the x37 legs: the legs under `claude-output/2026-08-26-x37-engine-tail/*-windows.json` are
  window datasets, not cell directories with `cell-state.json` + traces; neither CLI consumes them (the Kotlin tests use
  them to pin `summarize()` against `goldens/x37_legs.json`). The only cell directory on this machine is
  `validation/vfm/mid-b__9.2.0__reference`, and `matrix-report` was run over that.
* `ingest` without `--dry-run` into the real store: deliberately not done (would append to `claude-output/longitudinal/store.jsonl`);
  the `--force` runs went into scratch stores.
* `maxims score` with a real ledger, `maxims render -o <file>`: would write `.claude/skills/_shared/maxims/ledger.json` / `MAXIMS.md`; only `--ledger none` and `-o -` were used.
* `submit` without `--dry-run`: would append to a corpus; every submit was `--dry-run` (the corpus paths were scratch paths anyway).
* `cohorts` over campaigns other than the two first-session-fix arms: those are the only `*-embverify.log` captures on the machine
  (the fleet-campaign wrote `passN-cohorts.json` directly for the others).
