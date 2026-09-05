#!/usr/bin/env python3
"""Maxims: the bench toolchain's beliefs about SDK init, checked on every campaign and accumulated.

A campaign either confirms what we believe about SDK startup or it does not, and both outcomes should
accumulate rather than evaporate when the terminal scrolls. This module holds the beliefs as MAXIMS -
each a statement with a mechanical check that a campaign's data confirms, contradicts, or is too thin to
judge - and a LEDGER on disk that tallies every campaign (a "cell": one device under one recipe and arm)
against every maxim. Contradictions are kept with the cell that produced them, so a belief that fails on
one device or tier is visible as exactly that rather than as a lower percentage. Strong associations no
maxim covers are recorded as CANDIDATES: a factor that keeps showing up is a maxim waiting to be written.

The structure and process mirror the production analysis tool (`tool/sdk-startup` in the go repository,
which scores per-app x per-device cells from ClickHouse): the same scopes, the same verdict vocabulary,
the same ledger shape and the same generated-versus-curated split between MAXIMS.md and FINDINGS.md. Where
a belief is the same claim on both sides it carries the SAME maxim id, so the two ledgers can one day be
read as two evidence columns for one belief. Bench-only beliefs have their own ids.

Inputs are the per-pass datasets a campaign already produces: `passN.json` (variance_analysis.py --json),
`passN-factors.json` (outlier_factors.py), `passN-cohorts.json` (fleet_campaign.py's EmbVerify tap), and
`run-metadata.json` / `cell-state.json` for provenance. No trace is opened here.

Usage:
  python3 maxims.py score <campaign-dir|campaign.zip> [--reference-set REF] [--device-key KEY] [--ledger PATH|none] [--records DIR|none]
  python3 maxims.py render [--ledger PATH] [-o MAXIMS.md]
"""
import argparse
import json
import pathlib
import re
import statistics
import sys
import tempfile
import time
import zipfile

HERE = pathlib.Path(__file__).resolve().parent
# The committed records root: the ledger and its pages under maxims/, and every recorded campaign's
# datasets and log packed into campaigns/<run-id>.zip (the traces they came from are wiped by the next
# benchmark; the archive is written once and never edited, so one compressed file per campaign).
DEFAULT_RECORDS = HERE / "records"
DEFAULT_LEDGER = DEFAULT_RECORDS / "maxims" / "ledger.json"
DEFAULT_DOC = DEFAULT_RECORDS / "maxims" / "MAXIMS.md"
DATASET_NAMES = {"campaign.log", "run-metadata.json", "cell-state.json"}
DATASET_PATTERN = re.compile(r"pass[0-9]+(-[a-z-]+)?\.json\Z")

SOURCE = "bench"

# Verdicts, exactly the production tool's vocabulary.
CONFIRMED = "confirmed"
CONTRADICTED = "contradicted"
THIN = "thin"
UNDETECTED = "undetected"
NA = "n/a"

UNIVERSAL = "universal"
DIRECTIONAL = "directional"
DEVICE_SPECIFIC = "device-specific"

# The null band: lifts read as "no effect" - the same band the production tool uses.
NULL_LOW = 0.85
NULL_HIGH = 1.15

# Power floors. A quartile group under MIN_GROUP iterations, or a campaign with under MIN_SLOW slow
# iterations, cannot carry a lift; those cells score thin rather than pretending.
MIN_GROUP = 8
MIN_SLOW = 4

# "Slow" is the bench's own definition (hypothesis_tests.py): window minus the PASS median above
# max(4 ms, 10% of the pass median), so it scales across tiers and is immune to the compile-state toggle.
SLOW_FLOOR_MS = 4.0
SLOW_FRACTION = 0.10

STARVED_SHARE = 0.20          # runnable-wait share that counts as starved; the production cutoff
TOGGLE_STEP = 0.10            # pass medians must move at least this much to count as a toggle step
LOW_RAM_CLASS = "<=2GB"       # the one tier where own-process GC during init is expected
# A real collection inside the window is milliseconds long. Datasets produced before outlier_metrics.sql
# gained its GLOB / lock-contention guards carry sub-millisecond false matches (`Lgc;` class loads read as
# GC) on nearly every iteration, so presence alone would call every campaign contradicted.
GC_REAL_MS = 1.0
CANDIDATE_LIFT = 1.5
# A competing process is only a candidate factor when it is a competitor at all: at least this mean share
# of the window on CPU across the campaign. The idle task (`swapper`) is never one.
COMPETITOR_MIN_SHARE = 0.10
IDLE_TASK = "swapper"
MAX_CONTRADICTIONS = 40

PURE_CPU = ["emb-span-service-init", "emb-otel-tracer-init"]
BLOCK_RESUME = ["emb-config-service-init", "emb-payload-source-init",
                "emb-essential-service-init", "emb-post-init", "emb-delivery-init"]
