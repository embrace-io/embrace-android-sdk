#!/bin/bash
set -euo pipefail

# Clear all GitHub Actions cache for the repository

gh cache delete --all --repo embrace-io/embrace-android-sdk
