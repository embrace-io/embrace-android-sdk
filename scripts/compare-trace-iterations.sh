#!/bin/sh
# Compares two macrobenchmark runs. Run with --help for the options.
#
# Usage: scripts/compare-trace-iterations.sh <baseline-dir> <candidate-dir> [options]
#
# Both inputs are run directories, as scripts/analyse-trace-iterations.sh takes one of. Each is
# aggregated the same way, so neither has to be analysed first.
#
# PARTLY IMPLEMENTED: both runs are read and aggregated, but nothing compares them or writes a
# report yet, so an invocation that gets that far exits 9.

set -eu

. "$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)/lib/analysis-task.sh"

run_analysis compareIterations "$@"
