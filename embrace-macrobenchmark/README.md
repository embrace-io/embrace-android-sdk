# Embrace Android SDK: Macrobenchmark

`SessionBenchmark` cold-starts `:embrace-macrobenchmark-app` ten times, ending a session and
starting the next one on each. Each iteration fills the session with telemetry (`session-workload`),
calls `Embrace.endUserSession()` (`session-end`), then waits ~2s for the SDK's queued persistence
writes so they land in the trace.

It wipes app data per iteration because the SDK restores a persisted user session in a new process
and declines a manual end for an iteration that inherits one. The app depends on
`:embrace-android-sdk` as a project dependency, so benchmarks measure the working tree.

## Running

```bash
scripts/macrobenchmark.sh
```

That runs the benchmark and copies the perfetto traces to `perf/macrobenchmark/<device>/`.
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

## Selecting the persistence layer

Without rebuilding the APK, via a global setting the app reads before starting the SDK:

```bash
adb shell settings put global embrace_pct_multi_file_persistence 100   # multi-file layer
adb shell settings put global embrace_pct_multi_file_persistence 0     # legacy layer
adb shell settings delete global embrace_pct_multi_file_persistence    # SDK default (legacy)
```

The app turns that into a remote config on disk, which the SDK reads at startup. It only works
because the benchmark's `pm clear` drops the binary config cache that would otherwise take priority.
