#!/usr/bin/env python3
"""Run ONE matrix cell: set the factor state, machine-check every invariant, then delegate the
passes to the multi-device skill's fleet_campaign.py and record provenance.

A failed invariant is a STOP, not a warning: every one of them has already caused a wasted or
wrong campaign in this project. Nothing here is a substitute for reading references/factors.md.

Usage:
  python3 cell_runner.py --cells cells.json --cell "a14|local|reference" --out <run-dir>
  python3 cell_runner.py --cells cells.json --cell ... --check-only    # invariants, no passes
"""
import argparse
import hashlib
import json
import os
import pathlib
import subprocess
import sys
import time

REPO = pathlib.Path("/Users/hansonho/work/embrace-android-sdk")
EXAMPLE = REPO / "examples/ExampleApp"
CATALOG = EXAMPLE / "gradle/libs.versions.toml"
PKG = "io.embrace.android.exampleapp"
LOCK = pathlib.Path("/tmp/startup-matrix-cell.pid")
MULTI_DEVICE = REPO / ".claude/skills/startup-multi-device-analysis/scripts"
WRAPPER_SLICE = "app-embrace-start"


def log(msg, logfile=None):
    line = f"{time.strftime('%H:%M:%S')} [cell] {msg}"
    print(line, flush=True)
    if logfile:
        with open(logfile, "a") as fh:
            fh.write(line + "\n")


def run(cmd, timeout=600, cwd=None):
    return subprocess.run(cmd, capture_output=True, text=True, timeout=timeout, cwd=cwd)


def adb(serial, *args, timeout=180):
    return run(["adb", "-s", serial, *args], timeout=timeout)


def shell(serial, cmd, timeout=180):
    return adb(serial, "shell", cmd, timeout=timeout)


# --------------------------------------------------------------------------- locking

def acquire_lock():
    """Refuse to start when another cell runner is alive. Two concurrent drivers silently
    destroy a run (force-stops kill the app mid-init; perfetto sessions compete)."""
    if LOCK.exists():
        try:
            pid = int(LOCK.read_text().strip())
            os.kill(pid, 0)
            sys.exit(f"ABORT: another cell runner is alive (pid {pid}); {LOCK}")
        except (ValueError, ProcessLookupError, PermissionError):
            print(f"stale lock {LOCK} ignored")
    LOCK.write_text(str(os.getpid()))


# --------------------------------------------------------------------------- invariants

def check_host_quiet():
    """No competing driver or gradle daemon build in flight."""
    ps = run(["pgrep", "-fl", "python3"], timeout=60).stdout
    others = [ln for ln in ps.splitlines()
              if ("fleet_campaign" in ln or "device_driver" in ln or "p6b" in ln)
              and str(os.getpid()) not in ln]
    if others:
        return False, f"other drivers alive: {others[:2]}"
    return True, "host quiet"


def check_temperature(serial, gate_c, band=None):
    """thermalservice first; dumpsys battery is NOT trustworthy on all devices (a fleet Pixel 3
    reports a constant 37.7 C, which stalls any band logic silently)."""
    out = shell(serial, "dumpsys thermalservice", timeout=60).stdout
    temps = []
    for line in out.splitlines():
        line = line.strip()
        if line.startswith("Temperature{"):
            for part in line.split(","):
                if part.strip().startswith("mValue="):
                    try:
                        temps.append(float(part.split("=")[1]))
                    except ValueError:
                        pass
    plausible = [t for t in temps if 10.0 < t < 100.0]
    if not plausible:
        return False, "no plausible thermalservice sensor values (do not fall back to battery)"
    hottest = max(plausible)
    if band == "hot":
        return True, f"hot cell: hottest sensor {hottest:.1f} C (band enforced by the heater)"
    if hottest > gate_c:
        return False, f"hottest sensor {hottest:.1f} C > gate {gate_c} C"
    return True, f"hottest sensor {hottest:.1f} C <= gate {gate_c} C"


def resolved_sdk_version():
    """Read the SDK coordinate the app actually resolves - never trust the catalog text."""
    cp = run([str(EXAMPLE / "gradlew"), "-p", str(EXAMPLE), ":app:dependencies",
              "--configuration", "benchmarkRuntimeClasspath", "-q"], timeout=900)
    for line in cp.stdout.splitlines():
        if "io.embrace:embrace-android-sdk" in line:
            return line.strip().lstrip("+\\-| ").strip()
    return None


