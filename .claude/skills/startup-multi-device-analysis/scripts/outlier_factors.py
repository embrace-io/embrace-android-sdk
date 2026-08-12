#!/usr/bin/env python3
"""Extract the external-factor catalogue (outlier_metrics.sql) for every trace in a dir;
part of the startup-multi-device-analysis skill's factor-forensics pipeline.

Produces the passN-factors.json consumed by factors_report.py.
Usage: python3 outlier_factors.py <trace_processor> <traces-dir> <out.json>
"""

import csv
import io
import json
import os
import re
import subprocess
import sys


def extract(tp, sql, trace_path):
    cmd = [tp, "-q", sql, trace_path]
    if not os.access(tp, os.X_OK):
        cmd = [sys.executable] + cmd
    proc = subprocess.run(cmd, capture_output=True, text=True)
    if proc.returncode != 0:
        raise RuntimeError(f"trace_processor failed on {trace_path}:\n{proc.stderr}")
    m = {"states": {}, "inproc": {}, "othercpu": {}}
    seen = False
    for row in csv.reader(io.StringIO(proc.stdout)):
        if row == ["what", "k", "val"]:
            seen = True
            continue
        if not seen or len(row) != 3 or row[2] == "[NULL]":
            continue
        what, k, val = row[0], row[1], float(row[2])
        if what.startswith("state:"):
            key = what[6:] + (":" + k if k else "")
            m["states"][key] = m["states"].get(key, 0) + val
        elif what in ("inproc", "othercpu"):
            m[what][k] = val
        else:
            m[what] = val
    return m


def main():
    tp, traces_dir, out_path = sys.argv[1], sys.argv[2], sys.argv[3]
    sql = os.path.join(os.path.dirname(os.path.abspath(__file__)), "outlier_metrics.sql")
    traces = sorted(
        (f for f in os.listdir(traces_dir) if f.endswith(".perfetto-trace")),
        key=lambda f: int(re.search(r"iter(\d+)", f).group(1)),
    )
    data = []
    for i, name in enumerate(traces):
        data.append({"trace": name, **extract(tp, sql, os.path.join(traces_dir, name))})
        print(f"{i + 1}/{len(traces)} {name}", flush=True)
    with open(out_path, "w") as f:
        json.dump(data, f)
    print(f"wrote {out_path}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
