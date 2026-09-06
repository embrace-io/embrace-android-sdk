#!/usr/bin/env python3
"""Freeze the outputs of the Python startup-analysis skills on fixed inputs as JSON goldens.

Everything is written under this file's own directory (goldens/). Nothing outside it is touched.
See MANIFEST.md (written by hand alongside) for what each file is, how it was produced, and what a
Kotlin parity test should compare it against.

Run:  python3 <abs path>/dump_golden.py
"""
import json
import math
import os
import pathlib
import random
import statistics
import subprocess
import sys
import traceback

REPO = pathlib.Path("/Users/hansonho/work/embrace-android-sdk")
SKILLS = REPO / ".claude" / "skills"
GOLD = pathlib.Path(__file__).resolve().parent
INPUTS = GOLD / "inputs"
ERRORS = GOLD / "errors"
OUT_DIR = REPO / "claude-output"
STORE = OUT_DIR / "longitudinal" / "store.jsonl"
SWEEP_STORE = OUT_DIR / "longitudinal" / "sweep-store.jsonl"
X37_DIR = OUT_DIR / "2026-08-26-x37-engine-tail"
PLAN_EXAMPLE = SKILLS / "startup-version-factor-matrix" / "plan-example.json"

sys.path.insert(0, str(SKILLS / "_shared"))
sys.path.insert(0, str(SKILLS / "startup-longitudinal-tracking" / "scripts"))
import stats  # noqa: E402
import ingest_run  # noqa: E402  (module-level code is imports + sys.path only; no side effects)

SEED = 12345
LITTLE_CPUS_ENV = "0,1,2,3"


# ----------------------------------------------------------------------------------------------
# helpers
# ----------------------------------------------------------------------------------------------
def sanitize(obj):
    """Strict JSON: NaN/inf become strings so any parser can read the goldens."""
    if isinstance(obj, float):
        if math.isnan(obj):
            return "NaN"
        if math.isinf(obj):
            return "Infinity" if obj > 0 else "-Infinity"
        return obj
    if isinstance(obj, dict):
        return {k: sanitize(v) for k, v in obj.items()}
    if isinstance(obj, (list, tuple)):
        return [sanitize(v) for v in obj]
    return obj


def dump(name, obj):
    path = GOLD / name
    path.write_text(json.dumps(sanitize(obj), sort_keys=True, indent=1) + "\n")
    print(f"wrote {path.relative_to(GOLD)} ({path.stat().st_size} bytes)")


def record_error(name, exc):
    ERRORS.mkdir(parents=True, exist_ok=True)
    path = ERRORS / f"{name}.txt"
    path.write_text("".join(traceback.format_exception(exc)))
    print(f"ERROR in {name} -> {path}", file=sys.stderr)


def load_jsonl(path):
    records = []
    for line in path.read_text().splitlines():
        if line.strip():
            records.append(json.loads(line))
    return records


def chunk(values, size):
    return [values[i:i + size] for i in range(0, len(values), size)]


def run_script(script, args, env_extra=None, cwd=None):
    """Run a skill script as a subprocess; returns (returncode, stdout, stderr)."""
    env = dict(os.environ)
    if env_extra:
        env.update(env_extra)
    cp = subprocess.run([sys.executable, str(script)] + [str(a) for a in args],
                        capture_output=True, text=True, env=env, cwd=str(cwd or GOLD))
    return cp.returncode, cp.stdout, cp.stderr


def reserialize(path):
    """Re-write a JSON file produced by a script with sort_keys=True, indent=1 (semantics kept)."""
    obj = json.loads(path.read_text())
    path.write_text(json.dumps(sanitize(obj), sort_keys=True, indent=1) + "\n")
    return obj


# ----------------------------------------------------------------------------------------------
# 1. rng.json
# ----------------------------------------------------------------------------------------------
def golden_rng():
    out = {"seed": SEED, "sys_version": sys.version,
           "note": ("Each sub-experiment uses a FRESH random.Random(12345); they are independent "
                    "of one another and of every other golden in this directory.")}
    rng = random.Random(SEED)
    out["random_first_1000"] = [rng.random() for _ in range(1000)]
    rng = random.Random(SEED)
    out["getrandbits32_first_200"] = [rng.getrandbits(32) for _ in range(200)]
    rng = random.Random(SEED)
    shuffles = []
    for _ in range(3):
        xs = list(range(50))
        rng.shuffle(xs)
        shuffles.append(xs)
    out["shuffle_range50_three_times"] = shuffles
    rng = random.Random(SEED)
    pool = list(range(97))
    out["choice_range97_first_300"] = [rng.choice(pool) for _ in range(300)]
    dump("rng.json", out)


# ----------------------------------------------------------------------------------------------
# 2. stats_synthetic.json
# ----------------------------------------------------------------------------------------------
def synthetic_arms():
    # 5 clusters x 8 values per arm. Values are exact binary fractions (multiples of 0.25) so
    # a Kotlin test can rebuild them from the formula OR read them from the file bit-exactly.
    a = [[50.0 + 3.0 * c + ((i * 7) % 5) - 2.0 + 0.25 * i for i in range(8)] for c in range(5)]
    b = [[48.0 + 2.5 * c + ((i * 3) % 5) - 2.0 + 0.5 * i for i in range(8)] for c in range(5)]
    return a, b


