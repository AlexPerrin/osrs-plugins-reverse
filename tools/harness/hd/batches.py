"""Batches (BR-B1..B6): trains (server) and releases (client). Record `harness/state/batches/<id>.json`:
  {id, kind: train|release, includes: [slugs], entries: [{slug, branch, commit, queued}], step,
   steps: [{step, started, ended, rc, detail}], integration (sha), tools (sha), gate_dir, image (tag), image_id,
   rollback, pid, log, evidence, commit (release: master HEAD), created, exception}
Train steps: queued -> pinning -> rebuilding -> gating -> imaging -> imaged (waiting for the deploy) -> deploying
-> live | exception | failed.
Release steps: queued -> gating -> gated -> published (the user's approval) | failed.

  enqueue(ch)                   a checked server change joins the queue (`harness/state/queue.json`; a newer
                                commit of the same branch replaces its entry); idempotent
  advance(obs, now=False)       one step of every open batch, never blocking: starts a train when the oldest
                                entry is past policy `batches.train_debounce_minutes` (or `now`, or a re-pin asked
                                for one), the `integration` lease is free and no train waits at `exception`; the
                                rebuild (integrate.py's helpers:
                                pins, the tools commit, build-integration.sh, the lease push) runs synchronously
                                (seconds); gate (`qa.sh clone` + `qa.sh gate` through buildq), image
                                (tools/harness/image.sh) and deploy (tools/harness/deploy.sh) run as background
                                processes recorded on the batch (pid, log) and are polled on later ticks; a
                                deploy is tried whenever an image waits and the daemon holds the leases.
                                Returns a list of actions taken (strings).
  deploy_ready(obs)             the newest imaged train waiting for a deploy, or None
  deploy(batch, obs)            BR-B3: needs every instance and `live-server` leased by the daemon (the tick drains
                                and takes them first; this only checks); runs deploy.sh in the background;
                                (started, message). On "Server ready in" the next advance() marks the included
                                changes live (and older imaged trains this integration carries), writes live.json
                                (integration, image, image_id, rollback, since, upstream_main), releases and
                                undrains the leases and emits `deploy`; on timeout deploy.sh rolled back: `exception`
  on_exception(batch, text)     records the stop output, frees the integration lease; the tick spawns an
                                `integrator` with it (BR-B4)
  resume(batch_id)              after the integrator recorded a resolution (or dropped a branch from
                                integration.txt): re-run from the failed step
  repin_due(obs)                BR-B5: upstream main moved past live.json's upstream_main (or a pinned change
                                merged upstream) and no re-pin to that tip was run yet: the sha, else None
  repin(obs)                    fast-forward the fork's main (tools/fork.sh sync), drop branches whose pin is in
                                upstream main from integration.txt and integration.lock (a merged change -> stage
                                merged on the next derive), start a train now (rebuild on the new main, even with
                                an empty queue; deferred to the next advance() while a train runs)
  release_due(idx)              BR-B6: the live client slugs not yet in a release batch when they number
                                policy `batches.release_after_live_changes`, or when `harness/state/release.request`
                                exists; None when no release is due (or one is open)
  new_release(slugs)            a release batch: gates master at HEAD with client-qa (the tick spawns it)
  status()                      open batches with their step and timings (the dashboard)
  wait_step(batch, timeout)     block until the batch's background step ends (tests, `hd batch wait`)
Scripts: tools/harness/image.sh <gated-clone> <batch> (stages server/ + data/ without key files, cache fixes,
template tools/harness/Dockerfile.integration with COPY --from the live image for the key and game.yml, key-hash
compare printing only match/MISMATCH, private boot check, record harness/state/images/<sha7>.json);
tools/harness/deploy.sh <batch> (image record ok, all instances held by harnessd, rollback tag, retag :241,
`docker compose --env-file switch.env up -d --no-deps openrune` in openrune/, wait for "Server ready in",
auto-rollback on timeout; never --build; record harness/state/deploys/<batch>.json).
Environment (tests): HD_QA_SH, HD_IMAGE_SH, HD_DEPLOY_SH, HD_FORK_SH, HD_DOCKER (passed to the scripts as DOCKER),
HD_QA_ROOT, HD_OURS (the client repo); the fork clone, its remote and the tools worktree come from integrate.py's
INTEGRATION_CLONE, INTEGRATION_REMOTE, INTEGRATION_TOOLS_WT. Locks: `harness/state/batches.lock` byte 0 guards the
queue, byte 1 the in-process train; the `integration` lease is the one-train-at-a-time lock (BR-B2).
"""
import contextlib
import fcntl
import os
import re
import shlex
import signal
import subprocess
import sys

