"""Shared fixtures for the harness tests: temp git repos (no network), task files, paths."""
import os
import subprocess
import tempfile

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(os.path.dirname(os.path.dirname(HERE)))   # the checkout these tests live in
HOOKS = os.path.join(ROOT, "tools", "hooks")
HARNESS_TOOLS = os.path.join(ROOT, "tools", "harness")

GIT_ENV = dict(os.environ, GIT_AUTHOR_NAME="Test", GIT_AUTHOR_EMAIL="test@example.invalid",
               GIT_COMMITTER_NAME="Test", GIT_COMMITTER_EMAIL="test@example.invalid",
               GIT_CONFIG_NOSYSTEM="1", GIT_CONFIG_GLOBAL=os.devnull, GIT_TERMINAL_PROMPT="0")

FORK_URL = "https://github.com/AlexPerrin/OpenRune-Server.git"
FORK_SSH = "git@github.com:AlexPerrin/OpenRune-Server.git"
UPSTREAM_URL = "https://github.com/OpenRune/OpenRune-Server.git"
OURS_URL = "https://github.com/AlexPerrin/osrs-plugins-reverse.git"


def git(cwd, *args):
    r = subprocess.run(["git", "-C", cwd, *args], capture_output=True, text=True, env=GIT_ENV)
    if r.returncode != 0:
        raise RuntimeError(f"git {' '.join(args)} in {cwd}: {r.stderr}")
    return r.stdout.strip()


def init_repo(path, branch="master", remotes=None, bare=False):
    os.makedirs(path, exist_ok=True)
    git(path, "init", "-q", "-b", branch, *(["--bare"] if bare else []))
    for name, url in (remotes or {}).items():
        git(path, "remote", "add", name, url)
    return path


def commit_file(repo, rel, text, msg=None):
    p = os.path.join(repo, rel)
    os.makedirs(os.path.dirname(p), exist_ok=True)
    with open(p, "w") as f:
        f.write(text)
    git(repo, "add", rel)
    git(repo, "commit", "-q", "-m", msg or f"change {rel}")
    return git(repo, "rev-parse", "HEAD")


def tempdir(test):
    d = tempfile.TemporaryDirectory(prefix="harness-test-")
    test.addCleanup(d.cleanup)
    return os.path.realpath(d.name)


def write_task(harness, tid, ticked=True, body_extra="", **meta):
    os.makedirs(os.path.join(harness, "tasks"), exist_ok=True)
    meta = dict({"id": tid, "track": "client", "title": tid, "status": "handover", "prio": "P2",
                 "role": "client-dev", "owner": "t1", "instance": [], "blocked_by": [], "unblocks": [],
                 "created": "2026-10-20T00:00:00Z"}, **meta)
    import yaml
    front = yaml.safe_dump(meta, sort_keys=False).rstrip("\n")
    box = "[x]" if ticked else "[ ]"
    with open(os.path.join(harness, "tasks", f"{tid}.md"), "w") as f:
        f.write(f"---\n{front}\n---\n## Task\n{tid}\n\n## Definition of done\n- {box} done\n{body_extra}\n"
                f"## Log\n- 2026-10-20T00:00:00Z created (ready)\n")


def read_task(harness, tid):
    import re
    import yaml
    with open(os.path.join(harness, "tasks", f"{tid}.md")) as f:
        text = f.read()
    m = re.match(r"---\n(.*?)\n---\n(.*)", text, re.S)
    return yaml.safe_load(m.group(1)), m.group(2)


def main_checkout():
    """The main checkout (from a worktree too), where the live task store is."""
    r = subprocess.run(["git", "-C", ROOT, "rev-parse", "--path-format=absolute", "--git-common-dir"],
                       capture_output=True, text=True)
    common = r.stdout.strip()
    return common[:-len("/.git")] if r.returncode == 0 and common.endswith("/.git") else ROOT
