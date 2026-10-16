#!/usr/bin/env python3
# Rank RuneLite API interface methods called by core plugins that the host doesn't implement.
# usage: python3 host/tools/rank_api.py [N]   (needs re/runelite/client-1.13.1.jar and re/host/classes from host/build.sh)
# Counts, per API method, the core plugins (client jar) that call it and the host doesn't implement
# (matched by method name against non-stub host classes, so overloads count as implemented).
import subprocess, re, zipfile, collections, os, sys
R='/home/alex/osrs-plugins-reverse/re'
jar=R+'/runelite/client-1.13.1.jar'
z=zipfile.ZipFile(jar)
cls=[n[:-6] for n in z.namelist() if n.startswith('net/runelite/client/plugins/') and n.endswith('.class')]
calls=collections.defaultdict(set)  # (iface,method,desc) -> plugins
for i in range(0,len(cls),300):
    out=subprocess.run(['javap','-c','-p','-cp',jar]+[c.replace('/','.') for c in cls[i:i+300]],capture_output=True,text=True).stdout
    cur=None
    for line in out.splitlines():
        m=re.match(r'(?:.* )?class (net\.runelite\.client\.plugins\.([a-z0-9]+)\S*)',line)
        if m and not line.startswith(' '): cur=m.group(2)
        m=re.search(r'invokeinterface +#\d+, +\d+ +// InterfaceMethod (net/runelite/api/[\w/$]+)\.(\w+):(\S+)',line)
        if m and cur: calls[(m.group(1),m.group(2),m.group(3))].add(cur)
# implemented: methods declared (non-abstract) in host classes (not *Stub)
impl=set()
hc=R+'/host/classes'
files=[]
for d,_,fs in os.walk(hc):
    for f in fs:
        if f.endswith('.class') and not f.endswith('Stub.class'): files.append(os.path.join(d,f))
out=subprocess.run(['javap','-p']+files,capture_output=True,text=True).stdout
for line in out.splitlines():
    m=re.search(r' (\w+)\(',line)
    if m: impl.add(m.group(1))
# api default methods
api=R+'/runelite/runelite-api-1.13.1.jar'
rank=[]
for (iface,meth,desc),pl in calls.items():
    if meth in impl: continue
    rank.append((len(pl),iface.split('/')[-1],meth,sorted(pl)))
rank.sort(key=lambda r:-r[0])
for r in rank[:int(sys.argv[1]) if len(sys.argv)>1 else 60]: print(r[0],r[1]+'.'+r[2],','.join(r[3])[:120])
