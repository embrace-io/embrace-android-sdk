#!/bin/bash
set -euo pipefail

# Run connected checks for the example app, excluding benchmark tests

./gradlew connectedCheck -x :app:benchmark:connectedBenchmarkAndroidTest
