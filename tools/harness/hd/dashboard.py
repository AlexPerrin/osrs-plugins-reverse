"""The dashboard (PRD section 9): one page, three areas (progress, status, approvals), readable on a phone, built
from the store and the event log, refreshed within a tick; the only writes are approve and deny.

  view(obs=None) -> dict   the view model (JSON-serialisable):
    progress: {client: {api, plugins_core, plugins_user, plugins_hub, packets}, server: {parity_live, parity_main},
               upstream: {merged, open, drafts, in_review}, rate: {live_per_day, metric_delta_week}, trends}
              client numbers from the last `metric` events (tools/progress.py --dry-run on each client live);
              parity from `metric name=parity-*` events when the parity tool exists
    changes:  rows: slug, track, stage, hold, reason, waiting (stages.derive's words), age_in_stage, owner,
              cost, prio, branch, commit; sorted holds first, then prio, then age
    batches, live, resources (leases + buildq status + MemAvailable), agents, stalls, kpis, approvals, daemon
              (pid, last tick, tick seconds, interval), generated (UTC) and generated_local
  html(view) -> str        the page: inline CSS, no external assets, light and dark, phone width, auto-refresh
                           from /api/view every 30 s, Approve / Deny (with a note) buttons posting to
                           /api/approve and /api/deny, a pending-count badge in the title, times in US Eastern,
                           every number linking to /events?kind=...&change=...
  text(view) -> str        the same as terminal text (hd status)

Environment: HD_OBSERVE=1 builds a real observe.Observer when none is given (default: no observation, `waiting`
empty); HD_PROGRESS=1 runs tools/progress.py --dry-run when no `metric` event exists; HD_BUILDQ=1 runs
tools/buildq.sh status. Tests leave all three unset.
"""
import datetime as dt
import json
import os
import re
import subprocess

from . import ROOT, events, kpi, leases, policy, stages, state_dir, store
from .util import ago, iso, local, now, parse, pid_alive, read_json, short

TILES = (("api", "RuneLite API implemented"), ("plugins_core", "Plugins running on Android"),
         ("plugins_user", "User's plugins running on Android"), ("plugins_hub", "Plugin Hub plugins running"),
         ("packets", "Mobile packets mapped"))
OPEN_BATCH = ("live", "published", "failed")
DONE_AFTER_DAYS = 2
RECENT_APPROVALS = 10
TREND_DAYS = 14


# --- the view model -------------------------------------------------------------------------------------------------

def _latest_metrics(evs):
    last, series = {}, {}
    for e in evs:
        if e.get("kind") == "metric" and e.get("name"):
            last[e["name"]] = e
            series.setdefault(e["name"], []).append(e)
    return last, series


def _tile(key, name, last, series, ref):
    e = last.get(name)
    if e is None and key == "plugins_user":
        for n in last:
            if "user's plugins" in n.lower():
                e, name = last[n], n
                break
    if e is None:
        return {"key": key, "name": name, "value": None, "total": None, "pct": None, "ts": None, "trend": [],
                "delta_week": None, "note": "no recount yet"}
    pts = [(x["ts"], x.get("pct")) for x in series.get(name, []) if x.get("pct") is not None]
    week = ref - dt.timedelta(days=7)
    before = [p for p in pts if (parse(p[0]) or ref) <= week]
    delta = round(e["pct"] - before[-1][1], 2) if before and e.get("pct") is not None else None
    return {"key": key, "name": name, "value": e.get("value"), "total": e.get("total"), "pct": e.get("pct"),
            "ts": e.get("ts"), "trend": pts[-TREND_DAYS:], "delta_week": delta, "note": None}


def _progress_py():
    """tools/progress.py --dry-run as {name: (value, total, pct)}; only with HD_PROGRESS=1."""
    if os.environ.get("HD_PROGRESS") != "1":
        return {}
    try:
        r = subprocess.run(["python3", os.path.join(ROOT, "tools", "progress.py"), "--dry-run"],
                           capture_output=True, text=True, timeout=120, cwd=ROOT)
    except (OSError, subprocess.SubprocessError):
        return {}
    out = {}
    for m in re.finditer(r"^(\S.*?)\s+(\d+)/(\d+)\s+([\d.]+)%", r.stdout, re.M):
        out[m.group(1).strip()] = (int(m.group(2)), int(m.group(3)), float(m.group(4)))
    return out


