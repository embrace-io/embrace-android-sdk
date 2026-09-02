#!/usr/bin/env python3
"""Per-device benchmark campaign runner: N back-to-back passes on one device.

Usage:
  python3 fleet_campaign.py <serial> <dir-match> <camp-dir> <passes> [method]
                            [gap-after-pass] [--repo PATH]

  serial         adb serial of the target device
  dir-match      substring identifying the device's dir under connected/. AGP names that
                 dir after the device model, so use a distinctive fragment of the model
                 name as reported by `adb -s <serial> shell getprop ro.product.model`.
                 Two devices of the same model share the dir — never run them concurrently.
  camp-dir       output dir for this campaign (absolute, or relative to the cwd)
  passes         number of 50-iteration passes
  method         StartupBenchmarks method to run (default: coldStartup); see the
                 startup-analysis skill's SKILL.md (Compilation-mode arms) for
                 coldStartupNoAot / coldStartupBaselineProfile
  gap-after-pass optional: sleep 300 s after this pass number
  --repo PATH    repo root. Defaults to `git rev-parse --show-toplevel` from the cwd, so
                 running this from anywhere inside the checkout needs no argument.

Copies each pass's traces aside before the next pass wipes them; logs battery level/temp
AND silicon (thermalservice) temps around every pass — battery temp understates silicon by
30 C+ under load, and some devices report a constant battery temperature regardless of load,
so judge thermal state from the silicon reading, not battery.
Requires StartupBenchmarks.kt at iterations = 50.
"""

import datetime
import os
import re
import shutil
import subprocess
import sys
import time

# Cool gate between passes. The margin is relative to the device's own pre-campaign silicon
# temperature rather than an absolute number, because idle baselines differ by tier and vendor
# (34 C on the entry device, 31-37 C on the others) and an absolute threshold would either never
# gate the cool devices or never release the hot one.
COOL_MARGIN_C = 8.0
COOL_POLL_S = 30
MAX_COOL_WAIT_S = 900

# Retried only when the failure carries THIS signature. Matching on the specific string keeps a
# real benchmark failure from being retried into a false pass.
INSTALL_TIMEOUT_MARKER = "Failed to install split APK"

CONNECTED_SUBPATH = (
    "app/benchmark/build/outputs/connected_android_test_additional_output/"
    "benchmark/connected"
)


