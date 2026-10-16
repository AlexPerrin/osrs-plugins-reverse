#!/usr/bin/env python3
"""Fills in the reference-table entries that OpenRune's cache packer leaves empty for groups it
adds, in a packed JS5 cache, in place.

Groups that OpenRune adds (custom clientscripts, e.g. 9973/9974 from the quest pack) get
sizes (0, 0) and name hash 0 in their archive's reference table. Every stock group has real
values, and the Android client (241.4) crashes with a null dereference during cache sync as soon
as such a group exists. Groups that OpenRune only rewrites keep their stock entries (stale sizes,
which the client tolerates).

For each group with sizes (0, 0): sizes = (container length without the 2-byte version trailer,
decompressed length). In a named archive with name hash 0: for clientscripts (archive 12) the
hash of the script name the compiler embeds ("[clientscript,name]"), else left as is.
Usage: fix_reftable.py [CACHE_DIR] [ARCHIVE...]   (default: src/.data/cache/LIVE, archive 12)
"""
import os
import struct
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.join(HERE, "..", "..", "tools", "openrune"))
from cachediff import Disk, decompress  # noqa: E402
from hybridcache import write_group  # noqa: E402
import reftable  # noqa: E402


def name_hash(s):
    h = 0
    for c in s.encode("cp1252"):
        h = (h * 31 + c) & 0xFFFFFFFF
    return h


def script_name(data):
    # OpenRune's compiler writes "<file>.cs2 [kind,name]" as the script's name string.
    if not data or data[0] == 0:
        return None
    text = data[:data.index(0)].decode("cp1252")
    return text.split(" ")[-1] if text.endswith("]") else None


def fix(cache, archive):
    disk = Disk(cache)
    table = reftable.decode(decompress(disk.raw(255, archive)))
    fixed = []
    for gid, g in table["groups"].items():
        if not table["flags"] & 4 or g["sizes"] != (0, 0):
            continue
        raw = disk.raw(archive, gid)
        if raw is None:
            continue
        data = decompress(raw)
        g["sizes"] = (len(raw) - 2, len(data))
        if table["flags"] & 1 and g["name"] == 0 and archive == 12:
            name = script_name(data)
            if name:
                g["name"] = name_hash(name)
        fixed.append(gid)
    if not fixed:
        print(f"archive {archive}: no empty reference-table entries")
        return
    table["version"] += 1
    body = reftable.encode(table)
    ref = bytes([0]) + struct.pack(">I", len(body)) + body
    dat_path = os.path.join(cache, "main_file_cache.dat2")
    dat = bytearray(open(dat_path, "rb").read())
    first = write_group(dat, ref, 255, archive)
    open(dat_path, "wb").write(dat)
    idx_path = os.path.join(cache, "main_file_cache.idx255")
    idx = bytearray(open(idx_path, "rb").read())
    idx[archive * 6:archive * 6 + 6] = len(ref).to_bytes(3, "big") + first.to_bytes(3, "big")
    open(idx_path, "wb").write(idx)
    print(f"archive {archive}: filled sizes/names for groups {fixed}")


def main():
    cache = sys.argv[1] if len(sys.argv) > 1 else os.path.join(HERE, "..", "src", ".data", "cache", "LIVE")
    for archive in [int(a) for a in sys.argv[2:]] or [12]:
        fix(cache, archive)


if __name__ == "__main__":
    main()
