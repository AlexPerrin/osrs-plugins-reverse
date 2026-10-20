#!/usr/bin/env bash
# The ship step after a hand-over check PASS (docs/harness.md "Ship"). One command per track, run by the orchestrator
# or the driver (orchestrate.py), never by a dev or QA agent (deny_publish.py denies it there).
#
#   tools/harness/ship.sh <task-id> [--dry-run] [--no-push]
#
# Client: merges the task's branch into master in the main tree (--no-ff, the standard merge message with the dev's
#         check and the Claude co-author line), pushes master to origin main, marks the task live (master is the
#         client's live target, with no separate deploy, so dependents unblock at the merge). The branch is kept
#         (release notes come from the commits); delete it when the release is out.
# Server: checks fork/<branch> is the handed-over commit, queues the task for the next integration train
#         (`integrate.py enqueue`: the train adds it to integration.txt, pins the commit in integration.lock,
#         rebuilds `integration` once and pushes it with a lease), and marks the task shipped (not live: not deployed
#         yet). No rebuild here; `orchestrate.py integrate build` runs the train. --no-push is client-only.
set -euo pipefail
ROOT=$(cd "$(dirname "$0")/../.." && pwd)
TASK=${1:?task-id}; shift
DRY=; PUSH=1
for a in "$@"; do case $a in --dry-run) DRY=1 ;; --no-push) PUSH= ;; *) echo "ship: unknown option $a" >&2; exit 2 ;; esac; done
T=$ROOT/tools/harness/task.py
CLONE=${INTEGRATION_CLONE:-$ROOT/re/upstream-pr/or}
AUTHOR="Alex Perrin <44126061+AlexPerrin@users.noreply.github.com>"
TRAILER="Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
run() { if [[ -n $DRY ]]; then echo "+ $*"; else "$@"; fi; }
HD=${HARNESS_DIR:-$ROOT/harness}; export HARNESS_DIR=$HD
TF=$HD/tasks/$TASK.md
meta() { python3 - "$TF" "$1" <<'PY'
import re, sys, yaml
m = re.match(r"---\n(.*?)\n---", open(sys.argv[1]).read(), re.S)
v = (yaml.safe_load(m.group(1)) or {}).get(sys.argv[2]); print("" if v in (None, False) else v)
PY
}
track=$(meta track); status=$(meta status); branch=$(meta branch); commit=$(meta commit); title=$(meta title); summary=$(meta summary)
role=$(meta role)
[[ $status == handover ]] || { echo "ship: $TASK is $status, not handover"; exit 1; }
last=$(grep -E 'handover-check (PASS|FAIL)' "$TF" | tail -1)
[[ $last == *PASS* ]] || { echo "ship: the last hand-over check of $TASK is not PASS (run tools/harness/handover-check.sh $TASK)"; exit 1; }

# A QA task (client-qa, server-qa) verifies a commit; there is nothing to merge. A gate whose PASS leads to an
# action only the user approves (server-qa: the upstream PR; client-qa release-*: the release) goes to `gated`, so
# reap files the approval; any other QA task (a Network QA capture, a check) is done.
if [[ $role == *-qa ]]; then
    if [[ $role == server-qa || $TASK == release-* ]]; then
        run "$T" log "$TASK" "QA gate PASS ($branch@${commit:0:9}); waiting for the user's approval"
        run "$T" set "$TASK" status=gated
        echo "QA gate $TASK -> gated (reap files the approval)"
    else
        run "$T" log "$TASK" "QA task verified ($branch@${commit:0:9}); nothing to ship"
        run "$T" done "$TASK"
        echo "closed QA task $TASK (done; nothing to ship)"
    fi
    exit 0
fi

case $track in
client)
    cd "$ROOT"
    [[ $(git branch --show-current) == master ]] || { echo "ship: main tree is not on master"; exit 1; }
    [[ -z $(git status --porcelain --untracked-files=no -- host native tools server) ]] || { echo "ship: the main tree has uncommitted changes in code dirs"; exit 1; }
    [[ $(git rev-parse "refs/heads/$branch") == "$commit" ]] || { echo "ship: $branch moved past the checked commit"; exit 1; }
    msg="Merge $branch: $title (dev's check: built, tried on the device)"
    [[ -n $summary ]] && msg+=$'\n\n'"$summary"
    msg+=$'\n\n'"Task: harness/tasks/$TASK.md"$'\n\n'"$TRAILER"
    run git merge --no-ff --no-edit -m "$msg" "$commit" || { echo "ship: merge failed; resolve by hand, then task.py set $TASK status=live"; exit 1; }
    if [[ -z $DRY ]]; then git commit --amend --no-edit --author="$AUTHOR" -q; fi
    [[ -n $PUSH ]] && run git push -q origin master:main
    run "$T" set "$TASK" status=live
    run "$T" log "$TASK" "shipped: merged into master $(git rev-parse --short HEAD)${PUSH:+, pushed}"
    echo "shipped $TASK to master (live)"
    ;;
server)
    # Only queue it: the next integration train (tools/harness/integrate.py) adds it to integration.txt, pins it to
    # this commit, and rebuilds and pushes `integration` once for every queued task (docs/integration-pipeline.md).
    cd "$CLONE"
    git fetch -q fork
    [[ $(git rev-parse -q --verify "refs/remotes/fork/$branch") == "$commit" ]] || { echo "ship: fork/$branch is not at $commit"; exit 1; }
    if [[ -n $DRY ]]; then echo "+ integrate.py enqueue $TASK"; else
        python3 "$ROOT/tools/harness/integrate.py" enqueue "$TASK" || { echo "ship: enqueue failed; see above"; exit 1; }
    fi
    run "$T" set "$TASK" status=shipped
    echo "shipped $TASK: queued for the next train"
    echo "next (orchestrator): tools/harness/orchestrate.py integrate build (once the oldest entry is ${INTEGRATION_DEBOUNCE_MIN:-10} min old, or --now)"
    ;;
*) echo "ship: no ship step for track $track"; exit 1 ;;
esac
