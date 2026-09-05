# Startup outlier investigation — multi-device experiment plan (2026-08-11 evening)

**Objective:** identify what causes slow SDK-init windows — especially the extreme outliers —
by cataloguing on-device factors external to SDK code, then prove/disprove the resulting
theories across three devices and a hot-vs-cool condition pair.

**Branch:** `EMBR-13523/hho/startup-profiling` · SDK 9.2.0-SNAPSHOT via mavenLocal ·
`StartupBenchmarks.kt` temporarily at `iterations = 50` (restore to 20 when the cooldown
campaign finishes).

**Living docs (updated as results land):**
- Testing log (scoreboard + test-by-test entries): `claude-output/startup-testing-log.html`
  → https://claude.ai/code/artifact/c8ef75b3-4c83-4335-8b9a-3776699266a0
- Variance deep-dive: `claude-output/startup-variance-analysis-2026-08-11.html`
  → https://claude.ai/code/artifact/f5761a2e-9a72-4709-937c-52c0146112b4

## Devices

| device | serial | SoC / cores | OS | role |
|---|---|---|---|---|
| Samsung A14 (SM-A145M) | <mid-a> | Exynos 850, 8×A55 @2.0 (2 clock domains: cpu0-3, cpu4-7) | 15 | primary; all original findings |
| Pixel 3 (blueline) | <mid-b> | SD845, 4×Silver@1.77 (cpu0-3) + 4×Gold@2.80 (cpu4-7) | 12 | generality: big.LITTLE, varying clocks, HOT tonight (41 °C) |
| Samsung A01 Core (SM-A013G) | <entry-a> | MT6739, 4×A53 32-bit @1.5, single cluster | 10 (Go) | extreme low end; API-29 floor check |
| Pixel 7 Pro (incoming) | TBD | Tensor G2, 2×X1 + 2×A78 + 4×A55, 3 clock domains | 13+ | modern flagship: does any of this matter on current hardware; capacity-annotated traces |

## Theories under test (HISTORICAL — state before this round; final verdicts live in the
## "Synthesis" and "Root-free follow-up" sections below and in the testing-log scoreboard)

- **T-pressure** (was T-GC): concurrent system_server/other-process CPU bursts degrade window
  IPC via memory-bus contention at pinned CPU clocks; ART heap compaction = biggest burst
  source on the A14. Status: corroborated correlate (r=0.73; GC sufficient 13/13) — needs
  causal + cross-device test.
- **T-MIF**: A14 pass-level fast/slow alternation = memory-interconnect (MIF/bus) devfreq
  state, invisible to cpufreq. Status: leading theory, untested → devfreq poller now unblocked
  (A14 exposes /sys/class/devfreq/17000010.devfreq_mif).
- **T-clock (per-device)**: window speed tracks delivered CPU clock where clocks vary.
  Refuted on A14 (pinned 2002 MHz); CONFIRMED on Pixel pass 1 (r=-0.71).
- **T-thermal/charging** (user hypothesis): hot and/or charging devices run slower — not
  necessarily via throttling; maybe governor/charging policy. Test = hot-vs-cool rerun.
- **T-typeC**: mild +4–6 ms outliers track cluster-0 residency on the A14 at equal clocks;
  mechanism open (softirq proxy too small; needs irq atrace).
- **T-IO (Type B)**: flash IO stalls (D/io 4.5–11 ms); function attribution impossible on A14
  (kptr_restrict); Pixel dex2oat storm suggests install-triggered IO contention variant.

## Experiment matrix