def golden_stats_synthetic():
    a, b = synthetic_arms()
    a_flat = sorted(x for c in a for x in c)
    b_flat = sorted(x for c in b for x in c)
    out = {"inputs": {
        "a_clusters": a, "b_clusters": b,
        "a_formula": "a[c][i] = 50 + 3*c + ((i*7) % 5) - 2 + 0.25*i, c in 0..4, i in 0..7",
        "b_formula": "b[c][i] = 48 + 2.5*c + ((i*3) % 5) - 2 + 0.5*i, c in 0..4, i in 0..7",
        "a_flat_sorted": a_flat, "b_flat_sorted": b_flat,
        "seed": SEED, "resamples": stats.DEFAULT_RESAMPLES,
    }}
    out["constants"] = {"MIN_CLUSTERS_PER_ARM": stats.MIN_CLUSTERS_PER_ARM,
                        "DEFAULT_RESAMPLES": stats.DEFAULT_RESAMPLES,
                        "QUANTILE_MIN_N": {str(k): v for k, v in stats.QUANTILE_MIN_N.items()}}

    ps = [0.0, 0.1, 0.25, 0.5, 0.75, 0.9, 0.95, 0.99, 1.0]
    out["quantile"] = {
        "a_flat": {str(p): stats.quantile(a_flat, p) for p in ps},
        "b_flat": {str(p): stats.quantile(b_flat, p) for p in ps},
        "empty": stats.quantile([], 0.5),
        "singleton_42.5": stats.quantile([42.5], 0.9),
        "two_values_[1,3]": {str(p): stats.quantile([1.0, 3.0], p) for p in ps},
    }
    ns = [20, 50, 100, 200, 1000]
    qps = [0.5, 0.9, 0.95, 0.99, 0.999]
    out["quantile_support"] = {str(n): {str(p): stats.quantile_support(n, p) for p in qps}
                               for n in ns}
    out["quantile_ci_trustworthy"] = {str(n): {str(p): stats.quantile_ci_trustworthy(n, p)
                                               for p in qps} for n in ns}

    out["icc_oneway"] = {
        "a": stats.icc_oneway(a), "b": stats.icc_oneway(b),
        "single_cluster": stats.icc_oneway([a[0]]),
        "constant_clusters": stats.icc_oneway([[5.0, 5.0, 5.0], [7.0, 7.0, 7.0]]),
        "singletons_only": stats.icc_oneway([[1.0], [2.0], [3.0]]),
    }
    out["design_effect"] = {"a": stats.design_effect(a), "b": stats.design_effect(b),
                            "empty": stats.design_effect([]),
                            "single_cluster": stats.design_effect([a[0]])}

    out["cluster_bootstrap_diff"] = {
        "median_default": stats.cluster_bootstrap_diff(a, b),
        "quantile_p90": stats.cluster_bootstrap_diff(a, b, statistic="quantile", p=0.90),
        "quantile_p95": stats.cluster_bootstrap_diff(a, b, statistic="quantile", p=0.95),
        "mean": stats.cluster_bootstrap_diff(a, b, statistic="mean"),
        "alpha_0.10": stats.cluster_bootstrap_diff(a, b, alpha=0.10),
        "too_few_clusters_3v3": stats.cluster_bootstrap_diff(a[:3], b[:3]),
    }
    out["cluster_permutation_test"] = {
        "median_default": stats.cluster_permutation_test(a, b),
        "quantile_p90": stats.cluster_permutation_test(a, b, statistic="quantile", p=0.90),
        "too_few_clusters_3v3": stats.cluster_permutation_test(a[:3], b[:3]),
        "too_few_clusters_1v1": stats.cluster_permutation_test(a[:1], b[:1]),
    }
    out["cliffs_delta"] = {
        "a_vs_b": stats.cliffs_delta(a_flat, b_flat),
        "b_vs_a": stats.cliffs_delta(b_flat, a_flat),
        "identical": stats.cliffs_delta(a_flat, a_flat),
        "empty_a": stats.cliffs_delta([], b_flat),
        "small_[1,2,3]_vs_[2,3,4]": stats.cliffs_delta([1.0, 2.0, 3.0], [2.0, 3.0, 4.0]),
    }
    out["cliffs_delta_caveat"] = {str(v): stats.cliffs_delta_caveat(v)
                                  for v in (None, 0.05, 0.1, 0.2, 0.3, 0.65)}
    out["tost_equivalence"] = {
        "margin_10": stats.tost_equivalence(a, b, 10.0),
        "margin_2": stats.tost_equivalence(a, b, 2.0),
        "too_few_clusters": stats.tost_equivalence(a[:2], b[:2], 10.0),
        "zero_baseline": stats.tost_equivalence([[0.0] * 4] * 4, [[0.0] * 4] * 4, 5.0),
    }
    bh_inputs = [0.01, 0.04, 0.03, None, 0.2, 0.001, 0.05, 0.9]
    out["benjamini_hochberg"] = {
        "inputs": bh_inputs,
        "alpha_0.05": stats.benjamini_hochberg(bh_inputs),
        "alpha_0.10": stats.benjamini_hochberg(bh_inputs, alpha=0.10),
        "all_none": stats.benjamini_hochberg([None, None]),
        "empty": stats.benjamini_hochberg([]),
    }
    out["required_n"] = {
        "cv15_effect5": stats.required_n(15.0, 5.0),
        "cv15_effect5_power0.5": stats.required_n(15.0, 5.0, power=0.5),
        "cv15_effect5_deff3.2": stats.required_n(15.0, 5.0, deff=3.2),
        "cv15_effect5_deff0.5": stats.required_n(15.0, 5.0, deff=0.5),
        "cv30_effect2": stats.required_n(30.0, 2.0),
        "effect0": stats.required_n(15.0, 0.0),
    }
    out["min_detectable_effect"] = {
        "n200_cv15": stats.min_detectable_effect(200, 15.0),
        "n200_cv15_deff5": stats.min_detectable_effect(200, 15.0, deff=5.0),
        "n50_cv20_power0.5": stats.min_detectable_effect(50, 20.0, power=0.5),
        "n0": stats.min_detectable_effect(0, 15.0),
    }
    out["n_for_quantile"] = {
        "sigma0.4_p0.9_rel0.05": stats.n_for_quantile(0.4, 0.9, 0.05),
        "sigma0.4_p0.99_rel0.05_deff2": stats.n_for_quantile(0.4, 0.99, 0.05, deff=2.0),
        "sigma0.3_p0.5_rel0.02": stats.n_for_quantile(0.3, 0.5, 0.02),
        "sigma0.6_p0.95_rel0.1_z1.645": stats.n_for_quantile(0.6, 0.95, 0.1, confidence_z=1.645),
    }
    out["n_for_exceedance_rate"] = {
        "rate0.05_abs0.01": stats.n_for_exceedance_rate(0.05, 0.01),
        "rate0.05_abs0.01_deff3": stats.n_for_exceedance_rate(0.05, 0.01, deff=3.0),
        "rate0.5_abs0.05": stats.n_for_exceedance_rate(0.5, 0.05),
    }
    out["cuped_variance_reduction"] = {str(r): stats.cuped_variance_reduction(r)
                                       for r in (0.7, 0.0, -0.5, 1.5, -1.5)}
    ndtri_ps = [0.001, 0.01, 0.02, 0.02425, 0.05, 0.1, 0.25, 0.5, 0.75, 0.9, 0.95, 0.975,
                0.97575, 0.99, 0.999]
    out["_ndtri"] = {str(p): stats._ndtri(p) for p in ndtri_ps}
    out["sigma_from_quantile_ratio"] = {str(r): stats.sigma_from_quantile_ratio(r)
                                        for r in (1.0, 1.5, 2.0, 3.7)}
    out["dilution"] = {"share4_change25": stats.dilution(4.0, 25.0),
                       "share35_change-10": stats.dilution(35.0, -10.0),
                       "share100_change3": stats.dilution(100.0, 3.0)}
    out["practical"] = {"3.9_vs_4": stats.practical(3.9, 4.0), "-4_vs_4": stats.practical(-4.0, 4.0),
                        "5_vs_4": stats.practical(5.0, 4.0), "0_vs_0": stats.practical(0.0, 0.0)}
    out["compare"] = {
        "default": stats.compare(a, b, "A", "B"),
        "noise2_q50_q90": stats.compare(a, b, "A", "B", noise_band_pct=2.0, quantiles=(0.5, 0.9)),
        "too_few_clusters_3v3": stats.compare(a[:3], b[:3], "A", "B"),
    }
    dump("stats_synthetic.json", out)


