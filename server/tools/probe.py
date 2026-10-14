#!/usr/bin/env python3
"""Headless JS5 probe: handshake, fetch the master index and verify each archive's CRC."""
import socket, struct, sys, zlib

host = sys.argv[1] if len(sys.argv) > 1 else "127.0.0.1"
port = int(sys.argv[2]) if len(sys.argv) > 2 else 43594
rev = int(sys.argv[3]) if len(sys.argv) > 3 else 241

s = socket.create_connection((host, port), timeout=10)
s.sendall(struct.pack(">BI4I", 15, rev, 0, 0, 0, 0))
status = s.recv(1)[0]
print("handshake status", status)
if status != 0:
    sys.exit(1)

def recv_exact(n):
    buf = b""
    while len(buf) < n:
        chunk = s.recv(n - len(buf))
        if not chunk:
            raise EOFError
        buf += chunk
    return buf

def request(archive, group):
    s.sendall(struct.pack(">BBH", 1, archive, group))
    a, g = struct.unpack(">BH", recv_exact(3))
    assert (a, g) == (archive, group), (a, g)
    compression, length = struct.unpack(">BI", recv_exact(5))
    total = length + (0 if compression == 0 else 4)
    data = bytearray(struct.pack(">BI", compression, length))
    pos = 8  # bytes of the current 512-byte block consumed (3 header + 5)
    while len(data) < 5 + total:
        if pos == 512:
            assert recv_exact(1) == b"\xff"
            pos = 1
        n = min(512 - pos, 5 + total - len(data))
        data += recv_exact(n)
        pos += n
    return bytes(data)

master = request(255, 255)
body = master[5:]
entries = [struct.unpack(">II", body[i:i + 8]) for i in range(0, len(body), 8)]
print(f"master index: {len(entries)} archives")
bad = 0
for archive, (crc, version) in enumerate(entries):
    if crc == 0:
        continue
    table = request(255, archive)
    ok = (zlib.crc32(table) & 0xFFFFFFFF) == crc
    bad += not ok
    print(f"  archive {archive:3d} crc {crc:08x} version {version:10d} table {len(table):7d} bytes {'ok' if ok else 'CRC MISMATCH'}")
sample = request(2, 8)
print("sample group 2/8:", len(sample), "bytes")
print("RESULT:", "PASS" if bad == 0 else f"FAIL ({bad} mismatches)")
