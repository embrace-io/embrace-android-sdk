#!/bin/bash
set -euo pipefail

# Set the next snapshot version in gradle.properties

next_version="${1:?Error: next_version required}"

sed -i -r "s#version=([^\']+)#version=${next_version}-SNAPSHOT#" gradle.properties