# ----------------------------------------------------------------------------------------------
# 3. stats_store.json
# ----------------------------------------------------------------------------------------------
STORE_PAIRS = [
    # (label, a_run_id, b_run_id)  -- b minus a is the reported direction
    ("mid-a 9.1.0 -> 9.2.0", "9.1.0__mid-a", "9.2.0__mid-a"),
    ("entry-a 8.3.0 -> 9.2.0", "8.3.0__entry-a", "9.2.0__entry-a"),
    ("flagship-a 9.0.0 -> 9.2.0", "9.0.0__flagship-a", "9.2.0__flagship-a"),
    ("mid-b 9.0.0 -> 9.2.0", "9.0.0__mid-b", "9.2.0__mid-b"),
    # extra: exercises the "arm is not 200 values" skip path (flagship 9.1.0 has 198)
    ("flagship-a 9.1.0 -> 9.2.0 (skip: 198)", "9.1.0__flagship-a", "9.2.0__flagship-a"),
]


def all_store_records():
    recs = {}
    for path in (STORE, SWEEP_STORE):
        for rec in load_jsonl(path):
            recs[rec["run_id"]] = (rec, path.name)
    return recs


def golden_stats_store():
    recs = all_store_records()
    out = {"note": ("windows_ms chunked in file order into 10 clusters of 20 (passes). "
                    "Store windows_ms are rounded to 3 dp by ingest_run; stats here are on those "
                    "stored values. Direction: b minus a."),
           "seed": SEED, "resamples": stats.DEFAULT_RESAMPLES,
           "arm_lengths": {rid: {"n": len(r["windows_ms"]), "store": src,
                                 "device_key": r["device_key"], "sdk_version": r["sdk_version"]}
                           for rid, (r, src) in sorted(recs.items())},
           "pairs": {}}
    for label, a_id, b_id in STORE_PAIRS:
        a_rec, a_src = recs[a_id]
        b_rec, b_src = recs[b_id]
        a_w, b_w = a_rec["windows_ms"], b_rec["windows_ms"]
        entry = {"a_run_id": a_id, "b_run_id": b_id, "a_store": a_src, "b_store": b_src,
                 "a_n": len(a_w), "b_n": len(b_w)}
        if len(a_w) != 200 or len(b_w) != 200:
            entry["skipped"] = True
            entry["reason"] = (f"arm(s) not exactly 200 values (a={len(a_w)}, b={len(b_w)}); "
                               f"cannot be chunked into 10x20 passes, pass-level stats skipped")
            entry["a_flat_sorted_median"] = stats.quantile(sorted(a_w), 0.5)
            entry["b_flat_sorted_median"] = stats.quantile(sorted(b_w), 0.5)
            out["pairs"][label] = entry
            continue
        a, b = chunk(a_w, 20), chunk(b_w, 20)
        entry.update({
            "a_clusters": a, "b_clusters": b,
            "design_effect": {"a": stats.design_effect(a), "b": stats.design_effect(b)},
            "icc_oneway": {"a": stats.icc_oneway(a), "b": stats.icc_oneway(b)},
            "cluster_bootstrap_diff": {
                "median": stats.cluster_bootstrap_diff(a, b),
                "p90": stats.cluster_bootstrap_diff(a, b, statistic="quantile", p=0.90),
                "p95": stats.cluster_bootstrap_diff(a, b, statistic="quantile", p=0.95),
            },
            "cluster_permutation_test": {
                "median": stats.cluster_permutation_test(a, b),
                "p90": stats.cluster_permutation_test(a, b, statistic="quantile", p=0.90),
            },
            "cliffs_delta": stats.cliffs_delta(sorted(a_w), sorted(b_w)),
            "compare": stats.compare(a, b, a_id, b_id),
        })
        out["pairs"][label] = entry
        print(f"  stats_store: {label} done")
    dump("stats_store.json", out)


