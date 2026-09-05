# Thermal Deep-Dive Experiment Program — SDK Startup vs Device Temperature

**Date:** 2026-08-13 · **Status:** PLAN (overnight, unattended, no root, single host)
**Fleet tonight:** Pixel 3 (SD845, A12), Pixel 7 Pro (Tensor G2, A15), Galaxy A01 Core (MT6739 1GB, A10 Go).
**Galaxy A14 (Exynos 850, A15) is BUSY tonight — all A14 work is Batch 2.**

Builds on the 2026-08 four-device synthesis (ground truth): the SDK-init window is
memory-bound (window = work ÷ (clock × IPC)); Pixel 3 shows a proven dose-response
(window 29→41 ms as battery 32→39 °C) via **memory-bus throttling at constant 2803 MHz CPU
clock**; Pixel 7 Pro drifts ~11-12→~16 ms past battery ~35 °C (slow-execution signature,
mechanism unknown); A01 is dominated by scheduler contention (~100 ms window); A14 has the
SPEG install-parity compile toggle that must be controlled before any thermal comparison.
New telemetry on the init span: `thermal-status`, `thermal-headroom`, `init-cpu-ms`,
`init-run-delay-ms`, plus per-section duration attrs — pullable per-launch from the debug
app's cached payloads via `run-as` (validated).

---

## 1. Questions → testable hypotheses

### Q1 — MAGNITUDE: what is the ms-per-°C curve, per device?

- **H-M1 (Pixel 3):** window vs battery-temp is piecewise linear: flat (slope ≈ 0) below an
  onset temp, then a positive linear segment of roughly **+1.5–2.0 ms/°C(battery)**
  (extrapolating the established 12 ms / 7 °C). *Falsified if:* the fitted relationship is
  better described as a step (single jump at a mitigation trip) or is non-monotonic, or the
  slope confidence interval includes 0 above the established 32–39 °C range.
- **H-M2 (Pixel 7 Pro):** same piecewise-linear shape with onset near **battery ~35 °C** and a
  smaller slope, ~**+0.8–1.5 ms/°C**, saturating (Tensor's mitigation is table-driven, so a
  staircase/step shape is the live alternative). *Falsified if:* no reproducible inflation at
  battery ≥36 °C under controlled heat-soak (would mean the observed am-start drift was an
  artifact of something co-varying with temperature that night, e.g. accumulated churn).
- **H-M3 (A01 Core):** a thermal slope exists but is **small relative to scheduler noise**
  (per-launch sd ~20–30 ms); detectable only on band *medians* with n≥45/band; predicted
  ≤ +1 ms/°C on the median. *Falsified either way is informative:* a large clean slope means
  entry-tier MediaTek throttles hard (prod-relevant); a null at 40 °C battery bounds the
  effect for Go-tier.
- **H-M4 (A14, Batch 2):** with compile state pinned, Exynos 850 shows a measurable slope;
  shape unknown. Must survive the compile-state control or it's confounded.

Curve shape is judged per device by comparing three fits on band medians: (a) single line,
(b) hinge (flat + line, free breakpoint), (c) step. Report slope ± bootstrap CI in
ms/°C for the chosen sensor axis (see Q2), on both battery and best-silicon axes.

### Q2 — TRIGGER POINT: what onset threshold, and which sensor predicts it?

- **H-T1:** each throttling device has an onset threshold; on Pixel 3 it lies at or below
  battery 32 °C-equivalent (we've only ever seen the sloped region — the staircase's cool
  bands establish the flat region for the first time). On P7P it is near battery 35 °C.
- **H-T2 (sensor race):** the best predictor of window inflation is a **heat-soak sensor**
  (battery or a board/skin thermalservice sensor), NOT the instantaneous CPU silicon sensor —
  because the mechanism is memory-subsystem temperature (slow thermal mass), and CPU sensors
  collapse within seconds of load removal. *Falsified if:* an instantaneous CPU/AP sensor
  fits window inflation with materially higher R² than every slow sensor — that would point
  at real-time trip-table mitigation rather than soak physics, and changes what prod
  telemetry should sample.
- **H-T3:** `dumpsys thermalservice` throttling **status level transitions lag onset** — on
  Pixel 3 the window is already inflated >20% while status still reads NONE/LIGHT.
  (Direct setup for Q4 detectability.) *Falsified if:* status transitions coincide with the
  fitted breakpoint within ±1 band.
