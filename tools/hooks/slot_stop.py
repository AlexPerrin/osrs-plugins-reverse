#!/usr/bin/env python3
"""SubagentStop hook for the dev and QA agents: the end-of-slot gate (docs/workflow.md "Gates").

  slot_stop.py <holder>     holder = the agent's device-lock name: client, client-qa, server, server-qa

The agent can't finish while it holds the device or desktop lock. An agent that backed up the Android device
(`tools/device-restore-check.sh pre /tmp/osrs-slot/<holder>`) also can't finish until a later `check` on that dir
passed (the check writes `<dir>/result`, 0 = all OK). The block reason goes back to the agent as its next
instruction.
"""
import json
import os
import sys

HOLDER = sys.argv[1]
SLOT = f"/tmp/osrs-slot/{HOLDER}"
LOCKS = {"device": "/tmp/osrs-device.lock", "desktop": "/tmp/osrs-desktop.lock"}


def block(reason):
    print(json.dumps({"decision": "block", "reason": reason}))
    sys.exit(0)


def mtime(path):
    try:
        return os.path.getmtime(path)
    except OSError:
        return None


def main():
    if json.load(sys.stdin).get("stop_hook_active"):
        return  # already continuing because of this hook; don't loop
    pre, result = mtime(f"{SLOT}/settings.properties"), mtime(f"{SLOT}/result")
    if pre is not None:
        if result is None or result < pre:
            block(f"Your device slot has no restore check yet: run `tools/device-restore-check.sh check {SLOT}` "
                  "while you still hold the device lock, fix every OFF line, then free the lock.")
        with open(f"{SLOT}/result") as f:
            if f.read().strip() != "0":
                block(f"The last restore check reported OFF lines: fix them, rerun "
                      f"`tools/device-restore-check.sh check {SLOT}` until it is all OK, then free the lock.")
    for name, lock in LOCKS.items():
        try:
            with open(f"{lock}/owner") as f:
                owner = f.read()
        except OSError:
            continue
        if owner.startswith(f"taking device ({HOLDER} dev,"):
            env = "LOCK_NAME=desktop " if name == "desktop" else ""
            block(f"You still hold the {name} lock: restore it, then `{env}tools/device-lock.sh free {HOLDER}`.")


main()