# ----------------------------------------------------------------------------------------------
# 4. derive_store.json
# ----------------------------------------------------------------------------------------------
def golden_derive_store():
    out = {"note": ("ingest_run.derive(windows_ms) per record; 'stored_derived' is what the store "
                    "already holds (computed at ingest on UNROUNDED windows, so p90/p95/max/iqr "
                    "may differ from 'derived' in the 4th decimal)."),
           "records": {}}
    for path in (STORE, SWEEP_STORE):
        for rec in load_jsonl(path):
            out["records"][rec["run_id"]] = {
                "store": path.name,
                "n_windows": len(rec["windows_ms"]),
                "derived": ingest_run.derive(rec["windows_ms"]),
                "stored_derived": rec.get("derived"),
            }
    out["edge_cases"] = {
        "empty": ingest_run.derive([]),
        "one_value": ingest_run.derive([12.5]),
        "three_values": ingest_run.derive([3.0, 1.0, 2.0]),
        "four_values": ingest_run.derive([4.0, 1.0, 3.0, 2.0]),
        "five_values": ingest_run.derive([5.0, 1.0, 3.0, 2.0, 4.0]),
    }
    dump("derive_store.json", out)


# ----------------------------------------------------------------------------------------------
# 5. trend_store.json (+ stdout)
# ----------------------------------------------------------------------------------------------
def golden_trend(store_path, json_name, stdout_name):
    script = SKILLS / "startup-longitudinal-tracking" / "scripts" / "trend_report.py"
    target = GOLD / json_name
    rc, so, se = run_script(script, ["--store", store_path, "--json", target])
    (GOLD / stdout_name).write_text(so)
    if rc != 0 or not target.exists():
        raise RuntimeError(f"trend_report.py rc={rc}\nstderr:\n{se}")
    reserialize(target)
    print(f"wrote {json_name}, {stdout_name}")


def trend_windows(centre, fat_tail=False):
    """200 deterministic values around `centre`: median ~centre, p90 ~centre+3, max ~centre+9.
    fat_tail adds +12 to every 10th value, which moves p90 but leaves the median alone."""
    out = []
    for i in range(200):
        w = centre + 0.3 * ((i * 37) % 21 - 10) + (6.0 if i % 20 == 19 else 0.0)
        if fat_tail and i % 10 == 9:
            w += 12.0
        out.append(round(w, 3))
    return out


def trend_record(run_id, device, sdk, measured_at, centre, signals, eligible=True, fat_tail=False):
    windows = trend_windows(centre, fat_tail)
    return {
        "run_id": run_id, "ingested_at": measured_at, "measured_at": measured_at,
        "device_key": device, "device_profile": {"api_level": 35},
        "sdk_version": sdk, "app_build_id": None,
        "recipe": {"build_type": "benchmark", "compile_state": "profile",
                   "instrument": "emb-sdk-start", "run_shape": {"passes": 10, "iterations": 20},
                   "trace_processor_version": "v46.0", "trace_processor_path": None},
        "conditions": {"compile": "profile"},
        "baseline_eligible": eligible, "signals_present": signals,
        "trace_health": {"traces": 200, "buffer_loss": 0, "parse_errors": 0,
                         "signals_from_clean_trace": True},
        "windows_ms": windows, "derived": ingest_run.derive(windows),
        "source_skill": "run-metadata.json", "notes": "",
    }


