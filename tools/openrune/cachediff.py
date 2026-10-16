#!/usr/bin/env python3
"""OpenRune spike: compares two OSRS disk caches (dat2/idx) archive by archive.

For every archive it prints the reference-table CRC (what the JS5 master index and the login CRC
block carry) and how many groups were added, removed or changed (CRC or version differ).
Usage: cachediff.py STOCK_DIR OTHER_DIR [-v ARCHIVE]   (-v lists the changed group ids)
"""
import bz2, gzip, os, struct, sys, zlib


class Disk:
    def __init__(self, d):
        self.dat = open(os.path.join(d, "main_file_cache.dat2"), "rb").read()
        self.d = d

    def raw(self, archive, group):
        p = os.path.join(self.d, f"main_file_cache.idx{archive}")
        if not os.path.exists(p):
            return None
        idx = open(p, "rb").read()
        if (group + 1) * 6 > len(idx):
            return None
        size = int.from_bytes(idx[group * 6:group * 6 + 3], "big")
        sector = int.from_bytes(idx[group * 6 + 3:group * 6 + 6], "big")
        if size == 0 or sector == 0:
            return None
        big = group > 0xFFFF
        hdr, chunk = (10, 510) if big else (8, 512)
        out = bytearray()
        while len(out) < size:
            o = sector * 520
            nxt = int.from_bytes(self.dat[o + hdr - 4:o + hdr - 1], "big")
            n = min(chunk, size - len(out))
            out += self.dat[o + hdr:o + hdr + n]
            sector = nxt
        return bytes(out)


def decompress(c):
    t, ln = c[0], struct.unpack(">i", c[1:5])[0]
    if t == 0:
        return c[5:5 + ln]
    if t == 1:
        return bz2.decompress(b"BZh1" + c[9:9 + ln])
    if t == 2:
        return gzip.decompress(c[9:9 + ln])
    raise ValueError(f"compression {t}")


def ref_table(data):
    pos = 0

    def u(n):
        nonlocal pos
        v = int.from_bytes(data[pos:pos + n], "big"); pos += n; return v

    def smart_or_short(p):
        nonlocal pos
        if p >= 7:
            return u(4) & 0x7FFFFFFF if data[pos] & 0x80 else u(2)
        return u(2)

    proto = u(1)
    version = u(4) if proto >= 6 else 0
    flags = u(1)
    count = smart_or_short(proto)
    ids, acc = [], 0
    for _ in range(count):
        acc += smart_or_short(proto); ids.append(acc)
    if flags & 1:
        pos += 4 * count
    crcs = [u(4) for _ in ids]
    if flags & 8:
        pos += 4 * count
    if flags & 2:
        pos += 64 * count
    if flags & 4:
        pos += 8 * count
    versions = [u(4) for _ in ids]
    return version, {g: (c, v) for g, c, v in zip(ids, crcs, versions)}


def main():
    a, b = Disk(sys.argv[1]), Disk(sys.argv[2])
    verbose = int(sys.argv[4]) if len(sys.argv) > 4 and sys.argv[3] == "-v" else None
    print(f"{'arch':>4} {'stock crc':>10} {'other crc':>10}  groups  added removed changed")
    for arch in range(0, 30):
        ra, rb = a.raw(255, arch), b.raw(255, arch)
        if ra is None and rb is None:
            continue
        if ra is None or rb is None:
            print(f"{arch:>4} only in {'other' if ra is None else 'stock'}")
            continue
        ca, cb = zlib.crc32(ra), zlib.crc32(rb)
        (_, ga), (_, gb) = ref_table(decompress(ra)), ref_table(decompress(rb))
        added = sorted(set(gb) - set(ga)); removed = sorted(set(ga) - set(gb))
        changed = sorted(g for g in set(ga) & set(gb) if ga[g] != gb[g])
        same = "same" if ca == cb else ""
        print(f"{arch:>4} {ca:>10x} {cb:>10x}  {len(gb):>6} {len(added):>6} {len(removed):>7} {len(changed):>7} {same}")
        if verbose == arch:
            print("  added", added[:200]); print("  removed", removed[:200]); print("  changed", changed[:400])


if __name__ == "__main__":
    main()


def content_diff(stock_dir, other_dir, arch):
    """Groups of ARCH whose decompressed bytes differ (recompression alone does not count)."""
    a, b = Disk(stock_dir), Disk(other_dir)
    _, ga = ref_table(decompress(a.raw(255, arch)))
    _, gb = ref_table(decompress(b.raw(255, arch)))
    diff = []
    for g in sorted(set(ga) & set(gb)):
        ra, rb = a.raw(arch, g), b.raw(arch, g)
        if ra is None or rb is None:
            diff.append(g); continue
        try:
            if decompress(ra) != decompress(rb):
                diff.append(g)
        except Exception:
            diff.append(g)
    return diff, sorted(set(gb) - set(ga))
