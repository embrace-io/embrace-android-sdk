#!/bin/sh
# Aggregates statistics across every iteration trace of a macrobenchmark run. Run with --help for the options.
#
# Usage: scripts/analyse-trace-iterations.sh <dir> [options]
#
# <dir> holds one .perfetto-trace per iteration, as scripts/macrobenchmark.sh collects them into
# perf/macrobenchmark/<device>/.
#
# NOT IMPLEMENTED: the task behind this is a placeholder that exits 9; only --help does anything.

set -eu

. "$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)/lib/analysis-task.sh"

run_analysis analyseIterations "$@"
