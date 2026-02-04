#!/bin/bash
set -euo pipefail

# Read the version from gradle.properties and output it for GitHub Actions

version=$(grep '^version' gradle.properties | cut -d'=' -f2 | tr -d '[:space:]')
echo "sdk_version=${version}"
