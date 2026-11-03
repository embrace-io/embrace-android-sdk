---
name: find-sdk-signal
description: Use when asked what the Android SDK actually sends for a signal (span name, `emb.type`, attributes, value ranges, snapshot or not, first SDK version, API or config gates), before docs, dashboard or other work that depends on it. Includes a gate 1 check: run the signal's integration test and report pass or fail.
---

# Find exactly what the SDK emits for a signal

A read-only lookup: what the SDK sends for X, answered from the code, for any consumer (docs, dashboards, support).

> Lines are from `origin/main` `1a9b23546` (2026-10-30). Module prefixes `embrace-android-` are shortened.

## Read-only

This is a lookup, not a change. When the question comes from work outside the SDK, the SDK is a fixed input:
if a cleaner attribute would help, note it for the user rather than planning an SDK change.

## The answer to produce

| Field | Where to find it |
|---|---|
| `emb.type` | `instrumentation-schema/.../schema/EmbType.kt` is the full list (24 types + `Custom`). `SchemaType.kt` is **not** complete: `ui_load` and `thread_blockage` are missing |
| Span name and attributes | The `SchemaType` entry, else the data source or emitter. SDK span names get an `emb-` prefix (`internal` defaults to true, `SpanService.kt:43`) |
| Shape | Root span, child span, session-part event, session-span attribute, or private span (below) |
| Snapshot? | Whether the current state stays open and is sent as a snapshot on the session payload |
| Values | Value types and ranges as the code enforces them, not as examples suggest |
| Gates | API level (`@RequiresApi`, the instrumentation provider), remote config flag and its default, caps |
| First SDK version | `git log --diff-filter=A --format=%h -- <data source file>`, then `git tag --contains <sha> \| sort -V \| head -1` |

## Shapes

- **Public root span:** `TelemetryDestination.startSpanCapture` with no parent starts on `context.root()`, private
  false (`instrumentation-api/.../datasource/TelemetryDestination.kt:43-52`).
- **Child span:** has a parent. Children usually keep the default `emb.type=perf`, not the root's type, so report a
  child's type separately from its root's.
- **Session-part event:** an event on the session span, carrying `emb.type` (`core/.../destination/TelemetryDestinationImpl.kt:182-194`).
  Caps: 100 custom breadcrumbs, 1000 others (`core/.../spans/CurrentSessionPartSpanImpl.kt:118-133`), both remote-configurable.
- **Session-span attribute:** a key on the session span itself, not an event on it. Keys are listed in
  `semconv/src/main/semconv/session.yaml`, constants in `semconv/.../EmbSessionAttributes.kt`. Most describe one part,
  e.g. `emb.session_end_type` (`EmbSessionAttributes.kt:89`). A few describe the whole user session and appear only on
  its last part, the one that carries `emb.is_final_session_part` (`EmbSessionAttributes.kt:65`). Several keys have
  near-identical names, so copy the key from the catalog before reporting it.
- **Private:** `emb.private=true` (`otel/.../spans/OtelSpanStartArgs.kt:35-36`). Never served to the dashboard.
- **State span:** `emb.type=state`, with `transition` events carrying `emb.state.new_value`; the event itself has no
  `emb.type` (`TelemetryDestinationImpl.kt:261-283`, `StateDataSource.kt:145-171`).

## Gate 1 check: run the signal's integration test

Once the answer is found, check it against a running SDK. Everything runs locally on the JVM, with no device.

1. Find the test: `grep -rl <DataSourceClass> embrace-android-sdk/src/integrationTest/kotlin`. These tests boot the
   full SDK with `SdkIntegrationTestRule`, which sends each upload to a local fake API server
   (`testframework/SdkIntegrationTestRule.kt:56-58`). If there is none, report that. Gate 1 cannot pass without one.
   A session-span attribute has no data source class. Grep its key and its constant across
   `embrace-android-sdk/src/integrationTest`, both the Kotlin and the JSON goldens in `resources/` (a golden's file
   name leads to its test class), then pick a test whose assertion or golden sets the key. A match is not proof: a
   test can upload the session span while its expected attributes leave the key out, and a test can assert the key is
   absent.
2. Start a local server that stores what it receives, then run that one class with forwarding on:

   ```sh
   EMBRACE_IT_FORWARD_DATA_URL=<server base URL> ./gradlew :embrace-android-sdk:testDebugUnitTest \
     --tests 'io.embrace.android.embracesdk.testcases.features.ThermalStateFeatureTest' --rerun --no-build-cache -i
   ```

   The test framework's `DataUploadForwarder` re-posts each upload, unchanged and still gzipped, to
   `<base>/v2/spans` or `<base>/v2/logs`, and fails the test if the server doesn't answer 2xx. `-i` shows one
   `EMBRACE_IT_FORWARD status=...` line per upload. `--rerun --no-build-cache` because the variable isn't a Gradle
   input. `--tests` takes the class name, which can differ from the file name: `ThermalStateTest.kt` holds
   `ThermalStateFeatureTest`. This is a lookup check. It does not replace `./gradlew verify --continue` for a code
   change.
3. Save the upload from the server. Without forwarding, only golden-file tests show it (`Dump of full JSON` on a
   mismatch, `testframework/actions/EmbracePayloadAssertionInterface.kt:349-354`). Tests that assert fields directly,
   like `ThermalStateFeatureTest`, print nothing.
4. Report pass or fail, the command, and the path of the saved upload.

## Worked example: thermal state

`instrumentation-thermal-state/.../ThermalStateDataSource.kt:79-94`: each change ends the previous span and opens
`emb-thermal-state`, `emb.type=perf.thermal_state`, `status` = the Android code as a string. Only `null` is
dropped, so `0` (none) is sent too. The open state is a snapshot (`ThermalStateTest.kt:100-106`). 100-state cap.
API gate in `ThermalStateInstrumentationProvider.kt:14`. Remote config `pct_thermal_status_enabled`, default on.
First span form: 6.8.0 (commit `d2b20938d`). The `0`–`6` range is Android's `PowerManager`, not an SDK check.

## Traps

| Trap | Why |
|---|---|
| `grep thermal` finds `startup-trace/.../SdkInitAttributeKeys.kt` (`thermal-status`, CPU %, memory) first | Performance-anomaly context on the private `sdk-init` span and cold-start `embrace-init` (`:20-27`). Not the customer signal |
| Information only in the span name (e.g. ui_load's `emb-<Activity>-<cold\|hot>-time-to-initial-display`, `traceName` in `UiLoadTraceEmitter.kt:314-317`) | A consumer has to parse the name to get it. Report it, don't change the SDK |
| `git log -S <Name>` reaching `first commit: 6.0.0` | A legacy non-OTel form existed earlier. Report the first version of the span form |

## Before done

The gate 1 check runs one test class and changes nothing. Any code change here must pass `./gradlew verify --continue` (`AGENTS.md:15-24`).