def check_sdk_matches(expected_version, logfile):
    resolved = resolved_sdk_version()
    if not resolved:
        return False, "could not read a resolved embrace-android-sdk coordinate"
    log(f"resolved SDK: {resolved}", logfile)
    if expected_version == "local":
        gp = (REPO / "gradle.properties").read_text()
        local = next((l.split("=", 1)[1].strip() for l in gp.splitlines()
                      if l.startswith("version=")), None)
        ok = local is not None and local in resolved
        return ok, f"expected local {local}, resolved {resolved}"
    return (expected_version in resolved), f"expected {expected_version}, resolved {resolved}"


def check_compile_state(serial, want):
    out = shell(serial, "dumpsys package dexopt", timeout=120).stdout
    block, capture = [], False
    for line in out.splitlines():
        if PKG in line and line.strip().startswith("["):
            capture = True
            continue
        if capture:
            if line.strip().startswith("[") and PKG not in line:
                break
            block.append(line.strip())
    status_line = next((b for b in block if "status=" in b), "")
    if not status_line:
        return False, "no dexopt status for the package (is it installed?)"
    if want == "profile":
        ok = "speed-profile" in status_line
    elif want == "none":
        ok = "verify" in status_line or "run-from-apk" in status_line
    elif want == "full":
        ok = "speed" in status_line and "speed-profile" not in status_line
    else:
        ok = True
    return ok, status_line


def apk_sha256(build_type):
    path = EXAMPLE / f"app/build/outputs/apk/{build_type}/app-{build_type}.apk"
    if not path.exists():
        return None
    h = hashlib.sha256()
    with path.open("rb") as fh:
        for chunk in iter(lambda: fh.read(1 << 20), b""):
            h.update(chunk)
    return h.hexdigest()


def check_instrument(trace_dir):
    """The wrapper slice must exist in the first pass, else the SDK/patch is wrong - fail fast
    rather than leave a hole in the matrix."""
    traces = sorted(pathlib.Path(trace_dir).rglob("*.perfetto-trace"))
    if not traces:
        return False, "no traces produced"
    tp = pathlib.Path(__file__).parent / "trace_processor"
    if not tp.exists():
        return True, f"{len(traces)} traces (trace_processor absent; instrument unverified)"
    q = f"SELECT COUNT(*) AS n FROM slice WHERE name = '{WRAPPER_SLICE}';"
    qf = pathlib.Path("/tmp/vfm_instrument.sql")
    qf.write_text(q)
    cp = run([str(tp), "-q", str(qf), str(traces[0])], timeout=600)
    n = 0
    for line in cp.stdout.splitlines():
        line = line.strip().strip('"')
        if line.isdigit():
            n = int(line)
    return (n > 0), f"{WRAPPER_SLICE} slices in pass-1 trace: {n}"


# --------------------------------------------------------------------------- factor state

