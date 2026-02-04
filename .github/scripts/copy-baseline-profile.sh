#!/bin/bash
set -euo pipefail

# Copy the generated baseline profile to the SDK module

cp android-sdk-benchmark/macrobenchmark/build/outputs/managed_device_android_test_additional_output/baselineprofile/pixel6Api34/BaselineProfileGenerator_startup-baseline-prof.txt \
   embrace-android-sdk/src/main/baseline-prof.txt
