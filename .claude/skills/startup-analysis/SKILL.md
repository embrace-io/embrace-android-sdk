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

Primary analysis — the Kotlin `startup-tools` module, run from the repo root as
`tools/startup <command>` (`startup-tools/README.md` has the full command table;
`tools/startup <command> --help` gives the exact flags):
- `tools/startup analyze <traces-dir>` — THE analysis entry point: runs the perfetto engine over
  every iteration trace in a directory and prints section stats, window stats, TTID stats, and
  the per-iteration contention/slow-execution table.
- `tools/startup serve-trace <dir> [port]` — CORS-enabled static server for opening traces in
  ui.perfetto.dev.
- The SQL the commands execute lives in
  `startup-tools/src/main/resources/io/embrace/startup/perfetto/sql/`: `startup_metrics.sql` is
  the per-trace query `analyze` runs (sections, window, TTID, main-thread thread-state;
  commented); `init_window_sched.sql` is a standalone deep-dive on ONE trace: thread-state +
  per-CPU residency over the modules-init window. To find who stole the CPUs, query `sched`
  joined to `thread`/`process` excluding the window's utid.

References:
- `references/interpreting-results.md` — **the foundation for interpreting any run, and the
  first thing to read before you trust a number.** Trace_processor query traps that yield
  plausible-but-wrong numbers; the telemetry verification tap contract; harness traps that
  poison a run before analysis; which run conditions to record and how to use them to explain
  your own outliers; the install-time compile-state pass toggle; run shape and tail statistics;
  within-device comparison hygiene; and the five outlier classes with their single-trace
  signatures. Every other startup skill assumes this file and none of them repeat it.
- `references/sections.md` — section nesting/execution order, which environments each section
  appears in, how to establish your own baseline (there are no portable reference numbers), and
  the two slow-pass signatures (contention vs slow execution) for judging noisy passes.
- `references/report-template.html` — the report page to adapt (a filled example; see the
  comment at the top for what to replace vs keep). Write each analysis's report to
  `_shared/records/analyses/startup-analysis-<YYYY-MM-DD-HHMMSS>.html` using the SAME timestamp as that
  analysis's summary .txt, so the pair is uniquely referencable and never clobbered.
  Publishing a new path mints a new artifact URL per analysis — reuse a previous report's
  exact file path only when intentionally updating that report (and its URL) in place.

Shared across the startup skills (`.claude/skills/_shared/`):
- `maxims/MAXIMS.md` — **what we currently believe about SDK init on the bench, and the evidence
  for it**: each maxim's statement, scope, status (accepted / under review / refuted / untested),
  how many reference devices hold it, and the latest contradictions. Generated from the maxim
  definitions and the ledger; never edit it by hand. `maxims/FINDINGS.md` is the hand-curated
  companion: conclusions that are not per-campaign checks, and the dated history of every maxim.
  Read both before an analysis, as priors to confirm or contradict, not as conclusions to repeat.
- `tools/startup maxims` — scores a campaign directory against every maxim and records the
  verdicts in `maxims/ledger.json`. See "Maxims" below.

There is deliberately no jq/grep/benchmark-JSON path — the traces are the single source of truth
for *timing*.

**Verifying what the SDK logged is a different job with a different instrument.** Traces cannot
show attribute VALUES (`init-cpu-pct`, `<section>-duration-ms`, thermal state). For that, use the
app-side verification tap: a gated Kotlin `SpanProcessor` in ExampleApp that emits each completed
span as chunked JSON to logcat (`adb logcat -d -s EmbVerify:I`, then reassemble and wait for the
flush marker). It works on any build type — including the non-debuggable benchmark build — and on
locked/unattended devices. Do NOT verify via the cached payload
(`run-as … files/embrace_cache/*session*`): that needs a debuggable build AND a foreground user
session, so it returns zero-span background payloads on an idle/locked device. And note the tap
must be a *processor*, not an exporter — exporters never see `emb.private` spans, and sdk-init is
private. Full contract + the trace_processor query traps that produce plausible-but-wrong numbers
are in `references/interpreting-results.md`.

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
  from macrobenchmark's `timeToInitialDisplayMs` by a few ms — a fixed offset on a given device,
  so it is fine for comparisons as long as both sides come from the same source.
