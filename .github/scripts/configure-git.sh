#!/bin/bash
set -euo pipefail

# Configure git with embrace-ci bot credentials

git config --global user.name 'embrace-ci[bot]'
git config --global user.email 'embrace-ci@users.noreply.github.com'
