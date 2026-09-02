#!/usr/bin/env python3
"""Probe one connected device and emit its DEVICE PROFILE.

Run this FIRST for every device in a multi-device campaign, and KEEP the resulting JSON
next to the campaign output — comparability across sessions, SDK versions, and engineers
depends on knowing exactly which device state produced the numbers. A marketing name is
not a profile.

The profile fields (shared vocabulary across the startup skills):
  api_level / android_release   ART generation; API 29 is the practical tracing floor
  vendor                        OEM: install-time compile policy, thermal governors,
                                SELinux readability of sysfs/procfs nodes
  soc_family / soc_model        silicon identity, independent of the OEM build
  clusters / little_cpus        cluster topology; homogeneous vs big.LITTLE
  ram_mb / ram_class            gates the memory-pressure and own-GC outlier classes
  storage_class                 eMMC-class vs UFS-class; gates the IO-stall class
  tier                          entry / mid / flagship (heuristic from RAM + clocks)
  thermal_sensors               which thermalservice zones exist (varies hugely by vendor)

little_cpus feeds --little-cpus on variance_analysis.py and LITTLE_CPUS on
hypothesis_tests.py / factors_report.py, since outlier_metrics.sql's run_cl0_ms/run_cl1_ms
split is a fixed cpu<4 partition that does not know which cluster is actually the little one.

Reads (via `adb -s <serial> shell ...`, each read independent so one failure doesn't
abort the probe):
  - getprop: manufacturer, model, Android release, API level, SoC manufacturer/model,
    board platform
  - /proc/cpuinfo: "CPU part" per processor index
  - /proc/meminfo: MemTotal
  - /sys/devices/system/cpu/cpufreq/policy*/related_cpus + .../cpuinfo_max_freq:
    the cluster map (cpus per policy, each policy's max frequency)
  - ls /sys/block: storage class hint (sd* = UFS/scsi-class, mmcblk* = eMMC-class)
  - dumpsys thermalservice: sensor names under "Current temperatures from HAL:"

Derives clusters (cpus + max_freq_khz + cpu_part) and little_cpus (the cpus of the
cluster(s) whose max frequency is the lowest; if every cluster shares one max frequency
the device is treated as homogeneous and little_cpus falls back to the first policy's
cpus). Writes <output-dir>/<name>-topology.json and prints a one-paragraph summary.

`tier` is a coarse heuristic (RAM + top cluster clock + cluster count) meant to be
overridden by judgement — it exists so a device set can be scored for tier coverage, not
to be authoritative.

Usage: python3 device_probe.py <serial> <name> <output-dir>
"""

import json
import os
import re
import subprocess
import sys

TRACING_API_FLOOR = 29


