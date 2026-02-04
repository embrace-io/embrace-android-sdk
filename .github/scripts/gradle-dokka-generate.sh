#!/bin/bash
set -euo pipefail

# Generate API documentation using Dokka

./gradlew dokkaGenerate
