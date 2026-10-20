#!/usr/bin/env python3
"""PreToolUse hook (Edit|Write) for the QA companions (client-qa, server-qa): a companion writes its QA records,
its checklist and its scratch files, never the code it checks (docs/workflow.md "Agents").

Records live in qa/ of this repo (client), qa/ of a fork `docs` checkout (server), or openrune/upstream/qa/
(server, until they move to the fork), so the check is on the path's tail, not on one root.
"""
import json
import os
import re
import sys

ALLOWED_TAIL = [
    r"(^|/)qa/[^/]+\.md",           # qa/<slug>.md, qa/client-<version>.md
    r"(^|/)QA\.md",                 # the server QA checklist
    r"(^|/)docs/qa/.+",             # activity briefs and their evidence
]


def main():
    path = os.path.realpath(json.load(sys.stdin).get("tool_input", {}).get("file_path", ""))
    if path.startswith("/tmp/claude-") or any(re.search(p + "$", path) for p in ALLOWED_TAIL):
        return
    print(json.dumps({"hookSpecificOutput": {
        "hookEventName": "PreToolUse",
        "permissionDecision": "deny",
        "permissionDecisionReason": f"A QA companion writes only QA records, QA.md and scratch files, not {path}. "
                                    "Send the fix to the dev agent that owns it (through the orchestrator).",
    }}))


if __name__ == "__main__":
    main()
