#!/usr/bin/env python3
"""Cross-pass hypothesis tests for one device's campaign.

Consumes the per-iteration JSON dumps produced by variance_analysis.py --json for each
campaign pass, plus the campaign log (battery temps), and prints the evidence for/against:

  H1  within-pass outliers = little-core placement (needs the device's real cluster map)
  H2  pass-level fast/slow alternation, uniform CPU inflation, block-resume sections ~2x
  H3  persisted-config-load bimodal: iter000 (fresh install, no cached config) fast
  H4  off-window fluctuators: power-service-registration binder stalls; native-lib IO

Run this per device BEFORE any cross-device or cross-arm comparison: if H2 fires, only
matching-state passes may be compared.

Usage: python3 hypothesis_tests.py <campaign-dir>   (expects pass1.json..passN.json + campaign.log)

Set LITTLE_CPUS (comma-separated cpu ids, default "0,1,2,3") to the little cluster's cpu
ids from device_probe.py's topology JSON. The default is wrong on any device whose little
cluster is not cpu0-3 and it fails silently, so set it explicitly per device.
"""

import json
import os
import re
import statistics
import sys

SLOW_DELTA_MS = 4.0        # floor; effective threshold is max(this, 10% of pass median)
                           # so "slow" scales across device tiers, whose median windows can
                           # differ by an order of magnitude between flagship and entry
CL0_MAJORITY = 0.5
CL0_ABSENT = 0.10          # H1 falsifier: slow iteration with <10% cluster-0 residency
CFGLOAD_FAST_MS = 5.0      # H3: below this = "no cached config" fast path
POWER_STALL_MS = 10.0
LITTLE_CPUS = {int(x) for x in os.environ.get("LITTLE_CPUS", "0,1,2,3").split(",")}

PURE_CPU = ["emb-span-service-init", "emb-otel-tracer-init"]
BLOCK_RESUME = ["emb-config-service-init", "emb-payload-source-init",
                "emb-essential-service-init", "emb-post-init", "emb-delivery-init"]


