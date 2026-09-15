#!/bin/sh
# Analyses a .perfetto-trace file.
#
# Usage: scripts/analyse-trace.sh <trace.perfetto.gz> [--operations a,b | --all-operations]
#                                 [--format markdown|json] [--dry-run]

set -eu

root=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)

if [ $# -lt 1 ]; then
    printf 'usage: %s <trace.perfetto.gz> [--operations a,b | --all-operations] [--format markdown|json] [--dry-run]\n' "$0" >&2
    exit 1
fi

# quoted one at a time, since gradle splits --args on whitespace
args="'$1'"
shift
for arg in "$@"; do
    args="$args '$arg'"
done

exec "$root/gradlew" -q --console=plain -p "$root" :embrace-perfetto-analysis:analyseTrace \
    --args="$args"
