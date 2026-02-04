#!/bin/bash
set -euo pipefail

# Run connected checks for the SDK, excluding microbenchmark
# On failure, capture logcat and test failures for debugging

./gradlew connectedCheck -x :embrace-microbenchmark:connectedCheck --stacktrace || {
    adb logcat '[Embrace]:d' '*:S' -t 100000 > emulator_logcat.log
    adb pull /storage/emulated/0/Android/data/io.embrace.android.embracesdk.test/cache/test_failure/
    exit 127
}
