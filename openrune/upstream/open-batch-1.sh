#!/usr/bin/env bash
# Opens upstream PR batch 1 (openrune/upstream/PR-BATCH-1.md) from AlexPerrin's fork, and files
# the long-varp compiler issue. Run it yourself; it pushes to GitHub and opens public PRs.
#   openrune/upstream/open-batch-1.sh [PR-texts-dir]
# Needs gh logged in as AlexPerrin and the batch-1 branches in re/upstream-pr/or.
set -euo pipefail
ROOT=$(cd "$(dirname "$0")/../.." && pwd)
CLONE=$ROOT/re/upstream-pr/or
TEXTS=${1:?dir with NN.title/NN.body and issue.title/issue.body}
UP=OpenRune/OpenRune-Server
FORK=AlexPerrin/OpenRune-Server

gh repo view "$FORK" >/dev/null 2>&1 || gh repo fork "$UP" --clone=false
git -C "$CLONE" remote get-url fork >/dev/null 2>&1 || git -C "$CLONE" remote add fork "git@github.com:$FORK.git"
git -C "$CLONE" fetch -q origin main

for n in 01 02 03 04 05 18 43 46 55; do
    br=$(git -C "$CLONE" branch --list "fix/$n-*" --format='%(refname:short)')
    [[ -n $br ]] || { echo "no branch for $n"; exit 1; }
    git -C "$CLONE" merge-base --is-ancestor origin/main "$br" || { echo "$br is behind upstream main; rebase first"; exit 1; }
    git -C "$CLONE" push -q fork "$br"
    if gh pr list -R "$UP" --head "AlexPerrin:$br" --state all --json number --jq '.[0].number' | grep -q .; then
        echo "PR for $br already exists"; continue
    fi
    gh pr create -R "$UP" --base main --head "AlexPerrin:$br" \
        --title "$(cat "$TEXTS/$n.title")" --body-file "$TEXTS/$n.body"
done

gh issue create -R "$UP" --title "$(cat "$TEXTS/issue.title")" --body-file "$TEXTS/issue.body"
