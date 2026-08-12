#!/usr/bin/env python3
"""Correlate external/on-device factors with SDK-init window slowness, outlier-first.

Consumes passN-factors.json files produced by outlier_factors.py + outlier_metrics.sql
(per-iteration: effective CPU MHz, cluster clocks/limits, thread-state with D-state
blocked_function, ART verify/class-load, lock contention, GC, in-process threads,
other-process CPU, binder counts, swap). Prints:

  1. pass-level factor means (what separates slow passes from fast passes)
  2. pooled per-iteration correlations vs window delta (within-pass, n=all)
  3. the outlier catalogue: every iteration >+4 ms vs pass median, with its factor vector
  4. extreme-outlier detail (top slowest overall): top blocked functions + top competitors
  5. D-state blocked_function totals (what main-thread IO waits actually are)

Usage: python3 factors_report.py <dir-with-passN-factors.json>
"""

import json
import os
import statistics
import sys

SLOW_DELTA_MS = 4.0
LITTLE_CPUS = {int(x) for x in os.environ.get("LITTLE_CPUS", "0,1,2,3").split(",")}


def main():
    d = sys.argv[1]
    passes = []
    for i in range(1, 20):
        p = os.path.join(d, f"pass{i}-factors.json")
        if not os.path.exists(p):
            break
        with open(p) as f:
            passes.append(json.load(f))
    if not passes:
        print("no passN-factors.json found", file=sys.stderr)
        return 1

    rows = []
    for pi, data in enumerate(passes):
        med = statistics.median([it["window_ms"] for it in data])
        for j, it in enumerate(data):
            rows.append(derive(it, pi + 1, j, med))

    n = len(rows)
    print(f"external-factor analysis: {len(passes)} passes, {n} iterations")
    print()

    # 1. pass-level means
    print("1. pass-level factor means (rates are CPU-ms per window-ms)")
    hdr = (f"  {'pass':>4}{'win p50':>9}{'run p50':>9}{'eff_mhz':>9}{'lim_cl1':>9}"
           f"{'sysserv rate':>13}{'sf rate':>9}{'other rate':>11}{'artload':>9}"
           f"{'d_io':>7}{'swap MB':>9}")
    print(hdr)
    for pi in range(1, len(passes) + 1):
        pr = [r for r in rows if r["pass"] == pi]
        print(f"  {pi:>4}"
              f"{statistics.median([r['window'] for r in pr]):>9.1f}"
              f"{statistics.median([r['run'] for r in pr]):>9.1f}"
              f"{mean0([r['eff_mhz'] for r in pr if r['eff_mhz']]):>9.0f}"
              f"{mean0([r['lim_cl1'] for r in pr if r['lim_cl1']]):>9.0f}"
              f"{statistics.mean([r['sysserver_rate'] for r in pr]):>13.3f}"
              f"{statistics.mean([r['sf_rate'] for r in pr]):>9.3f}"
              f"{statistics.mean([r['other_rate'] for r in pr]):>11.3f}"
              f"{statistics.mean([r['artload'] for r in pr]):>9.2f}"
              f"{statistics.mean([r['d_io'] for r in pr]):>7.2f}"
              f"{statistics.mean([r['swap'] for r in pr]) / 1e6:>9.1f}")
    print()

    # 2. pooled correlations vs delta
    print("2. pooled correlations with window delta (vs own pass median), n=%d" % n)
    deltas = [r["delta"] for r in rows]
    factors = [
        ("eff_mhz (weighted CPU clock)", "eff_mhz"),
        ("little share", "cl0_share"),
        ("system_server CPU rate", "sysserver_rate"),
        ("surfaceflinger CPU rate", "sf_rate"),
        ("other-proc CPU rate (excl idle)", "other_rate"),
        ("in-proc bg-thread CPU rate", "inproc_rate"),
        ("main-thread D/io ms", "d_io"),
        ("main-thread runnable ms", "rq"),
        ("ART class-load ms (in window)", "artload"),
        ("ART verify ms (in window)", "artverify"),
        ("lock contention ms", "lock"),
        ("GC slice ms (in proc)", "gc"),
        ("binder txns (main thread)", "binder"),
        ("process swap bytes", "swap"),
        ("MemAvailable", "memavail"),
    ]
    for label, key in factors:
        vals = [r[key] if r[key] is not None else 0 for r in rows]
        print(f"  {label:<36} r = {pearson(deltas, vals):>6.2f}")
    runs = [r["run"] for r in rows]
    print(f"  {'(sanity) run ms':<36} r = {pearson(deltas, runs):>6.2f}")
    print()

    # 3. outlier catalogue
    slows = sorted([r for r in rows if r["delta"] > SLOW_DELTA_MS],
                   key=lambda r: -r["delta"])
    print(f"3. outlier catalogue — {len(slows)} iterations with delta > +{SLOW_DELTA_MS:.0f} ms")
    print(f"  {'iter':>13}{'win':>7}{'Δ':>6}{'run':>7}{'MHz':>6}{'lit%':>6}"
          f"{'ss rate':>8}{'sf rate':>8}{'oth':>6}{'d_io':>6}{'rq':>5}"
          f"{'artload':>8}{'verify':>7}{'lock':>6}{'gc':>5}")
    for r in slows:
        print(f"  pass{r['pass']}:it{r['iter']:03d}"
              f"{r['window']:>7.1f}{r['delta']:>+6.1f}{r['run']:>7.1f}"
              f"{r['eff_mhz'] or 0:>6.0f}{r['cl0_share'] * 100:>6.0f}"
              f"{r['sysserver_rate']:>8.2f}{r['sf_rate']:>8.2f}{r['other_rate']:>6.2f}"
              f"{r['d_io']:>6.2f}{r['rq']:>5.1f}{r['artload']:>8.2f}"
              f"{r['artverify']:>7.2f}{r['lock']:>6.2f}{r['gc']:>5.2f}")
    med_fast = [r for r in rows if abs(r["delta"]) < 2]
    print(f"  --- baseline (|Δ|<2 ms, n={len(med_fast)}): "
          f"run {statistics.median([r['run'] for r in med_fast]):.1f}, "
          f"ss rate {statistics.mean([r['sysserver_rate'] for r in med_fast]):.2f}, "
          f"sf rate {statistics.mean([r['sf_rate'] for r in med_fast]):.2f}, "
          f"oth {statistics.mean([r['other_rate'] for r in med_fast]):.2f}, "
          f"d_io {statistics.median([r['d_io'] for r in med_fast]):.2f}, "
          f"artload {statistics.median([r['artload'] for r in med_fast]):.2f}, "
          f"verify {statistics.median([r['artverify'] for r in med_fast]):.2f}")
    print()

    # 4. extreme-outlier detail
    print("4. extreme outliers — top competitors and blocked functions")
    for r in sorted(rows, key=lambda r: -r["window"])[:8]:
        top_oth = sorted(r["othercpu"].items(), key=lambda kv: -kv[1])[:4]
        top_blk = sorted(r["blocked"].items(), key=lambda kv: -kv[1])[:3]
        oth = ", ".join(f"{k.split('/')[-1]} {v:.1f}" for k, v in top_oth)
        blk = ", ".join(f"{k or '?'} {v:.2f}" for k, v in top_blk) or "none"
        print(f"  pass{r['pass']}:it{r['iter']:03d} win {r['window']:.1f} "
              f"(Δ{r['delta']:+.1f})  competitors[ms]: {oth}")
        print(f"       blocked_on: {blk}")
    print()

    # 5. blocked-function totals
    print("5. main-thread D-state blocked_function totals across all iterations, ms")
    agg = {}
    for r in rows:
        for k, v in r["blocked"].items():
            agg[k or "?"] = agg.get(k or "?", 0) + v
    for k, v in sorted(agg.items(), key=lambda kv: -kv[1])[:15]:
        print(f"  {k:<44}{v:>9.1f}")
    return 0


