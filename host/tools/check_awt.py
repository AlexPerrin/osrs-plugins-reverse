#!/usr/bin/env python3
"""Lists java.awt members referenced by the given jars that the shim doesn't provide.

  check_awt.py <shim classes dir> <jar>...

Uses javap: references come from `javap -c` constant-pool comments, and the shim's members
from `javap -p` signatures (including inherited ones, by walking superclasses in the shim).
"""
import re
import subprocess
import sys
import zipfile

shim_dir, jars = sys.argv[1], sys.argv[2:]

refs = set()
ref_re = re.compile(r"// (Method|Field|InterfaceMethod) ((?:java/awt|javax/imageio)/[\w/$]+)\.(\"?[\w<>]+\"?):(\S+)")
for jar in jars:
    names = [n[:-6] for n in zipfile.ZipFile(jar).namelist() if n.endswith(".class")]
    for i in range(0, len(names), 200):
        out = subprocess.run(["javap", "-c", "-p", "-cp", jar, *names[i:i + 200]],
                             capture_output=True, text=True).stdout
        for kind, cls, name, sig in ref_re.findall(out):
            refs.add((cls, name.strip('"'), sig, kind))

members, supers = {}, {}
def load(cls):
    if cls in members:
        return
    # By file path: with -cp, javap would resolve java.awt.* to the JDK's own classes.
    import os
    path = os.path.join(shim_dir, cls + ".class")
    out = subprocess.run(["javap", "-p", "-s", path], capture_output=True, text=True).stdout if os.path.exists(path) else ""
    members[cls] = set()
    if not out:
        members[cls] = None
        return
    m = re.search(r"(?:class|interface) \S+(?: extends ([\w.$]+(?:, [\w.$]+)*))?(?: implements ([\w.$, ]+))?", out)
    sup = []
    if m:
        for g in m.groups():
            if g: sup += [s.strip().replace(".", "/") for s in g.split(",")]
    supers[cls] = sup
    lines = out.splitlines()
    for j, line in enumerate(lines):
        if line.strip().startswith("descriptor:"):
            decl = lines[j - 1]
            desc = line.split(":", 1)[1].strip()
            nm = re.search(r"([\w$<>]+)\(", decl)
            if nm:
                n = nm.group(1)
                if n == cls.split("/")[-1].split("$")[-1] or n.endswith("$" + cls.split("$")[-1]) or n == cls.replace("/", "."):
                    n = "<init>"
                members[cls].add((n, desc))
            else:
                fm = re.search(r"([\w$]+);\s*$", decl)
                if fm:
                    members[cls].add((fm.group(1), desc))

def has(cls, name, sig, seen=None):
    seen = seen or set()
    if cls in seen: return False
    seen.add(cls)
    load(cls)
    if members.get(cls) is None: return False
    if (name, sig) in members[cls]: return True
    if name != "<init>":
        for s in supers.get(cls, []):
            if (s.startswith("java/awt/") or s.startswith("javax/imageio/")) and has(s, name, sig, seen): return True
            if s in ("java/lang/Object", "java/util/HashMap", "java/util/EventObject") : continue
    return False

missing = sorted((c, n, s) for c, n, s, k in refs if not has(c, n, s))
for c, n, s in missing:
    print(f"{c}.{n}{s}")
print(f"{len(missing)} missing of {len(refs)} referenced", file=sys.stderr)
