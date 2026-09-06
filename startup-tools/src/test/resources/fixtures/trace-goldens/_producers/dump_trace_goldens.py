#!/usr/bin/env python3
"""Freeze the Python trace-layer outputs on the fixture traces - the Layer-B oracle.

For every fixture trace under fixtures/traces/<device>/pass1/, runs the pinned launcher
(`trace_processor -q <sql> <trace>`) for every SQL file the skills ship plus the inline queries in
ingest_run.py / trace_health.py, and saves the RAW stdout as goldens/trace/<device>/<trace>/<query>.csv.
Then records the Python-side parse of each (trace_health.check_trace verdict, ingest_run's window
parse) as JSON, and finally freezes the analyze_startup.py and trace_health.py CLI stdout per device.

Resumable: a query whose .csv already exists is skipped. Small traces first so partial output is
useful early. Log: goldens/trace/dump.log.
"""
import json
import pathlib
import subprocess
import sys
import time

REPO = pathlib.Path("/Users/hansonho/work/embrace-android-sdk")
PORT = REPO / "claude-output/2026-08-26-kotlin-port"
TRACES = PORT / "fixtures/traces"
OUT = PORT / "goldens/trace"
TP = pathlib.Path.home() / ".cache/embrace-startup-tools/trace_processor/v46.0/trace_processor"
SKILLS = REPO / ".claude/skills"
sys.path.insert(0, str(SKILLS / "_shared"))
sys.path.insert(0, str(SKILLS / "startup-longitudinal-tracking/scripts"))
import trace_health  # noqa: E402
import ingest_run  # noqa: E402

DEVICE_ORDER = ["mid-b", "mid-a", "flagship-a"]
CANARY = "emb-sdk-start"

SQL_FILES = {
    "startup_metrics": SKILLS / "startup-analysis/scripts/startup_metrics.sql",
    "init_window_sched": SKILLS / "startup-analysis/scripts/init_window_sched.sql",
    "variance_metrics": SKILLS / "startup-multi-device-analysis/scripts/variance_metrics.sql",
    "outlier_metrics": SKILLS / "startup-multi-device-analysis/scripts/outlier_metrics.sql",
    "foreign_gc_overlap": SKILLS / "startup-multi-device-analysis/scripts/foreign_gc_overlap.sql",
}
INLINE = {
    "health": trace_health.HEALTH_SQL.format(canary=CANARY),
    "window_emb_sdk_start": ingest_run.WINDOW_SQL.format(slice=CANARY),
    "window_composed": ingest_run.COMPOSED_WINDOW_SQL,
    "signals": ingest_run.SIGNALS_SQL,
}


def run_q(sql_path, trace):
    cp = subprocess.run([sys.executable, str(TP), "-q", str(sql_path), str(trace)],
                        capture_output=True, text=True, timeout=1800)
    return cp.returncode, cp.stdout, cp.stderr


def window_parse(stdout):
    """ingest_run.window_from_trace's parse, applied to saved stdout."""
    for line in reversed(stdout.strip().splitlines()):
        try:
            return float(line.strip().strip('"'))
        except ValueError:
            continue
    return None


def signals_parse(stdout):
    names = []
    for line in stdout.splitlines():
        value = line.strip().strip('"')
        if value.startswith("emb-"):
            names.append(value)
    return sorted(set(names))


def health_rows_parse(stdout):
    """trace_health._rows' parse, applied to saved stdout."""
    out = {}
    for line in stdout.splitlines():
        parts = [p.strip().strip('"') for p in line.split(",")]
        if len(parts) == 2 and parts[0] not in ("k", ""):
            try:
                out[parts[0]] = int(float(parts[1]))
            except ValueError:
                continue
    return out


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    inline_dir = OUT / "_sql"
    inline_dir.mkdir(exist_ok=True)
    for name, text in INLINE.items():
        (inline_dir / f"{name}.sql").write_text(text)
    queries = list(SQL_FILES.items()) + [(n, inline_dir / f"{n}.sql") for n in INLINE]
    log = open(OUT / "dump.log", "a")
    log.write(f"=== start {time.strftime('%Y-%m-%dT%H:%M:%S')} python {sys.version.split()[0]}\n")

    for device in DEVICE_ORDER:
        device_dir = TRACES / device
        traces = sorted(device_dir.rglob("*.perfetto-trace"))
        for trace in traces:
            stem = trace.name.removesuffix(".perfetto-trace")
            tdir = OUT / device / stem
            tdir.mkdir(parents=True, exist_ok=True)
            for qname, sql_path in queries:
                out_file = tdir / f"{qname}.csv"
                if out_file.exists():
                    continue
                t0 = time.time()
                rc, so, se = run_q(sql_path, trace)
                out_file.write_text(so)
                if rc != 0 or se.strip():
                    (tdir / f"{qname}.stderr.txt").write_text(f"rc={rc}\n{se}")
                log.write(f"{device} {stem} {qname} rc={rc} {time.time() - t0:.1f}s\n")
                log.flush()
            parsed_file = tdir / "python_parsed.json"
            if not parsed_file.exists():
                t0 = time.time()
                verdict = trace_health.check_trace(TP, trace, CANARY)
                verdict["trace"] = trace.name
                parsed = {
                    "health_check_trace": verdict,
                    "health_rows_from_saved_stdout": health_rows_parse((tdir / "health.csv").read_text()),
                    "window_emb_sdk_start_ms": window_parse((tdir / "window_emb_sdk_start.csv").read_text()),
                    "window_composed_ms": window_parse((tdir / "window_composed.csv").read_text()),
                    "signals": signals_parse((tdir / "signals.csv").read_text()),
                }
                parsed_file.write_text(json.dumps(parsed, indent=1, sort_keys=True) + "\n")
                log.write(f"{device} {stem} python_parsed {time.time() - t0:.1f}s\n")
                log.flush()

    cli_dir = OUT / "_cli"
    cli_dir.mkdir(exist_ok=True)
    for device in DEVICE_ORDER:
        device_dir = TRACES / device
        target = cli_dir / f"analyze_startup.{device}.stdout.txt"
        if not target.exists():
            t0 = time.time()
            cp = subprocess.run(
                [sys.executable, str(SKILLS / "startup-analysis/scripts/analyze_startup.py"),
                 "--trace-processor", str(TP), "--output-dir", str(cli_dir / "analyze_out"),
                 str(device_dir / "pass1")],
                capture_output=True, text=True, timeout=7200)
            target.write_text(cp.stdout)
            (cli_dir / f"analyze_startup.{device}.stderr.txt").write_text(f"rc={cp.returncode}\n{cp.stderr}")
            log.write(f"CLI analyze_startup {device} rc={cp.returncode} {time.time() - t0:.1f}s\n")
            log.flush()
        target = cli_dir / f"trace_health.{device}.stdout.txt"
        if not target.exists():
            t0 = time.time()
            cp = subprocess.run(
                [sys.executable, str(SKILLS / "_shared/trace_health.py"), str(device_dir),
                 "--trace-processor", str(TP)],
                capture_output=True, text=True, timeout=7200)
            target.write_text(cp.stdout)
            (cli_dir / f"trace_health.{device}.stderr.txt").write_text(f"rc={cp.returncode}\n{cp.stderr}")
            log.write(f"CLI trace_health {device} rc={cp.returncode} {time.time() - t0:.1f}s\n")
            log.flush()
    log.write(f"=== done {time.strftime('%Y-%m-%dT%H:%M:%S')}\n")
    log.close()


if __name__ == "__main__":
    main()
