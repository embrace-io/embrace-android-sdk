---
name: startup-multi-device-analysis
description: >-
  Run coordinated SDK startup benchmark campaigns across multiple connected Android devices
  and perform per-iteration forensics (delivered clocks, thread-state, competing processes,
  ART work, IO, GC) plus cross-device synthesis to explain WHY startups are slow or variable —
  distinguishing SDK code effects from device-tier, thermal, scheduling, and environmental
  causes that a single-device run cannot separate. Use when investigating outliers, variance,
  or anomalies; for a quick single-device timing/regression check use startup-analysis instead.
---

# Multi-device startup forensics

Single-device runs (the `startup-analysis` skill) answer *how fast is SDK init here*. This
skill answers *why is it slow or variable, and is the cause our code, the device, or the
environment* — questions where multiple devices are the inferential engine, not a nicety:

1. **Workload-identity check** — if section *shares* of the window match across devices, the
   SDK is doing identical work everywhere and differences are device effects; if shares
   diverge, the code path itself differs (OS/ART version, config) and that is the lead.
2. **Anomaly triangulation** — a per-device anomaly (e.g. a bimodal pass state) is scoped by
   testing vendor/SoC/OS arms: same-software-different-silicon and same-silicon-class-
   different-software runs tell you whose anomaly it is before you chase mechanisms.
3. **Tier scaling** — every finding is graded by whether it is absolute-ms or proportional
   across tiers, and whether it matters where outliers actually bite (entry-tier hardware).
4. **Pressure personality** — each device has its own dominant outlier cast (system-process
   GC vs install-time dexopt vs app-ecosystem churn); the taxonomy only generalizes when the
   same classes appear with different casts across devices.
5. **Condition arms** — hot vs cool, induced pressure, compilation-mode A/B: mechanism
   separation that correlation inside one run can never provide.

All analysis is trace-derived (perfetto trace_processor), same as `startup-analysis`.
References: `references/methodology.md` (run shapes, comparison rules, statistics),
`references/outlier-taxonomy.md` (known outlier classes + trace signatures + the prod
telemetry mapping), `references/device-gotchas.md` (per-vendor tooling limits and traps).

## Bundled files (relative to this skill's base directory)

Scripts (python3 stdlib only; trace_processor launcher fetched as in `startup-analysis`):

- `scripts/device_probe.py` — per-device topology/thermal probe: CPU part ids, cpufreq
  policies (cluster map + max freqs), thermalservice availability, OS version. Writes
  `<name>-topology.json`; its cluster map feeds `--little-cpus` below. Run FIRST per device.
- `scripts/fleet_campaign.py` — one device's campaign: N back-to-back passes of the chosen
  benchmark method, traces copied aside per pass before the next wipes them, battery AND
  silicon (thermalservice) temps logged per pass. Args: serial, connected-dir match,
  output dir, passes, method. Run per device, sequentially (single gradle project).
- `scripts/variance_analysis.py` — per-iteration extraction for one pass dir: window +
  every section duration, per-section thread-state, per-CPU window residency; `--json` for
  downstream tools; `--little-cpus` sets the cluster split from the probe.
- `scripts/hypothesis_tests.py` — cross-pass tests for one device: pass-state detection
  (fast/slow alternation + section-ratio fingerprint), config-load bimodality (iter000 vs
  rest), placement correlation, off-window fluctuators.
- `scripts/outlier_metrics.sql` + `scripts/outlier_factors.py` — the external-factor
  catalogue per iteration: delivered CPU clocks, thread-state with D/IO split, ART
  verify/class-load, lock contention, binder counts, own-process GC, in-process and
  other-process CPU competitors, swap/memory.
- `scripts/factors_report.py` — factor correlations vs window delta, pass-level factor
  means, the ranked outlier catalogue, extreme-outlier competitor drill-down.
- `scripts/cross_device_sections.py` — the side-by-side: per-section median/max/%-of-window
  per device (pooled across passes), top-3 shares — the workload-identity check.

## Prerequisites

- Everything from `startup-analysis` (built SDK in mavenLocal or a released version pinned,
  device prerequisites), times N devices. Multiple devices may be connected simultaneously;
  target each with `ANDROID_SERIAL` (fleet_campaign does this). Gradle runs are sequential
  across devices — one campaign at a time; analysis parallelizes freely.
- The benchmark harness (`StartupBenchmarks.kt`) exposes three methods: `coldStartup`
  (CompilationMode.DEFAULT), `coldStartupNoAot` (None), `coldStartupBaselineProfile`
  (Partial/Require — fails if no profile is packaged, which is itself the packaging check).
  Select with `#method` in the instrumentation class filter.

## Procedure

1. **Probe the fleet**: `device_probe.py` per device. Record per device: cluster map
   (little-CPU list), max freqs, OS/API, thermal sensors. Any device meeting the
   prerequisites fits; the probe output IS the device context for reports.
2. **Campaign per device**: default shape **4 passes × 50 iterations** (see
   methodology.md; 4×25 floor for targeted/fast runs — never fewer). Keep passes
   back-to-back; the campaign log carries temps. Set the iteration count in
   `StartupBenchmarks.kt` if it differs, and restore it afterwards.
3. **Per-device analysis**: `variance_analysis.py --json` per pass (use the probe's
   `--little-cpus`), then `hypothesis_tests.py` per device. Check FIRST for pass-state
   alternation — if present, all cross-pass comparisons use matching-state passes only.
4. **Factor forensics**: `outlier_factors.py` per pass, `factors_report.py` per device.
   Read the outlier catalogue tail-first (p90/p95/max/top-3, slow-rate) — never medians
   alone. Classify outliers against `references/outlier-taxonomy.md`.
5. **Cross-device synthesis**: `cross_device_sections.py` over all devices. Run the
   workload-identity check; grade each per-device finding by tier scaling; triangulate any
   anomaly across vendor/SoC/OS arms before theorizing mechanisms.
6. **Condition arms as needed** (each is a separate mini-campaign, compared to its own
   baseline): hot vs cool+charged (≥2 h gap; judge with SILICON temps, not battery);
   compilation-mode A/B (the three benchmark methods); causal pressure (concurrent
   `dumpsys package` hammer threads from the host while probing launches — expect
   tier-differentiated inflation); un-traced probes (`am start -W` loops) to separate
   measurement-context effects from real ones.
7. **Report**: per-device findings + the cross-device synthesis, with every claim tagged by
   which inference pattern (1–5 above) supports it. State per device: model, topology, OS,
   temps during runs, pass-state status. Keep per-pass records (summary txts, JSONs, logs) —
   raw traces are wiped by the next run and are too large to keep for whole fleets.

## Interpretation guardrails

- Judge regressions on fast-state medians of matching-state passes; never compare across
  pass states or window sources.
- iter000 of each pass is a different code path (no persisted config) AND runs inside the
  install aftermath — analyze it as its own cohort, and VERIFY freshness (a failed harness
  uninstall silently turns later iter000s into cached-config launches).
- Delivered CPU clock explains little where it is pinned (most devices at benchmark load);
  the moving term is effective memory-system speed — look at competitors, temps, and IO
  before clocks.
- Distinguish measurement artifacts from real effects: tracing overhead multiplies short,
  context-switch-heavy sections under load; corroborate any pass-level anomaly with
  un-traced probes before treating it as real.