def main() -> int:
    if len(sys.argv) != 4:
        print("usage: python3 device_probe.py <serial> <name> <output-dir>", file=sys.stderr)
        return 1
    serial, name, out_dir = sys.argv[1], sys.argv[2], sys.argv[3]

    props_out = run_shell(
        serial,
        "getprop ro.product.manufacturer; getprop ro.product.model; "
        "getprop ro.build.version.release; getprop ro.build.version.sdk; "
        "getprop ro.soc.manufacturer; getprop ro.soc.model; getprop ro.board.platform",
    )
    props = parse_props(props_out, 7)
    vendor, model, android, api, soc_vendor, soc_model, board = props
    soc_family = soc_vendor or board or None

    cpuinfo_out = run_shell(serial, "cat /proc/cpuinfo")
    cpu_parts = parse_cpuinfo(cpuinfo_out)

    cpufreq_out = run_shell(
        serial,
        "cat /sys/devices/system/cpu/cpufreq/policy*/related_cpus "
        "/sys/devices/system/cpu/cpufreq/policy*/cpuinfo_max_freq",
    )
    related_lines, max_freq_lines = split_cpufreq_output(cpufreq_out)
    clusters = derive_clusters(related_lines, max_freq_lines, cpu_parts)
    little_cpus, homogeneous = compute_little_cpus(clusters)

    ram_mb = parse_mem_total_mb(run_shell(serial, "cat /proc/meminfo"))
    storage_class = classify_storage(run_shell(serial, "ls /sys/block"))
    tier = classify_tier(ram_mb, clusters)

    thermal_out = run_shell(serial, "dumpsys thermalservice")
    thermal_sensors = parse_thermal_sensors(thermal_out)

    api_int = int(api) if (api or "").isdigit() else None
    topology = {
        "serial": serial,
        "name": name,
        "vendor": vendor,
        "model": model,
        "android_release": android,
        "api_level": api_int,
        "soc_family": soc_family,
        "soc_model": soc_model,
        "clusters": clusters,
        "little_cpus": little_cpus,
        "homogeneous": homogeneous,
        "ram_mb": ram_mb,
        "ram_class": classify_ram(ram_mb),
        "storage_class": storage_class,
        "tier": tier,
        "thermal_sensors": thermal_sensors,
    }
    os.makedirs(out_dir, exist_ok=True)
    out_path = os.path.join(out_dir, f"{name}-topology.json")
    with open(out_path, "w") as f:
        json.dump(topology, f, indent=2)

    little_arg = ",".join(str(c) for c in sorted(little_cpus)) if little_cpus else "0,1,2,3"
    homog_note = " (homogeneous — no DVFS cluster split detected, using policy0's cpus)" \
        if homogeneous else ""
    sensors_note = ", ".join(thermal_sensors) if thermal_sensors else "none readable"
    ram_note = f"{ram_mb} MB ({topology['ram_class']})" if ram_mb else "RAM unknown"
    print(
        f"{name}: {vendor or '?'} {model or '?'}, Android {android or '?'} "
        f"(API {api or '?'}); soc {soc_family or '?'} {soc_model or ''}; "
        f"tier {tier}; {ram_note}; storage {storage_class}; "
        f"{len(clusters)} cpufreq policy(ies){homog_note}; "
        f"thermal sensors: {sensors_note}. Wrote {out_path} — pass "
        f"--little-cpus {little_arg} to variance_analysis.py, or set "
        f"LITTLE_CPUS={little_arg} for hypothesis_tests.py / factors_report.py, "
        f"when analyzing this device's traces."
    )
    if api_int is not None and api_int < TRACING_API_FLOOR:
        print(
            f"WARNING: API {api_int} is below the API {TRACING_API_FLOOR} floor for "
            "profileable shell tracing — only debuggable targets run here and their "
            "numbers are not comparable. Exclude this device or treat it as debug-only.",
            file=sys.stderr,
        )
    print(
        "Record this profile with the campaign output; report tier / vendor / api_level "
        "coverage for the whole device set before drawing cross-device conclusions."
    )
    return 0


def run_shell(serial: str, command: str):
    """Run one `adb shell <command>`; return stdout, or None on any failure."""
    try:
        proc = subprocess.run(
            ["adb", "-s", serial, "shell", command],
            capture_output=True, text=True, timeout=15,
        )
    except Exception:
        return None
    if proc.returncode != 0:
        return None
    return proc.stdout


def parse_props(text, count):
    lines = (text or "").splitlines()
    vals = [ln.strip() or None for ln in lines[:count]]
    while len(vals) < count:
        vals.append(None)
    return tuple(vals)


def parse_cpuinfo(text):
    """Return {processor_index: cpu_part_hex_string} from /proc/cpuinfo."""
    parts = {}
    cur = None
    for line in (text or "").splitlines():
        if ":" not in line:
            continue
        key, _, val = line.partition(":")
        key, val = key.strip(), val.strip()
        if key == "processor":
            try:
                cur = int(val)
            except ValueError:
                cur = None
        elif key == "CPU part" and cur is not None:
            parts[cur] = val
    return parts


