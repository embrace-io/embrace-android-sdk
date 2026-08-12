#!/usr/bin/env python3
"""Probe one connected device's CPU topology, thermal sensors, and OS version.

Run this FIRST per device in a multi-device campaign — its output feeds
--little-cpus on variance_analysis.py and LITTLE_CPUS on hypothesis_tests.py /
factors_report.py, since outlier_metrics.sql's run_cl0_ms/run_cl1_ms split is a fixed
cpu<4 partition that does not know which cluster is actually the little one.

Reads (via `adb -s <serial> shell ...`, each read independent so one failure doesn't
abort the probe):
  - getprop: manufacturer, model, Android release, API level
  - /proc/cpuinfo: "CPU part" per processor index
  - /sys/devices/system/cpu/cpufreq/policy*/related_cpus + .../cpuinfo_max_freq:
    the cluster map (cpus per policy, each policy's max frequency)
  - dumpsys thermalservice: sensor names under "Current temperatures from HAL:"

Derives clusters (cpus + max_freq_khz + cpu_part) and little_cpus (the cpus of the
cluster(s) whose max frequency is the lowest; if every cluster shares one max frequency
the device is treated as homogeneous and little_cpus falls back to the first policy's
cpus). Writes <output-dir>/<name>-topology.json and prints a one-paragraph summary.

Usage: python3 device_probe.py <serial> <name> <output-dir>
"""

import json
import os
import re
import subprocess
import sys


def main() -> int:
    if len(sys.argv) != 4:
        print("usage: python3 device_probe.py <serial> <name> <output-dir>", file=sys.stderr)
        return 1
    serial, name, out_dir = sys.argv[1], sys.argv[2], sys.argv[3]

    props_out = run_shell(
        serial,
        "getprop ro.product.manufacturer; getprop ro.product.model; "
        "getprop ro.build.version.release; getprop ro.build.version.sdk",
    )
    manufacturer, model, android, api = parse_props(props_out)

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

    thermal_out = run_shell(serial, "dumpsys thermalservice")
    thermal_sensors = parse_thermal_sensors(thermal_out)

    topology = {
        "serial": serial,
        "name": name,
        "manufacturer": manufacturer,
        "model": model,
        "android": android,
        "api": api,
        "clusters": clusters,
        "little_cpus": little_cpus,
        "homogeneous": homogeneous,
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
    print(
        f"{name}: {manufacturer or '?'} {model or '?'}, Android {android or '?'} "
        f"(API {api or '?'}); {len(clusters)} cpufreq policy(ies){homog_note}; "
        f"thermal sensors: {sensors_note}. Wrote {out_path} — pass "
        f"--little-cpus {little_arg} to variance_analysis.py, or set "
        f"LITTLE_CPUS={little_arg} for hypothesis_tests.py / factors_report.py, "
        f"when analyzing this device's traces."
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


def parse_props(text):
    lines = (text or "").splitlines()
    vals = [ln.strip() or None for ln in lines[:4]]
    while len(vals) < 4:
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


def parse_thermal_sensors(text):
    """Return the mName values listed under "Current temperatures from HAL:" in
    `dumpsys thermalservice` output, or [] if the section is missing/unreadable."""
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
