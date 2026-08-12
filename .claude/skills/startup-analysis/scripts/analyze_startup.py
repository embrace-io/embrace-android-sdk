#!/usr/bin/env python3
"""Aggregate SDK startup metrics purely from macrobenchmark .perfetto-trace files.

Runs perfetto trace_processor with startup_metrics.sql against every iteration trace in a
directory and prints min/median/mean/max per metric, each canonical section's share of the
SDK-init window, and a per-iteration scheduler-contention readout. No logcat, jq, grep, or
benchmark-JSON involvement. The window is the emb-sdk-start slice when the SDK emits it
(9.2.0+), else composed from modules-init start -> post-services-setup end.

The summary is also written to a uniquely named file,
<output-dir>/startup-analysis-<YYYY-MM-DD-HHMMSS>.txt (analysis start time), so successive
runs never clobber each other; --output-dir overrides the default <repo>/claude-output.

Usage:
  python3 analyze_startup.py --trace-processor <path-to-launcher-or-binary> <traces-dir>

The trace_processor launcher is a python script; fetch it once with:
  curl -sL -o <scratchpad>/trace_processor https://get.perfetto.dev/trace_processor
(analyze_startup.py runs it via this same python3 when it is not executable).

Notes:
- Section duration = FIRST occurrence of each emb-* slice (TraceSectionMetric Mode.First
  semantics). Sections absent from a trace are reported as missing, never as zero.
- ttid_ms comes from trace_processor's android.startup module; its anchor differs from
  macrobenchmark's timeToInitialDisplayMs by a few ms (consistently), so compare like with
  like across runs.
- wait_ms is main-thread R/R+ time inside the SDK-init window; iterations where it exceeds
  CONTENTION_THRESHOLD of the window are flagged CONTENDED (see references/sections.md for
  why that matters when judging regressions).
"""

import argparse
import contextlib
import csv
import datetime
import io
import os
import re
import statistics
import subprocess
import sys

CONTENTION_THRESHOLD = 0.15

# Canonical init sections in execution order (see references/sections.md); value = nesting depth.
CANONICAL_SECTIONS = [
    ("emb-embrace-impl-init", 0),
    ("emb-bootstrapper-init", 1),
    ("emb-modules-init", 0),
    ("emb-persisted-config-load", 1),
    ("emb-config-service-init", 1),
    ("emb-span-service-init", 1),
    ("emb-otel-tracer-init", 2),
    ("emb-essential-service-init", 1),
    ("emb-delivery-init", 1),
    ("emb-payload-source-init", 1),
    ("emb-post-init", 0),
    ("emb-post-services-setup", 0),
    ("emb-load-instrumentation", 1),
]


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("traces_dir", help="directory containing *.perfetto-trace files")
    parser.add_argument("--trace-processor", required=True, dest="tp",
                        help="path to the trace_processor launcher/binary")
    parser.add_argument("--all-sections", action="store_true",
                        help="list every emb-* section (default: canonical + top 15 others)")
    parser.add_argument("--output-dir", default=None,
                        help="directory for the timestamped summary file "
                             "(default: <repo>/claude-output)")
    args = parser.parse_args()

    start = datetime.datetime.now()
    script_dir = os.path.dirname(os.path.abspath(__file__))
    sql = os.path.join(script_dir, "startup_metrics.sql")
    # scripts/ -> startup-analysis/ -> skills/ -> .claude/ -> repo root
    repo_root = os.path.dirname(os.path.dirname(os.path.dirname(os.path.dirname(script_dir))))
    out_dir = args.output_dir or os.path.join(repo_root, "claude-output")
    traces = sorted(
        (f for f in os.listdir(args.traces_dir) if f.endswith(".perfetto-trace")),
        key=iter_index,
    )
    if not traces:
        print(f"no .perfetto-trace files in {args.traces_dir}", file=sys.stderr)
        return 1

    per_trace = []
    for name in traces:
        path = os.path.join(args.traces_dir, name)
        per_trace.append((name, extract_metrics(args.tp, sql, path)))

    buf = io.StringIO()
    with contextlib.redirect_stdout(buf):
        print(f"startup analysis started {start:%Y-%m-%d %H:%M:%S}")
        print(f"traces dir: {os.path.abspath(args.traces_dir)}")
        report(per_trace, args.all_sections)
    text = buf.getvalue()
    sys.stdout.write(text)

    os.makedirs(out_dir, exist_ok=True)
    out_path = os.path.join(out_dir, f"startup-analysis-{start:%Y-%m-%d-%H%M%S}.txt")
    with open(out_path, "w") as f:
        f.write(text)
    print(f"\nsummary written to {out_path}")
    return 0


def extract_metrics(tp: str, sql: str, trace_path: str) -> dict:
    """Run trace_processor -q sql on one trace; return {'sections': {...}, scalars...}."""
    cmd = [tp, "-q", sql, trace_path]
    if not os.access(tp, os.X_OK):
        cmd = [sys.executable] + cmd
    proc = subprocess.run(cmd, capture_output=True, text=True)
    if proc.returncode != 0:
        raise RuntimeError(f"trace_processor failed on {trace_path}:\n{proc.stderr}")

    metrics = {"sections": {}}
    rows = csv.reader(io.StringIO(proc.stdout))
    seen_header = False
    for row in rows:
        if row == ["what", "k", "val"]:
            seen_header = True
            continue
        if not seen_header or len(row) != 3:
            continue
        what, k, raw = row
        if raw == "[NULL]":
            continue
        if what == "window_source":
            metrics["window_source"] = k
            continue
        val = float(raw)
        if what == "section":
            metrics["sections"][k] = val
        else:
            metrics[what] = val
    if not seen_header:
        raise RuntimeError(f"no result header in trace_processor output for {trace_path}")
    return metrics