- Known trap baked in: **Pixel 3's skin-type sensor reads a constant 37.083 — excluded a
  priori; battery + thermalservice AP/silicon sensors are the P3 axes.**

Analysis: per device, per sensor, fit hinge regression of window on that sensor's reading
at launch time; rank sensors by out-of-sample R² (fit ascending arm, score descending arm).
The winning sensor + its breakpoint = the device's trigger point.

### Q3 — MECHANISM: clock-capping vs bus/devfreq throttling vs scheduler, per device

Discrimination table (all unrooted observables):

| Observable | Clock capping | Bus/devfreq throttle | Scheduler/placement |
|---|---|---|---|
| Delivered CPU MHz in-window (perfetto freq track; host `scaling_cur_freq` probe) | **drops** | constant | constant |
| `init-cpu-ms` (thread CPU time) | up ∝ clock deficit | **up (IPC drop) at constant clock** | flat |
| `init-run-delay-ms` (runnable wait) | flat | flat | **up** |
| Section inflation uniformity (per-section attrs / trace sections) | uniform | uniform-to-skewed: memory-bound sections (class-load, deserialization, persisted-config-load) inflate MORE than compute-lean sections | lumpy — specific sections hit by preemption |
| Per-CPU window residency (trace) | unchanged | unchanged | **shifts to little/mid cores** |

- **H-X1 (Pixel 3):** trace-level confirmation of the established verdict — hot passes show
  constant 2803 MHz, Running time inflated ~1.3–1.4× uniformly (memory-bound sections most),
  runnable-wait flat, residency unchanged. *Falsified if:* delivered clock drops in hot
  passes (would revise mechanism to mixed/clock), or inflation is dominated by run-delay
  (scheduler mitigation — e.g. thermal core-pinning — masquerading as execution slowness).
- **H-X2 (Pixel 7 Pro):** open question, two live predictions. P7P's drift has a
  *slow-execution* signature, so it's execution-side; Tensor is known to clock-throttle
  aggressively → **prediction: delivered-clock drop** (mid/big cluster caps) rather than
  constant-clock IPC loss. Whichever side wins, the decomposition + freq track decides it in
  one C-H-H-C campaign. *Falsified (as thermal at all) if:* hot and cool arms match at
  battery ≥36 °C.
- **H-X3 (A01):** any thermal inflation on A01 loads onto `init-run-delay-ms` no more than on
  `init-cpu-ms` — i.e. heat does not primarily act via worsened scheduling on the 4×A53. The
  design must first EXCLUDE heating-method leftovers (hogs killed + quiesce-verified before
  every launch), else Class-A contamination fakes a scheduler mechanism.
- **H-X4 (A14, Batch 2):** devfreq is SELinux-unobservable on Samsung → mechanism call rests
  on freq tracks + cpu/run-delay decomposition + section skew only; state that limitation in
  the verdict.

### Q4 — DETECTABILITY: can `thermal-status` / `thermal-headroom` classify degraded startups?

- **H-D1:** `thermal-headroom` is a *graded* predictor: AUC ≥ 0.8 for classifying
  outcome-degraded startups (window > cool-median × 1.15) on Pixel 3 and P7P.
  *Falsified if:* AUC < 0.65 — headroom then reflects a different trip table than the one
  gating the memory subsystem, and prod needs a different signal.
- **H-D2:** `thermal-status` alone is coarse and late (per H-T3): sensitivity < 0.5 at the
  degraded label on Pixel 3 even where headroom performs well.
