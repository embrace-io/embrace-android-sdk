# Embrace Android SDK - Development Guide

Conventions and rules for AI coding agents and human contributors. Distilled from `CONTRIBUTING.md`, `buildSrc/`,
`config/detekt/`, `.editorconfig`, module READMEs and build scripts; update this file when those change.

> **Last update**: October 22 2026.

## Project Overview

An observability SDK for Android apps, built on [OpenTelemetry](https://opentelemetry.io). It captures spans, logs,
crashes and network requests and delivers them to the Embrace backend. Published to Maven Central under `io.embrace`.

## Verifying Changes

**`./gradlew verify --continue` is the gold standard for local verification and MUST be run before declaring a change
done, pushing, or restacking.** It runs every module's `check` (compile, detekt, Android Lint, API compatibility, unit
tests, and the `embrace-android-sdk` integration tests) plus one Gradle plugin integration sanity test.

- **Do NOT run individual Gradle tasks or tests** (`:module:test`, `:module:lintDebug`, `--tests ...`). The build and
  configuration caches make unchanged modules near-instant, and task names differ between Android and JVM modules.
- `--continue` surfaces every failure in one pass instead of one Gradle run per fix.
- For changes that might affect Gradle plugin behavior, run `./gradlew build --continue` instead; it also runs every
  Gradle plugin integration test.
- On stacked branches, verify the base of the stack first so one broken commit doesn't fail every PR above it.

### Other Commands

```bash
./gradlew apiDump                                     # update public API dumps
CI=true ./gradlew koverXmlReport -Pembrace.coverage   # coverage (Kover only applies with CI set)
cd examples/ExampleApp && ./gradlew bundleRelease     # build the example app

scripts/macrobenchmark.sh                             # run the startup macrobenchmark and collect traces
scripts/analyse-trace.sh <trace.perfetto.gz>          # analyse one perfetto trace (--help for options)
scripts/analyse-trace-iterations.sh perf/macrobenchmark/<device>                      # one report per run
scripts/compare-trace-iterations.sh perf/macrobenchmark/<base> perf/macrobenchmark/<candidate>
```

## Build & Architecture

- Gradle Kotlin DSL with convention plugins in `buildSrc/src/main/kotlin/embrace-*-conventions.gradle.kts`. New modules
  apply a convention plugin rather than configuring the build directly.
- All versions live in `gradle/libs.versions.toml` and `gradle.properties`. The SDK targets an older Kotlin stdlib
  (`kotlinCoreLibrariesVersion`) than it compiles with, so check it before using recent stdlib APIs.
- Configuration cache is enabled with `problems=fail`; all build logic must be compatible.
- See `settings.gradle.kts` for modules and each module's `README.md` for its purpose. Key layering:
    - `embrace-android-sdk` is the consumer entry point and wires everything together; `embrace-android-core` is the
      main implementation and is hidden from consumers.
    - Modules applying `embrace-public-api-conventions` are the public surface: `kotlin.explicitApi()`, binary
      compatibility validation (`<module>/api/*.api`), and Dokka with warnings as errors.
    - `embrace-internal-api` is shared with Embrace's React Native/Unity/Flutter SDKs, not app developers.
    - Each `embrace-android-instrumentation-*` module captures one type of telemetry: a data source built on
      `embrace-android-instrumentation-api`, registered with the `InstrumentationRegistry`, using `SchemaType` from
      `embrace-android-instrumentation-schema` for attributes.
- `embrace-android-sdk/src/main/baseline-prof.txt` ships in the AAR and merges into consumer apps' profiles. When adding
  or removing a dependency, check whether the baseline profile generation rules need updating.

## Code Conventions

Formatting and style are enforced by detekt (`config/detekt/`, zero tolerance), `.editorconfig`,
`allWarningsAsErrors` and Android Lint; read those configs rather than relying on a copy here.

- Kotlin only. Internal code lives in `io.embrace.android.embracesdk.internal.*`; public API in non-`internal` packages.
- Prefer `internal` visibility. Mark technically-visible internal APIs with `@InternalApi`.
- Public APIs: prefer interfaces over concrete classes/constructors, and keep the surface minimal.
- **The SDK must not crash.** Program defensively; anything thrown must be caught before it reaches the consumer.
- **Concurrency**: no coroutines. Use `BackgroundWorker` (`embrace-android-infra`, wrapping
  `ScheduledExecutorService`) and `WorkerThreadPool`.
