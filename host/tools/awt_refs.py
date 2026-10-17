#!/usr/bin/env python3
"""Collects the java.awt / javax.swing members and classes that the given jars reference.

  awt_refs.py <refs.txt> <classes.txt> <jar>...

refs.txt gets "owner name descriptor" lines; classes.txt gets class names (JVM internal form).
Input for host/gen/GenHeadless.java.
"""
import re
import subprocess
import sys
import zipfile

refs_out, classes_out, jars = sys.argv[1], sys.argv[2], sys.argv[3:]
ref_re = re.compile(r"// (?:Method|Field|InterfaceMethod) ((?:java/awt|javax/swing|javax/accessibility)/[\w/$]+)\.(\"?[\w<>]+\"?):(\S+)")
cls_re = re.compile(r"= Class\s+#\d+\s+// \"?\[*L?((?:java/awt|javax/swing|javax/accessibility)/[\w/$]+)")
refs, classes = set(), set()
other_refs = set()           # refs whose owner is a jar class (resolved through superclasses below)
supers = {}                  # jar class -> superclass (internal names)
any_ref_re = re.compile(r"// (?:Method|Field|InterfaceMethod) ([\w/$]+)\.(\"?[\w<>]+\"?):(\S+)")
self_ref_re = re.compile(r"// (?:Method|Field|InterfaceMethod) (\"?[\w<>]+\"?):(\S+)")
head_re = re.compile(r"^(?:public |final |abstract |private |protected |static |synchronized |strictfp )*(?:class|interface|enum) ([\w.$]+)(?: extends ([\w.$]+))?", re.M)
for jar in jars:
    names = [n[:-6] for n in zipfile.ZipFile(jar).namelist() if n.endswith(".class")]
    for i in range(0, len(names), 300):
        out = subprocess.run(["javap", "-v", "-p", "-cp", jar, *names[i:i + 300]],
                             capture_output=True, text=True).stdout
        refs.update(ref_re.findall(out))
        classes.update(cls_re.findall(out))
        for c, n, d in any_ref_re.findall(out):
            if not c.startswith(("java/", "javax/", "android/")):
                other_refs.add((c, n, d))
        # Per class: header (superclass) and owner-less refs (javap omits the current class).
        for block in out.split("Classfile ")[1:]:
            h = head_re.search(block)
            if not h:
                continue
            me = h.group(1).replace(".", "/")
            if h.group(2):
                supers[me] = h.group(2).replace(".", "/")
            for n, d in self_ref_re.findall(block):
                other_refs.add((me, n, d))
# Calls through jar subclasses of AWT/Swing classes (e.g. MyButton.setVerticalAlignment) need the
# inherited member generated: attribute them to the first AWT/Swing ancestor.
for c, n, d in other_refs:
    k, seen = c, set()
    while k in supers and k not in seen:
        seen.add(k)
        k = supers[k]
        if k.startswith(("java/awt/", "javax/swing/", "javax/accessibility/")):
            refs.add((k, n, d))
            break
with open(refs_out, "w") as f:
    f.write("".join(f"{c} {n} {d}\n" for c, n, d in sorted(refs)))
with open(classes_out, "w") as f:
    f.write("".join(f"{c}\n" for c in sorted(classes)))
print(f"{len(refs)} members, {len(classes)} classes", file=sys.stderr)