CFGLOAD = "emb-persisted-config-load"
FIRST_SESSION_ATTR = "start-first-session-duration-ms"
CPU_ATTR = "init-cpu-pct"
DELAY_ATTR = "init-run-delay-pct"


# ---------------------------------------------------------------------------------------------
# Campaign loading
# ---------------------------------------------------------------------------------------------

class Iteration:
    """One launch of the campaign with everything the checks read, in run order."""

    def __init__(self, pass_no, index, record, factors):
        self.pass_no = pass_no
        self.index = index
        self.window = float(record["window_ms"])
        self.dur = record.get("dur", {})
        self.factors = factors
        states = (factors or {}).get("states", {})
        self.cpu_share = None
        self.rq_share = None
        self.gc = None
        self.competitor_share = None
        if factors is not None and self.window > 0:
            running = float(states.get("Running", 0.0))
            runnable = sum(float(v) for k, v in states.items() if k.split(":")[0] in ("R", "R+"))
            self.cpu_share = running / self.window
            self.rq_share = runnable / self.window
            self.gc = float(factors.get("gc_slice_ms") or 0.0) >= GC_REAL_MS
            others = factors.get("othercpu", {})
            self.competitor_share = sum(float(v) for k, v in others.items() if k != IDLE_TASK) / self.window
        self.slow = False  # set once the pass median is known


class Campaign:
    def __init__(self, run_dir, passes, cohorts, meta, ram_class):
        self.run_dir = run_dir
        self.passes = passes            # list of lists of Iteration
        self.iterations = [it for p in passes for it in p]
        self.cohorts = cohorts          # list of launch dicts across passes, or [] when the tap was not armed
        self.meta = meta
        self.ram_class = ram_class
        for p in passes:
            med = statistics.median(it.window for it in p)
            cut = max(SLOW_FLOOR_MS, SLOW_FRACTION * med)
            for it in p:
                it.slow = (it.window - med) > cut

    @property
    def has_factors(self):
        return all(it.factors is not None for it in self.iterations)


def load_json(path):
    return json.loads(pathlib.Path(path).read_text())


def load_provenance(run_dir):
    for name in ("cell-state.json", "run-metadata.json"):
        for path in sorted(pathlib.Path(run_dir).rglob(name)):
            return load_json(path)
    return {}


def load_campaign(run_dir, ram_class=None):
    run_dir = pathlib.Path(run_dir)
    passes, cohorts = [], []
    for i in range(1, 100):
        data_path = run_dir / f"pass{i}.json"
        if not data_path.exists():
            break
        data = load_json(data_path)
        fac_path = run_dir / f"pass{i}-factors.json"
        factors = load_json(fac_path) if fac_path.exists() else None
        if factors is not None and len(factors) != len(data):
            factors = None  # misaligned datasets are worse than none
        passes.append([Iteration(i, j, rec, factors[j] if factors is not None else None)
                       for j, rec in enumerate(data)])
        coh_path = run_dir / f"pass{i}-cohorts.json"
        if coh_path.exists():
            cohorts.extend(load_json(coh_path).get("launches", []))
    if not passes:
        sys.exit(f"no pass1.json under {run_dir} - run variance_analysis.py --json per pass first")
    meta = load_provenance(run_dir)
    if ram_class is None:
        ram_class = (meta.get("device_profile") or {}).get("ram_class")
    return Campaign(run_dir, passes, cohorts, meta, ram_class)


def resolve_cell(campaign, ref, device_key, sdk_version=None, arm=None):
    """The cell a campaign is scored as: device x SDK version x arm. The device comes from the reference
    set via the run's serial, the version and arm from provenance; each can be overridden for campaigns
    recorded before provenance was written."""
    meta = campaign.meta
    serial = meta.get("serial")
    if not device_key and ref and serial:
        device_key = next((k for k, cfg in (ref.get("devices") or {}).items()
                           if cfg.get("serial") == serial), None)
    device = device_key or "unknown"
    sdk = (sdk_version or (meta.get("checks") or {}).get("sdk matches")
           or meta.get("sdk_version") or "unknown")
    levels = ((meta.get("cell") or {}).get("levels") or {})
    arm = arm or levels.get("compile") or "default"
    ram_class = campaign.ram_class
    if ram_class is None and ref and device_key in (ref.get("devices") or {}):
        ram_class = (ref["devices"][device_key].get("profile") or {}).get("ram_class")
        campaign.ram_class = ram_class
    return {"device": device, "sdk": sdk, "arm": arm, "label": f"{device} / {sdk} / {arm}"}


# ---------------------------------------------------------------------------------------------
# Lifts
# ---------------------------------------------------------------------------------------------

def verdict(status, observed):
    return {"status": status, "observed": observed}


