#!/usr/bin/env python3
"""Headless rev-241 DESKTOP login against our test server (OpenRune), for the multi-client setup.

It speaks the desktop login protocol the way RuneLite does (client type 1, password auth), then
reads the game stream for a few seconds and checks what a desktop client must get:
  - login response 2 (OK) for a username/password account (OpenRune creates it on first login);
  - IF_OPENTOP with a desktop toplevel (161/548/165), not the mobile 601;
  - REBUILD_NORMAL and a steady PLAYER_INFO stream.
It prints the game messages (MESSAGE_GAME) and a packet histogram. No client packets are sent after
login, so the session changes nothing in the world; it disconnects at the end.

Usage: desktop_login.py [--host 127.0.0.1] [--port 43594] [--http http://127.0.0.1:8082]
                        [--user desktop] [--password desktop] [--seconds 8]
The RSA modulus comes from <http>/rsa. Packet sizes are parsed from RSProt's desktop GameServerProt
(openrune/rsprot, from `openrune/scripts/openrune.sh init`).
"""
import argparse, hashlib, os, re, secrets, socket, struct, sys, time, urllib.request, zlib
from pathlib import Path

REPO = Path(__file__).resolve().parents[2]
PROT_DIR = REPO / "openrune/rsprot/protocol/osrs-241/osrs-241-desktop/src/main/kotlin/net/rsprot/protocol/game/outgoing/prot"
REVISION = 241
DESKTOP_TOPLEVELS = {161: "toplevel_osrs_stretch", 548: "toplevel (fixed)", 165: "toplevel_pre_eoc"}
MOBILE_TOPLEVEL = 601

# --- protocol tables --------------------------------------------------------------------------------

def load_server_prots():
    ids = dict(re.findall(r"const val (\w+) = (\d+)", (PROT_DIR / "GameServerProtId.kt").read_text()))
    prots = {}
    for name, size in re.findall(r"^\s*(\w+)\(GameServerProtId\.\w+, ([^)]+)\)", (PROT_DIR / "GameServerProt.kt").read_text(), re.M):
        size = size.strip()
        size = -1 if size.endswith("VAR_BYTE") else -2 if size.endswith("VAR_SHORT") else int(size)
        prots[int(ids[name])] = (name, size)
    return prots

# --- crypto -----------------------------------------------------------------------------------------

M32 = 0xFFFFFFFF

class Isaac:
    """Port of RSProt's IsaacRandom (net.rsprot.crypto.cipher)."""
    def __init__(self, seed):
        self.rsl = [s & M32 for s in seed] + [0] * (256 - len(seed))
        self.mem = [0] * 256
        self.a = self.b = self.c = 0
        g = [0x9E3779B9] * 8
        def mix(v):
            a, b, c, d, e, f, gg, h = v
            a ^= (b << 11) & M32; d = (d + a) & M32; b = (b + c) & M32
            b ^= c >> 2;          e = (e + b) & M32; c = (c + d) & M32
            c ^= (d << 8) & M32;  f = (f + c) & M32; d = (d + e) & M32
            d ^= e >> 16;         gg = (gg + d) & M32; e = (e + f) & M32
            e ^= (f << 10) & M32; h = (h + e) & M32; f = (f + gg) & M32
            f ^= gg >> 4;         a = (a + f) & M32; gg = (gg + h) & M32
            gg ^= (h << 8) & M32; b = (b + gg) & M32; h = (h + a) & M32
            h ^= a >> 9;          c = (c + h) & M32; a = (a + b) & M32
            return [a, b, c, d, e, f, gg, h]
        for _ in range(4):
            g = mix(g)
        for src in (self.rsl, None):
            arr = self.rsl if src is not None else self.mem
            for i in range(0, 256, 8):
                g = [(g[k] + arr[i + k]) & M32 for k in range(8)]
                g = mix(g)
                self.mem[i:i + 8] = g
        self._isaac()
        self.count = 256

    def _isaac(self):
        mem, rsl = self.mem, self.rsl
        self.c = (self.c + 1) & M32
        a, b = self.a, (self.b + self.c) & M32
        shifts = ((13, True), (6, False), (2, True), (16, False))
        for i in range(256):
            x = mem[i]
            s, left = shifts[i & 3]
            a = (a ^ ((a << s) & M32)) if left else (a ^ (a >> s))
            a = (a + mem[(i + 128) & 255]) & M32
            y = (mem[(x >> 2) & 255] + a + b) & M32
            mem[i] = y
            b = (mem[(y >> 10) & 255] + x) & M32
            rsl[i] = b
        self.a, self.b = a, b

    def next(self):
        if self.count == 0:
            self._isaac()
            self.count = 256
        self.count -= 1
        return self.rsl[self.count]