- **H-D3:** headroom **combined** with the decomposition (`init-cpu-ms`/wall share — "ran
  slow" vs "was blocked") beats headroom alone (ΔAUC ≥ +0.05), because it separates
  thermally-slow from contention-slow startups that a temp attr alone conflates.
- **Platform limit:** A01 is API 29 → `getThermalHeadroom` unavailable; A01 detectability is
  graded on `thermal-status` only. Record this as a prod coverage gap for Android 10 devices.

Ground truth is *independent of the attrs*: host-logged protocol phase + host-side
thermalservice/battery readings per launch (§4).

---

## 2. Experiment matrix

Common instrumentation (every experiment): per-device CSV log
`claude-output/thermal-<date>-<device>/launches.csv` with columns
`ts, phase, band_c, launch_idx, am_start_total_ms, batt_temp, batt_level, therm_status,
<every thermalservice sensor by name>, scaling_cur_freq_policy*(feature-detected)`;
temps sampled **immediately before each launch** and every **30 s** during heat/cool phases;
telemetry attrs (window, init-cpu-ms, init-run-delay-ms, thermal-status, thermal-headroom,
per-section durations) joined post-hoc from `run-as` payload pulls at each block boundary,
keyed by process-start timestamp. Pulled payload files are deleted on-device after each pull
(`run-as <pkg> rm`) so the cache never grows unbounded; app data is otherwise never cleared
(preserves persisted config → no iter000 fast-path pollution).

**Battery-safety watchdog (every experiment, hard requirement):** before every hog burst and
every 60 s during any heating phase, read battery temp; **if ≥ 43.0 °C → kill all hogs,
restore brightness, `settings put global stay_on_while_plugged_in 7→0` (screen allowed off),
`dumpsys battery reset`, mark the device's protocol ABORTED-THERMAL, keep logging temps at
2-min cadence for the cooldown record.** Soft ceiling 42.0 °C: no further up-steps, finish
the current block, begin descent. The watchdog is per-device and independent — one device
aborting never stops the others.

### T0 — Heating/cooling calibration (all 3 devices, parallel, ~45 min)

- **Protocol:** per device: (1) log baseline temps 2 min idle, screen on; (2) start 2 dd
  hogs, log temps/30 s for 5 min; (3) kill, +2 hogs (4 total; A01: 2 max — 1 GB device, do
  not exceed), 5 min; (4) kill all, screen max brightness only, 5 min; (5) all off, screen
  off, log cooling for 8 min. A01 uses 1-then-2 hogs.
- **Measures:** °C/min heating rate per hog count, screen-only steady-state delta,
  cooling rate (validates the ~1 °C/2–4 min prior), and the hog-count→achievable-band map
  that T1–T3 scripts consume as a lookup table.
- **Prediction:** each device can reach its top band (§3) within 15 min of heating.
  *If not* (P7P is the flagship-dissipation risk): drop the unreachable band and extend the
  hold at the highest achievable one — the script must handle this branch (band skipped is
  logged, not fatal).
- **Confounds controlled:** run at night-start so ambient is at its warmest-indoor point;
  calibration data is not analysis data.

### T1 — Pixel 3 staircase dose-response ★ core magnitude experiment (~3.5 h, parallel)

- **Protocol:** §3 staircase, bands (battery °C): **30 (baseline), 33, 36, 39, 41** ascending,
  then **39, 36, 33, 30** descending (hysteresis). n = 30 launches/band/direction
  (`am start -W` cycles, §3 hold discipline). Ends with T10 recovery tail.
- **Measured:** window + decomposition + attrs per launch (telemetry join), am-start
  TotalTime as the cheap cross-check, all sensors per launch.
- **Predictions:** H-M1 slope +1.5–2.0 ms/°C above onset; flat ≤ ~31 °C; descending arm
  retraces ascending within ±1 ms at matched battery temp (soak physics, minimal governor
  hysteresis). *Falsifiers:* per H-M1; large asc/desc gap at matched battery temp =
  governor-state hysteresis → mechanism follow-up in Batch 2 (T8-style status-transition
  logging at 5 s cadence).
- **Confounds:** heating-hog contention → hogs killed + 3 s quiesce + host idle-check before
  every launch, and any launch whose telemetry shows anomalous run-delay is flagged
  (contamination screen §3); charging heat → charger physically connected ALL night on ALL
  devices = constant background, not a variable; time-of-night ambient drift → the
  descending arm re-samples every band hours later (built-in counterbalance), plus the
  baseline band is sampled at both night-start and night-end; install aftermath → single
  install before T0, first 3 launches discarded.

### T2 — Pixel 7 Pro staircase dose-response (~3.5 h, parallel with T1)

- **Protocol:** identical staircase; bands **28 (baseline), 32, 35, 38, 40**; heating needs
  4 hogs + screen-max + charging (flagship dissipation — T0 tells us the real ceiling;
  degrade gracefully per T0's branch). n = 30/band/direction.
- **Predictions:** H-M2 onset ~35 °C battery, +0.8–1.5 ms/°C or step shape; below 34 °C
  reproduces the established immunity (medians 11–12 ms). *Falsifier:* per H-M2 (null at
  ≥36 °C soak → the earlier drift was not thermal).
- **Confounds:** as T1; additionally P7P is the device where a *step* (mitigation-table)
  shape is most likely — the 5-band design with two passes per band is exactly what
  separates hinge from step.

### T3 — A01 Core staircase dose-response (~4 h, parallel with T1/T2)

- **Protocol:** staircase with **4 bands: 28, 33, 37, 40** (fewer bands, bigger n:
  **45/band/direction** because per-launch sd is huge); max 2 hogs ever (1 GB RAM — more
  hogs induce Class-D memory pressure, a confound not a heater); launch cycle is slower
  (~30 s) so blocks run ~22 min.
- **Measured:** as T1; `thermal-headroom` will be absent (API 29) — expected, log the gap.
- **Predictions:** H-M3 (median slope ≤ +1 ms/°C, or clean null); H-X3 (inflation loads on
  cpu-ms not run-delay). *Falsifiers:* per H-M3/H-X3; if hot-band launches show elevated
  run-delay AND host idle-checks passed, that is a real scheduler-side thermal mechanism on
  MT6739 — report it, don't suppress it.
- **Confounds:** kswapd/memory pressure from hogs → hogs are CPU-only `dd if=/dev/zero
  of=/dev/null` (no buffers), 2 max, killed before launches; the device's chronic Class-A
  churn → quantified by the baseline bands and by judging on medians per methodology.

### T4 — Pixel 3 mechanism discrimination, traced (macrobenchmark C-H-H-C, serialized, ~1.5–2 h)

- **Protocol:** after staircases finish (gradle serializes across devices):
  1. Verify SDK pin (`embrace =` in ExampleApp catalog) + first-pass `emb-sdk-start`
     presence (device-gotchas pre-flight; a wrong-SDK campaign is unsalvageable).
  2. **C1:** cool pass, 25 iters `coldStartup`, battery ≤ 31 °C at start.
  3. Heat to battery 38–39 (hogs+screen, ~12 min per T0 calibration).
  4. **H1, H2:** two back-to-back hot passes (self-heating maintains band; if battery drops
     >1.5 °C between passes, 2-min hog top-up, quiesce, continue). Log temps per pass
     (fleet_campaign already does).
  5. Cool ≥ 30 min screen-off to ≤ 31 °C. **C2:** final cool pass.
  - Order C-H-H-C counterbalances pass-ordering/self-heating bias (methodology §ordering);
    the boundary comparison (C1-tail vs H1-head; H2-tail vs C2-head) is reported alongside
    headline medians.
- **Measured:** per-iteration delivered clock (freq tracks), per-section Running/wait
  thread-state (`variance_analysis.py`, `outlier_factors.py`), per-CPU residency,
  section-inflation ratios H/C, plus the same launches' telemetry decomposition
  (cross-validates the prod attrs against trace ground truth — feeds Q4).
- **Predictions (H-X1):** clock constant 2803 MHz in H; Running ×1.3–1.4 uniform with
  memory-bound sections ≥ compute-lean sections; wait flat; residency unchanged.
  *Falsifiers:* clock drop → clock-capping verdict; run-delay-dominated inflation →
  scheduler-side mitigation; lumpy section pattern → contention contamination (check hog
  kill discipline before concluding anything).
- **Confounds:** tracing overhead inflates short sections under load (device-gotchas) →
  mechanism calls rest on the WINDOW-level Running/wait split and freq tracks, with section
  ratios as corroboration only; the un-traced staircase (T1) is the artifact control.

### T5 — Sensor race / onset analysis (analysis-only, uses T1–T3 data; ~0 device time)

- **Protocol:** per device, per logged sensor: hinge-fit window~sensor on ascending-arm
  launches; score on descending arm (out-of-sample R², breakpoint estimate ± bootstrap CI).
  Rank sensors; report the winner + threshold as the device's trigger point. Also locate
  every `thermal-status` level transition on the temperature axis vs the fitted breakpoint
  (H-T3).
- **Predictions/falsifiers:** H-T1/H-T2/H-T3 as stated in §1.

### T6 — Pixel 7 Pro mechanism discrimination, traced (C-H-H-C; tail of Batch 1 IF ≥1.5 h remains before 07:00, else Batch 2 opener)

- **Protocol:** identical to T4 on P7P, hot band = battery 38 (or T0's ceiling). If only
  ~45 min remain, run the degraded 2-pass form **C-H with boundary comparison** and mark it
  provisional pending Batch 2 counterbalance.
- **Predictions (H-X2):** primary prediction = delivered-clock drop on mid/big clusters in H
  passes; alternative = constant-clock IPC loss (P3-style). *Falsifier of "thermal":* H≈C.
- **Confounds:** as T4.

### T7 — Detectability grading (analysis-only, pooled over T1–T3 + T4/T6 iterations)

- **Protocol:** §4. Train thresholds on ascending arms, test on descending arms (temporal
  split kills time-of-night leakage). Metrics per device: status confusion matrix; headroom
  ROC/AUC + calibration curve (headroom vs realized inflation); logistic combo
  headroom + init-cpu-share vs headroom alone (ΔAUC); FPR at TPR = 0.8.
- **Predictions/falsifiers:** H-D1/H-D2/H-D3.

### T8 — A14 staircase dose-response with compile-state control (Batch 2, ~4 h)

- **Protocol:** as T1 with bands 28/32/35/38/40, PLUS the Samsung control: after the single
  night-start install, **`pm compile -m speed-profile -f <pkg>`** and verify via
  `dumpsys package <pkg> | grep -A4 dexopt`-equivalent read (Read the dexopt state, assert
  `speed-profile`) BEFORE any launch; never reinstall mid-night (reinstall = SPEG parity
  flip = campaign poisoned — if a reinstall becomes necessary, re-run `pm compile` and
  restart the staircase from baseline). Status-transition logging at 5 s cadence during
  band transitions (feeds any hysteresis question left open by T1).
- **Predictions:** H-M4; two-state pass toggle is a macrobenchmark-context phenomenon and
  should NOT appear in these un-traced launches (device-gotchas) — if bimodality appears in
  am-start data anyway, split-analyze before fitting any curve.
- **Confounds:** SPEG (controlled above); devfreq unobservable (accepted, §H-X4).

### T9 — A14 mechanism arm, traced (Batch 2, C-H-H-C, ~2 h)

- As T4 on A14 with compile-state pinned per T8 and **even pass counts / matching-state
  comparison** (the traced pass toggle WILL appear here): effectively C-H-H-C twice if the
  toggle splits arms unevenly; judge on matching-state passes only.
- **Prediction:** open; decomposition + freq tracks + section skew give the verdict with the
  stated devfreq-blindness caveat.

### T10 — Recovery kinetics / governor hysteresis tail (each staircase device, 30 min, folded into T1–T3 tails)

- **Protocol:** after the final descending block: from hot-ish state (battery ~35), screen
  off, all heat off; every 2 min: wake, 1 launch, temps, screen off. 15 points.
- **Measures:** window recovery time-constant vs temperature recovery — do they track
  (soak physics) or does the window recover slower/faster than temperature (governor state)?
- **Prediction:** window tracks battery temp within one 2-min sample on P3 (consistent with
  soak-driven bus throttling). *Falsifier:* window stays inflated after temps normalize →
  sticky mitigation state; flag for Batch 2 status-cadence follow-up.

---

## 3. Staircase dose-response design (core protocol for T1/T2/T3/T8)

**Axis choice — heat-soak, not instantaneous silicon.** CPU/AP sensors collapse within
seconds of load removal; the mechanism (memory-subsystem temperature) follows the slow
thermal mass. Bands are therefore **defined and servo'd on battery temp** (stable over a
block, matches the established P3 dose-response axis), while every thermalservice sensor is
co-logged per launch so T5 can test whether a silicon sensor beats battery as predictor.
(P3 skin sensor excluded — constant 37.083.)

**Band structure:** 5 bands (A01: 4) ascending, then re-sample each interior band
descending. Every band except the top is measured twice hours apart → hysteresis check +
time-of-night counterbalance are the same design element. Band tolerance: center ±1.0 °C.

**Block = one band's measurement.** Launch cycle (scripted, per device):

1. `am force-stop <pkg>` (previous instance).
2. Read battery temp + thermalservice (one chained adb call). **Thermostat:** if battery <
   center − 0.3 °C → start N hogs (N from T0 lookup) for 10 s, kill them. If battery >
   center + 1.0 °C → insert 60 s screen-dim idle wait.
3. Quiesce: sleep 3 s after any hog kill; host idle-check (read `/proc/loadavg`, require
   1-min load < cores/2) — retry once, then log `quiesce_failed=1` on the launch rather
   than block forever.
4. Log all sensors + status (this is the launch's ground-truth temp record).
5. `am start -W` the ExampleApp launch activity; record TotalTime.
6. Sleep 4 s (init span closes, payload persists). Snapshot `scaling_cur_freq` (coarse
   clock probe, feature-detected).
7. Repeat until n launches collected in-band (launches outside band tolerance are logged
   but tagged `out_of_band=1`, and the block extends until n in-band launches exist, cap
   +50 % duration).

**Why this holds temperature:** battery/board mass moves ~1 °C per 2–4 min, so 10-s hog
bursts between launches trim upward drift without ever running concurrently with a launch —
the heater can never become a Class-A confound. Screen-max-brightness supplies contention-
free base heat for the upper bands; charging (physically connected all night) is constant
background. Silicon sensors will sawtooth (hog bursts + the launch itself) — that's data,
not error: T5 uses it.

**Band transitions:** up-steps: continuous hogs (+screen for top bands) with the watchdog at
60 s cadence, target = center + 0.5 °C overshoot (~5–10 min per 3 °C per T0). Down-steps:
hogs off, screen off (`stay_on_while_plugged_in 0`, keyevent 26), temps every 30 s until
center + 0.5, then wake (keyevent 224) and restore stay-awake. Prereq: lockscreen = None on
all bench devices.

**Contamination screen (analysis-side):** any launch with `init-run-delay-ms` above the
device's cool-baseline p95 is excluded from magnitude/onset fits (it was contended, not
merely hot) but RETAINED for the detectability set (prod sees contended launches too). This
keeps the dose-response curve a pure execution-speed measurement.

**Sample-size rationale:** P3: established effect ~12 ms over the range, per-launch sd
~4–8 ms → n=30/band gives band-median SE ~1–2 ms → 5-band slope resolved to well under
±0.3 ms/°C. P7P: total effect ~4–5 ms, sd 2–3 ms → n=30 adequate for hinge-vs-step
discrimination. A01: sd 20–30 ms → n=45 medians + quantile regression; power only for
slopes ≥ ~0.7 ms/°C on the median — stated as the design's detection floor.

---

## 4. Detectability grading design

**Ground truth must be independent of the attrs being graded.** Each launch carries:

1. **Condition label** (host protocol state): band + phase (asc/desc/baseline/recovery) —
   we KNOW the device was hot because we made it hot; recorded by the host, not the app.
2. **Independent temp record**: host-side thermalservice + battery reading taken pre-launch
   (step 4 above) — never the in-app attr.
3. **Outcome label**: `degraded = window > (device cool-baseline median × 1.15)`, baseline
   = pooled 28–31 °C bands. (Thermal detectability is about *outcome*: a hot device that
   isn't throttling isn't degraded.)

**Grading (T7), per device:**

- `thermal-status` (categorical): confusion matrix of status ≥ LIGHT / ≥ MODERATE vs the
  outcome label; report sensitivity/specificity per cut.
- `thermal-headroom` (continuous, API 30+): ROC over threshold sweep → AUC; calibration
  curve (headroom decile vs median window inflation) — prod wants a *dose* readout, not
  just a flag.
- **Combined model:** logistic on headroom + init-cpu-share (init-cpu-ms ÷ wall) vs
  headroom alone; ΔAUC tests H-D3 (the decomposition separates ran-slow from was-blocked).
- **Split discipline:** fit on ascending-arm launches, evaluate on descending-arm launches
  (same bands, different clock hours → temporal leakage controlled). Contended launches
  (quiesce-failed / run-delay-flagged) are included in evaluation as realistic negatives.
- **Cross-validation against traces:** T4/T6 iterations have BOTH telemetry attrs and
  perfetto ground truth — verify init-cpu-ms/run-delay-ms against trace thread-state before
  trusting them as classifier features (one-time validation, per outlier-taxonomy guidance).
- **Deliverable:** per device: AUC, FPR@TPR=0.8, recommended prod rule (e.g. "headroom ≥ X
  OR status ≥ MODERATE ⇒ tag thermally-degraded"), and the A01/API-29 coverage-gap note.

---

## 5. Prioritized overnight batches

**Batch 1 — tonight (Pixel 3 + Pixel 7 Pro + A01 only; A14 excluded).**
Highest information/hour: the three staircases run **in parallel** (am-start loops are
independent per device; no gradle after the single install), then one serialized traced
mechanism campaign on the flagship thermal device.

| Clock (approx) | P3 | P7P | A01 |
|---|---|---|---|
| 21:30–22:00 | Setup: verify SDK pin, build+install ExampleApp debug on all 3 (one gradle install run, serialized but quick), `pm compile` NOT needed (non-Samsung tonight), smoke-launch + payload-pull test per device, discard-3 launches | ← same | ← same |
| 22:00–22:45 | **T0** calibration | **T0** | **T0** |
| 22:45–02:15 | **T1** staircase asc+desc | **T2** staircase | **T3** staircase (runs to ~02:45) |
| 02:15–02:45 | **T10** recovery tail | **T10** tail | (staircase finishing) |
| 02:45–03:15 | cool-down to ≤31 °C | idle/cool | **T10** tail |
| 03:15–05:00 | **T4** C-H-H-C traced mechanism (gradle, exclusive) | idle | idle |
| 05:00–06:30 | — | **T6** C-H-H-C (full if time; else C-H provisional) | — |
| 06:30–07:00 | Final payload pulls + log collection all devices; restore brightness/stay-awake/battery service; leave devices cooling | | |

Wall-clock risk buffer: T3 runs long (A01 cycle time); T6 is the designated drop item.
Batch-1 outputs alone answer: magnitude curves + onset + sensor race for 3 devices (T5),
trace-confirmed mechanism for P3 (T4), P7P mechanism at least provisionally (T6), and the
full detectability grade for P3/P7P + status-only for A01 (T7).

**Batch 2 — next night (A14 returns; ~8 h):** T8 (A14 staircase + compile control, 4 h),
T9 (A14 traced mechanism, 2 h), T6 completion/counterbalance on P7P if provisional (1.5 h),
plus any T10-flagged sticky-mitigation follow-up (5 s status-cadence logging during a single
heat/cool cycle, 30 min).

**Batch 3 — refinement night:** fine-grained onset staircase (2 °C steps bracketing each
device's fitted breakpoint, asc+desc), out-of-night validation of the T7 prod rule on fresh
launches (the real test of detectability), and replication of any surprising Batch-1 slope.

---

## 6. Risks, abort conditions, and "done"

**Abort conditions (scripted, per device, independent):**

- **Battery ≥ 43.0 °C → hard abort** (kill hogs, brightness restore, screen-off allowed,
  `dumpsys battery reset`, protocol marked ABORTED-THERMAL, cooldown logging continues).
  Soft ceiling 42.0 °C: no up-steps, begin descent. Checked before every hog burst + every
  60 s while heating.
- Battery level < 30 % → `dumpsys battery reset` + 20-min charge pause in-protocol (logical
  unplug is never left on across blocks; physical charger connected all night).
- adb disconnect / device offline > 5 min → that device's script exits after writing state;
  others unaffected (each device = its own host process keyed by `ANDROID_SERIAL`).
- Gradle failure at T4/T6 start → skip traced arm, extend staircase-derived analysis; never
  debug builds unattended at 3 am (mitigation: APKs and one benchmark smoke pass are built
  and verified during the 21:30 setup window).
- Wrong-SDK symptom (no `emb-sdk-start` in T4's first pass) → quarantine the pass, stop T4;
  staircase telemetry is unaffected (it uses the installed debug app, verified at setup).

**Other risks:** flagship (P7P) can't reach top band → T0 branch handles (band dropped,
longest-hold extended); A01 hogs induce memory pressure → capped at 2, CPU-only pattern;
payload cache growth → pull-and-delete each block; ambient drops overnight → descending arm
+ repeated baseline bands absorb it; lockscreen/stay-awake misconfig discovered at 2 am →
checked at setup (wake, swipe-free launch verified per device).

**"Done" for the program:**

1. Per device: a window-vs-temperature curve with fitted shape (line/hinge/step), slope
   ms/°C ± CI on both battery and best-silicon axes, and the flat-region baseline.
2. Per device: an onset threshold + the named winning sensor with out-of-sample fit stats,
   and where thermal-status transitions sit relative to that onset.
3. Per device: a mechanism verdict (clock-cap / bus-throttle / scheduler / mixed) supported
   by the discrimination table's observables, with A14's devfreq-blindness caveat recorded.
4. Detectability report: AUC/confusion matrices per device, a recommended prod
   classification rule validated out-of-night (Batch 3), the API-29 coverage gap, and the
   telemetry-vs-trace cross-validation of init-cpu-ms/run-delay-ms.
5. All of it folded back into the skill references (methodology temperature-discipline
   section + outlier-taxonomy thermal row) and the living startup-testing-log artifact.
