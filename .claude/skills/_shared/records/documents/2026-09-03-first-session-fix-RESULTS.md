# start-first-session fix — verification (2026-09-03, Pixel 3 `mid-b`, Android 12)

## What was fixed (uncommitted on `hho/startup-tools-kotlin`)

The user-session metadata write inside `start-first-session` called the reified
`PlatformSerializer.toJson(value)` from core, where the kotlinx serialization compiler plugin is not
applied, so the serializer was resolved at runtime (`serializer(typeOf<Map<String,String>>())`). The
first such lookup in a process loads kotlinx's builtin serializer table (~55 classes) and runs
reflection, on the main thread. The reified helpers are deleted; every call site passes a static
strategy; detekt forbids `kotlinx.serialization.serializer` imports in production code; the trace
health pass flags >= 10 class loads inside `emb-start-first-session` as the regression signature.

## Why the lab never saw it

The SDK restores a persisted user session within its 30-minute inactivity timeout and only creates
and persists a new one otherwise. Every benchmark relaunch restores (write skipped, ~0 ms); every
production cold start hours after the last one creates (write taken). Ground truth from the
ExampleApp's `EmbVerify` logcat tap on 9.2.0, uncompiled fresh install: fresh launch 5 ms, plain
relaunch 0 ms with the same `emb.user_session_id`, launch after `pm clear` 3 ms with a new id.

## A/B 1 — compiled (baseline profile applied), `pm clear` every iteration, 50 launches each

Traces under `arm-a-9.2.0/pass1` and `arm-b-fix/pass1`; per-iteration tables in
`arm-a-analysis.txt` / `arm-b-analysis.txt` (script: scratchpad `ab_first_session.py`).

| arm | SDK | window median | post-init median | start-first-session median (p90) | class loads in section median (max) |
|---|---|---|---|---|---|
| A | 9.2.0 (mavenCentral) | 27.44 ms | 1.09 ms | 1.01 ms (1.06) | 24 (138) |
| B | HEAD + fix (9.3.0-SNAPSHOT, mavenLocal) | 27.96 ms | 1.06 ms | 0.96 ms (1.04) | 20 (21) |

With the app image present the reflective lookup was already cheap on this device; the visible
change is the iteration-0 burst (138 class loads) disappearing. Arm B traces contain NO load of
`ParametrizedCacheEntry`, `KTypeWrapper` or `PrimitivesKt` anywhere in startup; Arm A loads them on
the main thread at ~+55 ms (the foreground transition's write).

## A/B 2 — UNCOMPILED (plain `adb install`), `pm clear` before every launch, 10 launches each

Read from the `sdk-init` span attributes via the logcat tap (script: scratchpad `uncompiled_ab.py`).
Every launch created a new user session (ids differ, `version_startup_counter` = 1).

| SDK | init median | modules-init median | post-init median | start-first-session median (max) |
|---|---|---|---|---|
| 9.2.0 | 20 ms | 14 ms | 3 ms | 3 ms (6) |
| fix | 18 ms | 14 ms | 1 ms | 1 ms (2) |

Same device, same hour, `modules-init` identical: the fix removes ~2 ms (10% of init) on a Pixel 3
without its app image. Production mid-tier devices in verify state pay class verification on every
one of those ~55 loads, which is where the 15-19 ms p50 comes from.

## A/B 3 — the returning-user arm (the production shape), compiled, 50 launches each

`coldStartupBaselineProfileExpiredUserSession`: the ExampleApp deletes only the stored user session
before `Embrace.start` (config and all other state kept), so every launch takes the create path with
a persisted config present. Cohort verification (both toolchains) confirmed 49 created + 1 unknown
(the first launch, counter already past 1), 0 restored, 0 violations in both arms. Traces under
`arm-c-expired-hook/pass1` (9.2.0) and `arm-d-expired-hook-fix/pass1` (fix).

| arm | SDK | window median | post-init median | start-first-session median (p90) | class loads in section median (max) |
|---|---|---|---|---|---|
| C | 9.2.0 | 35.33 ms | 6.76 ms | 6.67 ms (7.12) | 119 (135) |
| D | fix | 30.40 ms | 2.57 ms | 2.50 ms (2.74) | 49 (50) |

This is the arm that reproduces the production signature: with the persisted config present the 9.2.0
create path loads the kotlinx serializer table inside `start-first-session` on EVERY launch (51 kotlinx
classes among the 119, deobfuscated), which the `pm clear` arm never did (its first-launch config path
shifted the first lookup to the later foreground transition). The fix removes 4.2 ms of a 35 ms init on
a compiled Pixel 3 (12%). The 50 loads that remain in the fixed build are the JSON encoder itself,
SDK classes and app classes; none are `kotlinx.serialization.internal`.

Consequence for the trace-health check: the burst threshold is 80 class loads (healthy create path
~50, regression ~120), identical in `_shared/trace_health.py` and `TraceHealth.kt`.

## Cohort verification

Both campaign runners arm the ExampleApp's logcat tap (`embrace_verify_telemetry=startup:1`), stream
`adb logcat -v threadtime -T 1 -s EmbVerify:I` to `passN-embverify.log`, and classify each launch's
`sdk-init` span (`_shared/cohorts.py` == `campaign/Cohorts.kt`; `passN-cohorts.json`; a `cohorts:`
line in `campaign.log`; WARN with iteration indices on violations). Verified identical output from
both implementations on a synthetic capture and on the real Arm C capture.

## Compile-state attribute (added after the fix, per Hanson)

`ArtOptimizationState` (final name; was `OdexCompileState`) reads the `compiler-filter` entry from the
oat header of the app's own `base.odex` (byte scan of the first 64 KB; page-cache hit, off the init
thread) and checks for `base.art`; exported as **`art-compile-filter`** and **`app-image-at-init`**
(final names, renamed 2026-09-04 from init-compile-filter / init-app-image) with the other environment
attributes. First verified on the Pixel 3 through the logcat tap:

