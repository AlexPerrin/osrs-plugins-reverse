#!/usr/bin/env python3
"""Shim: this hook lives in the harness repo (HD_HOME, default ~/osrs-harness/hooks/slot_stop.py)."""
import os, sys
home = os.environ.get("HD_HOME") or os.path.expanduser("~/osrs-harness")
os.environ.setdefault("HD_CLIENT_ROOT", os.environ.get("CLAUDE_PROJECT_DIR") or os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__)))))
os.execv(sys.executable, [sys.executable, os.path.join(home, "hooks", "slot_stop.py")] + sys.argv[1:])
