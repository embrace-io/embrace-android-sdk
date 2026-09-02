#!/usr/bin/env python3
"""Expand a matrix plan into an ordered, interleaved cell list with a wall-clock estimate.

Dry-run by default: prints what WOULD run and what it would change, so a plan that does not fit
the available window is trimmed before any device time is spent. Trim cells, never iterations.

Usage:
  python3 matrix_plan.py plan.json                 # print plan + estimate + checklist
  python3 matrix_plan.py plan.json --emit cells.json   # also write the ordered cell list
"""
import argparse
import json
import pathlib
import sys

# Rough per-launch seconds by TIER, for budgeting only. Calibrate against your own first cell:
# the estimate exists to stop you planning 20 h of work into an 8 h window, not to be precise.
LAUNCH_SECONDS = {"flagship": 7.0, "mid": 9.0, "entry": 20.0}
DEFAULT_LAUNCH_SECONDS = 10.0
PASS_OVERHEAD_S = 180.0   # gradle invocation, install, cool gate, trace copy
COOL_GATE_S = 300.0       # typical wait per pass; longer from a hot start


def cell_id(version, levels, device):
    tags = [f"{k}={v}" for k, v in sorted(levels.items())]
    return f"{device}|{version}|" + (",".join(tags) if tags else "reference")


def build_cells(plan):
    ref = plan["reference"]
    dev = plan["primary_device"]
    cells = []

    # 1. version sweep in the reference cell - the primary deliverable
    for version in plan["versions"]:
        cells.append({"id": cell_id(version, {}, dev), "version": version,
                      "levels": dict(ref), "device": dev, "group": "version-sweep"})

    # 2. one factor moved off reference, at anchor versions only
    for factor, levels in plan.get("factor_levels", {}).items():
        for level in levels:
            for version in plan["anchors"]:
                merged = dict(ref)
                merged[factor] = level
                cells.append({"id": cell_id(version, {factor: level}, dev),
                              "version": version, "levels": merged, "device": dev,
                              "group": f"factor:{factor}={level}"})

    # 3. deliberate multi-factor combos (real user populations, not convenience)
    for combo in plan.get("combos", []):
        merged = dict(ref)
        merged.update(combo["levels"])
        device = combo.get("device") or dev
        cells.append({"id": cell_id(combo["version"], combo["levels"], device),
                      "version": combo["version"], "levels": merged, "device": device,
                      "group": f"combo:{combo['id']}"})
    return cells


def interleave(cells):
    """Order cells: ALL reference (version-sweep) cells first, then factor/combo cells
    round-robin across groups.

    Two constraints, both learned the hard way:
      * every factor cell's effect is reported against its own version's reference cell, so
        references must exist first - an interrupted run then still yields interpretable data
        rather than orphaned factor cells;
      * within the factor phase, round-robin across groups so one group does not sit entirely
        in a single thermal/temporal regime (pass-level A B B A is cell_runner's job).
    """
    refs = [c for c in cells if c["group"] == "version-sweep"]
    rest = [c for c in cells if c["group"] != "version-sweep"]
    by_group = {}
    for c in rest:
        by_group.setdefault(c["group"], []).append(c)
    ordered, groups = list(refs), list(by_group.values())
    while any(groups):
        for g in groups:
            if g:
                ordered.append(g.pop(0))
    return ordered


def estimate_seconds(cell, plan):
    tier = (plan.get("devices", {}).get(cell["device"], {}) or {}).get("tier")
    per_launch = LAUNCH_SECONDS.get(tier, DEFAULT_LAUNCH_SECONDS)
    launches = plan["passes"] * plan["iterations"]
    extra = 0.0
    if cell["levels"].get("install") in ("fresh", "updated"):
        extra += launches * 12.0   # an install per measured launch
    if cell["levels"].get("thermal") == "hot":
        extra += plan["passes"] * 600.0  # heating to band before each pass
    return launches * per_launch + plan["passes"] * (PASS_OVERHEAD_S + COOL_GATE_S) + extra


