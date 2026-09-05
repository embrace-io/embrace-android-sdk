#!/usr/bin/env python3
"""Build the maxims fixture: two synthetic campaigns shaped to exercise every verdict, then the Python
reference's outputs on them (score stdout, the ledger, MAXIMS.md). Closed-form data, no RNG.

Run from anywhere: python3 make_fixture.py   (rewrites everything beside this file)
"""
import json
import pathlib
import subprocess
import sys

HERE = pathlib.Path(__file__).resolve().parent
REPO = HERE.parents[5]
MAXIMS_PY = REPO / ".claude/skills/_shared/maxims.py"
NOW_A = "2026-09-04T20:00:00Z"
NOW_B = "2026-09-04T21:00:00Z"
NOW_DOC = "2026-09-04T21:30:00Z"

SECTIONS_FIXED = {
    "emb-embrace-impl-init": 1.0, "emb-bootstrapper-init": 0.4, "emb-modules-init": 30.0,
    "emb-span-service-init": 10.0, "emb-otel-tracer-init": 5.0, "emb-post-services-setup": 2.0,
    "emb-load-instrumentation": 1.5, "emb-install-native-crash-signal-handlers": 3.0,
    "emb-load-embrace-native-lib": 2.0, "emb-record-startup": 0.3, "emb-power-service-registration": 1.0,
    "emb-snapshot-session": 0.5,
}
# Block-and-resume sections take a state-dependent value: fast state, slow state.
BLOCK_RESUME = {
    "emb-config-service-init": (2.0, 4.0), "emb-payload-source-init": (1.0, 2.0),
    "emb-essential-service-init": (1.5, 3.0), "emb-post-init": (1.0, 2.0), "emb-delivery-init": (0.5, 1.0),
}


def dur(state_slow, cfgload):
    d = dict(SECTIONS_FIXED)
    for name, (fast, slow) in BLOCK_RESUME.items():
        d[name] = slow if state_slow else fast
    d["emb-persisted-config-load"] = cfgload
    return d


def factors(trace, window, cpu_share, rq_share, gc_ms, sys_server):
    running = round(window * cpu_share, 3)
    runnable = round(window * rq_share, 3)
    return {
        "trace": trace, "window_ms": window,
        "states": {"Running": running, "R+": runnable, "S": 2.0, "D+io:submit_bio_wait": 0.5, "D:futex_wait_queue": 0.1},
        "inproc": {"emb-io-reg": 1.0},
        "othercpu": {"swapper": 40.0, "system_server": sys_server, "/system/bin/surfaceflinger": 1.0},
        "eff_mhz": 2000.0, "run_cl0_ms": running / 2, "run_cl1_ms": running / 2,
        "art_verify_ms": 1.0, "art_classload_ms": 3.0, "lock_contention_ms": 0.2, "binder_txn_cnt": 0.0,
        "gc_slice_ms": gc_ms, "mem_available": 1500.0,
    }


def campaign_a(out):
    """mid-a: toggle present, fast path intact, three flavours of slow iteration, cohorts armed."""
    levels = [40.0, 50.0, 40.0, 50.0]
    for p, level in enumerate(levels, start=1):
        slow_state = level > 45
        recs, facs, launches = [], [], []
        for j in range(20):
            trace = f"pass{p}-iter{j:03d}.perfetto-trace"
            window = round(level + j * 0.1, 3)
            cpu, rq, sys_server = 0.70, 0.07, 3.0
            if j == 0:
                window = round(level * 1.3, 3)            # install aftermath on the first launch
            if j == 5:
                window, cpu, rq, sys_server = round(level + 8.0, 3), 0.40, 0.05, 30.0   # off-CPU behind a system_server burst
            if j == 11:
                window, cpu, rq = round(level + 8.0, 3), 0.60, 0.30   # starved
            if j == 17:
                window, sys_server = round(level + 8.0, 3), 30.0     # concurrent system_server burst
            cfgload = 2.0 if j == 0 else round(8.0 + j * 0.05, 3)
            recs.append({"trace": trace, "window_ms": window, "dur": dur(slow_state, cfgload),
                         "cpu_ms": {"0": round(window * cpu / 2, 3), "4": round(window * cpu / 2, 3)},
                         "states": {"emb-modules-init": {"Running": round(window * cpu * 0.8, 3), "S": 1.0}}})
            gc_ms = 1.5 if (p == 1 and j == 3) else 0.0
            facs.append(factors(trace, window, cpu, rq, gc_ms, sys_server))
            created = j in (0, 10)
            launches.append({
                "pid": 1000 * p + j, "emb.user_session_id": f"S{p}{'A' if j < 10 else 'B'}",
                "emb.app.version_startup_counter": str(j + 1),
                "start-first-session-duration-ms": "3.5" if created else "0.2",
                "post-init-duration-ms": "4.0" if created else "0.6",
                "init-cpu-pct": "70", "init-run-delay-pct": "7",
                "init_ms": window, "iteration": j, "cohort": "created" if created else "restored",
            })
        (out / f"pass{p}.json").write_text(json.dumps(recs, indent=1) + "\n")
        (out / f"pass{p}-factors.json").write_text(json.dumps(facs, indent=1) + "\n")
        (out / f"pass{p}-cohorts.json").write_text(json.dumps(
            {"method": "coldStartupBaselineProfile", "expected": "restored", "violations": [10],
             "launches": launches}, indent=1) + "\n")
    meta = {
        "serial": "FIXTURE-SERIAL-A", "method": "coldStartupBaselineProfile", "build_type": "benchmark",
        "run_shape": {"passes": 4, "iterations": 20},
        "device_profile": {"api_level": 35, "release": "15", "vendor": "samsung", "soc_family": "Exynos 850",
                           "clusters": [2002000], "ram_class": "3-4GB", "storage_class": "unknown"},
        "cell": {"levels": {"compile": "profile"}},
        "catalog_pin": 'embrace = "9.2.0"', "sdk_version": "9.2.0", "repo_head": "abc123",
        "started": "2026-09-04T18:00:00",
    }
    (out / "run-metadata.json").write_text(json.dumps(meta, indent=1) + "\n")


