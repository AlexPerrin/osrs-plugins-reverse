"""The event log (BR-H4): `harness/state/events.jsonl`, append-only, one JSON object per line, UTC. Every stage
change, hold, batch step, lease event, approval event, agent start and end, denial, drift, deploy, re-pin and metric
recount is one line. The dashboard, the KPIs and every post-mortem read it; nothing rewrites it.

  emit(kind, **fields)      append one event; returns it
  read(since=None, kinds=None, change=None, limit=None)   the events, oldest first
  tail(n)                   the last n

Kinds (the `kind` field; `data` holds the rest):
  stage      change moved: from, to, commit, why
  hold       change held or released: hold (blocked|decision|failed|exception|none), reason
  check      hand-over check: result (PASS|FAIL), items
  batch      a train or release step: batch, step (queued|pinning|rebuilding|gating|imaging|deploying|live|
             exception|failed|rolled-back), detail
  lease      resource lease: action (grant|take|heartbeat|release|reclaim|expire|drain|undrain|deny), resource,
             holder, purpose, why
  agent      start|end: name, role, change, stage, pid; on end: turns, minutes, usd, tokens, exit
  approval   created|approved|denied|done|stale|failed: approval, kind, change
  denial     a hook or classifier denied an action: source, rule, command (truncated)
  drift      record != observation: change, field, recorded, observed
  deploy     image, integration, result (ok|rollback), seconds
  repin      upstream main moved: from, to, dropped
  metric     a recount: name, value, total, pct
  tick       one driver tick: n, seconds, actions
  error      a step raised: step, error
  note       free text (hd note)

HD_EVENTS overrides the file (tests)."""
import json
import os

from . import state_dir
from .util import iso, parse


def path():
    return os.environ.get("HD_EVENTS") or os.path.join(state_dir(), "events.jsonl")


def emit(kind, **fields):
    ev = {"ts": iso(), "kind": kind}
    ev.update({k: v for k, v in fields.items() if v is not None})
    p = path()
    os.makedirs(os.path.dirname(p), exist_ok=True)
    with open(p, "a") as f:
        f.write(json.dumps(ev, sort_keys=True) + "\n")
    return ev


def read(since=None, kinds=None, change=None, limit=None):
    out = []
    since = parse(since) if since else None
    try:
        with open(path()) as f:
            for line in f:
                line = line.strip()
                if not line:
                    continue
                try:
                    ev = json.loads(line)
                except ValueError:
                    continue
                if kinds and ev.get("kind") not in kinds:
                    continue
                if change and ev.get("change") != change:
                    continue
                if since and (parse(ev.get("ts")) or since) < since:
                    continue
                out.append(ev)
    except OSError:
        return []
    return out[-limit:] if limit else out


def tail(n=20):
    return read(limit=n)
