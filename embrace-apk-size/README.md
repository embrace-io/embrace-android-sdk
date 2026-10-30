# embrace-apk-size

Measures how much the Embrace SDK adds to an app's APK. It builds the same app twice, with (`embrace` flavor) and without
(`baseline` flavor) the SDK, and reports the difference broken down by code, native libraries, resources and other files.
Both are release builds with R8 and resource shrinking, packaging only `arm64-v8a` native libraries.

The `embrace` flavor includes only the core SDK, without optional instrumentation modules. The app has no code:
`proguard-rules.pro` keeps OkHttp and Firebase Messaging in both flavors so that only the SDK's cost shows in the
difference. The Embrace gradle plugin isn't applied; `embrace-proguard-rules.pro` keeps the bytecode entrypoints it
would otherwise call.

```bash
./gradlew :embrace-apk-size:apkSizeReport
```

The report is printed and written to `build/reports/apk-size/`. Passing `-Pembrace.apkSizeMain=<path>` with the
`apk-size.properties` from a build of main adds a column showing how the SDK's cost differs from main. CI runs it in the
`apk-size` job of the `CI` workflow, comparing each PR with main and posting the report as a PR comment that later
pushes update.
