#!/usr/bin/env python3
"""Detect trace data loss BEFORE trusting any number derived from a trace.

Perfetto does not fail loudly when it drops data: a saturated ring buffer silently evicts the
oldest packets, which are exactly the app slices at the start of a launch. A partially-evicted
trace still parses and still answers queries, so it produces plausible numbers from an incomplete
picture.

But "the window is missing from some traces" is NOT by itself evidence of that. The same symptom
is produced - more often - by a query bug, typically a process-name predicate that returns nothing
when process_stats fails to resolve names. Which is why this module never infers loss from a
missing window; it reads perfetto's own counters and lets them decide.

## Not all loss counters mean your number is wrong

This is the correction that matters most here, and it was learned the expensive way: an earlier
version of this module treated EVERY non-zero counter in the `data_loss`/`error` severities as
"lossy". Run over a real 596-trace corpus it condemned 524 of them - and 437 of those were pure
metadata counters (unknown memory-stat field types, unparsed log lines) that cannot touch a slice,
a sched row, or a window duration. A health check that cries wolf on 88% of good traces gets
switched off, which is strictly worse than not having one.

So counters are bucketed by WHAT A NON-ZERO VALUE CAN INVALIDATE:

* **buffer** - data was written and then lost: the central buffer wrapped (`chunks_overwritten`),
  a writer dropped packets, or the kernel ftrace buffer overran. This is the one that can remove
  your window or a chunk of sched. Real, and rare: zero occurrences in that 596-trace corpus,
  including under injected load.
* **parse** - individual events failed to parse or arrived out of order (`systrace_parse_failure`,
  `sorter_push_event_out_of_order`). Surviving slices keep their correct timestamps and durations,
  but *counts* and *presence* claims over the whole trace are no longer safe, because an
  unparsed atrace line is an absent slice. Device-clustered in practice (one device family
  produced 27-55% of traces with these while another produced none), so it shows up as a
  per-device caveat rather than a run-wide failure.
* **meta** - counters about data streams that carry no slices and no scheduling: memory/vmstat
  field types, log formatting, metatrace, power rails. Non-zero here is noise for startup work.

An unrecognised counter is bucketed as **parse**, not ignored, and its raw name is reported - a new
counter should make you look, not disappear.

Two independent checks, because either alone can be fooled:

1. **Loss counters**, bucketed as above.
2. **A canary slice** - a slice you know must exist in every good trace (the init window itself,
   or an app-emitted marker). Its absence with clean buffer counters means a QUERY bug (most
   commonly a process-name predicate that fails when process_stats does not resolve names), the
   wrong build, or a broken instrument - NOT saturation; its absence WITH buffer loss means
   saturation. Distinguishing those two is the whole point of checking both, and the query bug is
   the more common of the two in practice.

Usage:
    from trace_health import check_trace, summarize
    verdict = check_trace(tp_path, trace_path, canary="emb-sdk-start")

CLI:
    python3 trace_health.py <traces-dir> [--canary NAME] [--trace-processor PATH]
"""
import argparse
import os
import pathlib
import subprocess
import sys
import tempfile

# One query, because each trace_processor invocation re-parses the whole trace.
HEALTH_SQL = """
SELECT 'loss.' || name AS k, CAST(SUM(value) AS INT) AS v
  FROM stats
 WHERE severity IN ('data_loss', 'error') AND value > 0
 GROUP BY name
UNION ALL
SELECT 'canary', (SELECT COUNT(*) FROM slice WHERE name = '{canary}')
UNION ALL
SELECT 'slices', (SELECT COUNT(*) FROM slice)
UNION ALL
SELECT 'sched_rows', (SELECT COUNT(*) FROM sched);
"""

# Written data was lost. Only these can remove a window or part of sched.
BUFFER_MARKERS = ("chunks_overwritten", "packet_loss", "abi_violations", "ftrace_cpu_overrun",
                  "trace_writer_packet_loss", "chunks_discarded", "packets_lost")
# Individual events failed to parse / arrived out of order: durations survive, counts do not.
PARSE_MARKERS = ("systrace_parse_failure", "out_of_order", "atrace_tgid_mismatch",
                 "tokenizer_error", "truncated_", "unknown_extension_fields",
                 "invalid_", "mismatched_")
# Streams with no slices and no scheduling: irrelevant to startup numbers.
META_MARKERS = ("mm_unknown_type", "meminfo_unknown", "vmstat_unknown", "android_log_",
                "metatrace_", "energy_", "power_rail", "battery_", "gpu_counter",
                "clock_sync_cache_miss", "frame_timeline_event_parser")


def classify(counter_name):
    """Bucket a perfetto stats counter by what a non-zero value can invalidate."""
    for marker in BUFFER_MARKERS:
        if marker in counter_name:
            return "buffer"
    for marker in META_MARKERS:
        if marker in counter_name:
            return "meta"
    for marker in PARSE_MARKERS:
        if marker in counter_name:
            return "parse"
    return "parse"  # unrecognised: surface it rather than assume it is harmless