def main() -> int:
    camp = sys.argv[1]
    passes = []
    for i in range(1, 20):
        p = os.path.join(camp, f"pass{i}.json")
        if not os.path.exists(p):
            break
        with open(p) as f:
            passes.append(json.load(f))
    if not passes:
        print("no passN.json files found", file=sys.stderr)
        return 1
    temps = parse_temps(os.path.join(camp, "campaign.log"))

    print(f"hypothesis tests over {len(passes)} passes, "
          f"{sum(len(p) for p in passes)} iterations total")
    print()

    # ---- H2: pass-level behavior --------------------------------------------------
    print("H2. pass-level windows (ms) and battery temps")
    print(f"  {'pass':>4}{'n':>5}{'p50':>8}{'p90':>8}{'max':>8}{'run p50':>9}"
          f"{'temp start':>12}{'temp end':>10}")
    meds = []
    for i, data in enumerate(passes):
        wins = [d["window_ms"] for d in data]
        runs = [sum(d["cpu_ms"].values()) for d in data]
        meds.append(statistics.median(wins))
        t0, t1 = temps.get(i + 1, (None, None))
        print(f"  {i + 1:>4}{len(wins):>5}{statistics.median(wins):>8.1f}"
              f"{pctl(wins, 90):>8.1f}{max(wins):>8.1f}{statistics.median(runs):>9.1f}"
              f"{fmt(t0):>12}{fmt(t1):>10}")
    print(f"  pass medians sequence: {' -> '.join(f'{m:.1f}' for m in meds)}")
    fastest = min(range(len(passes)), key=lambda i: meds[i])
    slowest = max(range(len(passes)), key=lambda i: meds[i])
    print(f"  fastest pass {fastest + 1} ({meds[fastest]:.1f}), "
          f"slowest pass {slowest + 1} ({meds[slowest]:.1f}), "
          f"swing {meds[slowest] - meds[fastest]:.1f} ms")
    print("  slow/fast section-median ratios (H2 predicts pure-CPU ~1x, block-resume ~2x):")
    for c in PURE_CPU + BLOCK_RESUME:
        f_ = med_section(passes[fastest], c)
        s_ = med_section(passes[slowest], c)
        kind = "pure-CPU" if c in PURE_CPU else "block-resume"
        print(f"    {c:<38}{kind:<14}{f_:>7.2f}{s_:>7.2f}  ratio {s_ / f_:>5.2f}x")
    print()

    # ---- H1: cluster placement ----------------------------------------------------
    print("H1. little-core placement vs window")
    all_delta, all_share = [], []
    for i, data in enumerate(passes):
        wins = [d["window_ms"] for d in data]
        med = statistics.median(wins)
        shares = [cl0_share(d) for d in data]
        r = pearson(wins, shares)
        c0 = [w for w, s in zip(wins, shares) if s > CL0_MAJORITY]
        c1 = [w for w, s in zip(wins, shares) if s <= CL0_MAJORITY]
        print(f"  pass {i + 1}: r={r:>5.2f}  little-majority n={len(c0):>3} "
              f"mean={mean_or(c0):>6}  cluster1-majority n={len(c1):>3} mean={mean_or(c1):>6}")
        all_delta.extend(w - med for w in wins)
        all_share.extend(shares)
    print(f"  pooled r(window-delta-vs-pass-median, little-share) = "
          f"{pearson(all_delta, all_share):.2f}   n={len(all_delta)}")
    slow_iters = [(i + 1, j, d) for i, data in enumerate(passes)
                  for j, d in enumerate(data)
                  if d["window_ms"] - statistics.median([x["window_ms"] for x in data])
                  > max(SLOW_DELTA_MS,
                        0.10 * statistics.median([x["window_ms"] for x in data]))]
    on_c0 = [t for t in slow_iters if cl0_share(t[2]) > CL0_MAJORITY]
    falsifiers = [t for t in slow_iters if cl0_share(t[2]) < CL0_ABSENT]
    print(f"  slow iterations (delta > +{SLOW_DELTA_MS:.0f} ms): {len(slow_iters)} total, "
          f"{len(on_c0)} little-majority, {len(falsifiers)} FALSIFIERS "
          f"(<{CL0_ABSENT:.0%} little-core)")
    for pn, j, d in falsifiers[:12]:
        print(f"    pass{pn} iter{j:03d}: window {d['window_ms']:.1f}, "
              f"little {cl0_share(d):.0%}, cfgload {d['dur'].get('emb-persisted-config-load', 0):.1f}, "
              f"spansvc {d['dur'].get('emb-span-service-init', 0):.1f}")
    print()

    # ---- H3: persisted-config-load bimodality --------------------------------------
    print("H3. persisted-config-load: iter000 (fresh install) vs the rest, ms")
    print(f"  {'pass':>4}{'iter000':>9}{'rest min':>10}{'rest p50':>10}{'rest max':>10}"
          f"{'rest <5ms':>11}")
    for i, data in enumerate(passes):
        c0 = data[0]["dur"].get("emb-persisted-config-load")
        rest = [d["dur"].get("emb-persisted-config-load") for d in data[1:]]
        rest = [v for v in rest if v is not None]
        fast_rest = len([v for v in rest if v < CFGLOAD_FAST_MS])
        print(f"  {i + 1:>4}{c0:>9.2f}{min(rest):>10.2f}{statistics.median(rest):>10.2f}"
              f"{max(rest):>10.2f}{fast_rest:>11}")
    print()

    # ---- H4: off-window fluctuators -------------------------------------------------
    print("H4. off-window fluctuators")
    print(f"  {'pass':>4}{'power p50':>10}{'power max':>10}{'stalls>10':>10}"
          f"{'natlib io p50':>15}{'sighand io p50':>15}{'snap max':>10}")
    for i, data in enumerate(passes):
        power = [d["dur"].get("emb-power-service-registration") for d in data]
        power = [v for v in power if v is not None]
        stalls = len([v for v in power if v > POWER_STALL_MS])
        nat_io = [io_ms(d, "emb-load-embrace-native-lib") for d in data]
        sig_io = [io_ms(d, "emb-install-native-crash-signal-handlers") for d in data]
        snap = [d["dur"].get("emb-snapshot-session", 0) for d in data]
        print(f"  {i + 1:>4}{statistics.median(power):>10.2f}{max(power):>10.2f}{stalls:>10}"
              f"{statistics.median(nat_io):>15.2f}{statistics.median(sig_io):>15.2f}"
              f"{max(snap):>10.2f}")
    return 0


def cl0_share(d):
    tot = sum(d["cpu_ms"].values()) or 1.0
    return sum(v for k, v in d["cpu_ms"].items() if int(k) in LITTLE_CPUS) / tot


def io_ms(d, section):
    st = d["states"].get(section, {})
    return (st.get("D", 0) + st.get("D+io", 0) + st.get("DK", 0) + st.get("DK+io", 0)
            + st.get("S+io", 0))


def med_section(data, c):
    return statistics.median([d["dur"][c] for d in data if c in d["dur"]])


def pctl(vals, p):
    s = sorted(vals)
    return s[min(len(s) - 1, int(round(p / 100 * (len(s) - 1))))]


def mean_or(vals):
    if not vals:
        return "--"
    return f"{statistics.mean(vals):.1f}"


def fmt(t):
    if t is None:
        return "--"
    return f"{t:.1f}C"


def parse_temps(log_path):
    temps = {}
    if not os.path.exists(log_path):
        return temps
    with open(log_path) as f:
        for ln in f:
            m = re.search(r"pass (\d+)/\d+ starting, battery ([\d.]+)", ln)
            if m:
                temps.setdefault(int(m.group(1)), [None, None])[0] = float(m.group(2))
            m = re.search(r"pass (\d+) done in .* battery ([\d.]+)", ln)
            if m:
                temps.setdefault(int(m.group(1)), [None, None])[1] = float(m.group(2))
    return {k: tuple(v) for k, v in temps.items()}


def pearson(xs, ys):
    mx, my = statistics.mean(xs), statistics.mean(ys)
    num = sum((x - mx) * (y - my) for x, y in zip(xs, ys))
    den = (sum((x - mx) ** 2 for x in xs) * sum((y - my) ** 2 for y in ys)) ** 0.5
    if den == 0:
        return float("nan")
    return num / den


if __name__ == "__main__":
    sys.exit(main())
