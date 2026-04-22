#!/usr/bin/env bash
# Fetches a validated-but-unpublished Central Portal deployment into a local
# Maven-layout directory, so Gradle can resolve from file:// instead of hitting
# the Portal's flaky /download/ endpoint (which returns 500 for missing files
# and disables the whole repo).
#
# Usage:
#   ./fetch-rc-bundle.sh <deployment-id> <version>
#
# The Portal auth header is read automatically from the `centralManualTestingAuthHeaderValue`
# property in (in order):
#   1. ~/.gradle/gradle.properties
#   2. <script-dir>/gradle.properties  (examples/ExampleApp/gradle.properties)
# You can override with the TOKEN_VALUE env var.
#
# Modules are discovered dynamically from `embrace-*` directories in the SDK
# repo root. Extensions are restricted to those actually produced by our
# publish pipeline (pom / module / jar|aar plus checksums + .asc signatures).
#
# Output:
#   ./embrace-bundle-<version>-<deployment-id>/  (Maven layout)
#   Any existing directory with the same name is deleted first.
#
# Point Gradle at the resulting directory:
#   maven { url = uri("file:///.../embrace-bundle-<version>-<deployment-id>/") }

set -u

if [[ $# -ne 2 ]]; then
  echo "Usage: $0 <deployment-id> <version>"
  echo "Example: $0 abcdefgh-1234-ijkl-5678-abcdefghijkl 8.2.1"
  exit 2
fi

DEPLOYMENT_ID="$1"
VERSION="$2"
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
SDK_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"

read_gradle_prop() {
  local key="$1" file="$2"
  [[ -f "$file" ]] || return 1
  awk -F= -v k="$key" '
    $0 ~ "^[[:space:]]*"k"[[:space:]]*=" {
      sub("^[[:space:]]*"k"[[:space:]]*=[[:space:]]*", "")
      sub("[[:space:]]*$", "")
      print
      found = 1
      exit
    }
    END { exit !found }
  ' "$file"
}

TOKEN_SOURCE=""
if [[ -n "${TOKEN_VALUE:-}" ]]; then
  TOKEN_SOURCE="\$TOKEN_VALUE env var"
else
  for candidate in "$HOME/.gradle/gradle.properties" "$SCRIPT_DIR/gradle.properties"; do
    if value=$(read_gradle_prop "centralManualTestingAuthHeaderValue" "$candidate"); then
      TOKEN_VALUE="$value"
      TOKEN_SOURCE="$candidate"
      break
    fi
  done
fi

if [[ -z "${TOKEN_VALUE:-}" ]]; then
  echo "ERROR: could not find 'centralManualTestingAuthHeaderValue' in:"
  echo "  - \$TOKEN_VALUE env var"
  echo "  - $HOME/.gradle/gradle.properties"
  echo "  - $SCRIPT_DIR/gradle.properties"
  echo "Set it in one of those, e.g. in ~/.gradle/gradle.properties:"
  echo "  centralManualTestingAuthHeaderValue=Bearer <base64(user:pass)>"
  exit 1
fi

OUT="${SCRIPT_DIR}/embrace-bundle-${VERSION}-${DEPLOYMENT_ID}"
BASE="https://central.sonatype.com/api/v1/publisher/deployment/${DEPLOYMENT_ID}/download"

# Discover all embrace-* module directories in the SDK root.
MODULES=()
while IFS= read -r name; do
  [[ -d "$SDK_ROOT/$name" ]] || continue
  MODULES+=("$name")
done < <(ls "$SDK_ROOT" 2>/dev/null | grep "^embrace-" | sort)

if [[ ${#MODULES[@]} -eq 0 ]]; then
  echo "ERROR: no embrace-* modules found under $SDK_ROOT"
  exit 1
fi

# Extensions actually produced by our publish pipeline (verified against a
# previously fetched bundle). `aar` and `jar` are both tried per module; only
# one will exist. No sources/javadoc classifiers are published.
EXTENSIONS=(
  pom pom.asc pom.md5 pom.sha1 pom.sha256 pom.sha512
  module module.asc module.md5 module.sha1 module.sha256 module.sha512
  jar jar.asc jar.md5 jar.sha1 jar.sha256 jar.sha512
  aar aar.asc aar.md5 aar.sha1 aar.sha256 aar.sha512
)

# Plugin marker is POM-only (packaging=pom).
MARKER_EXTENSIONS=(pom pom.asc pom.md5 pom.sha1 pom.sha256 pom.sha512)

# Clean any prior bundle for this (version, deployment) so the new fetch is authoritative.
if [[ -d "$OUT" ]]; then
  echo "Removing existing bundle: $OUT"
  rm -rf "$OUT"
fi

ok=0
missing=0
fetch() {
  local path="$1"
  local target="${OUT}/${path}"
  mkdir -p "$(dirname "$target")"
  local code
  code=$(curl -sS -L -o "$target" -w "%{http_code}" \
    -H "Authorization: ${TOKEN_VALUE}" "${BASE}/${path}")
  if [[ "$code" == "200" ]]; then
    ok=$((ok + 1))
    echo "  [200] ${path}"
  else
    rm -f "$target"
    missing=$((missing + 1))
  fi
}

echo "Deployment: ${DEPLOYMENT_ID}"
echo "Version:    ${VERSION}"
echo "Token from: ${TOKEN_SOURCE}"
echo "Modules:    ${#MODULES[@]} (from ${SDK_ROOT})"
echo "Output:     ${OUT}"
echo

for m in "${MODULES[@]}"; do
  echo "-- io.embrace:${m}:${VERSION}"
  for ext in "${EXTENSIONS[@]}"; do
    fetch "io/embrace/${m}/${VERSION}/${m}-${VERSION}.${ext}"
  done
done

echo
echo "-- plugin marker io.embrace.gradle:io.embrace.gradle.gradle.plugin:${VERSION}"
for ext in "${MARKER_EXTENSIONS[@]}"; do
  fetch "io/embrace/gradle/io.embrace.gradle.gradle.plugin/${VERSION}/io.embrace.gradle.gradle.plugin-${VERSION}.${ext}"
done

echo
echo "Done. Downloaded: ${ok}. Skipped (missing/error): ${missing}."

# Patch settings.gradle.kts so Gradle picks up the new bundle. Replaces either
# the literal <path-to-bundle> placeholder or a previously-written bundle path.
SETTINGS="${SCRIPT_DIR}/settings.gradle.kts"
if [[ $ok -eq 0 ]]; then
  echo "Skipping settings.gradle.kts update (no artifacts downloaded)."
elif [[ ! -f "$SETTINGS" ]]; then
  echo "WARNING: $SETTINGS not found — skipping settings update."
else
  # BSD + GNU sed compatible: -i.bak then remove the backup.
  sed -i.bak \
    -e "s|<path-to-bundle>|${OUT}|g" \
    -e "s|file://[^\"]*embrace-bundle-[^\"]*|file://${OUT}|g" \
    "$SETTINGS"
  rm -f "${SETTINGS}.bak"
  if grep -q "file://${OUT}" "$SETTINGS"; then
    echo "Updated ${SETTINGS} → file://${OUT}"
  else
    echo "WARNING: no <path-to-bundle> or prior bundle URL found in ${SETTINGS}."
    echo "         Add    url = uri(\"file://<path-to-bundle>/\")   to centralManualTesting"
    echo "         and rerun, or point Gradle manually at: file://${OUT}/"
  fi
fi
