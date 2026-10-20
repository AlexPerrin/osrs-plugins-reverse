#!/usr/bin/env python3
"""Integration trains (docs/integration-pipeline.md, phase 1): the queue, the integration lock, trains and pins.

  integrate.py status                 the queue, the open/last trains with their next step, pin warnings
  integrate.py enqueue <task>         queue a shipped server task's handed-over commit for the next train (ship.sh)
  integrate.py build [--now]          drain the queue into one train: one integration.txt + integration.lock commit
                                      on local/integration-tools, one build-integration.sh run, one lease push of
                                      `integration`; waits INTEGRATION_DEBOUNCE_MIN (10) minutes after the oldest
                                      entry unless --now. Also `orchestrate.py integrate ...`.

Files (re/harness/integration/): queue.json; .lock, a record-locked file: byte 0 guards the queue (held for a
moment by enqueue and by the train when it reads or drains it), byte 1 is the integration lock (held by `build` for
the whole train; a second build exits 75 "busy"). Holding the integration lock doesn't block an enqueue: that entry
waits for the next train. build/ is the worktree the rebuild runs in; the tools worktree is
re/harness/integration-tools (local/integration-tools).

A train is a task `int-<yyyymmdd>-<n>` (track orch, role integration: dispatch skips it) with `includes` (task ids),
`integration` (the pushed sha), `tools` (the local/integration-tools sha it was built from), `stage` (building,
built, needs-agent, failed) and `evidence` (re/evidence/<train>/, the rebuild log). A build re-uses the newest train
that isn't `built`. On a failure the queue is left as it was: `needs-agent` (the rebuild stopped, e.g. an
unrecorded conflict; the stopped merge stays in build/), `failed` (a push was refused; re-run build).

Pins: integration.lock on local/integration-tools, `<branch> <sha>` lines. A train pins each queued branch to its
handed-over commit; build-integration.sh merges the pin, not the tip, so a push after the hand-over isn't taken
until the branch is handed over (and queued) again. `status` warns about tips that moved past their pins.

Environment (tests): HARNESS_DIR, INTEGRATION_DIR, INTEGRATION_CLONE, INTEGRATION_TOOLS_WT, INTEGRATION_REMOTE (fork),
INTEGRATION_EVIDENCE_ROOT, INTEGRATION_DEBOUNCE_MIN.
"""
import argparse
import contextlib
import datetime as dt
import fcntl
import json
import os
import re
import subprocess
import sys
import tempfile

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import task as taskstore  # noqa: E402

ROOT = taskstore.main_repo(os.path.dirname(os.path.dirname(HERE)))
AUTHOR = "Alex Perrin <44126061+AlexPerrin@users.noreply.github.com>"
TRAILER = "Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
TOOLS_BRANCH = "local/integration-tools"
BUSY = 75
SECTIONS = ("# Upstream fixes", "# Content", "# Local only")


# --- configuration (read at call time, so tests can set the environment) -------------------------------------------

def state_dir():
    return os.environ.get("INTEGRATION_DIR") or os.path.join(ROOT, "re", "harness", "integration")


def clone():
    return os.environ.get("INTEGRATION_CLONE") or os.path.join(ROOT, "re", "upstream-pr", "or")


def tools_wt():
    return os.environ.get("INTEGRATION_TOOLS_WT") or os.path.join(ROOT, "re", "harness", "integration-tools")


def build_wt():
    return os.path.join(state_dir(), "build")


def remote():
    return os.environ.get("INTEGRATION_REMOTE") or "fork"


def evidence_root():
    return os.environ.get("INTEGRATION_EVIDENCE_ROOT") or os.path.join(ROOT, "re", "evidence")


def debounce_min():
    return float(os.environ.get("INTEGRATION_DEBOUNCE_MIN", "10"))


def tasks():
    """The task store module, pointed at HARNESS_DIR (or the main checkout's harness/)."""
    h = os.environ.get("HARNESS_DIR") or os.path.join(ROOT, "harness")
    taskstore.HARNESS, taskstore.TASKS = h, os.path.join(h, "tasks")
    return taskstore


def now():
    return dt.datetime.now(dt.timezone.utc)


def iso(t):
    return t.strftime("%Y-%m-%dT%H:%M:%SZ")


def parse_iso(s):
    return dt.datetime.strptime(s, "%Y-%m-%dT%H:%M:%SZ").replace(tzinfo=dt.timezone.utc)