def build_trend_store():
    """A store whose series have ENOUGH runs to reach trend_report's verdict/JSON code paths."""
    INPUTS.mkdir(parents=True, exist_ok=True)
    base_sig = ["emb-modules-init", "emb-record-startup", "emb-sdk-start"]
    recs = [
        # series synth-a / 9.2.0: 3 baseline runs, a candidate, a confirmed REGRESSION whose run
        # also loses a signal and gains one, plus an ad-hoc (not baseline-eligible) run
        trend_record("ta-1", "synth-a", "9.2.0", "2026-08-01T10:00:00", 40.0, base_sig),
        trend_record("ta-2", "synth-a", "9.2.0", "2026-08-02T10:00:00", 41.0, base_sig),
        trend_record("ta-3", "synth-a", "9.2.0", "2026-08-03T10:00:00", 40.5, base_sig),
        trend_record("ta-4", "synth-a", "9.2.0", "2026-08-04T10:00:00", 43.0, base_sig),
        trend_record("ta-5", "synth-a", "9.2.0", "2026-08-05T10:00:00", 43.5,
                     ["emb-modules-init", "emb-sdk-start", "emb-new-thing"]),
        trend_record("ta-6", "synth-a", "9.2.0", "2026-08-06T10:00:00", 41.2, base_sig,
                     eligible=False),
        # same device, older version, one run -> feeds only the VERSION COMPARISON block
        trend_record("ta-0", "synth-a", "9.1.0", "2026-07-30T10:00:00", 47.0, base_sig),
        # series synth-b / 9.2.0: baseline of 3 then a TAIL-ONLY move (median flat, p90 up)
        trend_record("tb-1", "synth-b", "9.2.0", "2026-08-01T12:00:00", 30.0, base_sig),
        trend_record("tb-2", "synth-b", "9.2.0", "2026-08-02T12:00:00", 30.2, base_sig),
        trend_record("tb-3", "synth-b", "9.2.0", "2026-08-03T12:00:00", 30.1, base_sig),
        trend_record("tb-4", "synth-b", "9.2.0", "2026-08-04T12:00:00", 30.3, base_sig,
                     fat_tail=True),
        # series synth-c: only two runs -> baseline of one run, second judged against it
        trend_record("tc-1", "synth-c", "9.2.0", "2026-08-01T14:00:00", 80.0, []),
        trend_record("tc-2", "synth-c", "9.2.0", "2026-08-02T14:00:00", 75.0, []),
        # a record with no derived.n is skipped entirely
        {"run_id": "junk", "device_key": "synth-c", "sdk_version": "9.2.0", "derived": {},
         "windows_ms": [], "recipe": {}, "conditions": {}},
    ]
    path = INPUTS / "trend-store.jsonl"
    path.write_text("".join(json.dumps(r, sort_keys=True) + "\n" for r in recs))
    return path


# ----------------------------------------------------------------------------------------------
# 6. reproducibility.json (+ synthetic corpus)
# ----------------------------------------------------------------------------------------------
def corpus_record(contributor, unit, model, os_build, sdk, median, p90, pass_medians,
                  windows, installed="50-150", patch="2026-06-05", settings=None, n_override=None):
    n = len(windows) if n_override is None else n_override
    values = sorted(windows)
    derived = {"n": n, "median": median, "p90": p90, "p95": values[-2] if values else None,
               "max": values[-1] if values else None, "iqr": 1.5,
               "pass_medians": {str(i + 1): m for i, m in enumerate(pass_medians)}}
    return {
        "schema_version": 1,
        "submission_id": f"{contributor}-run-{model.replace(' ', '')}-{sdk}",
        "submitted_at": "2026-08-30T12:00:00",
        "contributor": contributor,
        "sdk_version": sdk,
        "app_build_id": "deadbeef",
        "recipe": {"build_type": "benchmark", "compile_state": "profile",
                   "instrument": "emb-sdk-start", "run_shape": {"passes": 10, "iterations": 20},
                   "trace_processor_version": "v46.0"},
        "conditions": {"compile": "profile"},
        "derived": derived,
        "windows_ms": windows,
        "signals_present": ["emb-sdk-start", "emb-modules-init"],
        "trace_health": {"traces": n, "buffer_loss": 0, "parse_errors": 0,
                         "signals_from_clean_trace": True},
        "notes": "",
        "unit_id": unit,
        "model": model,
        "os_build": os_build,
        "api_level": "35",
        "security_patch": patch,
        "skin_version": "AP4A.250905.001",
        "kernel_version": "6.1.99-android14-11",
        "soc_family": "Tensor G2",
        "storage_free_pct": "40-60%",
        "battery_health": "2",
        "installed_app_count": installed,
        "device_settings": settings or {"animation_scale": "1.0", "background_process_limit": "null",
                                        "battery_saver": "0"},
    }


