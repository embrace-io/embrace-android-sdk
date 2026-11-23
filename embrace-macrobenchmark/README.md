# Embrace Android SDK: Macrobenchmark

`SessionBenchmark` cold-starts `:embrace-macrobenchmark-app` ten times, ending a session and
starting the next one on each. Each iteration fills the session with telemetry (`session-workload`),
calls `Embrace.endUserSession()` (`session-end`), then waits ~2s for the SDK's queued persistence
writes so they land in the trace.

Every benchmark runs once per opentelemetry-kotlin implementation (`compat` and `regular`), which appear
as a suffix on the test name, e.g. `sessionEnd[compat]`. `ResurrectionBenchmark` also runs once per
persistence mode, and its test names carry the fixture and both modes, e.g.
`resurrect[large_part-multi-file-compat]`.

`SessionBenchmark` wipes app data per iteration because the SDK restores a persisted user session in a
new process and declines a manual end for an iteration that inherits one. The app depends on
`:embrace-android-sdk` as a project dependency, so benchmarks measure the working tree.

## Running

```bash
scripts/macrobenchmark.sh
```

That runs `SessionBenchmark` and copies the perfetto traces to `perf/macrobenchmark/<device>/`.
Pass `--test` to run a different one:

```bash
scripts/macrobenchmark.sh --out perf/macrobenchmark/scenarios \
    --test io.embrace.android.embracesdk.macrobenchmark.PersistenceScenarioBenchmark
```

`OtelInitBenchmark` cold-starts the app with each opentelemetry-kotlin implementation and reports
the `emb-otel-sdk-wrapper-init`, `emb-otel-tracer-init` and `emb-otel-logger-init` sections, to
compare what assembling the OTel SDK costs in each:

```bash
scripts/macrobenchmark.sh --out perf/macrobenchmark/otel-init \
    --test io.embrace.android.embracesdk.macrobenchmark.OtelInitBenchmark
```

`ResurrectionBenchmark` measures what resurrecting the session part a dead app instance left on disk costs
the next launch, in each persistence mode. What that costs grows with how much the part holds, so each
of `ResurrectionScenarios` leaves one dead part of a different size: `small_part`, `medium_part`, `large_part`, and
`capped_part`, which fills the SDK's default limits per session part (500 spans created through its API, 2000
network requests and 100 each of breadcrumbs and session properties). Each part records requests through
the manual network API and completed spans with times of their own, adds breadcrumbs and session properties,
and leaves spans running when its app instance dies. The benchmark records the fixture once, with the SDK under test: a launch
that runs the scenario and is killed once the SDK has written it. Before every iteration, a launch that does
not start the SDK restores the recorded part; the measured launch then cold-starts the SDK and waits until
the part has been resurrected. It reports `emb-sdk-start` (the SDK's start on the main
thread), `emb-mf-read-session-parts` and `emb-resurrect-payloads` (the background worker resurrecting the
part from multi-file mode's files or from single-file mode's cached payload; their sum is its wall clock
time), and the app's peak Java heap and resident memory. The memory figures need the `memory` capture
profile, so the benchmark refuses to run under any other, and they only compare between modes and fixtures
on one device: how a device reports them differs across Android versions. It
runs 10 iterations a fixture, a pass; a
comparison should take its repetition from several passes rather than from more iterations in one.
Pass `-e resurrection.iterations N` to `am instrument`, or `--gradle-arg -Pandroid.testInstrumentationRunnerArguments.resurrection.iterations=N`, to change it:

```bash
scripts/macrobenchmark.sh --out perf/macrobenchmark/resurrection --capture memory \
    --test io.embrace.android.embracesdk.macrobenchmark.ResurrectionBenchmark
```

The two halves are usable separately: `scripts/run-macrobenchmark.sh --help` and
`scripts/grab-macrobenchmark-output.sh [dest]`.

Needs one connected device on API 29+.

## Running Gradle directly

```bash
./gradlew :embrace-macrobenchmark:connectedBenchmarkAndroidTest
```

The app needs the Embrace gradle plugin applied, as that is the only thing that can instrument it
with the `app_id` in `embrace-macrobenchmark-app/src/main/embrace-config.json`, and the SDK refuses
to start without one. The plugin is a subproject of this build, so it has to come from a published
copy in `build/integration-repo`: the benchmark task publishes it there itself, and the app applies
it whenever a copy is present.

A buildscript classpath is resolved before any task runs, so that publish lands in time for the
*next* build. On a checkout that has never published, the first run therefore builds an
un-instrumented app and fails with `done: sdk-not-started`; run it again and it works. From then on
it stays current on its own.

Running from Android Studio works once `build/integration-repo` has a copy, which either command
above puts there. Studio never publishes one itself, so if it reports `done: sdk-not-started`, run
`./gradlew :embrace-gradle-plugin:publishAllPublicationsToIntegrationTestRepository` and build again.
A `./gradlew clean` deletes the copy. `MacrobenchmarkRunner`
handles the other Studio pitfall: `MacrobenchmarkRule` skips every test unless
`androidx.benchmark.enabledRules` names Macrobenchmark, and Studio builds its own `am instrument`
command rather than passing the module's `testInstrumentationRunnerArguments`.

## Generating the baseline profile

```bash
scripts/generate-baseline-profile.sh
```

That runs `BaselineProfileGenerator` against the app's `baselineProfile` variant on the
`pixel6Api34` Gradle managed device (API 34 AOSP), once with each opentelemetry-kotlin
implementation (compat and regular), and overwrites `embrace-android-sdk/src/main/baseline-prof.txt`
with the union of both profiles. Gradle downloads and boots the emulator itself. The
`Generate Baseline Profile` workflow runs the same script on CI and opens a PR with the result.

## Traces