def git(cwd, *args, check=True):
    r = subprocess.run(["git", "-C", cwd, *args], capture_output=True, text=True)
    if check and r.returncode != 0:
        raise GitError(f"git {' '.join(args)} (in {cwd}): {(r.stderr or r.stdout).strip()}")
    return r


class GitError(RuntimeError):
    pass


def read_text(path):
    with open(path) as f:
        return f.read()


def rev(cwd, ref):
    return git(cwd, "rev-parse", "-q", "--verify", f"{ref}^{{commit}}", check=False).stdout.strip()


# --- locks and the queue ---------------------------------------------------------------------------------------------

_FDS = {}
_HOLDING = set()   # state dirs whose integration lock this process holds (POSIX locks don't conflict in-process)


def _fd():
    """One descriptor per process: POSIX record locks are per process, and closing any descriptor of the file would
    drop them all."""
    p = os.path.join(state_dir(), ".lock")
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


def try_build_lock():
    """Take the integration lock (byte 1) without waiting; False when another build holds it."""
    if state_dir() in _HOLDING:
        return False
    try:
        fcntl.lockf(_fd(), fcntl.LOCK_EX | fcntl.LOCK_NB, 1, 1)
    except OSError:
        return False
    _HOLDING.add(state_dir())
    return True


def release_build_lock():
    _HOLDING.discard(state_dir())
    fcntl.lockf(_fd(), fcntl.LOCK_UN, 1, 1)


def build_running():
    if state_dir() in _HOLDING:
        return True
    if not try_build_lock():
        return True
    release_build_lock()
    return False


def queue_path():
    return os.path.join(state_dir(), "queue.json")


def read_queue():
    try:
        with open(queue_path()) as f:
            q = json.load(f)
    except FileNotFoundError:
        q = {}
    q.setdefault("entries", [])
    return q


def write_queue(q):
    fd, tmp = tempfile.mkstemp(dir=state_dir(), prefix=".queue.")
    with os.fdopen(fd, "w") as f:
        json.dump(q, f, indent=1)
        f.write("\n")
    os.replace(tmp, queue_path())


def check_layout():
    """re/harness/integration used to be ship.sh's rebuild worktree; the state dir can't be a worktree itself."""
    if os.path.exists(os.path.join(state_dir(), ".git")):
        sys.exit(f"integrate: {state_dir()} is still the old rebuild worktree (ship.sh before phase 1). Remove it once: "
                 f"git -C {clone()} worktree remove --force {state_dir()}")


# --- enqueue ---------------------------------------------------------------------------------------------------------

def enqueue(tid, fetch=True):
    T = tasks()
    meta, _ = T.load(tid)
    branch, commit = meta.get("branch"), str(meta.get("commit") or "")
    if meta.get("track") != "server" or (meta.get("role") or "").endswith("-qa"):
        sys.exit(f"integrate: {tid} is not a server dev task")
    if not branch or len(commit) < 40:
        sys.exit(f"integrate: {tid} has no branch and full commit")
    check_layout()
    if fetch:
        git(clone(), "fetch", "-q", remote())
    tip = rev(clone(), f"refs/remotes/{remote()}/{branch}")
    if tip != commit:
        sys.exit(f"integrate: {remote()}/{branch} is at {tip[:9] or 'nothing'}, not the handed-over {commit[:9]}")
    with queue_lock():
        q = read_queue()
        replaced = [e for e in q["entries"] if e["branch"] == branch]
        q["entries"] = [e for e in q["entries"] if e["branch"] != branch]
        q["entries"].append({"task": tid, "branch": branch, "commit": commit, "queued": iso(now())})
        write_queue(q)
        n = len(q["entries"])
    note = " (replaces " + ", ".join(f"{e['task']}@{e['commit'][:9]}" for e in replaced) + ")" if replaced else ""
    busy = build_running()
    meta, body = T.load(tid)
    T.save(meta, T.add_log(body, f"queued for the next train ({branch}@{commit[:9]}){note}"))
    print(f"queued {tid} ({branch}@{commit[:9]}){note}; {n} in the queue" +
          ("; a train is building now, so this goes in the next one" if busy else ""))
    return 0


# --- integration.txt and integration.lock ----------------------------------------------------------------------------

def section_for(branch):
    return "# Local only" if branch.startswith("local/") else "# Upstream fixes" if branch.startswith("fix/") else "# Content"


