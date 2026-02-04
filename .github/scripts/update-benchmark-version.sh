#!/bin/bash
set -euo pipefail

# Update the embrace_version in android-sdk-benchmark's gradle.properties

sdk_version="${1:?Error: sdk_version required}"

sed -i "s/embrace_version=.*/embrace_version=${sdk_version}/" gradle.properties