def build_corpus():
    INPUTS.mkdir(parents=True, exist_ok=True)
    recs = []
    # cell 1: reproduces (two contributors, two units, agree on median, p90 and shape)
    w1 = [12.0 + 0.1 * (i % 7) + 0.02 * i for i in range(40)]
    w2 = [12.3 + 0.1 * (i % 5) + 0.02 * i for i in range(40)]
    recs.append(corpus_record("alice", "unit-a1", "Pixel 7 Pro",
                              "google/cheetah/cheetah:15/AP4A.250905.001/13579:user/release-keys",
                              "9.2.0", 12.0, 15.0, [11.8, 12.0, 12.1, 12.2, 11.9, 12.0, 12.3, 12.1,
                                                    12.0, 11.9], w1))
    recs.append(corpus_record("bob", "unit-b7", "Pixel 7 Pro",
                              "google/cheetah/cheetah:15/AP4A.250905.001/13579:user/release-keys",
                              "9.2.0", 12.3, 15.4, [12.1, 12.4, 12.3, 12.5, 12.2, 12.3, 12.6, 12.4,
                                                    12.3, 12.2], w2))
    # cell 2: does NOT reproduce - medians 25% apart, tails apart, one two-state shape, and
    # three provenance fields that differ (installed_app_count, security_patch, device_settings)
    w3 = [60.0 + 0.5 * (i % 6) + 0.05 * i for i in range(40)]
    w4 = [75.0 + 0.5 * (i % 6) + 0.05 * i for i in range(40)]
    recs.append(corpus_record("alice", "unit-a2", "Galaxy A14",
                              "samsung/a14xxx/a14:15/UP1A.231005.007/A145FXXU5CXK1:user/release-keys",
                              "9.2.0", 60.0, 66.0, [59.0, 60.0, 61.0, 60.5, 59.5, 60.2, 60.8, 60.1,
                                                    59.8, 60.4], w3, installed="<50",
                              patch="2026-05-01"))
    recs.append(corpus_record("carol", "unit-c3", "Galaxy A14",
                              "samsung/a14xxx/a14:15/UP1A.231005.007/A145FXXU5CXK1:user/release-keys",
                              "9.2.0", 75.0, 90.0, [66.0, 66.5, 67.0, 66.2, 84.0, 84.5, 85.0, 84.2,
                                                    84.8, 85.5], w4, installed=">150",
                              patch="2026-07-01",
                              settings={"animation_scale": "0.5", "background_process_limit": "null",
                                        "battery_saver": "1"}))
    # cell 3: insufficient data (one contributor, one unit)
    w5 = [30.0 + 0.1 * (i % 4) for i in range(40)]
    recs.append(corpus_record("dave", "unit-d4", "Pixel 3",
                              "google/blueline/blueline:12/SP1A.210812.016.C1/8618562:user/release-keys",
                              "9.2.0", 30.0, 30.4, [29.9, 30.0, 30.1, 30.0], w5))
    # a record with derived.n == 0 must be ignored entirely
    recs.append(corpus_record("erin", "unit-e5", "Pixel 3",
                              "google/blueline/blueline:12/SP1A.210812.016.C1/8618562:user/release-keys",
                              "9.2.0", 0.0, 0.0, [], [], n_override=0))
    path = INPUTS / "corpus.jsonl"
    path.write_text("".join(json.dumps(r, sort_keys=True) + "\n" for r in recs))
    return path


def golden_reproducibility():
    corpus = build_corpus()
    script = SKILLS / "startup-global-corpus" / "scripts" / "reproducibility_report.py"
    target = GOLD / "reproducibility.json"
    rc, so, se = run_script(script, ["--corpus", corpus, "--json", target])
    (GOLD / "reproducibility.stdout.txt").write_text(so)
    if rc != 0 or not target.exists():
        raise RuntimeError(f"reproducibility_report.py rc={rc}\nstderr:\n{se}")
    reserialize(target)
    print("wrote reproducibility.json, reproducibility.stdout.txt")


# ----------------------------------------------------------------------------------------------
# 7. matrix_plan.json (+ stdout)
# ----------------------------------------------------------------------------------------------
def golden_matrix_plan():
    script = SKILLS / "startup-version-factor-matrix" / "scripts" / "matrix_plan.py"
    target = GOLD / "matrix_plan.json"
    rc, so, se = run_script(script, [PLAN_EXAMPLE, "--emit", target])
    (GOLD / "matrix_plan.stdout.txt").write_text(so)
    if rc != 0 or not target.exists():
        raise RuntimeError(f"matrix_plan.py rc={rc}\nstderr:\n{se}")
    reserialize(target)
    print("wrote matrix_plan.json, matrix_plan.stdout.txt")


# ----------------------------------------------------------------------------------------------
# 8. multi-device scripts on synthetic passN.json / passN-factors.json
# ----------------------------------------------------------------------------------------------
SECTIONS_16 = [
    "emb-embrace-impl-init", "emb-bootstrapper-init", "emb-modules-init",
    "emb-persisted-config-load", "emb-config-service-init", "emb-span-service-init",
    "emb-otel-tracer-init", "emb-essential-service-init", "emb-delivery-init",
    "emb-payload-source-init", "emb-post-init", "emb-post-services-setup",
    "emb-load-instrumentation", "emb-install-native-crash-signal-handlers",
    "emb-load-embrace-native-lib", "emb-record-startup",
]
EXTRA_SECTIONS = ["emb-power-service-registration", "emb-snapshot-session"]
# share of the window each section takes (deterministic; parents overlap children on purpose)
SECTION_SHARE = {
    "emb-embrace-impl-init": 0.08, "emb-bootstrapper-init": 0.06, "emb-modules-init": 0.90,
    "emb-persisted-config-load": None,  # explicit table below
    "emb-config-service-init": 0.03, "emb-span-service-init": 0.30, "emb-otel-tracer-init": 0.25,
    "emb-essential-service-init": 0.02, "emb-delivery-init": 0.05, "emb-payload-source-init": 0.09,
    "emb-post-init": 0.01, "emb-post-services-setup": 0.06, "emb-load-instrumentation": 0.05,
    "emb-install-native-crash-signal-handlers": 0.20, "emb-load-embrace-native-lib": 0.18,
    "emb-record-startup": 0.15, "emb-power-service-registration": 0.04,
    "emb-snapshot-session": 0.05,
}
CFGLOAD_BY_ITER = [2.0, 8.5, 9.0, 4.5, 9.5, 10.0]
LITTLE_SHARE_BY_ITER = [0.8, 0.6, 0.3, 0.05, 0.9, 0.02]
PASS_BASE = {1: 40.0, 2: 46.0, 3: 41.0}
BLOCKED_FUNCS = ["submit_bio_wait", "futex_wait_queue", "do_page_fault"]


