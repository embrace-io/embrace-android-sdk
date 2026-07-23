# embrace-android-semconv

Defines Embrace's [semantic conventions](https://opentelemetry.io/docs/concepts/semantic-conventions/) for Android.

## Regenerating

The Kotlin under `src/main/kotlin/io/embrace/android/embracesdk/semconv/` is generated with
[weaver](https://github.com/open-telemetry/weaver), pinned by `WEAVER_VERSION` in
[`versions.env`](versions.env). CI installs it via the shared **`setup-weaver`** GitHub Action in
[`embrace-io/embrace-semconv`](https://github.com/embrace-io/embrace-semconv), passing this repo's
pinned version.

To run the regeneration locally, install weaver using that script, and it will put the executable in
your `PATH`, e.g. run `.github/actions/setup-weaver/install-weaver.sh` from a checkout of
the `embrace-semconv` repo. Pass `WEAVER_VERSION` as a parameter to install the same version.

If you change anything under `src/main/semconv/`, `src/main/templates/`, or change the Weaver
version, rerun the generate task and commit the regenerated source code together with your change*:

```
./gradlew :embrace-android-semconv:generateEmbraceSemanticConventions
```

The `Semconv drift` CI workflow fails any PR whose committed generated code doesn't match what the
model and templates should produce.