- **Dependency injection** is manual: `createRealSdkInstance` (`embrace-android-sdk`) constructs every service as a
  local. Implementations stay `internal`, exposed via top-level `create*` functions in
  `embrace-android-core/.../injection/*Factories.kt`. Test seams are nullable `SdkOverrides` fields resolved with
  `override ?: real`.
- **Payload models** (`embrace-android-payload`): immutable `data class`es with `val`s and no logic (enforced by
  detekt), annotated with `@Serializable`/`@SerialName`. Pass serializers explicitly (`Foo.serializer()`); never
  resolve them at runtime.
- JSON files have no trailing newline.
- Do not use `android.util.Pair` (forbidden import); use `kotlin.Pair`.
- Do not bump Compose past 1.0.5 (`getAllSemanticsNodes` changed in 1.6+). Other pinned versions are explained by
  comments in `libs.versions.toml`.

### Mandatory Change Requirements

- Keep PRs under ~500 changed lines unless absolutely unavoidable.
- Consolidate tests into existing ones rather than adding parallel test cases.
- Do not change the public API without prior agreement.
- Implement `AutoCloseable`, not a bare `fun close()` or `java.io.Closeable`.
- Prefer `@Volatile` over atomics when sufficient; prefer avoiding locks on the public API hot path unless necessary.
- No import aliases (`import … as …`).
- No `/* STATE */`-style marker comments in tests; put the state in the test name.
- Do not hardcode defaults to paper over nullability; trace where the value really comes from.

## Testing

- JUnit 4, Robolectric, OkHttp MockWebServer. Test classes are `internal`, with backtick-quoted descriptive names and
  `lateinit var` + `@Before` setup.
- **Prefer fakes over mocks**; use MockK only when unavoidable. Fakes are named `Fake*` and live in
  `embrace-android-*-fakes` (module-specific), `embrace-test-fakes` (cross-cutting, including `TestInitServices` and
  `TestOtelStack` for pre-start services and a real OTel stack), or a module's own `src/test/.../fakes/`. Shared
  utilities live in `embrace-test-common`.
- **Unit tests**: `src/test/kotlin/` in each module.
- **SDK integration tests**: `embrace-android-sdk/src/integrationTest/kotlin/`. They use `SdkIntegrationTestRule` to
  boot the full SDK with fake time, config and delivery; cases live in `testcases/` and `testcases/features/`.
- **Gradle plugin integration tests**: `embrace-gradle-plugin-integration-tests`, using Gradle TestKit (supports remote
  JVM debugging; see its README). `sanityTest` runs in `verify`; the full suite runs in `build`.

### Integration-Test Flake Patterns

Check for these when a test is flaky, and avoid them in new tests. A golden-file failure ends with
`Dump of full JSON:` and the observed payload; read it before theorising about what is extra.

1. **Asserting delivery order against telemetry content.** Delivery order comes from `StoredTelemetryComparator` over
   stored metadata (`envelopeType → timestamp → uuid → complete`), but `assertSessionsDeliveredInOrder` /
   `assertLogsDeliveredInOrder` check values inside the telemetry. These agree for live sessions but not for
   resurrected or bulk-delivered payloads, whose re-stored metadata keeps the original timestamp. Pass
   `getSessionEnvelopes(n, assertOrdering = false)` / `getLogEnvelopes(n, logsOrderedByTimestamp = false)` and look
   payloads up by content; don't force clock gaps. Payloads stored at the same fake-clock instant tie-break on a
   seeded-random `uuid`, so never assert their arrival index; match them by a content key.
2. **Real workers with no synchronization barrier.** `runTest` doesn't drain workers between the action and the
   assertions, so real async work (especially `PeriodicCacheWorker`, ticking every ~2s of wall-clock time) races the
   assertions and crash teardown. Fake the relevant `Worker.Background` workers and set
   `getFakedWorkerExecutor(worker).blockingMode = false` so tasks run inline.
3. **Real timers reading fake-clock timestamps.** `BlockedThreadDetector` ticks on a real timer but reads the fake
   clock, so a `FakeClock` jump while the main looper is paused can produce a spurious `emb-thread-blockage` span. It is
   off unless a test passes `threadBlockageWatchdogThread`. For optional services with a supplier seam, a supplier
   returning `null` means "disabled", not "use the real implementation".

## PR Guidelines

See `CONTRIBUTING.md`. In short: a stated goal and detailed description (`Goal` and `Testing` sections in the
template), tests and docs where applicable, passing CI, one project-member approval.
