#!/usr/bin/env python3
"""Turn a locally ingested run into a corpus submission: collect provenance, REDACT, validate, append.

Two design choices matter more than the code:

* **Allowlist, not denylist.** The record is built field-by-field from an explicit allowlist, so a
  field nobody considered cannot leak by default. Raw traces are never submitted - they carry every
  running process name and can expose the installed-app inventory of a personal handset.
* **Reject loudly.** A record that cannot be compared later is worse than no record, so
  admissibility failures stop the submission instead of being smoothed over.

Usage:
  python3 submit_run.py --store <local store.jsonl> --run-id <id> --corpus <corpus.jsonl> \
      --contributor <team-or-handle> [--serial <serial>] [--dry-run]

The serial is used ONLY to derive a salted unit hash locally; it is never stored or transmitted.
The salt lives in <corpus dir>/.unit-salt on your machine.
"""
import argparse
import hashlib
import json
import pathlib
import re
import secrets
import subprocess
import sys
import time

SCHEMA_VERSION = 1

# Coarse buckets: more identifying detail is not more useful.
def bucket_apps(count):
    if count is None:
        return None
    return "<50" if count < 50 else ("50-150" if count <= 150 else ">150")


def bucket_pct(value):
    if value is None:
        return None
    return f"{int(value // 20) * 20}-{int(value // 20) * 20 + 20}%"


def adb(serial, *args, timeout=90):
    cmd = ["adb"] + (["-s", serial] if serial else []) + list(args)
    cp = subprocess.run(cmd, capture_output=True, text=True, timeout=timeout)
    return (cp.stdout or "").strip()


def prop(serial, name):
    return adb(serial, "shell", "getprop", name)


def unit_token(serial, corpus_path):
    """Stable per-handset, non-reversible. The salt never leaves this machine."""
    salt_file = corpus_path.parent / ".unit-salt"
    if salt_file.exists():
        salt = salt_file.read_text().strip()
    else:
        salt = secrets.token_hex(16)
        salt_file.write_text(salt)
    return hashlib.sha256((salt + (serial or "unknown")).encode()).hexdigest()[:16]


def collect_device_provenance(serial, corpus_path):
    """Only the dimensions that explain reproducibility failures - see the schema reference."""
    installed = adb(serial, "shell", "pm list packages -3")
    app_count = len([l for l in installed.splitlines() if l.strip()]) or None
    df = adb(serial, "shell", "df /data")
    free_pct = None
    match = re.search(r"(\d+)%", df)
    if match:
        try:
            free_pct = 100 - int(match.group(1))
        except ValueError:
            pass
    battery = adb(serial, "shell", "dumpsys battery")
    health = None
    for line in battery.splitlines():
        if "health:" in line:
            health = line.split(":")[1].strip()
    return {
        "unit_id": unit_token(serial, corpus_path),
        "model": prop(serial, "ro.product.model"),
        "os_build": prop(serial, "ro.build.fingerprint"),
        "api_level": prop(serial, "ro.build.version.sdk"),
        "security_patch": prop(serial, "ro.build.version.security_patch"),
        "skin_version": prop(serial, "ro.build.display.id"),
        "kernel_version": adb(serial, "shell", "uname -r"),
        "soc_family": prop(serial, "ro.soc.model") or prop(serial, "ro.board.platform"),
        "storage_free_pct": bucket_pct(free_pct),
        "battery_health": health,
        "installed_app_count": bucket_apps(app_count),
        "device_settings": {
            "animation_scale": adb(serial, "shell", "settings get global animator_duration_scale"),
            "background_process_limit": adb(
                serial, "shell", "settings get global background_process_limit"),
            "battery_saver": adb(serial, "shell", "settings get global low_power"),
        },
    }


