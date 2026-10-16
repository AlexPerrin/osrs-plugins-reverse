#!/usr/bin/env python3
"""Adds varclient configs (archive 2, group 19) that OpenRune's custom clientscripts use but the
rev-241 cache leaves undefined, to the packed JS5 cache in place.

OpenRune's quest pack declares varcstr 1293/1294 (quest_available_ids, quest_policy_complete_ids,
content/quest/pack/.../varc.sym). Rev 241 has no varclient entries for those ids, and the Android
client crashes when a script reads or writes a varc without one. A varclient entry carries no
type, so an empty config (just the terminating 0) is what every plain varc in the cache has.
Usage: add_varcs.py [CACHE_DIR] [ID...]   (default: src/.data/cache/LIVE, ids from the varc.sym
files of the packs in src/content)
"""
import glob
import os
import struct
import sys
import zlib

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.join(HERE, "..", "..", "tools", "openrune"))
from cachediff import Disk, decompress  # noqa: E402
from hybridcache import write_group  # noqa: E402
import reftable  # noqa: E402

ARCHIVE, GROUP = 2, 19
EMPTY_CONFIG = b"\x00"


def declared_ids():
    ids = set()
    for sym in glob.glob(os.path.join(HERE, "..", "src", "content", "**", "varc.sym"), recursive=True):
        for line in open(sym):
            if line.strip():
                ids.add(int(line.split()[0]))
    return ids


def split_files(data, n):
    chunks = data[-1]
    p = len(data) - 1 - chunks * n * 4
    sizes = []
    for _ in range(chunks):
        acc, row = 0, []
        for _ in range(n):
            acc += struct.unpack(">i", data[p:p + 4])[0]
            p += 4
            row.append(acc)
        sizes.append(row)
    files, off = [bytearray() for _ in range(n)], 0
    for row in sizes:
        for i, size in enumerate(row):
            files[i] += data[off:off + size]
            off += size
    return [bytes(f) for f in files]


def join_files(files):
    body, last = bytearray(), 0
    for f in files:
        body += f
    for f in files:
        body += struct.pack(">i", len(f) - last)
        last = len(f)
    body.append(1)
    return bytes(body)


def main():
    cache = sys.argv[1] if len(sys.argv) > 1 else os.path.join(HERE, "..", "src", ".data", "cache", "LIVE")
    wanted = {int(a) for a in sys.argv[2:]} or declared_ids()
    disk = Disk(cache)
    table = reftable.decode(decompress(disk.raw(255, ARCHIVE)))
    group = table["groups"][GROUP]
    kids = group["kids"]
    missing = sorted(wanted - set(kids))
    if not missing:
        print(f"varclient: all {len(wanted)} declared ids present")
        return
    files = dict(zip(kids, split_files(decompress(disk.raw(ARCHIVE, GROUP)), len(kids))))
    files.update({i: EMPTY_CONFIG for i in missing})
    new_kids = sorted(files)
    body = join_files([files[k] for k in new_kids])
    container = bytes([0]) + struct.pack(">I", len(body)) + body

    if table["flags"] & 1:
        names = dict(zip(kids, group["knames"]))
        group["knames"] = [names.get(k, 0) for k in new_kids]
    group["kids"] = new_kids
    group["ver"] += 1
    # The client checks the CRC over the container and the version against a 2-byte trailer.
    group["crc"] = zlib.crc32(container)
    container += struct.pack(">H", group["ver"] & 0xFFFF)
    if table["flags"] & 4:
        group["sizes"] = (len(body), len(body))
    if table["flags"] & 2:
        group["wp"] = bytes(64)
    table["version"] += 1
    ref_body = reftable.encode(table)
    ref = bytes([0]) + struct.pack(">I", len(ref_body)) + ref_body

    dat_path = os.path.join(cache, "main_file_cache.dat2")
    dat = bytearray(open(dat_path, "rb").read())
    entries = {}
    for archive, group_id, raw in ((ARCHIVE, GROUP, container), (255, ARCHIVE, ref)):
        entries[(archive, group_id)] = (len(raw), write_group(dat, raw, archive, group_id))
    open(dat_path, "wb").write(dat)
    for (archive, group_id), (size, first) in entries.items():
        idx_path = os.path.join(cache, f"main_file_cache.idx{archive}")
        idx = bytearray(open(idx_path, "rb").read())
        idx[group_id * 6:group_id * 6 + 6] = size.to_bytes(3, "big") + first.to_bytes(3, "big")
        open(idx_path, "wb").write(idx)
    print(f"varclient: added empty configs for {missing}")


if __name__ == "__main__":
    main()