from . import ROOT, events, evidence_dir, leases, policy, state_dir, store
from .util import iso, minutes_since, now, parse, pid_alive, read_json, write_json

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
import integrate  # noqa: E402  (the v2 train helpers: pins, lists, the tools commit, rebuild, the lease push)

BATCHES = "batches"
TOOLS_BRANCH = integrate.TOOLS_BRANCH
OPEN_STEPS = ("queued", "pinning", "rebuilding", "gating", "imaging", "imaged", "deploying", "gated")
BACKGROUND = ("gating", "imaging", "deploying")
TERMINAL = ("live", "published", "failed")
_PROCS = {}      # pid -> Popen of a background step started by this process (reaped on poll)
_FDS = {}
_HOLDING = set()


# --- configuration ---------------------------------------------------------------------------------------------------

def _tool(env, *rel):
    return os.environ.get(env) or os.path.join(ROOT, *rel)


def qa_sh():
    return _tool("HD_QA_SH", "openrune", "upstream", "qa", "qa.sh")


def image_sh():
    return _tool("HD_IMAGE_SH", "tools", "harness", "image.sh")


def deploy_sh():
    return _tool("HD_DEPLOY_SH", "tools", "harness", "deploy.sh")


def fork_sh():
    return _tool("HD_FORK_SH", "tools", "fork.sh")


def qa_root():
    return _tool("HD_QA_ROOT", "re", "harness", "qa")


def ours():
    return os.environ.get("HD_OURS") or ROOT


def build_wt():
    return os.environ.get("HD_BUILD_WT") or os.path.join(state_dir(), "build")


def _cfg(key, default):
    return policy.get().get("batches", {}).get(key, default)


def _deploy_resources():
    return list(policy.instances()) + ["live-server"]


# --- locks and the queue ---------------------------------------------------------------------------------------------

def _fd():
    """One descriptor per process and state dir: POSIX record locks are per process."""
    p = os.path.join(state_dir(), "batches.lock")
    if p not in _FDS:
        os.makedirs(state_dir(), exist_ok=True)
        _FDS[p] = os.open(p, os.O_RDWR | os.O_CREAT, 0o644)
    return _FDS[p]


@contextlib.contextmanager
def queue_lock():
    fd = _fd()
    fcntl.lockf(fd, fcntl.LOCK_EX, 1, 0)
    try:
        yield
    finally:
        fcntl.lockf(fd, fcntl.LOCK_UN, 1, 0)


def try_train_lock():
    """The in-process train lock (byte 1) without waiting; False when another process rebuilds now."""
    if state_dir() in _HOLDING:
        return False
    try:
        fcntl.lockf(_fd(), fcntl.LOCK_EX | fcntl.LOCK_NB, 1, 1)
    except OSError:
        return False
    _HOLDING.add(state_dir())
    return True


def release_train_lock():
    _HOLDING.discard(state_dir())
    fcntl.lockf(_fd(), fcntl.LOCK_UN, 1, 1)


def queue_path():
    return os.path.join(state_dir(), "queue.json")


def read_queue():
    q = read_json(queue_path(), {}) or {}
    q.setdefault("entries", [])
    return q


def write_queue(q):
    write_json(queue_path(), q)


def enqueue(ch):
    branch, commit = ch.get("branch"), str(ch.get("commit") or "")
    if ch.get("track") != "server":
        raise ValueError(f"{ch.get('slug')} is not a server change")
    if not branch or len(commit) < 40:
        raise ValueError(f"{ch.get('slug')} has no branch and full commit")
    with queue_lock():
        q = read_queue()
        same = [e for e in q["entries"] if e["branch"] == branch]
        if same and same[0]["commit"] == commit and same[0]["slug"] == ch.slug:
            return same[0]
        q["entries"] = [e for e in q["entries"] if e["branch"] != branch]
        entry = {"slug": ch.slug, "branch": branch, "commit": commit, "queued": iso()}
        q["entries"].append(entry)
        write_queue(q)
    note = " (replaces " + ", ".join(f"{e['slug']}@{e['commit'][:9]}" for e in same) + ")" if same else ""
    events.emit("batch", step="queued", change=ch.slug, detail=f"{branch}@{commit[:9]}{note}")
    return entry


