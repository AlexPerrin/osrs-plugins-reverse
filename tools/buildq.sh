#!/usr/bin/env bash
# Build queue shared by all agents, in classes with their own slots, so one class never starves
# another (FIFO within a class; BUILDQ_PRIO=1 tickets go first within theirs):
#   gate     full builds: QA gates (qa.sh gate, priority) and any other `docker run` build. 2 slots.
#   dev      dev-lane builds (qa.sh dev: an exec into the dev's warm container). 2 slots.
#   measure  timing runs. 1 slot.
# Memory guard: a ticket starts only while MemAvailable minus a ramp reserve (each ticket started in
# the last BUILDQ_RAMP_S=120 s counts as its class's ramp: gate/measure 4096 MB, dev 2048 MB, since
# a new container hasn't grown yet) is at least the ticket's need: BUILDQ_MEM_MB, else per class
# gate/measure 5632 MB (a 5 GB gate container), dev 4608 MB. With nothing running anywhere the
# guard is skipped (it would wait forever on memory held outside the queue).
#   tools/buildq.sh <owner> -- <command...>                 class gate (default)
#   BUILDQ_CLASS=dev BUILDQ_MEM_MB=1024 tools/buildq.sh sd25 -- docker exec ...
#   BUILDQ_PRIO=1 tools/buildq.sh qa -- ...                 priority ticket
#   tools/buildq.sh status                                  the queue, then slots and memory per class
# Slots: BUILDQ_GATE_SLOTS (2), BUILDQ_DEV_SLOTS (2), BUILDQ_MEASURE_SLOTS (1).
# Tickets are files [<class>.][0]<ns>-<pid>-<owner> (gate has no prefix; 0 = priority); a running
# ticket's file says "running since" and its mtime is its start. A ticket whose process died is
# dropped. Replace this file with mv, never edit it in place: waiting bash processes read it as
# they run.
set -euo pipefail
Q=${BUILDQ_DIR:-/tmp/claude-1000/-home-alex-osrs-plugins-reverse/a0087aa1-b8d3-439d-add9-f4cbee8fc5e6/scratchpad/buildq}
mkdir -p "$Q"
RAMP_S=${BUILDQ_RAMP_S:-120}

class_of() { case $1 in dev.*) echo dev ;; measure.*) echo measure ;; *) echo gate ;; esac; }
slots_of() { case $1 in dev) echo "${BUILDQ_DEV_SLOTS:-2}" ;; measure) echo "${BUILDQ_MEASURE_SLOTS:-1}" ;; *) echo "${BUILDQ_GATE_SLOTS:-2}" ;; esac; }
need_of() { case $1 in dev) echo 4608 ;; *) echo 5632 ;; esac; }
ramp_of() { case $1 in dev) echo 2048 ;; *) echo 4096 ;; esac; }
# Queue order: by class prefix, then priority (0) before normal, then age.
order() {
    local t b p
    for t in $(ls "$Q" 2>/dev/null); do
        b=${t#dev.}; b=${b#measure.}; p=1; [[ $b == 0* ]] && { p=0; b=${b#0}; }
        echo "$p $b $t"
    done | sort -k1,1n -k2,2 | awk '{print $3}'
}
live() { # the live tickets in queue order; removes dead ones
    local t pid rest
    for t in $(order); do
        rest=${t#*-}; pid=${rest%%-*}
        if kill -0 "$pid" 2>/dev/null; then echo "$t"; else rm -f "$Q/$t"; fi
    done
}
is_running() { grep -q ' running since ' "$Q/$1" 2>/dev/null; }
mem_avail_mb() { awk '/^MemAvailable:/ {print int($2/1024)}' /proc/meminfo; }

if [[ ${1:-} == status ]]; then
    declare -A run=()
    for t in $(live); do
        echo "$t $(cat "$Q/$t" 2>/dev/null)"
        c=$(class_of "$t"); is_running "$t" && run[$c]=$(( ${run[$c]:-0} + 1 ))
    done
    for c in gate dev measure; do printf 'slots %s: %d/%d running\n' $c "${run[$c]:-0}" "$(slots_of $c)"; done
    echo "MemAvailable $(mem_avail_mb) MB (gate starts at $(need_of gate), dev at $(need_of dev) or BUILDQ_MEM_MB)"
    exit 0
fi

owner=${1:?owner}; shift
[[ ${1:-} == -- ]] || { echo "usage: buildq.sh <owner> -- <command...>" >&2; exit 2; }
shift
[[ $owner =~ ^[A-Za-z0-9._-]+$ ]] || { echo "buildq: bad owner '$owner'" >&2; exit 2; }
CLASS=${BUILDQ_CLASS:-gate}
case $CLASS in gate) cpre= ;; dev|measure) cpre=$CLASS. ;; *) echo "buildq: bad class '$CLASS'" >&2; exit 2 ;; esac
NEED=${BUILDQ_MEM_MB:-${BUILDQ_MIN_MEM_MB:-$(need_of "$CLASS")}}
SLOTS=$(slots_of "$CLASS")

prefix=; [[ ${BUILDQ_PRIO:-0} == 1 ]] && prefix=0
ticket="$cpre$prefix$(date +%s%N)-$$-$owner"
printf '%s waiting since %s\n' "$*" "$(date +%H:%M:%S)" > "$Q/$ticket"
trap 'rm -f "$Q/$ticket"' EXIT
exec 9>"$Q/.lock"   # the start decision is serialised, so two waiters never both take the last slot

# 0 = may start now; prints why not otherwise.
may_start() {
    local t c running=0 any=0 rank=0 seen= now reserve=0 avail
    now=$(date +%s)
    for t in $(live); do
        c=$(class_of "$t")
        if is_running "$t"; then
            any=$((any+1))
            [[ $c == "$CLASS" ]] && running=$((running+1))
            if (( now - $(stat -c %Y "$Q/$t" 2>/dev/null || echo 0) < RAMP_S )); then
                reserve=$((reserve + $(ramp_of "$c")))
            fi
        elif [[ $c == "$CLASS" && -z $seen ]]; then
            rank=$((rank+1)); [[ $t == "$ticket" ]] && seen=1
        fi
    done
    if (( running + rank > SLOTS )); then echo "$CLASS position $rank, $running/$SLOTS $CLASS slots busy"; return 1; fi
    avail=$(( $(mem_avail_mb) - reserve ))
    if (( any > 0 && avail < NEED )); then
        echo "$CLASS position $rank, waiting for memory (${avail} MB available, need $NEED)"; return 1
    fi
    return 0
}

last=
while :; do
    flock 9
    if why=$(may_start); then
        printf '%s running since %s\n' "$*" "$(date +%H:%M:%S)" > "$Q/$ticket"
        flock -u 9
        break
    fi
    flock -u 9
    if [[ $why != "$last" && ( -z $last || $why == *memory* ) ]]; then
        echo "buildq: $owner queued: $why" >&2
    fi
    last=$why
    sleep 5
done
exec 9>&-
"$@"
