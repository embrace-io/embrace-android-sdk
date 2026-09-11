#!/bin/sh
# Analyses a .perfetto-trace file.
#
# Usage: scripts/analyse-trace.sh <trace.perfetto-trace> [--dry-run]

set -eu

root=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)

if [ $# -lt 1 ]; then
    printf 'usage: %s <trace.perfetto-trace> [--dry-run]\n' "$0" >&2
    exit 1
fi

trace=$1
shift

exec "$root/gradlew" -q --console=plain -p "$root" :embrace-perfetto-analysis:analyseTrace \
    --args="'$trace' $*"