def _progress(evs, changes, ref):
    last, series = _latest_metrics(evs)
    if not any(n in last for _, n in TILES):
        for name, (v, t, p) in _progress_py().items():
            last[name] = {"name": name, "value": v, "total": t, "pct": p, "ts": iso(ref)}
    client = {k: _tile(k, n, last, series, ref) for k, n in TILES}
    server = {}
    for k, n in (("parity_live", "parity-live"), ("parity_main", "parity-main")):
        e = last.get(n)
        server[k] = None if e is None else {"value": e.get("value"), "total": e.get("total"), "pct": e.get("pct"),
                                             "ts": e.get("ts"),
                                             "trend": [(x["ts"], x.get("pct")) for x in series.get(n, [])][-TREND_DAYS:]}
    merged = sum(1 for c in changes if c.stage == "merged")
    in_review = sum(1 for c in changes if c.stage == "published")
    open_ = sum(1 for c in changes if c.get("upstream_pr") and c.stage != "merged")
    notes = [e for e in evs if kpi.note_kind(e) == "upstream"]
    upstream = {"merged": merged, "open": open_, "in_review": in_review, "drafts": max(open_ - in_review, 0),
                "note": notes[-1].get("text") if notes else None, "note_ts": notes[-1].get("ts") if notes else None}
    return {"client": client, "server": server, "upstream": upstream}


def _row(ch, obs, idx, evs_by_change, ref):
    waiting = ""
    if obs is not None:
        try:
            waiting = stages.derive(ch, obs, idx)[1]
        except Exception as e:   # the page never dies on one change
            waiting = f"derive failed: {e!r}"
    st = [e for e in evs_by_change.get(ch.slug, []) if e.get("kind") == "stage"]
    since = st[-1]["ts"] if st else ch.get("updated")
    return {"slug": ch.slug, "track": ch.get("track"), "stage": ch.stage, "hold": ch.hold, "reason": ch.hold_reason,
            "waiting": waiting, "age": ago(since, ref), "since": since, "owner": ch.get("owner"),
            "cost": ch.get("cost"), "prio": ch.get("prio"), "branch": ch.get("branch"),
            "commit": short(ch.get("commit")), "title": ch.get("title"), "terminal": stages.terminal(ch),
            "events": len(evs_by_change.get(ch.slug, []))}


def _changes(changes, obs, idx, evs, ref):
    by = {}
    for e in evs:
        if e.get("change"):
            by.setdefault(e["change"], []).append(e)
    rows, done = [], 0
    for ch in changes:
        r = _row(ch, obs, idx, by, ref)
        t = parse(r["since"])
        if r["stage"] == r["terminal"] and t and (ref - t).days >= DONE_AFTER_DAYS:
            done += 1
            continue
        rows.append(r)
    rows.sort(key=lambda r: (0 if r["hold"] else 1, policy.prio_order(r["prio"]),
                             (parse(r["since"]) or ref).timestamp()))
    return rows, done


def _resources():
    rows = []
    try:
        for name, r in leases.status_all().items():
            rows.append({"resource": name, "holder": r.get("holder"), "purpose": r.get("purpose"),
                         "since": r.get("since"), "expiry": r.get("expiry"), "pid": r.get("pid"),
                         "container": r.get("container"), "drain": r.get("drain"),
                         "grants": sorted(r.get("grants") or {})})
    except Exception as e:
        rows.append({"resource": "leases", "holder": None, "purpose": f"unreadable: {e!r}"})
    buildq = "n/a"
    if os.environ.get("HD_BUILDQ") == "1":
        try:
            p = subprocess.run([os.path.join(ROOT, "tools", "buildq.sh"), "status"], capture_output=True,
                               text=True, timeout=20)
            buildq = (p.stdout or p.stderr).strip() or "n/a"
        except (OSError, subprocess.SubprocessError) as e:
            buildq = f"buildq.sh status failed: {e!r}"
    mem = None
    try:
        with open("/proc/meminfo") as f:
            for line in f:
                if line.startswith("MemAvailable:"):
                    mem = int(line.split()[1]) // 1024
                    break
    except OSError:
        pass
    return {"leases": rows, "buildq": buildq, "mem_available_mb": mem}


def _agents(runs):
    out = []
    for r in runs:
        out.append({"name": r.get("id") or r.get("name"), "role": r.get("role"), "change": r.get("change"),
                    "stage": r.get("stage"), "pid": r.get("pid"), "alive": pid_alive(r.get("pid")),
                    "started": r.get("started"), "turns": r.get("turns"), "minutes": r.get("minutes"),
                    "usd": r.get("usd"), "tokens": r.get("tokens"), "budget": r.get("budget")})
    return out


