#!/usr/bin/env bash
# Instance leases for the agents (harness v3): a thin wrapper over `hd lease` (tools/harness/hd/leases.py), keeping
# the v2 command names the skills use. LOCK_NAME=<instance> selects the resource (default `device`; `desktop`,
# `desktop2`, see tools/instances.tsv).
#
#   tools/device-lock.sh grant <who> [minutes] [reuse]   daemon/orchestrator: allow <who> to take it
#   tools/device-lock.sh take  <who> <what...>           needs a grant; refused while held, draining, or ungranted
#   tools/device-lock.sh free  <who>                     only the holder (or the daemon) releases it
#   tools/device-lock.sh reclaim [why]                   daemon/orchestrator: release an idle hold (an event)
#   tools/device-lock.sh revoke <who>                    drop a pending grant
#   tools/device-lock.sh status | status-all             the lease(s); exit 0 when free
#   tools/device-lock.sh env                             the instance's env line (ANDROID_SERIAL=..., ports)
#   tools/device-lock.sh heartbeat <who>                 "still using it" (the daemon reclaims idle holds)
# Takes and frees are logged in harness/state/events.jsonl. LOCK_DIR (tests) is where the v2 mirror files go.
set -euo pipefail
HERE=$(dirname "$(readlink -f "$0")")
HD="python3 $HERE/harness/hd.py"
INSTANCES=$HERE/instances.tsv
inst=${LOCK_NAME:-device}
export HD_LOCK_DIR=${LOCK_DIR:-${HD_LOCK_DIR:-/tmp}}
case "${1:-status}" in
grant)   who=${2:?who}; mins=${3:-}; mode=${4:-}
         args=(); [[ -n $mins ]] && args+=(--minutes "$mins"); [[ $mode == reuse ]] || args+=(--once)
         exec $HD lease grant "$inst" "$who" "${args[@]}" ;;
take)    who=${2:?who}; shift 2; exec $HD lease take "$inst" "$who" "$*" ;;
free)    who=${2:?who}; exec $HD lease release "$inst" "$who" ;;
reclaim) exec $HD lease reclaim "$inst" harnessd "${2:-reclaimed by hand}" ;;
revoke)  who=${2:?who}; exec $HD lease revoke "$inst" "$who" ;;
heartbeat) who=${2:?who}; exec $HD lease heartbeat "$inst" "$who" ;;
status)  out=$($HD lease status "$inst"); echo "$out"; [[ $out == *" free"* ]] ;;
status-all) exec $HD lease status ;;
env)     awk -F'\t' -v n="$inst" '!/^#/ && $1 == n {print $3}' "$INSTANCES" ;;
*)       sed -n '2,14p' "$0"; exit 2 ;;
esac