def add_to_list(text, branch):
    """integration.txt with `branch` at the end of its section (fix/* under "# Upstream fixes", content/* and fin/*
    under "# Content", local/* under "# Local only"); unchanged when it is already listed."""
    lines = text.splitlines()
    if branch in (l.strip() for l in lines):
        return text
    section = section_for(branch)
    start = next((i for i, l in enumerate(lines) if l.startswith(section)), None)
    if start is None:
        lines.append(branch)
    else:
        end = next((i for i in range(start + 1, len(lines)) if lines[i].startswith("#")), len(lines))
        while end > start + 1 and not lines[end - 1].strip():
            end -= 1
        lines.insert(end, branch)
    return "\n".join(lines) + "\n"


def listed(text):
    return [l.strip() for l in text.splitlines() if l.strip() and not l.strip().startswith("#")]


def parse_pins(text):
    pins = {}
    for l in text.splitlines():
        w = l.split()
        if len(w) >= 2 and not w[0].startswith("#"):
            pins[w[0]] = w[1]
    return pins


def set_pin(text, branch, sha):
    lines, done = text.splitlines(), False
    for i, l in enumerate(lines):
        w = l.split()
        if len(w) >= 2 and w[0] == branch:
            lines[i], done = f"{branch} {sha}", True
    if not done:
        lines.append(f"{branch} {sha}")
    return "\n".join(lines) + "\n"


# --- trains ----------------------------------------------------------------------------------------------------------

def trains():
    T = tasks()
    out = [(m, b) for m, b in T.all_tasks() if m.get("role") == "integration" and str(m["id"]).startswith("int-")]
    key = lambda mb: (str(mb[0]["id"])[4:12], int(str(mb[0]["id"]).rsplit("-", 1)[-1]))
    return sorted(out, key=key)


def open_train():
    open_ = [m for m, _ in trains() if m.get("stage") != "built" and m.get("status") not in ("done", "live")]
    return open_[-1] if open_ else None


def new_train():
    T = tasks()
    day = now().strftime("%Y%m%d")
    n = 1 + sum(1 for m, _ in trains() if str(m["id"]).startswith(f"int-{day}-"))
    tid = f"int-{day}-{n}"
    os.makedirs(T.TASKS, exist_ok=True)
    ev = os.path.join(evidence_root(), tid)
    meta = {"id": tid, "track": "orch", "title": f"Integration train {tid}", "status": "in-progress", "prio": "P1",
            "role": "integration", "owner": "integrate", "instance": [], "branch": "integration", "commit": None,
            "blocked_by": [], "unblocks": [], "created": iso(now()), "includes": [], "integration": None, "tools": None,
            "stage": "building", "evidence": ev}
    body = ("## Task\nOne integration train (docs/integration-pipeline.md): the queued server tasks in `includes`, "
            "merged at their pins into one `integration` rebuild and one lease push; then the gate, image and deploy.\n\n"
            "## Definition of done\n- [ ] integration.txt and integration.lock committed on local/integration-tools\n"
            "- [ ] integration rebuilt and pushed with a lease (stage built)\n- [ ] gate, image, deploy (later phases)\n")
    T.save(meta, T.add_log(body, "created (train)"))
    return meta


def set_train(tid, msg=None, **kw):
    T = tasks()
    meta, body = T.load(tid)
    meta.update(kw)
    T.save(meta, T.add_log(body, msg) if msg else body)
    return meta


def tail(text, n=15):
    return "\n".join(text.strip().splitlines()[-n:])


def gate_command(sha):
    return (f"QA_ROOT=$ROOT/re/harness/qa openrune/upstream/qa/qa.sh clone integration {sha[:9]} && qa.sh gate <clone>; "
            "boot-check; then deploy with both instances reserved")


def build(now_=False):
    check_layout()
    if not try_build_lock():
        print(f"integrate: busy (another build holds {os.path.join(state_dir(), '.lock')})")
        return BUSY
    try:
        with queue_lock():
            entries = list(read_queue()["entries"])
        if not entries:
            t = open_train()
            print("integrate: the queue is empty" + (f"; {t['id']} is {t.get('stage')}" if t else ""))
            return 0
        oldest = min(parse_iso(e["queued"]) for e in entries)
        age = (now() - oldest).total_seconds() / 60
        if not now_ and age < debounce_min():
            print(f"integrate: waiting: the oldest entry is {age:.0f} min old; the train starts at "
                  f"{debounce_min():.0f} min (or build --now)")
            return 0
        return run_train(entries)
    finally:
        release_build_lock()


def rebuild(script, cwd, log):
    """Run build-integration.sh against the remote in the rebuild worktree; (rc, output)."""
    r = subprocess.run(["sh", script, remote()], cwd=cwd, capture_output=True, text=True)
    out = r.stdout + ("\n" + r.stderr if r.stderr else "")
    with open(log, "w") as f:
        f.write(out)
    return r.returncode, out


