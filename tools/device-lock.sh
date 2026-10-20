#!/usr/bin/env bash
# Test instance locks for the dev and QA agents (and container restarts, which log the player out).
# The orchestrator grants an instance to one agent; that agent takes it, uses it, and frees it.
#
#   tools/device-lock.sh grant <who> [minutes] [reuse]
#                                               orchestrator only: allow <who> to take it within [minutes]
#                                               (default 60); one grant per holder, so a grant to someone else
#                                               never replaces it. A plain grant is single-use; `reuse` keeps
#                                               it until it expires, for a pair given this instance for a task
#   tools/device-lock.sh take  <who> <what...>  fails unless free AND granted to <who> (unexpired)
#   tools/device-lock.sh free  <who>            only the holder can free it
#   tools/device-lock.sh status                 "free" or the holder line (OVERDUE once past the
#                                               granted time), plus pending grants; exit 0 if free
#   tools/device-lock.sh status-all             status of every instance in tools/instances.tsv
#   tools/device-lock.sh env                    the instance's env line (e.g. ANDROID_SERIAL=...)
#
# <who> is the agent's own name (sd23, sq24, cd47), or a role name for older callers. Also overwrites (never
# appends) /tmp/osrs-<instance>-status for anything still reading the old status file.
#
# LOCK_NAME=<instance> selects an instance from tools/instances.tsv; the default is `device` (BlueStacks
# Pie64), `desktop` is the isolated desktop RuneLite (tools/multiclient/desktop.sh).
set -euo pipefail
INSTANCES=$(dirname "$(readlink -f "$0")")/instances.tsv
inst=${LOCK_NAME:-device}
row=$(awk -F'\t' -v n="$inst" '!/^#/ && $1 == n' "$INSTANCES")
if [[ -z $row ]]; then
    echo "unknown LOCK_NAME=$inst (instances: $(awk -F'\t' '!/^#/ && NF {printf "%s ", $1}' "$INSTANCES"))"
    exit 2
fi
LOCK=/tmp/osrs-$inst.lock GRANT=/tmp/osrs-$inst.grant STATUS=/tmp/osrs-$inst-status

GRANTS=$GRANT.d   # one file per holder: "<epoch second the grant ends> [reuse]"
mkdir -p "$GRANTS"
rm -f "$GRANT"    # the old single-grant file
holder() { cat "$LOCK/owner" 2>/dev/null || echo "(unknown holder)"; }
name_ok() { [[ $1 =~ ^[a-z0-9_-]{1,20}$ ]] || { echo "bad name '$1'"; exit 2; }; }

case "${1:-status}" in
grant)
    who=${2:?who}; name_ok "$who"; mins=${3:-60}; mode=${4:-}
    [[ $mins =~ ^[0-9]+$ ]] || { echo "bad minutes '$mins'"; exit 2; }
    [[ -z $mode || $mode == reuse ]] || { echo "bad mode '$mode' (reuse)"; exit 2; }
    echo "$(( $(date +%s) + mins * 60 )) $mode" >"$GRANTS/$who"
    echo "$inst granted to $who for ${mins} min${mode:+ ($mode)}"
    ;;
take)
    who=${2:?who}; name_ok "$who"; shift 2; what="$*"
    until=0; mode=
    [[ -f $GRANTS/$who ]] && read -r until mode <"$GRANTS/$who"
    if (( until <= $(date +%s) )); then
        rm -f "$GRANTS/$who"
        echo "DENIED: no unexpired grant of $inst for $who (ask the orchestrator)"; exit 1
    fi
    if ! mkdir "$LOCK" 2>/dev/null; then
        echo "DENIED: held by $(holder)"; exit 1
    fi
    [[ $mode == reuse ]] || rm -f "$GRANTS/$who"
    echo "$until" >"$LOCK/until"
    line="taking $inst ($who dev, $what) $(date) until $(date -d "@$until" +%H:%M)"
    echo "$line" >"$LOCK/owner"
    echo "$line" >"$STATUS"
    echo "$line"
    ;;
free)
    who=${2:?who}
    if [[ ! -d $LOCK ]]; then echo "already free"; exit 0; fi
    if [[ $(holder) != "taking $inst ($who dev,"* ]]; then
        echo "DENIED: held by $(holder)"; exit 1
    fi
    rm -rf "$LOCK"
    echo "$inst free, restart ok ($who dev) $(date)" | tee "$STATUS"
    ;;
status)
    now=$(date +%s)
    for g in "$GRANTS"/*; do
        [[ -e $g ]] || continue
        until=0; mode=
        read -r until mode <"$g" || true
        if (( until > now )); then
            echo "grant pending: $(basename "$g") until $(date -d "@$until" +%H:%M)${mode:+ ($mode)}"
        else
            rm -f "$g"
        fi
    done
    if [[ -d $LOCK ]]; then
        until=$(cat "$LOCK/until" 2>/dev/null || echo 0)
        if (( until > 0 && until < now )); then echo "OVERDUE: $(holder)"; else holder; fi
        exit 1
    fi
    echo "free"
    ;;
status-all)
    for n in $(awk -F'\t' '!/^#/ && NF {print $1}' "$INSTANCES"); do
        echo "## $n"; LOCK_NAME=$n "$0" status || true
    done
    ;;
env)
    cut -f3 <<<"$row"
    ;;
*)
    sed -n '2,21p' "$0"; exit 2
    ;;
esac
