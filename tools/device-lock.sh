#!/usr/bin/env bash
# Shared BlueStacks device lock for the dev agents (and container restarts, which log the
# player out). The orchestrator grants the device to one agent; that agent takes it, uses it,
# and frees it. A grant is single-use, so every session on the device needs a fresh OK.
#
#   tools/device-lock.sh grant <who> [minutes]  orchestrator only: allow <who> to take it once,
#                                               within [minutes] (default 60); one grant per holder,
#                                               so a grant to someone else never replaces it
#   tools/device-lock.sh take  <who> <what...>  fails unless free AND granted to <who> (unexpired)
#   tools/device-lock.sh free  <who>            only the holder can free it
#   tools/device-lock.sh status                 "free" or the holder line (OVERDUE once past the
#                                               granted time), plus pending grants; exit 0 if free
#
# <who> is any short name ("client", "server", "qa", ...). Also overwrites (never appends)
# /tmp/osrs-device-status for anything still reading the old status file.
#
# LOCK_NAME=desktop selects the separate lock for the isolated desktop RuneLite
# (tools/multiclient/desktop.sh); the default is the BlueStacks device.
set -euo pipefail
case "${LOCK_NAME:-device}" in
device)  LOCK=/tmp/osrs-device.lock GRANT=/tmp/osrs-device.grant STATUS=/tmp/osrs-device-status ;;
desktop) LOCK=/tmp/osrs-desktop.lock GRANT=/tmp/osrs-desktop.grant STATUS=/tmp/osrs-desktop-status ;;
*) echo "unknown LOCK_NAME=$LOCK_NAME (device|desktop)"; exit 2 ;;
esac

GRANTS=$GRANT.d   # one file per holder: the epoch second the grant (and then the slot) ends
mkdir -p "$GRANTS"
rm -f "$GRANT"    # the old single-grant file
holder() { cat "$LOCK/owner" 2>/dev/null || echo "(unknown holder)"; }
name_ok() { [[ $1 =~ ^[a-z0-9_-]{1,20}$ ]] || { echo "bad name '$1'"; exit 2; }; }

case "${1:-status}" in
grant)
    who=${2:?who}; name_ok "$who"; mins=${3:-60}
    [[ $mins =~ ^[0-9]+$ ]] || { echo "bad minutes '$mins'"; exit 2; }
    echo $(( $(date +%s) + mins * 60 )) >"$GRANTS/$who"
    echo "granted to $who for ${mins} min"
    ;;
take)
    who=${2:?who}; name_ok "$who"; shift 2; what="$*"
    until=$(cat "$GRANTS/$who" 2>/dev/null || echo 0)
    if (( until <= $(date +%s) )); then
        rm -f "$GRANTS/$who"
        echo "DENIED: no unexpired grant for $who (ask the orchestrator for OK device)"; exit 1
    fi
    if ! mkdir "$LOCK" 2>/dev/null; then
        echo "DENIED: held by $(holder)"; exit 1
    fi
    rm -f "$GRANTS/$who"
    echo "$until" >"$LOCK/until"
    line="taking device ($who dev, $what) $(date) until $(date -d "@$until" +%H:%M)"
    echo "$line" >"$LOCK/owner"
    echo "$line" >"$STATUS"
    echo "$line"
    ;;
free)
    who=${2:?who}
    if [[ ! -d $LOCK ]]; then echo "already free"; exit 0; fi
    if [[ $(holder) != "taking device ($who dev,"* ]]; then
        echo "DENIED: held by $(holder)"; exit 1
    fi
    rm -rf "$LOCK"
    echo "device free, restart ok ($who dev) $(date)" | tee "$STATUS"
    ;;
status)
    now=$(date +%s)
    for g in "$GRANTS"/*; do
        [[ -e $g ]] || continue
        if (( $(cat "$g") > now )); then
            echo "grant pending: $(basename "$g") until $(date -d "@$(cat "$g")" +%H:%M)"
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
*)
    sed -n '2,16p' "$0"; exit 2
    ;;
esac
