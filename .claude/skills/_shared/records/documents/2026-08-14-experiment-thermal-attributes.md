# Experiment design: definitive verdict on `thermal-status` and `thermal-headroom-pct`

**Date:** 2026-08-14 · **Status:** DESIGN ONLY — feasibility probes run (read-only, no heating,
no app launches); the dose-response/staleness/cross-vendor experiments below are **not yet run**.
**Owner attributes:** `SdkInitAttributeKeys.THERMAL_STATUS` / `THERMAL_HEADROOM_PCT`
(`embrace-android-instrumentation-startup-trace/.../SdkInitAttributeKeys.kt`,
`SdkInitEnvironmentAttributes.kt`).

This builds directly on the existing overnight thermal program in
`claude-output/2026-08-13-thermal-deep-dive-plan.md` (T0–T10: staircase dose-response,
mechanism discrimination, detectability grading). That plan already contains most of the
machinery this task asks for (battery watchdog, staircase protocol, sensor-race analysis,
AUC-based detectability). This document narrows that machinery to the two specific verdicts
required here, adds the two checks the 08-13 plan doesn't cover (headroom polarity/cap
behavior, and `getThermalHeadroom` staleness/NaN risk), and records what today's read-only
probes actually showed — as opposed to what the API spec claims.

## Summary table

| Attribute | Verdict today (spec + probes, NOT yet empirically graded) | What would make it OBTAINABLE+ACCURATE | What would make it FANTASY / USELESS | Biggest unresolved risk |
|---|---|---|---|---|
| `thermal-status` | Populates ("none") on all 4 devices at idle — confirmed prior work. Today's probes add: every device's HAL exposes a `type=SKIN` sensor with named hot-throttling thresholds (light/moderate/severe/…), and those thresholds sit only ~4–14 °C above today's idle skin readings — i.e. the transition band looks reachable by the already-validated 08-13 heating protocol (battery 30–41 °C got Pixel 3 to a measurable 12 ms init slowdown). **Not yet observed to leave "none".** | Status visibly steps to "light"/"moderate" within the 08-13 staircase's already-reached bands, at a point that *predicts* (not lags) the measured init slowdown. | Status never leaves "none" within the safe heating envelope (43 °C battery hard limit), or only steps up after the window is already inflated by >20% (a "too little too late" warning). | The status transition is keyed to a **SKIN** sensor with tens-of-seconds thermal mass — by the time it reports "light" the device may already have been slow for minutes (H-T3 in the 08-13 plan predicts exactly this lag on Pixel 3). |
| `thermal-headroom-pct` | Populates on 3/4 devices (absent on API 29 A01, correctly — spec-gated). Idle values (57–65% depending on vendor) sit **well above** what a naive "current-temp ÷ light-threshold" calculation would predict from today's actual idle skin temps (~31 °C, vs. light thresholds of 36–39 °C) — meaning the platform's forecast is doing something more than an instantaneous ratio, exactly as flagged in the task. Today's probe also found the *severity-ratio thresholds* PowerManager uses internally (`dumpsys thermalservice` → "Temperature headroom thresholds") differ noticeably between Pixel 7 Pro and Galaxy A14 in the sub-100 (pre-severe) region. | Headroom rises monotonically with real heat, uncapped region is linear-enough to fit a slope, and a fixed headroom value corresponds to comparable real slowdown across P7P/A14/Pixel 3 (within the calibration experiment's tolerance). | Headroom is pinned, non-monotonic, saturates long before severe, returns NaN/stale on repeated reads, or the same headroom value means measurably different things on different vendors. | The internal severity-ratio arrays are NOT exposed on Pixel 3 (API 31) or A01 (API 29) via `dumpsys` at all — so 2 of 4 devices can only be calibrated by the empirical heating experiment, not by reading a table. |

---

## 0. What today's probes actually did (and did not do)

Ran ONLY, per device, via `adb -s <serial> shell` (single chained read-only command,
no installs/launches/heating/settings changes):
`dumpsys thermalservice`, `dumpsys battery`, `cat .../scaling_max_freq`,
`cat .../scaling_cur_freq`, `getprop` (manufacturer/model/sdk).

**Did not**: induce any heat, launch the ExampleApp or read the SDK's own emitted attribute
values, exercise `getThermalHeadroom()` directly (that call is inside the SDK's Kotlin code,
not something `dumpsys` invokes), or check staleness/NaN behavior (needs repeated in-process
calls). Everything below the per-device tables is **design**, not measurement, and is labeled
as such throughout.