def xtea_encrypt(data, key):
    out = bytearray(data)
    for off in range(0, len(data) - len(data) % 8, 8):
        v0, v1 = struct.unpack_from(">II", out, off)
        s = 0
        for _ in range(32):
            v0 = (v0 + ((((v1 << 4) & M32 ^ v1 >> 5) + v1) & M32 ^ (s + key[s & 3]) & M32)) & M32
            s = (s + 0x9E3779B9) & M32
            v1 = (v1 + ((((v0 << 4) & M32 ^ v0 >> 5) + v0) & M32 ^ (s + key[(s >> 11) & 3]) & M32)) & M32
        struct.pack_into(">II", out, off, v0, v1)
    return bytes(out)

# --- helpers ----------------------------------------------------------------------------------------

def jstr(s):
    return s.encode("cp1252") + b"\0"

def jstr2(s):
    return b"\0" + jstr(s)

def p4alt1(v):  # little endian
    return struct.pack("<I", v & M32)

def p4alt2(v):  # bytes 2,3,0,1 (inverse of g4Alt2)
    v &= M32
    return bytes([(v >> 8) & 255, v & 255, (v >> 24) & 255, (v >> 16) & 255])

def p4alt3(v):  # bytes 1,0,3,2 (inverse of g4Alt3)
    v &= M32
    return bytes([(v >> 16) & 255, (v >> 24) & 255, v & 255, (v >> 8) & 255])

def p4(v):
    return struct.pack(">I", v & M32)

class Conn:
    def __init__(self, host, port):
        self.s = socket.create_connection((host, port), timeout=10)

    def exact(self, n):
        buf = b""
        while len(buf) < n:
            chunk = self.s.recv(n - len(buf))
            if not chunk:
                raise EOFError(f"connection closed after {len(buf)}/{n} bytes")
            buf += chunk
        return buf

def js5_crcs(host, port):
    """Archive CRCs from the JS5 master index (the login block carries them)."""
    c = Conn(host, port)
    c.s.sendall(struct.pack(">BI4I", 15, REVISION, 0, 0, 0, 0))
    if c.exact(1)[0] != 0:
        raise SystemExit("JS5 handshake refused")
    c.s.sendall(struct.pack(">BBH", 1, 255, 255))
    c.exact(3)
    compression, length = struct.unpack(">BI", c.exact(5))
    data, pos = b"", 8
    while len(data) < length:
        if pos == 512:
            c.exact(1); pos = 1
        n = min(512 - pos, length - len(data))
        data += c.exact(n); pos += n
    c.s.close()
    crcs = [struct.unpack_from(">I", data, i)[0] for i in range(0, len(data), 8)]
    return (crcs + [0] * 23)[:23]

DESKTOP_CRC_ORDER = [  # DesktopLoginCrcDecoder.decodeLive: (index, encoding)
    (5, p4alt3), (4, p4alt3), (1, p4), (17, p4), (21, p4alt2), (9, p4alt3), (8, p4alt1),
    (22, p4alt2), (15, p4alt3), (19, p4alt1), (7, p4alt3), (0, p4alt3), (18, p4alt3), (12, p4alt3),
    (11, p4alt1), (14, p4alt2), (3, p4), (13, p4alt3), (2, p4), (10, p4alt2), (6, p4), (16, p4),
    (20, p4alt1),
]

