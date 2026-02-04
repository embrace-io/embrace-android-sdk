#!/bin/bash
set -euo pipefail

# Validate that the version in gradle.properties ends with -SNAPSHOT

if ! grep -q -- '-SNAPSHOT$' gradle.properties; then
    echo "ERROR: version must end with -SNAPSHOT"
    exit 1
fi
