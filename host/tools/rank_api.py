#!/usr/bin/env python3
# Rank RuneLite API interface methods called by core plugins that the host doesn't implement.
# usage: python3 host/tools/rank_api.py [N]   (needs re/runelite/client-1.13.1.jar and re/host/classes from host/build.sh)
#        python3 host/tools/rank_api.py N --jars a.jar b.jar   (Plugin Hub class jars, e.g. re/hub-build/*.jar;
#        a jar's plugin name is its file name)
# Counts, per API method, the core plugins (client jar) that call it and the host doesn't implement
# (matched by method name against non-stub host classes, so overloads count as implemented).
import subprocess, re, zipfile, collections, os, sys
R='/home/alex/osrs-plugins-reverse/re'
jar=R+'/runelite/client-1.13.1.jar'
calls=collections.defaultdict(set)  # (iface,method,desc) -> plugins
def scan(jar, prefix, fixed=None):
    z=zipfile.ZipFile(jar)
    cls=[n[:-6] for n in z.namelist() if n.startswith(prefix) and n.endswith('.class')]
    for i in range(0,len(cls),300):
        out=subprocess.run(['javap','-c','-p','-cp',jar]+[c.replace('/','.') for c in cls[i:i+300]],capture_output=True,text=True).stdout
        cur=fixed
        for line in out.splitlines():
            m=re.match(r'(?:.* )?class (net\.runelite\.client\.plugins\.([a-z0-9]+)\S*)',line)
            if m and not line.startswith(' ') and not fixed: cur=m.group(2)
            m=re.search(r'invokeinterface +#\d+, +\d+ +// InterfaceMethod (net/runelite/api/[\w/$]+)\.(\w+):(\S+)',line)
            if m and cur: calls[(m.group(1),m.group(2),m.group(3))].add(cur)
if '--jars' in sys.argv:
    i=sys.argv.index('--jars')
    for j in sys.argv[i+1:]: scan(j,'',os.path.basename(j)[:-4])
    del sys.argv[i:]
else:
    scan(jar,'net/runelite/client/plugins/')
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
# api default methods (implemented by the API itself)
api=R+'/runelite/runelite-api-1.13.1.jar'
ifaces=sorted({k[0] for k in calls})
defaults=set()
for i in range(0,len(ifaces),200):
    out=subprocess.run(['javap','-cp',api]+[c.replace('/','.') for c in ifaces[i:i+200]],capture_output=True,text=True).stdout
    cur=None
    for line in out.splitlines():
        m=re.match(r'public (?:abstract )?(?:interface|class) ([\w.$]+)',line)
        if m: cur=m.group(1).replace('.','/')
        m=re.search(r' default .* (\w+)\(',line)
        if m and cur: defaults.add((cur,m.group(1)))
rank=[]
for (iface,meth,desc),pl in calls.items():
    if meth in impl or (iface,meth) in defaults: continue
    rank.append((len(pl),iface.split('/')[-1],meth,sorted(pl)))
rank.sort(key=lambda r:-r[0])
for r in rank[:int(sys.argv[1]) if len(sys.argv)>1 else 60]: print(r[0],r[1]+'.'+r[2],','.join(r[3])[:120])