def quartile(iterations, key, lowest):
    """The lowest (or highest) quartile of iterations by key, ties broken by run order; None if thin."""
    valued = [it for it in iterations if key(it) is not None]
    k = len(valued) // 4
    if k < MIN_GROUP:
        return None
    ordered = sorted(valued, key=lambda it: (key(it), it.pass_no, it.index))
    return ordered[:k] if lowest else ordered[-k:]


def lift_of(group, iterations):
    """(lift, observed) for a group, or (None, reason) when the campaign cannot carry one."""
    base_slow = sum(1 for it in iterations if it.slow)
    if base_slow < MIN_SLOW:
        return None, f"only {base_slow} slow iterations of {len(iterations)}"
    g_slow = sum(1 for it in group if it.slow)
    base_rate = base_slow / len(iterations)
    lift = (g_slow / len(group)) / base_rate
    return lift, (f"{lift:.2f}x on {len(group)} iterations "
                  f"(slow {g_slow}/{len(group)} vs {base_slow}/{len(iterations)})")


def directional_status(lift, floor):
    if lift >= floor:
        return CONFIRMED
    if lift < NULL_LOW:
        return CONTRADICTED
    return UNDETECTED


def lift_check(key, lowest, floor, directional, needs_factors=True):
    def check(c):
        if needs_factors and not c.has_factors:
            return verdict(NA, "no passN-factors.json")
        group = quartile(c.iterations, key, lowest)
        if group is None:
            return verdict(THIN, f"quartile under {MIN_GROUP} iterations")
        lift, obs = lift_of(group, c.iterations)
        if lift is None:
            return verdict(THIN, obs)
        if directional:
            return verdict(directional_status(lift, floor), obs)
        return verdict(CONFIRMED if lift >= floor else CONTRADICTED, obs)
    return check


# ---------------------------------------------------------------------------------------------
# The maxims
# ---------------------------------------------------------------------------------------------

def check_starved(c):
    if not c.has_factors:
        return verdict(NA, "no passN-factors.json")
    starved = [it for it in c.iterations if it.rq_share is not None and it.rq_share >= STARVED_SHARE]
    slow = [it for it in c.iterations if it.slow]
    if len(starved) < MIN_SLOW or len(slow) < MIN_SLOW:
        return verdict(THIN, f"{len(starved)} starved and {len(slow)} slow iterations of {len(c.iterations)}")
    share_all = len(starved) / len(c.iterations)
    share_slow = sum(1 for it in slow if it in starved) / len(slow)
    enrichment = share_slow / share_all
    obs = f"{enrichment:.2f}x enrichment, {len(starved)} starved iterations"
    return verdict(CONFIRMED if enrichment >= 3.0 else CONTRADICTED, obs)


def check_gc_rare(c):
    if c.ram_class == LOW_RAM_CLASS:
        return verdict(NA, f"out of scope on a {LOW_RAM_CLASS} device")
    if not c.has_factors:
        return verdict(NA, "no passN-factors.json")
    n = len(c.iterations)
    with_gc = sum(1 for it in c.iterations if it.gc)
    share = with_gc / n
    return verdict(CONFIRMED if share <= 0.02 else CONTRADICTED,
                   f"{100.0 * share:.1f}% of {n} iterations ran own GC")


def check_first_launch(c):
    ratios = []
    for p in c.passes:
        if len(p) < 3:
            continue
        rest = statistics.median(it.window for it in p[1:])
        if rest > 0:
            ratios.append(p[0].window / rest)
    if len(ratios) < 2:
        return verdict(THIN, f"{len(ratios)} passes with a first launch and a rest")
    ratio = statistics.median(ratios)
    return verdict(directional_status(ratio, NULL_HIGH), f"iter000/rest {ratio:.2f}x over {len(ratios)} passes")


def check_config_fast_path(c):
    """Tier-relative on purpose: the fresh path is a fraction of the cached path on every tier, but the
    absolute numbers span an order of magnitude between flagship and entry, so a fixed cutoff would call
    a flagship's cached path fresh and an entry device's fresh path cached."""
    bad, seen = [], 0
    for p in c.passes:
        first = p[0].dur.get(CFGLOAD)
        rest = [it.dur[CFGLOAD] for it in p[1:] if CFGLOAD in it.dur]
        if first is None or not rest:
            continue
        seen += 1
        rmed = statistics.median(rest)
        if first >= rmed:
            bad.append(f"pass {p[0].pass_no}: iter000 {first:.1f} ms vs rest p50 {rmed:.1f} ms")
    if not seen:
        return verdict(NA, f"{CFGLOAD} not in the datasets")
    if bad:
        return verdict(CONTRADICTED, f"{len(bad)} of {seen} passes; " + bad[0])
    return verdict(CONFIRMED, f"every pass's iter000 below the rest's median over {seen} passes")


