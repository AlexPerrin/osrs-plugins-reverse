#!/usr/bin/env bash
# SessionStart hook (matcher "compact"): after a compaction, print the state the orchestrator works from, so it
# is rebuilt from disk rather than from the summary (docs/harness.md "Orchestrator context"). Read-only, and
# short: the task dashboard, runs, instances and the build queue, with pointers to the STATE docs instead of
# their text (read them when a task needs them).
cd "${CLAUDE_PROJECT_DIR:-$(dirname "$0")/../..}" || exit 0
echo "# State after compaction (tools/hooks/session_state.sh)"
echo; echo "## Harness"; python3 tools/harness/orchestrate.py status 2>&1
echo; echo "## This repo"; git log --oneline -5
echo; echo "Read when needed: docs/orchestrator.md (decisions, waiting on the user), docs/client-state.md,"
echo "the fork's STATE.md (git -C re/upstream-pr/or show fork/docs:STATE.md), harness/approvals/."
exit 0
