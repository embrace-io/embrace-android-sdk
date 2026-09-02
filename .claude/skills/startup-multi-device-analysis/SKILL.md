---
name: startup-multi-device-analysis
description: >-
  Run coordinated SDK startup benchmark campaigns across a deliberately diverse set of physical
  Android devices, then COMPARE the result sets: flag where devices differ, scope whose anomaly
  it is (SDK code vs device tier, vendor, ART generation, thermal or scheduling environment),
  and handle signals that are missing or incomparable on some devices. Use when investigating
  outliers, variance, or anomalies across devices, or when choosing a device set. For a single
  device's run use startup-analysis; to attribute a flagged difference to a specific cause use
  startup-version-factor-matrix.
---

# Multi-device startup forensics

Single-device runs (the `startup-analysis` skill) answer *how fast is SDK init here*. This
skill answers *why is it slow or variable, and is the cause our code, the device, or the
environment* — questions where a **deliberately diverse set of physical devices** is the
inferential engine, not a nicety:

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

**This skill assumes `startup-analysis/references/interpreting-results.md` and does not repeat
it.** That file owns everything that is true of a single device's traces: the trace_processor
query traps, the telemetry verification tap, harness traps, which run conditions to record and
how to use them to explain an iteration, the install-time compile-state toggle, run shape and
tail statistics, within-device comparison hygiene, and the five outlier classes' single-trace
signatures. Read it first; the references here add only what a *set* of devices buys.

References: `references/methodology.md` (device-set assembly, condition control, comparing sets
with missing values, cross-device comparison rules), `references/outlier-taxonomy.md` (tier
gating, per-device casts, extending telemetry validation across the set),
`references/device-gotchas.md` (vendor-class tooling capability limits).

**What this layer may conclude.** It compares result sets and **flags differences** — "these two
differ by this much on this metric", "this signal exists on one and not the other", "this delta is
confounded by X". It does not explain *why* a difference exists: attribution needs one factor
moved at a time, which is `startup-version-factor-matrix`. Escalate a flagged difference there
rather than asserting a cause from an uncontrolled cross-device delta.

## Choosing your device set

Every inference pattern above is bought with a **contrast**. A set assembled from whatever is
on the desk usually buys none of them. Assemble deliberately. **Physical devices only** —
emulators share the host's scheduler, thermals, page cache, and storage stack, so they cannot
produce a single one of these contrasts.

Axes, and what each buys:

- **ART / Android generation — span at least 2.** AOT and install-time compile behaviour,
  class-load and verification accounting, and even *which ART slices exist in a trace* change
  between generations. Without this axis you cannot separate "the SDK's class loading got
  cheaper" from "this ART generation stopped emitting per-class slices". Practical floor:
  **API 29** — below it only debuggable targets can be traced and their numbers are
  meaningless.
- **Vendor / OEM — span at least 2.** Install-time compile policy, thermal governors, and
  procfs/sysfs + SELinux readability are OEM decisions, not silicon ones. A single-vendor set
  cannot separate OEM policy from silicon; every finding stays confounded.
- **Tier — span at least 2, and include one entry / low-RAM device.** Outlier classes are
  tier-specific: memory pressure and own-process GC essentially vanish above a couple of GB
  of RAM, while core-placement damage is a big.LITTLE phenomenon. The tail is also where
  users actually hurt. A flagship-only set reports a healthy SDK while the entry tier burns.
- **Volume representativeness.** Prefer devices matching the app's real installed base (pull
  the actual API-level / model / RAM distribution) over whatever is newest. Newest hardware
  systematically under-reports every class in the taxonomy.

**Minimum useful set: 2 devices differing on tier. Recommended: 3–4 devices spanning
tier × vendor × ART generation.** Three devices chosen on different axes beat six chosen on
none — a second device of the same tier, vendor, and OS generation adds samples, not
inference.

What a missing axis costs you:

| missing axis | what becomes uninterpretable |
| --- | --- |
| single ART/Android generation | compile-mode and class-load findings; you cannot tell a code change from an ART accounting change |
| single vendor | OEM install-time compile policy, thermal-governor behaviour, and every sysfs/SELinux readability gap read as "how Android behaves" |
| single tier | outlier taxonomy coverage (memory pressure, GC competition, IO stalls are tier-gated); absolute-vs-proportional grading of every finding |
| no entry/low-RAM device | the classes that dominate real user pain; you will conclude the tail is fine |
| flagship-only / newest-only | all of the above, plus a systematically optimistic baseline |

State the gaps in the report rather than papering over them: "single-vendor set — OEM policy
and silicon are confounded for finding X" is a legitimate, useful conclusion.

## Device profile vocabulary

Describe every device by these fields — never by marketing name alone — and use the same
fields across all the startup skills so results stay comparable:

- **api_level / Android release** — the ART generation; API 29 is the practical floor for
  profileable shell tracing.
- **tier** — entry / mid / flagship, proxied by RAM and SoC class.
- **vendor / OEM** — governs install-time compile policy, thermal governors, SELinux
  readability of sysfs/procfs nodes.
- **soc_family + cluster topology** — cluster map, per-cluster max frequencies, homogeneous
  vs big.LITTLE (from `device_probe.py`).
