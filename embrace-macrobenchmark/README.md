# Embrace Android SDK: Macrobenchmark

`SessionBenchmark` cold-starts `:embrace-macrobenchmark-app` ten times, ending a session and
starting the next one on each. Each iteration fills the session with telemetry (`session-workload`),
calls `Embrace.endUserSession()` (`session-end`), then waits ~2s for the SDK's queued persistence
writes so they land in the trace.

Every benchmark runs once per opentelemetry-kotlin implementation (`compat` and `regular`), which
appear as a suffix on the test name, e.g. `sessionEnd[compat]`.

It wipes app data per iteration because the SDK restores a persisted user session in a new process
and declines a manual end for an iteration that inherits one. The app depends on
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

The two halves are usable separately: `scripts/run-macrobenchmark.sh --help` and
`scripts/grab-macrobenchmark-output.sh [dest]`.

Needs one connected device on API 29+.

## Running Gradle directly

```bash
./gradlew :embrace-macrobenchmark:connectedBenchmarkAndroidTest
```

The app needs the Embrace gradle plugin applied, as that is the only thing that can instrument it
with the `app_id` in `embrace-macrobenchmark-app/src/main/embrace-config.json`, and the SDK refuses
to start without one. The plugin is a subproject of this build, so it has to come from mavenLocal:
the benchmark task publishes it there itself, and the app applies it whenever a copy is present.

A buildscript classpath is resolved before any task runs, so that publish lands in time for the
*next* build. On a checkout that has never published, the first run therefore builds an
un-instrumented app and fails with `done: sdk-not-started`; run it again and it works. From then on
it stays current on its own.

Running from Android Studio works once mavenLocal has a copy, which either command above puts there.
Studio never publishes one itself, so if it reports `done: sdk-not-started`, run
`./gradlew :embrace-gradle-plugin:publishToMavenLocal` and build again. `MacrobenchmarkRunner`
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

The benchmark records only the app's own [atrace sections](https://perfetto.dev/docs/data-sources/atrace) and counters.
The default macrobenchmark config captures system-wide scheduling data as well, which costs tens of
megabytes an iteration; this drastically reduces size for analysis. The tradeoff
is that lots of other data is omitted.

androidx.benchmark wraps each trace in a zip. `scripts/analyse-trace.sh` and
`scripts/analyse-trace-iterations.sh` read that as it is; unpack one only to open it in something that
will not, such as the perfetto UI:

```bash
unzip -p <bundle>.perfetto-trace Trace_output.pb > trace.perfetto
```

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