def _stalls(rows, runs):
    by_role = {}
    for r in runs:
        by_role[r.get("role")] = by_role.get(r.get("role"), 0) + 1
    out = []
    for r in rows:
        if r["stage"] != "ready" or r["hold"] or r["owner"] or "a free slot" not in (r["waiting"] or ""):
            continue
        try:
            t = policy.track(r["track"])
            role, slots = t.get("dev_role"), int(t.get("dev_slots") or 0)
        except (KeyError, ValueError):
            continue
        used = by_role.get(role, 0)
        if used < slots:
            out.append({"slug": r["slug"], "reason": f"ready for {r['age']} with {slots - used} of {slots} {role} "
                                                     f"slot(s) free and no owner"})
    return out


def _approvals():
    recs = store.rec_all("approvals")
    pending = [a for a in recs if a.get("state") == "pending"]
    rest = [a for a in recs if a.get("state") != "pending"]
    rest.sort(key=lambda a: a.get("decided") or a.get("updated") or "", reverse=True)
    out = []
    for a in pending + rest[:RECENT_APPROVALS]:
        a = dict(a)
        try:
            from . import approvals as ap
            a["rendered"] = ap.render(a)
        except (NotImplementedError, ImportError, Exception):
            a["rendered"] = None
        out.append(a)
    return out, len(pending)


def view(obs=None):
    if obs is None and os.environ.get("HD_OBSERVE") == "1":
        from . import observe
        obs = observe.Observer()
    ref = now()
    evs = events.read()
    changes = store.all_changes()
    idx = {c.slug: c for c in changes}
    rows, done = _changes(changes, obs, idx, evs, ref)
    runs = [r for r in store.rec_all("runs") if not r.get("ended")]
    lv = store.live()
    upstream_tip = None
    if obs is not None:
        try:
            upstream_tip = obs.upstream_main()
        except Exception:
            upstream_tip = None
    approvals, pending = _approvals()
    daemon = read_json(os.path.join(state_dir(), "daemon.json"), {}) or {}
    daemon["alive"] = pid_alive(daemon.get("pid"))
    k = kpi.compute(evs)
    prog = _progress(evs, changes, ref)
    return {
        "generated": iso(ref), "generated_local": local(ref),
        "progress": dict(prog,
                         rate={"live_per_day": k["live_per_day"]["mean"],
                               "metric_delta_week": {t: x["delta_week"] for t, x in prog["client"].items()}},
                         trends={"live": kpi.trend("live", TREND_DAYS, evs),
                                 "interventions": kpi.trend("interventions", TREND_DAYS, evs),
                                 "denials": kpi.trend("denials", TREND_DAYS, evs),
                                 "usd": kpi.trend("usd", TREND_DAYS, evs)}),
        "changes": rows, "done": done,
        "batches": [b for b in store.rec_all("batches") if b.get("step") not in OPEN_BATCH],
        "live": dict(lv, upstream_tip=upstream_tip,
                     repin_due=bool(upstream_tip and lv.get("upstream_main") and upstream_tip != lv["upstream_main"])),
        "resources": _resources(),
        "agents": _agents(runs),
        "stalls": _stalls(rows, runs),
        "approvals": approvals, "pending": pending,
        "daemon": daemon,
        "kpis": k,
    }


# --- the page -------------------------------------------------------------------------------------------------------

