#!/usr/bin/env python3
"""Declare and inspect the stable reference device set that longitudinal comparison depends on.

Probes every attached device for its PROFILE (not its identity), assigns each a stable
device_key you control, and freezes the measurement recipe. Re-probing later flags profile
DRIFT - an OS upgrade or replaced handset must become a new key, because silently comparing
across it is the classic way to invent or hide a regression.

Usage:
  python3 reference_set.py --probe --out reference-set.json
  python3 reference_set.py --probe --check reference-set.json      # drift check, no writes
  python3 reference_set.py --show reference-set.json
"""
import argparse
import json
import pathlib
import re
import subprocess
import sys
import time

RECIPE_DEFAULT = {
    # Many short passes, not a few long ones. Under clustering the variance of an arm is
    # sigma_between^2/G + sigma_within^2/(G*n); holding total launches fixed makes the second term
    # a constant, so only the pass count G buys precision - and on startup benchmarks the
    # between-pass term dominates heavily. Ten passes roughly halves the CI half-width versus four
    # at the same launch count, and drops the permutation floor from 0.029 to ~1e-5, which is what
    # lets a comparison clear alpha at all. More passes also means more independent installs, the
    # only thing that averages down bimodal per-install effects like compile-state parity.
    # See _shared/STATISTICS.md, "Spend the budget on PASSES, not iterations".
    "run_shape": {"passes": 10, "iterations": 20},
    "build_type": "benchmark",
    "compile_state": "profile",
    # Deliberately null: the probe cannot know which window instrument a harness actually records,
    # and a plausible-looking default here is worse than a missing value. On 2026-08-16 this field
    # shipped as "app-embrace-start" - a span the harness never emits - and every ingest of a
    # 200-trace leg was refused with "no window found" until it was corrected by hand. ingest_run
    # refuses a null instrument with a pointed message, so leaving this unset fails LOUDLY at the
    # first ingest instead of silently poisoning the series definition. Set it to what your traces
    # really contain: "emb-sdk-start" (SDK >= 9.2.0) or "composed" (the fallback window).
    "instrument": None,
    "_comment": "EVERY value above is provisional output of --probe, not just the device keys: "
                "review the recipe field by field before the first ingest. Changing any of these "
                "later starts a NEW comparable series; see references/store.md",
}

PROFILE_FIELDS = ["api_level", "release", "tier", "vendor", "soc_family", "clusters",
                  "ram_class", "storage_class"]


def adb(serial, *args, timeout=60):
    cmd = ["adb"] + (["-s", serial] if serial else []) + list(args)
    return subprocess.run(cmd, capture_output=True, text=True, timeout=timeout)


def prop(serial, name):
    return adb(serial, "shell", "getprop", name).stdout.strip()


def attached_serials():
    out = adb(None, "devices").stdout
    serials = []
    for line in out.splitlines()[1:]:
        parts = line.split()
        if len(parts) >= 2 and parts[1] == "device":
            serials.append(parts[0])
    return serials


def ram_class(serial):
    out = adb(serial, "shell", "cat", "/proc/meminfo").stdout
    match = re.search(r"MemTotal:\s+(\d+)", out)
    if not match:
        return "unknown"
    gb = int(match.group(1)) / (1024.0 * 1024.0)
    if gb <= 2.2:
        return "<=2GB"
    if gb <= 4.5:
        return "3-4GB"
    if gb <= 7.0:
        return "6GB"
    return ">=8GB"


def tier_guess(ram, clusters):
    """A coarse hint only - the operator should confirm it. Tier is about user-visible class,
    which no single property captures."""
    distinct = len(set(clusters)) if clusters else 1
    if ram in ("<=2GB",):
        return "entry"
    if ram in ("3-4GB",) and distinct <= 2:
        return "entry-mid"
    if distinct >= 3:
        return "flagship"
    return "mid"


def clusters(serial):
    """Distinct max frequencies per cpufreq policy, ascending - the cluster topology."""
    out = adb(serial, "shell",
              "for p in /sys/devices/system/cpu/cpufreq/policy*; do cat $p/cpuinfo_max_freq; done"
              ).stdout
    freqs = sorted({int(v) for v in re.findall(r"\d{5,}", out)})
    return freqs


def soc_family(serial):
    for name in ("ro.soc.model", "ro.board.platform", "ro.hardware"):
        value = prop(serial, name)
        if value and value.lower() not in ("unknown", ""):
            return value
    return "unknown"


def probe(serial):
    return {
        "api_level": int(prop(serial, "ro.build.version.sdk") or 0),
        "release": prop(serial, "ro.build.version.release"),
        "vendor": prop(serial, "ro.product.manufacturer"),
        "soc_family": soc_family(serial),
        "clusters": clusters(serial),
        "ram_class": ram_class(serial),
        "storage_class": "unknown",  # not reliably readable unrooted; fill in by hand if known
    }


