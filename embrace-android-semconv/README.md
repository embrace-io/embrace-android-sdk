# embrace-android-semconv

Defines Embrace's [semantic conventions](https://opentelemetry.io/docs/concepts/semantic-conventions/) for Android.

## Regenerating

The Kotlin under `src/main/kotlin/io/embrace/android/embracesdk/semconv/` is generated using
[weaver](https://github.com/open-telemetry/weaver). The specific version used is defined in
[`versions.env`](versions.env). The install script itself is fetched from 
[`embrace-io/embrace-semconv`](https://github.com/embrace-io/embrace-semconv) using the
version tag for the Embrace semantic conventions registry. To run this locally, 
run the script in the `embrace-semconv` repo, and it will install the correct version
to a location that is in your `PATH`.

If you change anything under `src/main/semconv/`, `src/main/templates/`, or change the Weaver
version, rerun the generate task and commit the regenerated source code together with your change*:

```
./gradlew :embrace-android-semconv:generateEmbraceSemanticConventions
```

The `Semconv drift` CI workflow fails any PR whose committed generated code doesn't match what the
model and templates should produce.