- Per-iteration main-thread scheduling inside the window: Running time, runnable-wait (R/R+)
  time, distinct CPUs — flags CONTENDED iterations and distinguishes the two slow signatures
  (see references/sections.md).

## Prerequisites

- **Physical device** on `adb devices`, booted (`adb shell getprop sys.boot_completed` → 1),
  screen on/unlocked. Emulators are NOT valid for these measurements: their clocks, scheduler,
  and IO are host artefacts, so neither absolute timings nor variance mean anything.
- **API 29 or newer** — the practical floor, because perfetto tracing of a *profileable,
  non-debuggable* app (which is what the benchmark variant is) requires it. Below that the
  harness cannot produce the traces this skill analyzes.
- **JDK 17+** (the repo's Gradle needs one anyway) and `adb`. `tools/startup` builds the Kotlin
  module incrementally and fetches its own perfetto engine (see "Analyze" below); nothing else
  to install.
- `examples/ExampleApp/app/benchmark/src/main/java/io/embrace/android/benchmark/StartupBenchmarks.kt`
  is purely the harness: it drives N instrumented cold starts so each iteration records a
  perfetto trace. Its metric list is the minimum `measureRepeated` requires
  (`StartupTimingMetric` only) and is NOT the source of any reported number — it never needs to
  track SDK sections.

## Choosing a device, and recording its profile

Any device meeting the prerequisites works. What it can tell you is bounded, and the bound is the
main reason to be careful about how far you generalise:

- **A single device answers "did this change move SDK-init time here, on this build?"** — that is
  a real and useful answer, and it is the question this skill is for.
- **It cannot separate SDK effects from device-class effects.** Tier, ART generation, vendor
  install-time compile policy and thermal governor all shift both the absolute numbers and the
  variance, and one device holds all of them fixed. A section that dominates on an entry-tier
  device may be noise on a flagship, and vice versa.
- **Conclusions from one device are provisional.** Before treating a result as a property of the
  SDK, re-check it on a device with a *different* profile — different tier and, ideally, a
  different vendor and ART generation. Use the multi-device skill
  (`startup-multi-device-analysis`) for that: it does the coordinated cross-device runs and the
  forensics that attribute a difference to tier, thermal, or scheduling rather than to SDK code.
- **Prefer the profile that matches the question.** Judging a regression that would hurt
  low-end users? Measure on an entry-tier device, where init cost and tails are largest. Checking
  that a change is neutral on modern hardware? A flagship is the right instrument. One device
  cannot do both jobs.

**Record the device profile in every report** — this is what makes results comparable later, and
it is exactly what the longitudinal skill consumes:

| Field | How to get it |
|---|---|
| `api_level` + Android release | `getprop ro.build.version.sdk`, `getprop ro.build.version.release` (drives ART generation and tracing capability) |
| `tier` — entry / mid / flagship | your judgement, proxied by RAM class and SoC class |
| `vendor` / OEM | `getprop ro.product.manufacturer` (drives install-time compile policy, thermal governor, SELinux readability of sysfs) |
| `soc_family` + cluster topology | CPU part ids from `/proc/cpuinfo` and the `cpufreq` policies — `tools/startup probe` collects and summarises these |
| `ram_class`, `storage_class` | where detectable |

A number without its profile is not reusable by anyone, including you next month.

## Run shape policy

- **Default: 4 passes × 50 iterations** per device. Four passes balances two-state devices
  (some devices alternate a fast/slow pass state per benchmark cycle — always use an even
  pass count and judge on fast-state passes) and yields four first-post-install samples;
  50 iterations gives churn-driven outliers enough runway to appear (system_server GC and
  similar competitors typically only start firing once a pass has accumulated a dozen-plus
  iterations of load, so short passes systematically miss them).
- **Targeted or time-constrained runs may use 4 × 25 — never go below that.** Fewer
  iterations under-samples mid-pass outliers; fewer passes breaks state balance and
  first-launch sampling.
- Report tails, not just medians: p90/p95/max/top-3 and the slow-iteration rate (threshold
  = max(4 ms, 10% of the pass median)). With ≤200 samples, do not quote a p99.
- Full rationale, event-count sizing, the scheduling-table triage, and the within-device
  comparison rules (matching pass states, matching window sources, iter000 as its own cohort)
  are in `references/interpreting-results.md`.

## Compilation-mode arms

The compilation state is a first-order lever on SDK-init time — commonly on the order of 2×,
though the ratio is device-specific, so establish it on yours — and MUST be explicit:

- `StartupBenchmarks` exposes: `coldStartup` (CompilationMode.DEFAULT — fresh-install
  `verify` state; the historical-continuity arm), `coldStartupBaselineProfile`
  (Partial/Require — what Play-installed users with profiles experience; **fails if no
  baseline profile is packaged, which is itself the packaging check**), and
  `coldStartupNoAot` / `coldStartupFullAot` (canary/diagnostic arms). Select with
  `#methodName` appended to the instrumentation `class` filter.
- **A standard analysis runs TWO arms: `coldStartup` and `coldStartupBaselineProfile`**,
  and reports both (with the delta). Default-mode-only runs never exercise the shipped
  profile, so profile-coverage regressions are invisible to them. Run `coldStartupNoAot`
  occasionally as the what-is-the-profile-worth canary.

## User-session state arms (the production cold start)

The compile state is not the only device state the harness controls by accident. The SDK
restores the previous user session on launch when it is within its inactivity timeout
(30 minutes by default) and only CREATES and PERSISTS a new one otherwise. Every benchmark
method above relaunches within seconds, so every iteration after the first restores, and
`start-first-session` (inside `post-init`) is close to free. A production cold start is
usually hours after the previous one: it takes the create-and-persist path, which does real
work on the main thread (serialization, a preferences write, thread starts) and on an
uncompiled install costs several times the restore path - enough to be a large share of
`post-init` in production while the lab reported it as negligible. **The benchmark modelled a
user who relaunches every few seconds; production is the other user.** Evidence:
the first-session A/B campaign (September 2026, in the analysis records) and
`references/interpreting-results.md` "User-session state decides post-init".

- `coldStartupBaselineProfileNewUserSession` runs `pm clear` in `setupBlock`, so every
  iteration starts with no stored user session AND no persisted config (the first-launch
  config path). It is the "fresh install" cohort made repeatable. Its `start-first-session`
  is the number production sees; its `persisted-config-load` is NOT (compare that section
  against the plain arm).
- `coldStartupBaselineProfileExpiredUserSession` isolates the user-session write from the
  config path: `setupBlock` arms `settings put global embrace_bench_expire_user_session 1`,
  and the ExampleApp's `BenchmarkStateHooks` deletes only the `embrace.user_session` key from
  the default SharedPreferences before `Embrace.start` (a release build cannot be reached with
  `run-as`, so the app does it to itself). Persisted config and all other state stay as a
  returning user's would. This is the "returning user, hours later" cohort - the production
  median. Side effect: the prefs file is loaded on the main thread before the SDK's prewarm, so
  `key-value-store-init`/`prefs-first-read` read slightly low in this arm. The setting persists
  on the device; disarm with `settings delete global embrace_bench_expire_user_session`
  before any arm that should restore.
- **The campaign runner verifies the cohort of every launch** (`tools/startup fleet-campaign`):
  it arms the ExampleApp's `EmbVerify` logcat tap for the campaign, streams the pass's lines to
  `passN-embverify.log`, and classifies each launch's `sdk-init` span as created (new
  `emb.user_session_id`, or counter reset to 1), restored (same id) or unknown, writing
  `passN-cohorts.json` and a `cohorts:` line to the campaign log. A launch on the wrong path for
  the arm is a WARN with its iteration index - drop or split it in analysis, never average it
  in. `tools/startup cohorts` re-runs the classification on a saved capture. The tap adds a span
  processor to the app under test, so
  every arm of a comparison must run with it in the same state (it is on by default; the
  runner restores the setting afterwards).