def _drain(entries):
    with queue_lock():
        q = read_queue()
        gone = {(e["branch"], e["commit"]) for e in entries}
        q["entries"] = [e for e in q["entries"] if (e["branch"], e["commit"]) not in gone]
        write_queue(q)


def _due(now_):
    """(entries, why): the queue when a train may start."""
    with queue_lock():
        entries = list(read_queue()["entries"])
    if now_ or _repin_pending():
        return entries, ""
    if not entries:
        return [], "queue empty"
    age = minutes_since(min(parse(e["queued"]) for e in entries))
    debounce = float(_cfg("train_debounce_minutes", 10))
    if age < debounce:
        return [], f"debounce: oldest entry {age:.0f} min old, train at {debounce:.0f}"
    return entries, ""


# --- batch records ---------------------------------------------------------------------------------------------------

def load(bid):
    return store.rec_load(BATCHES, bid)


def _save(b):
    return store.rec_save(BATCHES, b["id"], b)


def _all():
    return store.rec_all(BATCHES)


def open_batches(kind=None):
    return [b for b in _all() if b.get("step") not in TERMINAL and (kind is None or b.get("kind") == kind)]


def _new_id(kind):
    prefix = "int" if kind == "train" else "rel"
    day = now().strftime("%Y%m%d")
    n = 1 + sum(1 for b in _all() if str(b["id"]).startswith(f"{prefix}-{day}-"))
    return f"{prefix}-{day}-{n}"


def _new(kind, includes, **extra):
    bid = _new_id(kind)
    b = {"id": bid, "kind": kind, "includes": list(includes), "step": None, "steps": [], "integration": None,
         "tools": None, "gate_dir": None, "image": None, "image_id": None, "rollback": None, "pid": None,
         "log": None, "evidence": os.path.join(evidence_dir(), bid), "created": iso(), "exception": None}
    b.update(extra)
    return _step(b, "queued")


def _close(b, rc=None, detail=None):
    """End the open step entry; returns its duration in seconds."""
    if not b["steps"] or b["steps"][-1].get("ended"):
        return None
    s = b["steps"][-1]
    s["ended"] = iso()
    if rc is not None:
        s["rc"] = rc
    if detail:
        s["detail"] = detail
    return int((parse(s["ended"]) - parse(s["started"])).total_seconds())


def _step(b, step, detail=None, rc=None):
    """Move a batch to `step` (closing the one before it with `rc`), save it and emit the `batch` event."""
    seconds = _close(b, rc)
    t = iso()
    entry = {"step": step, "started": t, "ended": t if step in TERMINAL + ("exception",) else None, "rc": rc,
             "detail": detail}
    b["steps"].append(entry)
    b["step"] = step
    _save(b)
    events.emit("batch", batch=b["id"], batch_kind=b["kind"], step=step, detail=detail, seconds=seconds)
    return b


def _failed_step(b):
    """The step a stopped batch was in before `exception` or `failed`."""
    for s in reversed(b.get("steps") or []):
        if s["step"] not in ("exception", "failed"):
            return s["step"]
    return "queued"


def _release_train(b):
    if b.get("kind") == "train" and leases.holds("integration", leases.DAEMON):
        leases.release("integration", leases.DAEMON)


def _release_deploy():
    for r in _deploy_resources():
        if leases.holds(r, leases.DAEMON):
            leases.release(r, leases.DAEMON)
        leases.undrain(r)


def _fail(b, text):
    """A retryable stop (a refused push): `failed`."""
    b["exception"] = text
    _step(b, "failed", detail=text, rc=1)
    _release_train(b)
    return b


def on_exception(batch, text):
    b = batch if isinstance(batch, dict) else load(batch)
    b["exception"] = text
    _step(b, "exception", detail=text, rc=1)
    if _failed_step(b) == "deploying":
        _release_deploy()
    _release_train(b)
    return b


def status():
    out = []
    for b in open_batches():
        s = b["steps"][-1] if b.get("steps") else {}
        out.append({"id": b["id"], "kind": b["kind"], "step": b.get("step"), "includes": b.get("includes") or [],
                    "integration": b.get("integration"), "image": b.get("image"), "since": s.get("started"),
                    "minutes": minutes_since(s.get("started")), "exception": b.get("exception"),
                    "steps": [(x["step"], x.get("started"), x.get("ended"), x.get("rc")) for x in b.get("steps") or []]})
    return out


