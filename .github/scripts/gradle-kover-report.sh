#!/bin/bash
set -euo pipefail

# Generate Kover code coverage report

./gradlew koverXmlReport
