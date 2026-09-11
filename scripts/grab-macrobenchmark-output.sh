#!/bin/sh
# Copies the last macrobenchmark run's output into a shell-safe directory.
#
# Usage: scripts/grab-macrobenchmark-output.sh [dest-dir]
#
# AGP already pulls the traces off the device; this only renames the device directory (which is
# named e.g. "Medium_Phone(AVD) - 15") and keeps the files flat, since the summary txt links
# traces by relative filename. Exit codes: 1 no run output, 2 no traces.

set -eu

root=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
outputs=$root/embrace-macrobenchmark/build/outputs
results=$outputs/androidTest-results/connected/benchmark

src=""
for d in "$outputs/connected_android_test_additional_output/benchmark/connected"/*/; do
    if [ -d "$d" ] && { [ -z "$src" ] || [ "$d" -nt "$src" ]; }; then src=${d%/}; fi
done

if [ -z "$src" ]; then
    printf 'no run output; run scripts/run-macrobenchmark.sh first\n' >&2
    exit 1
fi

device=$(basename -- "$src")
name=$(printf '%s' "$device" | tr -cs 'A-Za-z0-9._-' '_' | sed -e 's/[_-][_-]*/_/g' -e 's/^_//' -e 's/_$//')
dest=${1:-$root/perf/macrobenchmark/${name:-device}}
mkdir -p "$dest"

for f in "$src"/* "$results/$device"/logcat-*.txt "$results"/TEST-*.xml; do
    if [ -f "$f" ]; then cp -p "$f" "$dest/"; fi
done

count=0
for f in "$dest"/*.perfetto-trace; do
    if [ -f "$f" ]; then count=$((count + 1)); fi
done

if [ "$count" -eq 0 ]; then
    printf 'no traces in %s\n' "$src" >&2
    exit 2
fi

printf '%d traces -> %s\n' "$count" "$dest"
if grep -qs 'Running on Emulator' "$dest"/additionaltestoutput.*.txt; then
    printf 'WARNING: emulator run; results are not comparable to a real device.\n'
fi