def med_section(p, name):
    vals = [it.dur[name] for it in p if name in it.dur]
    return statistics.median(vals) if vals else None


def check_toggle(c):
    meds = [statistics.median(it.window for it in p) for p in c.passes]
    if len(meds) < 3:
        return verdict(THIN, f"{len(meds)} passes; the toggle needs at least 3")
    steps = [meds[i + 1] / meds[i] - 1.0 for i in range(len(meds) - 1)]
    alternating = all(abs(s) >= TOGGLE_STEP for s in steps) and all(
        (steps[i] > 0) != (steps[i + 1] > 0) for i in range(len(steps) - 1))
    fast = min(range(len(meds)), key=lambda i: meds[i])
    slow = max(range(len(meds)), key=lambda i: meds[i])

    def ratio(names):
        rs = []
        for name in names:
            f_, s_ = med_section(c.passes[fast], name), med_section(c.passes[slow], name)
            if f_ and s_ is not None and f_ > 0:
                rs.append(s_ / f_)
        return statistics.median(rs) if rs else None

    pure, block = ratio(PURE_CPU), ratio(BLOCK_RESUME)
    seq = " -> ".join(f"{m:.1f}" for m in meds)
    if pure is None or block is None:
        return verdict(NA, f"pass medians {seq}; section medians missing")
    obs = f"pass medians {seq}; pure-CPU {pure:.2f}x, block-resume {block:.2f}x"
    fingerprint = pure <= 1.25 and block >= 1.5
    return verdict(CONFIRMED if alternating and fingerprint else CONTRADICTED, obs)


def attr_float(launch, key):
    raw = launch.get(key)
    if raw is None:
        return None
    try:
        return float(raw)
    except (TypeError, ValueError):
        return None


def check_restore_vs_create(c):
    if not c.cohorts:
        return verdict(NA, "no passN-cohorts.json (EmbVerify tap not armed)")
    created = [attr_float(l, FIRST_SESSION_ATTR) for l in c.cohorts if l.get("cohort") == "created"]
    restored = [attr_float(l, FIRST_SESSION_ATTR) for l in c.cohorts if l.get("cohort") == "restored"]
    created = [v for v in created if v is not None]
    restored = [v for v in restored if v is not None]
    if not created and not restored:
        return verdict(NA, f"{FIRST_SESSION_ATTR} absent from the tap")
    if len(created) < 5 or len(restored) < 5:
        return verdict(THIN, f"{len(created)} created and {len(restored)} restored launches with the attribute")
    c_med, r_med = statistics.median(created), statistics.median(restored)
    obs = f"created p50 {c_med:.2f} ms (n={len(created)}) vs restored p50 {r_med:.2f} ms (n={len(restored)})"
    ok = c_med >= 2.0 * r_med and (c_med - r_med) >= 0.5
    return verdict(CONFIRMED if ok else CONTRADICTED, obs)


def check_cpu_closure(c):
    if not c.cohorts:
        return verdict(NA, "no passN-cohorts.json (EmbVerify tap not armed)")
    pairs = [(attr_float(l, CPU_ATTR), attr_float(l, DELAY_ATTR)) for l in c.cohorts]
    pairs = [(a, b) for a, b in pairs if a is not None and b is not None]
    if not pairs:
        return verdict(NA, f"{CPU_ATTR} / {DELAY_ATTR} absent from the tap")
    if len(pairs) < MIN_GROUP:
        return verdict(THIN, f"{len(pairs)} launches carry both attributes")
    ok = sum(1 for a, b in pairs if 0.0 <= 100.0 - a - b <= 100.0)
    obs = f"{ok}/{len(pairs)} launches close to 100%"
    return verdict(CONFIRMED if ok / len(pairs) >= 0.98 else CONTRADICTED, obs)


