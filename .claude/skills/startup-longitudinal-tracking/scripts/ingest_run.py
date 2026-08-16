#!/usr/bin/env python3
"""Ingest one completed run into the longitudinal store, with validation.

Accepts run directories produced by the other startup skills: it looks for provenance written by
the version/factor matrix (cell-state.json), then for a generic run-metadata.json, and extracts
per-iteration windows from perfetto traces via trace_processor.

Validation is the point. A record that cannot be compared later is worse than no record, so this
refuses (rather than quietly stores) runs whose device is not in the reference set, whose profile
has drifted, or whose recipe differs from the frozen one - unless you pass --force, which stamps
the reason into the record so future-you knows.

Usage:
  python3 ingest_run.py <run-dir> --reference-set reference-set.json --store store.jsonl
  python3 ingest_run.py <run-dir> --reference-set ... --store ... --dry-run
"""
import argparse
import json
import pathlib
import statistics
import subprocess
import sys
import tempfile
import time

WINDOW_SQL = """
WITH win AS (
  SELECT s.ts AS ts, s.dur AS dur FROM slice s
  WHERE s.name = '{slice}' AND s.dur > 0
  ORDER BY s.ts DESC LIMIT 1
)
SELECT (SELECT dur / 1e6 FROM win) AS window_ms;
"""

# A baseline is built from PUBLISHED SDK versions, and the single-slice window instrument
# (`emb-sdk-start`) only exists from 9.2.0 onwards. Every earlier published version has to be
# measured with the composed window the analysis tooling already falls back to: first
# `emb-modules-init` start -> first `emb-post-services-setup` end, both present since 9.0.0. Without
# this, a longitudinal store can only ever hold versions newer than the instrument, which defeats
# the point of having a control version.
#
# The two sources are NOT interchangeable - the composed window is a few ms narrower than the
# native one by construction - so `recipe.instrument` records which produced each value and
# `trend_report.py` must never compare across them.
COMPOSED_WINDOW_SQL = """
WITH m AS (
  SELECT MIN(s.ts) AS ts FROM slice s WHERE s.name = 'emb-modules-init' AND s.dur > 0
),
p AS (
  SELECT MIN(s.ts + s.dur) AS te FROM slice s
   WHERE s.name = 'emb-post-services-setup' AND s.dur > 0
     AND s.ts >= (SELECT ts FROM m)
)
SELECT CASE WHEN (SELECT ts FROM m) IS NULL OR (SELECT te FROM p) IS NULL THEN NULL
            ELSE ((SELECT te FROM p) - (SELECT ts FROM m)) / 1e6 END AS window_ms;
"""

# Which SDK-emitted slices this run produced at all. Presence is NOT a performance measure - across
# devices/versions/recipes it is a capability difference and means nothing. Its value is WITHIN one
# device_key + recipe over time: a slice that was always there and vanishes is a data-integrity
# finding (dropped instrument, revoked capability, saturated capture, or a code path that stopped
# running), which is the one case where an absence is informative.
SIGNALS_SQL = """
SELECT DISTINCT name FROM slice WHERE name GLOB 'emb-*' ORDER BY name;
"""


def signals_in_trace(tp, trace):
    query = pathlib.Path(tempfile.gettempdir()) / "longitudinal_signals.sql"
    query.write_text(SIGNALS_SQL)
    cp = subprocess.run([str(tp), "-q", str(query), str(trace)],
                        capture_output=True, text=True, timeout=600)
    names = []
    for line in cp.stdout.splitlines():
        value = line.strip().strip('"')
        if value.startswith("emb-"):
            names.append(value)
    return sorted(set(names))


sys.path.insert(0, str(pathlib.Path(__file__).resolve().parents[2] / "_shared"))
from tooling import ensure_trace_processor, tool_provenance  # noqa: E402  (path set above)


def find_trace_processor(explicit):
    """Shared, version-pinned cache (see _shared/tooling.py). The tool version is part of the
    measurement recipe: an unpinned tool can shift a longitudinal series in a way that looks
    exactly like a real regression."""
    try:
        return ensure_trace_processor(explicit)
    except (RuntimeError, FileNotFoundError) as exc:
        print(f"trace_processor unavailable: {exc}")
        return None