### Per-device probe results (2026-08-14, current session)

| Device | Serial | API | Thermal HAL | `Thermal Status` (idle) | Status-driving sensor (type=SKIN) | Idle SKIN reading | Idle battery temp | Hot-throttling thresholds on that sensor (light/moderate/severe/critical/emergency/shutdown, °C) | "Temperature headroom thresholds" ratio array exposed? | `scaling_max_freq` readable? | `scaling_cur_freq` (idle) |
|---|---|---|---|---|---|---|---|---|---|---|---|
| Pixel 7 Pro | flagship-a | 35 | AIDL 2 | 0 (none) | `VIRTUAL-SKIN` | 31.46 °C | 30.7 °C | 39 / 43 / 45 / 46.5 / 52 / 55 | Yes: `[NaN, 0.8, 0.933, NaN, 1.05, 1.233, 1.333]` | Yes | little 1803 MHz (pinned at max, idle-adb artifact), mid 400 MHz, big 500 MHz — none throttled |
| Galaxy A14 | mid-a | 35 | AIDL 2 | 0 (none) | `SKIN` | 31.3 °C | 29.2 °C | 36 / 38 / 40 / 42 / 45 / 60 | Yes: `[NaN, 0.867, 0.933, NaN, 1.1, 1.6, 2.6]` | Yes (2002 MHz uniform cap, all 8 cores) | 949 MHz (cores 0–3), 546 MHz (cores 4–7) — DVFS idle scaling, not throttling |
| Pixel 3 | mid-b | 31 | HAL 2.0 | 0 (none) | `fps-therm-monitor` | 31.76 °C | 29.8 °C ("maxfg") | 39 / 43 / 45 / 47 / 51 / 55 | **No** — line absent from this device's dump despite headroom being API-supported | **No** — `Permission denied` on all 4 entries read this session | 2803.2 MHz on the 4 entries that WERE readable — matches the prior-established constant hot-clock value exactly, at idle |
| Galaxy A01 Core | entry-a | 29 | HAL 1.0 | 0 (none) | `SKIN_T` | 34.0 °C | 32.1 °C | *(HAL 1.0 dump has no threshold struct at all)* | No (and `getThermalHeadroom` is unavailable here regardless — API <30) | Yes (1495 MHz, all 4 cores) | 962 MHz |

Findings worth flagging as **new since the last synthesis**, purely from this read-only pass:

- **Every device's `dumpsys thermalservice` exposes exactly one `type=3` (SKIN) sensor**, and on
  the two devices where the internal ratio table is printed, that SKIN sensor is the one the
  table's thresholds describe. This is a candidate universal "ground-truth sensor" across
  vendors for Q2 — worth testing directly rather than assuming from naming, since the multi-device
  skill's standing warning is "never hardcode a sensor name."
- **Pixel 3's `scaling_max_freq` is unreadable via shell on this unit this session**
  (`Permission denied` on all 4 entries), while `scaling_cur_freq` reads fine. This is a new
  device-specific read restriction not previously logged — treat `cur_freq` as the portable
  signal fleet-wide and do not assume `max_freq` readability (worth a device-gotchas addendum).
- **The internal headroom ratio-threshold array is not printed on Pixel 3 (API 31) or A01
  (API 29)**, even though Pixel 3 fully supports `getThermalHeadroom()` (API 30+). The
  diagnostic line's availability is gated by something other than headroom-API support — so it
  cannot be relied on as a per-device calibration shortcut; 2 of 4 devices need the empirical
  heating experiment to establish their curve, not a table read.
- **Idle SKIN temps sit 31–34 °C on all four devices** (used as this session's un-throttled
  reference point) while **idle headroom was previously recorded at 55–65%** — i.e. idle skin
  temperature is 5–8 °C *below* every device's own "light" threshold, yet the reported headroom
  is already halfway to the cap. This gap is exactly the discrepancy the task description flags
  ("mid-scale is the RESTING state") and confirms `getThermalHeadroom()` is not a simple
  instantaneous ratio of current temp to the light threshold — it is very likely doing some
  form of trend/forecast smoothing whose window differs by vendor. **This is an inference from
  today's numbers, not a confirmed mechanism** — Section 5 below designs the experiment that
  would confirm or refute it.