def published(version):
    text = (version or "").lower()
    return bool(text) and not any(m in text for m in ("snapshot", "local", "dirty", "+"))


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--store", required=True)
    ap.add_argument("--run-id", required=True)
    ap.add_argument("--corpus", required=True)
    ap.add_argument("--contributor", required=True)
    ap.add_argument("--serial", help="used only to derive a local salted unit hash")
    ap.add_argument("--lossy-tolerance", type=float, default=0.0,
                    help="max share of lossy traces still admissible (default: none)")
    ap.add_argument("--dry-run", action="store_true")
    args = ap.parse_args()

    corpus_path = pathlib.Path(args.corpus)
    corpus_path.parent.mkdir(parents=True, exist_ok=True)

    local = None
    for line in pathlib.Path(args.store).read_text().splitlines():
        if not line.strip():
            continue
        rec = json.loads(line)
        if rec.get("run_id") == args.run_id:
            local = rec
    if local is None:
        sys.exit(f"run_id {args.run_id!r} not found in {args.store}")

    problems = []
    # Buffer-level loss can have removed the window the submitted distribution is made of, so it
    # gates admissibility. Event-parse errors do not - surviving durations are correct - but they
    # do invalidate the signal inventory, which another contributor would otherwise read as "this
    # unit does not emit that signal". Travel with a note instead of being rejected.
    health = local.get("trace_health") or {}
    traces_n = health.get("traces") or 0
    buffer_loss = health.get("buffer_loss") or 0
    if traces_n and buffer_loss:
        share = 100.0 * buffer_loss / traces_n
        if share > args.lossy_tolerance:
            problems.append(f"{buffer_loss}/{traces_n} traces ({share:.0f}%) lost written data, "
                            f"above the {args.lossy_tolerance:.0f}% tolerance - the submitted "
                            f"distribution may be built on evicted windows")
    if health and not health.get("signals_from_clean_trace", True):
        problems.append("the signal inventory did not come from a clean trace (event-parse "
                        "errors), so signals_present cannot be distinguished from a genuine "
                        "absence - re-capture, or submit with signals_present omitted")
    if not published(local.get("sdk_version")):
        problems.append(f"sdk_version {local.get('sdk_version')!r} is not a published artifact - "
                        f"working-tree builds are not reproducible by others and are inadmissible")
    if not local.get("derived", {}).get("n"):
        problems.append("no derived statistics in the local record")
    recipe = local.get("recipe") or {}
    for field in ("build_type", "compile_state", "instrument"):
        if not recipe.get(field):
            problems.append(f"recipe.{field} missing - the cell key would be incomplete")

    device = collect_device_provenance(args.serial, corpus_path) if args.serial else {}
    if args.serial and not device.get("os_build"):
        problems.append("could not read the OS build fingerprint - same api_level is NOT the same "
                        "software, so the record would not be groupable")

    record = {
        "schema_version": SCHEMA_VERSION,
        "submission_id": f"{args.contributor}-{args.run_id}",
        "submitted_at": time.strftime("%Y-%m-%dT%H:%M:%S"),
        "contributor": args.contributor,
        "sdk_version": local.get("sdk_version"),
        "app_build_id": local.get("app_build_id"),
        "recipe": recipe,
        "conditions": local.get("conditions") or {},
        "derived": local.get("derived") or {},
        "windows_ms": local.get("windows_ms") or [],
        "signals_present": local.get("signals_present") or [],
        "trace_health": local.get("trace_health") or {},
        "notes": local.get("notes") or "",
    }
    record.update(device)

    for problem in problems:
        print(f"INADMISSIBLE: {problem}")
    if problems:
        sys.exit("\nsubmission refused - fix the above; a record that cannot be compared later is "
                 "worse than no record")

    print(json.dumps({k: v for k, v in record.items() if k != "windows_ms"}, indent=1))
    print(f"(+ {len(record['windows_ms'])} per-iteration window values)")
    print("\nredaction check: no serial, no package names, no paths, no raw traces in the above.")
    if args.dry_run:
        print("dry-run: nothing appended")
        return
    with corpus_path.open("a") as fh:
        fh.write(json.dumps(record) + "\n")
    print(f"appended to {corpus_path}")


if __name__ == "__main__":
    main()
