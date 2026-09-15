#!/bin/sh
# Aggregates statistics across every iteration trace of a macrobenchmark run.
#
# Usage: scripts/analyse-trace-iterations.sh <dir> [--operations a,b | --all-operations]
#                                            [--format markdown|json|html] [--output <file>] [--dry-run]
#
# <dir> holds one .perfetto-trace per iteration, as scripts/macrobenchmark.sh collects them into
# perf/macrobenchmark/<device>/.
#
# NOT IMPLEMENTED: the task behind this is a placeholder that exits 9; only --help does anything.

set -eu

root=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)

if [ $# -lt 1 ]; then
    printf 'usage: %s <dir> [--operations a,b | --all-operations] [--format markdown|json|html] [--output <file>] [--dry-run]\n' "$0" >&2
    exit 1
fi

# quoted one at a time, since gradle splits --args on whitespace
args="'$1'"
shift
for arg in "$@"; do
    args="$args '$arg'"
done

exec "$root/gradlew" -q --console=plain -p "$root" :embrace-perfetto-analysis:analyseIterations \
    --args="$args"
