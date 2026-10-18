#!/usr/bin/env python3
"""PreToolUse hook (Bash) for the dev and QA agents (.claude/agents/*.md): denies what only the orchestrator
does (docs/workflow.md "Agents"): pushes, GitHub writes, tools/fork.sh, deploys, device grants and progress
writes. The orchestrator's own session doesn't load this hook.

Reads the hook JSON on stdin; prints a deny decision, or nothing (normal permission flow).
"""
import json
import re
import sys

RULES = [
    (r"\bgit\b[^|;&]*\bpush\b", "pushes"),
    (r"\bgh\s+(pr|issue|release|gist|repo)\s+(create|edit|comment|close|reopen|merge|ready|review|delete|fork|upload)\b",
     "GitHub writes"),
    (r"\bgh\s+api\b[^|;&]*(-X\s*(POST|PATCH|PUT|DELETE)|--method|\s-[fF]\s|--field|--raw-field|--input|\bgraphql\b)",
     "GitHub writes"),
    (r"\btools/fork\.sh\b", "fork pushes and PRs"),
    (r"\bopenrune\.sh\s+deploy\b|--env-file\s+switch\.env\s+up\b", "live server deploys and restarts"),
    (r"\bdevice-lock\.sh\s+grant\b", "device grants"),
    (r"\bprogress\.py\b(?![^|;&]*--dry-run)", "progress writes (use --dry-run)"),
]


def main():
    cmd = json.load(sys.stdin).get("tool_input", {}).get("command", "")
    for pattern, what in RULES:
        if re.search(pattern, cmd):
            print(json.dumps({"hookSpecificOutput": {
                "hookEventName": "PreToolUse",
                "permissionDecision": "deny",
                "permissionDecisionReason": f"Only the orchestrator does {what} (docs/workflow.md). "
                                            "Send the request to the orchestrator instead; don't work around this.",
            }}))
            return


main()