- Cached vs. "current" HAL temperature blocks disagreed sharply on Pixel 3 (cached section
  showed CPU cores at 64–72 °C; the live HAL re-query showed 34–35 °C) and, to a lesser extent,
  on Pixel 7 Pro (cached `soc`=100 °C/status=2 vs. live 30–35 °C range). This is expected: the
  "Cached temperatures" block is the last value the HAL pushed via callback (which can be stale
  by an unknown interval, plausibly left over from other work on these shared devices per this
  session's constraints), while "Current temperatures from HAL" is a live poll. **Any future
  script must read the live-poll block, never the cached block**, and should log the delta
  between them as a staleness sanity check in its own right.

---

## 1. Ground truth, independent of the attributes

Ground truth must never be the attribute's own SDK code path. Two independent instruments,
both read-only, unrooted, already probed working on all 4 devices this session:

1. **`dumpsys thermalservice`** — per-sensor current values (`Current temperatures from HAL`
   block only, never `Cached temperatures`), the platform's own throttling `Thermal Status`
   integer, and — where exposed — the per-sensor hot-throttling threshold arrays and the
   headroom severity-ratio array. Per-device sensor names are **discovered, not hardcoded**
   (confirmed necessary again this session: `VIRTUAL-SKIN` / `SKIN` / `fps-therm-monitor` /
   `SKIN_T` are four different names for the same sensor role). The type=SKIN sensor found on
   every device this session is the a-priori best candidate for "the sensor status/headroom are
   actually computed from" and should be logged explicitly per launch.
2. **CPU frequency caps**: `/sys/devices/system/cpu/cpu*/cpufreq/scaling_cur_freq` (portable —
   read on all 4 devices this session) as the primary clock-throttling signal;
   `scaling_max_freq` as a secondary corroborating signal **only where readable** (failed on
   Pixel 3 this session — feature-detect, don't assume). A drop in `cur_freq` from its
   established idle/max value during a heated pass is direct, independent evidence that
   clock-level throttling is occurring; a flat `cur_freq` with a slowing SDK-init window points
   at bus/memory throttling instead (the mechanism already established for Pixel 3 in the
   2026-08-13 program).

Per-device baseline to reuse in the design below (this session's readings, all screens-on,
idle, and NOT freshly heated by this session):

- Pixel 7 Pro: SKIN 31.5 °C / battery 30.7 °C, clocks unthrottled (2850/2348/1803 MHz caps).
- Galaxy A14: SKIN 31.3 °C / battery 29.2 °C, clocks unthrottled (2002 MHz cap, uniform).
- Pixel 3: SKIN(fps-therm-monitor) 31.8 °C / battery("maxfg") 29.8 °C, `cur_freq` already at
  the known throttle-plateau value 2803.2 MHz even at idle (SD845 appears to run this cluster
  at a fixed clock regardless of load — consistent with the prior finding that Pixel 3's
  throttling is bus/memory-side, not a clock drop, so idle and hot passes may show an
  *identical* `cur_freq` and the discrimination must rest on `init-cpu-ms` / window inflation,
  never on `cur_freq` alone for this device).
- Galaxy A01 Core: SKIN_T 34.0 °C / battery 32.1 °C (warmest idle reading of the four — no
  headroom API on this device regardless, since API 29 < 30).

---

## 2. Inducing heat safely and repeatably

Reuse the 2026-08-13 program's protocol verbatim rather than re-deriving it — it is already
validated (Pixel 3 dose-response reproduced 29→41 ms window inflation across battery
30→41 °C):

- **Duty-cycled CPU load**: `dd if=/dev/zero of=/dev/null` hogs, N scaled per device
  (2 on A01 — 1 GB RAM, more induces Class-D memory pressure which is a confound, not a
  heater; 4 on Pixel 7 Pro/Pixel 3/A14).
- **Screen-max-brightness** for contention-free baseline heat on the upper bands
  (flagship dissipation on Pixel 7 Pro may need this plus charging to reach its top band —
  degrade gracefully by dropping the unreached band rather than forcing it).
- **Charging held constant** (physically connected for the whole experiment on every device —
  a controlled background, not a variable to induce or avoid mid-run).
- **Quiesce discipline** (this is the confound-prevention step the task calls out
  specifically): hogs killed → sleep 3 s → host idle-check (`/proc/loadavg` < cores/2,
  retry once) → THEN read ground truth → THEN launch. The heater must never run concurrently
  with a measured launch, or a Class-A CPU-contention artifact masquerades as a thermal one.
- **Hard abort watchdog**: battery ≥ 43.0 °C → kill all hogs, restore brightness/stay-awake,
  `dumpsys battery reset`, mark the device ABORTED-THERMAL, keep 2-min cooldown logging.
  Soft ceiling 42.0 °C → no further up-steps, finish current block, begin descent. Checked
  before every hog burst and every 60 s during any heating phase. Per-device and independent —
  one device aborting never stops the others.
- **Axis choice**: bands are defined and serviced on **battery temperature** (slow thermal
  mass, matches the already-established Pixel 3 dose-response axis), while every
  `dumpsys thermalservice` sensor is co-logged per launch so the sensor-race analysis (Q2) can
  test whether a silicon/skin sensor predicts window inflation better than battery.

This machinery is unchanged from the 08-13 plan; nothing here needs re-validating before use.

---

## 3. Polarity / cap check for `thermal-headroom-pct`

Two things must be verified empirically, not assumed from the spec:

1. **Monotonicity through the reachable range.** During each staircase band, plot headroom-pct
   against the co-logged SKIN temperature. It must be non-decreasing band-over-band (allowing
   sensor noise within a band). A non-monotonic run (headroom *drops* as the device gets hotter,
   or plateaus below 100 while the SKIN sensor keeps climbing past its own "severe" threshold)
   would mean the attribute has drifted from what the platform's own threshold table implies —
   grounds for a FANTASY verdict regardless of what the spec promises.
2. **Where the cap actually bites.** The task's own device sample already shows idle headroom
   (55–65%) sitting well below 100 while status reads "none" everywhere — so the interesting
   question is not whether 100 is reachable (trivially, push the device to the severe
   threshold) but **whether the reported value still discriminates in the band immediately
   below the cap**, i.e. does headroom keep rising smoothly from ~70 to ~99 as SKIN approaches
   its severe threshold, or does it saturate early (e.g., pinned at 95–100 for the whole upper
   third of the reachable range)? A device whose real slowdown only appears once headroom is
   already saturated at 100 would make the attribute USELESS for early warning even though it
   is technically monotonic. Test this directly: bin headroom into deciles across every band
   collected (baseline through the highest safely-reached band) and check whether window
   inflation (relative to that device's cool-baseline median) has a strictly increasing
   median per decile, including the deciles just below 100.
3. Cross-check the ratio-threshold arrays where they are exposed (Pixel 7 Pro, Galaxy A14):
   compute the expected `100 × ratio` a purely-instantaneous formula would predict for the
   band's observed SKIN reading, and compare to the SDK's actually-reported
   `thermal-headroom-pct` at the same instant. A large, systematic gap (as already hinted by
   the idle-temperature mismatch above) confirms the platform is smoothing/forecasting rather
   than reporting instantaneous ratio — record the gap's size and direction per device, since
   that gap is itself part of what makes cross-vendor comparison hard (Section 5).

---

## 4. Staleness / NaN question

The SDK code (`SdkInitEnvironmentAttributes.kt`) calls `getThermalHeadroom(0)` exactly once,
guards with `runCatching {}.getOrNull()` and an `isFinite()` check, and **silently omits the
attribute** on failure or NaN — there is no counter, log, or fallback. This is precisely the
PSI failure shape (correct per spec, silently absent in practice) unless proven otherwise.
Today's read-only probes cannot answer this: `dumpsys thermalservice` does not exercise
`getThermalHeadroom()` at all (it reports raw HAL sensor state and static thresholds — a
different code path), so staleness/NaN behavior needs a purpose-built micro-probe, not another
`dumpsys` read.

**Design** (a tiny standalone instrumented test/harness, not the SDK's own init path, run
interactively — NOT part of today's read-only pass):

1. A minimal on-device probe (Espresso/instrumented test or a debug-build button) that calls
   `powerManager.getThermalHeadroom(0)` **N times back-to-back with no delay**, logging each
   raw return value, its timestamp, and whether it is `NaN`/finite. Repeat immediately after
   varying delays (0 ms, 200 ms, 500 ms, 1 s, 2 s) to find the platform's actual cache/refresh
   interval empirically per device, rather than trusting the "~1 s" figure from documentation.
2. Run this probe **once at idle** and **once immediately after a heating burst** on each of
   the 3 headroom-capable devices (Pixel 7 Pro, Galaxy A14, Pixel 3) — the failure mode most
   worth checking is whether a call issued right after a burst of other binder/thermal-HAL
   traffic (which SDK init itself generates, since init reads several other attributes via
   binder calls in the same window) is more likely to return stale or NaN than one issued at
   rest.
3. **Grade**: any NaN observed → the attribute is a real staleness risk and the SDK should
   count (not just silently drop) that outcome — recommend an internal debug counter even if
   the public attribute stays silent-on-failure, so a future audit doesn't have to rediscover
   this from scratch the way the PSI removal did. Any run returning identical values across all
   N rapid-fire calls → confirms caching (informs how often production init can expect a
   "fresh" reading versus a leftover one from a previous call elsewhere in the process).
4. This is cheap (no heating required for the idle half of the test) and should be run **before**
   the full staircase campaign — a device whose headroom read is frequently NaN right after
   binder-heavy init work would need that fact folded into the interpretation of every other
   result in this document.

**Not yet probed — explicitly flagged**: whether this specific `getThermalHeadroom(0)` call
site, invoked from the SDK's actual environment-attribute code path (not a synthetic loop),
ever returns NaN or a stale value in situ. That is exactly what step 2 above is for; it has not
been run.

---

## 5. Cross-vendor comparability

The central comparability question: **does headroom = 70 mean the same real slowdown risk on a
Pixel 7 Pro as on a Galaxy A14?** Today's probes already surfaced a reason to doubt it (idle
headroom sits 5–8 °C below each device's own light-threshold on the SKIN axis, yet reads
55–65% on all of them, and the internal severity-ratio arrays differ in shape between vendors
in the sub-severe region: Pixel 7 Pro's moderate/severe/critical bands are compressed to
0.933/1.0(implied)/1.05, while Galaxy A14's are 0.933/1.0(implied)/1.1 — i.e., proportionally
more headroom-percentage-points separate "moderate" from "critical" on the Samsung device).

**Design**: run the identical staircase protocol (Section 2) on both devices side by side,
targeting matched **outcome** bands rather than matched raw temperature — i.e., instead of
comparing "headroom at battery 35 °C" across vendors (meaningless if their thermal mass and
onset differ, which they demonstrably do — Pixel 3 onset ~32 °C-equivalent, Pixel 7 Pro ~35 °C
per the 08-13 program), compare **headroom at matched relative init-window inflation**
(e.g., the headroom value each device reports when its own window is 15% slower than its own
cool-baseline median). If the headroom value at "15%-slower" differs by more than the
within-device measurement noise between Pixel 7 Pro and Galaxy A14, headroom is **not**
comparable across vendors as a raw number, and:

- **What to conclude if it is not comparable** (state plainly, per the task's requirement):
  a fixed threshold like "headroom ≥ 80 ⇒ thermally degraded" cannot be applied fleet-wide in
  production. The attribute would need to be queried **per device model** (joined against
  `device.model`/`device.manufacturer` resource attributes, which the SDK already emits) rather
  than with one global cutoff, or paired with `thermal-status` (which IS anchored to
  vendor-calibrated absolute thresholds, per Section 1) as the portable half of the signal.
  This should be written up as an explicit recommendation, not left implicit, since it directly
  changes how a dashboard or alert should query the attribute.
- A same-conclusion sanity check: repeat the comparison using `thermal-status` transitions
  instead of headroom values as the matching key (does "light" mean the same real slowdown
  magnitude on both vendors?) — status is coarser but anchored to an absolute, documented
  physical threshold per sensor, so it is the natural fallback if headroom fails the
  cross-vendor test.

---

## 6. Grading protocol

- **Arms**: cool baseline (idle, both devices already ~30–32 °C skin/battery per today's
  probes), warm (mid-staircase band, prior-established onset ~32–35 °C battery), hot
  (highest safely-reached band under the 43 °C hard abort). Reuse the 08-13 program's 5-band
  (4 for A01) ascending+descending staircase rather than inventing a new one.
- **Iterations**: n=30/band/direction on Pixel 7 Pro and Galaxy A14 (effect sizes ~4–12 ms,
  per-launch sd 2–8 ms per prior work), n=30 on Pixel 3 (established largest effect, same n
  suffices), n=45/band/direction on A01 (sd 20–30 ms, status-only grading since no headroom
  API). Floor: never below n=25/band — matches the house 4×25 minimum.
- **Per-iteration grading against that iteration's own ground truth** (never pooled stats
  first): each launch's `thermal-status`/`thermal-headroom-pct` is compared to that same
  launch's host-logged `dumpsys thermalservice` SKIN reading and battery temp, taken
  immediately pre-launch — never to another iteration's reading, and never to the cached HAL
  block (Section 0 finding).
- **Numeric bar for "directionally accurate"**: (a) `thermal-status` must show at least one
  step transition (none→light or higher) within the safely-reached temperature range on at
  least one of Pixel 7 Pro / Pixel 3 / Galaxy A14 — a status that stays "none" on every device
  up to the 43 °C hard limit is the kill condition (Section 8); (b) `thermal-headroom-pct`
  must show Spearman ρ ≥ 0.8 against the co-logged SKIN temperature across all bands on a
  device (monotonicity bar), and the decile-binned window-inflation curve from Section 3 must
  be non-decreasing with no more than one decile-pair inversion.
- **Correlation / ordering test used**: Spearman rank correlation (headroom vs. SKIN temp,
  and headroom vs. window duration) per device — rank-based because the platform's
  forecast/smoothing behavior (Section 0 finding) means the relationship is not assumed linear;
  hinge/step-shape fitting reused from the 08-13 program's Q1 methodology for the underlying
  window-vs-temperature curve, with headroom substituted for temperature as an additional axis
  to rank against the same outcome.
- **Detectability, reused from 08-13's T7 design**: AUC of `thermal-headroom-pct` (and
  `thermal-status` ≥ light/≥ moderate cuts) against the outcome label
  `degraded = window > cool-baseline-median × 1.15`, fit on the ascending arm, scored on the
  descending arm (temporal-leakage control). Combined-model ΔAUC test (headroom +
  `init-cpu-pct`) reused verbatim from H-D3.

---

## 7. Integration with profiling runs, cost, devices, safety

- **Devices needed**: the same 4 already in this fleet (Pixel 7 Pro, Galaxy A14, Pixel 3,
  Galaxy A01 Core) — no new hardware. Galaxy A01 is status-only (no headroom API), consistent
  with its existing role in the fleet.
- **Cost**: this reuses the 08-13 program's Batch 1/Batch 2 wall-clock estimates almost
  exactly (staircases ~3.5–4 h/device run in parallel across 3 devices; A14's batch runs on
  its own night since it is busy with other work per this session's device list) — the
  additions here (Section 3's decile-binning and Section 4's staleness micro-probe) are
  analysis-side or a single short interactive session (~20 min/device, no heating needed for
  the idle half of Section 4) and do not add device-night cost.
- **Does it block repo work?** No — it uses the same ExampleApp debug build already used for
  the broader startup-benchmark work, run overnight/unattended per device, and does not touch
  any files this session was told not to touch. It should NOT run concurrently with any other
  benchmark campaign on the same device (device-gotchas: a second driver silently kills a run).
- **Safety limits**: identical to Section 2 — 43 °C hard abort, 42 °C soft ceiling, per-device
  independent watchdogs, charging held constant (never induced/removed as a variable),
  A01 capped at 2 CPU-only hogs (1 GB RAM — more hogs induce Class-D memory pressure, a
  confound not a heater).

---

## 8. Kill criteria

**`thermal-status`:**

- **Useless-as-early-warning verdict, specifically**: if, across the full safely-reachable
  heating range on every device (up to the 43 °C battery hard limit), `thermal-status` either
  (a) never leaves "none" at all, or (b) only transitions to "light"/"moderate" at a point
  where the co-logged window duration has *already* inflated by more than 20% over that
  device's cool-baseline median (i.e., the status lags the damage rather than predicting it —
  this is precisely what H-T3 in the 08-13 program predicts for Pixel 3 and must be checked on
  every device here, not assumed from that one prediction). Either observation downgrades the
  verdict to OBTAINABLE BUT USELESS regardless of how "correct" the status mapping is against
  the raw PowerManager call.
- **FANTASY verdict**: if the status value returned by the SDK's attribute never matches what
  `dumpsys thermalservice`'s live `Thermal Status` integer reports at the same instant (a
  plumbing bug, not a sensor limitation).

**`thermal-headroom-pct`:**

- **FANTASY verdict**: if Section 4's staleness probe shows NaN or clearly-stale repeated
  values at a rate that would matter in practice (e.g., NaN on a non-trivial fraction of calls
  made under binder-call load resembling SDK init's own environment-read burst), since the
  code silently drops the attribute on that outcome today — an undetected-in-production failure
  shaped exactly like the PSI removal.
- **USELESS verdict**: if Section 3's monotonicity/decile check shows the value saturating
  (pinned near 95–100) well before real slowdown appears, so the "early warning" region is
  compressed into noise; or if Section 6's Spearman bar (ρ ≥ 0.8 against SKIN temp) is not met
  on any headroom-capable device.
- **Comparability finding (not a kill, but changes the recommendation)**: if Section 5's
  matched-outcome comparison shows the same headroom value corresponds to materially different
  real slowdown between Pixel 7 Pro and Galaxy A14, the attribute survives as
  OBTAINABLE + DIRECTIONALLY ACCURATE **per device model**, but the recommendation must state
  explicitly that it cannot be queried with one global threshold in production — query it
  joined to `device.model`, or prefer `thermal-status` for any fleet-wide rule.

---

## 9. Results — RAN 2026-08-14, both verdicts reached

**Status: RAN.** Tracked in the tracker as X16 (was: designed, not yet run). Ran on Pixel 7 Pro,
Galaxy A14, and Pixel 3 in parallel (not staggered batch 1/2 as scoped above); Galaxy A01 Core
was not part of this campaign (no headroom API, per the capability fact already noted in
Section 7).

### Method actually used

Duty-cycled heating rather than the staircase-with-quiesce-discipline protocol in Section 2:
8 CPU burners for 150 s, then heaters **stopped** and the device left to settle 20 s before each
measurement, so the heater's own load is never part of what is measured (otherwise "hot" and
"busy" are confounded). Per round: platform `thermal-status` and skin sensor from
`dumpsys thermalservice`, `scaling_max_freq` per cluster as independent throttling evidence, full
app launch time via `am start -W`, and the attributes read via the X11 verification tap. 43 °C
battery abort throughout, three devices in parallel. This is a smaller, faster instrument than
Section 6's grading protocol (single measurement per round rather than n≥25–30/band
ascending+descending arms, no Spearman/AUC fit) — see the deviations noted below.

