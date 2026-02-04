#!/bin/bash
set -euo pipefail

# Generate baseline profile using Android emulator

./gradlew :macrobenchmark:pixel6Api34BaselineProfileAndroidTest \
    -Pandroid.testInstrumentationRunnerArguments.androidx.benchmark.enabledRules=BaselineProfile \
    -Pandroid.testoptions.manageddevices.emulator.gpu="swiftshader_indirect" \
    -Pandroid.experimental.testOptions.managedDevices.maxConcurrentDevices=1
