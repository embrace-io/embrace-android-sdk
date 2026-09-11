#!/bin/sh
# Runs the macrobenchmark, then copies its output.
#
# Usage: scripts/macrobenchmark.sh [--out DIR] [run-macrobenchmark.sh options...]
#
# --out must come first if given; everything else is forwarded to run-macrobenchmark.sh.

set -eu

dir=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)

out=""
if [ "${1:-}" = --out ]; then out=$2; shift 2; fi

# Nothing to collect for a dry run.
case " $* " in
    *" --dry-run "*|*" -h "*|*" --help "*) exec "$dir/run-macrobenchmark.sh" "$@" ;;
esac

"$dir/run-macrobenchmark.sh" "$@"
"$dir/grab-macrobenchmark-output.sh" ${out:+"$out"}
