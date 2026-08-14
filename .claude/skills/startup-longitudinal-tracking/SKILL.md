---
name: startup-longitudinal-tracking
description: >-
  Accumulate SDK-init startup results from many runs over time into a persistent store anchored
  to a stable reference device set, so that per-device baselines, drift, and regressions become
  visible across weeks and releases rather than only within one campaign. Use when asked whether
  startup has regressed or improved over time, to maintain a startup baseline for CI or release
  gating, or to pool repeated runs into a sample large enough to judge tail behaviour. For a
  single measurement use startup-analysis; for cross-device forensics use
  startup-multi-device-analysis; for controlled version/condition comparisons use
  startup-version-factor-matrix.
---

# Longitudinal startup tracking

The other three skills each answer a question *inside one sitting*: how fast is it here
(`startup-analysis`), why is it slow or variable across devices
(`startup-multi-device-analysis`), and does a version or condition change it under controlled
cells (`startup-version-factor-matrix`). None of them can answer **"is this getting worse?"**,
because that question needs measurements taken at different times to be comparable — which is a
data-management problem, not a benchmarking one.

This skill owns that problem. It ingests results produced by the other three, stores them with
enough provenance to be trusted later, maintains a **baseline per device profile**, and reports
drift, regressions, and pooled tail statistics that no single run can supply.

It assumes `startup-analysis/references/interpreting-results.md` for everything about judging a
single run — trace query traps, which conditions to record, install-time compile state, run shape
and tail statistics — and does not repeat it. A run that was misread on the day does not become
correct by being stored.

Two hard ideas make it work:

1. **A stable reference device set.** Longitudinal comparison is only valid against devices that
   stay the same. The set is declared once (`reference-set.json`), keyed by device profile, and
   every ingest is checked against it. A device whose OS or hardware profile changes is treated
   as a NEW device, not a continuation — silently comparing across an OS upgrade is the classic
   way to invent a regression.
2. **A fixed measurement recipe.** Runs are only poolable when the run shape, build type,
   compile state, and instrument match. Every stored record carries them, and comparisons refuse
   to mix incompatible records rather than averaging them into nonsense.

## Bundled files

- `references/store.md` — the record schema, the reference-set contract, what makes two runs
  comparable, and how to handle device retirement, replacement, and OS upgrades.
- `references/analysis.md` — how to read the trend output: baselines, drift vs regression,
  pooled tail statistics, and the traps (population drift, survivorship, seasonality of device
  state, and why a moving baseline hides slow regressions).
- `scripts/ingest_run.py` — take a completed run directory from any of the other three skills,
  extract per-iteration windows plus provenance, validate it against the reference set, and
  append it to the store. Refuses non-comparable records loudly.
- `scripts/trend_report.py` — per-device-profile baselines, run-over-run deltas with a
  significance rule that respects the tail-heavy distribution, and a regression verdict.
- `scripts/reference_set.py` — declare/inspect the reference set: probe attached devices, write
  or update `reference-set.json`, and flag drift in a device's own profile.

## Setting up the reference set (do this once, revisit rarely)

1. Decide which devices you can commit to keeping available and unchanged for months. Fewer,
   stable devices beat more, churning ones — the whole value is comparability over time.
2. Choose them for coverage, not convenience: span at least two tiers (include one
   entry/low-RAM device, where regressions bite hardest), two vendors (install-time compile
   policy and thermal governors are OEM decisions), and two ART generations (AOT behaviour and
   class-load accounting differ). Physical devices only. Practical floor: API 29.
3. `python3 scripts/reference_set.py --probe --out reference-set.json` records each device's
   profile: `api_level`, `tier`, `vendor`, `soc_family`, cluster topology, `ram_class`, plus a
   stable `device_key` you choose (e.g. `entry-a`, `mid-b`) that is used in every later report.
4. Freeze the measurement recipe in the same file: run shape (default 10x20 — ten passes of twenty
   iterations), build type (benchmark/profileable), compile state, and the window instrument.
   Changing any of these starts a new comparable series — the store keeps both and never mixes
   them, so treat a reshape as a deliberate re-baselining with a cost, not a tweak.

Keep the set small enough that you will actually re-run it on schedule. A set you skip is worse
than a smaller set you maintain, because gaps are where regressions hide.

## Procedure

1. **Produce a run** with whichever skill fits (single-device check, multi-device campaign, or a
   matrix cell). Nothing special is required beyond the profile-carrying provenance those skills
   already write.
2. **Ingest it**: `python3 scripts/ingest_run.py <run-dir> --store <store.jsonl>`. The script
   pulls per-iteration windows, the device profile, SDK version, build type, compile state, run
   shape, and instrument; it then checks the record against the reference set and the frozen
   recipe. Mismatches are reported, not silently accepted.
3. **Report**: `python3 scripts/trend_report.py --store <store.jsonl>` prints, per device key: the
   baseline, the most recent runs, deltas with their significance verdict, and pooled tail
   statistics across all comparable runs.
4. **Act on the verdict, not the delta.** A single run that moved is a candidate; a move that
   survives the significance rule *and* reproduces on the next run is a regression. Escalate a
   confirmed regression into `startup-version-factor-matrix` to find which factor carries it.

## What this skill deliberately does NOT do

- It does not run benchmarks. Measurement stays in the other skills so there is exactly one
  implementation of each measurement, and this layer cannot silently diverge from it.
- It does not average across device profiles. Devices differ by multiples, so a fleet-wide mean
  is meaningless; every number is per device key, and only pooled *within* a key.
- It does not chase absolute targets. There are no portable reference numbers — the baseline is
  whatever your own reference set produced, and the only meaningful statement is a change
  relative to it.