def main() -> int:
    argv = list(sys.argv[1:])
    repo = None
    if "--repo" in argv:
        i = argv.index("--repo")
        if i + 1 >= len(argv):
            print("--repo needs a PATH", file=sys.stderr)
            return 1
        repo = os.path.abspath(argv[i + 1])
        del argv[i:i + 2]
    if len(argv) < 4:
        print(__doc__, file=sys.stderr)
        return 1

    repo = repo or find_repo_root()
    if repo is None:
        print("could not determine the repo root — run from inside the checkout or "
              "pass --repo PATH", file=sys.stderr)
        return 1
    app = os.path.join(repo, "examples/ExampleApp")
    connected = os.path.join(app, CONNECTED_SUBPATH)
    if not os.path.isdir(app):
        print(f"{app} does not exist — is {repo} the right repo root?", file=sys.stderr)
        return 1

    serial, match, camp_dir = argv[0], argv[1], argv[2]
    passes = int(argv[3])
    method = argv[4] if len(argv) > 4 else "coldStartup"
    gap_after = int(argv[5]) if len(argv) > 5 else None
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

    def max_silicon():
        """Hottest silicon sensor, or None if unreadable."""
        try:
            out = subprocess.run(
                ["adb", "-s", serial, "shell", "dumpsys", "thermalservice"],
                capture_output=True, text=True, timeout=15,
            ).stdout
        except Exception:
            return None
        pairs = parse_silicon_temps(out)
        return max((val for _, val in pairs), default=None)

    def cool_down(baseline):
        """Wait for silicon to return near its pre-campaign temperature before the next pass.

        Gates on SILICON, never on battery: battery temperature understates silicon by 30+ C, so a
        battery-based gate reads "cool" at 31 C while the CPU sits at 55 C and happily starts the
        next pass. That is not a hypothetical - the entry-tier device failed its 9.0.0 leg twice
        this way, both times with `installCommit` hanging in ShellCommandUnresponsiveException on a
        device that had just finished a pass at 55 C CPU / 43 C skin. Heat is also a first-class
        confound for the measurement itself, so this protects validity as well as reliability.

        Best-effort: if the sensors are unreadable (some vendors expose none) it proceeds rather
        than blocking a whole campaign, and always logs which happened so a leg run hot stays
        auditable instead of silently contaminating a baseline.
        """
        if baseline is None:
            log("cool gate: no silicon baseline available - proceeding without it")
            return
        target = baseline + COOL_MARGIN_C
        waited = 0
        while waited < MAX_COOL_WAIT_S:
            now = max_silicon()
            if now is None:
                log("cool gate: silicon unreadable - proceeding")
                return
            if now <= target:
                if waited:
                    log(f"cool gate: {now:.1f}C <= {target:.1f}C after {waited}s")
                return
            time.sleep(COOL_POLL_S)
            waited += COOL_POLL_S
        log(f"cool gate: TIMED OUT after {waited}s at {max_silicon()}C (target {target:.1f}C) - "
            f"proceeding, but this pass ran warm")

    def settle_baseline():
        """The device's RESTING silicon temperature, not whatever it reads right now.

        Sampling once at campaign start is wrong whenever the campaign begins on a warm device -
        e.g. resuming a leg straight after a failed pass - because the baseline would then bake the
        heat in and the gate below would permit every subsequent pass. So poll until the
        temperature stops falling and take the settled value.
        """
        prev = max_silicon()
        if prev is None:
            return None
        waited = 0
        while waited < MAX_COOL_WAIT_S:
            time.sleep(COOL_POLL_S)
            waited += COOL_POLL_S
            now = max_silicon()
            if now is None:
                return prev
            if now >= prev - 1.0:      # stopped falling
                return min(prev, now)
            prev = now
        return prev

    log(f"repo {repo}")
    env = dict(os.environ, ANDROID_SERIAL=serial)
    baseline_silicon = settle_baseline()
    log(f"silicon baseline for the cool gate (settled): {baseline_silicon}")
    for p in range(1, passes + 1):
        dest = os.path.join(camp, f"pass{p}")
        if os.path.isdir(dest):
            log(f"pass {p} already collected, skipping")
            continue
        if p > 1:
            cool_down(baseline_silicon)
        log(f"pass {p}/{passes} starting, battery {battery()}, {silicon()}")
        t0 = datetime.datetime.now()
        proc = subprocess.run(
            [os.path.join(app, "gradlew"), "-p", app,
             ":app:benchmark:connectedBenchmarkAndroidTest",
             "-Pandroid.testInstrumentationRunnerArguments.class="
             f"io.embrace.android.benchmark.StartupBenchmarks#{method}",
             "-Pandroid.testInstrumentationRunnerArguments."
             "androidx.benchmark.dryRunMode.enable=false",
             "-Pandroid.testInstrumentationRunnerArguments."
             "androidx.benchmark.suppressErrors=LOW-BATTERY"],
            capture_output=True, text=True, cwd=repo, env=env,
        )
        with open(os.path.join(camp, f"pass{p}-gradle.log"), "w") as f:
            f.write(proc.stdout + "\n--- stderr ---\n" + proc.stderr)
        if proc.returncode != 0 and INSTALL_TIMEOUT_MARKER in (proc.stdout or ""):
            # Entry-tier devices intermittently blow ddmlib's install-commit timeout: the APK
            # install hangs and surfaces as ShellCommandUnresponsiveException. Measured on the Go
            # device across three attempts - it failed once on pass 2 and twice on pass 3, at 55 C
            # and again at 44 C, on a disk with 8.7 GB free. So it is neither thermal nor space,
            # it is a slow-eMMC timeout that happens to land on whichever pass gets unlucky.
            # Retried ONCE, and only for this specific signature: a genuine benchmark failure must
            # still abort rather than be papered over by repetition.
            log(f"pass {p} hit the install-timeout signature; retrying once")
            cool_down(baseline_silicon)
            proc = subprocess.run(
                [os.path.join(app, "gradlew"), "-p", app,
                 ":app:benchmark:connectedBenchmarkAndroidTest",
                 "-Pandroid.testInstrumentationRunnerArguments.class="
                 f"io.embrace.android.benchmark.StartupBenchmarks#{method}",
                 "-Pandroid.testInstrumentationRunnerArguments."
                 "androidx.benchmark.dryRunMode.enable=false",
                 "-Pandroid.testInstrumentationRunnerArguments."
                 "androidx.benchmark.suppressErrors=LOW-BATTERY"],
                capture_output=True, text=True, cwd=repo, env=env,
            )
            with open(os.path.join(camp, f"pass{p}-gradle-retry.log"), "w") as f:
                f.write(proc.stdout + "\n--- stderr ---\n" + proc.stderr)
        if proc.returncode != 0:
            log(f"pass {p} FAILED (exit {proc.returncode}); aborting")
            return 1
        src = resolve_trace_dir(connected, match, log)
        if src is None:
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


