#!/bin/bash
set -euo pipefail

# Choose the appropriate GitHub Actions runner

actor="${1:-}"
depot_runner="${2:-}"

if [ "$actor" != 'dependabot[bot]' ]; then
    echo "where=${depot_runner}"
else
    echo "where=ubuntu-latest"
fi