def split_cpufreq_output(text):
    """Split the concatenated related_cpus + cpuinfo_max_freq cat output in half:
    the shell glob lists all related_cpus lines (one per policy) before all
    cpuinfo_max_freq lines, both in the same policy order."""
    if not text:
        return [], []
    lines = [ln for ln in text.splitlines() if ln.strip()]
    half = len(lines) // 2
    return lines[:half], lines[half:]


def derive_clusters(related_lines, max_freq_lines, cpu_parts):
    clusters = []
    for rel, freq in zip(related_lines, max_freq_lines):
        try:
            cpus = [int(x) for x in rel.split()]
        except ValueError:
            cpus = []
        try:
            max_freq_khz = int(freq.strip())
        except ValueError:
            max_freq_khz = None
        cpu_part = cpu_parts.get(cpus[0]) if cpus else None
        clusters.append({"cpus": cpus, "max_freq_khz": max_freq_khz, "cpu_part": cpu_part})
    return clusters


def compute_little_cpus(clusters):
    """little_cpus = cpus of the cluster(s) with the lowest max_freq_khz; if every
    cluster shares one max frequency, treat the device as homogeneous and fall back
    to the first policy's cpus."""
    freqs = {c["max_freq_khz"] for c in clusters if c["max_freq_khz"] is not None}
    if not clusters:
        return [], False
    if len(freqs) <= 1:
        return clusters[0]["cpus"], True
    min_freq = min(freqs)
    little = []
    for c in clusters:
        if c["max_freq_khz"] == min_freq:
            little.extend(c["cpus"])
    return little, False


def parse_mem_total_mb(text):
    """MemTotal in MB from /proc/meminfo, or None if unreadable."""
    for line in (text or "").splitlines():
        m = re.match(r"MemTotal:\s+(\d+)\s*kB", line.strip())
        if m:
            return int(m.group(1)) // 1024
    return None


def classify_ram(ram_mb):
    """Coarse ram_class. The boundaries matter because outlier classes are gated by
    them: own-process GC / memory pressure essentially vanish above a couple of GB."""
    if not ram_mb:
        return "unknown"
    if ram_mb < 1536:
        return "go"
    if ram_mb < 3072:
        return "low"
    if ram_mb < 6144:
        return "mid"
    return "high"


def classify_storage(ls_block_out):
    """eMMC-class vs UFS/scsi-class hint from /sys/block device names. Storage class
    changes main-thread IO-stall severity by an order of magnitude."""
    names = (ls_block_out or "").split()
    if any(n.startswith("sd") for n in names):
        return "ufs-class"
    if any(n.startswith("mmcblk") for n in names):
        return "emmc-class"
    return "unknown"


def classify_tier(ram_mb, clusters):
    """Coarse entry/mid/flagship heuristic from RAM plus top cluster clock and cluster
    count. Deliberately crude — it exists so a device set can be scored for tier
    coverage; override it with judgement when it is wrong."""
    top_ghz = 0.0
    for c in clusters:
        if c.get("max_freq_khz"):
            top_ghz = max(top_ghz, c["max_freq_khz"] / 1e6)
    if not ram_mb and not top_ghz:
        return "unknown"
    if (ram_mb and ram_mb < 3072) or (top_ghz and top_ghz < 2.0):
        return "entry"
    if len(clusters) >= 3 and top_ghz >= 2.6 and (ram_mb or 0) >= 6144:
        return "flagship"
    return "mid"


def parse_thermal_sensors(text):
    """Return the mName values listed under "Current temperatures from HAL:" in
    `dumpsys thermalservice` output, or [] if the section is missing/unreadable.
    The set varies enormously by vendor — read it, never hardcode a sensor name."""
    if not text:
        return []
    sensors = []
    in_section = False
    for line in text.splitlines():
        if "Current temperatures from HAL:" in line:
            in_section = True
            continue
        if not in_section:
            continue
        if re.match(r"\s*Current .*:", line):
            break
        m = re.search(r"mName\s*=\s*([^,}]+)", line)
        if m:
            sensors.append(m.group(1).strip())
    return sensors


if __name__ == "__main__":
    sys.exit(main())