MAXIMS = [
    {"id": "off-cpu", "scope": UNIVERSAL,
     "statement": "A slow init is one whose main thread was off-CPU: the lowest quartile of on-CPU share is at least 1.5x as likely to be slow.",
     "why": "Init is main-thread work; when the thread is not running, something else is holding it. Same claim as the production maxim, read from the trace's thread states instead of init-cpu-pct.",
     "check": lift_check(lambda it: it.cpu_share, lowest=True, floor=1.5, directional=False)},
    {"id": "scheduler-wait", "scope": DIRECTIONAL,
     "statement": "Any scheduler wait makes a slow init likelier, by 2x where the effect is measurable: the highest quartile of runnable-wait share.",
     "why": "A runnable thread that is not scheduled loses wall time the SDK cannot recover; how often that happens is the device's. The bench proved the concurrent-CPU mechanism causal by inducing churn.",
     "check": lift_check(lambda it: it.rq_share, lowest=False, floor=2.0, directional=True)},
    {"id": "concurrent-cpu", "scope": DIRECTIONAL,
     "statement": "Concurrent CPU from other processes makes a slow init likelier, by 1.5x or more where measurable: the highest quartile of other-process on-CPU share of the window, the idle task excluded.",
     "why": "The bench's extreme-outlier class: system_server GC compactions, dexopt and app-ecosystem churn inflate the window through the memory bus while the main thread's own wait stays small. Proven causal by inducing churn; how much of it a device sees is the device's.",
     "check": lift_check(lambda it: it.competitor_share, lowest=False, floor=1.5, directional=True)},
    {"id": "starved-enriched", "scope": UNIVERSAL,
     "statement": "Starved inits (runnable-wait share of 20% or more) are at least 3x enriched among slow inits.",
     "why": "Rare on a quiet bench, and decisive when it happens; the production tool uses the same 20% cutoff.",
     "check": check_starved},
    {"id": "gc-rare", "scope": UNIVERSAL,
     "statement": "The SDK's own GC almost never runs during init: a collection of 1 ms or more inside the window on 2% or fewer of iterations on any device above 2 GB of RAM.",
     "why": "Own-process collection during init was only ever seen on the 1 GB tier; above it a zero is the device, not the detector. Production sees 0.02 to 0.3%. The 1 ms floor keeps sub-millisecond class-load slices misread as GC in older datasets from counting.",
     "check": check_gc_rare},
    {"id": "first-launch", "scope": DIRECTIONAL,
     "statement": "The first launch after an install is slower than the rest of its pass, by 1.15x or more where measurable.",
     "why": "Install aftermath: dexopt and system bursts. The bench also found the fresh-install config fast path can cancel it on fast tiers, which is why this is directional and why a contradiction here is expected on flagships.",
     "check": check_first_launch},
    {"id": "config-fast-path", "scope": UNIVERSAL,
     "statement": "Every pass's first launch takes the fresh-install config fast path: its persisted-config-load is below the median of the rest of the pass.",
     "why": "iter000 has no cached config to decode. A violation means app data survived a failed uninstall and the pass's first-launch sample is poisoned (hypothesis H3); the check is tier-relative because the two bands scale with the device.",
     "check": check_config_fast_path},
    {"id": "compile-state-toggle", "scope": DEVICE_SPECIFIC,
     "statement": "Pass medians alternate between two levels at least 10% apart, with pure-CPU sections within 1.25x and block-and-resume sections 1.5x or more between the two states.",
     "why": "Install-time compile state alternating per reinstall on some OEM builds (hypothesis H2). Which devices show it is the finding; compare only matching-state passes where they do.",
     "check": check_toggle},
    {"id": "restore-vs-create", "scope": UNIVERSAL,
     "statement": "A launch that creates a user session spends at least 2x as long in start-first-session as one that restores the persisted session.",
     "why": "The create path serializes and persists session metadata on the main thread; the restore path is a read. The bench measured only the restore path until the cohort tap existed.",
     "check": check_restore_vs_create},
    {"id": "cpu-closure", "scope": UNIVERSAL,
     "statement": "The init span's CPU attributes close: init-cpu-pct plus init-run-delay-pct is between 0 and 100 on at least 98% of launches.",
     "why": "The one attribute check that needs no ground truth; a violation means the attributes and the trace disagree and the run is not trustworthy.",
     "check": check_cpu_closure},
]


# ---------------------------------------------------------------------------------------------
# Candidates: strong associations no maxim covers
# ---------------------------------------------------------------------------------------------

def candidate_factors(c):
    """name -> (key function, lowest?) for every per-iteration factor present on all iterations.

    Every duration or count is divided by the iteration's window first. A slow iteration is a long window,
    and anything that accumulates over the window - another process's CPU, sleep time, binder calls - grows
    with it, so the raw value would correlate with slowness by construction (interpreting-results.md,
    "counts that grow with duration"). Shares and rates can be read against slowness; totals cannot.
    """
    if not c.has_factors:
        return {}
    its = c.iterations

    def per_window(value, it):
        return None if value is None else float(value) / it.window

    def scalar_share(name):
        return lambda it: per_window(it.factors.get(name), it)

    def scalar(name):
        return lambda it: (None if it.factors.get(name) is None else float(it.factors[name]))

    def state_share(pred):
        return lambda it: sum(float(v) for k, v in it.factors.get("states", {}).items() if pred(k)) / it.window

    def competitor_share(name):
        return lambda it: float(it.factors.get("othercpu", {}).get(name, 0.0)) / it.window

    factors = {
        "D+io share": (state_share(lambda k: k.split(":")[0].startswith("D") and "+io" in k.split(":")[0]), False),
        "S share": (state_share(lambda k: k.split(":")[0].startswith("S")), False),
        "lock_contention share": (scalar_share("lock_contention_ms"), False),
        "art_classload share": (scalar_share("art_classload_ms"), False),
        "art_verify share": (scalar_share("art_verify_ms"), False),
        "binder_txn per ms": (scalar_share("binder_txn_cnt"), False),
        "eff_mhz": (scalar("eff_mhz"), True),
        "mem_available": (scalar("mem_available"), True),
    }
    means = {}
    for it in its:
        for name, v in it.factors.get("othercpu", {}).items():
            if name != IDLE_TASK:
                means[name] = means.get(name, 0.0) + float(v) / it.window / len(its)
    for name, mean in means.items():
        if mean >= COMPETITOR_MIN_SHARE:
            factors[f"othercpu:{name} share"] = (competitor_share(name), False)
    present = {}
    for name, (key, lowest) in factors.items():
        if all(key(it) is not None for it in its):
            present[name] = (key, lowest)
    return present


