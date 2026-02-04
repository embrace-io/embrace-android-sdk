#!/bin/bash
set -euo pipefail

# Run Gradle plugin integration tests

./gradlew embrace-gradle-plugin-integration-tests:test --stacktrace
