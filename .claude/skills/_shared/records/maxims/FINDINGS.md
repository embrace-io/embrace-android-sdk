# SDK startup findings (bench)

Conclusions about Android SDK init from the benchmark fleet that are not per-campaign checks, and the
history of the maxims that are. Hand-curated: add a dated entry when a campaign establishes or overturns
something, and point at the run that did it. The current standing of every maxim is generated into
[MAXIMS.md](MAXIMS.md); this file is why it stands there.

This is the bench half of a pair. The production half lives in the go repository at
`tool/sdk-startup/FINDINGS.md`, curated the same way from ClickHouse data; the two tools measure the same
thing by different means, so they share this structure, the verdict vocabulary, the ledger shape and, where
the claim is the same, the maxim id. When a bench finding is generalised, read the production page first;
when a production maxim is contradicted, look for the bench mechanism here.

Devices are referred to by their reference-set keys. The fleet as of September 2026: a Tensor flagship
(`flagship-a`, API 35), a 2018 Snapdragon flagship (`mid-b`, API 31), a Samsung mid-tier Exynos (`mid-a`,
API 35) and a 1 GB Go device (`entry-a`, API 29), two vendors. Every number below is from the benchmark
build of the example app with the baseline profile applied unless it says otherwise; the run that produced
it is under `../campaigns/`, `../experiments/` or `../analyses/`, and each run's dated write-up is inside
that same archive rather than loose - this page is the curated form, the archive holds the working. The
living pages (`startup-testing-log.html`, `startup-external-factors-summary.html`,
`startup-version-evolution.html`) are the session-by-session record this page condenses.

## What init costs and where it goes

- **Init time is memory-bound work**: window = work ÷ (clock × IPC), and the term that moves is effective
  memory-system speed - concurrent CPU bursts, heat on the memory bus, core placement - not the delivered
  CPU clock, which is pinned at benchmark load on every device measured (four-device synthesis, August
  2026; `startup-analysis/references/interpreting-results.md`).
- **The tier gradient is an order of magnitude for identical work, and it is a silicon property.** Pooled
  August 2026 windows, median/worst: `flagship-a` 11.8/29.4 ms, `mid-b` 44.5/120 ms, `mid-a` 46.8/92 ms,
  `entry-a` 101.7/203 ms; the flagship's worst window beats the Go device's fastest median. At 9.2.0 the
  span is 8.0x (11.5/32.0/45.1/92.6 ms), at 8.3.0 it was 8.5x, so halving the SDK's work left the ratio
  untouched. Absolute milliseconds are release-scoped; ratios and shares are the durable form of a finding.
- **The section mix is stable across devices for the same code path**, so divergent shares mean different
  code paths, not different hardware. The same three sections lead on every device (`span-service-init`,
  `otel-tracer-init`, `persisted-config-load`, together 74-85% of the window); only the order changes on the
  flagship, where config decode becomes the largest share (35%) because OTel construction scales down
  with core quality faster than the decode does. The bench runs one host app, so its section mix is one
  app's; the production tool finds the mix follows the app (`start-first-session` 0-2% of init on one
  app, 30-60% on others).
- **`persisted-config-load` is the top cross-tier optimisation target**: bimodal by cached-file presence
  (a fast miss on the first launch after install, a CPU-bound binary decode afterwards: 22 ms on
  `entry-a`, 12 ms on `mid-a`, 8 ms on `mid-b`, 4 ms on `flagship-a`), ms-dominant on the low end and
  share-dominant on the flagship. The cost is ART verifying the serialisation stack (81% class-load and
  verify time on `mid-a`), and the compile state decides whether it is paid at all (see below). It is the
  largest direct child of `emb-modules-init` on every device at 9.2.0 (22% of the whole window on
  `entry-a`).
- **Startup improved monotonically 8.3.0 → 9.0.0 → 9.1.0 → 9.2.0 on every device**, 10 x 20 series,
  all 22 comparisons into 9.2.0 supported at the design floor (p = 5e-5): `flagship-a` −45.0%
  (20.90 → 11.50 ms), `mid-b` −26.5% (43.43 → 31.90), `mid-a` −40.4% (75.55 → 45.05), `entry-a` −47.6%
  (176.70 → 92.58, the largest absolute win at 84 ms). 9.2.0 is the largest single step on every device;
  8.3.0 → 8.4.0 is the one step with no measured effect. The tail moved with the median everywhere
  (p90 −28% to −39%, p95 −20% to −37%), so no median was bought by worsening the tail. Init cost had
  roughly doubled between 6.14 and 7.5, and config cost was born in the 7.9 era (`config-service-init`
  1.1 → 2.1 → 19.1 ms). `emb-sdk-start` existed in 6.14-7.9, vanished in 8.3-9.1 and returned in 9.2.
