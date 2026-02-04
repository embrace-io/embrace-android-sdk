#!/bin/bash
set -euo pipefail

# Publish generated documentation to GitHub Pages

rc_version="${1:?Error: rc_version required}"
commit_sha="${2:?Error: commit_sha required}"

mv build/dokka/html .docs-newly-generated
git checkout gh-pages
git rm -rf docs
mv .docs-newly-generated docs
date > docs/version.txt
echo "$commit_sha" >> docs/version.txt
echo "$rc_version" >> docs/version.txt
git add -f docs

.github/scripts/configure-git.sh

git commit --allow-empty --message "CI/CD: Automatically generated documentation for ${rc_version}" docs/
git push --force origin gh-pages
