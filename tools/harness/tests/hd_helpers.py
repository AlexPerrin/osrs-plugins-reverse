"""Fixtures for the hd (harness v3) tests: a temp harness dir (changes, state, policy copy, lock mirror), the
FakeObserver, and change factories. Nothing touches the real store, /tmp locks or the network."""
import os
import shutil
import sys
import tempfile

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(os.path.dirname(os.path.dirname(HERE)))
sys.path.insert(0, os.path.join(ROOT, "tools", "harness"))

ENV_KEYS = ("HD_HARNESS_DIR", "HD_STATE_DIR", "HD_EVENTS", "HD_POLICY", "HD_LOCK_DIR", "HD_EVIDENCE_DIR", "HD_NO_FETCH", "HD_NO_RECOUNT")


def sandbox(test):
    """A temp harness dir with the real policy copied in; environment pointed at it for the test's duration."""
    d = tempfile.mkdtemp(prefix="hd-test-")
    test.addCleanup(shutil.rmtree, d, True)
    harness = os.path.join(d, "harness")
    os.makedirs(os.path.join(harness, "changes"))
    os.makedirs(os.path.join(harness, "state"))
    os.makedirs(os.path.join(d, "locks"))
    os.makedirs(os.path.join(d, "evidence"))
    shutil.copy(os.path.join(ROOT, "harness", "policy.yaml"), os.path.join(harness, "policy.yaml"))
    old = {k: os.environ.get(k) for k in ENV_KEYS}
    os.environ.update(HD_HARNESS_DIR=harness, HD_STATE_DIR=os.path.join(harness, "state"),
                      HD_EVENTS=os.path.join(harness, "state", "events.jsonl"),
                      HD_POLICY=os.path.join(harness, "policy.yaml"), HD_LOCK_DIR=os.path.join(d, "locks"),
                      HD_EVIDENCE_DIR=os.path.join(d, "evidence"), HD_NO_FETCH="1", HD_NO_RECOUNT="1")

    def restore():
        for k, v in old.items():
            if v is None:
                os.environ.pop(k, None)
            else:
                os.environ[k] = v
    test.addCleanup(restore)
    from hd import policy
    policy._cache.clear()
    test.addCleanup(policy._cache.clear)
    return d


def change(slug="feat", track="server", stage="ready", branch=None, commit=None, dod=("done",), ticked=True, **kw):
    from hd import store
    ch = store.new(slug, track, slug, dod=list(dod), stage="ready" if dod else "backlog",
                   branch=branch or (f"content/{slug}" if track == "server" else f"client/{slug}"))
    ch["stage"] = stage
    if commit:
        ch["commit"] = commit
    if ticked:
        ch.body = ch.body.replace("- [ ] ", "- [x] ")
    ch.update(kw)
    store.save(ch)
    return store.load(slug)


SHA = "a" * 40
SHB = "b" * 40


def env(test, **kv):
    """Set environment variables for the test's duration (None removes)."""
    old = {k: os.environ.get(k) for k in kv}
    for k, v in kv.items():
        if v is None:
            os.environ.pop(k, None)
        else:
            os.environ[k] = v

    def restore():
        for k, v in old.items():
            if v is None:
                os.environ.pop(k, None)
            else:
                os.environ[k] = v
    test.addCleanup(restore)


def dead_pid():
    """A pid that belonged to a process that has exited (reaped, so os.kill(pid, 0) fails)."""
    import subprocess
    p = subprocess.Popen(["true"])
    p.wait()
    return p.pid


def stub(d, name, body):
    """An executable script in d; returns its path."""
    p = os.path.join(d, name)
    with open(p, "w") as f:
        f.write("#!/usr/bin/env bash\n" + body)
    os.chmod(p, 0o755)
    return p