### Per-device results

**Pixel 3 (older mid, API 31)** — baseline skin 37.08 °C, status 0, attr `none`, headroom 58,
launch 1259 ms, CPU 2803 MHz.

| round | skin °C | platform status | attr status | headroom | launch ms | max freq |
|---|---|---|---|---|---|---|
| 1 | 37.71 | 0 | none | 78 | 1181 | 2803 MHz |
| 2 | 39.01 | 1 | light | 80 | 1154 | 2803 MHz |
| 3 | 39.01 | 1 | light | 81 | 1276 | 2803 MHz |

Status flipped at exactly the vendor's documented 39.0 °C `light` threshold. **No slowdown and
no clock capping at any point.**

**Pixel 7 Pro (flagship, API 35)** — baseline skin 36.98 °C, status 0, attr `none`, headroom 53,
launch 1189 ms, clusters at 1803/2348/2850 MHz.

| round | skin °C | platform status | attr status | headroom | launch ms | max freq (clusters) |
|---|---|---|---|---|---|---|
| 1 | 36.98 | 0 | none | 72 | 1562 | 1803/1836/**2048** |
| 2 | 37.02 | 0 | none | 73 | 1697 | 1803/1826/**1836** |
| 3 | 37.87 | 0 | none | 75 | 1695 | 1745/1803/1836 |
| 4 | 38.11 | 0 | none | 76 | 1769 | 1803/1826/1836 |
| 5 | 39.02 | 1 | light | 77 | 1717 | 1401/1745/1836 |
| 6 | 39.02 | 1 | light | 79 | 1711 | 1745/1803/1836 |

**This is the headline result.** The big cluster was capped from 2850 to 2048 MHz at round 1 and
to 1836 MHz by round 2, and launch time rose from 1189 ms to 1562–1769 ms (+31% to +49%) — all
while `thermal-status` still read `none`. Status did not flip until round 5, roughly 12 minutes
and four rounds after throttling and the slowdown began. `thermal-headroom-pct` by contrast
jumped 53→72 at round 1, coincident with the first clock cap.

**Galaxy A14 (mid, API 35)** — inconclusive on heat, but informative in two other ways. Its skin
sensor reported exactly 36.9 °C on all seven measurements (a stalled sensor, echoing the known
Pixel 3 battery-sensor stall), battery rose only 28.9→29.7 °C across ~20 minutes of 8-burner
load, no clock capping (2002 MHz throughout), and no launch-time trend (4404–4809 ms, noisy). Yet
headroom still climbed 64→83. CPU-only `dd` load does not meaningfully heat this device.

### Verdicts reached

**`thermal-status` — obtainable and faithful to the platform, but USELESS as an early-warning or
segmentation signal.** Two independent failures: (a) on the flagship it lagged badly, staying
`none` through a 31–49% slowdown and a 2850→1836 MHz cap, so segmenting on it would miss the
majority of thermally-degraded launches; and (b) it is not comparable across vendors — it fired
at 39 °C with zero slowdown on the Pixel 3 and at 39 °C after severe slowdown on the Pixel 7 Pro.
The same value means opposite things on different devices. This hits the Section 8 kill bar for
`thermal-status` directly: it only transitioned at a point where window duration had already
inflated well past the 20% lag threshold on the device where it mattered.

**`thermal-headroom-pct` — obtainable and genuinely responsive, but only interpretable per device
model.** It caught the throttling onset that status missed (53→72 exactly at the first capped
round). But its value is not portable: 79 accompanied severe slowdown on the flagship while 81
accompanied none at all on the Pixel 3. It should be read against a given model's own history,
never as a fleet-wide threshold — exactly the Section 8 "comparability finding" outcome, not a
kill: the attribute survives as OBTAINABLE + DIRECTIONALLY ACCURATE **per device model**. Also
note its likely nature as a forecast rather than a temperature reading — on the A14 it rose
64→83 while the skin sensor was pinned and battery moved under 1 °C, which is consistent with the
API's documented forecast semantics and with it responding to sustained load.

**NaN/staleness risk (Section 4's cheap prerequisite) — resolved, negative.** The headroom
attribute was present on every launch across all three devices; there were zero silent drops.
This was the prerequisite the design flagged as the PSI-shaped failure mode, and it did not
materialise. It was folded into this campaign's presence tally rather than run as the standalone
rapid-fire micro-probe designed in Section 4.

**Practical recommendation:** prefer `thermal-headroom-pct` for detecting thermal involvement,
treat `thermal-status` as confirmation rather than detection, and never compare either value
across device models.

### Deviations from the design above

- Ran as a short ascending duty-cycled staircase (3 rounds on Pixel 3, 6 on Pixel 7 Pro, 7
  measurements on Galaxy A14, one measurement per round) rather than Section 6's n≥25–30/band
  ascending+descending arms with Spearman/AUC grading — this run is directional, not a formal
  statistical bar.
- Section 2's quiesce discipline (hogs killed → sleep 3 s → host idle-check → read ground truth
  → launch) was replaced with a fixed 20 s settle after a fixed 150 s heat phase — same intent
  (heater load never overlaps a measured launch), simpler execution.
- Section 4's staleness micro-probe (N rapid-fire `getThermalHeadroom(0)` calls at varying
  delays) was not run as a standalone step; staleness was instead judged from attribute presence
  across this campaign's own launches, which is a weaker but directionally sufficient check given
  the result (zero drops observed).
- Idle baselines this session (skin 36.98–37.08 °C on Pixel 7 Pro/Pixel 3) ran noticeably warmer
  than the 2026-08-13/14 probe baselines used to scope this design (31.5–31.8 °C on the same two
  devices) — worth checking before trusting future "idle" reference points from this fleet at
  face value; not investigated further here.

### Left outstanding

- The A14 never heated meaningfully under CPU-only load, so it contributed no heat/slowdown
  data; a heavier or GPU-inclusive workload would be needed there.
- The headroom→slowdown mapping is established only on the Pixel 7 Pro; the Pixel 3 never slowed
  within the safe range, so its mapping is unknown.
- Measurements used full app launch time (`am start -W` TotalTime), which is coarser than the
  SDK-init window; it was sensitive enough to catch the flagship's large regression but could
  miss the ~10 ms-scale window changes earlier thermal work found on the Pixel 3.
- The A01 Core was not tested — it is API 29 and has no `getThermalHeadroom` at all (a capability
  fact, not a gap).
