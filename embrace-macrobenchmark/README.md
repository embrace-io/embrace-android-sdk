# Embrace Android SDK: Macrobenchmark

`SdkInitBenchmark` cold-starts `:embrace-macrobenchmark-app` ten times and reports startup timings
plus the duration of `emb-sdk-start`, the trace section wrapping `Embrace.start()`. The app depends
on `:embrace-android-sdk` as a project dependency, so benchmarks measure the working tree.

## Running

```bash
scripts/macrobenchmark.sh
```

That runs the benchmark and copies the perfetto traces to `claude-output/macrobenchmark/<device>/`.
The two halves are usable separately: `scripts/run-macrobenchmark.sh --help` and
`scripts/grab-macrobenchmark-output.sh [dest]`.

Needs one connected device on API 29+;.

## Running Gradle directly

```bash
./gradlew publishToMavenLocal -Psigning.skip
./gradlew :embrace-macrobenchmark:connectedBenchmarkAndroidTest \
    -Pembrace.macrobenchmark.instrument=true \
    -Pandroid.testInstrumentationRunnerArguments.androidx.benchmark.suppressErrors=EMULATOR
```

`embrace.macrobenchmark.instrument` applies the Embrace gradle plugin from mavenLocal, the only
thing that can instrument the app with the `app_id` in
`embrace-macrobenchmark-app/src/main/embrace-config.json`. It defaults to `false` so a checkout that
hasn't published still compiles. Without it the SDK has no appId and refuses to start; the logcat
says either `started for appId = abcde` or `without an app ID`.

The two invocations cannot be combined — the property is read in the app's `buildscript {}` block,
so the plugin must be in mavenLocal before the benchmark configures.
