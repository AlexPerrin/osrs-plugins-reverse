#!/usr/bin/env bash
# The deterministic evaluator between a dev's hand-over and the ship step (docs/harness.md "Hand-over check").
# It checks the claims a hand-over makes, not the feature: the task file, the commit, the evidence, and per track
# the machine checks the dev's check relies on. PASS lets ship.sh run; FAIL goes back to the dev as a task log line.
#
#   tools/harness/handover-check.sh <task-id> [--quiet]
#
# Client: branch exists at the commit, the commit is not on master yet, re/evidence/<id>/ holds a repack log with the
#         7 telemetry: lines and at least one screenshot or logcat extract; a task with network: true has a Network QA
#         report (netqa*.md) there.
# Server: the commit is in the push clone (fetched from the fork), the branch is off upstream main (no other open
#         branch's commits), accuracy/<slug>.md exists on the fork's docs branch, the fork PR is recorded, and the
#         fork's GitHub Actions CI concluded success at exactly that sha (or the task names a local gate log).
set -uo pipefail
ROOT=$(cd "$(dirname "$0")/../.." && pwd)
TASK=${1:?task-id}; QUIET=${2:-}
T=$ROOT/tools/harness/task.py
CLONE=$ROOT/re/upstream-pr/or
FORK=AlexPerrin/OpenRune-Server
fails=0
ok()   { [[ -n $QUIET ]] || echo "  OK   $*"; }
fail() { echo "  FAIL $*"; fails=$((fails + 1)); }
meta() { python3 - "$ROOT/harness/tasks/$TASK.md" "$1" <<'PY'
import re, sys, yaml
text = open(sys.argv[1]).read()
m = re.match(r"---\n(.*?)\n---", text, re.S)
v = (yaml.safe_load(m.group(1)) or {}).get(sys.argv[2])
print(",".join(v) if isinstance(v, list) else ("" if v in (None, False) else v))
PY
}
[[ -f $ROOT/harness/tasks/$TASK.md ]] || { echo "handover-check: no task $TASK"; exit 2; }
HD=${HARNESS_DIR:-$ROOT/harness}
status=$(meta status); track=$(meta track); branch=$(meta branch); commit=$(meta commit)
evidence=$(meta evidence); network=$(meta network); pr=$(meta pr); ci=$(meta ci); gate=$(meta gate)
echo "handover-check $TASK ($track, $status) $branch@${commit:0:9}"
[[ $status == handover ]] && ok "status handover" || fail "status is $status, not handover"
[[ -n $branch && -n $commit ]] || { fail "branch or commit missing"; echo "FAIL"; exit 1; }
grep -q '^- \[ \]' "$ROOT/harness/tasks/$TASK.md" && fail "definition of done has unticked items" || ok "definition of done ticked"
[[ -n $evidence ]] || evidence=re/evidence/$TASK
ev=$ROOT/$evidence; [[ $evidence == /* ]] && ev=$evidence
if [[ -d $ev && -n $(ls -A "$ev" 2>/dev/null) ]]; then ok "evidence dir $evidence ($(ls "$ev" | wc -l) files)"; else fail "evidence dir $evidence missing or empty"; fi

case $track in
client)
    if git -C "$ROOT" rev-parse -q --verify "$commit^{commit}" >/dev/null; then ok "commit exists"; else fail "commit $commit not in this repo"; fi
    if git -C "$ROOT" rev-parse -q --verify "refs/heads/$branch" >/dev/null; then
        tip=$(git -C "$ROOT" rev-parse "refs/heads/$branch")
        [[ $tip == "$commit" ]] && ok "branch $branch is at the commit" || fail "branch $branch is at ${tip:0:9}, not ${commit:0:9}"
    else fail "no local branch $branch"; fi
    git -C "$ROOT" merge-base --is-ancestor "$commit" master 2>/dev/null && fail "commit already on master" || ok "not yet on master"
    rl=$(ls "$ev"/repack*.log "$ev"/*repack*.txt 2>/dev/null | head -1)
    if [[ -n $rl ]]; then
        n=$(grep -c '^telemetry:' "$rl" || true)
        [[ $n -eq 7 ]] && ok "repack log: 7 telemetry lines" || fail "repack log $rl has $n telemetry: lines, not 7"
    else fail "no repack log in $evidence (copy the build's repack output there as repack.log)"; fi
    { compgen -G "$ev/*.png" || compgen -G "$ev/*logcat*.txt"; } >/dev/null && ok "device evidence (screenshot or logcat) present" || fail "no screenshot (*.png) or logcat extract (*logcat*.txt) in $evidence"
    if [[ $network == True || $network == true ]]; then
        compgen -G "$ev/netqa*.md" >/dev/null && ok "Network QA report present" || fail "task is network: true but $evidence has no netqa*.md"
    fi
    ;;
server)
    git -C "$CLONE" fetch -q fork "+refs/heads/$branch:refs/remotes/fork/$branch" 2>/dev/null || true
    if git -C "$CLONE" rev-parse -q --verify "$commit^{commit}" >/dev/null; then ok "commit in the push clone"; else fail "commit $commit not in $CLONE (push the branch to the fork)"; fi
    ftip=$(git -C "$CLONE" rev-parse -q --verify "refs/remotes/fork/$branch" 2>/dev/null || true)
    [[ $ftip == "$commit" ]] && ok "fork/$branch is at the commit" || fail "fork/$branch is at ${ftip:0:9}, not ${commit:0:9}"
    git -C "$CLONE" fetch -q origin main 2>/dev/null || true
    mb=$(git -C "$CLONE" merge-base "$commit" origin/main 2>/dev/null || true)
    if [[ -n $mb ]]; then
        extra=$(git -C "$CLONE" rev-list --count "origin/main..$commit" 2>/dev/null)
        ok "off upstream main ($extra commits on top of $(git -C "$CLONE" rev-parse --short "$mb"))"
    else fail "no merge base with upstream main"; fi
    slug=${branch#*/}
    if git -C "$CLONE" cat-file -e "fork/docs:accuracy/$slug.md" 2>/dev/null || git -C "$CLONE" cat-file -e "docs:accuracy/$slug.md" 2>/dev/null; then
        ok "accuracy/$slug.md on docs"
    else fail "no accuracy/$slug.md on the fork's docs branch (the spec is part of the dev's check)"; fi
    [[ -n $pr ]] && ok "fork PR $pr" || fail "no fork PR recorded (task.py handover --pr <url>)"
    if [[ -n $gate ]]; then
        [[ -f $gate ]] && grep -q 'rc=0' "$gate" && ok "local gate log $gate" || fail "local gate log $gate missing or not rc=0"
    else
        runs=$(gh run list -R "$FORK" --branch "$branch" --limit 20 --json headSha,conclusion,workflowName,url 2>/dev/null)
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
    ;;
*) fail "track $track has no hand-over check" ;;
esac
if (( fails == 0 )); then
    echo "PASS"; "$T" log "$TASK" "handover-check PASS"; exit 0
fi
echo "FAIL ($fails)"; "$T" log "$TASK" "handover-check FAIL ($fails items; see the check output)"; exit 1
