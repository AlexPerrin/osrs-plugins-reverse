#!/usr/bin/env python3
"""harness v3 entry point: python3 tools/harness/hd.py <command> ... (see hd/cli.py)."""
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from hd.cli import main  # noqa: E402

if __name__ == "__main__":
    sys.exit(main())
