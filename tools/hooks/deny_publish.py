#!/usr/bin/env python3
"""PreToolUse hook (Bash) for the dev and QA agents (.claude/agents/*.md): denies what only the orchestrator
does (docs/workflow.md "Agents"): pushes, GitHub writes, tools/fork.sh, deploys, device grants and progress
writes. The orchestrator's own session doesn't load this hook.

`--fork-writes` (server-dev, user 2026-10-19): the dev does its own GitHub work on our fork
(AlexPerrin/OpenRune-Server): pushes to the fork or the local push clone, tools/fork.sh, and gh writes with
`-R AlexPerrin/OpenRune-Server`. Anything naming upstream (OpenRune/*) stays denied. github_scope.py still asks
the user before a push to a fork branch that backs an open upstream PR.

`--docs-writes` (server-qa, user 2026-10-19): the companion pushes its own records to the fork's `docs` branch
(a plain push of a local branch or HEAD onto `docs`, never forced). Nothing else.

Reads the hook JSON on stdin; prints a deny decision, or nothing (normal permission flow).
"""
import json
import re
import sys

RULES = [
    (r"\bgit\b(\s+-\S+(\s+[^-\s]\S*)?)*\s+push\b", "pushes"),   # git [opts] push, not `git stash push`
    (r"\bgh\s+(pr|issue|release|gist|repo)\s+(create|edit|comment|close|reopen|merge|ready|review|delete|fork|upload)\b",
     "GitHub writes"),
    (r"\bgh\s+api\b[^|;&]*(-X\s*(POST|PATCH|PUT|DELETE)|--method|\s-[fF]\s|--field|--raw-field|--input|\bgraphql\b)",
     "GitHub writes"),
    (r"\btools/fork\.sh\b", "fork pushes and PRs"),
    (r"\bopenrune\.sh\s+deploy\b|--env-file\s+switch\.env\s+up\b", "live server deploys and restarts"),
    (r"\bdevice-lock\.sh\s+grant\b", "device grants"),
    (r"\bprogress\.py\b(?![^|;&]*--dry-run)", "progress writes (use --dry-run)"),
]


FORK = re.compile(r"AlexPerrin/OpenRune-Server", re.I)
UPSTREAM = re.compile(r"OpenRune/OpenRune|github\.com[:/]+OpenRune/|\bupstream\b", re.I)


def fork_write(what, cmd):
    """With --fork-writes: is this push / gh write / fork.sh call aimed only at our fork?"""
    if UPSTREAM.search(cmd):
        return False
    segs = re.split(r"[|;&]+", cmd)
    if what == "fork pushes and PRs":
        return True
    if what == "pushes":
        return all(re.search(r"\bpush\b(\s+-\S+)*\s+(fork|origin|\S*AlexPerrin/OpenRune-Server\S*)(\s|$)", seg)
                   for seg in segs if re.search(r"\bgit\b.*\bpush\b", seg))
    if what == "GitHub writes":
        return all(FORK.search(seg) for seg in segs if re.search(r"\bgh\b", seg))
    return False


def docs_write(what, cmd):
    """With --docs-writes: is this a non-forced push of a branch onto the fork's docs branch, and nothing else?"""
    if what != "pushes" or UPSTREAM.search(cmd) or re.search(r"--force|\s-f\b|\s\+", cmd):
        return False
    pushes = [seg for seg in re.split(r"[|;&]+", cmd) if re.search(r"\bgit\b.*\bpush\b", seg)]
    return all(re.search(r"\bpush\s+(fork|origin|\S*AlexPerrin/OpenRune-Server\S*)\s+"
                         r"(docs|(HEAD|[A-Za-z0-9._/-]+):(refs/heads/)?docs)\s*$", seg.strip()) for seg in pushes)


def main():
    fork_ok = "--fork-writes" in sys.argv[1:]
    docs_ok = "--docs-writes" in sys.argv[1:]
    cmd = json.load(sys.stdin).get("tool_input", {}).get("command", "")
    for pattern, what in RULES:
        if re.search(pattern, cmd):
            if fork_ok and fork_write(what, cmd) or docs_ok and docs_write(what, cmd):
                continue
            print(json.dumps({"hookSpecificOutput": {
                "hookEventName": "PreToolUse",
                "permissionDecision": "deny",
                "permissionDecisionReason": f"Only the orchestrator does {what} (docs/workflow.md). "
                                            "Send the request to the orchestrator instead; don't work around this.",
            }}))
            return


main()