def resolve_trace_dir(connected, match, log):
    """Find the run's output directory under connected/, tolerantly.

    Macrobenchmark names this directory from the device's own model string plus the API level -
    e.g. 'SM-A145M - 15', with spaces and hyphens. A caller-supplied hint is almost always written
    the other way ('SM_A145'), and a plain substring test then fails AFTER a pass has already been
    measured, throwing away 12 minutes of device time per attempt. That exact failure wiped out a
    four-device baseline campaign on 2026-08-14, one doomed leg at a time.

    So: compare with separators and case normalised away, and fall back to the sole directory
    present when the hint matches nothing - gradle wipes this directory at the start of every run,
    so a single candidate is unambiguous. Both non-exact outcomes are logged loudly, and a genuine
    failure lists what WAS there, so the next person diagnoses it in one attempt instead of one
    attempt per leg.
    """
    def norm(text):
        return "".join(ch for ch in text.lower() if ch.isalnum())

    if not os.path.isdir(connected):
        log(f"no output directory at {connected}; the benchmark produced nothing - aborting")
        return None
    candidates = [d for d in sorted(os.listdir(connected))
                  if os.path.isdir(os.path.join(connected, d))]
    if not candidates:
        log(f"{connected} exists but is empty; the run produced no per-device output - aborting")
        return None

    wanted = norm(match)
    hits = [d for d in candidates if wanted in norm(d)]
    if len(hits) == 1:
        return os.path.join(connected, hits[0])
    if len(hits) > 1:
        log(f"hint '{match}' matches {len(hits)} directories {hits}; refusing to guess which "
            f"device's traces these are - narrow the hint - aborting")
        return None
    if len(candidates) == 1:
        log(f"hint '{match}' matched nothing, but exactly one output directory exists "
            f"({candidates[0]!r}) and gradle wipes this directory per run, so it is this run's - "
            f"using it. Fix the hint to silence this: the real name is what the device reports.")
        return os.path.join(connected, candidates[0])
    log(f"hint '{match}' matched none of {candidates} under {connected} (comparison ignores case "
        f"and separators, so this is a genuinely different name, not a '_' vs '-' problem) - "
        f"aborting")
    return None


def find_repo_root():
    """Repo root from the cwd via git; None if the cwd is not inside a checkout."""
    try:
        proc = subprocess.run(
            ["git", "rev-parse", "--show-toplevel"],
            capture_output=True, text=True, timeout=15,
        )
    except Exception:
        return None
    if proc.returncode != 0:
        return None
    root = proc.stdout.strip()
    return root or None


def parse_silicon_temps(text):
    """Return up to [3 CPU-type (mType=0) (name, value)] pairs plus any SKIN-type
    (mType=3) pair, from `dumpsys thermalservice`'s "Current temperatures from HAL:"
    block, in that section's order. Returns [] if the section is missing/unreadable —
    the exposed sensor set varies widely by vendor, so log whatever is there rather
    than expecting a particular sensor name."""
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
