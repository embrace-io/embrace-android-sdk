#!/bin/bash -e
#
# Prints a draft changelog entry for the next release, in the format used by the Embrace Android SDK release notes.
# The candidate entries are raw commit subjects and must be curated into user-facing bullets before publishing.
#
# Usage: draft-changelog-entries.sh [version]
#   version defaults to the version in gradle.properties, without the -SNAPSHOT suffix.

repo_url="https://github.com/${GITHUB_REPOSITORY:-embrace-io/embrace-android-sdk}"

version="${1:-$(sed -n -E 's/^version=(.*)-SNAPSHOT$/\1/p; s/^version=([^-]*)$/\1/p' gradle.properties)}"
[[ "$version" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]] || { echo "Error: invalid version '$version'" >&2; exit 1; }

# Release tags live on release branches and are not reachable from main, so pick the highest tag below this version.
prior_version=$(
  { git tag --list '[0-9]*.[0-9]*.[0-9]*'; echo "$version"; } \
    | grep -E '^[0-9]+\.[0-9]+\.[0-9]+$' \
    | sort -uV \
    | grep -B1 -x -F "$version" \
    | grep -v -x -F "$version" || true
)
[[ -n "$prior_version" ]] || { echo "Error: no release tag found before $version" >&2; exit 1; }

# Symmetric difference with --cherry-pick drops fixes that already shipped in a hotfix on the release branch.
# --first-parent keeps one entry per PR, whether it was squashed or merged.
commits() {
  git log --reverse --first-parent --cherry-pick --right-only \
    --perl-regexp --author='^(?!(github-actions|embrace-ci)\[bot\] )' \
    --format='%H' "$prior_version...HEAD"
}

# Merge commits carry the PR title in the body rather than the subject.
format_commit() {
  local subject pr
  subject=$(git log -1 --format='%s' "$1")
  if [[ "$subject" =~ ^Merge\ pull\ request\ \#([0-9]+) ]]; then
    pr="${BASH_REMATCH[1]}"
    subject=$(git log -1 --format='%b' "$1" | sed '/^$/d' | head -n 1)
  elif [[ "$subject" =~ ^(.*)\ \(\#([0-9]+)\)$ ]]; then
    subject="${BASH_REMATCH[1]}"
    pr="${BASH_REMATCH[2]}"
  fi
  if [[ -n "$pr" ]]; then
    echo "- $subject ([#$pr]($repo_url/pull/$pr))"
  else
    echo "- $subject"
  fi
}

changes=()
dependencies=()
while read -r sha; do
  author=$(git log -1 --format='%an' "$sha")
  if [[ "$author" == "dependabot[bot]" ]]; then
    # The example app isn't shipped, so its bumps never belong in the changelog.
    git log -1 --format='%s' "$sha" | grep -q ' in /examples/' || dependencies+=("$(format_commit "$sha")")
  else
    changes+=("$(format_commit "$sha")")
  fi
done < <(commits)

echo "### $version"
echo
echo "_$(date '+%B %-d, %Y')_"
echo
echo "<!--"
echo "Draft generated from commits in $prior_version...HEAD. Before publishing:"
echo "- Rewrite entries as user-facing bullets and merge related ones."
echo "- Remove internal-only changes (tests, CI, refactors, tooling) and the PR links."
echo "- Keep only dependency bumps that matter to SDK consumers, e.g. 'Update OpenTelemetry Java API and SDK to x.y.z'."
echo "-->"
echo
printf '%s\n' "${changes[@]}"
if (( ${#dependencies[@]} )); then
  echo
  echo "#### Dependencies"
  echo
  printf '%s\n' "${dependencies[@]}"
fi
