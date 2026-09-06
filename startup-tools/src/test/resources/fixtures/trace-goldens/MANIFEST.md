# Trace-layer goldens (Layer B) for the Kotlin port

Frozen outputs of the Python toolchain on the **captured fixture traces** (released SDK 9.2.0, one
pass of 20 cold launches per device, captured 2026-09-02 from commit `7b3c3cd8f`). The traces
themselves are NOT in the repo (~345 MB; see `claude-output/2026-08-26-kotlin-port/fixtures/`);
everything here is small text derived from them, so the Perfetto client, the CSV parser, the health
verdicts and every trace-driven report can be tested for parity in CI without a device or a binary.

## Devices

| key | device | traces |
|---|---|---|
| `flagship-a` | Pixel 7 Pro (Tensor G2, API 35) | 20 |
| `mid-a` | Galaxy A14 (Exynos 850, API 35) | 20 |
| `mid-b` | Pixel 3 (Snapdragon 845, API 31) | 20 |

## Producer

`_producers/dump_trace_goldens.py` (2026-09-02, Python 3.14.2) ran the Python suite's pinned
launcher (`~/.cache/embrace-startup-tools/trace_processor/v46.0/trace_processor`, which resolves to
the **Perfetto v57.2** native engine - the same prebuilt the Kotlin `Prebuilt` pins directly) with
`-q <sql> <trace>` for every query below, on every trace, and saved the RAW stdout. Then
`_producers/dump_cli_goldens.py` drove `variance_analysis.py` / `outlier_factors.py` with their
`extract` reading that saved stdout (zero re-parsing), and ran `factors_report.py`,
`hypothesis_tests.py` and `cross_device_sections.py` on the result. `_producers/copy_trace_goldens_into_module.py`
copied it all here.

## Storage

The goldens are generated once and never edited, and there are ~630 of them, so they are kept as a
single deflated archive, `data.zip`, beside this manifest. `TraceGoldens` unpacks it to a temporary
directory once per test JVM, and falls back to an unpacked `trace-goldens/` directory if one is present.
Only this manifest stays a plain file, because it is the part a person reads.

**Frozen at the cutover (2026-09-05).** The `_producers/` scripts described above were deleted with the
Python they drove, and the captured traces were never in the repo, so these goldens cannot be
regenerated. They are the spec the trace layer is held to: a golden that moves means the Kotlin changed.

## Layout

Inside `data.zip`:

```
<device>/<trace-stem>/
  startup_metrics.csv       analyze_startup's query (sections, window + source, TTID, wait/run/cpus)
  init_window_sched.csv     main-thread scheduling over emb-modules-init
  variance_metrics.csv      variance_analysis's query
  outlier_metrics.csv       outlier_factors's query (the external-factor catalogue)
  foreign_gc_overlap.csv    foreign-process GC buckets
  health.csv                trace_health's loss-counter + canary query (canary emb-sdk-start)
  window_emb_sdk_start.csv  ingest_run WINDOW_SQL for emb-sdk-start
  window_composed.csv       ingest_run COMPOSED_WINDOW_SQL
  signals.csv               ingest_run SIGNALS_SQL
  python_parsed.json        trace_health.check_trace verdict; the Python parses of health/window/signals stdout
_cli/
  analyze_startup.<device>.stdout.txt   analyze_startup.py on the device's pass1 dir
  trace_health.<device>.stdout.txt      trace_health.py on the device dir
  variance.<device>.stdout.txt          variance_analysis.py report (ABSENT for flagship-a, see below)
  variance.<device>.error.txt           the Python's own failure, where it failed
  outlier_factors.<device>.stdout.txt   progress lines only
  factors_report.<device>.stdout.txt
  hypothesis_tests.<device>.stdout.txt  one pass, no campaign.log (temps print as --)
  cross_device_sections.all.stdout.txt  labels mid-b, mid-a, flagship-a in that order
_campaign/<device>/pass1.json, pass1-factors.json   the variance / outlier-factors datasets
```

## How the tests use them

* `TraceProcessorTest` (offline): every saved stdout parses into the rows the Python read; the ingest
  window equals the analyze window. With `STARTUP_TOOLS_TRACE_PARITY=1` and the traces on disk, the
  native engine is re-run on every trace and its rows compared to these.
* `TraceHealthTest`: `python_parsed.json > health_check_trace` reproduced from `health.csv`.
* `StartupAnalysisTest`, `VarianceAnalysisTest`, `CampaignReportsTest`: report text line for line
  and datasets as JSON trees against `_cli/` and `_campaign/`.

## Live gate result

`./gradlew -PtraceParity=1 :startup-tools:test` was run on 2026-09-02 (09:03Z, mac-arm64): the native
v57.2 `trace_processor_shell` re-ran all nine queries on all 60 fixture traces and every parsed row
matched the frozen launcher output. 942 s wall, 540 trace parses. Layer B is green end to end.

Re-run attempted on 2026-09-05 (mac-arm64, branch `hho/startup-tools-kotlin`, after the cohorts,
trace-health class-load, maxims and ingest changes): the first attempt was voided by a concurrent
`:startup-tools:test` invocation sharing the module's test-results directory (Gradle failed with
`NoSuchFileException ... in-progress-results-generic.bin` after 17m53s; not a parity result). Do not run
any other `:startup-tools:test` while the gate runs. The clean re-run, alone: **PASS** — exit 0,
`TraceProcessorTest` 5 tests / 0 failures / 0 errors, the live parity case 1045 s, same 60 traces
(JUnit report `startup-tools/build/test-results/test/TEST-io.embrace.startup.perfetto.TraceProcessorTest.xml`).
Part of the pre-cutover evidence in `claude-output/2026-09-05-shadow-run/REPORT.md`.

## Findings recorded while freezing (Python behaviour on real data)

1. **`variance_analysis.py` crashes on flagship-a**: `statistics.median([])` in section C, because
   `emb-record-startup` (in its EXTRAS list) is emitted by no Pixel 7 Pro trace on 9.2.0. Recorded
   as `_cli/variance.flagship-a.error.txt`; the JSON dataset (written before the report) survives. The
   Kotlin `VarianceAnalysis` renders NaN for such a section and drops it from the excess list - a
   deliberate departure, noted in the port log.
2. `hypothesis_tests.py` and `factors_report.py` run fine on a single pass (no alternation to test,
   but every table renders). `cross_device_sections.py` prints `--` for the absent section.
3. Every fixture trace is health-`ok`: only `mm_unknown_type` / `power_rail_empty_packet` counters
   (meta bucket), canary present, no buffer or parse loss.
4. Every query's stderr is Perfetto loading chatter plus its own "Trace health issues" echo of the
   same meta counters; exit code 0 throughout. The Kotlin client treats stderr as informational.
