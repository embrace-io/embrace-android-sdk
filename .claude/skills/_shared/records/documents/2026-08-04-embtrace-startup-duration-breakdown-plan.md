# SDK Startup Breakdown via EmbTrace Section Durations

**Date:** 2026-08-04
**Status:** ✅ IMPLEMENTED + COMMITTED — ticket **EMBR-13407**, branch
`EMBR-13407/hho/sdk-init-details`, commit `d9892bbee` "SDK init details in spans" (based on main
@ `ae47d561a`). In/awaiting review as of 2026-08-10. All touched modules' tests + `detekt` passed
at implementation time.

## Session close-out (2026-08-10)

**Where things stand:** the full feature (EmbTrace `recordDuration` param + `SectionDurationTracker`
+ StartupService/AppStartupTraceEmitter attribute plumbing + tests), scoped to the 4 high-level
sections, is committed on `EMBR-13407/hho/sdk-init-details`. Working tree has since moved on to
other work (resource-attr-unify stack).

**Next steps after review lands:**
1. Refine + re-add fine-grained traced sections (Hanson's explicit intent). The full candidate
   list with file paths is in "Post-plan revision" below and the session's call-site inventory:
   bootstrap phases (`init-module`, `otel-module`, `persisted-config-load`, `workerthread-init`,
   `config-service-init`, `sdk-disable-check`/`behavior-check`, `span-service-init`, the 12
   generic `<name>-init` sections, `service-registration`), per-module sections (core:
   `okhttp-client-init`, 4× EssentialServiceModule, 5× PayloadSourceModule,
   `load-user-info-from-pref`; otel: `otel-sdk-wrapper-init`, `otel-tracer-init`,
   `otel-logger-init`, `process-identifier-init`), and the racy background ones (crash-ndk ×3,
   `thermal-service-registration`, `power-service-registration`). Each is a one-line flip:
   `EmbTrace.trace("name") {` → `EmbTrace.trace(sectionName = "name", recordDuration = true) {`
   (function-reference sites already use `code =`).
2. Address review feedback on EMBR-13407.

**RESUME:** this session ran in conversation dir `5144a130-6d25-4e2f-96d0-860c3f27d716`
(runtime/tasks dir `0f94900f-22c2-4dfc-b61f-69080453c6bc`); design decisions + their reversals are
chronicled in the Status/constraint notes throughout this doc.
**Post-plan revision (same day):** duration recording is now OPT-IN per call site —
`EmbTrace.trace(sectionName, recordDuration: Boolean = false, code)` only records when
`recordDuration = true` is passed (AND the one-shot flush gate is still open).
**2026-08-05 — scoped down for initial review:** only the HIGH-LEVEL sections are opted in
(`embrace-impl-init`, `bootstrapper-init`, `modules-init`, `post-services-setup`, and — added
2026-08-11 to close the coverage gap inside the sdk-init window — `post-init` wrapping
`bootstrapper.postInit()`); `modules-init` + `post-init` + `post-services-setup` now tile the
sdk-init span, while the two construction sections fall before its start timestamp.
**2026-08-11 — integration coverage:** existence of the in-window `<section>-duration-ms` attrs
is asserted on BOTH spans — TracingApiTest (first test) for the `emb-sdk-init` private span, and
AppStartupTraceTest (`startup spans recorded in foreground session…`) for the `emb-embrace-init`
cold-trace child span — via a single shared helper
`List<Attribute>?.assertSdkInitSectionDurationsRecorded()` in
`embrace-android-otel-fakes/.../assertions/SpanAssertions.kt`, whose private
`expectedSdkInitSections` list is the one place the hardcoded sections live. Existence-only by
Hanson's direction (no sum/≤-total checks: durations use live `SystemClock.elapsedRealtime` while
span timestamps use the harness FakeClock, so values aren't comparable; they're all "0" under
Robolectric). Hanson then added 3 finer sections (`span-service-init`, `otel-sdk-wrapper-init`,
`load-instrumentation`; he also moved the `post-init` wrap inside `SdkInitActions.postInit()`),
so the asserted in-window set is now 6. Construction sections (`embrace-impl-init`,
`bootstrapper-init`) never run in the harness — it constructs `EmbraceImpl(bootstrapper = …)`
directly, bypassing the `Embrace` singleton class-load and the traced ctor default arg — so
production yields 8 sections, integration tests assert 6; verify the extra 2 on-device. the ~30 fine-grained opt-ins (bootstrap phases, per-module
sections, crash-ndk/thermal/power registrations) were reverted to plain
`EmbTrace.trace("name") { ... }` and will be re-added/refined AFTER review. Gotcha: function-
reference call sites (`init-module`, `process-identifier-init`) can no longer pass the provider
positionally (the Boolean occupies slot 2) — they use `EmbTrace.trace("name", code = ref)`;
`bootstrapper-init` (opted in) names all params. Session/ANR/export/`startup-tracking` sites
were never opted in.
**Goal:** Break down Embrace SDK start time by adding per-section duration attributes (derived from
the sections `EmbTrace` already wraps for systrace) to the startup-related spans, with zero ongoing
runtime cost once SDK startup completes.

