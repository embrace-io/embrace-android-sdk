# Embrace Android SDK: Macrobenchmark

`SdkInitBenchmark` cold-starts `:embrace-macrobenchmark-app` ten times and reports startup timings
plus the duration of `emb-sdk-start`, the trace section wrapping `Embrace.start()`. The app depends
on `:embrace-android-sdk` as a project dependency, so benchmarks measure the working tree.

## Running

```bash
./gradlew publishToMavenLocal -Psigning.skip
./gradlew :embrace-macrobenchmark:connectedBenchmarkAndroidTest \
    -Pembrace.macrobenchmark.instrument=true
```

That flag applies the Embrace gradle plugin from mavenLocal, the only thing that can instrument the
app with the `app_id` in `embrace-macrobenchmark-app/src/main/embrace-config.json`. It defaults to
`false` so a checkout that hasn't published still compiles.


