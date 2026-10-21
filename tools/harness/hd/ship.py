"""The client and harness ship step: a checked change's commit merged into master with `--no-ff` and the standard
message, pushed to origin main; master containing the commit is what `live` observes. Server changes are not
shipped here: a train carries them (batches.py).

HD_NO_PUSH=1 skips the push (tests, offline); HD_SHIP_REPO overrides the repo (tests)."""
import os
import subprocess

from . import ROOT, events

AUTHOR = "Alex Perrin <44126061+AlexPerrin@users.noreply.github.com>"
TRAILER = "Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
CODE_DIRS = ("host", "native", "tools", "server")


def _git(repo, *args):
    return subprocess.run(["git", "-C", repo, *args], capture_output=True, text=True)


def merge_to_master(ch):
    """(ok, message). Preconditions: the tree is on master with no uncommitted changes in code dirs; the branch is
    at the handed-over commit."""
    repo = os.environ.get("HD_SHIP_REPO") or ROOT
    branch, commit = ch.get("branch"), ch.get("commit")
    if _git(repo, "branch", "--show-current").stdout.strip() != "master":
        return False, "the main tree is not on master"
    dirty = _git(repo, "status", "--porcelain", "--untracked-files=no", "--", *CODE_DIRS).stdout.strip()
    if dirty:
        return False, "the main tree has uncommitted changes in code dirs"
    tip = _git(repo, "rev-parse", "-q", "--verify", f"refs/heads/{branch}").stdout.strip()
    if tip != commit:
        return False, f"{branch} is at {tip[:9] or 'nothing'}, not the checked {commit[:9]}"
    if _git(repo, "merge-base", "--is-ancestor", commit, "master").returncode == 0:
        return True, "already on master"
    msg = f"Merge {branch}: {ch.get('title')} (dev's check: built, tried on the device)"
    if ch.get("summary"):
        msg += f"\n\n{ch['summary']}"
    msg += f"\n\nChange: harness/changes/{ch['slug']}.md\n\n{TRAILER}"
    r = _git(repo, "merge", "--no-ff", "--no-edit", "-m", msg, commit)
    if r.returncode != 0:
        _git(repo, "merge", "--abort")
        return False, f"merge failed: {(r.stderr or r.stdout).strip()[-300:]}"
    _git(repo, "commit", "--amend", "--no-edit", "-q", f"--author={AUTHOR}")
    head = _git(repo, "rev-parse", "--short", "HEAD").stdout.strip()
    pushed = ""
    if not os.environ.get("HD_NO_PUSH"):
        p = _git(repo, "push", "-q", "origin", "master:main")
        pushed = ", pushed" if p.returncode == 0 else f", push failed: {p.stderr.strip()[-200:]}"
    events.emit("ship", change=ch["slug"], merge=head, branch=branch, commit=commit)
    return True, f"merged into master {head}{pushed}"