def host_platform_stats():
    b = bytes([9, 1, 1]) + struct.pack(">H", 10)          # version, os windows, 64-bit, os version
    b += bytes([1, 17, 0, 12, 1])                          # java vendor/version, not an applet (1)
    b += struct.pack(">HB", 1024, 8) + (8192).to_bytes(3, "big") + struct.pack(">H", 3000)
    b += jstr2("") + jstr2("") + jstr2("") + jstr2("")    # gpu names/versions
    b += bytes([0]) + struct.pack(">H", 0)                 # driver month/year
    b += jstr2("") + jstr2("") + bytes([8, 8]) + p4(0) * 3 + p4(0)
    b += jstr2("") + jstr2("")                             # client name, device name
    return b

def solve_pow(version, difficulty, salt):
    base = f"{version:x}{difficulty:x}{salt}"
    nonce = 0
    while True:
        h = hashlib.sha256((base + f"{nonce:x}").encode()).digest()
        bits = 0
        for byte in h:
            if byte == 0:
                bits += 8
                continue
            bits += 8 - byte.bit_length()
            break
        if bits >= difficulty:
            return nonce
        nonce += 1

def login(args, modulus, crcs):
    c = Conn(args.host, args.port)
    c.s.sendall(bytes([14]))                     # INIT_GAME_CONNECTION
    status = c.exact(1)[0]
    if status != 0:
        raise SystemExit(f"init game connection refused: {status}")
    session_id = struct.unpack(">Q", c.exact(8))[0]

    seed = [secrets.randbits(32) for _ in range(4)]
    rsa = bytes([1]) + b"".join(p4(s) for s in seed) + struct.pack(">Q", session_id)
    rsa += bytes([2]) + b"\0\0\0\0"              # OTP none
    rsa += bytes([0]) + jstr(args.password)       # password authentication
    enc = pow(int.from_bytes(rsa, "big"), 0x10001, modulus)
    enc = enc.to_bytes((enc.bit_length() + 8) // 8, "big")  # like BigInteger.toByteArray

    xb = jstr(args.user) + bytes([2]) + struct.pack(">HH", 765, 503) + bytes(24)
    xb += jstr("") + p4(0) + bytes([0]) + host_platform_stats() + bytes([1]) + p4(0)
    xb += b"".join(f(crcs[i]) for i, f in DESKTOP_CRC_ORDER)
    xb = xtea_encrypt(xb, seed)

    body = p4(REVISION) + p4(1) + p4(0) + bytes([1, 0, 0])   # rev, sub, server ver, DESKTOP, platform, ext
    body += struct.pack(">H", len(enc)) + enc + xb
    c.s.sendall(bytes([16]) + struct.pack(">H", len(body)) + body)

    code = c.exact(1)[0]
    if code == 69:                               # proof of work
        size = struct.unpack(">H", c.exact(2))[0]
        pow_data = c.exact(size)
        kind, version, difficulty = pow_data[0], pow_data[1], pow_data[2]
        salt = pow_data[3:pow_data.index(0, 3)].decode()
        t = time.time()
        nonce = solve_pow(version, difficulty, salt)
        print(f"proof of work: type {kind}, difficulty {difficulty}, solved in {time.time() - t:.1f}s")
        c.s.sendall(bytes([19]) + struct.pack(">HQ", 8, nonce))
        code = c.exact(1)[0]
    if code != 2:
        c.s.close()
        return None, None, code
    ok = c.exact(c.exact(1)[0])
    if os.environ.get("DEBUG"):
        print("OK payload", ok.hex())
    index = struct.unpack_from(">H", ok, 7)[0]
    print(f"login OK: user '{args.user}', player index {index}, staff {ok[5]}")
    return c, seed, ok


# --- main -------------------------------------------------------------------------------------------

def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--host", default="127.0.0.1")
    ap.add_argument("--port", type=int, default=43594)
    ap.add_argument("--http", default="http://127.0.0.1:8082")
    ap.add_argument("--user", default="desktop")
    ap.add_argument("--password", default="desktop")
    ap.add_argument("--seconds", type=float, default=8)
    args = ap.parse_args()

    prots = load_server_prots()
    modulus = int(urllib.request.urlopen(f"{args.http}/rsa", timeout=5).read().decode().strip(), 16)
    crcs = js5_crcs(args.host, args.port)
    print(f"modulus from {args.http}/rsa ({modulus.bit_length()} bits); JS5 CRCs: {len(crcs)}")

    for attempt in range(15):
        c, seed, ok = login(args, modulus, crcs)
        code = 2 if c else ok
        if code != 5:
            break
        print("login response 5 (account still online from a previous run); retrying in 5 s")
        time.sleep(5)
    if code != 2:
        raise SystemExit(f"FAIL: login response {code} (3 = invalid username/password, 5 = already online)")

    isaac = Isaac([(s + 50) & M32 for s in seed])
    counts, toplevel, messages = {}, None, []
    c.s.settimeout(0.5)
    end = time.time() + args.seconds
    # The OK block is 34 bytes; its length byte also covers the header (opcode + size) of the
    # first game packet, REBUILD_NORMAL, which the client reads together with it.
    buf = ok[34:]

    def need(n):
        nonlocal buf
        while len(buf) < n:
            if time.time() > end:
                raise TimeoutError
            try:
                chunk = c.s.recv(65536)
            except socket.timeout:
                continue
            if not chunk:
                raise EOFError
            buf += chunk
        out, buf = buf[:n], buf[n:]
        return out

    try:
        while True:
            op = (need(1)[0] - isaac.next()) & 255
            if op >= 128:
                op = ((op - 128) << 8) + ((need(1)[0] - isaac.next()) & 255)
            print("op", op, prots.get(op)) if os.environ.get("DEBUG") else None
            if op not in prots:
                raise SystemExit(f"FAIL: unknown opcode {op} (stream out of sync)")
            name, size = prots[op]
            if size == -1:
                size = need(1)[0]
            elif size == -2:
                size = struct.unpack(">H", need(2))[0]
            payload = need(size)
            counts[name] = counts.get(name, 0) + 1
            if name == "IF_OPENTOP":
                v = struct.unpack(">H", payload)[0]
                toplevel = (v & 0xFF00) | ((v - 128) & 0xFF)   # p2Alt2
            elif name == "MESSAGE_GAME":
                p = 1 if payload[0] < 128 else 2
                has_name = payload[p]; p += 1
                if has_name:
                    p = payload.index(0, p) + 1
                messages.append(payload[p:payload.index(0, p)].decode("cp1252"))
    except (TimeoutError, EOFError) as e:
        if isinstance(e, EOFError):
            print("server closed the connection")
    c.s.close()

    print("game messages:", messages)
    print("packets:", ", ".join(f"{k}x{v}" for k, v in sorted(counts.items(), key=lambda kv: -kv[1])))
    checks = [
        ("IF_OPENTOP is a desktop toplevel", toplevel in DESKTOP_TOPLEVELS,
         f"{toplevel} ({DESKTOP_TOPLEVELS.get(toplevel, 'mobile' if toplevel == MOBILE_TOPLEVEL else '?')})"),
        ("REBUILD_NORMAL received", any(k.startswith("REBUILD_NORMAL") for k in counts), ""),
        ("PLAYER_INFO stream", counts.get("PLAYER_INFO", 0) >= 3, f"{counts.get('PLAYER_INFO', 0)} packets"),
    ]
    for label, good, info in checks:
        print(f"  {'ok  ' if good else 'FAIL'} {label} {info}")
    print("RESULT:", "PASS" if all(g for _, g, _ in checks) else "FAIL")
    sys.exit(0 if all(g for _, g, _ in checks) else 1)

if __name__ == "__main__":
    main()
