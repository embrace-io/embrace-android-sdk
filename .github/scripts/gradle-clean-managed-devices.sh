#!/bin/bash
set -euo pipefail

# Clean unused managed devices

./gradlew cleanManagedDevices --unused-only
