#!/usr/bin/env bash
# The ship step after a hand-over check PASS (docs/harness.md "Ship"). One command per track, run by the orchestrator
# or the driver (orchestrate.py), never by a dev or QA agent (deny_publish.py denies it there).
#
#   tools/harness/ship.sh <task-id> [--dry-run] [--no-push]
#
# Client: merges the task's branch into master in the main tree (--no-ff, the standard merge message with the dev's
#         check and the Claude co-author line), pushes master to origin main, marks the task shipped. The branch is
#         kept (release notes come from the commits); delete it when the release is out.
# Server: appends the branch to integration.txt on local/integration-tools (push clone), pushes it to the fork,
#         rebuilds `integration` with scripts/build-integration.sh, pushes it, marks the task shipped with the new
#         integration commit, and prints the gate + deploy commands (the gate is a buildq ticket of 8-17 minutes; the
#         deploy needs both instances free). The image build and deploy stay separate commands, since they need the
#         instance reservation.
set -euo pipefail
ROOT=$(cd "$(dirname "$0")/../.." && pwd)
TASK=${1:?task-id}; shift
DRY=; PUSH=1
for a in "$@"; do case $a in --dry-run) DRY=1 ;; --no-push) PUSH= ;; *) echo "ship: unknown option $a" >&2; exit 2 ;; esac; done
T=$ROOT/tools/harness/task.py
CLONE=$ROOT/re/upstream-pr/or
AUTHOR="Alex Perrin <44126061+AlexPerrin@users.noreply.github.com>"
TRAILER="Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
run() { if [[ -n $DRY ]]; then echo "+ $*"; else "$@"; fi; }
meta() { python3 - "$ROOT/harness/tasks/$TASK.md" "$1" <<'PY'
import re, sys, yaml
m = re.match(r"---\n(.*?)\n---", open(sys.argv[1]).read(), re.S)
v = (yaml.safe_load(m.group(1)) or {}).get(sys.argv[2]); print("" if v in (None, False) else v)
PY
}
track=$(meta track); status=$(meta status); branch=$(meta branch); commit=$(meta commit); title=$(meta title); summary=$(meta summary)
[[ $status == handover ]] || { echo "ship: $TASK is $status, not handover"; exit 1; }
last=$(grep -E 'handover-check (PASS|FAIL)' "$ROOT/harness/tasks/$TASK.md" | tail -1)
[[ $last == *PASS* ]] || { echo "ship: the last hand-over check of $TASK is not PASS (run tools/harness/handover-check.sh $TASK)"; exit 1; }

case $track in
client)
    cd "$ROOT"
    [[ $(git branch --show-current) == master ]] || { echo "ship: main tree is not on master"; exit 1; }
    [[ -z $(git status --porcelain --untracked-files=no -- host native tools server) ]] || { echo "ship: the main tree has uncommitted changes in code dirs"; exit 1; }
    [[ $(git rev-parse "refs/heads/$branch") == "$commit" ]] || { echo "ship: $branch moved past the checked commit"; exit 1; }
    msg="Merge $branch: $title (dev's check: built, tried on the device)"
    [[ -n $summary ]] && msg+=$'\n\n'"$summary"
    msg+=$'\n\n'"Task: harness/tasks/$TASK.md"$'\n\n'"$TRAILER"
    run git merge --no-ff --no-edit -m "$msg" "$commit" || { echo "ship: merge failed; resolve by hand, then task.py set $TASK status=shipped"; exit 1; }
    if [[ -z $DRY ]]; then git commit --amend --no-edit --author="$AUTHOR" -q; fi
    [[ -n $PUSH ]] && run git push -q origin master:main
    run "$T" set "$TASK" status=shipped
    run "$T" log "$TASK" "shipped: merged into master $(git rev-parse --short HEAD)${PUSH:+, pushed}"
    echo "shipped $TASK to master"
    ;;
server)
    cd "$CLONE"
    git fetch -q fork
    [[ $(git rev-parse -q --verify "refs/remotes/fork/$branch") == "$commit" ]] || { echo "ship: fork/$branch is not at $commit"; exit 1; }
    wt=$ROOT/re/harness/integration-tools
    if [[ ! -d $wt ]]; then run git worktree add -q -B local/integration-tools "$wt" fork/local/integration-tools; fi
    if [[ -z $DRY ]]; then
        git -C "$wt" checkout -q -B local/integration-tools fork/local/integration-tools
        if grep -qx "$branch" "$wt/integration.txt"; then echo "ship: $branch already in integration.txt"; else
            printf '%s\n' "$branch" >> "$wt/integration.txt"
            git -C "$wt" add integration.txt
            git -C "$wt" commit -q --author="$AUTHOR" -m "integration: add $branch ($TASK)"$'\n\n'"$TRAILER" -- integration.txt
            [[ -n $PUSH ]] && git -C "$wt" push -q fork local/integration-tools:local/integration-tools
        fi
    else
        echo "+ append $branch to integration.txt on local/integration-tools, commit, push to fork"
    fi
    iwt=$ROOT/re/harness/integration
    if [[ ! -d $iwt ]]; then run git worktree add -q --detach "$iwt" fork/main; fi
    run sh -c "cd '$iwt' && git fetch -q fork && sh <(git show fork/local/integration-tools:scripts/build-integration.sh) fork" || { echo "ship: build-integration failed (unrecorded conflict?); see above"; exit 1; }
    if [[ -z $DRY ]]; then
        isha=$(git -C "$iwt" rev-parse HEAD)
        [[ -n $PUSH ]] && git -C "$iwt" push -q fork "HEAD:refs/heads/integration"
        "$T" set "$TASK" status=shipped integration="$isha"
        "$T" log "$TASK" "shipped: in integration ${isha:0:9}${PUSH:+ (pushed)}; gate + deploy pending"
        echo "shipped $TASK into integration ${isha:0:9}"
        echo "next (orchestrator): QA_ROOT=\$ROOT/re/harness/qa openrune/upstream/qa/qa.sh clone integration ${isha:0:9} && qa.sh gate <clone>; boot-check; then deploy with both instances reserved"
    fi
    ;;
*) echo "ship: no ship step for track $track"; exit 1 ;;
esac