PAGE = r"""<!doctype html>
<html lang="en"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>__TITLE__</title>
<style>
:root{color-scheme:light;--bg:#fcfcfb;--panel:#f3f2ef;--ink:#0b0b0b;--ink2:#52514e;--line:#dcdbd6;--accent:#2a78d6;
--spark:#9aa3ad;--good:#0ca30c;--warn:#fab219;--serious:#ec835a;--crit:#d03b3b;--hold:#fdeeea;--btn:#2a78d6;--btnink:#fff}
@media (prefers-color-scheme:dark){:root:not([data-theme="light"]){color-scheme:dark;--bg:#1a1a19;--panel:#242422;--ink:#f0efec;
--ink2:#c3c2b7;--line:#3a3a37;--accent:#3987e5;--spark:#6f7781;--hold:#3a2420;--btn:#3987e5}}
:root[data-theme="dark"]{color-scheme:dark;--bg:#1a1a19;--panel:#242422;--ink:#f0efec;--ink2:#c3c2b7;--line:#3a3a37;
--accent:#3987e5;--spark:#6f7781;--hold:#3a2420;--btn:#3987e5}
*{box-sizing:border-box}body{margin:0;padding:12px 16px 40px;background:var(--bg);color:var(--ink);
font:14px/1.4 system-ui,-apple-system,Segoe UI,Roboto,sans-serif;max-width:1200px;margin-inline:auto}
h1{font-size:20px;margin:8px 0}h2{font-size:15px;margin:22px 0 8px;color:var(--ink2);text-transform:uppercase;letter-spacing:.04em}
a{color:var(--accent);text-decoration:none}a:hover{text-decoration:underline}
.tiles{display:grid;grid-template-columns:repeat(auto-fill,minmax(160px,1fr));gap:10px}
.tile{background:var(--panel);border:1px solid var(--line);border-radius:8px;padding:10px}
.tile .l{font-size:12px;color:var(--ink2)}.tile .v{font-size:22px;font-weight:600;margin:2px 0}.tile .t{font-size:12px;color:var(--ink2)}
.tile svg{display:block;width:100%;height:28px;margin-top:4px}
.wrap{overflow-x:auto;-webkit-overflow-scrolling:touch}table{border-collapse:collapse;width:100%;font-size:13px}
th,td{text-align:left;padding:5px 8px;border-bottom:1px solid var(--line);vertical-align:top;white-space:nowrap}
td.w{white-space:normal;min-width:160px}th{color:var(--ink2);font-weight:500;font-size:12px}
tr.hold td{background:var(--hold)}.b{display:inline-block;padding:0 6px;border-radius:10px;font-size:12px;border:1px solid var(--line)}
.b.failed,.b.exception{border-color:var(--crit);color:var(--crit)}.b.blocked,.b.decision{border-color:var(--serious);color:var(--serious)}
.card{background:var(--panel);border:1px solid var(--line);border-radius:8px;padding:12px;margin-bottom:10px}
.card.pending{border-left:4px solid var(--warn)}.card h3{margin:0 0 6px;font-size:15px}
pre{background:var(--bg);border:1px solid var(--line);border-radius:6px;padding:8px;overflow-x:auto;font-size:12px;white-space:pre-wrap;word-break:break-word}
details{margin:6px 0}summary{cursor:pointer;color:var(--accent)}
textarea{width:100%;min-height:50px;background:var(--bg);color:var(--ink);border:1px solid var(--line);border-radius:6px;padding:6px;font:inherit}
button{font:inherit;padding:7px 14px;border-radius:6px;border:1px solid var(--line);background:var(--panel);color:var(--ink);cursor:pointer;margin:6px 6px 0 0}
button.ok{background:var(--btn);color:var(--btnink);border-color:var(--btn)}.muted{color:var(--ink2)}.small{font-size:12px}
footer{margin-top:28px;color:var(--ink2);font-size:12px}#err{color:var(--crit)}
</style></head><body>
<h1>harness <span id="badge" class="b"></span> <span id="err"></span></h1>
<div id="root"></div>
<footer id="foot"></footer>
<script id="data" type="application/json">__DATA__</script>
<script>
var TZ='America/New_York';
var fmt=new Intl.DateTimeFormat('en-US',{timeZone:TZ,hour:'numeric',minute:'2-digit',timeZoneName:'short',month:'2-digit',day:'2-digit'});
function esc(s){return s==null?'':String(s).replace(/[&<>"]/g,function(c){return {'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;'}[c]})}
function t(s){if(!s)return '-';var d=new Date(s);if(isNaN(d))return esc(s);return fmt.format(d)}
function ago(s){if(!s)return '-';var m=(Date.now()-new Date(s))/60000;if(m<0)m=0;return m<60?Math.floor(m)+' min':m<1440?(m/60).toFixed(1)+' h':(m/1440).toFixed(1)+' d'}
function ev(n,kind,change){var q='/events?kind='+encodeURIComponent(kind||'')+(change?'&change='+encodeURIComponent(change):'');return '<a href="'+q+'">'+esc(n==null?'-':n)+'</a>'}
function num(x,d){return x==null?'-':(typeof x=='number'?(Number.isInteger(x)?x:x.toFixed(d==null?1:d)):esc(x))}
function spark(pts,last){pts=(pts||[]).map(function(p){return Array.isArray(p)?p[1]:p}).filter(function(v){return v!=null});
 if(pts.length<2)return '';var w=160,h=28,mn=Math.min.apply(null,pts),mx=Math.max.apply(null,pts),r=(mx-mn)||1;
 var xy=pts.map(function(v,i){return [(i/(pts.length-1))*(w-4)+2,h-3-((v-mn)/r)*(h-8)]});
 var d=xy.map(function(p,i){return (i?'L':'M')+p[0].toFixed(1)+' '+p[1].toFixed(1)}).join(' ');var e=xy[xy.length-1];
 return '<svg viewBox="0 0 '+w+' '+h+'" preserveAspectRatio="none" aria-hidden="true"><path d="'+d+'" fill="none" stroke="var(--spark)" stroke-width="2" stroke-linejoin="round"/><circle cx="'+e[0].toFixed(1)+'" cy="'+e[1].toFixed(1)+'" r="3" fill="var(--accent)"/></svg>'}
function tile(label,v,total,pct,trend,delta,sub,link){var val=v==null?(sub||'n/a'):(total!=null?v+'/'+total:num(v));
 var d=delta==null?'':' <span>'+(delta>=0?'+':'')+num(delta)+' pct/wk</span>';
 return '<div class="tile"><div class="l">'+esc(label)+'</div><div class="v">'+(link?'<a href="'+link+'">'+esc(val)+'</a>':esc(val))+'</div><div class="t">'+(pct!=null?num(pct)+'%':'')+d+'</div>'+spark(trend)+'</div>'}
function table(heads,rows,cls){if(!rows.length)return '<p class="muted small">none</p>';
 return '<div class="wrap"><table><thead><tr>'+heads.map(function(h){return '<th>'+esc(h)+'</th>'}).join('')+'</tr></thead><tbody>'+rows.map(function(r){return '<tr'+(cls&&cls(r)?' class="'+cls(r)+'"':'')+'>'+r.map(function(c,i){return '<td'+(typeof c=='object'&&c&&c.w?' class="w"':'')+'>'+((typeof c=='object'&&c)?c.h:c)+'</td>'}).join('')+'</tr>'}).join('')+'</tbody></table></div>'}
function W(h){return {w:1,h:h}}
function kv(o){return Object.keys(o||{}).map(function(k){return esc(k)+' '+num(o[k],2)}).join(', ')||'-'}
function cost(c){return c?('$'+num(c.usd,2)+' '+num(c.minutes,0)+'m '+num(c.turns,0)+'t'):'-'}
function card(a){var pend=a.state=='pending';var cmds=(a.commands||[]).map(function(c){return Array.isArray(c)?c.map(function(w){return /[\s"']/.test(w)?JSON.stringify(w):w}).join(' '):String(c)}).join('\n');
 var tx=a.text||{};var full=a.rendered||[tx.title,tx.body,tx.comment].filter(Boolean).join('\n\n');
 return '<div class="card '+esc(a.state)+'"><h3>'+esc(a.kind)+' · '+esc(a.change||a.batch||'')+' · '+esc(a.action||a.question||'')+' <span class="b">'+esc(a.state)+'</span></h3>'
 +'<div class="small muted">#'+esc(a.id)+' · created '+t(a.created)+(a.decided?' · decided '+t(a.decided):'')+(a.record?' · record '+esc(a.record):'')+(a.diff?' · diff <code>'+esc(a.diff)+'</code>':'')+'</div>'
 +(a.question?'<p>'+esc(a.question)+'</p>'+(a.options?'<ul>'+a.options.map(function(o){return '<li>'+esc(o)+'</li>'}).join('')+'</ul>':''):'')
 +(cmds?'<pre>'+esc(cmds)+'</pre>':'')+(full?'<details><summary>full text</summary><pre>'+esc(full)+'</pre></details>':'')
 +((a.caveats||[]).length?'<div class="small">caveats: '+a.caveats.map(esc).join('; ')+'</div>':'')
 +(a.note?'<div class="small">note: '+esc(a.note)+'</div>':'')+(a.result?'<div class="small">result: '+esc(typeof a.result=='string'?a.result:JSON.stringify(a.result))+'</div>':'')
 +(pend?'<form class="approve" data-id="'+esc(a.id)+'" onsubmit="return false"><textarea name="note" placeholder="note (required to deny)"></textarea><button class="ok" onclick="decide(this,\'approve\')">Approve</button><button onclick="decide(this,\'deny\')">Deny</button></form>':'')+'</div>'}
function decide(btn,what){var f=btn.closest('form');var note=f.querySelector('textarea').value;if(what=='deny'&&!note){alert('a deny needs a note');return}
 f.querySelectorAll('button').forEach(function(b){b.disabled=true});
 fetch({approve:'/api/approve',deny:'/api/deny'}[what],{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({id:f.dataset.id,note:note})})
 .then(function(r){return r.json()}).then(function(j){if(!j.ok)document.getElementById('err').textContent=j.error||'failed';refresh()})
 .catch(function(e){document.getElementById('err').textContent=String(e);refresh()})}
function render(v){var p=v.progress,c=p.client,s=p.server,u=p.upstream,k=v.kpis,h='';
 document.title='harness'+(v.pending?' ('+v.pending+')':'');document.getElementById('badge').textContent=v.pending?v.pending+' pending':'';
 h+='<p class="muted small">Project tracker. Agents: <a href="http://127.0.0.1:4820/">Claude Code Agent Monitor</a> · operations: <a href="/text">/text</a> · data: <a href="/api/view">/api/view</a></p>';
 h+='<h2>Progress</h2><div class="tiles">';
 ['api','plugins_core','plugins_user','plugins_hub','packets'].forEach(function(key){var x=c[key];if(!x)return;if(key=='plugins_user'&&x.value==null)return;
  h+=tile(x.name,x.value,x.total,x.pct,x.trend,x.delta_week,x.note,'/events?kind=metric')});
 h+=tile('Server parity: test server',s.parity_live?s.parity_live.pct:null,null,null,s.parity_live&&s.parity_live.trend,null,'not measured yet','/events?kind=metric');
 h+=tile('Server parity: upstream main',s.parity_main?s.parity_main.pct:null,null,null,s.parity_main&&s.parity_main.trend,null,'not measured yet','/events?kind=metric');
 h+='<div class="tile"><div class="l">Upstream</div><div class="v">'+ev(u.merged,'stage')+' merged</div><div class="t">open '+u.open+' · in review '+u.in_review+' · drafts '+u.drafts+(u.note?'<br>'+esc(u.note):'')+'</div></div>';
 h+=tile('Changes live per day (7d)',p.rate.live_per_day,null,null,p.trends.live,null,null,'/events?kind=stage');
 h+='</div>';
 h+='<h2>Approvals'+(v.pending?' · '+v.pending+' pending':'')+'</h2>';
 var pend=v.approvals.filter(function(a){return a.state=='pending'}),rest=v.approvals.filter(function(a){return a.state!='pending'});
 h+=pend.length?pend.map(card).join(''):'<p class="muted small">nothing waits on you</p>';
 if(rest.length)h+='<details><summary>'+rest.length+' recent (done, stale, denied)</summary>'+rest.map(card).join('')+'</details>';
 h+='<h2>Changes'+(v.done?' <span class="muted small">(+'+v.done+' done)</span>':'')+'</h2>';
 h+=table(['change','track','stage','hold','waiting','age','owner','cost','prio','commit'],v.changes.map(function(r){return [
  '<a href="/events?change='+encodeURIComponent(r.slug)+'" title="'+esc(r.title)+'">'+esc(r.slug)+'</a>',esc(r.track),ev(r.stage,'stage',r.slug),
  W(r.hold?'<span class="b '+esc(r.hold)+'">'+esc(r.hold)+'</span> '+esc(r.reason||''):''),W(esc(r.waiting||'')),esc(r.age),esc(r.owner||''),cost(r.cost),esc(r.prio),'<code>'+esc(r.commit||'')+'</code>']}),function(r){return r[3].h?'hold':''});
 h+='<h2>Batches</h2>'+table(['batch','kind','step','includes','integration','image','created'],v.batches.map(function(b){return [ev(b.id,'batch'),esc(b.kind),esc(b.step),W(esc((b.includes||[]).join(', '))),'<code>'+esc((b.integration||'').slice(0,9))+'</code>',esc(b.image||''),t(b.created)]}));
 var l=v.live||{};h+='<h2>Live</h2><div class="card small">integration <code>'+esc((l.integration||'').slice(0,9)||'-')+'</code> · image '+esc(l.image||'-')+' · rollback '+esc(l.rollback||'-')+' · since '+t(l.since)+' · upstream main at pin <code>'+esc((l.upstream_main||'').slice(0,9)||'-')+'</code>'+(l.upstream_tip?' · tip <code>'+esc(l.upstream_tip.slice(0,9))+'</code>':'')+(l.repin_due?' · <b>re-pin due</b>':'')+' · '+ev('deploys','deploy')+'</div>';
 document.getElementById('root').innerHTML=h;var d=v.daemon||{};
 document.getElementById('foot').innerHTML='generated '+t(v.generated)+' ('+esc(v.generated_local)+') · daemon '+(d.pid?'pid '+d.pid+(d.alive?'':' (dead)')+' · last tick '+t(d.last_tick)+' ('+num(d.tick_seconds)+' s, every '+esc(d.interval)+' s, '+esc(d.ticks)+' ticks)':'not running')+' · <a href="/text">text</a> · <a href="/api/view">json</a> · <a href="/events?limit=200">events</a>'}
function refresh(){fetch('/api/view').then(function(r){return r.json()}).then(function(v){document.getElementById('err').textContent='';render(v)}).catch(function(e){document.getElementById('err').textContent='refresh failed: '+e})}
render(JSON.parse(document.getElementById('data').textContent));
setInterval(refresh,30000);
</script></body></html>
"""


