#!/usr/bin/env bash
# Validates the semantic-convention registry:
#   1. the pinned core-semconv and embrace-semconv versions match the manifest deps, and
#   2. the schema is valid and every dependency ref resolves (weaver registry check).
#
# Requires the pinned weaver on PATH set in versions.env. See README.md for how it is
# installed. Usage: bash embrace-android-semconv/check.sh
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=versions.env
source "${SCRIPT_DIR}/versions.env"
REGISTRY="${SCRIPT_DIR}/src/main/semconv"
MANIFEST="${REGISTRY}/manifest.yaml"

# Keep the pinned core-semconv version and the manifest's otel dependency in lockstep.
if ! grep -q "semantic-conventions@${CORE_SEMCONV_VERSION}\[model\]" "${MANIFEST}"; then
  echo "error: ${MANIFEST} otel dependency does not match CORE_SEMCONV_VERSION=${CORE_SEMCONV_VERSION} in versions.env." >&2
  echo "Update whichever of the two is stale." >&2
  exit 1
fi

# Keep the pinned embrace-semconv version and the manifest's embrace dependency in lockstep
if ! grep -q "embrace-semconv@${EMBRACE_SEMCONV_VERSION}\[model\]" "${MANIFEST}"; then
  echo "error: ${MANIFEST} embrace dependency does not match EMBRACE_SEMCONV_VERSION=${EMBRACE_SEMCONV_VERSION} in versions.env." >&2
  echo "Update whichever of the two is stale." >&2
  exit 1
fi

# Validate the schema and resolve every dependency ref (fails on a bad schema or an
# unresolved ref, e.g. a definition removed from a dependency that a group still refs).
weaver registry check -r "${REGISTRY}" --v2
