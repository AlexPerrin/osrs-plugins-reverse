"""Agents are stage executors (BR-G1..G4): one change, one stage, a budget, a fresh context, a brief generated
from the change; the daemon spawns them headless (`claude -p --agent <role>`), records cost on exit and stops any
that linger. Roles: client-dev, server-dev, client-qa, server-qa, integrator, harness-dev (harness/policy.yaml).

Run record `harness/state/runs/<name>.json`:
  {name, role, change, stage, pid, pgid, started, cwd, log, budget: {usd, minutes}, instances, ended, exit,
   turns, minutes, usd, tokens, result, fake, batch}
Agent names: policy `agents.names` prefix + a counter (`harness/state/counters.json`), continuing v2's gens.

  brief(ch, role, stage, instances)   the spawn prompt (the change file, the name, the leases, how to hand over)
  spawn(ch, role, stage, instances)   grant leases, set owner, start `claude -p`; returns the run record.
                                      HD_SPAWN=print writes the brief to the log and records pid None (the
                                      console session spawns it with the Agent tool); tests set HD_SPAWN=fake.
                                      For stage `train` the record passed is the batch (its id names the change).
  running()                           run records whose process is alive
  poll()                              runs whose process ended since the last poll: parses the stream-json
                                      result (turns, duration, cost), writes it on the record and the change
                                      (`cost`), emits `agent end`, frees the change's owner; returns them
  stop(name, why)                     SIGTERM the process group, SIGKILL after a grace; emits `agent end`
  lingering(ch_index)                 [(run, why)] for agents whose change left the stage they execute (BR-G2),
                                      past policy `linger_grace_minutes`
  over_budget()                       [(run, why)] for agents past their minutes budget
Workspace: client and harness roles get a worktree re/harness/wt/<name> (symlinked re/, APKs, openrune/src);
server roles run in ROOT with their own clone re/agents/<name>/or (made by the agent as the role file says).
HD_WORKTREE=0 skips the worktree (tests, the console).
"""
import json
import os
import signal
import subprocess
import time

from . import ROOT, events, evidence_dir, leases, policy, store
from .util import iso, minutes_since, pid_alive, read_json, write_json

RUNS = "runs"
SHARED = ("re", "openrune/src", "openrune/rsprot")   # git-ignored inputs a worktree shares with the main checkout
HD = "python3 tools/harness/hd.py"
STOP_GRACE_S = 10


def _counters_path():
    from . import state_dir
    return os.path.join(state_dir(), "counters.json")


def _log_path(name):
    from . import state_dir
    return os.path.join(state_dir(), RUNS, f"{name}.log")


def next_name(role):
    counters = {"cd": 47, "sd": 30, "sq": 30, "cq": 1, "ig": 0, "hd": 0}
    counters.update(read_json(_counters_path(), {}) or {})
    p = policy.get()["agents"]["names"][role]
    counters[p] = counters.get(p, 0) + 1
    write_json(_counters_path(), counters)
    return f"{p}{counters[p]}"


def _slug(ch):
    return ch.get("slug") or ch.get("id")


# --- the brief ----------------------------------------------------------------------------------------------------

def _stage_text(ch, stage, name):
    slug, branch = _slug(ch), ch.get("branch") or "<branch>"
    if stage == "dev":
        lines = [f"Stage `dev`: build the change on branch `{branch}` until its definition of done is ticked, then hand",
                 "it over on disk, not in chat:",
                 f"  {HD} handover {slug} --branch {branch} --commit <sha> --evidence {_evidence(ch)} --summary \"...\"",
                 "  (server: add --pr <fork PR url> --ci <CI run url>; --repo <clone> when the commit is in your clone)",
                 "The hand-over check runs from there without you; a FAIL returns the change to ready with the items.",
                 f"Blocked by the other track: file the blocker, then `{HD} block {slug} --by <other-slug> \"why\"`:",
                 f"  {HD} new --track server --slug <x> --title \"...\" --dod \"repro on the test server\" --dod \"...\"",
                 f"Cannot finish: `{HD} fail {slug} \"why\"`."]
        if ch.get("track") == "server":
            lines.append("Definition of done, always (BR-C3): accuracy/<slug>.md on the fork's docs branch; a fork PR with "
                         "green CI at the commit; one change only; off current upstream main.")
    elif stage == "gate":
        what = f"batch `{ch.get('batch')}`" if ch.get("batch") else f"`{branch}` at `{ch.get('commit')}`"
        lines = [f"Stage `gate`: run the gate on {what} (the role file says which gate and how). Push the record to the",
                 f"fork's docs branch, then `{HD} gate {slug} PASS|FAIL \"note\"`. You never write the code you check."]
    else:
        lines = [f"Stage `train`: batch `{slug}` stopped on an exception. Record a resolution (or drop one change with a",
                 f"note) and end with `{HD} resolve {slug} \"note\"`. You never push integration or deploy.",
                 "Exception:", (ch.get("exception") or "(see the batch log)").rstrip()]
    hold = ch.get("hold") or {}
    if hold.get("kind") == "failed":
        lines.append(f"Hold `failed`: {hold.get('reason')}")
        lines.append(f"What must change is above. The fix is a new commit on the SAME branch `{branch}` (BR-C2, BR-S3): no "
                     "second branch, no suffix; hand over again and the hold clears.")
    return lines


