#!/bin/bash
set -euo pipefail

# Extracts the base version (major.minor.0) from a full version string

if [ -z "${1:-}" ]; then
    echo "Error: Version argument required"
    exit 1
fi

version="$1"
base_version=$(echo "$version" | cut -d. -f1,2).0
echo "base_version=$base_version"