# --- background steps ------------------------------------------------------------------------------------------------

def _env():
    env = dict(os.environ, HD_STATE_DIR=state_dir(), HD_EVIDENCE_DIR=evidence_dir(),
               HD_IMAGE_TIMEOUT=str(int(_cfg("image_timeout_minutes", 30)) * 60),
               HD_DEPLOY_TIMEOUT=str(int(_cfg("deploy_ready_timeout_seconds", 300))))
    if os.environ.get("HD_DOCKER"):
        env["DOCKER"] = os.environ["HD_DOCKER"]
    return env


def _spawn(b, step, cmd, log):
    """Start `cmd` (bash -c) detached, its output appended to `log`; record pid and log; step -> `step`."""
    os.makedirs(os.path.dirname(log), exist_ok=True)
    with open(log, "ab") as f:
        p = subprocess.Popen(["bash", "-c", cmd], stdout=f, stderr=subprocess.STDOUT, stdin=subprocess.DEVNULL,
                             start_new_session=True, cwd=ROOT, env=_env())
    _PROCS[p.pid] = p
    b["pid"], b["log"] = p.pid, log
    return _step(b, step, detail=f"pid {p.pid}: {cmd} (log {log})")


def _done(b):
    pid = b.get("pid")
    p = _PROCS.get(pid)
    if p is not None:
        if p.poll() is None:
            return False
        _PROCS.pop(pid, None)
        return True
    return not pid_alive(pid)


def wait_step(batch, timeout=60):
    b = batch if isinstance(batch, dict) else load(batch)
    p = _PROCS.get(b.get("pid"))
    if p is not None:
        p.wait(timeout)
    return load(b["id"])


def _kill(b):
    try:
        os.killpg(int(b["pid"]), signal.SIGTERM)
    except (OSError, TypeError, ValueError):
        pass


def _tail(path, n=15):
    text = ""
    try:
        with open(path, errors="replace") as f:
            text = f.read()
    except OSError:
        pass
    return integrate.tail(text, n)


def _timeout_minutes(step):
    if step == "gating":
        return float(_cfg("gate_timeout_minutes", 60))
    if step == "imaging":
        return float(_cfg("image_timeout_minutes", 30))
    return float(_cfg("deploy_ready_timeout_seconds", 300)) / 60 * 2 + 5


def _summary_rc(gate_dir):
    """The rc of a qa.sh gate from `<dir>.logs/summary` (its last `rc=N`); None when it did not finish."""
    rc = None
    try:
        with open(gate_dir + ".logs/summary") as f:
            for m in re.finditer(r"rc=(\d+)", f.read()):
                rc = int(m.group(1))
    except OSError:
        return None
    return rc


def _poll(b, obs):
    """A background step: timeout, or its result when the process ended. Returns an action string or None."""
    step = b["step"]
    if not _done(b):
        elapsed = minutes_since(b["steps"][-1]["started"]) or 0
        if elapsed > _timeout_minutes(step):
            _kill(b)
            on_exception(b, f"{step} timed out after {elapsed:.0f} min (pid {b['pid']} killed)\n{_tail(b['log'])}")
            return f"{b['id']}: {step} timed out -> exception"
        return None
    if step == "gating":
        rc = _summary_rc(b["gate_dir"])
        if rc != 0:
            on_exception(b, f"gate rc={rc} ({b['gate_dir']}.logs/summary):\n{_tail(b['log'])}")
            return f"{b['id']}: gate rc={rc} -> exception"
        _start_image(b)
        return f"{b['id']}: gate passed, image started"
    if step == "imaging":
        rec = store.rec_load("images", b["integration"][:7])
        if not rec or rec.get("key_hash") != "match" or rec.get("boot") != "ok":
            why = ("no image record" if not rec else
                   f"key hash {rec.get('key_hash')}, boot {rec.get('boot')}")
            on_exception(b, f"image failed: {why}\n{_tail(b['log'])}")
            return f"{b['id']}: image failed -> exception"
        b.update(image=rec.get("image"), image_id=rec.get("image_id"))
        _step(b, "imaged", detail=f"{rec.get('image')} ready in {rec.get('seconds')} s; waiting for the leases", rc=0)
        return f"{b['id']}: imaged {rec.get('image')}"
    if step == "deploying":
        rec = store.rec_load("deploys", b["id"])
        if not rec or rec.get("result") != "ok":
            events.emit("deploy", batch=b["id"], image=b.get("image"), integration=b.get("integration"),
                        result="rollback", seconds=(rec or {}).get("seconds"))
            on_exception(b, f"deploy {(rec or {}).get('result') or 'wrote no record'}; the live server runs "
                            f"{(rec or {}).get('rollback_tag') or 'the previous image'}\n{_tail(b['log'])}")
            return f"{b['id']}: deploy rolled back -> exception"
        _go_live(b, rec, obs)
        return f"{b['id']}: live ({b['image']})"
    return None