def candidates_of(c):
    out = []
    for name in sorted(candidate_factors(c)):
        key, lowest = candidate_factors(c)[name]
        group = quartile(c.iterations, key, lowest)
        if group is None:
            continue
        lift, _ = lift_of(group, c.iterations)
        if lift is not None and lift >= CANDIDATE_LIFT:
            out.append((name, lift))
    return out


# ---------------------------------------------------------------------------------------------
# Ledger
# ---------------------------------------------------------------------------------------------

def empty_ledger():
    return {"source": SOURCE, "runs": 0, "updated_at": "", "maxims": {}, "candidates": {}}


def load_ledger(path):
    path = pathlib.Path(path)
    if not path.exists():
        return empty_ledger()
    led = json.loads(path.read_text())
    led.setdefault("source", SOURCE)
    led.setdefault("maxims", {})
    led.setdefault("candidates", {})
    return led


def save_ledger(led, path):
    path = pathlib.Path(path)
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(led, indent=2, sort_keys=True) + "\n")


def score(campaign):
    return [(m, m["check"](campaign)) for m in MAXIMS]


def record(led, run_id, cell, verdicts, cands, now):
    led["runs"] = led.get("runs", 0) + 1
    led["updated_at"] = now
    for m, v in verdicts:
        t = led["maxims"].setdefault(m["id"], {
            "statement": m["statement"], "scope": m["scope"],
            "confirmed": 0, "contradicted": 0, "thin": 0, "undetected": 0,
            "devices": {}, "contradictions": []})
        t["statement"], t["scope"] = m["statement"], m["scope"]
        status = v["status"]
        if status in (CONFIRMED, CONTRADICTED, THIN, UNDETECTED):
            # An n/a verdict (input absent) leaves no trace: the device has not tested the maxim.
            d = t["devices"].setdefault(cell["device"], {"confirmed": 0, "contradicted": 0, "thin": 0, "undetected": 0})
            t[status] += 1
            d[status] += 1
        if status == CONTRADICTED:
            t["contradictions"].append({"run": run_id, "device": cell["device"], "cell": cell["label"],
                                        "observed": v["observed"]})
            del t["contradictions"][:-MAX_CONTRADICTIONS]
    for name, lift in cands:
        cnd = led["candidates"].setdefault(name, {"label": name, "cells": 0, "max_lift": 0.0, "last_seen": ""})
        cnd["cells"] += 1
        cnd["max_lift"] = max(cnd["max_lift"], round(lift, 4))
        cnd["last_seen"] = run_id


def stance(d):
    if d["confirmed"] == 0 and d["contradicted"] == 0 and d.get("undetected", 0) > 0:
        return "undetected"
    if d["confirmed"] == 0 and d["contradicted"] == 0:
        return "untested"
    if d["confirmed"] >= 2 * d["contradicted"]:
        return "holds"
    if d["contradicted"] >= 2 * d["confirmed"]:
        return "fails"
    return "mixed"


def status_of(maxim, tally):
    if not tally or not tally.get("devices"):
        return "untested"
    holds = fails = mixed = tested = 0
    for d in tally["devices"].values():
        s = stance(d)
        if s == "holds":
            holds += 1
        elif s == "fails":
            fails += 1
        elif s == "mixed":
            mixed += 1
        if s != "untested":
            tested += 1
    if tested == 0:
        return "untested"
    if maxim["scope"] == DEVICE_SPECIFIC:
        return "accepted (device-specific)"
    if fails > 0 and fails >= holds:
        return "refuted"
    if fails > 0 or mixed > 0:
        return "under review"
    return "accepted"


def devices_line(tally):
    if not tally or not tally.get("devices"):
        return "no device has tested it"
    counts = {}
    for d in tally["devices"].values():
        s = stance(d)
        counts[s] = counts.get(s, 0) + 1
    tested = len(tally["devices"]) - counts.get("untested", 0)
    if tested == 0:
        return "no device has tested it"
    out = f"holds on {counts.get('holds', 0)} of {tested} devices tested"
    rest = [f"{counts[k]} {k}" for k in ("fails", "mixed", "undetected") if counts.get(k)]
    if rest:
        out += " (" + ", ".join(rest) + ")"
    return out


