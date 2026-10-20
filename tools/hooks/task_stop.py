#!/usr/bin/env python3
"""SubagentStop hook for the dev and QA agents: an agent spawned for a harness task can't finish while that task is
still in-progress or specced (docs/harness.md). It must end with `task.py handover|block|fail` (devs) or record its
verdict and `task.py set <id> status=gated|failed` (QA), so the result is on disk, not only in a chat message.

The task ids come from the agent's own transcript (the brief says "Harness task `<id>`"); no id, no check.
"""
import json
import os
import re
import sys

import subprocess

ROOT = os.environ.get("CLAUDE_PROJECT_DIR") or os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
_r = subprocess.run(["git", "-C", ROOT, "rev-parse", "--git-common-dir"], capture_output=True, text=True)
if _r.returncode == 0 and _r.stdout.strip().endswith(".git"):   # a worktree: the store is in the main checkout
    ROOT = os.path.dirname(os.path.abspath(os.path.join(ROOT, _r.stdout.strip())))
TASKS = os.path.join(os.environ.get("HARNESS_DIR") or os.path.join(ROOT, "harness"), "tasks")


def status_of(tid):
    try:
        text = open(os.path.join(TASKS, f"{tid}.md")).read()
    except OSError:
        return None
    m = re.search(r"^status:\s*(\S+)", text, re.M)
    return m.group(1).strip("'\"") if m else None


def main():
    data = json.load(sys.stdin)
    if data.get("stop_hook_active"):
        return
    path = data.get("agent_transcript_path") or data.get("transcript_path")
    if not path or not os.path.isfile(path):
        return
    ids = []
    with open(path, errors="replace") as f:
        for i, line in enumerate(f):
            if i > 50:
                break
            for tid in re.findall(r"Harness task `([a-z0-9][a-z0-9.-]{1,60})`", line):
                if tid not in ids:
                    ids.append(tid)
    open_ = [(t, status_of(t)) for t in ids]
    open_ = [(t, s) for t, s in open_ if s in ("in-progress", "specced")]
    if open_:
        tid, st = open_[0]
        print(json.dumps({"decision": "block", "reason":
            f"Harness task {tid} is still {st}. Before you finish, put the result on disk: tick the definition of done and "
            f"`tools/harness/task.py handover {tid} --branch <b> --commit <sha> --evidence re/evidence/{tid} --summary \"...\"`, "
            f"or `task.py block {tid} --by <task> \"why\"`, or `task.py fail {tid} \"why\"` (QA: `task.py set {tid} "
            f"status=gated|failed` after recording the verdict)."}))


main()
