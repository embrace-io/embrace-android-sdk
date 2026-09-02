#!/usr/bin/env python3
"""Side-by-side per-section comparison across devices: median / max / % of window,
pooled over every available pass's variance JSON (variance_analysis.py --json output).

This is the workload-identity check: compare section SHARES (%win), not absolute ms.
Matching shares across devices mean the SDK is doing identical work everywhere and the
differences are device effects; diverging shares mean the code path itself differs
(ART/OS generation, config) and that is the lead to chase.

Usage: python3 cross_device_sections.py <label>=<dir-with-passN.json> [more...]
Example (labels are yours — use the device-profile name you probed with, and give
each device a label that encodes the axis it covers, e.g. tier/vendor/api):
  python3 cross_device_sections.py entry-vendorA-api29=.../campaign1 \
      mid-vendorB-api33=.../campaign2 flagship-vendorB-api35=.../campaign3
Dirs given the same label are pooled together (e.g. repeat campaigns on one device).
"""

import json
import os
import statistics
import sys

SECTIONS = [
    "emb-embrace-impl-init",
    "emb-bootstrapper-init",
    "emb-modules-init",
    "emb-persisted-config-load",
    "emb-config-service-init",
    "emb-span-service-init",
    "emb-otel-tracer-init",
    "emb-essential-service-init",
    "emb-delivery-init",
    "emb-payload-source-init",
    "emb-post-init",
    "emb-post-services-setup",
    "emb-load-instrumentation",
    "emb-install-native-crash-signal-handlers",
    "emb-load-embrace-native-lib",
    "emb-record-startup",
]


def load_device(dirs):
    iters = []
    for d in dirs:
        for i in range(1, 20):
            p = os.path.join(d, f"pass{i}.json")
            if not os.path.exists(p):
                continue
            with open(p) as f:
                iters.extend(json.load(f))
    return iters


def main():
    devices = {}
    for arg in sys.argv[1:]:
        label, d = arg.split("=", 1)
        devices.setdefault(label, []).append(d)
    data = {label: load_device(dirs) for label, dirs in devices.items()}
    labels = list(data.keys())

    print("iterations pooled: " + "  ".join(f"{lb}={len(data[lb])}" for lb in labels))
    print()
    wmed = {}
    print(f"{'':<44}" + "".join(f"{lb + ' med':>10}{lb + ' max':>10}" for lb in labels))
    for lb in labels:
        wins = [it["window_ms"] for it in data[lb]]
        wmed[lb] = statistics.median(wins)
    row = f"{'SDK-init window':<44}"
    for lb in labels:
        wins = [it["window_ms"] for it in data[lb]]
        row += f"{statistics.median(wins):>10.1f}{max(wins):>10.1f}"
    print(row)
    print()
    print("sections (ms; %win = section median / device window median):")
    hdr = f"{'section':<44}"
    for lb in labels:
        hdr += f"{lb + ' med':>10}{lb + ' max':>10}{'%win':>6}"
    print(hdr)
    for sec in SECTIONS:
        row = f"{sec:<44}"
        present = False
        for lb in labels:
            vals = [it["dur"][sec] for it in data[lb] if sec in it["dur"]]
            if vals:
                present = True
                m = statistics.median(vals)
                row += f"{m:>10.2f}{max(vals):>10.2f}{m / wmed[lb] * 100:>6.1f}"
            else:
                row += f"{'--':>10}{'--':>10}{'--':>6}"
        if present:
            print(row)
    print()
    print("share of window, biggest three per device (med basis):")
    for lb in labels:
        shares = []
        for sec in SECTIONS[3:13]:
            vals = [it["dur"][sec] for it in data[lb] if sec in it["dur"]]
            if vals:
                shares.append((sec, statistics.median(vals) / wmed[lb] * 100))
        shares.sort(key=lambda kv: -kv[1])
        top = ", ".join(f"{s.replace('emb-', '')} {v:.0f}%" for s, v in shares[:3])
        print(f"  {lb}: {top}")


if __name__ == "__main__":
    sys.exit(main())
