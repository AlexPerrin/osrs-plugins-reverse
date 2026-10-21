#!/usr/bin/env python3
"""SubagentStop / Stop hook for the stage executors (harness v3): an agent spawned for a change cannot finish while
its result is not on disk. The change slug and the agent's name come from its brief in the transcript
("Harness change `<slug>`", "Your name is `<name>`"; HD_AGENT when spawned headless).

  dev   : the change is still at `dev` with this agent as owner and no hand-over (no commit) and no hold
          -> `hd handover|block|fail`
  gate  : no `gate PASS|FAIL` line by this agent in the change's log -> push the record, then `hd gate <slug> PASS|FAIL`
  train : the batch is still at `exception` without a resolution -> `hd resolve <batch> "note"`
"""
import json
import os
import re
import sys

ROOT = os.environ.get("CLAUDE_PROJECT_DIR") or os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
sys.path.insert(0, os.path.join(ROOT, "tools", "harness"))
try:
    from hd import ROOT as MAIN, store   # noqa: E402
    sys.path.insert(0, os.path.join(MAIN, "tools", "harness"))
    from hd import store   # noqa: E402,F811
except Exception:   # no hd: never block
    store = None

HD = "python3 tools/harness/hd.py"


def block(reason):
    print(json.dumps({"decision": "block", "reason": reason}))
    sys.exit(0)


def transcript_facts(path):
    slug = name = stage = batch = None
    try:
        with open(path, errors="replace") as f:
            for i, line in enumerate(f):
                if i > 80:
                    break
                slug = slug or (re.search(r"Harness change `([a-z0-9][a-z0-9.-]{1,60})`", line) or [None, None])[1]
                name = name or (re.search(r"Your name is `([a-z0-9_-]{1,20})`", line) or [None, None])[1]
                stage = stage or (re.search(r"Stage: `?(dev|gate|train)`?", line) or [None, None])[1]
                batch = batch or (re.search(r"Batch `([a-z0-9][a-z0-9.-]{1,60})`", line) or [None, None])[1]
    except OSError:
        pass
    return slug, name or os.environ.get("HD_AGENT"), stage or "dev", batch


def main():
    data = json.load(sys.stdin)
    if data.get("stop_hook_active") or store is None:
        return
    path = data.get("agent_transcript_path") or data.get("transcript_path")
    if not path or not os.path.isfile(path):
        return
    slug, name, stage, batch = transcript_facts(path)
    if stage == "train" and batch:
        b = store.rec_load("batches", batch) or {}
        if b.get("step") == "exception" and not b.get("resolution"):
            block(f"Batch {batch} is still at exception. Record what you did: `{HD} resolve {batch} \"<what was recorded or dropped>\"`.")
        return
    if not slug or not store.exists(slug):
        return
    ch = store.load(slug)
    if stage == "gate":
        if not any(re.search(rf"gate (PASS|FAIL) .*{re.escape(name or '')}", m) for _, m in ch.log_lines()):
            block(f"Change {slug}: your gate verdict is not on disk. Push the record to the fork's docs branch, then "
                  f"`{HD} gate {slug} PASS` or `{HD} gate {slug} FAIL \"what must change\"`.")
        return
    if ch.stage == "dev" and ch.get("owner") == name and not ch.get("commit") and not ch.hold:
        block(f"Change {slug} is still at dev without a hand-over. Put the result on disk: tick the definition of done, then "
              f"`{HD} handover {slug} --branch <b> --commit <sha> --evidence re/evidence/{slug} --summary \"...\"` "
              f"(--pr/--ci/--repo for a server change), or `{HD} block {slug} --by <other-slug> \"why\"`, or "
              f"`{HD} fail {slug} \"why\"`.")


main()
