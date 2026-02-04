#!/bin/bash
set -euo pipefail

# Run full build excluding plugin integration tests

./gradlew build embrace-microbenchmark:assembleAndroidTest -x embrace-gradle-plugin-integration-tests:test --stacktrace