def campaign_b(out):
    """flagship-a: no toggle, first launch faster, poisoned fast path, no cohorts, no starvation."""
    levels = [12.0, 12.5, 12.0]
    for p, level in enumerate(levels, start=1):
        recs, facs = [], []
        for j in range(20):
            trace = f"pass{p}-iter{j:03d}.perfetto-trace"
            window = round(level + j * 0.02, 3)
            if j == 0:
                window = round(level * 0.8, 3)             # config fast path outweighs aftermath
            if j in (7, 15):
                window = round(level + 6.0, 3)             # slow with ordinary shares
            cfgload = 6.0 if j == 0 else round(3.0 + j * 0.05, 3)
            recs.append({"trace": trace, "window_ms": window, "dur": dur(False, cfgload),
                         "cpu_ms": {"0": round(window * 0.35, 3), "4": round(window * 0.35, 3)},
                         "states": {"emb-modules-init": {"Running": round(window * 0.56, 3), "S": 0.5}}})
            facs.append(factors(trace, window, 0.70, 0.03, 0.0, 2.0))
        (out / f"pass{p}.json").write_text(json.dumps(recs, indent=1) + "\n")
        (out / f"pass{p}-factors.json").write_text(json.dumps(facs, indent=1) + "\n")
    meta = {
        "serial": "FIXTURE-SERIAL-B", "method": "coldStartupBaselineProfile", "build_type": "benchmark",
        "run_shape": {"passes": 3, "iterations": 20},
        "device_profile": {"api_level": 35, "release": "15", "vendor": "Google", "soc_family": "GS201",
                           "clusters": [1803000, 2348000, 2850000], "ram_class": ">=8GB", "storage_class": "unknown"},
        "cell": {"levels": {"compile": "profile"}},
        "catalog_pin": 'embrace = "9.2.0"', "sdk_version": "9.2.0", "repo_head": "abc123",
        "started": "2026-09-04T19:00:00",
    }
    (out / "run-metadata.json").write_text(json.dumps(meta, indent=1) + "\n")


def reference_set(path):
    ref = {
        "recipe": {"run_shape": {"passes": 4, "iterations": 20}, "build_type": "benchmark",
                   "compile_state": "profile", "instrument": "emb-sdk-start"},
        "devices": {
            "flagship-a": {"serial": "FIXTURE-SERIAL-B", "tier": "flagship",
                           "profile": {"api_level": 35, "release": "15", "vendor": "Google", "soc_family": "GS201",
                                       "clusters": [1803000, 2348000, 2850000], "ram_class": ">=8GB",
                                       "storage_class": "unknown"}},
            "mid-a": {"serial": "FIXTURE-SERIAL-A", "tier": "entry-mid",
                      "profile": {"api_level": 35, "release": "15", "vendor": "samsung", "soc_family": "Exynos 850",
                                  "clusters": [2002000], "ram_class": "3-4GB", "storage_class": "unknown"}},
        },
    }
    path.write_text(json.dumps(ref, indent=1) + "\n")


def run(args):
    cp = subprocess.run([sys.executable, str(MAXIMS_PY), *args], capture_output=True, text=True, check=True)
    return cp.stdout


def main():
    for name in ("campaign-a", "campaign-b"):
        d = HERE / name
        d.mkdir(exist_ok=True)
        for old in d.iterdir():
            old.unlink()
    campaign_a(HERE / "campaign-a")
    campaign_b(HERE / "campaign-b")
    reference_set(HERE / "reference-set.json")
    ledger = HERE / "ledger.json"
    if ledger.exists():
        ledger.unlink()
    (HERE / "score-a.stdout.txt").write_text(run([
        "score", str(HERE / "campaign-a"), "--reference-set", str(HERE / "reference-set.json"),
        "--ledger", str(ledger), "--now", NOW_A]).replace(str(ledger), "<ledger>"))
    (HERE / "score-b.stdout.txt").write_text(run([
        "score", str(HERE / "campaign-b"), "--reference-set", str(HERE / "reference-set.json"),
        "--ledger", str(ledger), "--now", NOW_B]).replace(str(ledger), "<ledger>"))
    (HERE / "score-b-noledger.stdout.txt").write_text(run([
        "score", str(HERE / "campaign-b"), "--reference-set", str(HERE / "reference-set.json"),
        "--ledger", "none", "--now", NOW_B]))
    run(["render", "--ledger", str(ledger), "-o", str(HERE / "MAXIMS.md"), "--now", NOW_DOC])
    print("fixture written under", HERE)


if __name__ == "__main__":
    main()
