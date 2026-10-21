"""The lease service (BR-R1..R5): every shared resource (instances from tools/instances.tsv, the integration lock,
the live server, the Ghidra writer) is one record `harness/state/leases/<resource>.json`:

  {resource, holder, purpose, since, expiry, heartbeat, pid, container, grants: {name: {until, reuse, purpose}},
   drain: {since, by} | null}

- A grant (the daemon, at dispatch) lets a holder take the resource until `until`; `reuse` keeps the grant for the
  whole stage. The daemon itself (`harnessd`) needs no grant.
- A take refuses when the resource is held by someone else, when the holder has no unexpired grant, or when the
  resource is draining (BR-R4) unless the taker is the daemon.
- A holder heartbeats while using it; `idle()` says whether a held lease shows no use: no live attached pid or
  container and no heartbeat within policy `leases.heartbeat_minutes`, or past its expiry. The daemon reclaims idle
  leases with that evidence (BR-R2), as an event.
- Instances also mirror v2's lock files (`$LOCK_DIR/osrs-<name>.lock/owner`, `osrs-<name>-status`) so tools that
  still read them see the same holder.

Every action is an event (`lease`). HD_LOCK_DIR overrides the mirror dir (tests)."""
import os
import shutil

from . import events, policy, store
from .util import iso, minutes_since, now, parse, pid_alive

DAEMON = "harnessd"


def _rec(resource):
    r = store.rec_load("leases", resource) or {"resource": resource, "holder": None, "grants": {}, "drain": None}
    r.setdefault("grants", {})
    r.setdefault("drain", None)
    r.setdefault("holder", None)
    return r


def _save(r):
    store.rec_save("leases", r["resource"], r)
    _mirror(r)
    return r


def _mirror(r):
    """v2's lock files for instances (slot_stop.py, status readers)."""
    name = r["resource"]
    if name not in policy.instances() and name not in ("device", "desktop", "desktop2"):
        return
    d = os.environ.get("HD_LOCK_DIR") or os.environ.get("LOCK_DIR") or "/tmp"
    lock = os.path.join(d, f"osrs-{name}.lock")
    status = os.path.join(d, f"osrs-{name}-status")
    try:
        if r.get("holder"):
            os.makedirs(lock, exist_ok=True)
            until = parse(r.get("expiry"))
            line = (f"taking {name} ({r['holder']} dev, {r.get('purpose') or ''}) {r.get('since')} until "
                    f"{r.get('expiry')}")
            with open(os.path.join(lock, "owner"), "w") as f:
                f.write(line + "\n")
            with open(os.path.join(lock, "until"), "w") as f:
                f.write(f"{int(until.timestamp()) if until else 0}\n")
            with open(status, "w") as f:
                f.write(line + "\n")
        else:
            shutil.rmtree(lock, ignore_errors=True)
            with open(status, "w") as f:
                f.write(f"{name} free {iso()}\n")
    except OSError:
        pass


def known(resource):
    return resource in policy.resources() or resource in policy.instances()


def default_minutes(resource):
    return int(policy.resources().get(resource, {}).get("default_minutes", 60))


def _prune(r):
    t = now()
    for who in list(r["grants"]):
        u = parse(r["grants"][who].get("until"))
        if u is None or u <= t:
            del r["grants"][who]


def grant(resource, holder, minutes=None, reuse=True, purpose=None, change=None):
    if not known(resource):
        raise ValueError(f"unknown resource {resource}")
    r = _rec(resource)
    _prune(r)
    minutes = minutes or default_minutes(resource)
    until = now().timestamp() + minutes * 60
    r["grants"][holder] = {"until": iso(parse(_ts(until))), "reuse": bool(reuse), "purpose": purpose, "change": change}
    _save(r)
    events.emit("lease", action="grant", resource=resource, holder=holder, purpose=purpose, change=change,
                minutes=minutes)
    return r


def _ts(epoch):
    import datetime as dt
    return dt.datetime.fromtimestamp(epoch, dt.timezone.utc)


