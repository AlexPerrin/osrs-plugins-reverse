"""Decode `npcprobe` logcat lines (native/src/npc_probe.cpp, setprop debug.osrsrl.npcprobe 1) the way the
client reader 0x469778 reads them. Usage: adb logcat -d > l.txt; python3 npc_info_decode.py l.txt [-v]
(-v prints every packet, else only adds/removals/count changes)."""
import re, sys
CLOCK = [18, 19, 20, 32]; TYPE = [12, 14, 17, 24]
class Bits:
    def __init__(s, b): s.b, s.p = b, 0
    def get(s, n):
        v = 0
        for _ in range(n):
            v = (v << 1) | ((s.b[s.p >> 3] >> (7 - (s.p & 7))) & 1); s.p += 1
        return v
    def left(s): return len(s.b) * 8 - s.p
parts = {}
for line in open(sys.argv[1], errors='replace'):
    m = re.search(r'npcprobe large (\d) pos (\d+) len (\d+) end (\d+) hires (-?\d+)->(-?\d+) part (\d+) ?([0-9a-f]*)', line)
    if not m: continue
    large, pos, ln, end, h0, h1, part, hx = m.groups()
    key = line[:18]
    if part == '0': cur = [line[:18], int(large), int(h0), int(h1), hx]; parts[len(parts)] = cur
    else: cur[4] += hx
for k, (t, large, h0, h1, hx) in parts.items():
    b = Bits(bytes.fromhex(hx)); out = []
    try:
        count = b.get(8); upd = []
        for i in range(count):
            if b.get(1) == 0: continue
            k2 = b.get(2)
            if k2 == 0: upd.append('ext')
            elif k2 == 1: d = b.get(3); e = b.get(1); upd.append(f'walk{d}' + ('+ext' if e else ''))
            elif k2 == 2: c = b.get(1); d = b.get(3); d2 = b.get(3) if c else None; e = b.get(1); upd.append(f'run{d},{d2}' + ('+ext' if e else ''))
            else: upd.append(f'remove#{i}')
        adds = []
        while b.left() > 27:
            idx = b.get(16)
            if idx == 0xffff: adds.append('END'); break
            n = 8 if large else 6
            dx = b.get(n); dz = b.get(n)
            half = 1 << (n - 1)
            if dx >= half + (0 if large else 1): dx -= 1 << n
            if dz >= half + (0 if large else 1): dz -= 1 << n
            clock = b.get(CLOCK[b.get(2)]) if b.get(1) else None
            typ = b.get(TYPE[b.get(2)])
            ext = b.get(1); d = b.get(3); jump = b.get(1)
            adds.append(f'add idx={idx} d=({dx},{dz}) clock={clock} type={typ} ext={ext} dir={d} jump={jump}')
        out = f'count={count} upd={upd} adds={adds} bitsleft={b.left()}'
    except IndexError:
        out = f'OVERRUN at bit {b.p}'
    interesting = h0 != h1 or 'add' in out or 'remove' in out or '-v' in sys.argv
    if interesting: print(t, f'large={large} hires {h0}->{h1}', out)
