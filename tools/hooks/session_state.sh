#!/usr/bin/env bash
# Shim: the harness lives in HD_HOME (default ~/osrs-harness).
exec bash "${HD_HOME:-$HOME/osrs-harness}/hooks/session_state.sh" "$@"