- **Most of the 8.3.0 → 9.1.0 window improvement was work moved, not removed.** Main-thread CPU stayed
  flat and whole-process CPU before TTID rose 6-11% (`entry-a`: window −39%, process CPU +11%). The
  correct statement is "start() blocks for less time at roughly constant total pre-TTID cost"; every
  window-only improvement claim must pass this re-attribution audit before it is credited as removal.
- **The 9.0.0 → 9.2.0 gain came from two sections on every device**, `config-service-init` and
  `post-services-setup`, which are siblings, not nested (0 of 48 traces): `flagship-a` −4.4 and −4.6 ms of
  an 8.1 ms gain, `mid-a` −13.8 and −13.6 ms, `mid-b` −7.3 and −8.1 ms, `entry-a` −20.2 and −30.7 ms.
  `mid-b` lagged the others on that step (−29% against ~−35%) and ~79% of its shortfall sits in
  `post-services-setup` alone (−67% against a peer median of −84%), while half its sections improved more
  than its peers - which argues against a runtime-wide "older ART" explanation. 19 of the ~60 `emb-*`
  sections lie outside the composed window and 9.0.0 → 9.2.0 is a rename boundary (25 minified names
  out, 19 literal names in), so cross-version section arithmetic needs the membership and containment
  maps first.
- **`emb-modules-init` grew across 9.0.0 → 9.2.0 (+16% to +36%) while the window shrank a third; it is a
  phase shift inside the rename boundary, not concealed cost.** The unnamed residual of the span explains
  only 4-12% of its growth; 9.2.0 moved config work earlier, into `modules-init`, and made the later
  blocking phases far cheaper. Net inflow scales with device slowness (+3.9 ms `mid-b`, +4.7 `mid-a`,
  +15.4 `entry-a`).
- **The Kotlin OTel engine is the largest measured software lever off the flagship**: −20.4% (9.2 ms) on
  `mid-a`, −17.7% (5.3 ms) on `mid-b`, −15.1% (14.5 ms) on `entry-a`, −4.3% (0.5 ms) on `flagship-a`,
  all supported. The saving tracks the absolute size of the OTel construction work removed (4.4 ms on the
  flagship, 59.3 ms on the Go device), which is why a flagship-only reading called it a minor lever and
  was wrong by 4x. It defaults off at HEAD, so it is stackable with 9.2.0's gain; its tail is unmeasured
  (the one attempt benchmarked a released SDK against itself) and its export parity is unvalidated.
- **The bench measured the restore path for a month without knowing it.** Relaunching every few seconds
  restores the persisted user session; production launches hours apart and creates one, and the create
  path in 9.2.0 resolved a kotlinx serializer at runtime on the main thread (~55 class loads with
  reflection). The returning-user arm on `mid-b` (session expired, config present, 50 launches) is the
  production shape: 9.2.0 window 35.33 ms with `start-first-session` 6.67 ms and 119 class loads inside
  it; the fix 30.40 ms, 2.50 ms, 49 loads - 4.2 ms of a 35 ms init (12%) on a compiled Pixel 3, ~2 ms of
  20 (10%) uncompiled. Production's median on-CPU share of 63% against the bench's 93-98%, and
  `start-first-session` at a third of init on the largest app, are this path. The cohort tap
  (`passN-cohorts.json`) exists so the two are never pooled again, and the trace-health class-load burst
  threshold is 80 (healthy create path ~50, regression ~120).
- **SDK init is a small slice of the app's cold start** on the bench app too (4-5% of an ~870 ms TTID on
  `mid-a`), and the bench app is light, so its share overstates production's 1-5%. TTID is version-
  invariant across the sweep and cannot arbitrate window claims (minimum detectable effect ~80 ms
  against ~15 ms effects).

## Hardware and device state

- **Install-time compile state is the strongest software factor, up to 2x on the window and ±45% on
  TTID, and there are three states, not two.** On `mid-a`: profiled `speed-profile` 29.3 ms (best),
  `verify` 35.6 ms, unprofiled `speed-profile` 51.3 ms (worst) - the profile removes the class-verify
  cost of the config decode entirely (X9d). The same code in `verify` state pays 0% class work in the
  decode; unprofiled `speed-profile` pays 34%.
