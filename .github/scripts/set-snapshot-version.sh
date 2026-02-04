#!/bin/bash
set -euo pipefail

# Set a custom snapshot version in gradle.properties (if provided)

snapshot_name="${1:-}"

if [ -n "$snapshot_name" ]; then
    sed -i -r "s#^version=.*#version=${snapshot_name}-SNAPSHOT#" gradle.properties
    git add gradle.properties
fi