# --- the train ---------------------------------------------------------------------------------------------------------

def _tools_checkout():
    """The local/integration-tools worktree at the fork's tip; raises GitError when it has local changes."""
    c, r_, wt = integrate.clone(), integrate.remote(), integrate.tools_wt()
    if not os.path.isdir(wt):
        integrate.git(c, "worktree", "add", "-q", "-B", TOOLS_BRANCH, wt, f"{r_}/{TOOLS_BRANCH}")
    if integrate.git(wt, "status", "--porcelain", "--untracked-files=no").stdout.strip():
        raise integrate.GitError(f"the tools worktree {wt} has uncommitted changes")
    integrate.git(wt, "checkout", "-q", "-B", TOOLS_BRANCH, f"{r_}/{TOOLS_BRANCH}")
    return wt


def _tools_files(wt):
    lp, kp = os.path.join(wt, "integration.txt"), os.path.join(wt, "integration.lock")
    return integrate.read_text(lp), (integrate.read_text(kp) if os.path.exists(kp) else "")


def _tools_commit(wt, lst, pins, subject, body):
    """Write and commit integration.txt + integration.lock, push to the fork (plain, never forced); (ok, message)."""
    with open(os.path.join(wt, "integration.txt"), "w") as f:
        f.write(lst)
    with open(os.path.join(wt, "integration.lock"), "w") as f:
        f.write(pins)
    integrate.git(wt, "add", "integration.txt", "integration.lock")
    integrate.git(wt, "commit", "-q", f"--author={integrate.AUTHOR}", "-m",
                  f"{subject}\n\n{body}\n\n{integrate.TRAILER}", "--", "integration.txt", "integration.lock")
    p = integrate.git(wt, "push", "-q", integrate.remote(), f"{TOOLS_BRANCH}:{TOOLS_BRANCH}", check=False)
    if p.returncode != 0:
        return False, f"push of {TOOLS_BRANCH} refused: {integrate.tail(p.stderr, 3)}"
    return True, integrate.rev(wt, "HEAD")


def remove_from_list(text, branch):
    return "".join(l for l in text.splitlines(keepends=True) if l.strip() != branch)


def drop_pin(text, branch):
    return "".join(l for l in text.splitlines(keepends=True) if (l.split() or [""])[0] != branch)


def _start_train(entries, now_=False):
    """A new train for `entries` (the queue, drained) when the integration lease is free; the batch or None."""
    if leases.status("integration").get("holder"):
        return None
    if not try_train_lock():
        return None
    try:
        b = _new("train", [e["slug"] for e in entries], entries=list(entries))
        ok, msg = leases.take("integration", leases.DAEMON, purpose=f"train {b['id']}", change=b["id"])
        if not ok:
            return _fail(b, msg)
        _drain(entries)
        _clear_repin_pending()
        return _run_train(b)
    finally:
        release_train_lock()


