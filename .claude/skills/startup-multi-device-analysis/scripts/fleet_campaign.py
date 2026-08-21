#!/usr/bin/env python3
"""Generic per-device benchmark campaign runner (replaces the per-device scripts).

Usage:
  python3 fleet_campaign.py <serial> <dir-match> <camp-dir> <passes> [method] [gap-after-pass]

  serial         adb serial of the target device
  dir-match      substring identifying the device's dir under connected/ (e.g. "Pixel 7")
  camp-dir       output dir for this campaign (absolute, or relative to the cwd)
  passes         number of 50-iteration passes
  method         StartupBenchmarks method to run (default: coldStartup); see
                 references/methodology.md for coldStartupNoAot / coldStartupBaselineProfile
  gap-after-pass optional: sleep 300 s after this pass number

Copies each pass's traces aside before the next pass wipes them; logs battery level/temp
AND silicon (thermalservice) temps around every pass — battery temp understates silicon by
30 C+ under load, so judge thermal state from the silicon reading, not battery.
Requires StartupBenchmarks.kt at iterations = 50.
"""

import datetime
import os
import re
import shutil
import subprocess
import sys
import time

REPO = "/Users/hansonho/work/embrace-android-sdk"
APP = os.path.join(REPO, "examples/ExampleApp")
CONNECTED = os.path.join(
    APP,
    "app/benchmark/build/outputs/connected_android_test_additional_output/"
    "benchmark/connected",
)


def main() -> int:
    serial, match, camp_dir = sys.argv[1], sys.argv[2], sys.argv[3]
    passes = int(sys.argv[4])
    method = sys.argv[5] if len(sys.argv) > 5 else "coldStartup"
    gap_after = int(sys.argv[6]) if len(sys.argv) > 6 else None
    camp = os.path.abspath(camp_dir)
    os.makedirs(camp, exist_ok=True)

    def log(msg):
        line = f"{datetime.datetime.now():%H:%M:%S} {msg}"
        print(line, flush=True)
        with open(os.path.join(camp, "campaign.log"), "a") as f:
            f.write(line + "\n")

    def battery():
        try:
            out = subprocess.run(
                ["adb", "-s", serial, "shell", "dumpsys", "battery"],
                capture_output=True, text=True, timeout=15,
            ).stdout
            temp = level = None
            for ln in out.splitlines():
                s = ln.strip()
                if s.startswith("temperature:"):
                    temp = int(s.split(":")[1].strip()) / 10.0
                elif s.startswith("level:"):
                    level = s.split(":")[1].strip()
            return f"{temp}C level={level}"
        except Exception as e:
            return f"battery-err:{e}"

    def silicon():
        try:
            out = subprocess.run(
                ["adb", "-s", serial, "shell", "dumpsys", "thermalservice"],
                capture_output=True, text=True, timeout=15,
            ).stdout
        except Exception as e:
            return f"silicon-err:{e}"
        pairs = parse_silicon_temps(out)
        return "silicon: " + " ".join(f"{name}={val:.1f}" for name, val in pairs) if pairs \
            else "silicon: n/a"

    env = dict(os.environ, ANDROID_SERIAL=serial)
    for p in range(1, passes + 1):
        dest = os.path.join(camp, f"pass{p}")
        if os.path.isdir(dest):
            log(f"pass {p} already collected, skipping")
            continue
        log(f"pass {p}/{passes} starting, battery {battery()}, {silicon()}")
        t0 = datetime.datetime.now()
        proc = subprocess.run(
            [os.path.join(APP, "gradlew"), "-p", APP,
             ":app:benchmark:connectedBenchmarkAndroidTest",
             "-Pandroid.testInstrumentationRunnerArguments.class="
             f"io.embrace.android.benchmark.StartupBenchmarks#{method}",
             "-Pandroid.testInstrumentationRunnerArguments."
             "androidx.benchmark.dryRunMode.enable=false",
             "-Pandroid.testInstrumentationRunnerArguments."
             "androidx.benchmark.suppressErrors=LOW-BATTERY"],
            capture_output=True, text=True, cwd=REPO, env=env,
        )
        with open(os.path.join(camp, f"pass{p}-gradle.log"), "w") as f:
            f.write(proc.stdout + "\n--- stderr ---\n" + proc.stderr)
        if proc.returncode != 0:
            log(f"pass {p} FAILED (exit {proc.returncode}); aborting")
            return 1
        src = None
        for d in os.listdir(CONNECTED):
            if match in d:
                src = os.path.join(CONNECTED, d)
        if src is None:
            log(f"no trace dir matching '{match}' under {CONNECTED}; aborting")
            return 1
        shutil.copytree(src, dest)
        n = len([f for f in os.listdir(dest) if f.endswith(".perfetto-trace")])
        mins = (datetime.datetime.now() - t0).total_seconds() / 60
        log(f"pass {p} done in {mins:.1f} min, {n} traces, battery {battery()}, {silicon()}")
        if gap_after == p:
            log("idle gap: 300 s")
            time.sleep(300)
    log(f"{camp} complete")
    return 0


def parse_silicon_temps(text):
    """Return up to [3 CPU-type (mType=0) (name, value)] pairs plus any SKIN-type
    (mType=3) pair, from `dumpsys thermalservice`'s "Current temperatures from HAL:"
    block, in that section's order. Returns [] if the section is missing/unreadable."""
    if not text:
        return []
    cpu_pairs, skin_pair = [], None
    in_section = False
    for line in text.splitlines():
        if "Current temperatures from HAL:" in line:
            in_section = True
            continue
        if not in_section:
            continue
        if re.match(r"\s*Current .*:", line):
            break
        m = re.search(r"mValue\s*=\s*([\-\d.]+).*mType\s*=\s*(\d+).*mName\s*=\s*([^,}]+)", line)
        if not m:
            continue
        value, mtype, name = float(m.group(1)), int(m.group(2)), m.group(3).strip()
        if mtype == 0 and len(cpu_pairs) < 3:
            cpu_pairs.append((name, value))
        elif mtype == 3 and skin_pair is None:
            skin_pair = (name, value)
    return cpu_pairs + ([skin_pair] if skin_pair else [])


if __name__ == "__main__":
    sys.exit(main())
