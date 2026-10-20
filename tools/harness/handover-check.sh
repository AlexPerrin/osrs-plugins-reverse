#!/usr/bin/env bash
# The deterministic evaluator between a hand-over and the ship step (docs/harness.md "Hand-over check").
# It checks the claims a hand-over makes, not the feature: the task file, the commit, the evidence, and per track
# the machine checks the dev's check relies on. PASS lets ship.sh run; FAIL goes back to the dev as a task log line.
#
#   tools/harness/handover-check.sh <task-id> [--quiet]
#
# Every task: status handover, branch and commit set, definition of done ticked, evidence dir non-empty.
# Client dev: the branch exists at the commit, the commit is not on master yet, the evidence holds a repack log with
#         the 7 telemetry: lines and a screenshot or logcat extract; network: true needs a Network QA report
#         (netqa*.md). A docs-only change (every file between merge-base(master, commit) and the commit is *.md) needs
#         neither the repack log nor device evidence.
# QA roles (*-qa): a QA task verifies and ships nothing, so the branch and merge items are skipped; client QA needs
#         the repack log, and a netqa*.md when network: true. ship.sh then marks the task done.
# Server dev: the commit is in the push clone (fetched from the fork), the branch is off upstream main,
#         accuracy/<name>.md exists on the fork's docs branch for <name> = the task's spec: key, the branch slug or the
#         task id; the fork PR is recorded; the fork's CI concluded success at exactly that sha (or gate: names a local
#         gate log with rc=0).
#
# Overrides (tests): HARNESS_DIR (task store), HANDOVER_REPO (client repo, default the main checkout),
# HANDOVER_CLONE (server push clone, default re/upstream-pr/or), HANDOVER_GH (gh command), HANDOVER_EVIDENCE_ROOT
# (base of a relative evidence path, default the main checkout).
set -uo pipefail
ROOT=$(cd "$(dirname "$0")/../.." && pwd)
TASK=${1:?task-id}; QUIET=${2:-}
T=$ROOT/tools/harness/task.py
common=$(git -C "$ROOT" rev-parse --path-format=absolute --git-common-dir 2>/dev/null || true)
MAIN=$ROOT; [[ $common == */.git ]] && MAIN=${common%/.git}   # from a worktree: the main checkout
HD=${HARNESS_DIR:-$MAIN/harness}; export HARNESS_DIR=$HD
TF=$HD/tasks/$TASK.md
REPO=${HANDOVER_REPO:-$MAIN}
CLONE=${HANDOVER_CLONE:-$MAIN/re/upstream-pr/or}
GH=${HANDOVER_GH:-gh}
EVROOT=${HANDOVER_EVIDENCE_ROOT:-$MAIN}
FORK=AlexPerrin/OpenRune-Server
fails=0
ok()   { [[ -n $QUIET ]] || echo "  OK   $*"; }
fail() { echo "  FAIL $*"; fails=$((fails + 1)); }
meta() { python3 - "$TF" "$1" <<'PY'
import re, sys, yaml
text = open(sys.argv[1]).read()
m = re.match(r"---\n(.*?)\n---", text, re.S)
v = (yaml.safe_load(m.group(1)) or {}).get(sys.argv[2])
print(",".join(v) if isinstance(v, list) else ("" if v in (None, False) else v))
PY
}
[[ -f $TF ]] || { echo "handover-check: no task $TASK ($TF)"; exit 2; }
status=$(meta status); track=$(meta track); role=$(meta role); branch=$(meta branch); commit=$(meta commit)
evidence=$(meta evidence); network=$(meta network); pr=$(meta pr); gate=$(meta gate); spec=$(meta spec)
qa=; [[ $role == *-qa ]] && qa=1
echo "handover-check $TASK ($track, ${role:-no role}, $status) $branch@${commit:0:9}"
[[ $status == handover ]] && ok "status handover" || fail "status is $status, not handover"
[[ -n $branch && -n $commit ]] || { fail "branch or commit missing"; echo "FAIL"; exit 1; }
grep -q '^- \[ \]' "$TF" && fail "definition of done has unticked items" || ok "definition of done ticked"
[[ -n $evidence ]] || evidence=re/evidence/$TASK
ev=$EVROOT/$evidence; [[ $evidence == /* ]] && ev=$evidence
if [[ -d $ev && -n $(ls -A "$ev" 2>/dev/null) ]]; then ok "evidence dir $evidence ($(ls "$ev" | wc -l) files)"; else fail "evidence dir $evidence missing or empty"; fi
network_qa() {
    if [[ $network == True || $network == true ]]; then
        compgen -G "$ev/netqa*.md" >/dev/null && ok "Network QA report present" || fail "task is network: true but $evidence has no netqa*.md"
    fi
}
repack_log() {
    local rl n
    rl=$(ls "$ev"/repack*.log "$ev"/*repack*.txt 2>/dev/null | head -1)
    if [[ -n $rl ]]; then
        n=$(grep -c '^telemetry:' "$rl" || true)
        [[ $n -eq 7 ]] && ok "repack log: 7 telemetry lines" || fail "repack log $rl has $n telemetry: lines, not 7"
    else fail "no repack log in $evidence (copy the build's repack output there as repack.log)"; fi
}

case $track in
client)
    if git -C "$REPO" rev-parse -q --verify "$commit^{commit}" >/dev/null; then ok "commit exists"; else fail "commit $commit not in $REPO"; fi
    if [[ -n $qa ]]; then
        ok "QA task ($role): verifies $branch@${commit:0:9}, ships nothing; branch and merge items skipped"
        repack_log
        network_qa
    else
        if git -C "$REPO" rev-parse -q --verify "refs/heads/$branch" >/dev/null; then
            tip=$(git -C "$REPO" rev-parse "refs/heads/$branch")
            [[ $tip == "$commit" ]] && ok "branch $branch is at the commit" || fail "branch $branch is at ${tip:0:9}, not ${commit:0:9}"
        else fail "no local branch $branch"; fi
        git -C "$REPO" merge-base --is-ancestor "$commit" master 2>/dev/null && fail "commit already on master" || ok "not yet on master"
        mb=$(git -C "$REPO" merge-base master "$commit" 2>/dev/null || true)
        files=$([[ -n $mb ]] && git -C "$REPO" diff --name-only "$mb" "$commit" 2>/dev/null)
        if [[ -n $files ]] && ! grep -qv '\.md$' <<<"$files"; then
            ok "docs-only change ($(wc -l <<<"$files") markdown files since master): repack log, device evidence and Network QA not required"
        else
            repack_log
            { compgen -G "$ev/*.png" || compgen -G "$ev/*logcat*.txt"; } >/dev/null && ok "device evidence (screenshot or logcat) present" || fail "no screenshot (*.png) or logcat extract (*logcat*.txt) in $evidence"
            network_qa
        fi
    fi
    ;;
server)
    if [[ -n $qa ]]; then
        ok "QA task ($role): verifies $branch@${commit:0:9}, ships nothing; the record is the gate's evidence"
    else
    git -C "$CLONE" fetch -q fork "+refs/heads/$branch:refs/remotes/fork/$branch" "+refs/heads/docs:refs/remotes/fork/docs" 2>/dev/null \
        || git -C "$CLONE" fetch -q fork "+refs/heads/$branch:refs/remotes/fork/$branch" 2>/dev/null || true
    if git -C "$CLONE" rev-parse -q --verify "$commit^{commit}" >/dev/null; then ok "commit in the push clone"; else fail "commit $commit not in $CLONE (push the branch to the fork)"; fi
    ftip=$(git -C "$CLONE" rev-parse -q --verify "refs/remotes/fork/$branch" 2>/dev/null || true)
    [[ $ftip == "$commit" ]] && ok "fork/$branch is at the commit" || fail "fork/$branch is at ${ftip:0:9}, not ${commit:0:9}"
    git -C "$CLONE" fetch -q origin main 2>/dev/null || true
    mb=$(git -C "$CLONE" merge-base "$commit" origin/main 2>/dev/null || true)
    if [[ -n $mb ]]; then
        extra=$(git -C "$CLONE" rev-list --count "origin/main..$commit" 2>/dev/null)
        ok "off upstream main ($extra commits on top of $(git -C "$CLONE" rev-parse --short "$mb"))"
    else fail "no merge base with upstream main"; fi
    # The spec: the task's spec: key, the branch slug, or the task id (DoD templates name the id).
    found=; tried=()
    for name in "$spec" "${branch#*/}" "$TASK"; do
        [[ -n $name && " ${tried[*]} " != *" $name "* ]] || continue
        tried+=("$name")
        if git -C "$CLONE" cat-file -e "fork/docs:accuracy/$name.md" 2>/dev/null || git -C "$CLONE" cat-file -e "docs:accuracy/$name.md" 2>/dev/null; then
            found=$name; break
        fi
    done
    [[ -n $found ]] && ok "accuracy/$found.md on docs" \
        || fail "no accuracy spec on the fork's docs branch (tried accuracy/{$(IFS=,; echo "${tried[*]}")}.md; the spec is part of the dev's check)"
    if [[ $branch == local/* ]]; then
        ok "local/* branch: never upstream, so no fork PR (fork CI at the commit is the build evidence)"
    else
        [[ -n $pr ]] && ok "fork PR $pr" || fail "no fork PR recorded (task.py handover --pr <url>)"
    fi
    if [[ -n $gate ]]; then
        [[ -f $gate ]] && grep -q 'rc=0' "$gate" && ok "local gate log $gate" || fail "local gate log $gate missing or not rc=0"
    else
        runs=$($GH run list -R "$FORK" --branch "$branch" --limit 20 --json headSha,conclusion,workflowName,url 2>/dev/null)
        if [[ -z $runs ]]; then fail "could not list CI runs for $branch (gh); set gate: <local gate log> instead"; else
            res=$(python3 -c '
import json,sys; runs=json.loads(sys.argv[1]); sha=sys.argv[2]
at=[r for r in runs if r["headSha"]==sha]
ci=[r for r in at if r["workflowName"]=="CI"]
if not at: print("none")
elif not ci: print("noci")
else: print(ci[0]["conclusion"] or "pending", ci[0]["url"])' "$runs" "$commit")
            case $res in
                "success "*) ok "fork CI success at the commit (${res#success })" ;;
                none) fail "no CI run at $commit (gh workflow run ci.yml -R $FORK --ref $branch)" ;;
                noci) fail "runs at the commit, but no CI workflow run" ;;
                *) fail "fork CI at the commit: $res" ;;
            esac
        fi
    fi
    fi
    ;;
*) fail "track $track has no hand-over check" ;;
esac
if (( fails == 0 )); then
    echo "PASS"; "$T" log "$TASK" "handover-check PASS"; exit 0
fi
echo "FAIL ($fails)"; "$T" log "$TASK" "handover-check FAIL ($fails items; see the check output)"; exit 1
