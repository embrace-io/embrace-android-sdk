---
name: startup-analysis
description: Run the SDK startup macrobenchmark on a connected Android device and analyze the EmbTrace section durations and SDK-init span timing, including each section's share of the SDK-init span. Defaults to benchmarking a locally built SDK; can also target a publicly released SDK version. Use when asked to measure, verify, or compare SDK startup/init performance on-device.
---

# SDK startup analysis (macrobenchmark)

Measures Embrace SDK init on a real device via `examples/ExampleApp` and reports per-section
durations, SDK-init span timing, each section's percentage of the span, and a main-thread
scheduling readout per iteration. The analysis is derived ENTIRELY from the per-iteration
`.perfetto-trace` files via perfetto's trace_processor — no logcat, grep, jq, or benchmark-JSON
parsing. All paths below are relative to the SDK repo root.

## Bundled files (relative to this skill's base directory)

Primary analysis:
- `scripts/analyze_startup.py` — THE analysis entry point (python3 stdlib only): runs
  trace_processor over every iteration trace in a directory and prints section stats,
  window stats, TTID stats, and the per-iteration contention/slow-execution table.
- `scripts/startup_metrics.sql` — the per-trace query analyze_startup.py executes (sections,
  window, TTID, main-thread thread-state; commented).
- `scripts/init_window_sched.sql` — standalone deep-dive on ONE trace: thread-state + per-CPU
  residency over the modules-init window. To find who stole the CPUs, query `sched` joined to
  `thread`/`process` excluding the window's utid.
- `scripts/serve_trace.py` — CORS-enabled static server for opening traces in ui.perfetto.dev
  (`python3 serve_trace.py <dir> [port]`).

References:
- `references/sections.md` — section nesting/execution order, which environments each section
  appears in, reference numbers, and the two slow-pass signatures (contention vs slow
  execution) for judging noisy passes.
- `references/report-template.html` — the report page to adapt (a filled example; see the
  comment at the top for what to replace vs keep). Write each analysis's report to
  `claude-output/startup-analysis-<YYYY-MM-DD-HHMMSS>.html` using the SAME timestamp as that
  analysis's summary .txt, so the pair is uniquely referencable and never clobbered.
  Publishing a new path mints a new artifact URL per analysis — reuse a previous report's
  exact file path only when intentionally updating that report (and its URL) in place.

There is deliberately no logcat/jq/grep/benchmark-JSON path — the traces are the single source
of truth. The one thing traces cannot verify is the `<section>-duration-ms` attributes on the
exported span (`recordDuration`); if that mechanism ever needs validating, it is an SDK
integration-test concern, not this skill's.

## What you get (all from the traces)

- Duration of EVERY `emb-*` section (first occurrence, matching TraceSectionMetric
  `Mode.First`), as min/median/max over N iterations — the 13 canonical sections in execution
  order with %-of-window, plus every finer-grained section the SDK emits (`emb-sdk-start`,
  `emb-install-native-crash-signal-handlers`, `emb-record-startup`, …), which the old
  JSON-metric path never captured.
- SDK-init window per iteration, from the `emb-sdk-start` slice — it wraps the whole
  `Embrace.start()` call and runs <1 ms wider than the exported `emb-embrace-init` /private
  `emb-sdk-init` span. On SDKs without `emb-sdk-start` (before 9.2.0) the window falls back to
  first `emb-modules-init` start → first `emb-post-services-setup` end, which equals the
  exported span interval by construction. The output states which source was used; never
  compare windows across sources.
- TTID per iteration from trace_processor's `android.startup` stdlib module. Its anchor differs
  from macrobenchmark's `timeToInitialDisplayMs` by a few ms (consistently, e.g. +6–7 ms on the
  Galaxy A14) — fine for comparisons as long as both sides come from the same source.
- Per-iteration main-thread scheduling inside the window: Running time, runnable-wait (R/R+)
  time, distinct CPUs — flags CONTENDED iterations and distinguishes the two slow signatures
  (see references/sections.md).

## Prerequisites

- Physical device on `adb devices`, booted (`adb shell getprop sys.boot_completed` → 1),
  screen on/unlocked.
- `examples/ExampleApp/app/benchmark/src/main/java/io/embrace/android/benchmark/StartupBenchmarks.kt`
  is purely the harness: it drives N instrumented cold starts so each iteration records a
  perfetto trace. Its metric list is the minimum `measureRepeated` requires
  (`StartupTimingMetric` only) and is NOT the source of any reported number — it never needs to
  track SDK sections.

## Choosing the SDK under test

Do NOT assume the version already in `examples/ExampleApp/gradle/libs.versions.toml` is correct —
always set it explicitly for the run, note the value you replaced, and restore it afterwards.

**Default — locally built SDK (the working tree):**
1. `./gradlew publishToMavenLocal -q` from the repo root.
2. Read `version=` from `gradle.properties` at the repo root (e.g. `9.2.0-SNAPSHOT`) and set
   `embrace = "<that version>"` in the ExampleApp catalog. Both ExampleApp repo blocks already
   include `mavenLocal()`, and the `io.embrace.gradle` plugin resolves from there at the same
   version.

**Option — publicly released SDK (e.g. comparing against a shipped version):**
1. Set `embrace = "<released version>"` (e.g. `9.0.0`) in the ExampleApp catalog; artifacts and
   the gradle plugin resolve from mavenCentral. No publish step.
2. Compile-check first (`:app:assembleBenchmark`) — the ExampleApp uses current public API, so
   very old versions may not compile; that bounds how far back this option reaches.

## Procedure

