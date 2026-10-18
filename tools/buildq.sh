#!/usr/bin/env bash
# First-in, first-out queue for heavy build containers shared by all agents (BUILDQ_SLOTS at a time,
# default 2: each build container is capped at 4 GB and WSL has 15 GB; flock alone is not fair).
# Each call takes a ticket, waits until it is among the oldest BUILDQ_SLOTS live tickets, runs the
# command, then gives the slot to the next ticket.
#   tools/buildq.sh <owner> -- <command...>    e.g. tools/buildq.sh acc-genie -- docker run ...
#   tools/buildq.sh status                     the queue, oldest first
# A ticket whose waiting process has died is dropped automatically.
set -euo pipefail
Q=${BUILDQ_DIR:-/tmp/claude-1000/-home-alex-osrs-plugins-reverse/a0087aa1-b8d3-439d-add9-f4cbee8fc5e6/scratchpad/buildq}
mkdir -p "$Q"

live() { # prints the live tickets, oldest first; removes dead ones
    local t pid
    for t in $(ls "$Q" 2>/dev/null | sort); do
        pid=${t#*-}; pid=${pid%%-*}
        if kill -0 "$pid" 2>/dev/null; then echo "$t"; else rm -f "$Q/$t"; fi
    done
}

if [[ ${1:-} == status ]]; then
    for t in $(live); do echo "$t $(cat "$Q/$t")"; done
    exit 0
fi

owner=${1:?owner}; shift
[[ ${1:-} == -- ]] || { echo "usage: buildq.sh <owner> -- <command...>" >&2; exit 2; }
shift
[[ $owner =~ ^[A-Za-z0-9._-]+$ ]] || { echo "buildq: bad owner '$owner'" >&2; exit 2; }

ticket="$(date +%s%N)-$$-$owner"
printf '%s waiting since %s\n' "$*" "$(date +%H:%M:%S)" > "$Q/$ticket"
trap 'rm -f "$Q/$ticket"' EXIT
SLOTS=${BUILDQ_SLOTS:-2}
announced=
while ! live | head -"$SLOTS" | grep -qx -- "$ticket"; do
    if [[ -z $announced ]]; then
        echo "buildq: $owner queued at position $(live | grep -n -- "$ticket" | cut -d: -f1)" >&2
        announced=1
    fi
    sleep 5
done
printf '%s running since %s\n' "$*" "$(date +%H:%M:%S)" > "$Q/$ticket"
"$@"
