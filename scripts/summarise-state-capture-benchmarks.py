#!/usr/bin/env python3
"""Summarise a run of the state capture benchmarks as markdown tables.

Reads the benchmarkData.json that :embrace-microbenchmark:connectedReleaseAndroidTest pulls from the device, and prints,
for each benchmark of StateCaptureBenchmarks and StateChangeScalingBenchmarks, the median time and allocation count of
recording states into a span for each state against recording them into the journal of state changes.

usage: summarise-state-capture-benchmarks.py <benchmarkData.json> [more.json ...]

Given several files, such as one per device or per run, each is summarised under its own heading.
"""

import json
import re
import sys

# e.g. changeSessionPart[JOURNAL-10-states] and persistChange[PER_STATE_SPAN-after-500-changes]. The benchmark library
# prefixes a name with each warning it was told to suppress, as in EMULATOR_changeSessionPart[...]; those are reported
# in the heading so that such a run is not mistaken for a sound one.
NAME = re.compile(
    r"^(?P<warnings>(?:[A-Z-]+_)*)(?P<benchmark>[a-z]\w*)"
    r"\[(?P<recording>PER_STATE_SPAN|JOURNAL)-(?:(?P<states>\d+)-states|after-(?P<changes>\d+)-changes)\]$"
)

CLASSES = ("StateCaptureBenchmarks", "StateChangeScalingBenchmarks")

# what one measured run of each benchmark covers, and what its rows are counted in
BENCHMARKS = {
    "changeSessionPart": ("one session part change, no state changed in the part", "states"),
    "changeSessionPartAfterTransitions": ("one session part change, every state changed 5 times in the part", "states"),
    "transitions": ("50 changes of one state at the start of a session part", "states"),
    "transitionWithIdleWorker": (
        "one change of one state, with the persistence worker idle and free to run; the allocation count includes "
        "whatever the worker did meanwhile",
        "states",
    ),
    "stampLog": ("building what one log record is stamped with, no state having changed since the last", "states"),
    "stampLogAfterChange": ("50 times: one change of one state, then building what a log record is stamped with", "states"),
    "readSessionPart": ("reading back a session part in which every state changed 5 times, for delivery", "states"),
    "recordChanges": ("100 changes of one state, on the thread reporting them", "changes already made in the part"),
    "persistChange": (
        "one write by the persistence worker: a record appended for the journal, made for every change; the span written "
        "again whole for a span per state, made once for all the changes of two seconds",
        "changes already made in the part",
    ),
}


def metric(benchmark, name):
    return benchmark.get("metrics", {}).get(name, {}).get("median")


def load(path):
    with open(path) as file:
        data = json.load(file)
    results = {}
    warnings = set()
    for benchmark in data.get("benchmarks", []):
        match = NAME.match(benchmark.get("name", ""))
        if not match or not benchmark.get("className", "").endswith(CLASSES):
            continue
        row = int(match["states"] if match["states"] is not None else match["changes"])
        results[(match["benchmark"], row, match["recording"])] = (
            metric(benchmark, "timeNs"),
            metric(benchmark, "allocationCount"),
        )
        warnings.update(warning for warning in match["warnings"].split("_") if warning)
    return data.get("context", {}), results, sorted(warnings)


def micros(nanos):
    return "n/a" if nanos is None else f"{nanos / 1000:,.1f}"


def count(value):
    return "n/a" if value is None else f"{value:,.0f}"


def change(per_state, journal):
    if not per_state or journal is None:
        return "n/a"
    return f"{(journal - per_state) / per_state:+.0%}"


def describe(context):
    build = context.get("build", {})
    parts = [
        f"{build.get('brand', '?')} {build.get('model', '?')}",
        f"API {build.get('version', {}).get('sdk', '?')}",
        f"{context.get('cpuCoreCount', '?')} cores",
    ]
    if context.get("cpuLocked") is not None:
        parts.append(f"cpuLocked={context['cpuLocked']}")
    return ", ".join(parts)


def summarise(path):
    context, results, warnings = load(path)
    print(f"## {path}\n")
    print(f"Device: {describe(context)}\n")
    if warnings:
        print(f"**Not a sound run.** The benchmark library flagged: {', '.join(warnings)}.\n")
    if not results:
        print("No state capture benchmark results in this file.\n")
        return
    names = sorted({key[0] for key in results}, key=lambda name: list(BENCHMARKS).index(name) if name in BENCHMARKS else 99)
    for name in names:
        unit, rows_are = BENCHMARKS.get(name, ("see the benchmark's source", "rows"))
        rows = sorted({key[1] for key in results if key[0] == name and key[1] > 0})
        if not rows:
            continue
        print(f"### {name}\n")
        print(f"One run is {unit}. Times are medians in microseconds.\n")
        floor_time, floor_allocations = results.get((name, 0, "PER_STATE_SPAN"), (None, None))
        if floor_time is not None:
            print(f"With no states: {micros(floor_time)} µs, {count(floor_allocations)} allocations.\n")
        print(f"| {rows_are} | span per state µs | journal µs | time change | span per state allocs | journal allocs | allocs change |")
        print("|---:|---:|---:|---:|---:|---:|---:|")
        for row in rows:
            span_time, span_allocations = results.get((name, row, "PER_STATE_SPAN"), (None, None))
            journal_time, journal_allocations = results.get((name, row, "JOURNAL"), (None, None))
            print(
                f"| {row:,} | {micros(span_time)} | {micros(journal_time)} | {change(span_time, journal_time)} "
                f"| {count(span_allocations)} | {count(journal_allocations)} "
                f"| {change(span_allocations, journal_allocations)} |"
            )
        print()


def main():
    if len(sys.argv) < 2:
        sys.exit(__doc__)
    for path in sys.argv[1:]:
        summarise(path)


if __name__ == "__main__":
    main()