def _rows(tp, trace, canary):
    handle, name = tempfile.mkstemp(suffix=".sql", prefix="trace_health_")
    query = pathlib.Path(name)
    os.close(handle)
    try:
        query.write_text(HEALTH_SQL.format(canary=canary))
        cp = subprocess.run([str(tp), "-q", str(query), str(trace)],
                            capture_output=True, text=True, timeout=900)
    finally:
        query.unlink(missing_ok=True)
    out = {}
    for line in cp.stdout.splitlines():
        parts = [p.strip().strip('"') for p in line.split(",")]
        if len(parts) == 2 and parts[0] not in ("k", ""):
            try:
                out[parts[0]] = int(float(parts[1]))
            except ValueError:
                continue
    return out


def check_trace(tp, trace, canary="emb-sdk-start"):
    """Verdict dict: ok / lossy / unusable / slices-incomplete / missing-canary, plus the
    bucketed counters that decided it. `windows_ok` says whether durations read from the window
    are trustworthy; `counts_ok` says whether count- or presence-shaped metrics are."""
    rows = _rows(tp, trace, canary)
    losses = {k[len("loss."):]: v for k, v in rows.items() if k.startswith("loss.") and v}
    buckets = {"buffer": {}, "parse": {}, "meta": {}}
    for name, value in losses.items():
        buckets[classify(name)][name] = value
    canary_count = rows.get("canary", 0)

    verdict, reason = "ok", ""
    if buckets["buffer"] and not canary_count:
        verdict = "unusable"
        reason = ("buffer-level data loss AND no canary slice - the capture saturated and evicted "
                  "the window; fix the config (see prevention below), do not analyse this trace")
    elif buckets["buffer"]:
        verdict = "lossy"
        reason = ("buffer-level data loss but the canary survived - window durations may be "
                  "usable, but anything counting events across the whole trace is not")
    elif not canary_count:
        verdict = "missing-canary"
        reason = ("no data loss at all, but the canary slice is absent - NOT eviction: suspect a "
                  "query bug (e.g. a process-name predicate), the wrong build, a missing atrace "
                  "category, or an instrument that does not exist in this version")
    elif buckets["parse"]:
        verdict = "slices-incomplete"
        reason = ("event-parse errors only: surviving slices keep correct timestamps and "
                  "durations, so window and duration numbers stand, but counts and 'signal was "
                  "absent' claims over this trace do not - an unparsed atrace line is an absent "
                  "slice. Usually clusters by device; report it as a per-device caveat")
    return {"trace": str(trace), "verdict": verdict, "reason": reason,
            "canary_slices": canary_count, "losses": losses, "buckets": buckets,
            "windows_ok": verdict in ("ok", "slices-incomplete", "lossy"),
            "counts_ok": verdict in ("ok",),
            "slices": rows.get("slices", 0), "sched_rows": rows.get("sched_rows", 0)}


def summarize(verdicts):
    counts = {}
    for v in verdicts:
        counts[v["verdict"]] = counts.get(v["verdict"], 0) + 1
    total = len(verdicts) or 1
    clean = counts.get("ok", 0)
    lines = [f"trace health: {clean}/{total} clean" +
             "".join(f", {n} {k}" for k, n in sorted(counts.items()) if k != "ok")]
    buffer_hit = counts.get("lossy", 0) + counts.get("unusable", 0)
    if buffer_hit:
        lines.append(
            f"PREVENTION ({buffer_hit}/{total} lost written data): raise buffer_size_kb, shorten "
            "duration_ms, switch fill_policy to DISCARD (keeps the EARLIEST data, which is where "
            "the init window is), narrow ftrace events and atrace categories, and give app slices "
            "their own buffer via target_buffer so a sched flood cannot evict them.")
    if counts.get("slices-incomplete"):
        lines.append(
            f"CAVEAT ({counts['slices-incomplete']}/{total} had event-parse errors): duration and "
            "window figures stand; do not make count-based or 'signal absent' claims from these "
            "traces without checking whether the affected traces cluster on one device. Narrowing "
            "atrace categories reduces userspace-side parse failures.")
    if counts.get("missing-canary"):
        lines.append(
            f"INVESTIGATE ({counts['missing-canary']}/{total} missing the canary with CLEAN loss "
            "counters): this is a query, build, or instrument problem, not saturation. Do not "
            "'fix' it by enlarging buffers.")
    return "\n".join(lines)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("traces_dir")
    ap.add_argument("--canary", default="emb-sdk-start")
    ap.add_argument("--trace-processor")
    ap.add_argument("--show-meta", action="store_true",
                    help="also print metadata-only counters (off by default: they are noise)")
    args = ap.parse_args()

    sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent))
    from tooling import ensure_trace_processor
    tp = ensure_trace_processor(args.trace_processor)

    traces = sorted(pathlib.Path(args.traces_dir).rglob("*.perfetto-trace"))
    traces += sorted(pathlib.Path(args.traces_dir).rglob("*.pftrace"))
    if not traces:
        sys.exit(f"no traces under {args.traces_dir}")

    verdicts = []
    for trace in traces:
        verdict = check_trace(tp, trace, args.canary)
        verdicts.append(verdict)
        if verdict["verdict"] != "ok":
            shown = dict(verdict["buckets"]["buffer"])
            shown.update(verdict["buckets"]["parse"])
            if args.show_meta:
                shown.update(verdict["buckets"]["meta"])
            detail = ", ".join(f"{k}={v}" for k, v in sorted(shown.items())) or "none"
            print(f"  [{verdict['verdict']:<17}] {trace.name}  canary={verdict['canary_slices']} "
                  f"{detail}")
    print(summarize(verdicts))


if __name__ == "__main__":
    main()
