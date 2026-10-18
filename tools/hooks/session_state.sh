#!/usr/bin/env bash
# SessionStart hook (matcher "compact"): after a compaction, print the state the orchestrator works from, so it
# is rebuilt from disk rather than from the summary (docs/workflow.md "State"). Read-only.
cd "${CLAUDE_PROJECT_DIR:-$(dirname "$0")/../..}" || exit 0
F=re/upstream-pr/or
echo "# State after compaction (tools/hooks/session_state.sh)"
echo; echo "## Client: docs/client-state.md"; cat docs/client-state.md
echo; echo "## Server: STATE.md on the fork's docs branch"
git -C "$F" show fork/docs:STATE.md 2>/dev/null || git -C "$F" show docs:STATE.md 2>/dev/null || echo "(not found)"
echo; echo "## Devices"; tools/device-lock.sh status; LOCK_NAME=desktop tools/device-lock.sh status
echo; echo "## Build queue"; tools/buildq.sh status
echo; echo "## This repo"; git log --oneline -10
exit 0