| state | art-compile-filter | app-image-at-init | start-first-session |
|---|---|---|---|
| fresh `adb install` (+ `pm clear`) | `verify` | absent | 2 ms |
| after `cmd package compile -m speed-profile -f` | `speed-profile` | `true` | 1 ms |
| after `cmd package compile -m verify -f` | `verify` | absent | 1 ms |

### Four-device runtime verification (2026-09-04, committed stack, 9.3.0-SNAPSHOT via mavenLocal)

ExampleApp release APK; per device: fresh install → launch, `cmd package compile -m speed-profile -f` →
launch, `cmd package compile -m verify -f` → launch; attributes read from the `emb-sdk-init` span via the
EmbVerify tap, ground truth from `dumpsys package` Dexopt state. 12/12 launches agree with the package
manager (script: session scratchpad `verify_art_attrs.py`, raw spans in `verify-art-attrs/*.json`).

| device | API | ABI | fresh install | forced speed-profile | forced verify |
|---|---|---|---|---|---|
| Pixel 7 Pro | 35 | arm64-v8a | verify / absent | speed-profile / true | verify / absent |
| Pixel 3 | 31 | arm64-v8a | verify / absent | speed-profile / true | verify / absent |
| Galaxy A14 | 35 | arm64-v8a | verify / absent | speed-profile / true | verify / absent |
| Galaxy A01 | 29 | armeabi-v7a | **speed-profile / absent** | speed-profile / true | verify / absent |

The A01 exercises the `armeabi-v7a → arm` ISA directory for real. Its fresh-install row is a genuine
device state, not an attribute bug: Android 10 compiles the bundled baseline profile at install time
(`reason=install`, `status=speed-profile`) but writes no app image; only the explicit compile produced
`base.art`. This is exactly why the two attributes are recorded separately.

## Harness additions

* `coldStartupBaselineProfileNewUserSession` — `pm clear` per iteration (fresh-install cohort).
* `coldStartupBaselineProfileExpiredUserSession` — arms `settings put global
  embrace_bench_expire_user_session 1`; `BenchmarkStateHooks.expireUserSessionIfRequested` deletes
  only the `embrace.user_session` prefs key before `Embrace.start` (returning-user cohort, config
  intact). Disarm with `settings delete global embrace_bench_expire_user_session`.
* Verify the cohort from the span: `settings put global embrace_verify_telemetry startup:2`, then
  read `emb.user_session_id` / `emb.app.version_startup_counter` from `EmbVerify` logcat lines.

## Skill/doc changes

`startup-analysis/SKILL.md` (User-session state arms), `references/interpreting-results.md`
(user-session state decides post-init; a rare lab iteration is a hypothesis about production),
`references/sections.md` (full 9.2.0 tree, what start-first-session does); primer phase 5 rewritten
(restore vs create; the first span is created in span-service-init, not here).