- **Samsung builds toggle compile state per reinstall** (`speed-profile` with `reason=install-speg`
  alternating with `verify`), so passes alternate ~38 ↔ ~51 ms with no tracing attached and the whole
  app's TTID moves +45% (597/866/598/863 ms across four install cycles of plain `am start -W`). It is
  installer-side state keyed to APK bytes: the alternation survives idle gaps, an 80-minute break, full
  battery off charge, and eight same-APK reinstalls with zero launches between them (X6), and it appears
  identically in every SDK version from 8.3.0 to 9.1.0. The inflation is asymmetric - pure-CPU sections
  ~1.1x, block-and-resume sections ~2x (`config-service-init` 2.40x, `post-init` 2.10x) - which is how
  to recognise it in a dataset. Compare only matching-state passes; on Play, cloud profiles
  (`install-dm`) make the compiled state the norm, so the baseline-profile arm is the
  production-representative one. The production tool's low-mid exception (Samsung A36/A27 1.5-2x slower
  than other phones on the same chip) has this as its leading mechanism hypothesis, testable now that
  `art-compile-filter` and `app-image-at-init` are exported.
- **The compile-state lever is absent on the flagship** (X24: a clean null, not an inversion), neutral on
  ART 12 (`mid-b`, ABBA re-test), and halves the cost on modern mid and entry tiers (window p50 −42-47%,
  max −47-52% on `mid-a`, `entry-a`, `flagship-a` in the first A/B). The "any AOT versus interpreter"
  boundary is the discriminator, not which profile method was selected.
- **Android 10 compiles the bundled baseline profile at install time but writes no app image**: a fresh
  install on `entry-a` reads `speed-profile` / no image where every other device reads `verify` / no image,
  and only an explicit compile produces `base.art`. The two attributes are recorded separately for exactly
  this case; 12 of 12 launches across four devices agreed with `dumpsys package`.
- **Heat throttles the memory bus before the CPU clock on some silicon.** `mid-b`: cool-arm medians rose
  31.9 → 42.2 ms while self-heating 29.8 → 39 °C with the delivered clock at 2803 MHz in every window
  (hot arm 49.7-61 ms at 2773-2799 MHz) - a ~50% IPC collapse at constant frequency, invisible to
  cpufreq; outlier rate 5/250 cool against 54/250 hot. `flagship-a` is indifferent below ~34-35 °C and
  then caps its big cluster (2850 → 1836 MHz) and loses 31-49% of launch time; +10% on the median from
  ≤33 °C to ≥38 °C, hysteresis-free. The platform thermal status lags that onset by minutes (four
  rounds on the flagship) and flips on `mid-b` at exactly 39.0 °C with no slowdown at all, so the same
  status means opposite outcomes on two vendors; headroom leads (+0.63 within-phase rank correlation
  with the window on the flagship, +0.55 controlling for memory) but is a per-device scale (79 meant
  severe slowdown on the flagship, 81 meant none on `mid-b`). Battery temperature, not the CPU sensor,
  is the axis that follows the mechanism.
- **Concurrent system-process CPU is the strongest outlier correlate on every device, and it is causal.**
  `system_server` CPU rate correlates r = 0.65-0.73 with within-pass inflation on `mid-a` (baseline
  0.30 CPU-ms per window-ms; the seven worst outliers ran at 1.17-1.62); induced `system_server` churn
  inflated whole cold launches +40% on `mid-a` (758 → 1058 ms) and +24% on `flagship-a` (208 → 258 ms)
  with instant recovery. The two most extreme windows overlapped a `Background young concurrent mark
  compact GC` in `system_server` (13 of 13 flagged windows carried a genuine collection, 0 of 39 control
  windows carried any) - heap copying at bus speed is the perfect bandwidth hog, costing the in-order
  A55 main thread 25-40% of its IPC at an unchanged 2.0 GHz. Main-thread waits stay under 3 ms
  throughout: bandwidth theft, not scheduling. Its cast is device-specific: `mid-a` = `system_server` GC,
  `mid-b` = dex2oat, Play and GMS bursts (to 413 ms CPU), `entry-a` = GMS sync (to 245 ms), kswapd and
  its own GC.
- **Own-process GC during init occurs only on the 1 GB tier, where it is real and concurrent.** On
  `entry-a` a collection is present in 79% of outlier windows against 20% of controls (45/57 vs 28/143,
  3.9x enrichment; the original 11 hand-picked windows reproduce 9/11 to the millisecond), every
  collection is `Background concurrent copying GC` with mutator suspension ~1% of the window, and
  collection wall time is 27-149 ms (median 78). Every "some, small own GC" flag on ART 12+ devices was
  fabricated by a case-insensitive `*GC*` match on obfuscated class-load slices (`Lgc;`) and is
  withdrawn; above the 1 GB tier a zero is the device, not the detector.
- **Starvation is rare on a quiet bench** even under induced load; 0 of 250 iterations were contended in
  the corroboration campaign and the main thread's runnable wait stays under a few milliseconds while
  concurrent CPU inflates the window through the memory bus. Production finds starvation is a chip
  property (5-10% of inits on Snapdragon 8 Gen 2, under 1% on Exynos). When it does happen it has its own
  signature (14.8 ms of wait spread over seven CPUs in one slow iteration), distinct from the
  slow-execution signature (all the excess in Running time, one CPU, no wait).