def mean0(vals):
    if not vals:
        return 0.0
    return statistics.mean(vals)


def cl0_share(it):
    """Little-core share of window run time. outlier_metrics.sql partitions
    run_cl0_ms/run_cl1_ms at a fixed cpu<4 boundary (it does not know device topology);
    remap that fixed partition against LITTLE_CPUS (see device_probe.py) so the share
    still means "little cluster" on devices where the little cluster is cpu4-7."""
    r_cl0 = it.get("run_cl0_ms", 0)
    r_cl1 = it.get("run_cl1_ms", 0)
    tot = r_cl0 + r_cl1
    if not tot:
        return 0
    if all(c >= 4 for c in LITTLE_CPUS):
        return r_cl1 / tot
    return r_cl0 / tot


def derive(it, pnum, j, pass_med):
    st = it.get("states", {})
    run = st.get("Running", 0)
    d_io = sum(v for k, v in st.items()
               if k.startswith("D") or k.startswith("S+io"))
    rq = sum(v for k, v in st.items() if k.split(":")[0] in ("R", "R+"))
    blocked = {}
    for k, v in st.items():
        if k.startswith("D") and ":" in k:
            blocked[k.split(":", 1)[1]] = blocked.get(k.split(":", 1)[1], 0) + v
    win = it["window_ms"]
    oth = it.get("othercpu", {})
    other_total = sum(v for k, v in oth.items() if k != "swapper")
    ss = oth.get("system_server", 0)
    sf = oth.get("/system/bin/surfaceflinger", 0)
    inproc = sum(it.get("inproc", {}).values())
    return {
        "pass": pnum, "iter": j, "window": win, "delta": win - pass_med,
        "run": run, "d_io": d_io, "rq": rq, "blocked": blocked,
        "eff_mhz": it.get("eff_mhz"), "lim_cl1": it.get("freq_limit_cl1"),
        "cl0_share": cl0_share(it),
        "sysserver_rate": ss / win, "sf_rate": sf / win,
        "other_rate": other_total / win, "inproc_rate": inproc / win,
        "artload": it.get("art_classload_ms", 0),
        "artverify": it.get("art_verify_ms", 0),
        "lock": it.get("lock_contention_ms", 0),
        "gc": it.get("gc_slice_ms", 0),
        "binder": it.get("binder_txn_cnt", 0),
        "swap": it.get("mem_swap", 0) or 0,
        "memavail": it.get("mem_available", 0) or 0,
        "othercpu": oth,
    }


def pearson(xs, ys):
    mx, my = statistics.mean(xs), statistics.mean(ys)
    num = sum((x - mx) * (y - my) for x, y in zip(xs, ys))
    den = (sum((x - mx) ** 2 for x in xs) * sum((y - my) ** 2 for y in ys)) ** 0.5
    if den == 0:
        return float("nan")
    return num / den


if __name__ == "__main__":
    sys.exit(main())
