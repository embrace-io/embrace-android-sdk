#!/bin/bash
set -euo pipefail

# Publish a git tag, removing any existing tag with the same name

version="${1:?Error: version required}"

git push origin ":refs/tags/${version}" || true
git tag -f "$version"
git push origin --tags