---

## Progress

- [x] **Step 1** — Extend `EmbTrace` to accumulate per-section durations, + `EmbTraceTest` — **DONE**
- [x] **Step 3** — `StartupService` interface + impl, + `StartupServiceImplTest` — **DONE**
- [x] **Step 4** — `AppStartupTraceEmitter`, + `AppStartupTraceEmitterTest` — **DONE**
- [x] **Step 2** — Flush + hand-off at SDK-init completion (`EmbraceImpl`, `SdkInitActions`) — **DONE**
- [x] **Step 5** — `FakeStartupService` — **DONE** (backing var named `sdkInitDurationsImpl` to avoid
  a JVM signature clash between the property getter and the interface's `getSdkInitDurations()`)
- [x] **Step 6** — Integration-test hygiene (`SdkIntegrationTestRule`) — **DONE**

(Ordering above follows the planned **Sequencing** — see that section below. Step 7's test work is
split across Steps 1/3/4 in this checklist rather than listed separately, since each test class is
delivered alongside its production step.)

**2026-08-11 — async sdk-init span recording:** `setSdkStartupInfo` is now a pure setter; the
span is recorded by a new `StartupService.recordSdkInitSpan()` (records once via AtomicBoolean
CAS, no-ops until info is set), submitted in `markSdkInitComplete` on
`Worker.Background.NonIoRegWorker` so span creation happens off the init thread. Test
determinism came free: the integration rule's existing `awaitAsyncInstrumentation()` drains
NonIoRegWorker right after start (real-worker case), and tests that fake that worker with
`blockingMode = false` (e.g. AppStartupTraceTest) execute the submit inline. StartupServiceImplTest
updated for the split (span deferred until record; record-before-set no-op; record-twice guard);
FakeStartupService tracks `sdkInitSpanRecordedCount`. Full sdk suite + rerun of the two
span-asserting integration classes pass.

**2026-08-11 — on-device verification assets:** `StartupBenchmarks` macrobenchmark +
`BaselineProfileGenerator` live in `examples/ExampleApp/app/benchmark` (ExampleApp repointed to
9.2.0-SNAPSHOT via mavenLocal); results artifact (same URL across reruns):
https://claude.ai/code/artifact/905f206b-efe8-4932-8cd9-cfde208fe558 — final state is a baseline-
profile A/B (20+20 cold starts, Galaxy A14): NO measurable difference, caveat that the profile was
generated from the minified build (AGP 9.3.1 is incompatible with the androidx.baselineprofile
plugin, so the manual `baseline-prof.txt` flow was used). Span-vs-section cross-check held at
Δ≤1ms across all passes. Duration tracking later swapped `otel-sdk-wrapper-init` (ctor-only,
~0.07ms) → `otel-tracer-init` (real OTel SDK assembly cost).

## Context

We want to break down Embrace SDK start time by adding per-section duration attributes to the
startup-related spans. Today `EmbTrace`
(`embrace-android-utils/src/main/kotlin/io/embrace/android/embracesdk/internal/utils/EmbTrace.kt`)
is a stateless inline shim over `android.os.Trace` that already wraps every meaningful SDK-init
section ("modules-init", "config-service-init", "post-services-setup", etc.), but the elapsed times
are only visible in a systrace capture. This change makes `EmbTrace` accumulate total elapsed time
per section internally (independent of systrace being enabled), flush that map once at SDK-startup
completion, and attach the durations as string attributes to **both** the private `sdk-init` span
(recorded by `StartupServiceImpl`) and the `embrace-init` child span of the app-startup trace
(recorded by `AppStartupTraceEmitter`).

**Decisions made with the user:**
1. Attributes go on **both** spans (`sdk-init` and `embrace-init`).
2. Recording runs from class-load until the SDK reads it at startup-complete, then is permanently
   disabled and cleared — zero ongoing cost for lifetime hot-path callers (session caching, ANR,
   thermal, power-save).

## Step 1 — Extend `EmbTrace`

**File:** `embrace-android-utils/src/main/kotlin/io/embrace/android/embracesdk/internal/utils/EmbTrace.kt`

Constraints:
- `trace` is a public `inline fun` → it can only reference public members (no
  `@PublishedApi internal` — used nowhere else in the SDK).
- State does NOT live in the `object`: it lives in a new `class SectionDurationTracker`
  (`embrace-android-utils/.../internal/utils/SectionDurationTracker.kt` —
  `record`/`flush`/`reset`, private `@Volatile` gate + `ConcurrentHashMap`). No locks:
  `record` is `putIfAbsent` — the FIRST duration recorded for a section name wins, repeats are
  discarded (revised from synchronized-and-summing at Hanson's direction). Since `trace` records
  at section END, the first-to-finish occurrence of a name wins. `object EmbTrace` is a stateless
  facade holding one public `val durationTracker` instance — held there only because `trace` runs
  from static/constructor contexts before any DI graph exists. Consumers call
  `EmbTrace.durationTracker.flush()` / `.reset()`; the tracker class is unit-tested in isolation
  (`SectionDurationTrackerTest`).
- minSdk 21, no desugaring → no `ConcurrentHashMap.merge`. Use a plain `HashMap` + `synchronized`
  inside a non-inline record function (init-path contention is negligible).
- Duration recording is independent of `Trace.isEnabled()`/API level — `recordDuration = true`
  records on ALL Android versions whether or not a systrace capture is running (briefly gated on
  an active capture at Hanson's direction, then reversed same day so production spans always get
  the breakdown). Keys are the **raw** section names (no `emb-` prefix, no truncation). Call
  sites that opt in name both parameters:
  `EmbTrace.trace(sectionName = "x", recordDuration = true) { ... }`.
- Accumulate ms directly from `SystemClock.elapsedRealtime()` — the SDK's standard interval clock
  (see `NormalizedIntervalClock`); sub-ms sections read as 0, consistent with the ms-granularity
  attributes we export. (Originally implemented with `System.nanoTime()` + flush-time conversion;
  revised same day at Hanson's direction.) Repeated same-name sections sum. Nested sections each
  record their full elapsed time (parents include children — same semantics as systrace).

Shape:

```kotlin
@PublishedApi
@Volatile
internal var durationRecordingEnabled: Boolean = true

private val recordedDurationsNanos = HashMap<String, Long>()

inline fun <T> trace(sectionName: String, code: Provider<T>): T {
    // existing systrace begin logic unchanged
    val shouldRecordDuration = durationRecordingEnabled
    val startNanos = if (shouldRecordDuration) System.nanoTime() else 0L
    try {
        return code()
    } finally {
        if (shouldRecordDuration) {
            recordDuration(sectionName, System.nanoTime() - startNanos)
        }
        // existing Trace.endSection() logic unchanged
    }
}

@PublishedApi
internal fun recordDuration(sectionName: String, durationNanos: Long) {
    if (!durationRecordingEnabled) return  // (braced multi-line form in real code)
    synchronized(recordedDurationsNanos) {
        recordedDurationsNanos[sectionName] = (recordedDurationsNanos[sectionName] ?: 0L) + durationNanos
    }
}

/** Snapshot ms durations keyed by raw section name; permanently disables recording and clears state. */
fun flushRecordedDurations(): Map<String, Long>

/** Re-enables recording with cleared state (for tests; Robolectric shares static state per classloader). */
fun resetDurationRecording()
```

Per user style: companion-object-less `object`; put the private map and `recordDuration` below the
public functions; `TimeUnit.NANOSECONDS.toMillis` at flush.

**Status: DONE** (this session) — production changes + `EmbTraceTest` extensions/additions per
Step 7 below are implemented.

## Step 2 — Flush + hand-off at SDK-init completion

**Files:** `embrace-android-sdk/src/main/kotlin/io/embrace/android/embracesdk/EmbraceImpl.kt`,
`.../internal/injection/SdkInitActions.kt`

2a. In `EmbraceImpl.start()` (line 109–127): move `bootstrapper.markSdkInitComplete()` (line 126)
**out of** the `EmbTrace.trace("post-services-setup") { ... }` block to immediately after it (still
inside the `try`). Otherwise the still-open `post-services-setup` section — which covers listener
registration + instrumentation loading — would be missing from the flushed map.
(`sdkCallChecker.started` is already set inside the block; behavior otherwise unchanged.)

2b. In `markSdkInitComplete()` (`SdkInitActions.kt:188`): flush first, then pass through:

```kotlin
internal fun ModuleGraph.markSdkInitComplete() {
    val sdkInitDurations = EmbTrace.flushRecordedDurations()
    EmbTrace.trace("startup-tracking") {
        dataCaptureServiceModule.startupService.setSdkStartupInfo(
            sdkStartTimeMs,
            initModule.clock.now(),
            essentialServiceModule.appStateTracker.getAppState(),
            Thread.currentThread().name,
            sdkInitDurations,
        )
    }
    // existing log lines unchanged
}
```

The `startup-tracking` systrace section still emits; its duration just isn't recorded (measurement
of the measurement). The single flush solves the two-consumer problem: `StartupServiceImpl` stores
the map and records `sdk-init` immediately; `AppStartupTraceEmitter` pulls it later via
`startupServiceProvider()` exactly like it already pulls `getSdkInitStartMs()`/`getSdkInitEndMs()`.

**Status: not started.**

## Step 3 — `StartupService` interface + impl

**Files:**
`embrace-android-instrumentation-startup-trace/src/main/kotlin/.../startup/StartupService.kt`,
`StartupServiceImpl.kt`

- Interface: add `sdkInitDurations: Map<String, Long> = emptyMap()` param to
  `setSdkStartupInfo(...)` (default keeps existing test call sites compiling) and a new getter
  `fun getSdkInitDurations(): Map<String, Long>` (empty until set).
- Impl: store in `@Volatile private var sdkInitDurations: Map<String, Long> = emptyMap()` (set
  alongside the other fields, matching existing repeat-call semantics), and merge into the
  `sdk-init` span attributes (`StartupServiceImpl.kt:38`):

```kotlin
attributes = mapOf(
    "ended-in-foreground" to foregroundEnd.toString(),
    "thread-name" to threadName,
) + sdkInitDurations.toDurationAttributes(),
```

- Attribute key scheme — shared `internal` helper in this module (top-level in
  `StartupService.kt`):

```kotlin
internal fun Map<String, Long>.toDurationAttributes(): Map<String, String> =
    entries.associate { "${it.key}-duration-ms" to it.value.toString() }
```

Kebab-case matches the existing plain keys (`thread-name`); section names are already kebab-case,
so keys read e.g. `modules-init-duration-ms`, `post-services-setup-duration-ms`. Values are ms as
decimal strings.

Prod callers of `setSdkStartupInfo`: only `SdkInitActions.markSdkInitComplete`. Test callers
covered in Steps 5/7.

**Status: not started.**

## Step 4 — `AppStartupTraceEmitter`

**File:**
`embrace-android-instrumentation-startup-trace/src/main/kotlin/.../startup/AppStartupTraceEmitter.kt`

- `recordStartup()` (~line 278): add
  `sdkInitDurations = if (recordColdStart) startupService.getSdkInitDurations() else emptyMap(),`
  to the `recordTrace(...)` call.
- Add `sdkInitDurations: Map<String, Long>` param to `recordTrace(...)` (~line 323).
- `EMBRACE_INIT_SPAN` block (~line 353): add `attributes = sdkInitDurations.toDurationAttributes(),`
  to the `recordCompletedSpan` call (it already accepts `attributes: Map<String, String>`).

**Status: not started.**

## Step 5 — `FakeStartupService`

**File:** `embrace-android-sdk/src/test/kotlin/io/embrace/android/embracesdk/fakes/FakeStartupService.kt`

Add the new param to the `setSdkStartupInfo` override, store in
`var sdkInitDurations: Map<String, Long> = emptyMap()`, implement
`getSdkInitDurations() = sdkInitDurations`.

**Status: not started.**

## Step 6 — Integration-test hygiene

**File:**
`embrace-android-sdk/src/integrationTest/kotlin/io/embrace/android/embracesdk/testframework/SdkIntegrationTestRule.kt`

Robolectric shares static state within a classloader and every integration test starts the SDK
(which now flushes and disables recording). Add `EmbTrace.resetDurationRecording()` in `before()`
(line 197). No existing integration assertion touches `sdk-init` attributes (`TracingApiTest` only
filters by span name), so nothing else changes.

**Status: not started.**

## Step 7 — Tests

Extend existing tests; new tests only for the new flush workflow.

**`EmbTraceTest.kt`** (`embrace-android-utils`) — **DONE** (delivered with Step 1):
- setup/teardown: `EmbTrace.resetDurationRecording()`.
- Extended existing tests with duration assertions: prefix test → flushed key is the **raw** name;
  tracing-disabled test → duration IS still recorded; truncation test → key is the full untruncated
  name; API-P test → duration recorded below Q.
- New tests (new workflow): accumulation (`recordDuration("x", 2_000_000)` twice → flush returns
  `mapOf("x" to 4L)` — deterministic, no clock faking); flush disables + clears (post-flush
  `trace {}` records nothing, second flush empty); `resetDurationRecording` re-enables.

**`StartupServiceImplTest.kt`** — not started: extend the span-recording test — pass
`sdkInitDurations = mapOf("modules-init" to 100L)`, assert
`attributes["modules-init-duration-ms"] == "100"` alongside existing attribute assertions; extend
the getter test with `getSdkInitDurations()`.

**`AppStartupTraceEmitterTest.kt`** — not started: in `initApp` (~line 768) pass
`sdkInitDurations = mapOf("modules-init" to 30L)` to `setSdkStartupInfo` (real
`StartupServiceImpl` used → flows end-to-end); in the cold-start `verifyTrace` branch assert
`embraceInitSpan()?.attributes?.get("modules-init-duration-ms") == "30"`.

## Sequencing

1. Step 1 + `EmbTraceTest`. **← DONE**
2. Step 3 + `StartupServiceImplTest`.
3. Step 4 + `AppStartupTraceEmitterTest`.
4. Steps 2 & 5 (wiring + fake).
5. Step 6.

## Verification

```
./gradlew :embrace-android-utils:testReleaseUnitTest :embrace-android-instrumentation-startup-trace:testReleaseUnitTest
./gradlew :embrace-android-sdk:testReleaseUnitTest    # integration tests live here — confirms no static-state bleed across tests
./gradlew detekt                                       # repo-root, pre-commit — trailing commas on multiline lists, braced multi-line if/else, camelCase private vals, no unused imports
```

## Notes / risks

- If SDK start aborts before `markSdkInitComplete` (e.g. config-disabled), recording stays enabled
  for the process lifetime — bounded by ~20 distinct section names and one nanoTime + synchronized
  write per call; acceptable, no mitigation needed.
- The inline `trace` grows by one volatile read + branch at every call site on the disabled path —
  negligible.