def report(per_trace: list, all_sections: bool) -> None:
    n = len(per_trace)
    windows = [m["window_ms"] for _, m in per_trace if "window_ms" in m]
    ttids = [m["ttid_ms"] for _, m in per_trace if "ttid_ms" in m]

    sources = {m.get("window_source", "?") for _, m in per_trace}
    if sources == {"emb-sdk-start"}:
        window_desc = "emb-sdk-start slice (wraps Embrace.start(); ~<1 ms wider than the exported emb-embrace-init span)"
    elif sources == {"composed"}:
        window_desc = "composed: modules-init start -> post-services-setup end (= exported emb-embrace-init span; emb-sdk-start absent in this SDK version)"
    else:
        window_desc = f"MIXED sources across traces: {sorted(sources)} — do not compare iterations across sources"

    print(f"traces analyzed: {n}")
    print()
    print(f"SDK-init window, ms — source: {window_desc}")
    print(f"  {fmt_stats(windows)}")
    print(f"  per-iteration: {', '.join(f'{w:.1f}' for w in windows)}")
    print()
    print("TTID (android.startup module; anchor differs a few ms from macrobenchmark JSON), ms:")
    print(f"  {fmt_stats(ttids)}")
    print()

    window_median = statistics.median(windows) if windows else None
    print("canonical sections, execution order (first-occurrence slice durations, ms):")
    header = f"  {'section':<44}{'n':>4}{'min':>9}{'median':>9}{'max':>9}{'% of win':>10}"
    print(header)
    section_values = collect_sections(per_trace)
    for name, depth in CANONICAL_SECTIONS:
        vals = section_values.pop(name, [])
        label = "  " * depth + ("↳ " if depth else "") + name
        if not vals:
            print(f"  {label:<44}{'-- not instrumented in this SDK version --':>41}")
            continue
        pct = f"{statistics.median(vals) / window_median * 100:.1f}" if window_median else "n/a"
        print(f"  {label:<44}{len(vals):>4}{min(vals):>9.2f}{statistics.median(vals):>9.2f}"
              f"{max(vals):>9.2f}{pct:>10}")
    print()

    section_values.pop("emb-sdk-start", None)  # reported as the window, not a section
    extras = sorted(section_values.items(), key=lambda kv: -statistics.median(kv[1]))
    if not all_sections:
        extras = extras[:15]
    if extras:
        shown = "all" if all_sections else "top 15 by median"
        print(f"other emb-* sections ({shown}; not part of the canonical breakdown):")
        for name, vals in extras:
            print(f"  {name:<44}{len(vals):>4}{min(vals):>9.2f}{statistics.median(vals):>9.2f}"
                  f"{max(vals):>9.2f}")
        print()

    print("main-thread scheduling inside the window (contention / slow-execution check):")
    print(f"  {'iteration':<52}{'window':>8}{'run':>8}{'wait':>8}{'wait%':>7}{'cpus':>6}")
    contended = 0
    for name, m in per_trace:
        window = m.get("window_ms")
        wait = m.get("wait_ms")
        if window is None or wait is None:
            continue
        ratio = wait / window
        flag = ""
        if ratio > CONTENTION_THRESHOLD:
            contended += 1
            flag = "  CONTENDED"
        print(f"  {short(name):<52}{window:>8.1f}{m.get('running_ms', 0):>8.1f}{wait:>8.1f}"
              f"{ratio * 100:>6.1f}%{int(m.get('cpus', 0)):>6}{flag}")
    print(f"  {contended}/{n} iterations contended (wait > {CONTENTION_THRESHOLD:.0%} of window).")
    print("  Two slow signatures (see references/sections.md): high wait% = scheduler contention;")
    print("  low wait% but elevated run time vs a fast pass = slower execution (core placement /")
    print("  clocks). Judge regressions on iterations that show neither.")


def collect_sections(per_trace: list) -> dict:
    out: dict = {}
    for _, m in per_trace:
        for name, val in m["sections"].items():
            out.setdefault(name, []).append(val)
    return out


def fmt_stats(vals: list) -> str:
    if not vals:
        return "no data"
    return (f"n={len(vals)}  min={min(vals):.1f}  median={statistics.median(vals):.1f}  "
            f"mean={statistics.mean(vals):.1f}  max={max(vals):.1f}")


def iter_index(filename: str) -> int:
    m = re.search(r"iter(\d+)", filename)
    if m:
        return int(m.group(1))
    return 1 << 30


def short(filename: str) -> str:
    m = re.search(r"iter\d+.*", filename)
    if m:
        return m.group(0).removesuffix(".perfetto-trace")
    return filename


if __name__ == "__main__":
    sys.exit(main())