- **The first launch after install is install aftermath, not missing profiles, and it is not uniformly
  slow.** iter000 runs with 2-3x baseline concurrent CPU (the install pipeline itself: `vending` 55-80 ms,
  `installd` 17-43 ms, `artd` 48 ms, `system_server` to 75 ms) and settles by iter002; on fast tiers the
  skipped config decode outweighs it and iter000 is often faster, only the entry tier nets slower. This is
  why `first-launch` is a directional maxim here, why a contradiction on a flagship is expected, and why
  the production tool should split its first-launch cells by `persisted-config-load`. In production,
  segment `version_startup_counter == 1` as its own cohort.
- **Core placement is a correlate, not a cause.** Cluster-0-majority iterations run +1.5 to +4.0 ms slower
  on `mid-a` in every pass (pooled r = 0.39, n = 250), but the four most extreme outliers ran with 0%
  cluster-0 residency; the interrupt-theft mechanism is refuted (normalised IRQ rate inverts to a
  negative correlation) and the gap survives busy and quiet strata alike. The mechanism (cache or
  memory locality) is open. The SoC's eight cores are identical A55s in two cpufreq policies (cpu0-3,
  cpu4-7): an unverified "big cores are cpu6-7" split turned a real r = 0.63 into a meaningless −0.26.
- **Memory pressure is a low-RAM-tier class only**: swap, kswapd and own-process GC appear in `entry-a`
  windows and nowhere else. The host app's SharedPreferences file is not a factor in the observed range,
  but the mechanism is real: a 50k-entry file moved `mid-b`'s window 102.9 → 690.2 ms (6.7x), landing in
  the first getter (`user-session-orchestration-init`, 388x), not in `key-value-store-init`, which is
  blind to it. Both entry count and byte volume drive the parse; prewarming is free when idle and saves
  27-48 ms when not, capped at ~66 ms of head start.
- **Cleared as factors**: the kernel-visible CPU clock (2002 MHz in all 450 `mid-a` iterations, fast and
  slow passes alike), charging state, core count (every tier ships eight), binder transactions inside
  the window (zero), lock contention (< 0.2 ms), swap and available memory across fast and slow passes
  (static). Transient clock dips do drive mid-tier outliers where clocks vary (`mid-b`, r = −0.55).

## Outliers

- **Within-pass outliers are three phenomena, not one** (34 slow iterations over 250 on `mid-a`): Type A,
  system pressure with concurrent GC (16, including every extreme); Type B, main-thread flash IO with
  D/io time 4.5-11 ms against a 2.4 ms baseline (7); Type C, mild cluster-0 residency, +4-6 ms,
  mechanism open (11). Every extreme outlier is pure execution speed - a 66.7 ms window was 63.9 ms
  Running, 0.4 ms wait, one CPU.
- **A rare lab iteration is a hypothesis about production**, not noise: decompose its sections and
  deobfuscate the classes the init thread loaded before filing it as environmental. The user-session
  write was found this way after being filed as environmental in several campaigns, and a drill-down
  into the two absolute-worst windows named `system_server` GC compaction where the fleet-wide
  correlation only gestured at it.
- **`power-service-registration` stalls on binder in every campaign pass** (stalls over 10 ms in 9 of
  250, maximum 38.4 ms; 76-87% of the section is binder wait, ~21% of the window when it fires), and the
  native-library and signal-handler sections carry a stable 2-3 ms IO cost. `snapshot-session` peaks at
  9-15 ms. These sit at or past the window's edge, which is why they fluctuate TTID more than the window.
- **The sections that carry variance are not the sections that carry time.** Per-section correlation
  with the window on `mid-a`: `modules-init` 0.97, `persisted-config-load` 0.75, `span-service-init`
  0.69, `otel-tracer-init` 0.65; `config-service-init`, `essential-service-init`, `post-init` and
  `post-services-setup` have real durations and ~0 correlation. Judge regressions on fast-pass medians.

## Telemetry attributes

What the fleet validation of the shipped `sdk-init` attributes established, per attribute; accuracy and
predictive usefulness are different properties and were graded separately.

