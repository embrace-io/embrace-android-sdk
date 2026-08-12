#!/usr/bin/env python3
"""Per-iteration variance analysis over macrobenchmark .perfetto-trace files.

Companion to variance_metrics.sql (same directory); produced the dataset in
startup-variance-analysis-2026-08-11.txt. Usage:
  python3 variance_analysis.py <trace_processor> <traces-dir> [--json PATH] [--out PATH]
      [--little-cpus CPU,CPU,...]

--little-cpus sets the cpu ids that make up the little cluster for the section-E residency
split (default "0,1,2,3"); pass the little_cpus value from device_probe.py's topology JSON
for the target device rather than assuming the default.

--json dumps the raw per-iteration dataset (window, section durations, per-CPU residency,
thread states) for downstream aggregation; --out tees the printed report to a file.

Prints:
  A. per-iteration matrix: window + canonical section durations (spot outliers/trends)
  B. per-section fluctuation stats: median, max, spread, stdev, Pearson r vs window
  C. outlier decomposition: for the slowest iterations, which sections exceed their medians
  D. per-section thread-state split (Running vs blocked S/D/IO), median vs slowest iterations
  E. per-cluster core residency of the main thread inside the window
"""

import argparse
import contextlib
import csv
import io
import json
import os
import re
import statistics
import subprocess
import sys

CANONICAL = [
    "emb-embrace-impl-init",
    "emb-bootstrapper-init",
    "emb-modules-init",
    "emb-persisted-config-load",
    "emb-config-service-init",
    "emb-span-service-init",
    "emb-otel-tracer-init",
    "emb-essential-service-init",
    "emb-delivery-init",
    "emb-payload-source-init",
    "emb-post-init",
    "emb-post-services-setup",
    "emb-load-instrumentation",
]
EXTRAS = [
    "emb-install-native-crash-signal-handlers",
    "emb-load-embrace-native-lib",
    "emb-record-startup",
    "emb-power-service-registration",
    "emb-snapshot-session",
]
SHORT = {
    "emb-embrace-impl-init": "impl",
    "emb-bootstrapper-init": "boot",
    "emb-modules-init": "modules",
    "emb-persisted-config-load": "cfgload",
    "emb-config-service-init": "cfgsvc",
    "emb-span-service-init": "spansvc",
    "emb-otel-tracer-init": "tracer",
    "emb-essential-service-init": "essent",
    "emb-delivery-init": "deliv",
    "emb-payload-source-init": "payload",
    "emb-post-init": "postini",
    "emb-post-services-setup": "postsvc",
    "emb-load-instrumentation": "loadins",
    "emb-install-native-crash-signal-handlers": "sighand",
    "emb-load-embrace-native-lib": "natlib",
    "emb-record-startup": "recstart",
    "emb-power-service-registration": "power",
    "emb-snapshot-session": "snap",
}


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("tp")
    ap.add_argument("traces_dir")
    ap.add_argument("--json", dest="json_path")
    ap.add_argument("--out", dest="out_path")
    ap.add_argument("--little-cpus", dest="little_cpus", default="0,1,2,3",
                     help="comma-separated cpu ids for the little cluster "
                          "(see device_probe.py's little_cpus output)")
    args = ap.parse_args()
    little_cpus = {int(x) for x in args.little_cpus.split(",")}

    sql = os.path.join(os.path.dirname(os.path.abspath(__file__)), "variance_metrics.sql")
    traces = sorted(
        (f for f in os.listdir(args.traces_dir) if f.endswith(".perfetto-trace")),
        key=lambda f: int(re.search(r"iter(\d+)", f).group(1)),
    )
    data = []
    for name in traces:
        data.append((name, extract(args.tp, sql, os.path.join(args.traces_dir, name))))

    if args.json_path:
        with open(args.json_path, "w") as f:
            json.dump([{"trace": name, **m} for name, m in data], f)

    buf = io.StringIO()
    with contextlib.redirect_stdout(buf):
        report(data, little_cpus)
    sys.stdout.write(buf.getvalue())
    if args.out_path:
        with open(args.out_path, "w") as f:
            f.write(buf.getvalue())
    return 0


