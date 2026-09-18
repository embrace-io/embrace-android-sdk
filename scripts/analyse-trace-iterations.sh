#!/bin/sh
# Aggregates statistics across every iteration trace of a macrobenchmark run. Run with --help for the options.
#
# Usage: scripts/analyse-trace-iterations.sh <dir> [options]
#
# <dir> holds one .perfetto-trace per iteration, as scripts/macrobenchmark.sh collects them into
# perf/macrobenchmark/<device>/. The run's own benchmarkData.json says which of the traces there belong
# to it, so traces left behind by earlier runs are ignored.
#
# Every section is measured once per iteration, as that iteration's total, so a run of ten iterations
# is ten observations of each. The report goes beside the directory as <dir>-report.<extension>
# unless --output names it.

set -eu

. "$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)/lib/analysis-task.sh"

run_analysis analyseIterations "$@"
