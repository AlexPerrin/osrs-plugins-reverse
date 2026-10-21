"""Policy as data (BR-P*): harness/policy.yaml, read by the daemon, the hooks and the briefs. `get()` caches the
file per process; tests point HD_POLICY at their own copy."""
import functools
import os

import yaml

from . import policy_path

_cache = {}


def get():
    p = policy_path()
    key = (p, os.path.getmtime(p) if os.path.exists(p) else 0)
    if key not in _cache:
        _cache.clear()
        with open(p) as f:
            _cache[key] = yaml.safe_load(f) or {}
    return _cache[key]


def track(name):
    return get()["tracks"][name]


def tracks():
    return list(get()["tracks"])


def repos():
    return get()["repos"]


def resources():
    return get().get("resources", {})


def role(name):
    return get()["agents"]["roles"][name]


def roles():
    return get()["agents"]["roles"]


def role_for(track_name, stage):
    """The role that executes `stage` on `track_name` (dev -> the dev role, gate -> the QA role)."""
    t = track(track_name)
    if stage == "dev":
        return t["dev_role"]
    if stage == "gate":
        return t.get("qa_role")
    if stage == "train":
        return "integrator"
    return None


def prio_order(p):
    return get().get("priorities", {}).get(p, 9)


def budget(role_name):
    a = get()["agents"]
    return {"usd": a.get("budget_usd", {}).get(role_name), "minutes": a.get("budget_minutes", {}).get(role_name)}


def branch_track(branch):
    """The track a branch name belongs to by its prefix, or None."""
    for name, t in get()["tracks"].items():
        if any(str(branch or "").startswith(p) for p in t.get("branch_prefixes", [])):
            return name
    return None


@functools.lru_cache(maxsize=None)
def instances():
    """Instance rows from tools/instances.tsv: {name: {kind, env, mcp}}."""
    from . import ROOT
    out = {}
    p = os.path.join(ROOT, "tools", "instances.tsv")
    try:
        with open(p) as f:
            for line in f:
                if line.startswith("#") or not line.strip():
                    continue
                w = line.rstrip("\n").split("\t")
                if len(w) >= 2:
                    out[w[0]] = {"kind": w[1], "env": w[2] if len(w) > 2 else "", "mcp": w[3] if len(w) > 3 else ""}
    except OSError:
        pass
    return out
