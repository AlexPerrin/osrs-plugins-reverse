"""Self-healing (BR-H2, BR-H3): each tick, repair what drifted and log it; the user hears of it only in the digest.

  run(obs, idx)   -> list of actions taken (strings), each also an event, in this order:
    1. idle leases past the policy window: reclaimed with the evidence (`lease reclaim`)
    2. agents alive past their stage (agents.lingering) or budget: stopped (`agent end`, exit=stopped)
    3. runs whose process died without a hand-over: the change goes back to `ready` with a note (BR-S5 style)
    4. dead branch servers / stale QA containers (`qa-or-*`, `hd-boot-*` not running): removed (HD_DOCKER
       overrides docker; tests record the command)
    5. a batch whose step process died while gating, imaging or deploying: step -> exception
    6. a deadlock (BR-H3): an image waits for a deploy while an instance is held by an agent whose change depends on
       a change in that batch: the instance drains (leases.drain); the deploy undrains after it
  Stale approvals (the action already done on GitHub) are approvals.ensure's job. A step that raises is an `error`
  event and never stops the next step. Pure checks are separated from actions so tests can inject (FakeObserver,
  HD_SPAWN=fake).
"""
import os
import subprocess

from . import agents, events, leases, policy, store
from .util import iso


def run(obs, idx):
    actions = []
    for step in (_idle_leases, _agents, _dead_runs, _containers, _dead_batches, _deadlock):
        try:
            actions += step(obs, idx)
        except NotImplementedError:
            continue
        except Exception as e:   # one broken repair never stops the others
            events.emit("error", step=step.__name__.lstrip("_"), error=f"{type(e).__name__}: {e}")
    return actions


def _idle_leases(obs, idx):
    def alive(name):
        return (obs.container(name) or {}).get("status") == "running"
    out = []
    for res, r in leases.status_all().items():
        if not r.get("holder"):
            continue
        idle, why = leases.idle(res, container_alive=alive)
        if idle:
            leases.reclaim(res, why)
            out.append(f"lease {res} reclaimed from {r['holder']}: {why}")
    return out


def _agents(obs, idx):
    out = []
    for r, why in agents.lingering(idx) + agents.over_budget():
        agents.stop(r["name"], why)
        out.append(f"agent {r['name']} stopped: {why}")
    return out


def _dead_runs(obs, idx):
    """A change in dev with no hand-over and no live run owning it goes back to ready."""
    live = {r["name"] for r in agents._open()}
    last = {}
    for r in store.rec_all(agents.RUNS):
        if r.get("ended") and r.get("stage") == "dev":
            last[r.get("change")] = r["name"]
    out = []
    for slug, ch in idx.items():
        if ch.stage != "dev" or ch.get("commit") or ch.get("owner") in live:
            continue
        name = ch.get("owner") or last.get(slug) or "?"
        why = f"run {name} ended without a hand-over"
        store.set_stage(ch, "ready", why)
        ch["owner"] = None
        store.save(ch)
        events.emit("stage", change=slug, **{"from": "dev", "to": "ready", "why": why})
        out.append(f"change {slug} back to ready: {why}")
    return out


def _docker(*args):
    cmd = [os.environ.get("HD_DOCKER") or "docker", *args]
    r = subprocess.run(cmd, capture_output=True, text=True, timeout=120)
    return r.returncode == 0, (r.stdout + r.stderr).strip()


def _containers(obs, idx):
    out = []
    for prefix in ("qa-or-", "hd-boot-"):
        for c in obs.containers(prefix) or []:
            st = str(c.get("status") or "")
            if st.lower().startswith("up") or st == "running":
                continue
            ok, text = _docker("rm", "-f", c["name"])
            events.emit("heal", action="container-removed", container=c["name"], status=st, ok=ok, detail=text[-200:])
            out.append(f"container {c['name']} removed ({st})" + ("" if ok else f": {text[-200:]}"))
    return out


def _dead_batches(obs, idx):
    from . import batches
    out = []
    for b in batches.status():
        pid, step = b.get("pid"), b.get("step")
        if step not in ("gating", "imaging", "deploying") or not pid or obs.alive(pid) or b.get("result"):
            continue
        why = f"process {pid} died during {step}"
        b.update(step="exception", exception=why, pid=None)
        b.setdefault("steps", []).append({"step": "exception", "started": iso(), "detail": why})
        store.rec_save(batches.BATCHES, b["id"], b)
        events.emit("batch", batch=b["id"], step="exception", detail=why)
        out.append(f"batch {b['id']} exception: {why}")
    return out


def _deadlock(obs, idx):
    """BR-H3: a pending deploy waits for instances held by agents whose changes wait for that deploy."""
    from . import batches
    b = batches.deploy_ready(obs)
    if not b:
        return []
    included = set(b.get("includes") or [])
    out = []
    for inst in policy.instances():
        r = leases.status(inst)
        if not r.get("holder") or r.get("drain"):
            continue
        ch = idx.get(r.get("change") or "")
        if ch is None:
            ch = next((c for c in idx.values() if c.get("owner") == r["holder"]), None)
        if ch is None or not included.intersection(ch.get("deps") or []):
            continue
        leases.drain(inst)
        detail = f"{inst} held by {r['holder']} for {ch['slug']}, which waits for {b['id']} (deps in the batch)"
        events.emit("heal", action="deadlock", detected=iso(), resource=inst, holder=r["holder"], change=ch["slug"],
                    batch=b["id"], detail=detail)
        out.append(f"deadlock: {detail}; draining {inst}")
    return out