| attribute | standing | evidence |
|---|---|---|
| `init-cpu-pct`, `init-run-delay-pct` | accurate | within 1-3 and 0-2 points of trace truth over 54 graded iterations, across 0-52% contention; run-delay is the only app-readable device-wide pressure signal |
| `init-gc-count` | accurate as a lower bound | 30 of 30 real collections on `entry-a`, median error 0-1, never invents; the GC-time attribute was dropped (collections are concurrent, pauses ~1%) |
| `init-disk-read-kb` | discriminating | separates cold from warm on all four devices (1136 → 0 KB `entry-a`, 116 → 0 `mid-b`, 68 → 0 `mid-a`, `flagship-a`), tracks the filemap ground truth launch for launch; precision unverifiable unrooted |
| `init-maj-faults` | removed | saturates at both tier extremes (0 on every `entry-a` launch that read 1136 KB, because readahead made the faults minor; 160-185 flat on the flagship) and predicts nothing (rho +0.10, n = 319) |
| `mem-available-pct`, `low-memory` | accurate | 6 of 6 launches inside the `MemFree + Cached` bracket under induced pressure on `entry-a`; `low-memory` first fired 260 KB below the `homeAppMem` threshold; zero false positives elsewhere |
| `seconds-since-install/update/boot` | accurate | boot clock is `uptimeMillis` (awake time), checked against `/proc/uptime`; `dumpsys package` timestamps must be parsed by field name (position differs between API 31 and 35) |
| `thermal-headroom-pct` | the only attribute shown to predict a slow init | within-phase rank correlation +0.63 with the window over 319 paired launches on `flagship-a`, +0.55 controlling for memory (~+5.5 ms across terciles on a 91 ms median); per-device scale, never comparable across models; absent below API 30 |
| `thermal-status` | presence-only | mirrors the platform integer but lagged a 31-49% slowdown by four rounds on the flagship and flipped with no slowdown on `mid-b`; confirmation, never detection |
| 17 `*-duration-ms` sections | accurate | 78 of 85 comparisons within ±1 ms, median bias +0.01 ms (one device, five launches) |
| `prefs-first-read`, `prefs-file-bytes` | accurate | sleep time within 0.4 ms, byte count exact |
| `art-compile-filter`, `app-image-at-init` | accurate | 12 of 12 launches agree with `dumpsys package` across four devices and three compile states |
| `psi-cpu-some-avg10` | removed | 0 of 54 launches populated: AOSP grants `/proc/pressure/cpu` only to `lmkd` and `system_server`, never any app domain, since Android 10 |

Three attribute populations must be kept apart when correlating against the window: decompositions
(`*-duration-ms`, `init-cpu-pct`, `init-run-delay-pct`) contain the window by construction; in-window
counters (`init-disk-read-kb`, `init-gc-count`, `prefs-first-read`) accumulate during it, so a longer
window mechanically permits more (a raw interrupt count read +0.64 against duration, its rate −0.15);
only exogenous state supports a prediction test. Correlations are rank-based and within-phase.

## Method

- **The pass, not the iteration, is the unit of evidence.** Iterations within a pass share install,
  thermal and thread state (design effect 1.5-6), so pooled p-values are anti-conservative by that
  factor. The standing design is 10 passes x 20 iterations per arm; it moved the permutation floor from
  0.029 (4 passes) to 1.1e-5 and cut the bootstrap half-width on the arm median 45-95% at the same
  launch budget. Report cluster-bootstrap CI + cluster-permutation p + effect size; CI-excludes-zero is
  the gate ("suggestive" when the two disagree); report the minimum detectable effect beside every null.
- **Run shape is series-defining.** Changing it moved medians device-dependently (`mid-b` −10 to −19%,
  `mid-a` +10 to +17%), and within-pass drift explains only 7-37% of that; treat `run_shape` like
  `instrument`. A follow-up meant to explain a difference must use the exact shape of the series that
  defined it: the 4-pass attribution run inverted the device ordering the 10-pass series had established.
- **The variance split is per cell, not a fleet constant.** "93-99% between passes" is the arm-estimate
  split measured on `mid-a` at 4 x 50; the flagship at 9.2.0 gives raw ICC 0.027 and a 36/64 split on
  the same data. Compute it at ingest for every arm.
- **A quiet campaign cannot explain variance**: with headroom moving 2.8 points and memory 0.9 over 200
  launches, no attribute correlated with anything, by design. Factors must be induced, not waited for.
- **Verify every leg against the device crash buffer, never the harness exit code**: one device
  produced 200 green, complete, plausible traces from a process that crashed on every launch (a pinned
  OTel API overriding the SDK under test). Refuse pass-level analysis when the trace count is not
  passes x iterations, in both directions - the over-long direction looks like unusually good data.
- **A null result is meaningful only if the arms provably differ.** A config-flag A/B benchmarked a
  released SDK against itself for 2.5 hours because the app's version pin was never repointed; every
  check that was run passed. Android builds are not byte-reproducible (275 vs 11,790 dex byte differences
  for the same pair), so the gate is trace-level: the OTel construction sections must move 25%+ while a
  control section holds within ±7%, checked on the first leg pair, and the arm's level should match the
  store's record for the version it claims (61.21 ms landed on 9.1.0's 63.02, not 9.2.0's 45.05).
- **Reduce for the question after next.** Two campaigns kept medians only and purged their traces; the
  tail question was then unanswerable rather than unanswered. Keep the per-launch array and the section
  rows; and a reduction is not a backup - a restart that re-archived over a pruned store destroyed
  1,600 measurements.
