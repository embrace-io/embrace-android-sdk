# Embrace Android SDK - Development Guide

This document captures the development conventions, architecture, and rules for the Embrace Android SDK.
It is intended for AI coding agents and human contributors alike.

> **Canonical sources**: This file distills conventions from `CONTRIBUTING.md`, `buildSrc/`, `config/detekt/`,
> `.editorconfig`, module READMEs, and build scripts. When those sources change, this file should be updated.
>
> **Last update**: February 9 2026. AI agents should regenerate this document when the project's structure or
> tooling materially changes or seems out-of-date, and allow humans to review the changes.

---

## Project Overview

The Embrace Android SDK is an observability SDK for Android apps built on [OpenTelemetry](https://opentelemetry.io).
It captures performance telemetry (spans, logs, crashes, network requests) and delivers it to the Embrace backend.
It is published to Maven Central under the `io.embrace` group.

---

## Build & Toolchain

Gradle (Kotlin DSL) with convention plugins in `buildSrc/`. All versions — Kotlin, JVM target, minSdk/compileSdk, and every dependency
— live in `gradle/libs.versions.toml` and `gradle.properties`; read those rather than a copy here. The SDK targets an older Kotlin and
JVM level than it compiles with, so check `kotlinCoreLibrariesVersion` before using recent stdlib APIs.

### Key Commands

```bash
# Full build (compile + lint + detekt + unit tests)
./gradlew build

# Build excluding slow integration tests
./gradlew build -x embrace-gradle-plugin-integration-tests:test

# Run tests for a specific module
./gradlew :embrace-android-core:test

# Run integration tests (in the SDK module)
./gradlew :embrace-android-sdk:test

# Code coverage report (XML)
./gradlew koverXmlReport

# Build the example app
cd examples/ExampleApp && ./gradlew bundleRelease

# Update binary compatibility API dumps
./gradlew apiDump
```

---

## Module Architecture

See `settings.gradle.kts` for the module list and each module's `README.md` for its purpose. Layering conventions worth knowing up
front:

- `embrace-android-core` is the main implementation module and is hidden from library consumers behind `embrace-android-sdk`.
- Modules using `embrace-public-api-conventions` are the consumer-facing surface, with `kotlin.explicitApi()`, binary compatibility
  validation, and Dokka enforced.
- `embrace-internal-api` is shared with Embrace's React Native/Unity/Flutter SDKs but is not exposed to app developers.
- Each `embrace-android-instrumentation-*` module captures one type of telemetry, and they broadly follow this pattern:
    - Implement a data source class that extends framework types from `embrace-android-instrumentation-api`
    - Register with the `InstrumentationRegistry` in `embrace-android-core`
    - Use `SchemaType` from `embrace-android-instrumentation-schema` for telemetry attributes

---

## Convention Plugins (`buildSrc/`)

All modules use convention plugins instead of duplicating build configuration. When creating a new module, apply the appropriate
convention plugin rather than configuring build settings directly — see `buildSrc/src/main/kotlin/embrace-*-conventions.gradle.kts`
for the available plugins and what each applies.

---

## Code Style & Formatting

> Formatting and style are enforced mechanically and the build fails on any violation: detekt (`config/detekt/`, zero-tolerance with
> auto-correct), `.editorconfig`, `allWarningsAsErrors`, and Android Lint. Read those configs for the current rule set rather than
> relying on a copy here.

### Conventions

- All new code must be Kotlin
- Package: `io.embrace.android.embracesdk.internal.*` for internal code
- Public API classes live under `io.embrace.android.embracesdk` (non-`internal` packages)
- Use `@InternalApi` annotation to mark APIs that are internal but technically visible
- Prefer `internal` visibility for implementation classes
- Public API modules use `kotlin.explicitApi()` - all declarations must have explicit visibility
- No business logic in payload data classes (enforced by detekt)
- JSON files should not have a trailing newline
- Prefer interfaces rather than concrete classes for public APIs

---

## Dependency Injection

The SDK uses **manual dependency injection** via module interfaces:

```
InitModule -> CoreModule -> EssentialServiceModule -> ...
```

- Each DI module is defined as an **interface** in `embrace-android-core/.../injection/`
- Implementations are `*Impl` classes (e.g., `InitModuleImpl`)
- Fake implementations exist in `embrace-test-fakes` for testing (e.g., `FakeInitModule`)
- The `ModuleInitBootstrapper` in `embrace-android-sdk` wires all modules together

---

## Concurrency

- **No coroutines** in the SDK codebase - concurrency uses `ScheduledExecutorService`
- `BackgroundWorker` (in `embrace-android-infra`) wraps `ScheduledExecutorService` to limit API surface
- `WorkerThreadModule` manages thread pools
- Tests use `FakeWorkers` for deterministic scheduling

---

## Testing

### Frameworks

- **JUnit 4** (not JUnit 5)
- **Robolectric** for Android framework mocking in unit tests
- **MockK** for Kotlin mocking
- **OkHttp MockWebServer** for HTTP testing

### Test Organization

**Unit tests**: `src/test/kotlin/` in each module

- Standard JUnit 4 tests with `@Test`, `@Before`, etc.
- Use backtick-quoted test names: `` `sensitive properties are redacted` ``
- Tests are `internal class`

**Integration tests**: `src/integrationTest/kotlin/` in `embrace-android-sdk`

- Uses `SdkIntegrationTestRule` (a JUnit `ExternalResource` rule)
- Boots the full SDK with controlled fakes for time, config, and delivery
- Tests live in `testcases/` and `testcases/features/` packages
- Test framework utilities in `testframework/` package

**Gradle plugin integration tests**: `embrace-gradle-plugin-integration-tests`

- Uses Gradle TestKit
- Supports remote JVM debugging (see module README)

### Fake Conventions

- **Prefer fakes over mocks**: The codebase has extensive hand-written fakes in dedicated modules
- Fakes are named `Fake*` (e.g., `FakeConfigService`, `FakeClock`, `FakePayloadStore`)
- Module-specific fakes live in `embrace-android-*-fakes` modules
- Cross-cutting fakes live in `embrace-test-fakes`
- Common test utilities in `embrace-test-common`
- Each module's tests may also have local fakes in `src/test/kotlin/.../fakes/`
- Mocks should not be used unless they are unavoidable

### Integration-Test Flake Patterns

Three recurring causes of intermittent failures in the `embrace-android-sdk` integration tests. Check for all of them when a test is flaky, and avoid them when writing new ones. A golden-file failure message ends with `Dump of full JSON:` followed by the observed payload; read that before theorising about which span or attribute is extra.

**1. Asserting delivery *order* against telemetry content instead of payload metadata.**
Payload **delivery order** is decided by `StoredTelemetryComparator` over the stored **metadata** (`envelopeType → timestamp → uuid → complete`) in `SchedulingServiceImpl.findNextPayload`. But helpers like `assertSessionsDeliveredInOrder` / `assertLogsDeliveredInOrder` assert on a value embedded *inside* the telemetry (e.g. the session-part span's `startTimeNanos`). For **live** sessions the two line up (a session is stored right after it ends, so metadata order tracks span order), so the default `assertOrdering = true` is fine. For **resurrected / bulk-delivered** payloads (crash resurrection, multiple cached sessions flushed together) they decouple: resurrection re-stores via `IntakeService.take(metadata = copy(...))`, which *preserves* the original metadata timestamp, and that has no guaranteed relationship to the embedded span start time. Delivery-by-start-time is **not a contract**. Fix: pass `getSessionEnvelopes(n, assertOrdering = false)` / `getLogEnvelopes(n, logsOrderedByTimestamp = false)` for these scenarios — the real assertions look payloads up by part id / log type, which is order-independent anyway. Don't try to "fix the data" (e.g. force a clock gap) to satisfy the order check; the gap usually already exists in the metadata and isn't what the assertion reads.

**2. Real workers + no synchronization barrier in crash/teardown tests.**
`SdkIntegrationTestRule.runTest` runs `testCaseAction → assertAction` with no worker drain between them. If the test leaves background workers real, async work (especially the recurring `PeriodicCacheWorker`, which ticks every ~2s on wall-clock time) races the assertion and the order-sensitive crash teardown, producing non-deterministic payload counts/contents (e.g. `NoSuchElementException` when the expected crash session hasn't been persisted yet). Fix: fake the relevant `Worker.Background` workers and set `getFakedWorkerExecutor(worker).blockingMode = false` so their tasks run **inline/synchronously** on the test thread (scheduled/periodic tasks then queue instead of firing on real time). For crash tests fake at least `PeriodicCacheWorker` (the real culprit); the crash session itself is persisted synchronously via the IntakeService `CRASH_RECEIVED` path, so this stays deterministic. Faking serializes timing/observability only — the asserted crash data is set synchronously under the orchestrator lock, so determinism doesn't weaken what the test validates.

A variant of (1): two payloads stored at the **same fake-clock instant** tie on type and timestamp, so the comparator falls through to `uuid`, which in tests comes from a seeded PRNG shared by every UUID consumer and is effectively random. Never assert on the arrival index of such payloads; match them by a content key (e.g. sort AEI logs by `emb.android.aei_crash_number`).

**3. Real timers comparing fake-clock timestamps.**
`BlockedThreadDetector` (thread blockage) runs its heartbeat check on a real timer but reads every timestamp from the injected clock. A test that jumps the `FakeClock` (as `recordSession` does, by ~40 s) while the Robolectric main looper is paused makes the main thread look blocked for that long, and if a tick lands in that window the session gains a spurious `emb-thread-blockage` span with a sample. Ordinary tests never notice; exact-span-list (golden-file) tests fail intermittently. The framework keeps the service off unless a test passes `threadBlockageWatchdogThread`, and the module graph honours a supplier's `null` for this optional service rather than falling back to the real implementation. When adding another optional service with a supplier seam, keep that distinction: a supplier returning `null` is a decision, not the absence of a supplier.

---

## Public API Compatibility

- Public API modules use the [Binary Compatibility Validator](https://github.com/Kotlin/binary-compatibility-validator)
- API dumps are stored in `<module>/api/<module-name>.api`
- Any change to public API signatures will fail CI until `./gradlew apiDump` is run
- Public API modules must have Dokka documentation; build fails on Dokka warnings
- `internal` packages are suppressed in generated docs

---

## PR Guidelines

From `CONTRIBUTING.md`:

- PRs must have a stated goal and detailed description
- Include test coverage and documentation where applicable
- Pass all CI checks (build, lint, detekt, tests)
- Require at least one approval from a project member
- **AI disclosure required**: none, autocomplete/research, or mostly AI-generated
- Follow existing code and naming conventions
- Lint suppression must be done in code with explanation
- Commits should be reasonably small (<500 lines diff) with proper messages
- PR template has `Goal` and `Testing` sections
- Verify changes with `./gradlew build -x embrace-gradle-plugin-integration-tests:test`

---

## Key Patterns to Follow

### Adding a New Instrumentation Module

1. Create module named `embrace-android-instrumentation-<feature>`
2. Apply `embrace-prod-android-conventions` plugin
3. Implement a data source extending types from `embrace-android-instrumentation-api`
4. Define schema attributes using `SchemaType` from `embrace-android-instrumentation-schema`
5. Register in the instrumentation registry
6. Add to `embrace-android-sdk/build.gradle.kts` as an `implementation` dependency
7. Add module to `settings.gradle.kts`
8. Add a `README.md` describing the module's purpose
9. Create a fakes module if needed for testing

### Adding a New Payload Model

1. Add to `embrace-android-payload` module
2. Use Moshi annotations: `@JsonClass(generateAdapter = true)` and `@Json(name = "...")`
3. Use `data class` with `val` properties (immutability enforced by detekt)
4. No business logic in payload classes (enforced by detekt)

### Writing Tests

1. Prefer fakes over mocks (check `embrace-test-fakes` first)
2. Use `lateinit var` + `@Before` setup pattern
3. Use descriptive backtick-quoted test names
4. For integration tests, use `SdkIntegrationTestRule`
5. Mark test classes as `internal`

---

## Important Constraints

- **Do NOT use `android.util.Pair`** - it's a forbidden import; use `kotlin.Pair`
- **Do NOT add business logic to payload data classes** - detekt enforces immutability and no functions
- **Do NOT use coroutines** - the SDK uses `BackgroundWorker` / `ScheduledExecutorService`
- **Do NOT bump Compose version past 1.0.5** - `getAllSemanticsNodes` signature changed in 1.6+, breaking backward compat
- **Some dependency versions are pinned** due to an unpatched AGP issue (see comments in `libs.versions.toml`)
- **Configuration cache** is enabled with `problems=fail` - all build logic must be compatible
- **Public API changes** require running `./gradlew apiDump` and committing the updated `.api` files

## Writing code for SDKs

- Prefer interfaces in public APIs over concrete symbols/constructors
- Minimize the public API surface area
- Use defensive programming and be paranoid with your error checking
- The SDK must not crash. When throwing exceptions make sure that something will catch it that isn't the library consumer.