def window_from_trace(tp, trace, slice_name):
    """Window duration in ms, or None.

    `slice_name` may be the literal name of a single wrapping slice, or the sentinel
    'composed' to use the modules-init -> post-services-setup window that pre-9.2.0 SDKs
    require (see COMPOSED_WINDOW_SQL).
    """
    sql = COMPOSED_WINDOW_SQL if slice_name == "composed" else WINDOW_SQL.format(slice=slice_name)
    query = pathlib.Path(tempfile.gettempdir()) / "longitudinal_window.sql"
    query.write_text(sql)
    cp = subprocess.run([str(tp), "-q", str(query), str(trace)],
                        capture_output=True, text=True, timeout=600)
    for line in reversed(cp.stdout.strip().splitlines()):
        try:
            return float(line.strip().strip('"'))
        except ValueError:
            continue
    return None


def load_provenance(run_dir):
    """Provenance from whichever skill produced the run; None if there is none to be found."""
    for name in ("cell-state.json", "run-metadata.json"):
        for path in sorted(run_dir.rglob(name)):
            return json.loads(path.read_text()), name
    return None, None


def record_is_published(sdk_version, forced_not_baseline=False):
    """A record may seed the baseline only if it was built against an immutable published
    artifact. Anything local/SNAPSHOT is a moving target and is comparison-only."""
    if forced_not_baseline:
        return False
    text = (sdk_version or "").lower()
    if not text:
        return False
    return not any(marker in text for marker in ("snapshot", "local", "dirty", "+"))