- Report `start-first-session` and `post-init` from this arm separately; never average them
  with the restoring arms, they are different code paths.
- **Arm-ordering bias is real**: back-to-back arms self-heat the device, disadvantaging
  whichever runs second. On a thermally-sensitive device a fixed arm order can inflate the
  apparent effect size by roughly half again — enough to change a conclusion — so never
  report a cross-arm delta from a single fixed order. Between arms, cool the device back to
  its pre-arm silicon temperature (`dumpsys thermalservice`, NOT battery temp — silicon runs
  tens of °C hotter under load), or counterbalance order across passes; cross-arm claims
  should survive the boundary comparison (last iterations of arm A vs first of arm B).
  How sensitive your device is to this is itself something to measure: thermal response
  varies with vendor governor and tier, and the effect can be negligible on one profile and
  dominant on another.

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
3. **Analyze**: run
   ```
   tools/startup analyze "<traces dir>"
   ```
   The first run on a machine fetches the pinned perfetto engine (`trace_processor_shell`) into
   `~/.cache/embrace-startup-tools/` and verifies its sha256; nothing to install by hand
   (`--trace-processor <path>` points at a different native binary if you must).
   (`--all-sections` to list every emb-* section instead of the top 15 extras.) Besides
   printing, the summary is written to a uniquely named file —
   `_shared/records/analyses/startup-analysis-<YYYY-MM-DD-HHMMSS>.txt` (analysis start time;
   `--output-dir` overrides) — so successive runs never clobber each other; cite that file
   in reports and commit it as the per-pass record (the traces themselves get wiped by the
   next benchmark run; the records root is what survives, see `_shared/records/README.md`).
   Summaries land as loose files there and are folded into `analyses/<YYYY-MM>.zip` by that
   directory's `pack.py` once they pile up.
   Every analysis/report must state the test context up front: the full device profile
   (see "Choosing a device" — manufacturer, model, api level + Android release, tier, SoC
   family) via
   `adb shell "getprop ro.product.manufacturer; getprop ro.product.model; getprop ro.build.version.release; getprop ro.build.version.sdk"`
   (`getprop` takes ONE property per call, so chain them in one `adb shell`; the model also
   appears in the results directory name), plus the SDK version under test, the build type and
   compilation arm, and the iteration count.
