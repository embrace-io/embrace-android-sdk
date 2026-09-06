#!/usr/bin/env python3
"""Freeze the report scripts' output on the fixture traces WITHOUT re-parsing any trace.

variance_analysis.py and outlier_factors.py each run trace_processor per trace and parse its stdout.
dump_trace_goldens.py already saved that exact stdout per (trace, query), so here their `extract`
functions are monkeypatched to read the saved CSV instead - byte-for-byte the same input, zero
Perfetto invocations - and their reports / JSON datasets are frozen under goldens/trace/_cli/.
The resulting pass1.json / pass1-factors.json per device are then laid out as a one-pass campaign
directory and fed to factors_report.py, hypothesis_tests.py and cross_device_sections.py.

Run after dump_trace_goldens.py has finished (all <trace>/<query>.csv present).
"""
import contextlib
import io
import json
import os
import pathlib
import shutil
import subprocess
import sys

REPO = pathlib.Path("/Users/hansonho/work/embrace-android-sdk")
PORT = REPO / "claude-output/2026-08-26-kotlin-port"
TRACES = PORT / "fixtures/traces"
OUT = PORT / "goldens/trace"
CLI = OUT / "_cli"
CAMPAIGN = OUT / "_campaign"
SCRIPTS = REPO / ".claude/skills/startup-multi-device-analysis/scripts"
sys.path.insert(0, str(SCRIPTS))
import variance_analysis  # noqa: E402
import outlier_factors  # noqa: E402

DEVICES = ["mid-b", "mid-a", "flagship-a"]
REAL_RUN = subprocess.run  # the monkeypatch below replaces the shared module attribute; restore it after


def saved_csv(trace_path, query):
    device = pathlib.Path(trace_path).parents[1].name
    stem = pathlib.Path(trace_path).name.removesuffix(".perfetto-trace")
    return (OUT / device / stem / f"{query}.csv").read_text()


class FakeProc:
    def __init__(self, stdout):
        self.returncode = 0
        self.stdout = stdout
        self.stderr = ""


def patched_run(query):
    def run(cmd, capture_output=True, text=True, **kwargs):
        return FakeProc(saved_csv(cmd[-1], query))
    return run


def main():
    CLI.mkdir(parents=True, exist_ok=True)
    CAMPAIGN.mkdir(parents=True, exist_ok=True)
    log = open(OUT / "dump.log", "a")

    for device in DEVICES:
        traces_dir = TRACES / device / "pass1"
        cdir = CAMPAIGN / device
        cdir.mkdir(exist_ok=True)
        # variance_analysis: report + JSON (default little cpus 0-3, as the goldens for the Kotlin port
        # pin; per-device topology is a separate concern). `subprocess` is ONE shared module, so the
        # patch must be switched before each script, not set once per module.
        subprocess.run = patched_run("variance_metrics")
        sys.argv = ["variance_analysis.py", "fake-tp", str(traces_dir), "--json", str(cdir / "pass1.json"),
                    "--out", str(CLI / f"variance.{device}.stdout.txt")]
        buf = io.StringIO()
        try:
            with contextlib.redirect_stdout(buf):
                rc = variance_analysis.main()
            (CLI / f"variance.{device}.error.txt").unlink(missing_ok=True)
            log.write(f"CLI variance_analysis {device} rc={rc}\n")
        except Exception as exc:  # noqa: BLE001 - record the Python's own failure as the golden
            # The JSON is written before report() runs, so the dataset golden survives; the report
            # golden is replaced by the traceback so the port can assert the same input is
            # unreportable for the same reason (a section present in no trace).
            (CLI / f"variance.{device}.stdout.txt").unlink(missing_ok=True)
            (CLI / f"variance.{device}.error.txt").write_text(f"{type(exc).__name__}: {exc}\n")
            log.write(f"CLI variance_analysis {device} FAILED {type(exc).__name__}: {exc}\n")
        # outlier_factors: JSON only (its stdout is progress lines).
        subprocess.run = patched_run("outlier_metrics")
        sys.argv = ["outlier_factors.py", "fake-tp", str(traces_dir), str(cdir / "pass1-factors.json")]
        buf = io.StringIO()
        with contextlib.redirect_stdout(buf):
            rc = outlier_factors.main()
        (CLI / f"outlier_factors.{device}.stdout.txt").write_text(buf.getvalue())
        log.write(f"CLI outlier_factors {device} rc={rc}\n")

    subprocess.run = REAL_RUN
    env = dict(os.environ, LITTLE_CPUS="0,1,2,3")
    for device in DEVICES:
        cdir = CAMPAIGN / device
        for script in ("factors_report.py", "hypothesis_tests.py"):
            cp = subprocess.run([sys.executable, str(SCRIPTS / script), str(cdir)],
                                capture_output=True, text=True, env=env, timeout=600)
            (CLI / f"{script.removesuffix('.py')}.{device}.stdout.txt").write_text(cp.stdout)
            (CLI / f"{script.removesuffix('.py')}.{device}.stderr.txt").write_text(f"rc={cp.returncode}\n{cp.stderr}")
            log.write(f"CLI {script} {device} rc={cp.returncode}\n")
    args = [f"{d}={CAMPAIGN / d}" for d in DEVICES]
    cp = subprocess.run([sys.executable, str(SCRIPTS / "cross_device_sections.py"), *args],
                        capture_output=True, text=True, env=env, timeout=600)
    (CLI / "cross_device_sections.all.stdout.txt").write_text(cp.stdout)
    (CLI / "cross_device_sections.all.stderr.txt").write_text(f"rc={cp.returncode}\n{cp.stderr}")
    log.write(f"CLI cross_device_sections all rc={cp.returncode}\n")
    log.close()
    # The campaign dirs double as fixtures for the Kotlin tests; keep them beside the CLI output.
    shutil.copy(OUT / "dump.log", CLI / "dump.log.snapshot")


if __name__ == "__main__":
    main()
