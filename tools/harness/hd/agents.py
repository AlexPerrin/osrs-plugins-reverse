"""Agents are stage executors (BR-G1..G4): one change, one stage, a budget, a fresh context, a brief generated
from the change; the daemon spawns them headless (`claude -p --agent <role>`), records cost on exit and stops any
that linger. Roles: client-dev, server-dev, client-qa, server-qa, integrator, harness-dev (harness/policy.yaml).

Run record `harness/state/runs/<name>.json`:
  {name, role, change, stage, pid, pgid, started, cwd, log, budget: {usd, minutes}, instances, ended, exit,
   turns, minutes, usd, tokens, result}
Agent names: policy `agents.names` prefix + a counter (`harness/state/counters.json`), continuing v2's gens.

  brief(ch, role, stage, instances)   the spawn prompt (the change file, the name, the leases, how to hand over)
  spawn(ch, role, stage, instances)   grant leases, set owner, start `claude -p`; returns the run record.
                                      HD_SPAWN=print writes the brief to the log and records pid None (the
                                      console session spawns it with the Agent tool); tests set HD_SPAWN=fake.
  running()                           run records whose process is alive
  poll()                              runs whose process ended since the last poll: parses the stream-json
                                      result (turns, duration, cost), writes it on the record and the change
                                      (`cost`), emits `agent end`, frees the change's owner; returns them
  stop(name, why)                     SIGTERM the process group, SIGKILL after a grace; emits `agent end`
  lingering(ch_index)                 running agents whose change left the stage they execute (BR-G2)
  over_budget()                       running agents past their minutes budget
Workspace: client and harness roles get a worktree re/harness/wt/<name> (symlinked re/, APKs, openrune/src);
server roles run in ROOT with their own clone re/agents/<name>/or (made by the agent as the role file says).
"""
import os

from . import ROOT, events, leases, policy, store
from .util import iso, now, pid_alive, read_json, write_json

RUNS = "runs"


def _counters_path():
    from . import state_dir
    return os.path.join(state_dir(), "counters.json")


def next_name(role):
    counters = {"cd": 47, "sd": 30, "sq": 30, "cq": 1, "ig": 0, "hd": 0}
    counters.update(read_json(_counters_path(), {}) or {})
    p = policy.get()["agents"]["names"][role]
    counters[p] = counters.get(p, 0) + 1
    write_json(_counters_path(), counters)
    return f"{p}{counters[p]}"


def brief(ch, role, stage, instances):
    raise NotImplementedError


def spawn(ch, role, stage, instances=None):
    raise NotImplementedError


def running():
    return [r for r in store.rec_all(RUNS) if not r.get("ended") and pid_alive(r.get("pid"))]


def poll():
    raise NotImplementedError


def stop(name, why):
    raise NotImplementedError


def lingering(idx):
    raise NotImplementedError


def over_budget():
    raise NotImplementedError
