#!/bin/bash
set -euo pipefail

# Create a PR for baseline profile updates

run_id="${1:?Error: run_id required}"
github_ref="${2:?Error: github_ref required}"
github_actor="${3:?Error: github_actor required}"

.github/scripts/configure-git.sh

branch="baseline-profile-${run_id}"
git checkout -b "$branch"

if [[ $(git status --porcelain) ]]; then
    git add embrace-android-sdk/src/main/baseline-prof.txt
    git commit -m "Generate baseline profiles"
    git push -f origin "$branch"

    gh pr create \
        --base main \
        --head "$branch" \
        --title "Generate baseline profile ${github_ref}" \
        --body "Baseline Profiles help guide optimization beyond app startup by providing common user interactions that improve app runtime from the first launch" \
        --assignee "$github_actor"
else
    echo "No changes to commit"
fi