- **Structure and level need different samples.** Containment is stable at four traces; duration medians
  are not (a child came out longer than its enclosing window when both were read from the first traces of
  pass 1). Never sum section percentages without the containment map.
- **Absence is a capability, not a measurement.** `/proc/pressure/cpu` and a foreign process's
  `/proc/<pid>/io` read fine from `adb shell` and never from an app; `init-disk-read-kb` graded well on
  every bench device and is near-dead in production. Verify app-side reads with `run-as`, request ftrace
  events explicitly (the `disk` category does not include filemap events), read the live block of
  `dumpsys thermalservice` not the cached one, and check attribute presence in prod before trusting a
  bench validation.
- **Interleave cheap toggles, block version arms.** A runtime setting or `pm compile` alternates ABBA;
  an SDK version switch is a rebuild and a byte-different install that drives the Samsung compile-state
  machine, so version arms run block-sequential with ten independent installs each (which averages the
  ±20% parity swing to ~3%). Report ABBA block deltas beside the aggregate.
- **Device-side processes outlive the host harness.** Killing a campaign left three `tracebox` tracers
  holding the ftrace buffer for 40 minutes (every later trace silently lacked sched rows while perfetto
  exited 0) and filled `/data` to 96%. Teardown verifies absence on both sides; every one-time step
  needs an idempotency guard because an unattended campaign will be restarted.
- **The verification tap costs nothing measurable in `startup` mode** (window +0.38 ms, CI spans zero;
  TTID +2.3 ms, CI spans zero; 400 launches on the flagship) but its timer flush never fires under
  macrobenchmark (0-1 of 50); it flushes on the root startup span's end plus 150 ms grace, the logcat
  buffer is raised to 16 MB, and the trace-to-span join is positional per leg and refused on any count
  mismatch. Tap state is a recipe field.

## Refuted and superseded

Kept so the same theory is not rediscovered.

- **"Cluster 0 is the slow cluster"**: the four most extreme outliers ran entirely on cluster 1.
- **Memory-interconnect DVFS as the pass toggle**: unobservable unrooted and unnecessary once X2/X6 named
  the compile state.
- **The pass toggle as a tracing or measurement artefact** (E14): reversed by E15 (perfect alternation in
  plain untraced launches, +45% TTID) - it is real device state.
- **The compile-state self-oscillator**: refuted by X6 (alternation with zero launches between installs;
  the installer, not the app, holds the state).
- **The `mid-b` baseline-profile inversion** (+43%): an ordering and thermal artefact; ABBA re-test neutral.
- **Own-process GC on ART 12+ devices**: 100% fabricated by case-insensitive `*GC*` matching.
- **`init-maj-faults` and `psi-cpu-some-avg10`**: shipped, graded, removed.
- **Section-share drift as a thermal-versus-warm-up discriminator**: fails in both directions even after
  FDR correction; the sign of the `running_ms` slope is the surviving mechanism signal (positive =
  thermal on `mid-b`, negative = warm-up on `mid-a`).
- **The Kotlin engine as a "minor lever"**: a flagship-only reading, wrong by 4x off the flagship.
- **"Older ART generation" as the reason `mid-b` gained less across versions**: phase-specific, not
  runtime-wide.
- **Interrupt theft as the cluster-0 mechanism**: normalised IRQ rate correlates negatively.
- **Device load "saturation" as the cause of `entry-a` campaign failures**: intermittent ddmlib install
  timeouts; a reboot changed nothing and cost a boot-epoch discontinuity.
- **The 4 x 50 → 10 x 20 shift as "drift"**: drift explains 7-37% of it; the cause is open and
  confounded with run date.
- **The engine-tail campaign (X37)** and its "no engine effect at 9.1.0": void, measured the wrong SDK.

## Open questions

- Why `mid-b` gains less than its peers in `post-services-setup` across 9.0.0 → 9.2.0.
- The Type C mechanism (+4-6 ms when the main thread sits on cluster 0 of a homogeneous SoC).
- The Kotlin engine's tail (p90/p95) and whether the flag was inert at 9.1.0 or merely uninjected; one
  propagation-gated leg decides it.
- Whether the compile-state toggle is the production Samsung low-mid exception (needs
  `art-compile-filter` volume in prod).
- The run-shape level shift (X28, interleaved 4 x 50 and 10 x 20 on one device).
- Whether tap overhead is free on the entry tier and in `all` mode.
- Storage class (eMMC/UFS) as a tier dimension: no variance in this fleet.
- The persisted-config decode overlap (P1): the largest open code lever, 18-35% of the window on every
  device, blocked on a prototype.
- Predictive power of every attribute except thermal headroom is untested, not negative.