def _run_train(b):
    """pinning -> rebuilding (synchronous) -> the gate in the background; exception/failed stop it."""
    entries = b.get("entries") or []
    ev = b["evidence"]
    os.makedirs(ev, exist_ok=True)
    what = ", ".join(f"{e['branch']}@{e['commit'][:9]}" for e in entries) or "no new entries (rebuild on main)"
    _step(b, "pinning", detail=what)
    c, r_ = integrate.clone(), integrate.remote()
    try:
        integrate.git(c, "fetch", "-q", r_)
        for e in entries:
            if not integrate.rev(c, e["commit"]):
                return on_exception(b, f"{e['slug']}: commit {e['commit'][:9]} of {e['branch']} is not in the clone")
        wt = _tools_checkout()
        lst, pins = _tools_files(wt)
        added, pinned = [], []
        for e in entries:
            new = integrate.add_to_list(lst, e["branch"])
            if new != lst:
                added.append(e["branch"])
                lst = new
            if integrate.parse_pins(pins).get(e["branch"]) != e["commit"]:
                pins = integrate.set_pin(pins, e["branch"], e["commit"])
                pinned.append(f"{e['branch']}@{e['commit'][:9]}")
        if added or pinned:
            subj = f"integration: {b['id']}" + (f": add {', '.join(added)}" if added else "") + \
                   (f"; pin {', '.join(pinned)}" if pinned else "")
            ok, msg = _tools_commit(wt, lst, pins, subj, "Changes: " + ", ".join(e["slug"] for e in entries))
            if not ok:
                return _fail(b, msg)
        b["tools"] = integrate.rev(wt, "HEAD")
        _step(b, "rebuilding", detail=f"tools {b['tools'][:9]}", rc=0)
        bw = build_wt()
        if not os.path.isdir(bw):
            integrate.git(c, "worktree", "add", "-q", "--detach", bw, f"{r_}/main")
        script = os.path.join(ev, "build-integration.sh")
        with open(script, "w") as f:
            f.write(integrate.git(wt, "show", f"{b['tools']}:scripts/build-integration.sh").stdout)
        log = os.path.join(ev, "rebuild.log")
        rc, out = integrate.rebuild(script, bw, log)
        if rc != 0:
            return on_exception(b, f"build-integration stopped (rc {rc}; the stopped merge is in {bw}; full log "
                                   f"{log}):\n{integrate.tail(out)}")
        isha = integrate.rev(bw, "HEAD")
        lease = integrate.rev(c, f"refs/remotes/{r_}/integration")
        p = integrate.push_integration(bw, lease)
        if p.returncode != 0:
            return _fail(b, f"lease push of integration {isha[:9]} refused (integration moved since the fetch): "
                            f"{integrate.tail(p.stderr, 3)}")
    except integrate.GitError as err:
        return _fail(b, str(err))
    b["integration"] = isha
    for e in entries:
        if store.exists(e["slug"]):
            ch = store.load(e["slug"])
            ch["batch"] = b["id"]
            store.add_log(ch, f"in train {b['id']}: integration {isha[:9]} pushed; gate, image and deploy pending")
            store.save(ch)
    return _start_gate(b)


def _start_gate(b):
    sha = b["integration"]
    d = os.path.join(qa_root(), f"integration-{sha[:9]}")
    b["gate_dir"] = d
    q = shlex.quote
    cmd = (f"export QA_ROOT={q(qa_root())}; [ -d {q(d)} ] || {q(qa_sh())} clone integration {sha} || exit $?; "
           f"{q(qa_sh())} gate {q(d)}")
    return _spawn(b, "gating", cmd, os.path.join(b["evidence"], "gate.log"))


def _start_image(b):
    cmd = f"{shlex.quote(image_sh())} {shlex.quote(b['gate_dir'])} {shlex.quote(b['id'])}"
    return _spawn(b, "imaging", cmd, os.path.join(b["evidence"], "image.log"))


def deploy_ready(obs):
    waiting = [b for b in open_batches("train") if b.get("step") == "imaged"]
    return max(waiting, key=lambda b: b["created"]) if waiting else None


def deploy(batch, obs):
    b = batch if isinstance(batch, dict) else load(batch)
    if b.get("step") != "imaged":
        return False, f"{b['id']} is {b.get('step')}, not imaged"
    missing = [r for r in _deploy_resources() if not leases.holds(r, leases.DAEMON)]
    if missing:
        return False, f"{b['id']}: waiting for the leases of {', '.join(missing)}"
    _spawn(b, "deploying", shlex.quote(deploy_sh()) + " " + shlex.quote(b["id"]), os.path.join(b["evidence"], "deploy.log"))
    return True, f"{b['id']}: deploy of {b.get('image')} started (pid {b['pid']})"


def _mark_live(b, isha, idx):
    """The included changes at their handed-over commit -> live; returns the slugs."""
    out = []
    for e in b.get("entries") or [{"slug": s} for s in b.get("includes") or []]:
        ch = idx.get(e["slug"])
        if ch is None or ch.reached("live"):
            continue
        if e.get("commit") and ch.get("commit") != e["commit"]:
            store.add_log(ch, f"train {b['id']} deployed {e['commit'][:9]}, but the change is now at "
                              f"{str(ch.get('commit'))[:9]} (not live)")
            store.save(ch)
            continue
        before = store.set_stage(ch, "live", f"deployed by {b['id']} (integration {isha[:9]})")
        ch["live_commit"], ch["batch"] = ch.get("commit"), b["id"]
        store.save(ch)
        events.emit("stage", change=ch.slug, commit=ch.get("commit"), batch=b["id"], **{"from": before, "to": "live"})
        out.append(ch.slug)
    return out