def synth_window(p, j, scale):
    w = PASS_BASE[p] + 2.0 * j
    if p == 2 and j == 5:
        w += 12.0     # a big slow iteration on a cluster-1-majority placement -> H1 falsifier
    if p == 3 and j == 4:
        w += 7.0      # a slow iteration on a little-majority placement
    return w * scale


def synth_pass(p, n_iter, scale, drop_section=None):
    data = []
    for j in range(n_iter):
        w = synth_window(p, j, scale)
        share = LITTLE_SHARE_BY_ITER[j % len(LITTLE_SHARE_BY_ITER)]
        run_total = 0.7 * w
        cpu_ms = {"0": share * run_total * 0.5, "1": share * run_total * 0.5,
                  "4": (1 - share) * run_total * 0.6, "5": (1 - share) * run_total * 0.4}
        dur = {}
        states = {}
        for sec in SECTIONS_16 + EXTRA_SECTIONS:
            if sec == drop_section:
                continue
            if sec == "emb-persisted-config-load":
                d = (CFGLOAD_BY_ITER[j % len(CFGLOAD_BY_ITER)] + 0.1 * p) * scale
            else:
                d = SECTION_SHARE[sec] * w
            if sec == "emb-power-service-registration" and j == 2:
                d += 12.0 * scale   # a binder stall (> POWER_STALL_MS)
            dur[sec] = d
            states[sec] = {"Running": 0.6 * d, "S": 0.2 * d, "D+io": 0.1 * d, "R": 0.05 * d,
                           "R+": 0.05 * d}
        if "emb-load-embrace-native-lib" in dur and j == 1:
            states["emb-load-embrace-native-lib"]["DK+io"] = 3.0 * scale
        data.append({"trace": f"pass{p}-iter{j:03d}.perfetto-trace", "window_ms": w,
                     "dur": dur, "cpu_ms": cpu_ms, "states": states})
    return data


def synth_factors_pass(p, n_iter):
    data = []
    for j in range(n_iter):
        w = synth_window(p, j, 1.0)
        share = LITTLE_SHARE_BY_ITER[j % len(LITTLE_SHARE_BY_ITER)]
        run_total = 0.7 * w
        states = {"Running": run_total, "S": 0.1 * w, "R": 0.05 * w, "R+": 0.02 * w,
                  f"D+io:{BLOCKED_FUNCS[j % 3]}": 0.03 * w + 0.5 * j,
                  f"D:{BLOCKED_FUNCS[(j + 1) % 3]}": 0.01 * w,
                  "S+io:wait_on_page_bit": 0.2 * j}
        it = {"trace": f"pass{p}-iter{j:03d}.perfetto-trace", "window_ms": w,
              "run_cl0_ms": share * run_total, "run_cl1_ms": (1 - share) * run_total,
              "freq_cl0_mhz": 1800.0, "freq_cl1_mhz": 2400.0 - 100.0 * j,
              "freq_limit_cl0": 1800.0, "freq_limit_cl1": 2400.0 - 100.0 * (j % 2),
              "states": states,
              "art_verify_ms": 0.5 + 0.1 * j, "art_classload_ms": 3.0 + 0.25 * j,
              "lock_contention_ms": 0.2 * j, "binder_txn_cnt": 10 + j,
              "gc_slice_ms": 0.0 if j % 2 else 1.5,
              "inproc": {"emb-bg-1": 2.0 + j, "RenderThread": 1.0},
              "othercpu": {"system_server": 4.0 + 2.0 * j, "/system/bin/surfaceflinger": 1.5,
                           "swapper": 50.0, "com.google.android.gms": 3.0 * (j % 2),
                           "/vendor/bin/hw/android.hardware.sensors@2.0-service": 0.4},
              "mem_swap": 1_000_000 * j, "mem_available": 1_500_000_000 - 10_000_000 * j}
        if j == 3:
            # missing eff_mhz exercises the None paths in factors_report
            it["mem_swap"] = None
        else:
            it["eff_mhz"] = 1900.0 + 50.0 * j - (300.0 if (p == 2 and j == 5) else 0.0)
        data.append(it)
    return data


