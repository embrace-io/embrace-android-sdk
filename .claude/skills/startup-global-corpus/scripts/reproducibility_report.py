#!/usr/bin/env python3
"""Per-cell reproducibility: do independent contributors on the same model agree?

Verdicts are given per statistic (median / tail / shape) because they fail for different reasons and
a single "agrees" hides the informative case. Tolerance is derived from the submissions' OWN
within-run spread rather than a fixed percentage: a cell whose passes vary by 8% cannot demand 2%
agreement between contributors.

Where a cell disagrees, the report ranks provenance DIFFS as candidate explanations - the dimension
hunt in references/reproducibility.md. It never averages a disagreeing cell.

Usage:
  python3 reproducibility_report.py --corpus corpus.jsonl [--json out.json]
"""
import argparse
import json
import pathlib
import statistics
import sys
from collections import defaultdict

# Ordered most-to-least common cause, per the dimension hunt.
HUNT_FIELDS = ["recipe", "installed_app_count", "os_build", "conditions", "gate_temp_c",
               "battery_health", "storage_free_pct", "device_settings", "kernel_version",
               "security_patch", "skin_version", "tool_versions"]


def cell_key(rec):
    recipe = rec.get("recipe") or {}
    return (rec.get("model") or "?", rec.get("os_build") or "?", rec.get("sdk_version") or "?",
            f"{recipe.get('build_type')}/{recipe.get('compile_state')}/{recipe.get('instrument')}",
            json.dumps(rec.get("conditions") or {}, sort_keys=True))


def own_spread_pct(rec):
    """Within-submission spread of pass medians, as a percentage - the basis for tolerance."""
    passes = list((rec.get("derived") or {}).get("pass_medians", {}).values())
    median = (rec.get("derived") or {}).get("median")
    if len(passes) < 2 or not median:
        return None
    return 100.0 * (max(passes) - min(passes)) / median


def shape_of(rec):
    """Crude but useful: does the run look two-state (compile-state alternation) or unimodal?"""
    passes = sorted((rec.get("derived") or {}).get("pass_medians", {}).values())
    median = (rec.get("derived") or {}).get("median")
    if len(passes) < 4 or not median:
        return "unknown"
    gap = max(passes[i + 1] - passes[i] for i in range(len(passes) - 1))
    return "two-state" if gap > 0.10 * median else "unimodal"


def diffs(recs, field):
    values = {json.dumps(r.get(field), sort_keys=True) if isinstance(r.get(field), (dict, list))
              else str(r.get(field)) for r in recs}
    return sorted(values) if len(values) > 1 else None


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--corpus", required=True)
    ap.add_argument("--json")
    args = ap.parse_args()

    path = pathlib.Path(args.corpus)
    if not path.exists():
        sys.exit(f"no corpus at {path}")
    records = []
    for line in path.read_text().splitlines():
        if line.strip():
            try:
                records.append(json.loads(line))
            except ValueError:
                print("skipping a malformed corpus line")

    cells = defaultdict(list)
    for rec in records:
        if (rec.get("derived") or {}).get("n"):
            cells[cell_key(rec)].append(rec)

    out = {}
    for key, recs in sorted(cells.items()):
        model, os_build, sdk, recipe, conditions = key
        contributors = {r.get("contributor") for r in recs}
        units = {r.get("unit_id") for r in recs}
        schema_versions = {r.get("schema_version") for r in recs}
        print(f"\n{'=' * 78}\n{model}  sdk={sdk}\n  build={os_build}\n  recipe={recipe} "
              f"conditions={conditions}")
        print(f"  submissions={len(recs)} contributors={len(contributors)} units={len(units)}"
              + (f"  schema_versions={sorted(schema_versions)}" if len(schema_versions) > 1 else ""))

        if len(contributors) < 2 and len(units) < 2:
            print("  insufficient data: needs a second contributor or unit before reproducibility "
                  "can be tested at all")
            out["|".join(key)] = {"status": "insufficient data", "submissions": len(recs)}
            continue

        medians = [r["derived"]["median"] for r in recs]
        p90s = [r["derived"]["p90"] for r in recs]
        spreads = [s for s in (own_spread_pct(r) for r in recs) if s is not None]
        tol = max(5.0, max(spreads) if spreads else 5.0)
        med_spread = 100.0 * (max(medians) - min(medians)) / statistics.median(medians)
        p90_spread = 100.0 * (max(p90s) - min(p90s)) / statistics.median(p90s)
        shapes = {shape_of(r) for r in recs}

        med_v = "agree" if med_spread <= tol else "MEDIANS DIFFER"
        p90_v = "agree" if p90_spread <= tol else "TAILS DIFFER"
        shape_v = "agree" if len(shapes - {"unknown"}) <= 1 else "SHAPE DIFFERS"
        print(f"  tolerance from own within-run spread: +-{tol:.0f}%")
        print(f"  median  spread {med_spread:>5.1f}%  -> {med_v}")
        print(f"  p90     spread {p90_spread:>5.1f}%  -> {p90_v}")
        print(f"  shape   {sorted(shapes)}  -> {shape_v}")

        reproduced = all(v == "agree" for v in (med_v, p90_v, shape_v))
        if reproduced:
            pooled = sorted(w for r in recs for w in r.get("windows_ms", []))
            if pooled:
                def pct(p):
                    return pooled[min(len(pooled) - 1, int(p * len(pooled)))]
                line = (f"  REPRODUCED - pooled n={len(pooled)} across {len(contributors)} "
                        f"contributors/{len(units)} units: median {statistics.median(pooled):.1f} "
                        f"p90 {pct(0.90):.1f} p95 {pct(0.95):.1f}")
                line += f" p99 {pct(0.99):.1f}" if len(pooled) >= 500 else " (n<500: no p99)"
                print(line)
            status = "reproduced"
            candidates = []
        else:
            candidates = [(f, diffs(recs, f)) for f in HUNT_FIELDS if diffs(recs, f)]
            print("  NOT REPRODUCED - do not pool, do not average. Candidate dimensions, most "
                  "common cause first:")
            if candidates:
                for field, values in candidates[:6]:
                    shown = [v[:60] for v in values[:3]]
                    print(f"    - {field}: {shown}")
            else:
                print("    - none: every recorded dimension matches, so the responsible dimension "
                      "is NOT yet in the schema. Mark unresolved and hunt it (see "
                      "references/reproducibility.md); if found, it becomes a required field.")
            status = "unresolved" if not candidates else "unresolved (candidates found)"
        out["|".join(key)] = {"status": status, "median_spread_pct": med_spread,
                              "p90_spread_pct": p90_spread, "tolerance_pct": tol,
                              "contributors": len(contributors), "units": len(units),
                              "candidates": [c[0] for c in candidates]}

    if not cells:
        print("corpus has no usable records yet")
    print("\nReminders: pool only inside a reproduced cell; never pool across models to make a "
          "fleet number (composition artefact); never widen tolerance to force agreement - that "
          "disables the only mechanism that finds unaccounted dimensions.")
    if args.json:
        pathlib.Path(args.json).write_text(json.dumps(out, indent=1))
        print(f"wrote {args.json}")


if __name__ == "__main__":
    main()