def push_integration(cwd, lease):
    return git(cwd, "push", "-q", f"--force-with-lease=integration:{lease}", remote(), "HEAD:refs/heads/integration",
               check=False)


def run_train(entries):
    T = tasks()
    train = open_train() or new_train()
    tid = train["id"]
    includes = list(train.get("includes") or [])
    includes += [e["task"] for e in entries if e["task"] not in includes]
    ev = train.get("evidence") or os.path.join(evidence_root(), tid)
    os.makedirs(ev, exist_ok=True)
    what = ", ".join(e["branch"] + "@" + e["commit"][:9] for e in entries)
    set_train(tid, f"building: {what}", includes=includes, stage="building", evidence=ev)
    print(f"train {tid}: {len(entries)} entr{'y' if len(entries) == 1 else 'ies'}")

    def stop(stage, msg):
        set_train(tid, f"{stage}: {msg}", stage=stage)
        print(f"train {tid} -> {stage}: {msg}\nthe queue is unchanged")
        return 1

    c, r_ = clone(), remote()
    try:
        git(c, "fetch", "-q", r_)
        for e in entries:
            if not rev(c, e["commit"]):
                return stop("needs-agent", f"{e['task']}: commit {e['commit'][:9]} of {e['branch']} is not in the clone")
        # 1. integration.txt + integration.lock: one commit on local/integration-tools, pushed (plain, never forced)
        wt = tools_wt()
        if not os.path.isdir(wt):
            git(c, "worktree", "add", "-q", "-B", TOOLS_BRANCH, wt, f"{r_}/{TOOLS_BRANCH}")
        if git(wt, "status", "--porcelain", "--untracked-files=no").stdout.strip():
            return stop("failed", f"the tools worktree {wt} has uncommitted changes")
        git(wt, "checkout", "-q", "-B", TOOLS_BRANCH, f"{r_}/{TOOLS_BRANCH}")
        lp, kp = os.path.join(wt, "integration.txt"), os.path.join(wt, "integration.lock")
        lst = read_text(lp)
        pins = read_text(kp) if os.path.exists(kp) else ""
        added, pinned = [], []
        for e in entries:
            new = add_to_list(lst, e["branch"])
            if new != lst:
                added.append(e["branch"])
                lst = new
            if parse_pins(pins).get(e["branch"]) != e["commit"]:
                pins = set_pin(pins, e["branch"], e["commit"])
                pinned.append(f"{e['branch']}@{e['commit'][:9]}")
        if added or pinned:
            with open(lp, "w") as f:
                f.write(lst)
            with open(kp, "w") as f:
                f.write(pins)
            subj = f"integration: {tid}" + (f": add {', '.join(added)}" if added else "") + \
                   (f"; pin {', '.join(pinned)}" if pinned else "")
            tasks_line = "Tasks: " + ", ".join(e["task"] for e in entries)
            git(wt, "add", "integration.txt", "integration.lock")
            git(wt, "commit", "-q", f"--author={AUTHOR}", "-m", f"{subj}\n\n{tasks_line}\n\n{TRAILER}", "--",
                "integration.txt", "integration.lock")
            p = git(wt, "push", "-q", r_, f"{TOOLS_BRANCH}:{TOOLS_BRANCH}", check=False)
            if p.returncode != 0:
                return stop("failed", f"push of {TOOLS_BRANCH} refused: {tail(p.stderr, 3)}")
        tools_sha = rev(wt, "HEAD")
        # 2. one rebuild with the tools commit's build-integration.sh
        bw = build_wt()
        if not os.path.isdir(bw):
            git(c, "worktree", "add", "-q", "--detach", bw, f"{r_}/main")
        script = os.path.join(ev, "build-integration.sh")
        with open(script, "w") as f:
            f.write(git(wt, "show", f"{tools_sha}:scripts/build-integration.sh").stdout)
        log = os.path.join(ev, "rebuild.log")
        rc, out = rebuild(script, bw, log)
        if rc != 0:
            return stop("needs-agent", f"build-integration stopped (rc {rc}; the stopped merge is in {bw}; "
                                       f"full log {log}):\n{tail(out)}")
        isha = rev(bw, "HEAD")
        # 3. one lease push: only over the integration tip the rebuild fetched
        lease = rev(c, f"refs/remotes/{r_}/integration")
        p = push_integration(bw, lease)
        if p.returncode != 0:
            return stop("failed", f"lease push of integration {isha[:9]} refused (integration moved since the fetch; "
                                  f"re-run build): {tail(p.stderr, 3)}")
    except GitError as err:
        return stop("failed", str(err))
    set_train(tid, f"built: integration {isha[:9]} pushed (tools {tools_sha[:9]})", stage="built", status="shipped",
              integration=isha, commit=isha, tools=tools_sha)
    for e in entries:
        m, b = T.load(e["task"])
        m["integration"] = isha
        T.save(m, T.add_log(b, f"in train {tid}: integration {isha[:9]} (pushed); gate + deploy pending"))
    with queue_lock():
        q = read_queue()
        drained = {(e["task"], e["commit"]) for e in entries}
        q["entries"] = [e for e in q["entries"] if (e["task"], e["commit"]) not in drained]
        write_queue(q)
        left = len(q["entries"])
    print(f"train {tid} built: integration {isha[:9]} pushed" + (f"; {left} entries wait for the next train" if left else ""))
    print(f"next (orchestrator): {gate_command(isha)}")
    return 0


