#!/bin/sh
# Compares two aggregates written by scripts/analyse-trace-iterations.sh.
#
# Usage: scripts/compare-trace-iterations.sh <baseline.json> <candidate.json>
#                                            [--format markdown|json|html] [--output <file>] [--dry-run]
#
# Both inputs are the json aggregates, not trace directories and not rendered reports.
#
# NOT IMPLEMENTED: the task behind this is a placeholder that exits 9; only --help does anything.

set -eu

root=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)

if [ $# -lt 2 ]; then
    printf 'usage: %s <baseline.json> <candidate.json> [--format markdown|json|html] [--output <file>] [--dry-run]\n' "$0" >&2
    exit 1
fi

# quoted one at a time, since gradle splits --args on whitespace
args="'$1' '$2'"
shift 2
for arg in "$@"; do
    args="$args '$arg'"
done

exec "$root/gradlew" -q --console=plain -p "$root" :embrace-perfetto-analysis:compareIterations \
    --args="$args"