def _evidence(ch):
    return os.path.join(evidence_dir(), _slug(ch))


def _lease_text(name, instances):
    if not instances:
        return ["Instances granted to you: none."]
    return [f"Instances granted to you (take without asking): {', '.join(instances)}. Take one only when you start using",
            "it and free it right after. The lease is bound to your process: it counts as in use while you run and is",
            "reclaimed once you end (no heartbeat needed; `lease heartbeat` exists only for a helper outside your process):",
            f"  take with: {HD} lease take <instance> {name} \"<what>\"",
            f"  heartbeat: {HD} lease heartbeat <instance> {name}",
            f"  free: {HD} lease release <instance> {name}"]


RULES = ("Rules (the user's, standing): read game state through the data tools (osrs-devtools, osrs-devtools-desktop, "
         "osrs-mcp), never screenshots unless the user asks; evidence is a logcat extract or a data-tool dump. Keep leases "
         "only while in use. Keep scripts and temp files under `<scratchpad>/<your-name>/`, never at the scratchpad's top "
         "level. Report a denied action, never work around it. Once you have handed over, gated, resolved, blocked or "
         "failed, stop every background job you started, free every lease, send one line and exit; start nothing new "
         "and never wait on CI after the hand-over.")


def brief(ch, role, stage, instances):
    """The spawn prompt. Its first line names the change (hooks find the slug in the transcript); the role file is
    loaded by --agent, so role rules stay out of it."""
    slug, name = _slug(ch), ch.get("owner") or "<name>"
    head = [f"Harness change `{slug}` ({ch.get('track') or 'server'} track, stage `{stage}`). Your name is `{name}`, "
            f"role `{role}`."]
    if isinstance(ch, store.Change):
        head.append(f"Change file: {ch.path} (the change, its definition of done and the log). Read it first.")
    lines = head + _stage_text(ch, stage, name) + _lease_text(name, instances)
    lines.append(f"Evidence goes in {_evidence(ch)}/ (the main checkout; never a scratchpad or a worktree's re/).")
    lines.append(RULES)
    body = getattr(ch, "body", "") or ""
    text = "\n".join(lines) + "\n"
    if body:
        text += "\n" + body.split("## Log")[0].rstrip() + "\n"
    return text


# --- spawn --------------------------------------------------------------------------------------------------------

def workspace(name, role):
    """The agent's cwd: a detached worktree of master for roles with policy `worktree: true` (shared git-ignored inputs
    symlinked from ROOT), else ROOT."""
    if not policy.role(role).get("worktree") or os.environ.get("HD_WORKTREE") == "0":
        return ROOT
    cwd = os.path.join(ROOT, "re", "harness", "wt", name)
    if not os.path.isdir(cwd):
        subprocess.run(["git", "-C", ROOT, "worktree", "add", "-q", "--detach", cwd, "master"], check=True)
    for rel in list(SHARED) + [f for f in os.listdir(ROOT) if f.endswith(".apk")]:
        src, dst = os.path.join(ROOT, rel), os.path.join(cwd, rel)
        if os.path.exists(src) and not os.path.lexists(dst):
            os.makedirs(os.path.dirname(dst), exist_ok=True)
            os.symlink(src, dst)
    return cwd


def _default_instances(ch, role, stage):
    track = ch.get("track") or policy.role(role)["track"]
    t = policy.track(track)
    return list(t.get("qa_instances" if stage == "gate" else "instances") or [])


def _persist(ch):
    if isinstance(ch, store.Change):
        store.save(ch)
    else:
        from .batches import BATCHES
        store.rec_save(BATCHES, ch["id"], ch)


