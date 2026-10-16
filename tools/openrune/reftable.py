"""OpenRune spike: full JS5 reference-table decode/encode (protocols 5-7), for group-level hybrids."""
import struct


def decode(data):
    pos = 0

    def u(n):
        nonlocal pos
        v = int.from_bytes(data[pos:pos + n], "big"); pos += n; return v

    def sm(p):
        return (u(4) & 0x7FFFFFFF) if (p >= 7 and data[pos] & 0x80) else u(2)

    t = {"proto": u(1)}
    t["version"] = u(4) if t["proto"] >= 6 else 0
    t["flags"] = fl = u(1)
    n = sm(t["proto"]); ids = []; a = 0
    for _ in range(n):
        a += sm(t["proto"]); ids.append(a)
    g = {i: {} for i in ids}
    if fl & 1:
        for i in ids: g[i]["name"] = u(4)
    for i in ids: g[i]["crc"] = u(4)
    if fl & 8:
        for i in ids: g[i]["ucrc"] = u(4)
    if fl & 2:
        for i in ids: g[i]["wp"] = data[pos:pos + 64]; pos += 64
    if fl & 4:
        for i in ids: g[i]["sizes"] = (u(4), u(4))
    for i in ids: g[i]["ver"] = u(4)
    cnt = {i: sm(t["proto"]) for i in ids}
    for i in ids:
        a = 0; kids = []
        for _ in range(cnt[i]):
            a += sm(t["proto"]); kids.append(a)
        g[i]["kids"] = kids
    if fl & 1:
        for i in ids: g[i]["knames"] = [u(4) for _ in g[i]["kids"]]
    t["groups"] = g
    return t


def encode(t):
    p, fl = t["proto"], t["flags"]
    out = bytearray([p])
    if p >= 6: out += struct.pack(">I", t["version"] & 0xFFFFFFFF)
    out.append(fl)

    def sm(v):
        out.extend(struct.pack(">I", v | 0x80000000) if (p >= 7 and v >= 0x8000) else struct.pack(">H", v))

    ids = sorted(t["groups"]); g = t["groups"]
    sm(len(ids)); last = 0
    for i in ids: sm(i - last); last = i
    if fl & 1:
        for i in ids: out += struct.pack(">I", g[i]["name"] & 0xFFFFFFFF)
    for i in ids: out += struct.pack(">I", g[i]["crc"] & 0xFFFFFFFF)
    if fl & 8:
        for i in ids: out += struct.pack(">I", g[i]["ucrc"] & 0xFFFFFFFF)
    if fl & 2:
        for i in ids: out += g[i]["wp"]
    if fl & 4:
        for i in ids: out += struct.pack(">II", *g[i]["sizes"])
    for i in ids: out += struct.pack(">I", g[i]["ver"] & 0xFFFFFFFF)
    for i in ids: sm(len(g[i]["kids"]))
    for i in ids:
        last = 0
        for k in g[i]["kids"]: sm(k - last); last = k
    if fl & 1:
        for i in ids:
            for h in g[i]["knames"]: out += struct.pack(">I", h & 0xFFFFFFFF)
    return bytes(out)
