#!/usr/bin/env python3
"""Computes project progress from the code and writes docs/progress.md + docs/progress-history.csv.

Metrics (all counted from source, nothing hand-maintained):
- Mobile protocol: opcodes named in the Android prot enums (identified) and opcodes with a real
  codec bound in AndroidRepositories.kt (implemented), against the client's own size tables
  (151 server->client, 102 client->server opcodes).
- Zone sub-prots: rows of the zone table in docs/android-protocol.md with a payload layout.
- RuneLite API: abstract methods of each interface the host backs that the Android* class
  implements (tools/progress/ApiCoverage.java; needs host/build.sh output in re/host/classes).

Usage: tools/progress.py [--no-history]   (needs server/rsprot: server/scripts/rsprot.sh init)
"""
import csv
import datetime
import os
import re
import subprocess
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ANDROID = os.path.join(ROOT, "server/rsprot/protocol/osrs-241/osrs-241-android/src/main/kotlin/net/rsprot/protocol/android")
S2C_TOTAL, C2S_TOTAL, ZONE_TOTAL = 151, 102, 15
SDK = os.environ.get("ANDROID_SDK", "/mnt/c/Users/Alex/AppData/Local/Android/Sdk")
API_PAIRS = [
    ("Client", "AndroidClient"), ("Player", "AndroidPlayer"), ("NPC", "AndroidNPC"),
    ("WorldView", "AndroidWorldView"), ("Scene", "AndroidScene"), ("Tile", "AndroidScene$Tile"),
    ("widgets.Widget", "AndroidWidget"), ("ItemContainer", "AndroidItemContainer"),
    ("ItemComposition", "AndroidItemComposition"), ("NPCComposition", "AndroidNPCComposition"),
    ("EnumComposition", "AndroidEnum"), ("MessageNode", "AndroidMessageNode"),
    ("Menu", "AndroidMenu"), ("MenuEntry", "AndroidMenuEntry"),
]


def read(path):
    with open(path) as f:
        return f.read()


def enum_entries(src):
    return re.findall(r"^\s+([A-Z][A-Z0-9_]*)\((\d+),", src, re.M)


def protocol():
    s2c = enum_entries(read(os.path.join(ANDROID, "AndroidGameServerProt.kt")))
    c2s = enum_entries(read(os.path.join(ANDROID, "AndroidGameClientProt.kt")))
    c2s_named = [n for n, _ in c2s if not re.fullmatch(r"OP_\d+", n)]
    repo = read(os.path.join(ANDROID, "AndroidRepositories.kt"))
    enc, dec = repo.split("object AndroidGameMessageDecoderRepository")[:2]
    s2c_impl = len(re.findall(r"^\s+bind(?:WithAlts)?\(", enc, re.M))
    # Decoders: single binds plus the OPLOC/OPOBJ/OPNPC tables (one decoder per row).
    dec_build = dec.split("fun build")[1].split("for (prot in")[0]
    c2s_impl = len(re.findall(r"^\s+bind\(", dec_build, re.M))
    for table in re.findall(r"in (ANDROID_OP\w+)\)", dec_build):
        defs = "".join(read(os.path.join(ANDROID, f)) for f in os.listdir(ANDROID) if f.endswith(".kt"))
        body = re.search(table + r"[^=]*=\s*listOf\((.*?)\n\s*\)", defs, re.S)
        c2s_impl += len(re.findall(r"Triple\(", body.group(1))) if body else 0
    return {
        "s2c_ident": len(s2c), "s2c_impl": s2c_impl,
        "c2s_ident": len(c2s_named), "c2s_impl": c2s_impl,
        "s2c_unknown": sorted(set(range(S2C_TOTAL)) - {int(o) for _, o in s2c}),
        "c2s_unknown": [int(o) for n, o in c2s if re.fullmatch(r"OP_\d+", n)],
    }


def zone():
    doc = read(os.path.join(ROOT, "docs/android-protocol.md")).split("## Zone packets")[1].split("\n## ")[0]
    rows = re.findall(r"^\| (\d+) \| (\w+) \|[^|]*\|([^|]*)\|", doc, re.M)
    return sum(1 for _, _, payload in rows if payload.strip()), [n for _, n, p in rows if p.strip()]


def api():
    rl = os.path.join(ROOT, "re/runelite")
    cp = ":".join([os.path.join(ROOT, "re/host/classes")] +
                  [os.path.join(rl, j) for j in sorted(os.listdir(rl)) if j.endswith(".jar")] +
                  [os.path.join(SDK, "platforms/android-37.0/android.jar")])
    args = [a for pair in API_PAIRS for a in pair]
    out = subprocess.run(["java", "-cp", cp, os.path.join(ROOT, "tools/progress/ApiCoverage.java")] + args,
                         capture_output=True, text=True, check=True).stdout
    rows = []
    for line in out.splitlines():
        iface, impl, done, total, *_ = line.split()
        rows.append((iface.split(".")[-1], int(done), int(total)))
    return rows


def pct(a, b):
    return 100.0 * a / b if b else 0.0


