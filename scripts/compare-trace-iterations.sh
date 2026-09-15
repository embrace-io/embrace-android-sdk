#!/bin/sh
# Compares two aggregates written by scripts/analyse-trace-iterations.sh. Run with --help for the options.
#
# Usage: scripts/compare-trace-iterations.sh <baseline.json> <candidate.json> [options]
#
# Both inputs are the json aggregates, not trace directories and not rendered reports.
#
# NOT IMPLEMENTED: the task behind this is a placeholder that exits 9; only --help does anything.

set -eu

. "$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)/lib/analysis-task.sh"

run_analysis compareIterations "$@"