| # | experiment | device | status | results location |
|---|---|---|---|---|
| E1 | 5×50 baseline campaign | A14 | DONE | `claude-output/campaign-2026-08-11/` (verdicts.txt, factors-report.txt, per-pass jsons/txts) |
| E2 | Tier 1: GC quantification, Type-C probe, blocked-reason | A14 traces | DONE | `campaign-2026-08-11/t1-gc-quantification.txt` |
| E3 | 5×50 campaign (hot arm: 41 °C) | Pixel | DONE — no A14-style alternation; ambient-load drift; dexopt-storm extremes; clock r=−0.55 within passes | `claude-output/pixel-campaign-2026-08-11/` |
| E4 | MIF/INT devfreq poller | A14 | DEAD — SELinux blocks shell AND perfetto sys_stats devfreq on this build; superseded by E14 discriminator | tooling note in testing log |
| E5 | 4×50 instrumented campaign + 5-min idle gap (pass-flip isolation) | A14 | DONE — 38.7→49.4→[gap]→38.7→49.3; gap did NOT perturb; phase persists across breaks | `claude-output/a14-campaign2-2026-08-11/` |
| E6 | 1×50 shakeout (Go/API-29 floor) | A01 | DONE — floor works; window ~108 ms; own-GC/swap/IO outlier classes | `claude-output/a01-campaign-2026-08-11/` |
| E7 | Cool/charged rerun: Pixel 5×50, A14 4×50+gap, A01 1×50 | all | DONE — both predictions HIT (A14 opened 39.7; Pixel dose-response 31.9→42.2 at constant 2803 MHz ⇒ memory-bus thermal throttle) | `claude-output/cooldown-2026-08-12/` |
| E8 | simpleperf IPC | A14 | DONE (repurposed) — --app blocked on Samsung; command-workload counters fine; used for E14 synthetic discriminator | `probes-2026-08-12/` |
| E9 | Causal pressure test (induced system_server churn) | A14 + P7P | DONE — CAUSAL: A14 +40% (758→1058 ms), P7P +24% (208→258), instant recovery | `probes-2026-08-12/` |
| E10 | A01 4×10 toggle-discrimination mini (Samsung sw × MediaTek hw) | A01 | DONE — NO toggle (medians 105.8→109.7→124.0→107.3) ⇒ not Samsung software; ("MIF-DVFS strengthened" inference later REFUTED by E14 — toggle is A14/benchmark-context-specific). Bonus: pass4 iter000 cfgload 35.8 ms (data survived a reinstall) — natural experiment confirming bimodality = file presence | `claude-output/a01-mini-2026-08-12/` |
| E11 | A01 data-equalization top-up 4×50 | A01 | DONE — no toggle at 50-iter scale; leftover-data iter000s from cooldown onward | `claude-output/a01-campaign-2026-08-11/` |
| E12 | Pixel 7 Pro 4×50 hot + 4×50 cool | P7P | DONE — window 11–13 ms med / 29.4 worst; thermally indifferent ≤34 °C; predictions hit except config share (35%, its #1 section — share GREW) | `claude-output/p7p-{campaign,cooldown}-2026-08-12/` |
| E13 | Cross-device side-by-side (sections med/max/%win) | all | DONE (final, 1,850 iters) — same top-3 sections all devices; order flips on P7P (cfgload #1) | `cross-device-sections-2026-08-12.txt` |
| E14 | Toggle discriminator: synthetic mem/cpu workloads under simpleperf in both states, cool | A14 | DONE — synthetics IDENTICAL across states; un-traced launches Δ~6% vs 27% in-benchmark; install-only doesn't advance ⇒ toggle = measurement-context artifact (tracing-overhead theory); prod relevance downgraded | `probes-2026-08-12/a14-probe2.log` |
| E15 | Toggle without tracing (install + 50 am-starts + uninstall, ×4) | A14 | QUEUED — the closing test for the tracing-overhead theory | — |
| E18 | Version-history sweep: 8.3.0 / 8.4.0 / 9.0.0 / 9.1.0 (mavenCentral) × fleet × 4×25 default mode; per-version compile-gate (old API/plugin incompat ⇒ version skipped + logged, itself a finding); old versions may have NO emb sections ⇒ TTID is the cross-version metric; script restores pin+iterations | all | ARMED — gated on E17 completion (~18:30), est. ~4 h ⇒ done ~23:00, machine free overnight | scratchpad `version-history/` + `version-history.log` |
| E16 | Baseline-profile A/B, 2×20 per device×mode | all | **first run INVALID** — the morning repo rebase silently reverted the catalog pin to 9.1.0, so all arms built against released 9.1.0 from mavenCentral (symptoms: no emb-sdk-start, old section names, obfuscated dynamic names). Data quarantined (`bp-ab-INVALID-built-against-9.1.0/`). Pin restored to 9.2.0-SNAPSHOT, rebased app compile-checked against the snapshot, campaign RERUNNING (~1 h). LESSON: after any rebase/sync, verify the catalog pin AND check traces for emb-sdk-start before trusting a run; note rebase also bumped ExampleApp deps (otel-bom/metro/wrapper) ⇒ absolute ms vs pre-rebase campaigns carry an app-dep caveat; pattern-level comparisons remain valid | scratchpad `bp-ab/` (fresh) |
| E17 | Verification campaign (REVISED per review; fires 16:30): single uniform phase — 4×50, default CompilationMode, ALL FOUR devices (~2 h). Phase A dropped (post-idle A/B top-up wouldn't represent a contiguous campaign; 2×20 A/B stands); 4×25/4×50 split collapsed (the split was time-budget only — nothing methodologically special triggers outliers in the longer shape beyond more iterations = more mid-pass GC runway + bigger tail sample). Same frozen mavenLocal snapshot ⇒ directly comparable. Script self-restores iterations=20. ANALYSIS CHECKLIST: extract variance+factors JSONs per pass dir; hypothesis_tests + cross_device_sections; verify — toggle alternation across 4 passes (A14), ratio fingerprint (pure-CPU ~1× vs block-resume ~2×), H3 iter000 (watch for leftover-data poisoning), extreme-rate ~3–5% mid-tier / ~30% A01, section top-3 shares per device, tail stats p90/p95/max/top-3 vs prior campaigns | all | ARMED — devices plugged in from 16:30; do NOT touch examples/ or the two uncommitted files (StartupBenchmarks.kt, libs.versions.toml) until complete; repo sync safe AFTER | scratchpad `verify-4x50/` + `tonight-verification.log` |

## Analysis pipeline (per pass / per device)

`variance_analysis.py` (+ `--json`) → `hypothesis_tests.py` (cross-pass verdicts) →
`outlier_factors.py`/`outlier_metrics.sql` → `factors_report.py` (factor correlations,
outlier catalogue). All in `claude-output/`; trace_processor launcher in session scratchpad
(refetch: `curl -sL -o <scratchpad>/trace_processor https://get.perfetto.dev/trace_processor`).
Cluster-share semantics: cl0 = cpu0-3 (A14: arbitrary domain; Pixel: little cores).

## Interim findings this round (details in testing log)

- Pixel pass 1: clock mechanism confirmed where clocks vary (eff_mhz r=-0.71); mega-outlier
  104 ms = dex2oat32 storm (412 ms CPU) + little-core displacement + IO; pressure sources on
  Pixel = user apps (Photos 44–89 ms/window!) + dex2oat, NOT system_server; artload r=0.97;
  config-load bimodality replicates (H3 holds cross-device).
- A14 Tier 1: GC sufficient-but-not-necessary; system_server CPU r=0.73 is the load-bearing
  factor; partial r(gc|ss)=-0.13.

## Additional confirmed findings (fold into synthesis)

- **First-launch penalty = install aftermath, measured**: iter000 windows run with 2–3×
  baseline concurrent CPU (other-proc rate 2.9–5.1 vs 1.6–1.9 in every A14 pass); the
  competitors are the install pipeline itself — com.android.vending 55–80 ms, installd
  17–43 ms, artd 48 ms, system_server bursts to 75 ms, plus PACKAGE_ADDED receivers
  (Samsung Health etc.). Signature is diffuse CPU (no extra IO/verify) = T-pressure with
  a deterministic trigger. Settles by iter002. Config fast-path (~-8 ms) partially masks
  it in window terms.
- **Prod implication (user-flagged, highlight in conclusions)**: extreme-outlier
  populations in prod are likely enriched with first-post-install launches, amplified
  per-app by whatever first-run work the host app does (onboarding, migrations,
  downloads, other SDKs). Real Play installs are heavier than adb install (verification,
  cloud-profile dexopt) and users open within the aftermath burst. → Prod outlier
  analysis/alerting must segment version_startup_counter == 1 as its own cohort with its
  own baseline; regression detection runs on the > 1 cohort.
- **Config-load bimodality verified in code** (PersistedConfig.kt:47 →
  RemoteConfigStoreImpl.loadResponse: both file opens FileNotFound → null on fresh
  install; binary-cache EmbraceBinary.decodeFromStream = the 7–22 ms CPU cost otherwise).
  Optimization target = the decode, not the disk.
- **No profile-driven within-day speedup**: steady state by iter002 (page cache +
  aftermath drain); ART profile→AOT payoff needs background dexopt (idle+charging), out
  of scope for passes; queued experiment: `cmd package bg-dexopt-job` before/after pass.
- **Pixel pass drift = ambient load growth, not sustained throttling** (per-pass eff_mhz
  flat 2773–2799); transient clock dips drive mid-tier Pixel outliers (r=-0.55); extremes
  at full clock under dex2oat/Play/GMS/system_server pressure + IO (up to 50.7 ms D/io).

## E5 result (A14 campaign2, pass-flip isolation) + predictions for E7

- Medians 38.7 → 49.4 → [5-min idle gap] → 38.7 → 49.3 at 28–29 °C. The alternation
  SURVIVED the idle gap, and the phase persisted across the ~80-min inter-campaign break
  (campaign1 ended slow → campaign2 opened fast). Conclusion: the A14 pass state is a
  deterministic two-state toggle advanced by each install/run cycle — not time, not
  thermal, not idle-based governor hysteresis. Ratio fingerprint replicated (pure-CPU
  0.98–0.99×, block-resume 1.82–2.37×). H3 now 14/14 passes, zero counterexamples.
- A14 MIF/devfreq: UNOBSERVABLE unrooted (shell SELinux-denied despite 0644 modes;
  perfetto sys_stats devfreq prober also returns nothing on this build). T-MIF is
  indirect-only on this hardware; root device required for the direct test.
- **Predictions registered for E7 (cooldown):** A14 cooldown pass 1 opens FAST (~38–40,
  continuing phase from campaign2's slow ending) and alternates; Pixel cool medians
  well below 54–61 ms ⇒ temperature mattered, unchanged ⇒ ambient load was the cause.

## E7 (cooldown) results — both registered predictions HIT

- **A14 phase continuation CONFIRMED**: cool arm 39.7 → 49.2 → 39.4 → 50.7 (opened fast
  exactly as predicted; 18/18 passes now in strict alternation; fingerprint replicated;
  ran at FULL BATTERY NOT CHARGING → charging also excluded; toggle robust to temp, time,
  idle, charging — a deterministic Exynos state advanced per install cycle).
- **Pixel temperature effect CONFIRMED with dose-response AND mechanism**: cool medians
  31.9→37.7→41.6→39.5→42.2 (monotonic rise while self-heating 29.8→39 °C, quiet ambient)
  vs hot 49.7–61.0. DECISIVE: eff_mhz = 2803 (max, zero dips) in BOTH cool pass1 (31.9 ms,
  run 28.9) and cool pass5 (42.2 ms, run 37.5); hot passes 2773–2799 MHz at 59–61 ms.
  ⇒ heat throttles the MEMORY SUBSYSTEM (DDR/bus/L3), invisible to cpufreq — IPC drops
  ~50% cool→hot at constant CPU clock. Unifies with A14 toggle + pressure mechanism:
  across all devices, window = work ÷ (CPU clock × IPC) and the moving term is always
  effective memory speed (modulated by heat / Exynos toggle / concurrent traffic).
- H3: 22/23 passes (the one exception both times = leftover app data surviving a
  reinstall — natural experiment confirming file-presence semantics).
- Cool Pixel outlier rate collapsed: 5/250 slow vs 54/250 hot; zero binder stalls.

## Synthesis — COMPLETE (2026-08-12 ~05:45)

All experiments done (E1–E7, E10–E13; E8 simpleperf and E9 causal-pressure remain queued
for a future session, plus the rooted-device asks). 1,850 iterations, 4 devices, hot+cool
arms on both Pixels. Both registered predictions hit. Final verdicts in the testing log
(https://claude.ai/code/artifact/c8ef75b3-4c83-4335-8b9a-3776699266a0); four-device
side-by-side in `cross-device-sections-2026-08-12.txt`; 92 record files synced into
claude-output per-campaign dirs; `iterations = 20` restored.

## Root-free follow-up round (2026-08-12 ~08:00, E8/E9 executed + toggle decomposition)

- **E9 causal pressure: DONE, causal.** Induced system_server churn (3 concurrent dumpsys
  hammers): A14 launches 758 → 1058 ms (+40%), P7P 208 → 258 ms (+24%); both recover
  instantly when load stops. T-pressure upgraded correlational → causal, with the tier
  differential measured under identical absolute load.
- **Toggle trigger narrowed: install-only does NOT advance it.** Three consecutive
  adb install -r cycles (no instrumented run) stayed in the same state (~600 ms launches);
  the flip requires something else in the benchmark cycle (instrumented run / uninstall).
  Late-battery slowdown was heat-confounded (see next); deconfounded discriminator
  running: synthetic mem (dd) + cpu (sha1) workloads under simpleperf in BOTH states —
  distinguishes device-global bus state vs app-install artifact.
- **A14 DOES thermally throttle under sustained load**: rapid-fire launching self-heated
  AP 27.6 → 38 °C, crossing its skin-throttle onset (36 °C). The paced campaigns never
  got close — which is why heat never confounded them.
- **Unrooted thermal instrumentation unlocked**: dumpsys thermalservice exposes silicon
  temps everywhere (Pixel 3: per-CPU sensors — its hot campaign ran at ~70 °C silicon
  while battery read 41; and its cooling-device list includes thermal-devfreq-0 =
  architectural confirmation that SD845 throttles the memory subsystem for cooling).
- **P7P install aftermath visible in raw launches**: 468 → 331 ms across the first minute
  post-install (third independent measurement of the first-launch effect).
- **E8 simpleperf**: --app attach blocked on Samsung builds (silent); command-workload
  counters fully available (perf_event_paranoid = -1) — repurposed for the discriminator.
- Tooling: ART-10 emits no per-class load slices (documented in outlier_metrics.sql);
  hypothesis_tests.py outlier threshold now tier-relative (max(4 ms, 10% of pass median)).

## M5/P9 code audit — main-thread block-and-resume sites on the init path (2026-08-12)

Ranked by likelihood of explaining the 2×-inflating sections (full agent report preserved in
session task output; key sites):

1. **`keyValueStore` SYNCHRONIZED-lazy handoff (fix first).** `ModuleInitBootstrapper.kt:78`
   (`lazy { createKeyValueStore(...) }`, SYNCHRONIZED) is forced by the MAIN thread in
   `instrumentation-init` (`InstrumentationModuleImpl.kt:39` → `CoreModuleImpl.kt:24-26` →
   `SharedPrefsStore.kt:20-21`) while the `emb-http-request` worker — scheduled with delay 0
   inside `config-service-init` (`ConfigServiceImpl.kt:96` → `CombinedRemoteConfigSource.kt:19`)
   — can hold the same lazy via `PersistedConfig.deviceId` (`DeviceIdProvider.kt:20-22`).
   Main thread parks unboundedly on a worker's monitor; under CPU pressure the race gets more
   likely — matches the 2× fingerprint. Fixes: defer `scheduleConfigRequests()` out of
   `config-service-init`; decouple deviceId from the shared lazy on the HTTP path; or
   `LazyThreadSafetyMode.PUBLICATION`.
2. **First-use worker THREAD CREATION inside hot sections** (pthread_create: ART parks the
   caller until the child thread finishes init): `CombinedRemoteConfigSource.kt:19-24`
   (config-service-init), `DeviceImpl.kt:38-42` 3 submits (payload-source-init),
   `PeriodicSessionPartCacher.kt:29-34` (post-init), `ModuleInitBootstrapper.kt:143-148`
   prewarm (unattributed). Fold/pre-create threads off-main or defer first submits.
3. **SharedPreferences contention lands in UN-RECORDED sections**: prewarm
   (`ModuleInitBootstrapper.kt:145`) can convert a disk wait into a lock wait at
   `SharedPrefsStore.kt:21` (instrumentation-init); first `awaitLoadedLocked()` read at
   `SessionOrchestratorImpl.kt:104` → `UserSessionMetadataStore.kt:41`
   (user-session-orchestration-init). BOTH sections lack `recordDuration=true` → costs hide in
   modules-init totals. Quick win: add recordDuration to both; measure prewarm-removed variant.
4. **`post-init` is a compound of 4 wait classes**: `SessionOrchestratorImpl.kt:351,357`
   (monitor + `store.batch{}` prefs RMW/apply), `OrdinalStoreImpl.kt:13-33`
   (appVersionStartupCounter read-modify-write — attribute-only, deferable), periodic-cacher
   thread start, first real span creation (class loading).
5. **persisted-config-load fallback path** costs ~3× the binary fast path (2 opens + JSON parse
   + etag readText, `RemoteConfigStoreImpl.kt:30-68`) — cache-miss runs look ~2× worse.
6. `delivery-init`/`essential-service-init`: NO main-thread waits found — inflation there is
   cold class loading (dex mmap faults, ClassLinker locks); androidx.lifecycle first-touch in
   essential. Addressable only by class-set reduction/baseline profile (ties to P2).
7. Binder: default path clean (all risky getSystemService/registerReceiver behind Lazy or on
   workers). One gated exception: `ActivityProcessLifecycleTracker.kt:168-173`
   (runningAppProcesses, main thread) fires only if isActivityProcessLifecycleTrackerEnabled
   is flipped on by config.
8. **EmbTrace atrace overhead** (`EmbTrace.kt:36-63`): 2 trace_marker write() syscalls per
   section when tracing, multiplied by nesting (payload-source-init wraps 5) — a near-uniform
   multiplier on SHORT sections under load. Independently corroborates the toggle-as-tracing-
   overhead theory (E14/E15); cheap test = tracing on-vs-off span comparison.
9. Explicit primitives (latch/semaphore/runBlocking/sleep/join): zero on production init path;
   the two Future.get(timeout) sites are correctly off-path.

Headline conclusions:
1. Window = work ÷ (CPU clock × IPC); the moving term is nearly always IPC = effective
   memory-subsystem speed. Modulators: concurrent CPU bursts (system_server GC, dexopt,
   GMS — universal), heat (Pixel 3: memory-bus thermal throttle at constant 2803 MHz CPU
   clock; P7P immune ≤34 °C), and the Exynos install-cycle toggle (A14 only, 18/18,
   identity needs root).
2. First-launch penalty = install aftermath (2–3× concurrent CPU), amplified in prod by
   Play installs + app first-run work → segment version_startup_counter == 1.
3. Same top-3 sections on all four devices; config decode is ms-dominant low-end (22 ms
   A01) and share-dominant on flagships (35% P7P) → top optimization target, followed by
   otel-tracer-init.
4. Tier gradient: P7P 11.8/29.4 (med/worst) · Pixel3 44.5/120 · A14 46.8/92 · A01
   101.7/203. Mechanisms universal; damage tier-dependent.
5. Methodology rules: ≥2 passes/device, fast-pass medians only, verify iter000 freshness,
   tier-appropriate outlier thresholds, expect uninstall failures to poison first-launch
   sampling.

**Resume info:** session of 2026-08-11 evening (same conversation as the 5×50 A14 campaign);
background tasks: E3 pixel campaign `bjx45z0wc`, E4 poller `b0eh1dukv`, E7 cooldown
`bptgswfjr`.