def _go_live(b, rec, obs):
    isha = b["integration"]
    idx = store.index()
    live_slugs = _mark_live(b, isha, idx)
    for ob in open_batches("train"):
        if ob["id"] == b["id"] or ob.get("step") not in ("imaged", "exception") or not ob.get("integration"):
            continue
        carried = [obs.contains("fork", isha, idx[s]["commit"]) for s in ob.get("includes") or [] if s in idx]
        if carried and all(carried):
            live_slugs += _mark_live(ob, isha, idx)
            _step(ob, "live", detail=f"carried by {b['id']} (integration {isha[:9]})", rc=0)
    b["rollback"] = rec.get("rollback_tag")
    store.set_live({"integration": isha, "image": rec.get("image") or b.get("image"), "image_id": b.get("image_id"),
                    "rollback": rec.get("rollback_tag"), "since": iso(), "upstream_main": obs.upstream_main(),
                    "batch": b["id"]})
    events.emit("deploy", batch=b["id"], image=rec.get("image") or b.get("image"), integration=isha, result="ok",
                seconds=rec.get("seconds"), rollback=rec.get("rollback_tag"), changes=live_slugs)
    _step(b, "live", detail=f"{rec.get('ready_line') or 'Server ready'}; live: {', '.join(live_slugs) or '-'}", rc=0)
    _release_deploy()
    _release_train(b)
    return b


def advance(obs, now=False):
    actions = []
    for b in open_batches("train"):
        if b.get("step") in BACKGROUND:
            a = _poll(b, obs)
            if a:
                actions.append(a)
    ready = deploy_ready(obs)
    if ready:
        ok, msg = deploy(ready, obs)
        actions.append(msg if ok else f"deploy refused: {msg}")
    if not leases.status("integration").get("holder") and not _exception_train():
        entries, why = _due(now)
        if entries or now or _repin_pending():
            b = _start_train(entries, now_=True)
            if b:
                actions.append(f"train {b['id']} -> {b['step']}" + (f": {b['exception']}" if b.get("exception") else ""))
    return actions


def _exception_train():
    """A train waiting for the integrator (BR-B4) blocks new trains: its stopped merge sits in the build worktree."""
    return next((b for b in open_batches("train") if b.get("step") == "exception"), None)


def resume(batch_id):
    b = load(batch_id)
    if not b or b.get("step") not in ("exception", "failed"):
        raise ValueError(f"{batch_id} is {(b or {}).get('step')}, nothing to resume")
    failed = _failed_step(b)
    if b["kind"] != "train":
        b["exception"] = None
        return _step(b, "queued", detail=f"resumed from {failed}")
    if leases.status("integration").get("holder"):
        raise RuntimeError(f"the integration lease is held by {leases.status('integration')['holder']}")
    ok, msg = leases.take("integration", leases.DAEMON, purpose=f"train {b['id']} (resumed)", change=b["id"])
    if not ok:
        raise RuntimeError(msg)
    b["exception"] = None
    if failed in ("queued", "pinning", "rebuilding"):
        if failed == "rebuilding":
            _drop_unlisted(b)
        if not try_train_lock():
            leases.release("integration", leases.DAEMON)
            raise RuntimeError("another process rebuilds now")
        try:
            return _run_train(b)
        finally:
            release_train_lock()
    if failed == "gating":
        return _start_gate(b)
    if failed == "imaging":
        return _start_image(b)
    return _step(b, "imaged", detail=f"resumed from {failed}: waiting for a deploy")


def _drop_unlisted(b):
    """Entries whose branch the integrator removed from integration.txt leave the train (BR-B4)."""
    c, r_ = integrate.clone(), integrate.remote()
    integrate.git(c, "fetch", "-q", r_)
    listed = integrate.listed(integrate.git(c, "show", f"refs/remotes/{r_}/{TOOLS_BRANCH}:integration.txt",
                                            check=False).stdout)
    keep, dropped = [], []
    for e in b.get("entries") or []:
        (keep if e["branch"] in listed else dropped).append(e)
    if dropped:
        b["entries"] = keep
        b["includes"] = [e["slug"] for e in keep]
        for e in dropped:
            if store.exists(e["slug"]):
                ch = store.load(e["slug"])
                store.add_log(ch, f"dropped from train {b['id']} by the integrator ({e['branch']} not in integration.txt)")
                store.save(ch)
        events.emit("batch", batch=b["id"], batch_kind="train", step="resume",
                    detail="dropped " + ", ".join(e["branch"] for e in dropped))
    return dropped