def build_campaigns():
    camp_a = INPUTS / "campaign-a"
    camp_b = INPUTS / "campaign-b"
    camp_a.mkdir(parents=True, exist_ok=True)
    camp_b.mkdir(parents=True, exist_ok=True)
    for p in (1, 2, 3):
        (camp_a / f"pass{p}.json").write_text(json.dumps(synth_pass(p, 6, 1.0)))
        (camp_a / f"pass{p}-factors.json").write_text(json.dumps(synth_factors_pass(p, 6)))
    # campaign-b: a slower "device" (x2.5), only pass1 and pass3 (pass2 missing exercises the
    # `continue` in cross_device_sections.load_device), and no emb-record-startup section
    for p in (1, 3):
        (camp_b / f"pass{p}.json").write_text(
            json.dumps(synth_pass(p, 6, 2.5, drop_section="emb-record-startup")))
    (camp_a / "campaign.log").write_text(
        "2026-08-30 10:00:00 pass 1/3 starting, battery 28.5C\n"
        "2026-08-30 10:05:00 pass 1 done in 300s, battery 30.1C\n"
        "2026-08-30 10:10:00 pass 2/3 starting, battery 29.0C\n"
        "2026-08-30 10:15:00 pass 2 done in 310s, battery 31.4C\n"
        "2026-08-30 10:20:00 pass 3/3 starting, battery 29.5C\n"
        "some unrelated line\n"
    )   # pass 3 has no "done" line -> temp end None -> '--'
    return camp_a, camp_b


def golden_multi_device():
    camp_a, camp_b = build_campaigns()
    scripts = SKILLS / "startup-multi-device-analysis" / "scripts"
    env = {"LITTLE_CPUS": LITTLE_CPUS_ENV}
    jobs = [
        ("hypothesis_tests", scripts / "hypothesis_tests.py", [camp_a]),
        ("factors_report", scripts / "factors_report.py", [camp_a]),
        ("cross_device_sections", scripts / "cross_device_sections.py",
         [f"synth-mid={camp_a}", f"synth-entry={camp_b}", f"synth-mid={camp_b}"]),
    ]
    for name, script, args in jobs:
        try:
            rc, so, se = run_script(script, args, env_extra=env)
            (GOLD / f"{name}.stdout.txt").write_text(so)
            if rc != 0:
                raise RuntimeError(f"{name} rc={rc}\nstderr:\n{se}")
            print(f"wrote {name}.stdout.txt")
        except Exception as exc:  # noqa: BLE001
            record_error(name, exc)


# ----------------------------------------------------------------------------------------------
# 9. x37_legs.json
# ----------------------------------------------------------------------------------------------
def golden_x37():
    legs = []
    for path in sorted(X37_DIR.glob("*-windows.json")):
        obj = json.loads(path.read_text())
        legs.append((path.name, obj))
    grouped = {}
    for name, obj in legs:
        key = f"{obj['device']}|{obj['arm']}"
        w = obj["windows_ms"]
        s = sorted(w)
        grouped.setdefault(key, {"device": obj["device"], "arm": obj["arm"], "legs": []})
        grouped[key]["legs"].append({
            "file": name, "leg": obj["leg"], "n": len(w),
            "median": statistics.median(w),
            "q90": stats.quantile(s, 0.90), "q95": stats.quantile(s, 0.95),
            "windows_ms": w,
        })
    out = {"note": ("22 per-leg files from X37 (VOID for engine conclusions - see the directory's "
                    "VOID-DO-NOT-ANALYSE.md - used here purely as fixed numeric input). Legs are "
                    "clusters, ordered by leg number within each arm. compare(): a=compat, "
                    "b=kotlin, so diff = kotlin - compat."),
           "groups": grouped, "compare": {}}
    for device in ("mid-a", "entry-a"):
        compat = grouped.get(f"{device}|compat", {}).get("legs", [])
        kotlin = grouped.get(f"{device}|kotlin", {}).get("legs", [])
        a = [l["windows_ms"] for l in sorted(compat, key=lambda l: l["leg"])]
        b = [l["windows_ms"] for l in sorted(kotlin, key=lambda l: l["leg"])]
        out["compare"][device] = {
            "a_leg_order": [l["leg"] for l in sorted(compat, key=lambda l: l["leg"])],
            "b_leg_order": [l["leg"] for l in sorted(kotlin, key=lambda l: l["leg"])],
            "result": stats.compare(a, b, "compat", "kotlin"),
        }
    dump("x37_legs.json", out)


# ----------------------------------------------------------------------------------------------
def main():
    GOLD.mkdir(parents=True, exist_ok=True)
    steps = [
        ("rng", golden_rng),
        ("stats_synthetic", golden_stats_synthetic),
        ("stats_store", golden_stats_store),
        ("derive_store", golden_derive_store),
        ("trend_store", lambda: golden_trend(STORE, "trend_store.json", "trend_store.stdout.txt")),
        ("trend_sweep_store", lambda: golden_trend(SWEEP_STORE, "trend_sweep_store.json",
                                                   "trend_sweep_store.stdout.txt")),
        ("trend_synthetic", lambda: golden_trend(build_trend_store(), "trend_synthetic.json",
                                                 "trend_synthetic.stdout.txt")),
        ("reproducibility", golden_reproducibility),
        ("matrix_plan", golden_matrix_plan),
        ("multi_device", golden_multi_device),
        ("x37_legs", golden_x37),
    ]
    failures = 0
    for name, fn in steps:
        print(f"== {name}")
        try:
            fn()
        except Exception as exc:  # noqa: BLE001
            failures += 1
            record_error(name, exc)
    print(f"done, {failures} step failure(s)")
    return 1 if failures else 0


if __name__ == "__main__":
    sys.exit(main())