# ---------------------------------------------------------------------------------------------
# MAXIMS.md
# ---------------------------------------------------------------------------------------------

def render(led, now):
    b = []
    b.append("# SDK startup maxims (bench)\n")
    b.append("What we currently believe about Android SDK init from the benchmark fleet, and the evidence for it. "
             f"Generated by `maxims render` at {now} from the maxim definitions in `_shared/maxims.py` "
             f"(mirrored in `startup-tools`) and a ledger of {led.get('runs', 0)} runs"
             + (f" (last updated {led['updated_at']})" if led.get("updated_at") else "")
             + ". Do not edit by hand: change a maxim in code or score another campaign, then regenerate. "
             "Conclusions that are not per-campaign checks are in [FINDINGS.md](FINDINGS.md). The production "
             "counterpart with the same structure is `tool/sdk-startup/MAXIMS.md` in the go repository; a maxim "
             "id that appears in both is the same claim tested on the fleet and on the bench.\n")
    b.append("**Scopes.** *Universal*: a claim about the SDK that should hold at the stated size on every device; "
             "one device contradicting it counts against it. *Directional*: the direction holds everywhere but the "
             "size is the device's, so a lift that points the right way but misses the floor is *undetected*, not "
             "a contradiction. *Device-specific*: the sign itself is the device's, and the split by device is the "
             "finding.\n")
    b.append("**Status.** *accepted*: every device that tested it holds it. *under review*: a device fails or is "
             "mixed but most hold it. *refuted*: as many devices fail it as hold it. *untested*: no verdict yet. A "
             "device *holds* a maxim when its confirmations outnumber contradictions two to one across every run, "
             "*fails* when the reverse. A cell is one campaign: one device under one recipe and arm.\n")
    b.append("| maxim | scope | status | devices |")
    b.append("|---|---|---|---|")
    for m in MAXIMS:
        t = led["maxims"].get(m["id"])
        b.append(f"| {m['id']} | {m['scope']} | {status_of(m, t)} | {devices_line(t)} |")
    b.append("")
    for m in MAXIMS:
        t = led["maxims"].get(m["id"])
        b.append(f"## {m['id']}\n")
        b.append(f"**{m['statement']}**\n")
        b.append(f"- Scope: {m['scope']}. Status: **{status_of(m, t)}**.")
        b.append(f"- Why: {m['why']}")
        if not t:
            b.append("- Evidence: none yet.\n")
            continue
        ev = f"{t['confirmed']} confirmed, {t['contradicted']} contradicted, {t['thin']} thin"
        if t.get("undetected"):
            ev += f", {t['undetected']} undetected"
        b.append(f"- Evidence: {ev} cells over {led.get('runs', 0)} runs; {devices_line(t)}.")
        contras = t.get("contradictions", [])
        if contras:
            b.append("- Latest contradictions (cell, observed):")
            for x in reversed(contras[-5:]):
                b.append(f"  - {x['cell']}: {x['observed']}")
        b.append("")
    current = {m["id"] for m in MAXIMS}
    retired = sorted(k for k in led["maxims"] if k not in current)
    if retired:
        b.append("## Retired maxims\n")
        b.append("Ledger records for maxims that are no longer defined in code - renamed, rewritten as a "
                 "different claim, or dropped. FINDINGS.md says why.\n")
        b.append("| former id | statement as recorded | confirmed | contradicted | thin |")
        b.append("|---|---|---|---|---|")
        for k in retired:
            t = led["maxims"][k]
            b.append(f"| {k} | {t['statement']} | {t['confirmed']} | {t['contradicted']} | {t['thin']} |")
        b.append("")
    cands = sorted(led["candidates"].values(), key=lambda c: (-c["cells"], c["label"]))
    if cands:
        b.append("## Candidates\n")
        b.append(f"Per-iteration factors whose top (or bottom) quartile was at least {CANDIDATE_LIFT}x as likely to be "
                 "slow in some campaign and that no maxim covers. One that keeps appearing is a maxim waiting to be "
                 "written - or a small band waiting to be disproved.\n")
        b.append("| factor | cells | max lift | last seen |")
        b.append("|---|---|---|---|")
        for c in cands:
            b.append(f"| {c['label']} | {c['cells']} | {c['max_lift']:.2f}x | {c['last_seen']} |")
        b.append("")
    return "\n".join(b) + "\n"


# ---------------------------------------------------------------------------------------------
# CLI
# ---------------------------------------------------------------------------------------------

def now_utc(explicit=None):
    return explicit or time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime())


