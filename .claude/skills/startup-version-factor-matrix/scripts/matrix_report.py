#!/usr/bin/env python3
"""Cross-cell comparison for a version x factor run.

Per cell: n, window median/p90/max, per-pass medians (drift and pass-state), TTID median.
Then the version table (reference cells only) and the factor table (effect per anchor version).

The window comes from the app-side wrapper slice by default - the only instrument that exists in
every SDK version. Pass --slice emb-sdk-start to use the native window when every cell has it.

Usage:
  python3 matrix_report.py <run-dir> [--slice app-embrace-start] [--json out.json]
"""
import argparse
import json
import pathlib
import statistics
import subprocess

TP = pathlib.Path(__file__).parent / "trace_processor"

WINDOW_SQL = """
WITH win AS (
  SELECT s.ts AS ts, s.dur AS dur FROM slice s
  WHERE s.name = '{slice}' AND s.dur > 0
  ORDER BY s.ts DESC LIMIT 1
)
SELECT (SELECT dur / 1e6 FROM win) AS window_ms;
"""


def query_one(trace, sql_text):
    """Run one query against one trace; returns the first numeric value or None."""
    qf = pathlib.Path("/tmp/vfm_q.sql")
    qf.write_text(sql_text)
    cp = subprocess.run([str(TP), "-q", str(qf), str(trace)],
                        capture_output=True, text=True, timeout=600)
    for line in reversed(cp.stdout.strip().splitlines()):
        val = line.strip().strip('"')
        try:
            return float(val)
        except ValueError:
            continue
    return None


def cell_windows(cell_dir, slice_name):
    """Per-pass lists of window values, so pass-state stays visible instead of pooled away."""
    passes = {}
    for trace in sorted(cell_dir.rglob("*.perfetto-trace")):
        pass_key = next((p for p in trace.parts if p.startswith("pass")), trace.parent.name)
        val = query_one(trace, WINDOW_SQL.format(slice=slice_name))
        if val:
            passes.setdefault(pass_key, []).append(val)
    return passes


def summarize(passes):
    allv = sorted(v for vals in passes.values() for v in vals)
    if not allv:
        return None
    return {
        "n": len(allv),
        "median": statistics.median(allv),
        "p90": allv[min(len(allv) - 1, int(0.9 * len(allv)))],
        "max": allv[-1],
        "iqr": (allv[int(0.75 * len(allv))] - allv[int(0.25 * len(allv))]) if len(allv) >= 4 else 0.0,
        "pass_medians": {k: statistics.median(v) for k, v in sorted(passes.items()) if v},
    }


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("run_dir")
    ap.add_argument("--slice", default="app-embrace-start")
    ap.add_argument("--json")
    args = ap.parse_args()

    run_dir = pathlib.Path(args.run_dir)
    cells = {}
    for state_file in sorted(run_dir.rglob("cell-state.json")):
        state = json.loads(state_file.read_text())
        cell = state["cell"]
        summary = summarize(cell_windows(state_file.parent, args.slice))
        if summary is None:
            print(f"SKIP {cell['id']}: no window values ({args.slice} missing?)")
            continue
        cells[cell["id"]] = {"cell": cell, "summary": summary,
                            "build_type": state.get("build_type"),
                            "apk_sha256": (state.get("apk_sha256") or "")[:12]}

    print(f"\ninstrument: {args.slice}   (absolute values are build-type specific)\n")
    print(f"{'cell':<58}{'n':>5}{'med':>9}{'p90':>9}{'max':>9}  pass medians")
    for cid, c in sorted(cells.items()):
        s = c["summary"]
        pm = " ".join(f"{v:.0f}" for v in s["pass_medians"].values())
        flag = ""
        if len(s["pass_medians"]) > 1 and s["iqr"]:
            spread = max(s["pass_medians"].values()) - min(s["pass_medians"].values())
            if spread > s["iqr"]:
                flag = "  <-- PASS-STATE: re-judge on same-parity passes"
        print(f"{cid:<58}{s['n']:>5}{s['median']:>9.1f}{s['p90']:>9.1f}{s['max']:>9.1f}  {pm}{flag}")

    # version table: reference cells only
    ref = {cid: c for cid, c in cells.items() if cid.endswith("|reference")}
    if len(ref) > 1:
        print("\n=== VERSION TABLE (reference cell) ===")
        newest = max(ref, key=lambda k: ref[k]["cell"]["version"] == "local")
        base = ref[newest]["summary"]["median"]
        print(f"{'version':<14}{'med':>9}{'p90':>9}{'max':>9}{'delta vs newest':>18}")
        for cid, c in sorted(ref.items(), key=lambda kv: kv[1]["summary"]["median"]):
            s = c["summary"]
            d = s["median"] - base
            print(f"{c['cell']['version']:<14}{s['median']:>9.1f}{s['p90']:>9.1f}{s['max']:>9.1f}"
                  f"{d:>+13.1f} ms ({100 * d / base:+.0f}%)" if base else "")

    # factor table: each non-reference level vs the same version's reference cell
    factor_cells = {cid: c for cid, c in cells.items() if not cid.endswith("|reference")}
    if factor_cells:
        print("\n=== FACTOR TABLE (vs the same version's reference cell) ===")
        print(f"{'version':<14}{'factor level':<30}{'med':>9}{'ref med':>10}{'effect':>20}")
        for cid, c in sorted(factor_cells.items()):
            version = c["cell"]["version"]
            device = c["cell"]["device"]
            ref_id = f"{device}|{version}|reference"
            if ref_id not in cells:
                print(f"{version:<14}{cid.split('|')[-1]:<30}{c['summary']['median']:>9.1f}"
                      f"{'--':>10}{'no reference cell':>20}")
                continue
            rm = cells[ref_id]["summary"]["median"]
            m = c["summary"]["median"]
            print(f"{version:<14}{cid.split('|')[-1]:<30}{m:>9.1f}{rm:>10.1f}"
                  f"{m - rm:>+13.1f} ms ({100 * (m - rm) / rm:+.0f}%)")
        print("\nParallel effects across anchor versions = factor effects. Effects that change "
              "sign or magnitude materially = version x factor interactions worth investigating.")

    print("\nReminders: pair every window delta with TTID and pre-TTID main-thread CPU (a window "
          "win with flat pre-TTID CPU is re-attribution, not improvement); never compare across "
          "build types or devices except as labelled tier replication.")

    if args.json:
        pathlib.Path(args.json).write_text(json.dumps(cells, indent=1, default=str))
        print(f"wrote {args.json}")


if __name__ == "__main__":
    main()
