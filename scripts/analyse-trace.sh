#!/bin/sh
# Analyses a .perfetto-trace file. Run with --help for the options.
#
# Usage: scripts/analyse-trace.sh <trace.perfetto.gz> [options]

set -eu

. "$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)/lib/analysis-task.sh"

run_analysis analyseTrace "$@"