def cmd_score(args):
    ref = load_json(args.reference_set) if args.reference_set else None
    origin = pathlib.Path(args.campaign_dir)
    datasets_dir = materialize(origin)
    campaign = load_campaign(datasets_dir)
    cell = resolve_cell(campaign, ref, args.device_key, args.sdk_version, args.arm)
    run_id = args.run_id or campaign_name(origin)
    verdicts = score(campaign)
    cands = candidates_of(campaign)
    print(f"maxims: {cell['label']} (run {run_id}, {len(campaign.iterations)} iterations in {len(campaign.passes)} passes)")
    for m, v in verdicts:
        print(f"  {v['status']:<12} {m['id']:<22} {v['observed']}")
    if cands:
        print("  candidates: " + ", ".join(f"{name} {lift:.2f}x" for name, lift in cands))
    if args.ledger == "none":
        print("ledger: not recorded (--ledger none)")
        return
    led = load_ledger(args.ledger)
    record(led, run_id, cell, verdicts, cands, now_utc(args.now))
    save_ledger(led, args.ledger)
    print(f"ledger: {args.ledger} now {led['runs']} runs")
    if args.records != "none":
        sys.stdout.write(keep_datasets(origin, datasets_dir, pathlib.Path(args.records), run_id))


def materialize(path):
    """A campaign is a directory, or a `.zip` of one (the records root's form): unpack the archive into a
    temporary directory and return it, else return the directory itself."""
    path = pathlib.Path(path)
    if path.is_file() and path.suffix == ".zip":
        out = pathlib.Path(tempfile.mkdtemp(prefix="maxims-campaign-"))
        with zipfile.ZipFile(path) as z:
            for name in z.namelist():
                if name.startswith("/") or ".." in pathlib.PurePosixPath(name).parts:
                    raise SystemExit(f"refusing entry {name!r} in {path}")
            z.extractall(out)
        return out
    return path


def campaign_name(path):
    """`pathlib.Path(dir).resolve().name`, minus `.zip` for an archive."""
    name = pathlib.Path(path).resolve().name
    return name[:-4] if name.endswith(".zip") else name


def keep_datasets(origin, datasets_dir, records_root, run_id):
    """Pack the campaign's passN*.json, campaign.log and provenance files into <records>/campaigns/<run_id>.zip
    (flat, deflated, replacing an earlier archive); a campaign that already lives under the records root is
    left where it is."""
    src = pathlib.Path(origin).resolve()
    root = pathlib.Path(records_root).resolve()
    if src == root or root in src.parents:
        return f"records: campaign already under {records_root}\n"
    dest = pathlib.Path(records_root) / "campaigns" / f"{run_id}.zip"
    files = sorted(p for p in pathlib.Path(datasets_dir).iterdir()
                   if p.is_file() and (p.name in DATASET_NAMES or DATASET_PATTERN.match(p.name)))
    dest.parent.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(dest, "w", zipfile.ZIP_DEFLATED) as z:
        for p in files:
            z.write(p, arcname=p.name)
    return f"records: {len(files)} files kept in {dest}\n"


def cmd_render(args):
    led = load_ledger(args.ledger) if args.ledger != "none" else empty_ledger()
    text = render(led, now_utc(args.now))
    if args.output == "-":
        sys.stdout.write(text)
    else:
        pathlib.Path(args.output).parent.mkdir(parents=True, exist_ok=True)
        pathlib.Path(args.output).write_text(text)
        print(f"wrote {args.output}")


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    sub = ap.add_subparsers(dest="cmd", required=True)
    s = sub.add_parser("score", help="score one campaign directory against every maxim and record the verdicts")
    s.add_argument("campaign_dir")
    s.add_argument("--reference-set", help="reference-set.json, to map the run's serial to a device_key")
    s.add_argument("--device-key", help="override when provenance cannot identify the device")
    s.add_argument("--sdk-version", help="override when provenance does not record the SDK version")
    s.add_argument("--arm", help="override the arm label (default: the provenance's compile level, else 'default')")
    s.add_argument("--ledger", default=str(DEFAULT_LEDGER), help="ledger JSON to update; 'none' for a rerun that must not count twice")
    s.add_argument("--run-id", help="defaults to the campaign directory name")
    s.add_argument("--now", help="timestamp to stamp (tests)")
    s.add_argument("--records", default=str(DEFAULT_RECORDS),
                   help="records root whose campaigns/<run-id>.zip keeps the run's datasets and log once it is recorded; 'none' to keep nothing")
    s.set_defaults(func=cmd_score)
    r = sub.add_parser("render", help="render MAXIMS.md from the maxim definitions and the ledger")
    r.add_argument("--ledger", default=str(DEFAULT_LEDGER))
    r.add_argument("-o", "--output", default=str(DEFAULT_DOC))
    r.add_argument("--now", help="timestamp to stamp (tests)")
    r.set_defaults(func=cmd_render)
    args = ap.parse_args()
    args.func(args)


if __name__ == "__main__":
    main()
