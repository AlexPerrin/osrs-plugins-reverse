#!/usr/bin/env bash
# Shim: instance leases live in the harness repo (HD_HOME, default ~/osrs-harness/device-lock.sh); same commands.
export HD_CLIENT_ROOT=${HD_CLIENT_ROOT:-$(cd "$(dirname "$(readlink -f "$0")")/.." && pwd)}
exec bash "${HD_HOME:-$HOME/osrs-harness}/device-lock.sh" "$@"
