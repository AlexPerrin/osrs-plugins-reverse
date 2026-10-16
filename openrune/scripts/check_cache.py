#!/usr/bin/env python3
"""Fails if OpenRune's JS5 cache (src/.data/cache/LIVE) holds clientscript groups (archive 12) the
Android client (241.4) crashes on: ids far above the stock range (stock ends at 9972). Custom
scripts must take the ids right after it. Usage: check_cache.py [CACHE_DIR] [MAX_ID]
"""
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.join(HERE, "..", "..", "tools", "openrune"))
from cachediff import Disk, decompress  # noqa: E402
import reftable  # noqa: E402

cache = sys.argv[1] if len(sys.argv) > 1 else os.path.join(HERE, "..", "src", ".data", "cache", "LIVE")
limit = int(sys.argv[2]) if len(sys.argv) > 2 else 9999
table = reftable.decode(decompress(Disk(cache).raw(255, 12)))
ids = sorted(table["groups"])
bad = [i for i in ids if i > limit]
print(f"clientscripts: {len(ids)} groups, max id {ids[-1]}")
if bad:
    sys.exit(f"clientscript ids above {limit} break the Android client: {bad}")