# --- status ----------------------------------------------------------------------------------------------------------

def pin_warnings():
    """Listed branches whose fork tip (as of the last fetch) differs from their pin with no queued hand-over."""
    c, r_ = clone(), remote()
    ref = f"refs/remotes/{r_}/{TOOLS_BRANCH}"
    lst = git(c, "show", f"{ref}:integration.txt", check=False).stdout
    pins = parse_pins(git(c, "show", f"{ref}:integration.lock", check=False).stdout)
    with queue_lock():
        queued = {e["branch"]: e["commit"] for e in read_queue()["entries"]}
    out = []
    for b in listed(lst):
        if b == TOOLS_BRANCH:
            continue
        tip, pin = rev(c, f"refs/remotes/{r_}/{b}"), pins.get(b)
        if not pin:
            out.append(f"{b}: no pin; build-integration merges its tip")
        elif tip and tip != pin and queued.get(b) != tip:
            how = "moved past" if git(c, "merge-base", "--is-ancestor", pin, tip, check=False).returncode == 0 else "diverged from"
            out.append(f"{b}: tip {tip[:9]} {how} its pin {pin[:9]} without a hand-over (the train keeps the pin)")
    return out


def next_step(m):
    st = m.get("stage")
    if st == "built":
        return gate_command(str(m.get("integration") or ""))
    if st == "needs-agent":
        return (f"resolve the stopped rebuild in {build_wt()} (log: {m.get('evidence')}/rebuild.log), record the "
                "resolution on local/integration-tools, then `orchestrate.py integrate build --now` (phase 4: the "
                "integrator agent)")
    if st == "failed":
        return "see the train's log; then `orchestrate.py integrate build --now`"
    return "a build is running" if build_running() else "interrupted build: `orchestrate.py integrate build --now`"


def status(short=False):
    check_layout()
    with queue_lock():
        entries = read_queue()["entries"]
    if entries:
        oldest = min(parse_iso(e["queued"]) for e in entries)
        age = (now() - oldest).total_seconds() / 60
        due = "due now" if age >= debounce_min() else f"due in {debounce_min() - age:.0f} min"
        print(f"queue: {len(entries)} (oldest {age:.0f} min, train {due}" + ("; a build is running" if build_running() else "") + ")")
        if not short:
            for e in entries:
                print(f"  {e['task']:40} {e['branch']}@{e['commit'][:9]}  queued {e['queued']}")
    else:
        print("queue: empty" + ("; a build is running" if build_running() else ""))
    ts = [m for m, _ in trains()]
    for m in ts[-1:] if short else ts[-3:]:
        print(f"train {m['id']}: {m.get('stage')}, integration {str(m.get('integration') or '-')[:9]}, "
              f"includes {', '.join(m.get('includes') or []) or '-'}")
        print(f"  next: {next_step(m)}")
    if not short:
        for w in pin_warnings():
            print(f"  pin warning: {w}")
    return 0


def main(argv=None):
    p = argparse.ArgumentParser(prog="integrate", description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    sp = p.add_subparsers(dest="cmd", required=True)
    sp.add_parser("status")
    s = sp.add_parser("enqueue"); s.add_argument("task")
    s = sp.add_parser("build"); s.add_argument("--now", action="store_true")
    a = p.parse_args(argv)
    if a.cmd == "status":
        return status()
    if a.cmd == "enqueue":
        return enqueue(a.task)
    return build(a.now)


if __name__ == "__main__":
    sys.exit(main())
