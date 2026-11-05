---
name: find-sdk-signal
description: Use when asked what the Android SDK actually sends for a signal (span name, `emb.type`, attributes, value ranges, snapshot or not, first SDK version, API or config gates), before docs, dashboard or other work that depends on it. Includes a gate 1 check: run the signal's integration test and report pass or fail.
---

# Find exactly what the SDK emits for a signal

A read-only lookup, answered from the code. The SDK is a fixed input: if a cleaner attribute would help, say so,
don't plan an SDK change.

> Lines are from `origin/main` `1a9b23546` (2026-10-30). Module prefixes `embrace-android-` are shortened.

## 1. Settle the shape first

The shape decides everything downstream (which go code, which gates), so report it before anything else.

| Shape | How to tell | On the timeline gate chain? |
|---|---|---|
| Public root span | `TelemetryDestination.startSpanCapture` with no parent (`instrumentation-api/.../TelemetryDestination.kt:43-52`) | Yes |
| Child span | Has a parent. Usually keeps the default `emb.type=perf`, not the root's type: report it separately | Yes, under its root |
| Session-part event | An event on the session span, carrying `emb.type` (`core/.../TelemetryDestinationImpl.kt:182-194`). Caps: 100 custom breadcrumbs, 1000 others | Yes |
| Session-span attribute | A key on the session span itself. Catalog: `semconv/src/main/semconv/session.yaml`, constants in `semconv/.../EmbSessionAttributes.kt` | Yes |
| State span | `emb.type=state`, `transition` events carrying `emb.state.new_value` (`TelemetryDestinationImpl.kt:261-283`) | Yes |
| Private span | `emb.private=true` (`otel/.../OtelSpanStartArgs.kt:35-36`) | No: never served. Stop and say so |
| Log | Sent to `/v2/logs`, not `/v2/spans` | No: not on the span gate chain. Stop and say so |

**GOOD:** `SHAPE: session-span attribute emb.user_session_termination_reason (EmbSessionAttributes.kt:260), set only on the part with emb.is_final_session_part`
**BAD:** `SHAPE: span`. A session-span attribute is never its own span row: go reads it as a field in the *existing*
session mapper, so the downstream path is "add a field to the session mapper", not "new mapper".

## 2. Copy the key from the catalog, never from a near match

Several session keys have near-identical names. Grep the catalog for the meaning, then copy the exact key.

**GOOD:** "why the user session ended" → `emb.user_session_termination_reason` (`EmbSessionAttributes.kt:260`):
whole-user-session, on the final part only.
**BAD:** "why the user session ended" → `emb.session_end_type` (`:89`): that is per part. Round 2's discover run
took it and planned the wrong go field (`EndMethod`).

**GOOD:** "thermal state" → `ThermalStateDataSource.kt:79-94`, span `emb-thermal-state`, `emb.type=perf.thermal_state`.
**BAD:** "thermal state" → `instrumentation-startup-trace/.../SdkInitAttributeKeys.kt:134` (`thermal-status`): context on the
private `sdk-init` span, not the customer signal. `grep thermal` finds it first.

## 3. The rest of the answer

| Field | Where |
|---|---|
| `emb.type` | `instrumentation-schema/.../EmbType.kt` (24 + `Custom`). `SchemaType.kt` is incomplete: no `ui_load`, `thread_blockage` |
| Span name, attributes | The `SchemaType` entry, else the data source. SDK spans get an `emb-` prefix (`SpanService.kt:43`) |
| Snapshot? | Whether the open state is sent as a snapshot on the session payload |
| Values | Types and ranges the code enforces, not what examples suggest |
| Gates | API level (`@RequiresApi`, the provider), remote config flag and default, caps |
| First SDK version | `git log --diff-filter=A --format=%h -- <data source>`, then `git tag --contains <sha> \| sort -V \| head -1`. Report the first version of the span form, not an older legacy form |

If the information lives only in the span name (ui_load's `emb-<Activity>-<cold|hot>-time-to-initial-display`,
`UiLoadTraceEmitter.kt:314-317`), report that a consumer must parse the name.

## 4. Gate 1: run the signal's integration test

Local JVM, no device. `SdkIntegrationTestRule` sends each upload to a fake API (`testframework/SdkIntegrationTestRule.kt:56-58`).

1. **Find a test whose upload carries the signal.** Data source: `grep -rl <DataSourceClass> embrace-android-sdk/src/integrationTest/kotlin`.
   Session-span attribute: grep the key and constant across `src/integrationTest`, including the JSON goldens in
   `resources/`; a golden's file name leads to its test class. A match is not proof: check the expected attributes
   include the key and the test doesn't assert it absent. No test: report it, gate 1 can't pass.

   **GOOD:** termination reason → `user_session_manual_end_1.json` → `UserSessionManualEndGoldenFileTest` (its golden sets the key)
   **BAD:** termination reason → `SessionPartIdentityTest` because grep matched it: `:103` asserts the key is *absent*

2. **Run that one class with forwarding on** to a local server that stores what it receives:

   ```sh
   EMBRACE_IT_FORWARD_DATA_URL=<server base URL> ./gradlew :embrace-android-sdk:testDebugUnitTest \
     --tests '<fully.qualified.TestClass>' --rerun --no-build-cache -i
   ```

   The forwarder re-posts each upload unchanged to `<base>/v2/spans` or `/v2/logs` and fails on non-2xx; `-i` prints
   one `EMBRACE_IT_FORWARD status=...` line per upload. `--tests` takes the class name, which can differ from the
   file: `ThermalStateTest.kt` holds `ThermalStateFeatureTest`.
3. **Save the upload** from the server: it is gate 3's fixture. Without forwarding, only golden tests show it.
4. **Report** pass or fail, the command, and the saved upload's path.

## Before done

Gate 1 runs one class and changes nothing. Any SDK code change must pass `./gradlew verify --continue` (`AGENTS.md:15-24`).