def take(resource, holder, purpose="", pid=None, container=None, minutes=None, change=None):
    """(ok, message)."""
    if not known(resource):
        return False, f"unknown resource {resource}"
    r = _rec(resource)
    _prune(r)
    if r.get("holder") and r["holder"] != holder:
        events.emit("lease", action="deny", resource=resource, holder=holder, why=f"held by {r['holder']}")
        return False, f"DENIED: {resource} held by {r['holder']} ({r.get('purpose') or ''}, since {r.get('since')})"
    if r.get("drain") and holder != DAEMON:
        events.emit("lease", action="deny", resource=resource, holder=holder, why="draining")
        return False, f"DENIED: {resource} is draining for a deploy (since {r['drain'].get('since')}); retry after it"
    g = r["grants"].get(holder)
    if holder != DAEMON and not g:
        events.emit("lease", action="deny", resource=resource, holder=holder, why="no grant")
        return False, f"DENIED: no unexpired grant of {resource} for {holder}"
    if g and not g.get("reuse"):
        del r["grants"][holder]
    until = parse(g["until"]) if g else None
    if minutes:
        until = _ts(now().timestamp() + minutes * 60)
    if until is None:
        until = _ts(now().timestamp() + default_minutes(resource) * 60)
    r.update(holder=holder, purpose=purpose, since=iso(), expiry=iso(until), heartbeat=iso(), pid=pid,
             container=container, change=change or (g or {}).get("change"))
    _save(r)
    events.emit("lease", action="take", resource=resource, holder=holder, purpose=purpose, change=r.get("change"))
    return True, f"{resource} taken by {holder} ({purpose}) until {iso(until)}"


def heartbeat(resource, holder, pid=None, container=None):
    r = _rec(resource)
    if r.get("holder") != holder:
        return False, f"{resource} is not held by {holder}"
    r["heartbeat"] = iso()
    if pid:
        r["pid"] = pid
    if container:
        r["container"] = container
    _save(r)
    return True, "ok"


def release(resource, holder):
    r = _rec(resource)
    if not r.get("holder"):
        return True, "already free"
    if r["holder"] != holder and holder != DAEMON:
        return False, f"DENIED: {resource} held by {r['holder']}"
    was = r["holder"]
    r.update(holder=None, purpose=None, since=None, expiry=None, heartbeat=None, pid=None, container=None, change=None)
    _save(r)
    events.emit("lease", action="release", resource=resource, holder=was)
    return True, f"{resource} free"


def reclaim(resource, why):
    r = _rec(resource)
    if not r.get("holder"):
        return True, "already free"
    was = dict(r)
    r.update(holder=None, purpose=None, since=None, expiry=None, heartbeat=None, pid=None, container=None, change=None)
    _save(r)
    events.emit("lease", action="reclaim", resource=resource, holder=was.get("holder"), why=why,
                purpose=was.get("purpose"), since=was.get("since"), heartbeat=was.get("heartbeat"), pid=was.get("pid"))
    return True, f"{resource} reclaimed from {was.get('holder')}: {why}"


def revoke(resource, holder):
    r = _rec(resource)
    if holder in r["grants"]:
        del r["grants"][holder]
        _save(r)
        events.emit("lease", action="revoke", resource=resource, holder=holder)
        return True, f"grant of {resource} revoked for {holder}"
    return True, f"no grant of {resource} for {holder}"


def drain(resource, by=DAEMON):
    r = _rec(resource)
    if not r.get("drain"):
        r["drain"] = {"since": iso(), "by": by}
        _save(r)
        events.emit("lease", action="drain", resource=resource, holder=by)
    return r


def undrain(resource):
    r = _rec(resource)
    if r.get("drain"):
        r["drain"] = None
        _save(r)
        events.emit("lease", action="undrain", resource=resource)
    return r


def status(resource):
    r = _rec(resource)
    _prune(r)
    return r


def status_all():
    names = list(policy.resources())
    for n in policy.instances():
        if n not in names:
            names.append(n)
    return {n: status(n) for n in names}


def holds(resource, holder):
    return _rec(resource).get("holder") == holder


def idle(resource, container_alive=None):
    """(idle, evidence) for a held lease: no attached live process or container and no heartbeat within the policy
    window, or past expiry. `container_alive(name)` is injected (docker)."""
    r = _rec(resource)
    if not r.get("holder"):
        return False, "free"
    hb = policy.get().get("leases", {}).get("heartbeat_minutes", 20)
    if r.get("expiry") and parse(r["expiry"]) and parse(r["expiry"]) < now():
        return True, f"past expiry {r['expiry']}"
    if r.get("pid") and pid_alive(r["pid"]):
        return False, f"pid {r['pid']} alive"
    if r.get("container") and container_alive and container_alive(r["container"]):
        return False, f"container {r['container']} running"
    m = minutes_since(r.get("heartbeat") or r.get("since"))
    if m is not None and m < hb:
        return False, f"heartbeat {m:.0f} min ago"
    return True, (f"no attached process or container, last heartbeat {m:.0f} min ago (limit {hb})" if m is not None
                  else "no heartbeat, no attached process")


def holders_free(resources):
    return all(not _rec(r).get("holder") for r in resources)
