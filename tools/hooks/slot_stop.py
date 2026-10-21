#!/usr/bin/env python3
"""SubagentStop / Stop hook for the stage executors (harness v3): the end-of-slot gate. An agent cannot finish
while it holds a lease (BR-R2: leases only while in use), or while a device backup it made
(`tools/device-restore-check.sh pre /tmp/osrs-slot/<name>`) has no later passing `check`.

  slot_stop.py <role>     the role is kept for the settings matcher; the agent's name comes from the transcript
                          ("Your name is `<name>`") or HD_AGENT.
"""
import json
import os
import re
import sys

ROOT = os.environ.get("CLAUDE_PROJECT_DIR") or os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
sys.path.insert(0, os.path.join(ROOT, "tools", "harness"))
try:
    from hd import ROOT as MAIN
    sys.path.insert(0, os.path.join(MAIN, "tools", "harness"))
    from hd import leases   # noqa: E402
except Exception:
    leases = None


def block(reason):
    print(json.dumps({"decision": "block", "reason": reason}))
    sys.exit(0)


def mtime(path):
    try:
        return os.path.getmtime(path)
    except OSError:
        return None


def agent_name(data):
    path = data.get("agent_transcript_path") or data.get("transcript_path")
    try:
        with open(path, errors="replace") as f:
            for i, line in enumerate(f):
                if i > 80:
                    break
                m = re.search(r"Your name is `([a-z0-9_-]{1,20})`", line)
                if m:
                    return m.group(1)
    except (OSError, TypeError):
        pass
    return os.environ.get("HD_AGENT") or (sys.argv[1] if len(sys.argv) > 1 else None)


def main():
    data = json.load(sys.stdin)
    if data.get("stop_hook_active"):
        return
    name = agent_name(data)
    if not name:
        return
    slot = f"/tmp/osrs-slot/{name}"
    pre, result = mtime(f"{slot}/settings.properties"), mtime(f"{slot}/result")
    if pre is not None:
        if result is None or result < pre:
            block(f"Your device slot has no restore check yet: run `tools/device-restore-check.sh check {slot}` while you "
                  "still hold the device lease, fix every OFF line, then release the lease.")
        with open(f"{slot}/result") as f:
            if f.read().strip() != "0":
                block(f"The last restore check reported OFF lines: fix them, rerun `tools/device-restore-check.sh check {slot}` "
                      "until it is all OK, then release the lease.")
    if leases is None:
        return
    held = [r for r, st in leases.status_all().items() if st.get("holder") == name]
    if held:
        block("You still hold " + ", ".join(held) + ": restore the instance, then "
              + "; ".join(f"`python3 tools/harness/hd.py lease release {r} {name}`" for r in held) + ".")


main()