def apply_factor_state(serial, levels, logfile):
    """Device-side state only; compile state comes from the benchmark CompilationMode and
    install state from the install sequence, both handled by the caller/campaign."""
    saved = {
        "stay_on": shell(serial, "settings get global stay_on_while_plugged_in").stdout.strip(),
        "airplane": shell(serial, "settings get global airplane_mode_on").stdout.strip(),
    }
    shell(serial, "settings put global stay_on_while_plugged_in 7")
    shell(serial, "svc wifi disable")
    shell(serial, "input keyevent 224")
    shell(serial, "wm dismiss-keyguard")
    if levels.get("contention", "quiet") != "quiet":
        n = 8 if levels["contention"] == "hog8" else 4
        procs = [subprocess.Popen(["adb", "-s", serial, "shell", "dd if=/dev/zero of=/dev/null"],
                                  stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
                 for _ in range(n)]
        log(f"contention: {n} hogs started ({levels['contention']})", logfile)
        return saved, procs
    return saved, []


def restore_state(serial, saved, procs, logfile):
    for p in procs:
        p.kill()
    shell(serial, "pkill -9 dd")
    shell(serial, "svc wifi enable")
    if saved.get("stay_on", "").isdigit():
        shell(serial, f"settings put global stay_on_while_plugged_in {saved['stay_on']}")
    log("device state restored", logfile)


# --------------------------------------------------------------------------- main

def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--cells", required=True)
    ap.add_argument("--cell", required=True)
    ap.add_argument("--out", default=None)
    ap.add_argument("--check-only", action="store_true")
    args = ap.parse_args()

    doc = json.loads(pathlib.Path(args.cells).read_text())
    plan = doc["plan"]
    cell = next((c for c in doc["cells"] if c["id"] == args.cell), None)
    if cell is None:
        sys.exit(f"no such cell: {args.cell}")

    out = pathlib.Path(args.out or (REPO / "claude-output" / f"vfm-{plan['run_id']}"))
    cell_dir = out / cell["id"].replace("|", "__").replace(",", "_").replace("=", "-")
    cell_dir.mkdir(parents=True, exist_ok=True)
    logfile = cell_dir / "cell.log"
    build_type = plan.get("build_type", "benchmark")

    # resolve the serial for THIS cell's device: combo cells deliberately run elsewhere, and
    # defaulting to the primary serial would silently measure the wrong device
    devices = plan.get("devices") or {plan["device"]["name"]: plan["device"]}
    dev_cfg = devices.get(cell["device"])
    if not dev_cfg or not dev_cfg.get("serial"):
        sys.exit(f"ABORT: no serial configured for device '{cell['device']}' "
                 f"(known: {sorted(devices)}) - fix the plan's devices map")
    serial = dev_cfg["serial"]
    attached = run(["adb", "devices"], timeout=60).stdout
    if serial not in attached:
        sys.exit(f"ABORT: device {cell['device']} ({serial}) is not attached")
    cool_gate = dev_cfg.get("cool_gate_c", 32.0)

    acquire_lock()
    try:
        log(f"cell {cell['id']} levels={cell['levels']}", logfile)

        checks = [
            ("host quiet", check_host_quiet()),
            ("sdk matches", check_sdk_matches(cell["version"], logfile)),
            ("temperature", check_temperature(serial, cool_gate, cell["levels"].get("thermal"))),
        ]
        if run(["adb", "-s", serial, "shell", f"pm list packages {PKG}"], timeout=60).stdout.strip():
            checks.append(("compile state", check_compile_state(serial, cell["levels"].get("compile"))))

        failed = [name for name, (ok, _) in checks if not ok]
        for name, (ok, detail) in checks:
            log(f"  [{'OK ' if ok else 'FAIL'}] {name}: {detail}", logfile)
        if failed:
            sys.exit(f"ABORT: invariants failed: {failed} - fix, do not proceed")

        sha = apk_sha256(build_type)
        provenance = {
            "cell": cell, "plan_run_id": plan["run_id"], "serial": serial,
            "build_type": build_type, "apk_sha256": sha,
            "repo_head": run(["git", "-C", str(REPO), "rev-parse", "HEAD"]).stdout.strip(),
            "repo_dirty": bool(run(["git", "-C", str(REPO), "status", "--short"]).stdout.strip()),
            "catalog_pin": next((l.strip() for l in CATALOG.read_text().splitlines()
                                 if l.strip().startswith("embrace =")), None),
            "checks": {name: detail for name, (_, detail) in checks},
            "started": time.strftime("%Y-%m-%dT%H:%M:%S"),
        }
        (cell_dir / "cell-state.json").write_text(json.dumps(provenance, indent=1))
        if args.check_only:
            log("check-only: invariants passed; not running passes", logfile)
            return

        saved, procs = apply_factor_state(serial, cell["levels"], logfile)
        try:
            method = ("coldStartupBaselineProfile"
                      if cell["levels"].get("compile") == "profile" else "coldStartup")
            cmd = ["python3", str(MULTI_DEVICE / "fleet_campaign.py"),
                   "--serial", serial, "--out", str(cell_dir),
                   "--passes", str(plan["passes"]), "--method", method,
                   "--iterations", str(plan["iterations"])]
            log(f"delegating passes: {' '.join(cmd)}", logfile)
            cp = subprocess.run(cmd, timeout=6 * 3600)
            log(f"campaign rc={cp.returncode}", logfile)
        finally:
            restore_state(serial, saved, procs, logfile)

        ok, detail = check_instrument(cell_dir)
        log(f"  [{'OK ' if ok else 'FAIL'}] instrument: {detail}", logfile)
        provenance["instrument_check"] = detail
        provenance["finished"] = time.strftime("%Y-%m-%dT%H:%M:%S")
        (cell_dir / "cell-state.json").write_text(json.dumps(provenance, indent=1))
        if not ok:
            sys.exit("ABORT: expected window instrument missing - quarantine this cell")
        log(f"cell complete: {cell_dir}", logfile)
    finally:
        LOCK.unlink(missing_ok=True)


if __name__ == "__main__":
    main()