By default the benchmark records only the app's own [atrace sections](https://github.com/google/perfetto/blob/v46.0/docs/data-sources/atrace.md)
and counters. The default macrobenchmark config captures system-wide scheduling data as well, which
costs tens of megabytes an iteration; this drastically reduces size for analysis. The tradeoff is
that lots of other data is omitted, which is what the second capture profile is for.

### Capture profiles

`--capture` picks the perfetto config a run captures with (`embrace.capture` as an instrumentation
argument when running Gradle directly). The profile is part of
what makes two runs comparable, and the trace records the config it was captured with, so the
analysis reads which one it has rather than being told.

`SessionBenchmark` and `PersistenceScenarioBenchmark` take any profile, `ResurrectionBenchmark` only
`memory`, and `OtelInitBenchmark` always captures `perfetto-config-app-only.pbtx` (the `app-only` config).
The profiles and their configs live in `embrace-benchmark-common` (`CaptureProfile`).

| profile | config | holds | per iteration |
|---|---|---|---|
| `app-only` (default) | `perfetto-config-app-only.pbtx` | the app's sections and counters, thread names | ~120 KB |
| `with-context` | `perfetto-config-with-context.pbtx` | the same, plus every thread's scheduling (switches, wakings, blocked reasons, compact-encoded), cpu frequency and limits, binder transactions, ART's class-load and GC sections (`dalvik`) from every process, and memory available / swap once a second | ~2 MB |
| `memory` | `perfetto-config-memory.pbtx` | the `app-only` contents plus what `MemoryUsageMetric` reads: the app's resident set size (`kmem/rss_stat`) and ART's heap size counter (the `dalvik` category), and the `task_newtask` / `task_rename` events without which a process named before it renames itself from `zygote64` is invisible | ~0.8–2.1 MB |

The `memory` profile leaves out `proc_stats_poll_ms`, which makes that early naming far more likely.

`with-context` is what explaining an outlier needs: which thread state a section spent its time in and on
which cpus, what else was running, and whether the clock had been throttled. Scheduling events arrive for
every task on every cpu, which the kernel cannot filter, so this profile is megabytes an iteration. The
app's sections get a ring buffer of their own so a burst of scheduling cannot evict the start of a launch.

```bash
scripts/macrobenchmark.sh --out perf/macrobenchmark/context --capture with-context
```

The sizes are approximate and grow with what a benchmark does in an iteration. They are the protobuf
trace; the zip androidx.benchmark wraps it in is roughly a third to a half of that.

The ART sections are the one thing `app-only` cannot show at all: `atrace_apps` alone leaves them out, and
only the `dalvik` category brings them, so a class-load check needs `with-context`.

androidx.benchmark wraps each trace in a zip. `scripts/analyse-trace.sh` and
`scripts/analyse-trace-iterations.sh` read that as it is; unpack one only to open it in something that
will not, such as the perfetto UI:

```bash
unzip -p <bundle>.perfetto-trace Trace_output.pb > trace.perfetto
```

## Embrace's server backend endpoints

The app's `embrace-config.json` (`src/main`, and `src/baselineProfile` for the baseline profile build) points the
SDK's config and data endpoints at `LocalEmbraceEndpoint` (`embrace-benchmark-endpoint`), which every benchmark and
`BaselineProfileGenerator` serve on the device for the length of each test, so nothing leaves the device. It serves
HTTPS on `localhost`, config on port 48711 and data on 48712, so the app does the TLS work it does against Embrace's
servers: a handshake on each of the two connections and verification of a chain shaped like theirs. The app trusts the
endpoint's root, which the build generates, for `localhost` alone (`src/main/res/xml/network_security_config.xml`). It
answers the config fetch with an empty remote config (`{}`) and every delivery with 200. When a test ends, it logs what
it answered to logcat (`adb logcat -d -s System.out:I`, the `local Embrace endpoint answered:` line).

These benchmarks measure persistence, delivery, sessions and OTel setup, not the network, so they all use the local
endpoint: it keeps them offline and makes every delivery succeed. It does its work on the device, but a warm-up
compiles its code before each test, and in cold-start measurements its CPU didn't shift launch time or SDK start
measurably. Cold-start benchmarks are a different case: there, the network work overlaps what's measured, and the
ExampleApp's `StartupBenchmarks` can run against Embrace's servers instead (its KDoc says how).

The remote config a launch runs with is still the one the app writes from global settings (below): the SDK reads it
at startup, and the next launch's write replaces whatever the fetch saved. That written config has no ETag, so every
launch fetches the config again, where a customer's app would revalidate its cached copy.

## Selecting the persistence mode and OTel implementation

Without rebuilding the APK, via global settings the app reads before starting the SDK:

```bash
adb shell settings put global embrace_pct_multi_file_persistence 100   # multi-file mode
adb shell settings put global embrace_pct_multi_file_persistence 0     # single-file mode
adb shell settings delete global embrace_pct_multi_file_persistence    # SDK default (single-file)

adb shell settings put global embrace_pct_otel_kotlin_sdk 100          # regular opentelemetry-kotlin
adb shell settings put global embrace_pct_otel_kotlin_sdk 0            # compat opentelemetry-kotlin
adb shell settings delete global embrace_pct_otel_kotlin_sdk           # SDK default (compat)
```

The app turns those into a remote config on disk, which the SDK reads at startup, and deletes the
binary config cache that would otherwise take priority. Every benchmark and `BaselineProfileGenerator`
set and clear `embrace_pct_otel_kotlin_sdk` themselves, so setting it by hand has no effect on them.
`ResurrectionBenchmark` likewise sets and clears `embrace_pct_multi_file_persistence` itself (and the
`embrace_benchmark_restore_fixture` setting that drives its fixture). The other benchmarks run in whichever
persistence mode the setting selects.