def coverage_warnings(devices):
    """Say plainly what the set cannot answer - a set chosen by convenience usually cannot
    separate the thing the operator most wants separated."""
    warnings = []
    apis = {d["profile"].get("api_level") for d in devices.values()}
    vendors = {(d["profile"].get("vendor") or "").lower() for d in devices.values()}
    tiers = {d.get("tier") or d["profile"].get("tier") for d in devices.values()}
    if len(devices) < 2:
        warnings.append("single device: cannot separate SDK effects from device-class effects")
    if len(apis) < 2:
        warnings.append("one ART/Android generation: compile-state and class-load findings may "
                        "not transfer to other releases")
    if len(vendors) < 2:
        warnings.append("one vendor: cannot separate OEM policy (install-time compilation, "
                        "thermal governors, procfs readability) from silicon")
    if not any(t in ("entry", "entry-mid") for t in tiers):
        warnings.append("no entry/low-RAM device: the outlier classes that hurt users most "
                        "(memory pressure, GC competition) will be under-represented")
    if any((d["profile"].get("api_level") or 0) < 29 for d in devices.values()):
        warnings.append("a device below API 29 cannot be traced as a profileable non-debuggable "
                        "target; its numbers are not comparable")
    return warnings


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--probe", action="store_true")
    ap.add_argument("--out")
    ap.add_argument("--check")
    ap.add_argument("--show")
    args = ap.parse_args()

    if args.show:
        doc = json.loads(pathlib.Path(args.show).read_text())
        print(json.dumps(doc, indent=1))
        for w in coverage_warnings(doc.get("devices", {})):
            print(f"COVERAGE GAP: {w}")
        return

    if not args.probe:
        sys.exit("use --probe (with --out or --check), or --show <file>")

    serials = attached_serials()
    if not serials:
        sys.exit("no attached devices (adb devices shows none in state 'device')")
    probed = {s: probe(s) for s in serials}

    if args.check:
        doc = json.loads(pathlib.Path(args.check).read_text())
        known = doc.get("devices", {})
        by_serial = {cfg.get("serial"): (key, cfg) for key, cfg in known.items()}
        for serial, profile in probed.items():
            if serial not in by_serial:
                print(f"NEW device attached (not in the reference set): {serial} -> {profile}")
                continue
            key, cfg = by_serial[serial]
            drift = {f: (cfg["profile"].get(f), profile.get(f))
                     for f in PROFILE_FIELDS
                     if f in cfg.get("profile", {}) and cfg["profile"].get(f) != profile.get(f)}
            if drift:
                print(f"PROFILE DRIFT on '{key}' ({serial}):")
                for field, (was, now) in drift.items():
                    print(f"    {field}: {was!r} -> {now!r}")
                print("    -> treat this as a NEW device_key and re-establish its baseline; do "
                      "NOT carry the old baseline across (see references/store.md).")
            else:
                print(f"ok '{key}' ({serial}): profile unchanged")
        for key, cfg in known.items():
            if cfg.get("serial") not in probed and not cfg.get("retired"):
                print(f"MISSING '{key}' ({cfg.get('serial')}): attach it or mark it retired - a "
                      f"silent gap in the series is where slow regressions hide")
        return

    devices = {}
    for i, (serial, profile) in enumerate(sorted(probed.items()), 1):
        tier = tier_guess(profile["ram_class"], profile["clusters"])
        key = f"{tier}-{chr(ord('a') + i - 1)}"
        devices[key] = {"serial": serial, "tier": tier, "profile": profile,
                        "cool_gate_c": 32.0, "retired": False}
        print(f"{key}: {serial} api={profile['api_level']} vendor={profile['vendor']} "
              f"soc={profile['soc_family']} ram={profile['ram_class']} tier~{tier}")
    doc = {
        "declared_at": time.strftime("%Y-%m-%dT%H:%M:%S"),
        "recipe": RECIPE_DEFAULT,
        "devices": devices,
        "_comment": ["device_key is YOUR stable label - rename these to something meaningful and "
                     "keep them forever; they are the axis every longitudinal report uses.",
                     "tier is a guess from RAM and cluster topology; confirm it by hand.",
                     "cool_gate_c is per device: a plugged-in device's warm idle floor can sit "
                     "above a naive gate, so set it from an observed idle temperature."],
    }
    for w in coverage_warnings(devices):
        print(f"COVERAGE GAP: {w}")
    if args.out:
        pathlib.Path(args.out).write_text(json.dumps(doc, indent=1))
        print(f"\nwrote {args.out} - review the keys and tiers before your first ingest")
    else:
        print(json.dumps(doc, indent=1))


if __name__ == "__main__":
    main()