def spawn(ch, role, stage, instances=None):
    slug = _slug(ch)
    name = next_name(role)
    if instances is None:
        instances = _default_instances(ch, role, stage)
    b = policy.budget(role)
    for inst in instances:
        leases.grant(inst, name, minutes=b["minutes"], reuse=True, purpose=f"{stage} {slug}", change=slug)
    ch["owner"], ch["instances"] = name, list(instances)
    if isinstance(ch, store.Change):
        if stage == "dev" and ch.stage == "ready":
            store.set_stage(ch, "dev", f"dispatched to {name}")
        store.add_log(ch, f"run {name} started ({role}, stage {stage}, instances {', '.join(instances) or 'none'})")
    text = brief(ch, role, stage, instances)
    mode = os.environ.get("HD_SPAWN")
    cwd = ROOT if mode else workspace(name, role)
    log = _log_path(name)
    rec = {"name": name, "role": role, "change": slug, "stage": stage, "pid": None, "pgid": None, "started": iso(),
           "cwd": cwd, "log": log, "budget": b, "instances": list(instances), "ended": None, "exit": None}
    if stage == "train" or ch.get("batch"):
        rec["batch"] = slug if stage == "train" else ch.get("batch")
    if mode in ("fake", "print"):
        os.makedirs(os.path.dirname(log), exist_ok=True)
        with open(log, "w") as f:
            f.write(text)
        rec["fake"] = True
        if mode == "print":
            print(f"--- spawn with the Agent tool: subagent_type={role}, name {name} ---\n{text}")
    else:
        a = policy.get()["agents"]
        cmd = ["claude", "-p", "--agent", role, "--permission-mode", a.get("permission_mode", "auto"),
               "--max-budget-usd", str(b["usd"]), "--output-format", "stream-json", "--verbose",
               "--no-session-persistence", text]
        os.makedirs(os.path.dirname(log), exist_ok=True)
        with open(log, "w") as f:
            proc = subprocess.Popen(cmd, cwd=cwd, stdout=f, stderr=subprocess.STDOUT, stdin=subprocess.DEVNULL,
                                    env=dict(os.environ, CLAUDE_PROJECT_DIR=cwd, HD_AGENT=name), start_new_session=True)
        rec["pid"] = rec["pgid"] = proc.pid
    _persist(ch)
    store.rec_save(RUNS, name, rec)
    events.emit("agent", action="start", name=name, role=role, change=slug, stage=stage, pid=rec["pid"])
    return rec


# --- the end of a run ---------------------------------------------------------------------------------------------

def running():
    return [r for r in store.rec_all(RUNS) if not r.get("ended") and pid_alive(r.get("pid"))]


def _open():
    """Runs not ended: alive processes and fake/print runs (which end only through stop())."""
    return [r for r in store.rec_all(RUNS) if not r.get("ended") and (r.get("fake") or pid_alive(r.get("pid")))]


def _result(log):
    """The last stream-json `result` line of a run log; for a run that ended without one (stopped, killed) a
    synthetic result with the usage summed over its assistant messages and `partial: True`, so the usage window
    spent is still counted; None when the log holds nothing."""
    out, usage, turns = None, {}, 0
    try:
        with open(log, errors="replace") as f:
            for line in f:
                if not line.startswith("{"):
                    continue
                try:
                    d = json.loads(line)
                except ValueError:
                    continue
                if d.get("type") == "result":
                    out = d
                elif d.get("type") == "assistant":
                    turns += 1
                    for k, v in ((d.get("message") or {}).get("usage") or {}).items():
                        if isinstance(v, (int, float)):
                            usage[k] = usage.get(k, 0) + v
    except OSError:
        return None
    if out is None and turns:
        out = {"usage": usage, "num_turns": turns, "partial": True, "is_error": True}
    return out


def _tokens(res):
    u = res.get("usage") or {}
    n = sum(int(u.get(k) or 0) for k in ("input_tokens", "output_tokens", "cache_creation_input_tokens",
                                        "cache_read_input_tokens"))
    return n or None


def _free_leases(name):
    for res, r in leases.status_all().items():
        if r.get("holder") == name:
            leases.release(res, name)
        if name in (r.get("grants") or {}):
            leases.revoke(res, name)


def _remove_worktree(r):
    cwd = r.get("cwd")
    if not cwd or cwd == ROOT or os.environ.get("HD_WORKTREE") == "0" or not os.path.isdir(cwd):
        return
    subprocess.run(["git", "-C", ROOT, "worktree", "remove", "--force", cwd], capture_output=True)


