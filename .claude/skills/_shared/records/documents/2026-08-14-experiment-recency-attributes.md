# Accuracy check: `seconds-since-install`, `seconds-since-update`, `seconds-since-boot`

Scope (owner decision): these three attributes need only be verified as **ACCURATE** — do they
report correct elapsed seconds against independent ground truth. Their diagnostic value (does a low
value actually predict concurrent dexopt/migration/post-boot contention) is not bench-testable; they
corroborate a CPU/memory reading we already see, and that link can only be validated from production
data. No install/update state matrix, no reboot campaign, no arms/grading protocol — just the
arithmetic check below, runnable in a few minutes and able to piggyback on any future launch/profiling
pass.

## 1. Clock finding for `seconds-since-boot` — read from source, not spec

- **What it was**: `SdkInitResourceUsageTracker.kt` computed `SECONDS_SINCE_BOOT` from
  `SystemClock.elapsedRealtime()` captured at `captureStart()` — wall/sleep-INCLUSIVE time since boot.
- **What it is now** (code changed since the first pass of this doc; re-read 2026-08-14): the same
  file now also captures `uptimeMs = SystemClock.uptimeMillis()` at `captureStart()`
  (`SdkInitResourceUsageTracker.kt:28,88`), and `buildAttributes()` emits `SECONDS_SINCE_BOOT` from
  `startUptimeMs`, not `startWallMs` (`SdkInitResourceUsageTracker.kt:132-136`). `elapsedRealtimeMs`
  is retained only for the unrelated scheduling-percentage math (`putSchedulingAttributes`).
  `SdkInitAttributeKeys.kt`'s kdoc for `SECONDS_SINCE_BOOT` (`SdkInitAttributeKeys.kt:56-69`) now
  states the awake-time semantics explicitly.
- **Why uptime is the right clock** (the reasoning is now in both files' comments, restated here
  because it's the whole point of the attribute): it exists to corroborate "the device probably
  hasn't finished post-boot work yet" — a proxy for how much RUNNING time the device has had to do
  that work, not for how long ago the boot event happened. A phone that booted 8 hours ago but slept
  7 of them has had 1 hour to settle; elapsed-realtime would misreport it as long-settled, uptime
  correctly reports ~1 hour.
- **Consequence for ground truth**: `/proc/uptime` field 1 is awake-time-since-boot on
  Android/Linux (it excludes suspend) — it is now a DIRECT match for the attribute's clock, with no
  divergence question to chase. (An RTC-based wall-clock delta — which the earlier version of this
  doc needed as a comparator, back when the attribute was elapsed-realtime-based — would now be the
  WRONG ground truth: it would disagree with the attribute on any device that has slept since boot,
  and that disagreement would be the check being wrong, not the attribute.)

## 2. Runnable arithmetic check

One ordinary launch per attribute is enough: read the attribute via the verification tap, read
ground truth via `adb` at roughly the same moment, compare within tolerance.

### Enable / read the tap
```
adb shell settings put global embrace_verify_telemetry startup
# launch the app normally, then:
adb logcat -d -s EmbVerify   # EMBV1-prefixed chunked JSON; wait for the flush marker
adb shell settings delete global embrace_verify_telemetry
```

### Ground truth per attribute

**`seconds-since-install` / `seconds-since-update`** — `dumpsys package <pkg>` timestamp fields:
```
adb shell dumpsys package io.embrace.android.exampleapp | grep -e firstInstallTime -e lastUpdateTime
```

Load-bearing parsing details, probed on all 4 attached devices (API 29/31/35, 2026-08-14) — keep
these, they are why a naive parser would silently break on half the fleet:
- Both fields print as a **human-readable local-timezone datetime string**, `YYYY-MM-DD HH:MM:SS`,
  whole-second resolution — not epoch millis. Convert with the device's own clock, e.g. `adb shell
  date -d "<string>" +%s` (confirmed present as a toybox flag on all 4 API levels tested).
- **Field position differs by API level — parse by field name, never by position.** API 29 and 31
  (Galaxy A01 Core, Pixel 3 — probed) print both fields top-level, `firstInstallTime` BEFORE
  `lastUpdateTime`. API 35 (Pixel 7 Pro, Galaxy A14 — probed) prints `lastUpdateTime` top-level but
  nests `firstInstallTime` one indent deeper under a per-user `User 0:` block, printed AFTER
  `lastUpdateTime`. A fixed-line-offset scrape is correct on one generation and silently wrong on the
  other; a field-name-anchored regex (`firstInstallTime=(.+)`, `lastUpdateTime=(.+)`) is correct on
  both.

**`seconds-since-boot`** — `/proc/uptime` field 1, now a direct match for the attribute's clock
(section 1):
```
adb shell cat /proc/uptime
```
`ro.boottime.*` properties were probed and confirmed **absent on all 4 attached devices** (API 29
through 35, two vendors) — not a usable fallback in this fleet. `/proc/uptime` is the only read-only
boot ground truth available and it parsed cleanly on every device tested, including the oldest
(API 29).

### Comparison and tolerance

`expected = floor(ground_truth_seconds_at_read_time)`, compared against the tap-reported attribute
value from the same launch.

**Tolerance: `|attribute − expected| <= 1 + measured_sampling_gap_seconds`.** Two independent,
legitimate sources, neither indicating a bug:
1. **±1s quantization** — the attribute floors to whole seconds, and `dumpsys package`'s string is
   already whole-second; two independent truncations of the same instant can land in adjacent
   buckets even with zero real elapsed time between reads.
2. **The sampling gap** — the attribute is captured at SDK-init time; ground truth is read by a
   separate `adb shell` round-trip afterward. Measure this per check (timestamp the tap-flush read
   vs. the ground-truth read) rather than assuming it — a fast scripted read should be ~1-3s, a
   manual one longer, and reporting the gap is what separates "the attribute is off" from "the check
   was slow."

Pass/fail arithmetic, not a statistical bar: a handful of ordinary launches is enough, no dedicated
campaign needed.

## 3. Footnote — can `install -r` even make the two attributes diverge?

Not tested (installing was out of scope for this pass). Worth flagging: every prior observation used
a fresh install before measuring, so `firstInstallTime == lastUpdateTime` always, and the two
attributes have never been seen to differ. Two commands would settle whether they even CAN, under
the install method actually used:
```
adb install -r <existing-installed-apk>       # or a rebuilt APK with bumped versionCode
adb shell dumpsys package io.embrace.android.exampleapp | grep -e firstInstallTime -e lastUpdateTime
```
If `lastUpdateTime` moves while `firstInstallTime` stays fixed, the two are independently
distinguishable and each is checkable with the section-2 method. If `install -r` over an identical
APK turns out to be a no-op that moves neither timestamp — **flagged as unverified, watch for it** —
that's worth knowing before relying on this pair in production: a query segmenting telemetry on
"post-update launch" (small `seconds-since-update`) would silently match nothing, which reads as "no
post-update slowness exists" rather than as a filter that never fires.

**Caveat** (production realism, compressed): production updates mostly arrive via Play's installer,
not `adb install -r`, and app restore/migration to a new device can reset `firstInstallTime` —
both are assumed, not verified, to behave like the `adb install -r` path referenced above.
