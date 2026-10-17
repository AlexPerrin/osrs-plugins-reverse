#!/usr/bin/env python3
"""Lists Java/Android platform methods that host.jar calls but the device's API level lacks.

usage: host/tools/api_gaps.py DEXDUMP_TXT... [--api 28] [--sdk-xml PATH] [--own DEXDUMP_TXT]...

Input: `dexdump -d classesN.dex` output of re/host/host.jar (Windows build-tools dexdump.exe
works from WSL). Each invoke of a java.*/javax.*/android.* method is checked against the SDK's
data/api-versions.xml; methods added after --api (or unknown to the platform) are printed with
the packages that call them. Java 9+ APIs that d8 doesn't backport show up here (e.g.
Matcher.appendReplacement(StringBuilder) broke RuneLite's WildcardMatcher on Android 9).
Calls are resolved through the superclass/interface graph, so inherited Java 9+ interface
default methods (List.toArray(IntFunction) -> Collection, API 33) are reported too.
For a converted plugin jar, pass host.jar's dexdump with --own so the classes the host provides
(the java.awt / javax.swing shim) aren't reported.
"""
import collections
import re
import sys
import xml.etree.ElementTree as ET

DEFAULT_XML = "/mnt/c/Users/Alex/AppData/Local/Android/Sdk/platforms/android-37.0/data/api-versions.xml"


def load_api(path):
    api = {}  # "java/lang/String.repeat(I)Ljava/lang/String;" -> since
    classes = {}
    supers = {}  # class -> [superclass, interfaces...] (to resolve inherited and default methods)
    for cls in ET.parse(path).getroot().iter("class"):
        name = cls.get("name")
        csince = float(cls.get("since", "1"))
        classes[name] = csince
        supers[name] = [e.get("name") for e in cls if e.tag in ("extends", "implements")]
        for m in cls.iter("method"):
            api[name + "." + m.get("name")] = float(m.get("since", csince))
    return api, classes, supers


# Members a class inherits from a hidden (non-SDK) superclass, which api-versions.xml can't show:
# StringBuilder/StringBuffer.getChars comes from AbstractStringBuilder (API 1), not from the API 37
# CharSequence default method the graph walk would otherwise find.
HIDDEN = {
    "java/lang/StringBuilder": {"getChars(II[CI)V"},
    "java/lang/StringBuffer": {"getChars(II[CI)V"},
}


def resolve(api, supers, cls, member):
    """Since-level of `member` (name+sig) as seen through `cls`: its own declaration, else the
    nearest one up the superclass/interface graph. Java 9+ interface default methods (e.g.
    Collection.toArray(IntFunction), API 33) are declared on the interface only, so a call
    through List/Set/ArrayList must be looked up there. None if no type declares it."""
    if member in HIDDEN.get(cls, ()):
        return 1.0
    seen, todo = set(), [cls]
    while todo:
        c = todo.pop(0)
        if c in seen:
            continue
        seen.add(c)
        since = api.get(c + "." + member)
        if since is not None:
            return since
        todo.extend(supers.get(c, ()))
    return None


def main():
    args = sys.argv[1:]
    level, xml = 28, DEFAULT_XML
    if "--api" in args:
        i = args.index("--api"); level = int(args[i + 1]); del args[i:i + 2]
    if "--sdk-xml" in args:
        i = args.index("--sdk-xml"); xml = args[i + 1]; del args[i:i + 2]
    owns = []
    while "--own" in args:
        i = args.index("--own"); owns.append(args[i + 1]); del args[i:i + 2]
    api, classes, supers = load_api(xml)
    # dexdump: "invoke-virtual {v0, v1}, Ljava/util/regex/Matcher;.appendReplacement:(Ljava/lang/StringBuilder;Ljava/lang/String;)Ljava/util/regex/Matcher; // method@..."
    inv = re.compile(r"invoke-\w+(?:/range)? \{[^}]*\}, L((?:java|javax|android)/[\w/$]+);\.([\w<>$]+):(\([^ ]*)")
    caller_re = re.compile(r"^\s+#\d+\s+: \(in L([\w/$]+);\)")
    gaps = collections.defaultdict(set)
    # Classes host.jar defines itself (the java.awt / javax.swing shim) aren't platform gaps.
    own = set()
    desc = re.compile(r"Class descriptor\s+: 'L([\w/$]+);'")
    for path in args + owns:
        with open(path, errors="replace") as f:
            for line in f:
                if "Class descriptor" in line:
                    m = desc.search(line)
                    if m:
                        own.add(m.group(1))
    for path in args:
        caller = "?"
        with open(path, errors="replace") as f:
            for line in f:
                m = caller_re.match(line)
                if m:
                    caller = m.group(1).rsplit("/", 1)[0].replace("/", ".")
                    continue
                if "invoke-" not in line:
                    continue
                m = inv.search(line)
                if not m:
                    continue
                cls, name, sig = m.groups()
                if cls in own:
                    continue
                key = cls + "." + name + sig
                since = resolve(api, supers, cls, name + sig)
                if since is None:
                    # Declared nowhere in the graph (e.g. a signature-polymorphic call); skip
                    # unless the class itself is new or missing.
                    csince = classes.get(cls)
                    if csince is not None and csince <= level:
                        continue
                    since = csince if csince is not None else 999
                if since > level:
                    gaps[(key, since)].add(caller)
    for (key, since), callers in sorted(gaps.items(), key=lambda kv: kv[0]):
        print(f"{'missing' if since == 999 else 'API %g' % since}  {key}  <- {', '.join(sorted(callers)[:6])}{' ...' if len(callers) > 6 else ''}")


if __name__ == "__main__":
    main()
