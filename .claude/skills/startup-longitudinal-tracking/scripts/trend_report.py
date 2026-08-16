#!/usr/bin/env python3
"""Report baselines, drift, and regressions per device_key from the longitudinal store.

Design choices that keep this honest:
  * groups by (device_key, build_type, compile_state, instrument, conditions) and NEVER averages
    across groups - devices differ by multiples and recipes differ by more;
  * the baseline is FIXED (earliest N comparable runs) rather than rolling, because a rolling
    baseline absorbs a slow regression and reports "no change" the whole way down;
  * significance is judged against the baseline's own run-to-run spread of medians, not a t-test
    on right-skewed samples;
  * median and tail are separate verdicts - a tail-only move is common, user-visible, and would be
    missed by a median-only report.

Usage:
  python3 trend_report.py --store store.jsonl [--baseline-runs 3] [--json out.json]
"""
import argparse
import json
import pathlib
import statistics
import sys
from collections import defaultdict


def group_key(record):
    """A series is one SDK VERSION on one device under one recipe, tracked over time.

    sdk_version belongs in the key, and leaving it out produces conclusions that are wrong on
    their face. Measured: a store holding 9.1.0 and 9.0.0 on one device grouped them into a single
    series and, because the control happened to be measured later, reported the OLDER version as a
    "+10.7% candidate regression" against the newer one. That is a version comparison wearing the
    clothes of drift over time - and the sign is meaningless, since which version looks like the
    "regression" depends only on measurement order.

    Drift and version-to-version change are different questions and must not share an axis: drift
    is the same version re-measured (anything that moves is the environment or the tooling), while
    a version difference is deliberate. Comparing versions is `version_comparison()` below.
    """
    recipe = record.get("recipe") or {}
    conditions = record.get("conditions") or {}
    return (record.get("device_key") or "unknown",
            record.get("sdk_version") or "?",
            recipe.get("build_type") or "?",
            recipe.get("compile_state") or "?",
            recipe.get("instrument") or "?",
            json.dumps(conditions, sort_keys=True))


def version_comparison(records):
    """Versions measured on the same device under the same recipe, side by side.

    Kept deliberately separate from the drift report: this answers "is this version faster than
    that one", which is a legitimate question the series axis cannot express. Reports each version
    once with its own n, and never labels a difference a regression - with one run each, a
    difference is a candidate finding, not a verdict.
    """
    by_cell = {}
    for rec in records:
        derived = rec.get("derived") or {}
        if not derived.get("n"):
            continue
        recipe = rec.get("recipe") or {}
        cell = (rec.get("device_key") or "unknown",
                recipe.get("build_type") or "?",
                recipe.get("compile_state") or "?",
                recipe.get("instrument") or "?")
        by_cell.setdefault(cell, {}).setdefault(rec.get("sdk_version") or "?", []).append(derived)
    return by_cell


def signal_changes(runs, min_prior=2):
    """Signals that were normally present in this series and are absent in the latest run.

    This is the ONLY place an absence is informative: within one device_key + recipe there is a
    baseline of what the configuration normally emits. Across devices, versions, or recipes,
    presence differences are capability differences and mean nothing, so this never compares
    across series. It is a DATA-INTEGRITY finding, not a performance verdict - a vanished signal
    means the series may have become incomparable, not that anything got faster.
    """
    if len(runs) < min_prior + 1:
        return [], []
    latest = set(runs[-1].get("signals_present") or [])
    if not latest and not any(r.get("signals_present") for r in runs[:-1]):
        return [], []   # this series never recorded signals; nothing to say
    prior_counts = {}
    for run in runs[:-1]:
        for name in set(run.get("signals_present") or []):
            prior_counts[name] = prior_counts.get(name, 0) + 1
    established = {n for n, c in prior_counts.items() if c >= min_prior}
    disappeared = sorted(established - latest)
    appeared = sorted(latest - set(prior_counts))
    return disappeared, appeared


