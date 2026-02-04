#!/bin/bash
set -euo pipefail

# Create a new release branch

version="${1:?Error: version required}"

git checkout -b "release/${version}"