# --- re-pin (BR-B5) ----------------------------------------------------------------------------------------------------

def _repin_path():
    return os.path.join(state_dir(), "repin.json")


def _repin_pending():
    return bool((read_json(_repin_path(), {}) or {}).get("train_pending"))


def _clear_repin_pending():
    r = read_json(_repin_path(), {}) or {}
    if r.get("train_pending"):
        r["train_pending"] = False
        write_json(_repin_path(), r)


def merged_branches(obs):
    """Listed branches whose change's upstream PR is merged (a squash merge leaves no ancestor in main), or whose
    pin upstream main contains: what a re-pin drops."""
    out = []
    listed = set(obs.integration_list())
    for ch in store.all_changes():
        b = ch.get("branch")
        if ch.get("track") != "server" or not b or b not in listed or ch.get("local_only"):
            continue
        pr = obs.upstream_pr(ch.get("upstream_pr") or b)
        if (pr and pr.get("merged")) or (ch.get("commit") and obs.contains("upstream", "main", ch["commit"])):
            out.append(b)
    return out


def repin_due(obs):
    um = obs.upstream_main()
    if not um:
        return None
    recorded = store.live().get("upstream_main")
    last = (read_json(_repin_path(), {}) or {})
    if last.get("to") == um and not (merged_branches(obs) and last.get("at") and
                                     set(merged_branches(obs)) - set(last.get("dropped") or [])):
        return None
    if recorded and recorded != um:
        return um
    if merged_branches(obs):
        return um
    return None


def repin(obs):
    um = obs.upstream_main()
    frm = store.live().get("upstream_main")
    r = subprocess.run([fork_sh(), "sync"], capture_output=True, text=True, cwd=ROOT)
    if r.returncode != 0:
        events.emit("error", step="repin", error=f"fork.sh sync rc={r.returncode}: {integrate.tail(r.stderr or r.stdout, 3)}")
        return None
    dropped = []
    try:
        integrate.git(integrate.clone(), "fetch", "-q", integrate.remote())
        wt = _tools_checkout()
        lst, pins = _tools_files(wt)
        pinned = integrate.parse_pins(pins)
        merged = set(merged_branches(obs))
        for branch in integrate.listed(lst):
            if branch != TOOLS_BRANCH and (branch in merged or (pinned.get(branch) and obs.contains("upstream", "main", pinned[branch]))):
                dropped.append(branch)
                lst, pins = remove_from_list(lst, branch), drop_pin(pins, branch)
        if dropped:
            ok, msg = _tools_commit(wt, lst, pins, f"integration: repin to upstream main {str(um)[:9]}: drop "
                                    + ", ".join(dropped), "Merged upstream: " + ", ".join(dropped))
            if not ok:
                events.emit("error", step="repin", error=msg)
                return None
    except integrate.GitError as err:
        events.emit("error", step="repin", error=str(err))
        return None
    write_json(_repin_path(), {"from": frm, "to": um, "dropped": dropped, "at": iso(), "train_pending": True})
    events.emit("repin", **{"from": frm, "to": um, "dropped": dropped})
    with queue_lock():
        entries = list(read_queue()["entries"])
    return _start_train(entries, now_=True)


# --- releases (BR-B6) --------------------------------------------------------------------------------------------------

def _request_path():
    return os.path.join(state_dir(), "release.request")


def release_due(idx):
    rels = [b for b in _all() if b.get("kind") == "release"]
    if any(b.get("step") not in TERMINAL for b in rels):
        return None
    covered = {s for b in rels for s in b.get("includes") or []}
    new = sorted(s for s, ch in idx.items() if ch.get("track") == "client" and ch.reached("live") and s not in covered)
    n = int(_cfg("release_after_live_changes", 6))
    if os.path.exists(_request_path()) or (n and len(new) >= n):
        return new
    return None


def new_release(slugs):
    commit = integrate.rev(ours(), "refs/heads/master") or None
    b = _new("release", slugs, commit=commit)
    try:
        os.remove(_request_path())
    except OSError:
        pass
    return b