def _end(r, exit_):
    """Bookkeeping for a run that is over: the record, the change's cost and owner, leases, worktree, event."""
    res = _result(r["log"]) or {}
    if exit_ is None:
        exit_ = "no-result" if not res or res.get("partial") else "error" if res.get("is_error") else "ok"
    r.update(ended=iso(), exit=exit_, turns=res.get("num_turns"), minutes=round((res.get("duration_ms") or 0) / 60000, 1),
             usd=round(res.get("total_cost_usd") or 0, 2), tokens=_tokens(res), result=(res.get("result") or "")[-500:])
    store.rec_save(RUNS, r["name"], r)
    name, slug = r["name"], r.get("change")
    if slug and store.exists(slug):
        ch = store.load(slug)
        cost = ch.get("cost") or {"runs": 0, "turns": 0, "minutes": 0, "usd": 0}
        cost["runs"] = cost.get("runs", 0) + 1
        cost["turns"] = cost.get("turns", 0) + (r["turns"] or 0)
        cost["minutes"] = round(cost.get("minutes", 0) + r["minutes"], 1)
        cost["usd"] = round(cost.get("usd", 0) + r["usd"], 2)
        ch["cost"] = cost
        if ch.get("owner") == name:
            ch["owner"] = None
        store.add_log(ch, f"run {name} ended: {r['turns'] if r['turns'] is not None else '?'} turns, {r['minutes']} min, "
                          f"${r['usd']}, exit {exit_}")
        store.save(ch)
    events.emit("agent", action="end", name=name, role=r.get("role"), change=slug, stage=r.get("stage"),
                turns=r["turns"], minutes=r["minutes"], usd=r["usd"], tokens=r["tokens"], exit=exit_)
    _remove_worktree(r)
    _free_leases(name)
    return r


def poll():
    out = []
    for r in store.rec_all(RUNS):
        if r.get("ended") or r.get("pid") is None or pid_alive(r["pid"]):
            continue
        out.append(_end(r, None))
    return out


def stop(name, why):
    r = store.rec_load(RUNS, name)
    if not r or r.get("ended"):
        return r
    pid = r.get("pid")
    if pid and pid_alive(pid):
        _kill(r.get("pgid") or pid, pid)
    return _end(r, f"stopped:{why}")


def _kill(pgid, pid):
    for sig in (signal.SIGTERM, signal.SIGKILL):
        try:
            os.killpg(int(pgid), sig)
        except OSError:
            return
        deadline = time.time() + STOP_GRACE_S
        while pid_alive(pid) and time.time() < deadline:
            time.sleep(0.2)
        if not pid_alive(pid):
            return


# --- lingering and budget -----------------------------------------------------------------------------------------

def _batch(bid):
    from .batches import BATCHES
    return store.rec_load(BATCHES, bid) if bid else None


def _left_stage(r, idx):
    """Why a run's subject no longer needs it: (why, when it changed) or None."""
    stage, name = r.get("stage"), r["name"]
    if stage == "train" or (stage == "gate" and r.get("batch")):
        b = _batch(r.get("batch"))
        want = "exception" if stage == "train" else "gating"
        if b is None:
            return "batch record missing", None
        if b.get("step") != want:
            return f"batch at {b.get('step')}", b.get("updated")
        return None
    ch = idx.get(r.get("change"))
    if ch is None:
        return "change missing", None
    if stage == "dev":
        # A dev run executes on a change at dev, or at a later stage under a `failed` hold (BR-S3: the fix is a new
        # commit); it lingers once the change no longer names it as owner (a hand-over, a check or a re-entry
        # cleared it), not merely because the stage is not `dev`.
        if ch.get("owner") != name:
            return f"owner is {ch.get('owner')} (change at {ch.stage})", ch.get("updated")
        return None
    if ch.stage != "live":
        return f"change at {ch.stage}", ch.get("updated")
    if ch.get("gated_commit") or ch.hold == "failed":
        return "gate recorded", ch.get("updated")
    return None


def lingering(idx):
    """Runs whose subject no longer needs them, on the atomic fact alone (the owner field, the batch step), with no
    grace period (feedback-atomic-not-time)."""
    out = []
    for r in _open():
        left = _left_stage(r, idx)
        if left:
            out.append((r, f"lingering: {left[0]}"))
    return out


def over_budget():
    """Nothing: the dollar budget is enforced atomically by `claude --max-budget-usd`; minutes are informational
    (feedback-atomic-not-time), never a reason to stop a working agent."""
    return []