def bar(p, width=20):
    n = round(p / 100 * width)
    return "█" * n + "░" * (width - n)


def git(*args):
    return subprocess.run(["git", "-C", ROOT] + list(args), capture_output=True, text=True).stdout.strip()


def main():
    p = protocol()
    z_done, z_names = zone()
    rows = api()
    api_done, api_total = sum(r[1] for r in rows), sum(r[2] for r in rows)
    ident = p["s2c_ident"] + p["c2s_ident"]
    impl = p["s2c_impl"] + p["c2s_impl"]
    total = S2C_TOTAL + C2S_TOTAL
    commit = git("rev-parse", "--short", "HEAD")
    dirty = "+" if git("status", "--porcelain", "--untracked-files=no") else ""
    today = datetime.date.today().isoformat()

    head = [
        ("Mobile packets mapped", ident, total, "named opcodes, both directions, of the client's size tables"),
        ("Mobile packets implemented", impl, total, "opcodes with an Android codec bound on the server"),
        ("Server→client mapped", p["s2c_ident"], S2C_TOTAL, "AndroidGameServerProt"),
        ("Client→server mapped", p["c2s_ident"], C2S_TOTAL, "AndroidGameClientProt (not OP_n)"),
        ("Zone sub-prots implemented", z_done, ZONE_TOTAL, ", ".join(z_names)),
        ("RuneLite API implemented", api_done, api_total, "abstract methods of the interfaces below"),
    ]
    md = [
        "# Progress",
        "",
        f"Generated by `tools/progress.py` on {today} at `{commit}{dirty}`. Do not edit by hand;",
        "history is in [`progress-history.csv`](progress-history.csv). Percentages are of what the",
        "project can enumerate: the Android client's own opcode size tables, and the RuneLite API",
        "interfaces the plugin host backs (new interfaces raise the denominator, like AnyPS5's",
        "\"functions known to the project\").",
        "",
        "| Metric | Done | Total | % | |",
        "|---|---:|---:|---:|---|",
    ]
    for name, a, b, note in head:
        md.append(f"| **{name}** | {a} | {b} | {pct(a, b):.1f}% | `{bar(pct(a, b))}` |")
    md += ["", "Notes: " + "; ".join(f"*{n}*: {note}" for n, _, _, note in head), ""]
    md += ["## RuneLite API by interface", "", "| Interface | Implemented | Total | % |", "|---|---:|---:|---:|"]
    for iface, a, b in sorted(rows, key=lambda r: -pct(r[1], r[2])):
        md.append(f"| `{iface}` | {a} | {b} | {pct(a, b):.0f}% |")
    md += ["", "## Unmapped opcodes", "",
           f"- Client→server ({len(p['c2s_unknown'])}): " + ", ".join(map(str, p["c2s_unknown"])),
           f"- Server→client ({len(p['s2c_unknown'])}): " + ", ".join(map(str, p["s2c_unknown"])),
           "",
           "Some server→client opcodes are recognised in `docs/android-protocol.md` but not yet in the",
           "enum; they count once added there.", ""]
    with open(os.path.join(ROOT, "docs/progress.md"), "w") as f:
        f.write("\n".join(md))

    readme = os.path.join(ROOT, "README.md")
    if os.path.exists(readme):
        text = read(readme)
        badges = " ".join(
            f"![{n}](https://img.shields.io/badge/{n.replace(' ', '%20').replace('→', '%E2%86%92')}-{pct(a, b):.1f}%25-blue)"
            for n, a, b, _ in head[:2] + head[5:])
        block = (f"<!-- progress:start -->\n{badges}\n\n"
                 + "\n".join(f"- **{n}**: {a}/{b} ({pct(a, b):.1f}%)" for n, a, b, _ in head)
                 + f"\n\nDetails: [docs/progress.md](docs/progress.md) (as of {today}, `{commit}{dirty}`).\n<!-- progress:end -->")
        text = re.sub(r"<!-- progress:start -->.*?<!-- progress:end -->", lambda _: block, text, flags=re.S)
        with open(readme, "w") as f:
            f.write(text)

    if "--no-history" not in sys.argv:
        path = os.path.join(ROOT, "docs/progress-history.csv")
        fields = ["date", "commit", "packets_mapped", "packets_implemented", "s2c_mapped", "c2s_mapped",
                  "zone_impl", "api_impl", "api_total", "packets_total"]
        hist = list(csv.DictReader(open(path))) if os.path.exists(path) else []
        hist = [h for h in hist if h["date"] != today]  # one row per day, latest wins
        hist.append(dict(zip(fields, [today, commit + dirty, ident, impl, p["s2c_ident"], p["c2s_ident"],
                                      z_done, api_done, api_total, total])))
        with open(path, "w", newline="") as f:
            w = csv.DictWriter(f, fieldnames=fields)
            w.writeheader()
            w.writerows(hist)

    for name, a, b, _ in head:
        print(f"{name:28} {a:4}/{b:<4} {pct(a, b):5.1f}%")


if __name__ == "__main__":
    main()
