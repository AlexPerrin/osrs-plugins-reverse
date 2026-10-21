"""Self-healing (BR-H2, BR-H3): each tick, repair what drifted and log it; the user hears of it only in the digest.

  run(obs, idx)   -> list of actions taken (strings), each also an event:
    - idle leases past the policy window: reclaimed with the evidence (`lease reclaim`)
    - agents alive past their stage (agents.lingering) or budget: stopped (`agent end`, exit=stopped)
    - runs whose process died without a hand-over: the change goes back to `ready` with a note (BR-S5 style)
    - dead branch servers / stale QA containers (`qa-or-*`, `qa-dev-*` idle > policy) : removed
    - a batch stuck in a step past its timeout: step -> exception with the tail of its log
    - stale approvals (the action already done on GitHub): marked stale (approvals.ensure does it)
    - a deadlock: a change waiting on a lease whose holder waits on a deploy that waits on that lease (BR-H3):
      resolved by draining (leases.drain) and recorded with detect and recover times
  Pure checks are separated from actions so tests can inject (FakeObserver, HD_SPAWN=fake).
"""
from . import agents, events, leases, policy, store


def run(obs, idx):
    raise NotImplementedError