## Deferred maxims

Conclusions above that could be scored on every campaign once the datasets carry the input. Thresholds
are the ones the evidence supports; each needs a definition in both toolchains, a fixture and a
FINDINGS entry when adopted.

| candidate id | check | input needed |
|---|---|---|
| `tree-holds` | section containment from the traces is the documented tree on every trace (`modules-init` contains config/essential/otel/span; `post-services-setup` is a sibling) | per-trace section tree in `passN.json` |
| `sdk-share-small` | SDK-init window is under ~10% of TTID on every device | TTID per iteration in `passN.json` |
| `thermal-pressure` | window rises with battery temperature at constant delivered clock on the affected devices; flagship flat below 35 °C | per-pass temperature and `eff_mhz` in `passN-factors.json` |
| `class-load-burst` | class loads inside `emb-start-first-session` under 80 on the create path | class-load counts per section (trace-health) in the campaign output |
| `section-mix-stable` | the top-three section identities and their shares stay within a few points of the device's own record; a rank change is a code-path change | already available: `passN.json` section medians + the longitudinal store |
| `install-aftermath` | iter000 (and 001-002) carry over 2x the pass's steady-state other-process CPU rate; segment rather than fail | already available: `passN-factors.json` |
| `moved-not-removed` | a version step whose window shrinks must keep main-thread and pre-TTID process CPU flat, else it is re-attribution | pre-TTID CPU per iteration |
| `variance-split` | ICC and the between/within arm-estimate split per arm, stored, with a "quiet campaign" flag when no admissible attribute's pass-mean range exceeds ~5% | attribute-joined datasets (tap on) |
| `leg-valid` | trace count == passes x iterations, zero app-process fatals in the crash buffer, level within the store's record for the claimed version | campaign log + store |
| `propagation-gate` | for any A/B, OTel construction sections move 25%+ while `otel-module` holds ±7% on the first leg pair | section medians per arm |
| `running-slope` | sign of the within-pass `running_ms` slope matches the device's record (positive `mid-b`, negative `mid-a`) | `passN.json` |
| `binder-registration` | `power-service-registration` stalls over 10 ms in some iteration of every pass | section durations per iteration |

## Maxim history

Dated changes to the maxims defined in `embrace-analysis-maxims` (`tools/startup maxims`); the current standing is
in MAXIMS.md.

