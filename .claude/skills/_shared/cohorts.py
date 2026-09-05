#!/usr/bin/env python3
"""Per-iteration user-session cohort verification for a benchmark pass.

`start-first-session` runs one of two paths on every cold start: RESTORE the persisted user session
(within its inactivity timeout; near-zero cost) or CREATE and PERSIST a new one (no stored session,
inactive, or expired; the production cold-start path, which does real work on the main thread). An
arm that intends one path can silently take the other - a `pm clear` that failed, a hook that did not
fire, a relaunch cadence inside the timeout - and the numbers still look valid. So the campaign
runner does not trust its setup: it enables the ExampleApp's logcat telemetry tap
(`settings put global embrace_verify_telemetry startup:1`), streams the `EmbVerify` lines for the
pass, and this module classifies every launch from its exported `sdk-init` span:

* `created`  - `emb.user_session_id` differs from the previous launch's (or the version-startup
               counter is 1, i.e. a fresh install / cleared data)
* `restored` - same `emb.user_session_id` as the previous launch
* `unknown`  - the first launch of a pass whose counter is not 1 (no previous launch to compare)

The expected cohort follows the benchmark method: methods containing `NewUserSession` or
`ExpiredUserSession` expect `created`; every other method expects `restored` except for its first
launch, which is a fresh install and exempt. A launch on the wrong path is a violation, reported in
the campaign log and in `passN-cohorts.json` so the analysis can drop or split it.

Usage:
    from cohorts import parse_embverify, classify, expected_cohort, summarize
CLI:
    python3 cohorts.py <passN-embverify.log> [--method NAME] [--json OUT]
"""
import argparse
import json
import re
import sys

SETTING_KEY = "embrace_verify_telemetry"
SETTING_VALUE = "startup:1"
LOGCAT_ARGS = ("logcat", "-v", "threadtime", "-T", "1", "-s", "EmbVerify:I")

# threadtime: "09-03 14:37:56.782 15291 15314 I EmbVerify: EMBV1 2 1/1 {json chunk}"
_LINE = re.compile(r"^\S+ \S+\s+(\d+)\s+(\d+)\s+I EmbVerify: EMBV1 (\d+) (\d+)/(\d+) (.*)$")

ATTRS = ("emb.user_session_id", "emb.app.version_startup_counter", "start-first-session-duration-ms",
         "post-init-duration-ms",
         # 9.3.0 names; the older name is kept so pre-rename captures still classify and print.
         "art-compile-filter", "app-image-at-init", "init-compile-filter",
         "thread-name", "ended-in-foreground")


def parse_embverify(text):
    """Launch records in log order from a threadtime `EmbVerify` capture: one per `emb-sdk-init` span.

    Chunks of one payload share (pid, seq); they are concatenated in index order and parsed once
    complete. Anything that is not a complete sdk-init span is ignored."""
    chunks = {}
    order = []
    for line in text.splitlines():
        m = _LINE.match(line)
        if not m:
            continue
        pid, _tid, seq, idx, total, chunk = m.groups()
        key = (pid, seq)
        if key not in chunks:
            chunks[key] = {"total": int(total), "parts": {}}
            order.append(key)
        chunks[key]["parts"][int(idx)] = chunk
    launches = []
    for key in order:
        entry = chunks[key]
        if len(entry["parts"]) != entry["total"]:
            continue
        payload = "".join(entry["parts"][i] for i in sorted(entry["parts"]))
        try:
            record = json.loads(payload)
        except ValueError:
            continue
        if record.get("kind") != "span" or record.get("name") != "emb-sdk-init":
            continue
        attrs = record.get("attrs") or {}
        launch = {"pid": int(key[0])}
        for name in ATTRS:
            launch[name] = attrs.get(name)
        start, end = record.get("startNanos"), record.get("endNanos")
        launch["init_ms"] = (end - start) / 1e6 if isinstance(start, int) and isinstance(end, int) else None
        launches.append(launch)
    return launches


def classify(launches):
    """Adds `iteration` and `cohort` to each launch, in place, and returns the list."""
    prev_id = None
    for i, launch in enumerate(launches):
        launch["iteration"] = i
        session = launch.get("emb.user_session_id")
        if i == 0:
            launch["cohort"] = "created" if launch.get("emb.app.version_startup_counter") == "1" else "unknown"
        elif session is not None and session == prev_id:
            launch["cohort"] = "restored"
        else:
            launch["cohort"] = "created"
        prev_id = session
    return launches


def expected_cohort(method):
    """The cohort a StartupBenchmarks method intends, or None when the method is unknown."""
    if not method:
        return None
    if "NewUserSession" in method or "ExpiredUserSession" in method:
        return "created"
    return "restored"


def violations(launches, expected):
    """Iterations whose cohort contradicts `expected`. The first launch of a restoring arm is a fresh
    install and exempt; `unknown` never counts."""
    if expected is None:
        return []
    out = []
    for launch in launches:
        cohort = launch["cohort"]
        if cohort == "unknown" or cohort == expected:
            continue
        if expected == "restored" and launch["iteration"] == 0:
            continue
        out.append(launch["iteration"])
    return out


def summarize(launches, expected, bad):
    counts = {"created": 0, "restored": 0, "unknown": 0}
    for launch in launches:
        counts[launch["cohort"]] += 1
    line = (f"cohorts: {len(launches)} launches, {counts['created']} created, {counts['restored']} restored, "
            f"{counts['unknown']} unknown; expected {expected or 'n/a'}; violations: {len(bad)}")
    if bad:
        line += " (iterations " + ", ".join(str(i) for i in bad) + ")"
    return line


def launch_line(launch):
    """One per-launch line: iteration, pid, cohort, session id prefix, counter, the two durations and the compile filter."""
    session = (launch.get("emb.user_session_id") or "?")[:8]
    return (f"  iter {launch['iteration']:03d}  pid {launch['pid']}  {launch['cohort']:<8} session {session}  "
            f"counter {launch.get('emb.app.version_startup_counter') or '?'}  "
            f"start-first-session {launch.get('start-first-session-duration-ms') or '?'} ms  "
            f"post-init {launch.get('post-init-duration-ms') or '?'} ms  "
            f"compile {launch.get('art-compile-filter') or launch.get('init-compile-filter') or '?'}")


def report(text, method):
    """Parse + classify + check; returns (launches, expected, violations, summary line)."""
    launches = classify(parse_embverify(text))
    expected = expected_cohort(method)
    bad = violations(launches, expected)
    return launches, expected, bad, summarize(launches, expected, bad)


def to_json(launches, expected, bad, method):
    return {"method": method, "expected": expected, "violations": bad, "launches": launches}


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("log", help="threadtime logcat capture filtered to EmbVerify:I")
    ap.add_argument("--method", help="StartupBenchmarks method, to derive the expected cohort")
    ap.add_argument("--json", help="write the per-launch dataset here")
    args = ap.parse_args()
    with open(args.log, encoding="utf-8", errors="replace") as fh:
        text = fh.read()
    launches, expected, bad, summary = report(text, args.method)
    for launch in launches:
        print(launch_line(launch))
    print(summary)
    if args.json:
        with open(args.json, "w") as fh:
            json.dump(to_json(launches, expected, bad, args.method), fh, indent=1, sort_keys=True)
    return 1 if bad else 0


if __name__ == "__main__":
    sys.exit(main())
