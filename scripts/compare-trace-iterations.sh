#!/bin/sh
# Compares two macrobenchmark runs. Run with --help for the options.
#
# Usage: scripts/compare-trace-iterations.sh <baseline-dir> <candidate-dir> [options]
#
# Both inputs are run directories, as scripts/analyse-trace-iterations.sh takes one of. Each is
# aggregated the same way, so neither has to be analysed first.
#
# A section counts as having moved only when the shift in its mean clears both runs' deviations added
# together. Without --output the report goes beside the baseline, named for both runs, so it does not
# write over the report either of them aggregates to on its own.

set -eu

. "$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)/lib/analysis-task.sh"

run_analysis compareIterations "$@"