- **2026-09-04** — First ten maxims written from the August campaigns and the hypothesis tests H1-H4,
  adopting the production tool's ledger: `off-cpu`, `scheduler-wait`, `starved-enriched`, `gc-rare`,
  `first-launch` (directional, expected to contradict on fast tiers) shared with production;
  `concurrent-cpu` (the bench's proven-causal outlier class), `config-fast-path`,
  `compile-state-toggle` (device-specific), `restore-vs-create`, `cpu-closure` bench-only. Scored from
  the per-pass datasets a campaign already writes. Deferred to a second round because the datasets do
  not yet carry the inputs: `tree-holds` (section containment), `sdk-share-small` (needs TTID in the
  dataset), `thermal-pressure` (needs per-pass silicon temperatures), the class-load burst check (needs
  trace-health with class loads in the campaign output).
- **2026-09-04** — Ledger seeded with the nine August 2026 campaigns from the analysis records (four
  devices, a 9.2.0 snapshot build, default and cool arms; `tools/startup records rebuild-ledger`
  reproduces it). Two rules were
  corrected on that data before seeding: own GC counts only from 1 ms, because datasets produced before
  `outlier_metrics.sql` gained its GLOB guard carry sub-millisecond `Lgc;` false matches on every
  iteration; and every candidate factor is normalised by the window, because raw other-process CPU
  correlates with slowness by construction (the idle task is excluded outright). Standing after seeding:
  `off-cpu` and `gc-rare` accepted on every device; `scheduler-wait` accepted (confirmed on the two
  mid-tier devices, undetected on the flagship and the Go device); `concurrent-cpu` under review (3.3x,
  2.7x and 2.2x on the mid and entry tiers, 0.3x on the flagship's warm arm, whose slow iterations were
  post-idle clock ramp rather than churn); `first-launch` **refuted on the bench** - iter000 was faster
  than the rest on three of four devices, as the config fast path predicts, and slower only on the 1 GB
  device, which is exactly the disagreement with production's accepted `first-launch` worth keeping
  visible; `config-fast-path` under review because it caught the three known poisoned-uninstall passes
  on the Go device and one suspect first pass on the flagship; `compile-state-toggle` held on the Samsung
  device only (3 of 3 campaigns) and failed on the three others, which is the device-specific finding;
  `starved-enriched` refuted on its single test (2.1x on the Go device, the only device with any starved
  iterations) - the production floor of 3x is not met on the bench, where starvation is rare;
  `restore-vs-create` and `cpu-closure` untested until a campaign with the cohort tap is scored. Top
  candidates: `mem_available` (4 cells), `eff_mhz`, `system_server` and `surfaceflinger` CPU shares
  (3 each), `D+io` share on the flagship.
- **2026-09-05** — The ledger, its pages, the longitudinal stores, every campaign's datasets, the
  analysis summaries and the living-document sources moved into the committed records root
  (`_shared/records/`, see its README); `maxims score` now keeps the scored campaign's datasets there
  automatically. The findings above were curated from every dated document and experiment directory
  that had lived only on one machine (the version sweeps X25-X36, the attribution runs X34/X35, the
  engine comparisons X32/X33/X37, the attribute-grading experiments, the thermal and outlier
  programmes, the first-session fix, the tooling incident log), so nothing that was learned depends on
  a directory git does not track. The Deferred maxims table was written from the same pass.
- **2026-09-05** — Ten more campaigns scored, all on `mid-b` except the Go device's four-pass mini
  campaign, bringing the ledger to 19 runs; nine of them had only traces, so their `passN.json` and
  `passN-factors.json` were produced from the traces before those are lost (the port validation runs,
  the four user-session arms of the first-session fix, the paired Python/Kotlin runs). The ledger is now
  rebuilt end to end by `tools/startup records rebuild-ledger`, which scores every run named in
  `maxims/ledger-runs.json` with that entry's cell overrides.
  Standing unchanged in status, sharper in evidence: `off-cpu` 5 confirmed / 0 contradicted, `gc-rare`
  13 / 0 on three devices, `concurrent-cpu` and `config-fast-path` under review, `first-launch` and
  `starved-enriched` refuted, `compile-state-toggle` device-specific (the three 9.2.0 `mid-b` passes
  ran 30.5 → 30.5 → 30.8 ms, a clean non-toggle). Three scoping limits surfaced and are left as
  rules to encode rather than ledger noise, all three of which were encoded the same day (see the next
  entry): (1) `config-fast-path` presumes a settled pass, so a
  `new-user-session` arm that runs `pm clear` before every launch contradicts it trivially (iter000
  2.1 ms against 1.8 ms) and should be n/a there; (2) `restore-vs-create` compares cohorts within a
  run, but the cohort arms are single-cohort by design (49 created / 0 restored, or 1 / 48), so it
  needs a cross-cell comparison of matching device, version and compile state (the fix's evidence,
  6.67 ms against ~1 ms, lives in the first-session RESULTS document); (3) `cpu-closure` needs
  `init-cpu-pct` and `init-run-delay-pct` in the tap capture, which the `startup` tap mode did not
  carry in these runs. Fifty-iteration single-pass runs score `thin` on every slow-iteration maxim
  (0-3 slow iterations); the maxims need the multi-pass campaign shape to speak.
- **2026-09-05** — **The three scoping gaps are closed, and the Python implementation is deleted.** A
  maxim now reports `n/a` where the arm's own design removes the comparison, instead of blaming the SDK
  for the harness: `config-fast-path` is n/a on any arm whose benchmark method clears app data before
  every launch (the method is named in a list in the code, because guessing from the data cannot
  separate "every launch took the fast path" from "the decode is cheap here"), and `restore-vs-create`
  is n/a on a single-cohort arm, which no number of extra launches can fix - that comparison lives
  across the matching cells of two arms. `cpu-closure` was blocked on its inputs rather than its rule,
  so the cohort tap now captures `init-cpu-pct` and `init-run-delay-pct` alongside the session
  attributes; runs recorded before that stay n/a and the next campaign grades it. Rebuilding the ledger
  over all 19 campaigns moved two spurious verdicts off the books: the first-session `pm clear` arms no
  longer contradict `config-fast-path` (its remaining contradictions are the genuine poisoned-uninstall
  passes, so it stays under review), and the two cohort-pinned arms no longer read `thin` on
  `restore-vs-create`, which is now honestly `untested`. A run with a thin-but-real second cohort still
  reads `thin` - the paired Pixel 3 runs, at 1 created against 48 restored, are not the same thing as an
  arm with none. Deleted with the Python: 26 scripts, 5 SQL files (the Kotlin ships its own as JVM
  resources) and the three golden producers, which imported the modules that went. Every golden stays
  and changes meaning: it no longer proves agreement with a second implementation, it pins the
  behaviour the port was accepted against, so a golden that moves now means the Kotlin changed. The two
  records-maintenance scripts followed the same day, as `tools/startup records pack` and
  `records rebuild-ledger`, so no Python remains; the Kotlin rebuild was checked by diffing its ledger
  against the Python's, which matched apart from the timestamps. The rebuild's run list now lives in
  `ledger-runs.json` beside the ledger, so adding a campaign is a data edit rather than a code one.
