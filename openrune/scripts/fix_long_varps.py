#!/usr/bin/env python3
"""Fixes long-varp reads/writes in the clientscripts OpenRune's compiler packs, in place.

OpenRune recompiles (almost) every clientscript from the osrs-dumps sources. Its compiler emits
PUSH_VARP/POP_VARP (opcodes 1/2) for varps whose symbol type is `long`, where Jagex's scripts use
PUSH_VAR_LONG/POP_VAR_LONG (64/65). The client's opcode 1 pushes the varp's int onto the int stack,
so a script that reads a long varp gets an extra int and one long too few: `ge_itemsink_get` (5733)
then returns obj 0 for an empty Grand Exchange slot and `ge_offers_switchpanel` shows the offer
status of "Dwarf remains" instead of the setup panel. Compared with OpenRS2 cache 2740 (rev 241):
Jagex's 5733 reads ge_itemsink_price_long_0 (5762) with opcode 64.

This rewrites opcode 1 -> 64 and 2 -> 65 where the operand is one of the long varps (the `long`
rows of osrs-dumps symbols/varp.sym, listed below), re-containers the changed groups (gzip),
appends them to the dat2 and updates idx12 and the archive-12 reference table (crc, sizes,
version). Scripts whose instruction walk does not end exactly at their instruction count are
skipped and reported. Usage: fix_long_varps.py [CACHE_DIR]   (default: src/.data/cache/LIVE)
"""
import gzip
import os
import struct
import sys
import zlib

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.join(HERE, "..", "..", "tools", "openrune"))
from cachediff import Disk, decompress  # noqa: E402
from hybridcache import write_group  # noqa: E402
import reftable  # noqa: E402

# osrs-dumps 2026-10-14-rev241 symbols/varp.sym, type `long`: ge_newoffer_price, ge_tax_slot_long_0-7,
# ge_itemsink_price_long_0-7.
LONG_VARPS = set(range(5753, 5770))
PUSH_VARP, POP_VARP, PUSH_VAR_LONG, POP_VAR_LONG = 1, 2, 64, 65
LCONST, SCONST = 61, 3


def patch(data):
    """Returns (patched bytes, changed count) or (None, reason) when the walk does not line up."""
    out = bytearray(data)
    p = data.index(0) + 1 if data[:1] == b"[" else (1 if data[:1] == b"\0" else 0)
    count = 0
    changed = 0
    end = len(data) - 2
    while p < end:
        if p + 4 <= len(data) and struct.unpack(">i", data[p:p + 4])[0] == count and _tail_ok(data, p):
            return bytes(out), changed
        op = struct.unpack(">H", data[p:p + 2])[0]
        q = p
        p += 2
        if op == SCONST:
            p = data.index(0, p) + 1
        elif op == LCONST:
            p += 8
        elif op < 100 and op not in (21, 38, 39):
            arg = struct.unpack(">i", data[p:p + 4])[0]
            if arg in LONG_VARPS and op in (PUSH_VARP, POP_VARP):
                out[q:q + 2] = struct.pack(">H", PUSH_VAR_LONG if op == PUSH_VARP else POP_VAR_LONG)
                changed += 1
            p += 4
        else:
            p += 1
        count += 1
    return None, "walk did not reach the instruction count"


def _tail_ok(data, p):
    # After the count: local/arg counts (u16s), the switch tables, then their u16 length.
    sw = struct.unpack(">H", data[-2:])[0]
    return len(data) - 2 - sw - p in range(12, 26)


def container(data, raw):
    comp = gzip.compress(data, mtime=0)
    c = bytes([2]) + struct.pack(">ii", len(comp), len(data)) + comp
    trailer = raw[5 + 4 + struct.unpack(">i", raw[1:5])[0]:] if raw[0] else raw[5 + struct.unpack(">i", raw[1:5])[0]:]
    return c, trailer


def main():
    cache = sys.argv[1] if len(sys.argv) > 1 else os.path.join(HERE, "..", "src", ".data", "cache", "LIVE")
    disk = Disk(cache)
    table = reftable.decode(decompress(disk.raw(255, 12)))
    dat_path = os.path.join(cache, "main_file_cache.dat2")
    idx_path = os.path.join(cache, "main_file_cache.idx12")
    dat = bytearray(open(dat_path, "rb").read())
    idx = bytearray(open(idx_path, "rb").read())
    fixed, skipped = {}, []
    for gid in sorted(table["groups"]):
        raw = disk.raw(12, gid)
        if raw is None:
            continue
        data = decompress(raw)
        if not any(struct.pack(">i", v) in data for v in LONG_VARPS):
            continue
        new, changed = patch(data)
        if new is None:
            skipped.append(gid)
            continue
        if not changed:
            continue
        c, trailer = container(new, raw)
        first = write_group(dat, c + trailer, 12, gid)
        idx[gid * 6:gid * 6 + 6] = len(c + trailer).to_bytes(3, "big") + first.to_bytes(3, "big")
        g = table["groups"][gid]
        g["crc"] = zlib.crc32(c)
        if "sizes" in g:
            g["sizes"] = (len(c), len(new))
        g["ver"] = (g["ver"] + 1) & 0xFFFFFFFF
        fixed[gid] = changed
    if fixed:
        table["version"] += 1
        body = reftable.encode(table)
        ref = bytes([0]) + struct.pack(">I", len(body)) + body
        first = write_group(dat, ref, 255, 12)
        idx255_path = os.path.join(cache, "main_file_cache.idx255")
        idx255 = bytearray(open(idx255_path, "rb").read())
        idx255[12 * 6:12 * 6 + 6] = len(ref).to_bytes(3, "big") + first.to_bytes(3, "big")
        open(dat_path, "wb").write(dat)
        open(idx_path, "wb").write(idx)
        open(idx255_path, "wb").write(idx255)
    print(f"long varps: rewrote {sum(fixed.values())} ops in {len(fixed)} scripts {sorted(fixed)}"
          + (f"; skipped (walk mismatch) {skipped}" if skipped else ""))


if __name__ == "__main__":
    main()