CHECKLIST = """
Controls this run depends on (cell_runner enforces the machine-checkable ones):
  [runner] resolved SDK coordinate matches the cell (read back, not from the catalog file)
  [runner] compile state matches the cell (dumpsys package dexopt, recorded per cell)
  [runner] APK sha256 recorded; unexpected changes flagged (git state alters the build id)
  [runner] temperature within band before each pass, from thermalservice - NOT dumpsys battery
  [runner] no second driver alive, no gradle build running, host load under threshold
  [runner] expected window instrument present in pass 1 traces (app-embrace-start wrapper)
  [human ] app source untouched between cells; no commit/sync mid-campaign
  [human ] launch-index policy declared (settled discards launches 0-2)
  [human ] device screen/charging/airplane state left to the runner to save and restore
  [human ] compat patches reverted and the version pin restored when the run ends
"""


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("plan")
    ap.add_argument("--emit", help="write the ordered cell list here")
    args = ap.parse_args()

    plan = json.loads(pathlib.Path(args.plan).read_text())
    for key in ("run_id", "primary_device", "devices", "passes", "iterations", "reference",
                "versions", "anchors"):
        if key not in plan:
            sys.exit(f"plan is missing required key: {key} (see plan-example.json)")

    # Any cell may name a device other than the primary (combos deliberately do). Every such
    # device needs a serial, or the runner would silently drive the primary device instead.
    devices = plan["devices"]
    needed = {plan["primary_device"]} | {c.get("device") or plan["primary_device"]
                                        for c in plan.get("combos", [])}
    missing = sorted(d for d in needed if d not in devices)
    if missing:
        sys.exit(f"plan references device key(s) {missing} with no entry in \"devices\" - add one "
                 f"per device with its serial and profile (see plan-example.json)")
    for key, cfg in devices.items():
        for field in ("serial", "api_level", "tier", "vendor"):
            if not cfg.get(field):
                print(f"WARNING: device '{key}' is missing '{field}'. The profile travels with "
                      f"every result and is what makes runs comparable later - fill it in.")
    # Pass count is the binding constraint, not iteration count: the permutation floor is
    # 2/C(2G,G), so below four passes per arm no result can reach alpha = 0.05 whatever the effect
    # size, and precision at a fixed launch budget scales with passes alone.
    if plan["passes"] < 4:
        print(f"WARNING: {plan['passes']} passes per arm cannot reach significance at all - the "
              f"smallest attainable p-value is above 0.05. Add passes or state plainly that the "
              f"cell is descriptive only.")
    elif plan["passes"] < 10:
        print(f"WARNING: {plan['passes']}x{plan['iterations']} is weaker than the scoped 10x20 "
              f"shape - precision and the p-value floor both improve with passes, not iterations. "
              f"Say so in the report.")

    cells = interleave(build_cells(plan))
    total = 0.0
    night_budget_s = float(plan.get("night_budget_hours", 8)) * 3600
    night, night_used = 1, 0.0
    print(f"\nrun {plan['run_id']}: {len(cells)} cells, {plan['passes']}x{plan['iterations']} each, "
          f"build={plan.get('build_type', 'benchmark')}, "
          f"night budget {night_budget_s / 3600:.0f} h\n")
    print(f"{'#':>3}  {'cell':<58}{'device':<8}{'group':<26}{'est':>7}")
    for i, c in enumerate(cells, 1):
        secs = estimate_seconds(c, plan)
        if night_used + secs > night_budget_s and night_used > 0:
            print(f"     {'-' * 40} night {night} full ({night_used / 3600:.1f} h) {'-' * 12}")
            night += 1
            night_used = 0.0
        night_used += secs
        total += secs
        print(f"{i:>3}  {c['id']:<58}{c['device']:<8}{c['group']:<26}{secs / 60:>6.0f}m")
    print(f"\ntotal estimate: {total / 3600:.1f} h across {night} night(s); "
          f"{len([c for c in cells if c['group'] == 'version-sweep'])} version-sweep (reference) "
          f"cells run FIRST so factor deltas always have a baseline")
    print(CHECKLIST)

    if args.emit:
        pathlib.Path(args.emit).write_text(json.dumps({"plan": plan, "cells": cells}, indent=1))
        print(f"wrote {args.emit} - run cells with: cell_runner.py --cells {args.emit} --cell <id>")


if __name__ == "__main__":
    main()