- **ram_class**, and **storage_class** where detectable (eMMC vs UFS-class changes IO-stall
  severity by an order of magnitude).

`device_probe.py` emits all of these. **Run it on every device before the first campaign and
store its topology JSON alongside the results.** Comparability later depends on it: without
the recorded profile, a subsequent run cannot tell whether a difference is the SDK, a
different device, or the same device in a different state.

## Bundled files (relative to this skill's base directory)

Scripts (python3 stdlib only; trace_processor launcher fetched as in `startup-analysis`):

- `scripts/device_probe.py` — per-device profile probe: vendor/model, API level, SoC family,
  CPU part ids, cpufreq policies (cluster map + max freqs), RAM and storage class, a
  heuristic tier, thermalservice availability. Writes `<name>-topology.json`; its cluster map
  feeds `--little-cpus` below. **Run FIRST per device and keep the JSON.**
- `scripts/fleet_campaign.py` — one device's campaign: N back-to-back passes of the chosen
  benchmark method, traces copied aside per pass before the next wipes them, battery AND
  silicon (thermalservice) temps logged per pass. Args: serial, connected-dir match,
  output dir, passes, method; `--repo` if not run from inside the repo. Run per device,
  sequentially (single gradle project).
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
- A device set assembled per **Choosing your device set** above, each device probed and its
  profile recorded.
- The benchmark harness (`StartupBenchmarks.kt`) exposes three methods: `coldStartup`
  (CompilationMode.DEFAULT), `coldStartupNoAot` (None), `coldStartupBaselineProfile`
  (Partial/Require — fails if no profile is packaged, which is itself the packaging check).
  Select with `#method` in the instrumentation class filter.

## Procedure

1. **Probe every device**: `device_probe.py` per device. Record the full device profile
   (cluster map / little-CPU list, max freqs, API level, vendor, SoC family, RAM and storage
   class, thermal sensors). The probe output IS the device context for every later report;
   store it with the results.
2. **Campaign per device**: default shape **4 passes × 50 iterations** (rationale in
   interpreting-results.md; 4×25 floor for targeted/fast runs — never fewer), and the SAME
   shape on every device in the set. Keep passes back-to-back; the campaign log carries temps.
   Set the iteration count in `StartupBenchmarks.kt` if it differs, and restore it afterwards.
3. **Per-device analysis**: `variance_analysis.py --json` per pass (use the probe's
   `--little-cpus`), then `hypothesis_tests.py` per device. Check FIRST for pass-state
   alternation — if present, all cross-pass comparisons use matching-state passes only.
4. **Factor forensics**: `outlier_factors.py` per pass, `factors_report.py` per device.
   Read the outlier catalogue tail-first (p90/p95/max/top-3, slow-rate) — never medians
   alone. Classify each outlier by its single-trace signature (interpreting-results.md), then
   use `references/outlier-taxonomy.md` to check the class against that device's tier gating
   and to name its cast.
5. **Cross-device synthesis**: `cross_device_sections.py` over all devices. Run the
   workload-identity check; grade each per-device finding by tier scaling; triangulate any
   anomaly across vendor/SoC/OS arms before theorizing mechanisms. Where an axis is missing
   from your set, say what that leaves confounded.
6. **Condition arms as needed** (each is a separate mini-campaign, compared to its own
   baseline): hot vs cool+charged (≥2 h gap; judge with SILICON temps, not battery);
   compilation-mode A/B (the three benchmark methods); causal pressure; un-traced probes
   (`am start -W` loops) plus a `dumpsys package dexopt` read to separate measurement-context
   effects from real device behaviour.
   **Dose the pressure arm by its effect, not by its knob.** A light load slows execution
   without ever filling the run queue: the window inflates while runnable-wait stays near zero,
   which looks like contention but is bandwidth/placement. Raise the injected load until the
   thread-state readout actually shows runnable-wait, and report which of the two mechanisms you
   produced — they are different arms, not different intensities of the same arm.
7. **Report**: per-device findings + the cross-device synthesis, with every claim tagged by
   which inference pattern (1–5 above) supports it. State per device its recorded profile
   (see **Device profile vocabulary**), temps during runs, and pass-state status. Keep
   per-pass records (summary txts, JSONs, logs) — raw traces are wiped by the next run and
   are too large to keep for a whole device set.

## Interpretation guardrails

Within-device guardrails (pass states, window sources, iter000 as its own cohort, clocks vs
memory speed, artifact-vs-real) are in interpreting-results.md and apply to **each** device
before any of the cross-device work below. On top of them:

- Both sides of a comparison must independently pass those checks first. A cross-device delta
  computed across mismatched pass states or window sources measures your own bookkeeping.
- Run the workload-identity check (section shares) before attributing any difference to
  hardware; divergent shares mean different code paths, not a faster device.
- Grade every finding absolute-ms vs proportional across tiers, and say which axis of your set
  supports it.
- Never import another device's magnitudes as a threshold. Every calibration number in these
  references is an order-of-magnitude expectation to be re-established on your own set.
- Where an axis is missing from your set, name what that leaves confounded rather than papering
  over it.
