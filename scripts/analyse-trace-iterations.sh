#!/bin/sh
# Aggregates statistics across every iteration trace of a macrobenchmark run. Run with --help for the options.
#
# Usage: scripts/analyse-trace-iterations.sh <dir> [options]
#
# <dir> holds one .perfetto-trace per iteration, as scripts/macrobenchmark.sh collects them into
# perf/macrobenchmark/<device>/. The run's own benchmarkData.json says which of the traces there belong
# to it, so traces left behind by earlier runs are ignored.
#
# PARTLY IMPLEMENTED: the run's traces are read and summarised, but nothing aggregates them into a
# report yet, so anything but --help and --dry-run exits 9.

set -eu

. "$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)/lib/analysis-task.sh"

run_analysis analyseIterations "$@"
