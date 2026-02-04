#!/bin/bash
set -euo pipefail

# Publish artifacts to Maven Central

./gradlew publishToMavenCentral --no-configuration-cache --stacktrace