1. Set the SDK version (above), then run:
   ```
   examples/ExampleApp/gradlew -p examples/ExampleApp :app:benchmark:connectedBenchmarkAndroidTest \
     -Pandroid.testInstrumentationRunnerArguments.class=io.embrace.android.benchmark.StartupBenchmarks \
     -Pandroid.testInstrumentationRunnerArguments.androidx.benchmark.dryRunMode.enable=false
   ```
   The dry-run override is mandatory — the module defaults it to true, and dry runs produce NO
   traces (there would be nothing to analyze).
2. **Collect**: the per-iteration traces land in
   `examples/ExampleApp/app/benchmark/build/outputs/connected_android_test_additional_output/benchmark/connected/<device>/`
   as `StartupBenchmarks_coldStartup_iterNNN_<timestamp>.perfetto-trace`. Each rerun WIPES this
   directory — copy any traces you need to keep (e.g. per-pass comparisons) before rerunning.
3. **Analyze**: fetch the trace_processor launcher once per machine (it is a python3 script that
   self-downloads the native binary on first use):
   ```
   curl -sL -o <scratchpad>/trace_processor https://get.perfetto.dev/trace_processor
   ```
   then run
   ```
   python3 .claude/skills/startup-analysis/scripts/analyze_startup.py \
     --trace-processor <scratchpad>/trace_processor "<traces dir>"
   ```
   (`--all-sections` to list every emb-* section instead of the top 15 extras.) Besides
   printing, the summary is written to a uniquely named file —
   `claude-output/startup-analysis-<YYYY-MM-DD-HHMMSS>.txt` (analysis start time;
   `--output-dir` overrides) — so successive runs never clobber each other; cite that file
   in reports and keep it as the per-pass record (the traces themselves get wiped by the
   next benchmark run).
   Every analysis/report must state the test context up front: device manufacturer + model and
   Android version (via
   `adb shell "getprop ro.product.manufacturer; getprop ro.product.model; getprop ro.build.version.release"`
   — `getprop` takes ONE property per call; the model also appears in the results directory
   name), the SDK version under test, iteration count, and date.
4. **Interpret**: present the canonical sections in execution order with nesting; children
   overlap parents, so percentages do not sum to 100; the class-load sections
   (`embrace-impl-init`, `bootstrapper-init`) run BEFORE the window opens, so their % is context
   relative to the window, not a share of it. Check the scheduling table before comparing
   passes — iterations flagged CONTENDED, or whole passes with elevated Running time, are
   environment noise, not regressions (signatures in references/sections.md).
5. **Inspect a trace visually (optional)**: serve the traces dir on `127.0.0.1:9001` with
   `serve_trace.py` (plain `python3 -m http.server` lacks the CORS header) and open
   `https://ui.perfetto.dev/#!/?url=http://127.0.0.1:9001/<file>` — the first fetch can take
   ~30 s (private-network preflight). Once loaded, the address bar rewrites to a durable
   `local_cache_key` URL that resolves from that machine's Chrome afterwards; use that form in
   reports. Pick the iteration whose window is the median (or a flagged-vs-clean pair when
   illustrating variance).
6. **Restore** the catalog's `embrace` version to the value you replaced.

## Handling missing data (older SDK versions)

- **Missing sections**: analyze_startup.py prints "not instrumented in this SDK version" for
  canonical sections with no slice — report those explicitly; never present the found set as
  complete. (At public 9.0.0, present: `embrace-impl-init`, `bootstrapper-init`, `modules-init`,
  `config-service-init`, `span-service-init`, `otel-tracer-init`, `post-services-setup`.)
- **Window**: `emb-sdk-start` exists from 9.2.0; older SDKs automatically fall back to the
  composed window (`emb-modules-init` + `emb-post-services-setup`, both present since 9.0.0) —
  the output names the source, and windows from different sources must not be compared
  directly. TTID from the `android.startup` module works regardless of SDK version.
- **Section semantics drift between versions.** A section that exists in both versions may cover
  different work (e.g. 9.0.0's `post-services-setup` includes what later versions subdivide into
  `load-instrumentation` and `post-init`-adjacent work, and its `config-service-init` covers a
  different construction path). When comparing versions, compare only sections present in both,
  call out semantic drift, and lean on the window duration and TTID as the apples-to-apples
  numbers.
- Confirm which sections a public version *should* emit with
  `git grep -n "EmbTrace.trace" <version-tag> -- "*.kt"` — and beware pathspec globs: `*` does
  not cross directory separators, so use the bare `"*.kt"` form over module-scoped globs.

## Interpretation gotchas

- **Section counts differ by environment**: sections behind supplier fallbacks
  (`config-service-init`) and the class-load sections (`embrace-impl-init`, `bootstrapper-init`)
  appear on-device but not in the Robolectric integration harness — the authoritative
  integration-test list lives in `SpanAssertions.expectedSdkInitSections` (embrace-android-otel-fakes).
- **Pre-start Embrace references skew the window**: the class-load init (`embrace-impl-init`,
  `bootstrapper-init`) fires on the app's FIRST reference to the `Embrace` class — any API call
  or property access triggers it, not any one method in particular. Pre-start API calls can
  additionally force SDK lazies (e.g. an exporter registration constructing the OTel config)
  before the window opens, hoisting that work out of the window. ExampleApp touches the API
  before `Embrace.start()`, so its window durations under-count relative to an app whose first
  Embrace reference is `start()` itself. Individual section durations remain comparable either
  way.
- **TTID source consistency**: trace-derived TTID (android.startup module) and macrobenchmark's
  JSON `timeToInitialDisplayMs` differ by a few ms in anchor. Never mix the two sources within
  one comparison.
