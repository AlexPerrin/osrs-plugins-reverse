#!/usr/bin/env python3
"""Shim: the harness lives in its own repo (HD_HOME, default ~/osrs-harness). This execs its hd.py with the client
checkout (this repo's main checkout, also from a worktree) as HD_CLIENT_ROOT."""
import os
import subprocess
import sys

HERE = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
r = subprocess.run(["git", "-C", HERE, "rev-parse", "--path-format=absolute", "--git-common-dir"], capture_output=True, text=True)
common = r.stdout.strip()
root = common[:-len("/.git")] if r.returncode == 0 and common.endswith("/.git") else HERE
home = os.environ.get("HD_HOME") or os.path.expanduser("~/osrs-harness")
os.environ.setdefault("HD_CLIENT_ROOT", root)
os.execv(sys.executable, [sys.executable, os.path.join(home, "hd.py")] + sys.argv[1:])