def report(data, little_cpus) -> None:
    n = len(data)
    windows = [m["window_ms"] for _, m in data]
    wmed = statistics.median(windows)

    # A: per-iteration matrix
    cols = CANONICAL + EXTRAS
    print("A. per-iteration durations, ms")
    print("  it   window " + " ".join(f"{SHORT[c]:>8}" for c in cols))
    for i, (_, m) in enumerate(data):
        row = " ".join(f"{m['dur'].get(c, float('nan')):>8.2f}" for c in cols)
        print(f"  {i:>3} {m['window_ms']:>8.1f} {row}")
    print()

    # B: fluctuation stats + correlation with window
    print("B. per-section fluctuation (n=%d), ms" % n)
    print(f"  {'section':<42}{'min':>7}{'med':>7}{'max':>7}{'spread':>8}{'stdev':>7}{'r_win':>7}")
    for c in cols:
        vals = [m["dur"].get(c) for _, m in data]
        vals = [v for v in vals if v is not None]
        if len(vals) < n:
            continue
        r = pearson(vals, windows)
        print(f"  {c:<42}{min(vals):>7.2f}{statistics.median(vals):>7.2f}{max(vals):>7.2f}"
              f"{max(vals) - min(vals):>8.2f}{statistics.stdev(vals):>7.2f}{r:>7.2f}")
    print()

    # C: outlier decomposition (window sections only; parents include children)
    med = {c: statistics.median([m["dur"][c] for _, m in data if c in m["dur"]]) for c in cols}
    slow = sorted(range(n), key=lambda i: -windows[i])[:4]
    print("C. slowest iterations: section excess vs median (window Δ = window - median window)")
    for i in slow:
        m = data[i][1]
        deltas = sorted(
            ((c, m["dur"].get(c, 0) - med[c]) for c in cols),
            key=lambda kv: -kv[1],
        )[:7]
        ds = ", ".join(f"{SHORT[c]} +{d:.1f}" for c, d in deltas if d > 0.3)
        print(f"  iter{i:03d}  window {windows[i]:.1f} (Δ +{windows[i] - wmed:.1f}): {ds}")
    print()

    # D: thread-state split per section — median vs the slowest iterations
    print("D. thread-state inside each section, ms (median across iters | mean over 4 slowest iters)")
    print(f"  {'section':<42}{'run':>7}{'sleep':>7}{'io/D':>7}{'rq':>6}  |"
          f"{'run':>7}{'sleep':>7}{'io/D':>7}{'rq':>6}")
    for c in cols:
        med_split = split_stats(data, c, range(n))
        slow_split = split_stats(data, c, slow)
        if med_split is None or slow_split is None:
            continue
        print(f"  {c:<42}" + "".join(f"{v:>7.2f}" for v in med_split[:3]) + f"{med_split[3]:>6.2f}"
              + "  |" + "".join(f"{v:>7.2f}" for v in slow_split[:3]) + f"{slow_split[3]:>6.2f}")
    print()

    # E: per-cluster residency inside window (SM-A145M: cluster0 = cpu0-3, cluster1 = cpu4-7,
    # identical A55 cores, independent cpufreq policies)
    cpus = sorted({int(k) for _, m in data for k in m["cpu_ms"]})
    print("E. main-thread CPU residency inside window, ms per cpu")
    print("  it   window " + " ".join(f"cpu{c:>4}" for c in cpus) + "   little-share")
    shares = []
    for i, (_, m) in enumerate(data):
        tot = sum(m["cpu_ms"].values()) or 1.0
        cl0 = sum(v for k, v in m["cpu_ms"].items() if int(k) in little_cpus) / tot
        shares.append(cl0)
        row = " ".join(f"{m['cpu_ms'].get(str(c), 0):>7.1f}" for c in cpus)
        print(f"  {i:>3} {m['window_ms']:>8.1f} {row}   {cl0 * 100:>5.1f}%")
    print(f"  r(window, little-share) = {pearson(windows, shares):.2f}")
    cl0_wins = [w for w, s in zip(windows, shares) if s > 0.5]
    cl1_wins = [w for w, s in zip(windows, shares) if s <= 0.5]
    if cl0_wins and cl1_wins:
        print(f"  little-majority: n={len(cl0_wins)} mean={statistics.mean(cl0_wins):.1f}"
              f"  |  cluster1-majority: n={len(cl1_wins)} mean={statistics.mean(cl1_wins):.1f}")


def split_stats(data, section, idxs):
    runs, sleeps, ios, rqs = [], [], [], []
    for i in idxs:
        m = data[i][1]
        if section not in m["dur"]:
            return None
        st = m["states"].get(section, {})
        runs.append(st.get("Running", 0))
        sleeps.append(st.get("S", 0))
        ios.append(st.get("D", 0) + st.get("D+io", 0) + st.get("DK", 0) + st.get("DK+io", 0)
                   + st.get("S+io", 0))
        rqs.append(st.get("R", 0) + st.get("R+", 0))
    agg = statistics.median if len(list(idxs)) > 6 else statistics.mean
    return (agg(runs), agg(sleeps), agg(ios), agg(rqs))


def extract(tp, sql, trace_path):
    cmd = [tp, "-q", sql, trace_path]
    if not os.access(tp, os.X_OK):
        cmd = [sys.executable] + cmd
    proc = subprocess.run(cmd, capture_output=True, text=True)
    if proc.returncode != 0:
        raise RuntimeError(f"trace_processor failed on {trace_path}:\n{proc.stderr}")
    m = {"dur": {}, "cpu_ms": {}, "states": {}}
    seen = False
    for row in csv.reader(io.StringIO(proc.stdout)):
        if row == ["what", "k", "val"]:
            seen = True
            continue
        if not seen or len(row) != 3 or row[2] == "[NULL]":
            continue
        what, k, val = row[0], row[1], float(row[2])
        if what == "window_ms":
            m["window_ms"] = val
        elif what == "cpu_ms":
            m["cpu_ms"][k] = val
        elif what == "dur":
            m["dur"][k] = val
        elif what.startswith("st:"):
            m["states"].setdefault(k, {})[what[3:]] = val
    return m


def pearson(xs, ys):
    mx, my = statistics.mean(xs), statistics.mean(ys)
    num = sum((x - mx) * (y - my) for x, y in zip(xs, ys))
    den = (sum((x - mx) ** 2 for x in xs) * sum((y - my) ** 2 for y in ys)) ** 0.5
    if den == 0:
        return float("nan")
    return num / den


if __name__ == "__main__":
    sys.exit(main())