def verdict(delta_pct, band_pct, reproduced):
    """band_pct is the baseline's own run-to-run spread; a move inside it is noise."""
    if abs(delta_pct) <= band_pct:
        return "noise"
    if not reproduced:
        return "candidate (re-run to confirm)"
    return "REGRESSION" if delta_pct > 0 else "improvement (confirmed)"


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--store", required=True)
    ap.add_argument("--baseline-runs", type=int, default=3)
    ap.add_argument("--json")
    args = ap.parse_args()

    path = pathlib.Path(args.store)
    if not path.exists():
        sys.exit(f"no store at {path}")
    records = []
    for line in path.read_text().splitlines():
        line = line.strip()
        if line:
            try:
                records.append(json.loads(line))
            except ValueError:
                print("skipping a malformed store line")

    groups = defaultdict(list)
    for record in records:
        if record.get("derived", {}).get("n"):
            groups[group_key(record)].append(record)

    out = {}
    for key, runs in sorted(groups.items()):
        runs.sort(key=lambda r: (r.get("measured_at") or r.get("ingested_at") or ""))
        device, sdk_version, build, compile_state, instrument, conditions = key
        print(f"\n{'=' * 78}\n{device}  sdk={sdk_version}  [build={build} "
              f"compile={compile_state} instrument={instrument}]\n  conditions={conditions}"
              f"\n  runs={len(runs)}")
        if len(runs) < 2:
            print("  only one run in this series - no trend yet; repeat the same recipe to start "
                  "a baseline")
            continue

        # The baseline is built ONLY from published-version runs. Working-tree runs are reported
        # separately as ad-hoc comparisons: folding a moving target into the reference would
        # re-define "normal" on every commit and destroy the series' reproducibility.
        eligible = [r for r in runs if r.get("baseline_eligible", True)]
        adhoc = [r for r in runs if not r.get("baseline_eligible", True)]
        if not eligible:
            print("  no published-version runs in this series - cannot form a baseline. Run the "
                  "reference cell against a released SDK version first; working-tree runs are "
                  "comparison-only.")
            continue
        runs = eligible
        baseline_runs = runs[:max(1, min(args.baseline_runs, len(runs) - 1))]
        base_medians = [r["derived"]["median"] for r in baseline_runs]
        base_p90s = [r["derived"]["p90"] for r in baseline_runs]
        base_median = statistics.median(base_medians)
        base_p90 = statistics.median(base_p90s)
        # run-to-run spread of the baseline statistic, as a percentage; floor it so a suspiciously
        # tight baseline cannot make every later wobble "significant"
        band = max(4.0, 100.0 * (max(base_medians) - min(base_medians)) / base_median
                   if base_median else 4.0)
        print(f"  baseline (first {len(baseline_runs)} run(s)): median {base_median:.1f} ms, "
              f"p90 {base_p90:.1f} ms, noise band +-{band:.0f}%")

        print(f"  {'measured_at':<21}{'n':>5}{'median':>9}{'p90':>8}{'max':>8}"
              f"{'d-median':>10}{'d-p90':>8}  verdict")
        deltas = []
        for i, r in enumerate(runs):
            d = r["derived"]
            dm = 100.0 * (d["median"] - base_median) / base_median if base_median else 0.0
            dp = 100.0 * (d["p90"] - base_p90) / base_p90 if base_p90 else 0.0
            deltas.append(dm)
            reproduced = (i > 0 and abs(deltas[i - 1]) > band
                          and (deltas[i - 1] > 0) == (dm > 0) and abs(dm) > band)
            tag = "" if i < len(baseline_runs) else verdict(dm, band, reproduced)
            if i < len(baseline_runs):
                tag = "baseline"
            elif abs(dp) > band and abs(dm) <= band:
                tag += " | TAIL-ONLY move (p90) - users feel this even when the median does not"
            print(f"  {(r.get('measured_at') or r.get('ingested_at') or '?')[:19]:<21}"
                  f"{d['n']:>5}{d['median']:>9.1f}{d['p90']:>8.1f}{d['max']:>8.1f}"
                  f"{dm:>+9.1f}%{dp:>+7.1f}%  {tag}")

        pooled = sorted(w for r in runs for w in r.get("windows_ms", []))
        if pooled:
            def pct(p):
                return pooled[min(len(pooled) - 1, int(p * len(pooled)))]
            print(f"  pooled across this series (n={len(pooled)}): median "
                  f"{statistics.median(pooled):.1f}  p90 {pct(0.90):.1f}  p95 {pct(0.95):.1f}  "
                  f"max {pooled[-1]:.1f}"
                  + ("  p99 needs more samples than this" if len(pooled) < 500 else
                     f"  p99 {pct(0.99):.1f}"))

        if adhoc:
            print(f"  ad-hoc comparisons vs this baseline (NOT part of it): {len(adhoc)}")
            for r in adhoc:
                d = r["derived"]
                dm = 100.0 * (d["median"] - base_median) / base_median if base_median else 0.0
                dp = 100.0 * (d["p90"] - base_p90) / base_p90 if base_p90 else 0.0
                inside = "within" if abs(dm) <= band else "OUTSIDE"
                print(f"    {(r.get('measured_at') or '?')[:19]}  {r.get('sdk_version') or 'local':<22}"
                      f" median {d['median']:>7.1f} ({dm:+.1f}%)  p90 {d['p90']:>7.1f} ({dp:+.1f}%)"
                      f"  {inside} the baseline noise band")
            print("    (a working-tree run is judged against the published baseline, never merged "
                  "into it; confirm any move with a second run before believing it)")

        disappeared, appeared = signal_changes(runs)
        if disappeared:
            print(f"  SIGNALS DISAPPEARED (present in >=2 earlier runs, absent now): "
                  f"{', '.join(disappeared)}")
            print("    -> data-integrity finding, NOT an improvement. Check trace health/capture "
                  "config, then the build and instrument, before trusting this run's numbers.")
        if appeared:
            print(f"  signals newly present: {', '.join(appeared)}")
            print("    -> an instrument or recipe change, not a regression; re-baseline "
                  "deliberately if this is intended.")

        latest = deltas[-1]
        if abs(latest) <= band:
            action = "no action - within the noise band"
        elif len(deltas) >= 2 and abs(deltas[-2]) > band and (deltas[-2] > 0) == (latest > 0):
            action = ("CONFIRMED move: escalate into startup-version-factor-matrix to find which "
                      "factor carries it")
        else:
            action = "re-run this cell with the same recipe to confirm or dismiss"
        print(f"  next action: {action}")
        out["|".join(key)] = {"baseline_median": base_median, "band_pct": band,
                              "latest_delta_pct": latest, "runs": len(runs),
                              "next_action": action}

    if not groups:
        print("store has no usable records yet")

    cells = version_comparison(records)
    multi = {cell: versions for cell, versions in cells.items() if len(versions) > 1}
    if multi:
        print("\n" + "=" * 78)
        print("VERSION COMPARISON - different SDK versions on the same device and recipe.")
        print("Separate from the drift report above: a difference here is deliberate (the SDK "
              "changed), not drift. With one run per version it is a candidate finding, not a "
              "verdict - repeat both versions before calling it real.")
        for cell, versions in sorted(multi.items()):
            device, build, compile_state, instrument = cell
            print(f"\n{device}  [build={build} compile={compile_state} instrument={instrument}]")
            rows = []
            for version in sorted(versions):
                medians = [d["median"] for d in versions[version] if d.get("median")]
                total_n = sum(d.get("n", 0) for d in versions[version])
                if medians:
                    rows.append((version, statistics.median(medians), total_n,
                                 len(versions[version])))
            if not rows:
                continue
            newest = max(rows, key=lambda r: r[0])
            for version, median, total_n, runs in rows:
                delta = ("" if version == newest[0]
                         else f"   {100.0 * (median - newest[1]) / newest[1]:+.1f}% vs {newest[0]}")
                print(f"  {version:<12} median {median:7.1f} ms   n={total_n:<5} "
                      f"runs={runs}{delta}")

    print("\nReminders: baselines are fixed, not rolling (a rolling baseline hides slow drift); "
          "one run is a candidate, two make a regression; never compare across device keys or "
          "recipes.")
    if args.json:
        pathlib.Path(args.json).write_text(json.dumps(out, indent=1))
        print(f"wrote {args.json}")


if __name__ == "__main__":
    main()
