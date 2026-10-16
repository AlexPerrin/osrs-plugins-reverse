#!/usr/bin/env python3
"""OpenRune spike: builds a disk cache that is STOCK except for whole archives taken from OTHER.

Used to bisect which of OpenRune's repacked archives the Android client rejects: serve the result
with our own server image (JS5 only) and see whether the app still crashes.
Usage: hybridcache.py STOCK_DIR OTHER_DIR OUT_DIR SPEC[;SPEC...]
SPEC is ARCH (whole archive from OTHER), ARCH:-G,G (from OTHER except groups G from STOCK) or
ARCH:+G,G (STOCK plus groups G from OTHER). Mixed archives get a re-encoded reference table.
"""
import os, shutil, sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from cachediff import Disk, decompress  # noqa: E402
import reftable  # noqa: E402
import struct, zlib  # noqa: E402


def write_group(dat, data, archive, group):
    """Appends one group container as a sector chain; returns the first sector."""
    if len(dat) % 520:
        dat.extend(b"\0" * (520 - len(dat) % 520))
    first = len(dat) // 520
    big = group > 0xFFFF
    chunk = 510 if big else 512
    parts = [data[i:i + chunk] for i in range(0, len(data), chunk)] or [b""]
    for n, part in enumerate(parts):
        sector = first + n
        nxt = sector + 1 if n + 1 < len(parts) else 0
        hdr = (group.to_bytes(4 if big else 2, "big") + n.to_bytes(2, "big")
               + nxt.to_bytes(3, "big") + bytes([archive]))
        dat.extend((hdr + part).ljust(520, b"\0"))
    return first


def main():
    stock, other, out = sys.argv[1:4]
    specs = [x for x in sys.argv[4].split(";") if x]
    os.makedirs(out, exist_ok=True)
    for f in os.listdir(stock):
        if f.startswith("main_file_cache.idx"):
            shutil.copy(os.path.join(stock, f), os.path.join(out, f))
    st, o = Disk(stock), Disk(other)
    dat = bytearray(open(os.path.join(stock, "main_file_cache.dat2"), "rb").read())
    idx255 = bytearray(open(os.path.join(out, "main_file_cache.idx255"), "rb").read())
    for spec in specs:
        arch, _, sel = spec.partition(":")
        arch = int(arch)
        ts = reftable.decode(decompress(st.raw(255, arch)))
        to = reftable.decode(decompress(o.raw(255, arch)))
        picked = {int(g) for g in sel[1:].split(",") if g} if sel else set()
        if not sel:
            src = {g: "o" for g in to["groups"]}
        elif sel[0] == "-":
            src = {g: "o" for g in to["groups"]}
            src.update({g: "s" for g in picked if g in ts["groups"]})
        else:
            src = {g: "s" for g in ts["groups"]}
            src.update({g: "o" for g in picked if g in to["groups"]})
        merged = dict(to); merged["groups"] = {}
        maxg = max(src) + 1
        idx = bytearray(6 * maxg)
        for g, w in src.items():
            disk, tab = (o, to) if w == "o" else (st, ts)
            raw = disk.raw(arch, g)
            entry = dict(tab["groups"][g])
            if raw is None:
                continue
            entry["crc"] = zlib.crc32(raw[:-2]) if tab["groups"][g]["crc"] != zlib.crc32(raw) else zlib.crc32(raw)
            merged["groups"][g] = entry
            first = write_group(dat, raw, arch, g)
            idx[g * 6:g * 6 + 6] = len(raw).to_bytes(3, "big") + first.to_bytes(3, "big")
        open(os.path.join(out, f"main_file_cache.idx{arch}"), "wb").write(idx)
        if sel:
            merged["version"] = to["version"] + 1
            body = reftable.encode(merged)
            ref = bytes([0]) + struct.pack(">I", len(body)) + body
        else:
            ref = o.raw(255, arch)
        first = write_group(dat, ref, 255, arch)
        idx255[arch * 6:arch * 6 + 6] = len(ref).to_bytes(3, "big") + first.to_bytes(3, "big")
        n_o = sum(1 for w in src.values() if w == "o")
        print(f"archive {arch}: {n_o} groups from OTHER, {len(src) - n_o} from STOCK")
    open(os.path.join(out, "main_file_cache.idx255"), "wb").write(idx255)
    open(os.path.join(out, "main_file_cache.dat2"), "wb").write(dat)


if __name__ == "__main__":
    main()
