#!/bin/bash
set -euo pipefail

# Set the release version in gradle.properties and push the release branch

version="${1:?Error: version required}"

sed -i -r "s#version=([^\']+)#version=${version}#" gradle.properties
git add gradle.properties
git commit -m "CI/CD: change version to be released: ${version}"
git push --set-upstream origin "release/${version}"
