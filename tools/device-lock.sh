#!/usr/bin/env bash
# Shared BlueStacks device lock for the dev agents (and container restarts, which log the
# player out). The orchestrator grants the device to one agent; that agent takes it, uses it,
# and frees it. A grant is single-use, so every session on the device needs a fresh OK.
#
#   tools/device-lock.sh grant <who>            orchestrator only: allow <who> to take it once
#   tools/device-lock.sh take  <who> <what...>  fails unless free AND granted to <who>
#   tools/device-lock.sh free  <who>            only the holder can free it
#   tools/device-lock.sh status                 "free" or the holder line; exit 0 if free
#
# <who> is "client" or "server". Also overwrites (never appends) /tmp/osrs-device-status
# for anything still reading the old status file.
set -euo pipefail
LOCK=/tmp/osrs-device.lock      # directory: mkdir is atomic
GRANT=/tmp/osrs-device.grant
STATUS=/tmp/osrs-device-status

holder() { cat "$LOCK/owner" 2>/dev/null || echo "(unknown holder)"; }

case "${1:-status}" in
grant)
    who=${2:?who}
    echo "$who $(date)" >"$GRANT"
    echo "granted to $who"
    ;;
take)
    who=${2:?who}; shift 2; what="$*"
    granted=$(cut -d' ' -f1 "$GRANT" 2>/dev/null || true)
    if [[ $granted != "$who" ]]; then
        echo "DENIED: no grant for $who (ask the orchestrator for OK device)"; exit 1
    fi
    if ! mkdir "$LOCK" 2>/dev/null; then
        echo "DENIED: held by $(holder)"; exit 1
    fi
    rm -f "$GRANT"
    line="taking device ($who dev, $what) $(date)"
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
    if [[ -d $LOCK ]]; then holder; exit 1; fi
    echo "free"
    ;;
*)
    sed -n '2,13p' "$0"; exit 2
    ;;
esac