def html(v):
    n = v.get("pending") or 0
    data = json.dumps(v, sort_keys=True).replace("</", "<\\/")
    return PAGE.replace("__TITLE__", f"harness ({n})" if n else "harness").replace("__DATA__", data)


# --- the terminal rendering -------------------------------------------------------------------------------------------

def _n(x, d=1):
    if x is None:
        return "-"
    if isinstance(x, float):
        return f"{x:.{d}f}"
    return str(x)


def _tile_text(x):
    if x.get("value") is None:
        return f"{x['name']:36} {x.get('note') or 'n/a'}"
    tot = f"{x['value']}/{x['total']}" if x.get("total") is not None else str(x["value"])
    d = x.get("delta_week")
    return f"{x['name']:36} {tot:>9} {_n(x.get('pct')):>6}%" + (f"  ({d:+.1f}/wk)" if d is not None else "")


def text(v):
    p, k, out = v["progress"], v["kpis"], []
    out.append(f"harness status  {v['generated_local']}  (pending approvals: {v.get('pending', 0)})")
    out.append("\n== Progress")
    for key, _ in TILES:
        x = p["client"].get(key)
        if x and not (key == "plugins_user" and x.get("value") is None):
            out.append("  " + _tile_text(x))
    for key, label in (("parity_live", "Server parity: test server"), ("parity_main", "Server parity: upstream main")):
        s = p["server"].get(key)
        out.append(f"  {label:36} " + (f"{_n(s.get('pct'))}%" if s else "not measured yet"))
    u = p["upstream"]
    out.append(f"  {'Upstream':36} merged {u['merged']}, open {u['open']}, in review {u['in_review']}, drafts {u['drafts']}"
               + (f"  ({u['note']})" if u.get("note") else ""))
    out.append(f"  {'Changes live per day (7d)':36} {_n(p['rate']['live_per_day'], 2)}")
    out.append("\n== Approvals")
    pend = [a for a in v["approvals"] if a.get("state") == "pending"]
    if not pend:
        out.append("  none pending")
    for a in v["approvals"]:
        out.append(f"  [{a.get('state')}] {a.get('id')}  {a.get('kind')} {a.get('change') or a.get('batch') or ''} "
                   f"{a.get('action') or a.get('question') or ''}  created {local(a.get('created'))}")
        for c in a.get("commands") or []:
            out.append("      $ " + (" ".join(c) if isinstance(c, list) else str(c)))
        if a.get("caveats"):
            out.append("      caveats: " + "; ".join(a["caveats"]))
    out.append(f"\n== Changes ({len(v['changes'])} shown, {v.get('done', 0)} done)")
    out.append(f"  {'change':28} {'track':7} {'stage':9} {'hold':10} {'age':7} {'owner':6} {'prio':4} waiting")
    for r in v["changes"]:
        hold = f"{r['hold']}" if r.get("hold") else ""
        out.append(f"  {r['slug']:28} {r['track'] or '':7} {r['stage']:9} {hold:10} {r['age']:7} {r.get('owner') or '':6} "
                   f"{r.get('prio') or '':4} {r.get('waiting') or ''}"
                   + (f"  [{r['reason']}]" if r.get("reason") else ""))
    out.append("\n== Batches")
    for b in v["batches"]:
        out.append(f"  {b.get('id')}  {b.get('kind')} {b.get('step')}  {', '.join(b.get('includes') or [])}  "
                   f"integration {short(b.get('integration'))}  since {local(b.get('created'))}")
    if not v["batches"]:
        out.append("  none open")
    lv = v["live"]
    out.append("\n== Live")
    out.append(f"  integration {short(lv.get('integration')) or '-'}  image {lv.get('image') or '-'}  rollback "
               f"{lv.get('rollback') or '-'}  since {local(lv.get('since'))}  upstream main {short(lv.get('upstream_main')) or '-'}"
               + (f"  tip {short(lv['upstream_tip'])}" if lv.get("upstream_tip") else "")
               + ("  RE-PIN DUE" if lv.get("repin_due") else ""))
    R = v["resources"]
    out.append("\n== Resources")
    for r in R["leases"]:
        holder = r.get("holder") or ("draining" if r.get("drain") else "free")
        out.append(f"  {r['resource']:12} {holder:10} {r.get('purpose') or '':30} since {local(r.get('since')):22} "
                   f"until {local(r.get('expiry')):22} pid {r.get('pid') or r.get('container') or '-'}"
                   + (f"  grants {' '.join(r['grants'])}" if r.get("grants") else ""))
    mem = R.get("mem_available_mb")
    out.append(f"  buildq: {R.get('buildq')}  MemAvailable: {mem if mem is None else f'{mem} MB'}")
    out.append("\n== Agents")
    for a in v["agents"]:
        out.append(f"  {a['name']:6} {a.get('role') or '':11} {a.get('change') or '':28} {a.get('stage') or '':6} "
                   f"{'alive' if a.get('alive') else 'DEAD':5} since {local(a.get('started'))}  turns {_n(a.get('turns'))} "
                   f"min {_n(a.get('minutes'), 0)} usd {_n(a.get('usd'), 2)}")
    if not v["agents"]:
        out.append("  none running")
    out.append("\n== Stalls")
    for s in v["stalls"]:
        out.append(f"  {s['slug']}: {s['reason']}")
    if not v["stalls"]:
        out.append("  nothing ready is stuck")
    dl, lt, cf, gf, dn = k["deadlocks"], k["lead_time_h"], k["check_first_pass"], k["gate_first_pass"], k["denials_per_hour"]
    out.append("\n== Harness KPIs")
    out.append(f"  unattended hours        {_n(k['unattended_hours'])}")
    out.append(f"  live per day (7d mean)  {_n(k['live_per_day']['mean'], 2)}")
    out.append(f"  lead time ready->live   median {_n(lt['median'])} h  p90 {_n(lt['p90'])} h  (n {lt['n']})")
    out.append(f"  check first pass        {_n(cf['rate'] and round(cf['rate'] * 100), 0)}%  ({cf['pass']}/{cf['n']})")
    out.append(f"  gate first pass         {_n(gf['rate'] and round(gf['rate'] * 100), 0)}%  ({gf['pass']}/{gf['n']})")
    out.append(f"  stall minutes           {_n(k['stall_minutes'])}")
    out.append(f"  interventions per day   {_n(k['interventions_per_day']['mean'], 2)}")
    out.append(f"  denials per hour        {_n(dn['total'], 2)}  " + ", ".join(f"{s} {_n(x, 2)}" for s, x in dn['by_source'].items()))
    for role, x in k["tokens_per_change"].items():
        out.append(f"  tokens per change       {role}: ${_n(x['usd'], 2)} {_n(x['minutes'], 0)} min {_n(x['turns'], 0)} turns (n {x['n']})")
    out.append(f"  deadlocks               {dl['count']}  detect {_n(dl['detect_min'])} min  recover {_n(dl['recover_min'])} min")
    out.append(f"  gate build seconds      median {_n(k['builds']['median'], 0)}  last {_n(k['builds']['last'], 0)}")
    d = v.get("daemon") or {}
    out.append("\n-- daemon " + (f"pid {d.get('pid')}{'' if d.get('alive') else ' (dead)'}  last tick {local(d.get('last_tick'))}  "
                                  f"{_n(d.get('tick_seconds'))} s  every {d.get('interval')} s  ticks {d.get('ticks')}"
                                  if d.get("pid") else "not running"))
    return "\n".join(out) + "\n"