4. **Interpret**: present the canonical sections in execution order with nesting; children
   overlap parents, so percentages do not sum to 100; the class-load sections
   (`embrace-impl-init`, `bootstrapper-init`) run BEFORE the window opens, so their % is context
   relative to the window, not a share of it. Check the scheduling table before comparing
   passes — iterations flagged CONTENDED, or whole passes with elevated Running time, are
   environment noise, not regressions (signatures in references/sections.md).
5. **Inspect a trace visually (optional)**: serve the traces dir on `127.0.0.1:9001` with
   `tools/startup serve-trace "<traces dir>"` (a plain static file server lacks the CORS header
   ui.perfetto.dev needs) and open
   `https://ui.perfetto.dev/#!/?url=http://127.0.0.1:9001/<file>` — the first fetch can take
   ~30 s (private-network preflight). Once loaded, the address bar rewrites to a durable
   `local_cache_key` URL that resolves from that machine's Chrome afterwards; use that form in
   reports. Pick the iteration whose window is the median (or a flagged-vs-clean pair when
   illustrating variance).
6. **Restore** the catalog's `embrace` version to the value you replaced.
7. **Score the maxims** when the run is a campaign with per-pass datasets (see below). A single
   pass analysed with `tools/startup analyze` alone has nothing to score yet; produce the datasets
   first if the run is worth adding to the ledger.

## Maxims: every campaign confirms a belief or teaches us something

The bench toolchain keeps its beliefs about SDK init as **maxims**: statements with a mechanical
check, a scope, and a one-line mechanism, defined in `startup-tools` and run as
`tools/startup maxims`. Every campaign is scored against every maxim and the verdicts accumulate in a
ledger, so a belief that fails on one device or tier is visible as exactly that rather than as a
lower percentage. This is the same structure and process as the production analysis tool
(`tool/sdk-startup` in the go repository, which scores per-app × per-device cells from ClickHouse):
the same scopes, the same verdict vocabulary, the same ledger shape, the same split between a
generated `MAXIMS.md` and a curated `FINDINGS.md`, and the same maxim id wherever the claim is the
same. The two tools measure the same thing by different means, so how their conclusions are shared,
updated and validated follows one shape.