def derive(windows):
    values = sorted(windows)
    if not values:
        return {}
    def pct(p):
        return values[min(len(values) - 1, int(p * len(values)))]
    return {"n": len(values), "median": statistics.median(values), "p90": pct(0.90),
            "p95": pct(0.95), "max": values[-1],
            "iqr": pct(0.75) - pct(0.25) if len(values) >= 4 else 0.0}


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("run_dir")
    ap.add_argument("--reference-set", required=True)
    ap.add_argument("--store", required=True)
    ap.add_argument("--device-key", help="override when provenance cannot identify the device")
    ap.add_argument("--trace-processor")
    ap.add_argument("--lossy-tolerance", type=float, default=2.0,
                    help="max %% of traces allowed to report buffer-level loss before the run is "
                         "refused (default 2%%); their windows are still used, since a lossy "
                         "trace whose canary survived has a valid window")
    ap.add_argument("--dry-run", action="store_true")
    ap.add_argument("--force", action="store_true",
                    help="store despite validation failures; the reasons are recorded")
    ap.add_argument("--not-baseline", action="store_true",
                    help="store as comparison-only even if the version looks published")
    args = ap.parse_args()

    run_dir = pathlib.Path(args.run_dir)
    if not run_dir.is_dir():
        sys.exit(f"not a directory: {run_dir}")
    ref = json.loads(pathlib.Path(args.reference_set).read_text())
    recipe_frozen = ref.get("recipe", {})
    devices = ref.get("devices", {})

    provenance, prov_name = load_provenance(run_dir)
    problems = []

    # ---- identify the device -------------------------------------------------------------
    device_key = args.device_key
    serial = (provenance or {}).get("serial")
    if not device_key and serial:
        device_key = next((k for k, cfg in devices.items() if cfg.get("serial") == serial), None)
    if not device_key:
        problems.append("cannot map this run to a device_key in the reference set "
                        "(pass --device-key, or ingest a run whose provenance records the serial)")
    elif devices and device_key not in devices:
        # An explicit --device-key must still be validated against the reference set. Trusting it
        # blindly creates a phantom series under a key no device owns, and every later comparison
        # against that key compares one run to nothing. The key must exist BEFORE runs accumulate
        # under it.
        problems.append(f"device_key {device_key!r} is not in the reference set (known: "
                        f"{sorted(devices)}) - either use one of those keys, or declare this "
                        f"device in the reference set first; do not invent a key at ingest time")
    profile_run = (provenance or {}).get("device_profile") or {}
    if device_key and device_key in devices and profile_run:
        known = devices[device_key].get("profile", {})
        drift = {f: (known.get(f), profile_run.get(f)) for f in known
                 if f in profile_run and known.get(f) != profile_run.get(f)}
        if drift:
            problems.append(f"device profile drift vs reference set: {drift} - an upgraded or "
                            f"replaced device must become a NEW device_key")

    # ---- recipe -------------------------------------------------------------------------
    instrument = recipe_frozen.get("instrument", "app-embrace-start")
    recipe_run = {
        "build_type": (provenance or {}).get("build_type"),
        "compile_state": ((provenance or {}).get("cell", {}).get("levels", {}) or {}).get("compile"),
        "instrument": instrument,
        "run_shape": recipe_frozen.get("run_shape"),
    }
    for field in ("build_type", "compile_state"):
        want, got = recipe_frozen.get(field), recipe_run.get(field)
        if want and got and want != got:
            problems.append(f"recipe mismatch on {field}: frozen={want!r} run={got!r} - this is a "
                            f"different comparable series")

    # ---- windows ------------------------------------------------------------------------
    tp = find_trace_processor(args.trace_processor)
    traces = sorted(run_dir.rglob("*.perfetto-trace")) + sorted(run_dir.rglob("*.pftrace"))
    windows = []
    signals = []
    lossy = 0
    parse_errors = 0
    if tp and traces:
        try:
            from trace_health import check_trace
        except ImportError:
            check_trace = None
        for trace in traces:
            # A saturated capture still parses and still answers queries, so a window read from
            # a lossy trace looks perfectly normal. Count them: a series silently built from
            # partially-evicted traces is exactly the kind of slow corruption this skill exists
            # to prevent. Buffer-level loss and event-parse errors are counted separately because
            # they invalidate different things - see _shared/trace_health.py.
            health = check_trace(tp, trace, instrument) if check_trace is not None else None
            if health is not None:
                if health["verdict"] in ("lossy", "unusable"):
                    lossy += 1
                elif health["verdict"] == "slices-incomplete":
                    parse_errors += 1
                if health["verdict"] == "unusable":
                    continue
            value = window_from_trace(tp, trace, instrument)
            if value:
                windows.append(value)
                # Only take the signal inventory from a fully clean trace. On a trace with
                # event-parse errors an unparsed atrace line is indistinguishable from a signal
                # the SDK never emitted, and this field is the input to the one absence that
                # this skill treats as a finding.
                if not signals and (health is None or health["verdict"] == "ok"):
                    signals = signals_in_trace(tp, trace)
    if lossy:
        share = 100.0 * lossy / max(1, len(traces))
        # Tolerate a trace or two. A `lossy` verdict means buffer loss occurred but the canary
        # SURVIVED, so that trace's window is present and usable - only whole-trace counts are
        # unsafe, and a longitudinal record needs only the window. Blocking a 200-iteration leg
        # over a single 0.5% trace (which is what an all-or-nothing gate did on a real run)
        # discards 199 good measurements to avoid one imperfect one. Genuine saturation shows up
        # as a share well above this, and `unusable` traces - canary evicted - are dropped from
        # the windows regardless of tolerance.
        if share > args.lossy_tolerance:
            problems.append(f"{lossy}/{len(traces)} traces ({share:.1f}%) lost written data "
                            f"(buffer level), above the {args.lossy_tolerance:.1f}% tolerance - "
                            f"raise buffer size / use DISCARD / narrow the captured events; a "
                            f"baseline built from saturated traces will drift for tooling reasons")
        else:
            print(f"NOTE: {lossy}/{len(traces)} traces ({share:.1f}%) reported buffer-level loss, "
                  f"within the {args.lossy_tolerance:.1f}% tolerance - their windows are kept and "
                  f"the count is recorded in trace_health")
    if parse_errors and not signals:
        problems.append(f"{parse_errors}/{len(traces)} traces had event-parse errors and none was "
                        f"clean enough to inventory signals - window durations still stand, but "
                        f"signals_present is empty by tooling, NOT because the SDK emitted nothing")
    elif not tp:
        problems.append("trace_processor not found: fetch it as the startup-analysis skill "
                        "describes, or pass --trace-processor")
    if traces and not windows:
        problems.append(f"no '{instrument}' window found in {len(traces)} trace(s) - wrong "
                        f"instrument for this SDK version, or the run did not record it")

    # An EMPTY run used to sail through every check above and land in the store as n=0,
    # median=nan: a record that looks like a measurement, plots as a gap, and quietly poisons any
    # baseline built from the store. It happened for real on 2026-08-14, when a campaign leg
    # aborted before collecting traces and the ingest still returned success. A run that produced
    # nothing is a FAILED run, and a truncated run is not a small run - it is a different
    # experiment, because the passes that are missing are the later, warmer ones.
    shape = recipe_frozen.get("run_shape") or {}
    expected = (shape.get("passes") or 0) * (shape.get("iterations") or 0)
    if not windows:
        problems.append(f"this run produced NO usable windows ({len(traces)} traces on disk) - "
                        f"there is nothing to record; fix the run, do not store a placeholder")
    elif expected and len(windows) < 0.9 * expected:
        problems.append(f"truncated run: {len(windows)} windows against a declared shape of "
                        f"{shape.get('passes')}x{shape.get('iterations')}={expected} - the missing "
                        f"passes are the later, warmer ones, so this is not comparable to a full "
                        f"run; re-run it or store it with --force and a reason")

    record = {
        "run_id": (provenance or {}).get("plan_run_id") or run_dir.name,
        "ingested_at": time.strftime("%Y-%m-%dT%H:%M:%S"),
        "measured_at": (provenance or {}).get("started") or (provenance or {}).get("measured_at"),
        "device_key": device_key,
        "device_profile": profile_run,
        "sdk_version": ((provenance or {}).get("checks", {}) or {}).get("sdk matches")
                       or (provenance or {}).get("sdk_version"),
        "app_build_id": (provenance or {}).get("apk_sha256"),
        "recipe": {**recipe_run, **tool_provenance(tp)},
        "conditions": ((provenance or {}).get("cell", {}) or {}).get("levels", {}),
        # Only PUBLISHED, immutable SDK versions may form a baseline. A working-tree build is a
        # moving target: baselining on it makes the series unreproducible and re-defines the
        # reference every time someone commits. HEAD/SNAPSHOT runs are still stored - they are
        # compared AGAINST the baseline ad hoc - but never folded into it.
        "baseline_eligible": bool(record_is_published(
            ((provenance or {}).get("checks", {}) or {}).get("sdk matches")
            or (provenance or {}).get("sdk_version") or "", args.not_baseline)),
        "signals_present": signals,
        # Kept as separate counts, not one "lossy" number: buffer loss can remove the window,
        # while parse errors only make counts and absences unsafe.
        "trace_health": {"traces": len(traces), "buffer_loss": lossy,
                         "parse_errors": parse_errors,
                         "signals_from_clean_trace": bool(signals)},
        "windows_ms": [round(w, 3) for w in windows],
        "derived": derive(windows),
        "source_skill": prov_name or "unknown",
        "notes": "",
    }

    for problem in problems:
        print(f"VALIDATION: {problem}")
    if problems and not args.force:
        sys.exit("\nREFUSED: fix the above, or re-run with --force to store it with these reasons "
                 "recorded. A record that cannot be compared later is worse than no record.")
    if problems:
        record["notes"] = "FORCED ingest despite: " + "; ".join(problems)

    summary = record["derived"]
    print(f"\n{record['run_id']} -> device_key={record['device_key']} "
          f"n={summary.get('n', 0)} median={summary.get('median', float('nan')):.1f} "
          f"p90={summary.get('p90', float('nan')):.1f} max={summary.get('max', float('nan')):.1f}")
    if args.dry_run:
        print("dry-run: nothing written")
        return
    with open(args.store, "a") as fh:
        fh.write(json.dumps(record) + "\n")
    print(f"appended to {args.store}")


if __name__ == "__main__":
    main()
