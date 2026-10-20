#!/usr/bin/env bash
# The harness tests (hooks, hand-over check, ship's QA path, reap approvals, task store lint). No network, no
# devices; temp repos and task stores only (task.py lint reads the live store, read-only).
#   tools/harness/tests/run.sh [unittest args]
cd "$(dirname "$0")/../../.." && exec python3 -m unittest discover -s tools/harness/tests -v "$@"