- **Scopes.** *Universal*: must hold at the stated size on every device. *Directional*: the sign
  holds everywhere but the size is the device's, so a right-way lift under the floor is
  *undetected*, not a contradiction. *Device-specific*: the sign belongs to the device, and the
  per-device split is the finding (the install-compile toggle is one).
- **Cell** = one campaign: one device under one recipe and arm. Verdicts per cell are confirmed /
  contradicted / thin (under the power floor) / undetected / n-a (input absent). Status derives
  from how many *devices* hold a maxim, not how many cells: nine cells from three devices are
  three votes.
- **Inputs** are the per-pass datasets a campaign already writes: `passN.json` (from
  `tools/startup variance --json`), `passN-factors.json` (`tools/startup outlier-factors`),
  `passN-cohorts.json` (the fleet campaign's EmbVerify tap) and `run-metadata.json`. No trace is
  opened.
- **Run it after every campaign**, then regenerate the page:
  ```
  tools/startup maxims score <campaign-dir> --reference-set <reference-set.json>
  tools/startup maxims render
  ```
  Use `--ledger none` for a rerun of a campaign already scored, so the same data is not counted
  twice.
- **A contradiction is the most valuable line on the page.** Name it, with the cell and the
  observed value, and say whether it repeats an earlier one (the page lists the latest). Thin
  cells are named, never read. A **candidate** is a factor with a lift of 1.5x or more that no
  maxim covers; one that keeps appearing is a maxim waiting to be written, and some are small bands
  the next campaign refutes.
- **Changing a belief.** A new maxim, a demotion or a refutation is a code change in
  `startup-tools`, a dated entry in `maxims/FINDINGS.md` naming the campaign that did it, and a
  regenerated `MAXIMS.md`. Retired
  maxims stay visible in the page with their record: a belief tried and removed is a finding.
- The ledger, `MAXIMS.md` and `FINDINGS.md` live in git beside the skills (device keys are not
  sensitive, unlike the production ledger's app IDs), so the page's history is the record of when
  a belief changed.

## Handling missing data (older SDK versions)

- **Missing sections**: `tools/startup analyze` prints "not instrumented in this SDK version" for
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

## Pre-flight checks (cheap, prevent unsalvageable runs)

- **Verify the SDK pin resolves to what you intend** (`embrace =` in the ExampleApp
  catalog) — repo syncs/rebases silently revert uncommitted pins, after which the app
  builds against a released SDK from mavenCentral and the whole run measures the wrong
  thing. Symptom check: the first pass's traces must contain `emb-sdk-start` (9.2.0+) and
  familiar section names.
- **Verify iter000 freshness per pass**: `persisted-config-load` must be fast (~2–5 ms) on
  a true first launch. A slow iter000 (~cached-mode cost) means app data survived a failed
  harness uninstall and the pass's first-launch sample is poisoned.
- **Record the compile state** (`dumpsys package dexopt` after each install) and this device's
  silicon temperature per pass. Both are conditions you will need to explain outliers later,
  and compile state can flip pass medians by tens of percent on its own.
- These and the remaining harness traps (SDK-pin reverts, APK-bytes changes, the wiped trace
  output dir) are detailed in `references/interpreting-results.md`.

## Interpretation gotchas

- **Absolute timings are build-type-specific.** This skill measures the *benchmark* variant:
  R8-minified and profileable-but-NOT-debuggable. A debuggable build is materially slower (JIT
  and debug-path overhead inflate init), so its numbers are not interchangeable with these, in
  either direction. Never compare a benchmark-build measurement against a debuggable-build one,
  and always state which build type produced a number. Only the benchmark variant is a
  defensible proxy for what users experience; a debuggable build is for behavioural checks
  (e.g. reading cached payloads), not for timing.
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
