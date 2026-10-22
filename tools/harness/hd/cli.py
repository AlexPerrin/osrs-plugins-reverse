"""hd: the harness v3 command line (python3 tools/harness/hd.py <command>).

Changes
  new --track t --slug s --title "..." [--prio P2] [--dod "item"]... [--deps a,b] [--branch b] [--spec n] [--network]
      [--local-only] [--backlog] [--text "..."] [--server-dod]
  list [--track t] [--stage s] [--all]      show <slug>      log <slug> "msg"      lint
  handover <slug> --branch b --commit sha [--evidence dir] [--summary "..."] [--pr url] [--ci url] [--repo dir]
      [--partial]                         the dev's hand-over; the next tick runs the check (BR-S5)
  block <slug> --by <other> ["why"]       a dep edge + a blocked hold (BR-C4)
  fail <slug> "why"                       an agent that cannot finish: a decision hold
  gate <slug> PASS|FAIL ["note"]          the QA companion's verdict after pushing the record
  hold <slug> blocked|decision|failed|exception "reason"   an operator's hold (never a stage: BR-S2);  unhold <slug>
  resolve <batch> "note"                  the integrator recorded a resolution; the train resumes
  check <slug>                            run the hand-over check now and print it
  brief <slug> [--stage dev|gate]         the spawn brief      dispatch <slug> [--print]   spawn now
  note "text" [--kind user|upstream|...]  a note event (the console records the user's messages as kind user)
  agents [--all]                          running agents: usage so far, `claude --resume <session>` to open the chat
  tail <name> [--turns 5]                 the last turns of an agent's log as text
Daemon
  status [--json]   tick [--dry-run]   run [--interval 60] [--once]   start [--interval]   stop   (the tick loop)
  serve [--port 7790] [--detach]   serve-stop   (the dashboard: its own process, never restarted with the loop)
  daemon-status     digest
Resources
  lease grant <res> <who> [--minutes n] [--once]   lease take <res> <who> "what" [--pid n] [--container c]
  lease heartbeat <res> <who>   lease release <res> <who>   lease reclaim <res> "why"   lease revoke <res> <who>
  lease status [<res>]   lease holds <res> <who>   lease drain <res>   lease undrain <res>
Approvals, batches, events
  approvals   approve <id> [--note]   deny <id> --note "..."   batches   train [--now]   resume <batch>
  release-request <tag>   events [--kind k] [--change s] [--limit n]   kpi   migrate [--apply]
"""
import argparse
import json
import os
import subprocess
import sys

from . import ROOT, events, leases, policy, store
from .util import iso, local, slug_ok

T = "    "


def _load(slug):
    if not store.exists(slug):
        sys.exit(f"hd: no change {slug}")
    return store.load(slug)


def _server_dod(spec):
    return [f"accuracy/{spec}.md on the fork's docs branch (accuracy-check: sources, RSProx comparison, caveats)",
            "change built and checked in game on the branch server (evidence in re/evidence/<slug>/)",
            "one change only, branch off upstream main (no stacking)",
            "fork PR open with green CI at the handed-over commit",
            "PR text reviewed (pr-text-review)"]


def cmd_new(a):
    dod = list(a.dod or [])
    spec = a.spec
    if a.server_dod:
        if a.track != "server":
            sys.exit("hd: --server-dod is for --track server")
        spec = spec or (a.branch.split("/", 1)[-1] if a.branch else a.slug)
        dod = _server_dod(spec) + dod
    branch = a.branch
    if not branch and a.track == "client":
        branch = f"client/{a.slug}"
    try:
        ch = store.new(a.slug, a.track, a.title, prio=a.prio, deps=[d for d in (a.deps or "").split(",") if d],
                       dod=dod, text=a.text or "", branch=branch, stage="backlog" if a.backlog or not dod else "ready",
                       network=a.network, spec=spec, local_only=a.local_only or str(branch or "").startswith("local/"))
    except (ValueError, FileExistsError) as e:
        sys.exit(f"hd: {e}")
    events.emit("stage", change=ch["slug"], **{"from": None, "to": ch.stage}, why="created")
    print(ch["slug"])


def fmt(ch, waiting=None):
    hold = f" [{ch.hold}: {ch.hold_reason[:50]}]" if ch.hold else ""
    own = f" @{ch['owner']}" if ch.get("owner") else ""
    w = f"  waits: {waiting}" if waiting else ""
    return f"{ch.get('prio') or '--':3} {ch['track']:8} {ch.stage:10} {ch['slug']:40}{own}{hold}{w}  {ch.get('title', '')[:60]}"


def cmd_list(a):
    waiting = (store.rec_load("view", "waiting") or {}).get("waiting", {})
    from .stages import terminal
    for ch in store.all_changes():
        if a.track and ch["track"] != a.track:
            continue
        if a.stage and ch.stage != a.stage:
            continue
        if not a.all and not a.stage and ch.stage == terminal(ch):
            continue
        print(fmt(ch, waiting.get(ch["slug"])))


def cmd_show(a):
    with open(store.path(a.slug)) as f:
        print(f.read(), end="")


def cmd_log(a):
    ch = _load(a.slug)
    store.add_log(ch, a.message)
    store.save(ch)


def cmd_note(a):
    events.emit("note", text=a.text, note_kind=a.kind)
    print("noted")


def cmd_handover(a):
    ch = _load(a.slug)
    repo = a.repo or ROOT
    r = subprocess.run(["git", "-C", repo, "rev-parse", "--verify", "-q", a.commit + "^{commit}"], capture_output=True, text=True)
    sha = r.stdout.strip()
    if not sha:
        sys.exit(f"hd: commit {a.commit} not found in {repo} (use --repo <clone>)")
    unticked = ch.dod_unticked()
    if unticked and not a.partial:
        sys.exit("hd: the definition of done still has unticked items (tick them with [x], or --partial and say what is "
                 "left in --summary):\n" + "\n".join(unticked))
    ch.update(branch=a.branch, commit=sha)
    if a.evidence:
        ch["evidence"] = a.evidence
    if a.summary:
        ch["summary"] = a.summary
    if a.pr:
        ch["pr"] = a.pr
    if a.ci:
        ch["ci"] = a.ci
    if ch.stage != "dev":
        store.set_stage(ch, "dev", "hand-over recorded")
    store.clear_hold(ch, "handed over (BR-S3)")
    store.add_log(ch, f"handover {a.branch}@{sha[:9]}: {a.summary or ''}")
    store.save(ch)
    events.emit("handover", change=ch["slug"], branch=a.branch, commit=sha, owner=ch.get("owner"))
    print(f"{ch['slug']}: hand-over recorded; the next tick runs the check")


def cmd_block(a):
    ch = _load(a.slug)
    if a.by not in ch["deps"]:
        ch["deps"].append(a.by)
    store.set_hold(ch, "blocked", f"waiting for {a.by}" + (f": {a.why}" if a.why else ""))
    ch["owner"] = None
    if ch.stage == "dev":
        store.set_stage(ch, "ready", f"blocked by {a.by}")
    store.save(ch)
    events.emit("hold", change=ch["slug"], hold="blocked", reason=a.by)
    if not store.exists(a.by):
        print(f"hd: note: {a.by} does not exist yet; create it with `hd new --track server --slug {a.by} ...`", file=sys.stderr)
    print(f"{ch['slug']}: blocked by {a.by}")


def cmd_fail(a):
    ch = _load(a.slug)
    store.set_hold(ch, "decision", a.why)
    ch["owner"] = None
    if ch.stage == "dev" and not ch.get("commit"):
        store.set_stage(ch, "ready", "agent could not finish")
    store.save(ch)
    events.emit("hold", change=ch["slug"], hold="decision", reason=a.why)
    print(f"{ch['slug']}: decision hold: {a.why}")


def cmd_hold(a):
    """BR-S2: an operator adds a hold or a note, never a stage."""
    ch = _load(a.slug)
    store.set_hold(ch, a.kind, a.reason)
    ch["owner"] = None
    store.save(ch)
    events.emit("hold", change=ch["slug"], hold=a.kind, reason=a.reason, by="operator")
    print(f"{ch['slug']}: {a.kind} hold")


def cmd_unhold(a):
    ch = _load(a.slug)
    store.clear_hold(ch, a.why or "cleared by the operator")
    store.save(ch)
    events.emit("hold", change=ch["slug"], hold="none", reason=a.why, by="operator")
    print(f"{ch['slug']}: hold cleared")


def cmd_gate(a):
    if not store.exists(a.slug) and store.rec_load("batches", a.slug):
        b = store.rec_load("batches", a.slug)
        b["step"] = "gated" if a.verdict == "PASS" else "failed"
        b["verdict"] = {"result": a.verdict, "note": a.note, "at": iso()}
        store.rec_save("batches", a.slug, b)
        events.emit("batch", batch=a.slug, step=b["step"], detail=a.note, batch_kind=b.get("kind"))
        print(f"{a.slug}: release gate {a.verdict} recorded")
        return
    ch = _load(a.slug)
    if a.verdict == "FAIL":
        store.set_hold(ch, "failed", a.note or "upstream gate FAIL (see the record)")
        events.emit("hold", change=ch["slug"], hold="failed", reason=a.note, source="gate")
    else:
        store.add_log(ch, f"gate PASS claimed: {a.note or ''} (the record on docs is what counts)")
    ch["owner"] = None
    store.save(ch)
    print(f"{ch['slug']}: gate {a.verdict} recorded")


def cmd_resolve(a):
    from . import batches
    b = store.rec_load("batches", a.batch)
    if not b:
        sys.exit(f"hd: no batch {a.batch}")
    b["resolution"] = a.note
    store.rec_save("batches", a.batch, b)
    events.emit("batch", batch=a.batch, step="resolved", detail=a.note)
    print(batches.resume(a.batch))


def _obs():
    from .observe import Observer
    return Observer()


def cmd_check(a):
    from . import check
    ch = _load(a.slug)
    passed, items = check.run(ch, _obs())
    print(check.render(ch, items))
    return 0 if passed else 1


def cmd_brief(a):
    from . import agents
    ch = _load(a.slug)
    role = policy.role_for(ch["track"], a.stage)
    inst = policy.track(ch["track"]).get("instances" if a.stage == "dev" else "qa_instances", [])
    print(agents.brief(ch, role, a.stage, inst))


def cmd_dispatch(a):
    from . import agents, stages
    ch = _load(a.slug)
    ex = a.stage or stages.next_executor(ch) or "dev"
    role = policy.role_for(ch["track"], ex)
    inst = policy.track(ch["track"]).get("instances" if ex == "dev" else "qa_instances", [])
    if a.print:
        os.environ["HD_SPAWN"] = "print"
    r = agents.spawn(ch, role, ex, inst)
    print(f"{r.get('name')} ({role}) -> {ch['slug']} ({ex}); pid {r.get('pid')}; log {r.get('log')}")


def cmd_agents(a):
    from . import agents
    rows = agents.running() if not a.all else store.rec_all("runs")
    if not rows:
        print("no running agents")
    for r in rows:
        res = agents._result(r["log"]) or {}
        tok = agents._tokens(res) or 0
        print(f"{r['name']:5} {r['role']:11} {str(r.get('change')):34} {r.get('stage'):5} since {local(r.get('started'))}  "
              f"turns {res.get('num_turns') or 0:<4} tokens {tok:<9} {'ended ' + str(r.get('exit'))[:30] if r.get('ended') else 'running'}")
        if r.get("session"):
            print(f"      open the chat: claude --resume {r['session']}    log: {r['log']}")
        else:
            print(f"      log: {r['log']}  (hd tail {r['name']})")


def cmd_tail(a):
    from . import agents
    r = store.rec_load("runs", a.name)
    if not r:
        sys.exit(f"hd: no run {a.name}")
    print(agents.transcript(r["log"], a.turns))


def cmd_lint(a):
    bad = 0
    idx = store.index()
    for ch in idx.values():
        for d in ch.get("deps") or []:
            if d not in idx:
                print(f"lint: {ch['slug']}: dep {d} unknown"); bad += 1
        if ch.stage not in store.STAGES:
            print(f"lint: {ch['slug']}: stage {ch.stage}"); bad += 1
        if ch.stage != "backlog" and not ch.has_dod():
            print(f"lint: {ch['slug']}: {ch.stage} without a definition of done"); bad += 1
        if ch.reached("checked") and not (ch.get("branch") and ch.get("commit")) and not (ch.stage == "live" and ch["track"] != "server"):
            print(f"lint: {ch['slug']}: {ch.stage} without branch and commit"); bad += 1
        if ch.get("track") == "server" and ch.get("branch") and policy.branch_track(ch["branch"]) is None:
            print(f"lint: {ch['slug']}: branch {ch['branch']} is not content/, fix/ or local/ (BR-C1)"); bad += 1
    seen = set()

    def dfs(n, stack):
        nonlocal bad
        if n in stack:
            print("lint: cycle " + " -> ".join(stack + [n])); bad += 1; return
        if n in seen:
            return
        for d in idx.get(n, {}).get("deps") or []:
            dfs(d, stack + [n])
        seen.add(n)
    for s in idx:
        dfs(s, [])
    print(f"lint: {len(idx)} changes, {bad} problems")
    return 1 if bad else 0


def cmd_status(a):
    from . import dashboard
    try:
        v = dashboard.view()
    except NotImplementedError:
        v = None
    if a.json:
        print(json.dumps(v, indent=1, default=str))
        return
    if v is None:
        waiting = (store.rec_load("view", "waiting") or {}).get("waiting", {})
        for ch in store.all_changes():
            print(fmt(ch, waiting.get(ch["slug"])))
        for r, st in leases.status_all().items():
            print(f"lease {r:12} {st.get('holder') or 'free':10} {st.get('purpose') or ''}")
        return
    print(dashboard.text(v))


def cmd_tick(a):
    from . import tick
    for line in tick.tick(dry_run=a.dry_run):
        print(line)


def cmd_run(a):
    from . import daemon
    daemon.run(a.interval, once=a.once)


def cmd_start(a):
    from . import daemon
    pid = daemon.start(a.interval, a.port)
    sv = daemon.server_status()
    print(f"harnessd started (pid {pid})" + (f"; dashboard http://127.0.0.1:{sv.get('port')}/" if sv.get("alive")
                                              else "; dashboard not running: hd serve --detach"))


def cmd_stop(a):
    from . import daemon
    print(daemon.stop())


def cmd_serve(a):
    from . import daemon
    if a.detach:
        pid = daemon.start_server(a.port)
        print(f"dashboard server started (pid {pid}); http://127.0.0.1:{a.port}/")
        return
    s = daemon.serve(a.port)
    print(f"serving http://127.0.0.1:{a.port}/ (Ctrl-C to stop)")
    try:
        import time
        while True:
            time.sleep(3600)
    except KeyboardInterrupt:
        s.shutdown()


def cmd_serve_stop(a):
    from . import daemon
    print(daemon.stop_server()["text"])


def cmd_daemon_status(a):
    from . import daemon
    print(json.dumps({"loop": daemon.status(), "server": daemon.server_status()}, indent=1))


def cmd_digest(a):
    from . import daemon
    print(daemon.digest() or "(nothing new)")


def cmd_lease(a):
    act = a.action
    if act == "status":
        rows = leases.status_all() if not a.res else {a.res: leases.status(a.res)}
        for r, st in rows.items():
            g = ", ".join(f"{w} until {local(v.get('until'))}" for w, v in (st.get("grants") or {}).items())
            d = " DRAINING" if st.get("drain") else ""
            if st.get("holder"):
                print(f"{r:12} held by {st['holder']} ({st.get('purpose') or ''}) since {local(st.get('since'))}, "
                      f"heartbeat {local(st.get('heartbeat'))}, until {local(st.get('expiry'))}{d}" + (f"; grants: {g}" if g else ""))
            else:
                print(f"{r:12} free{d}" + (f"; grants: {g}" if g else ""))
        return 0
    if act == "grant":
        leases.grant(a.res, a.who, a.minutes, reuse=not a.once); print(f"{a.res} granted to {a.who}"); return 0
    if act == "take":
        ok, msg = leases.take(a.res, a.who, a.what or "", pid=a.pid, container=a.container); print(msg); return 0 if ok else 1
    if act == "heartbeat":
        ok, msg = leases.heartbeat(a.res, a.who, pid=a.pid, container=a.container); print(msg); return 0 if ok else 1
    if act == "release":
        ok, msg = leases.release(a.res, a.who); print(msg); return 0 if ok else 1
    if act == "reclaim":
        ok, msg = leases.reclaim(a.res, a.what or "reclaimed by hand"); print(msg); return 0
    if act == "revoke":
        ok, msg = leases.revoke(a.res, a.who); print(msg); return 0
    if act == "holds":
        ok = leases.holds(a.res, a.who); print("yes" if ok else "no"); return 0 if ok else 1
    if act == "drain":
        leases.drain(a.res, a.who or leases.DAEMON); print(f"{a.res} draining"); return 0
    if act == "undrain":
        leases.undrain(a.res); print(f"{a.res} open"); return 0
    sys.exit(f"hd lease: unknown action {act}")


def cmd_approvals(a):
    from . import approvals
    rows = approvals.all_() if a.all else approvals.pending()
    if not rows:
        print("no pending approvals")
    for r in rows:
        try:
            print(approvals.render(r))
        except NotImplementedError:
            print(json.dumps(r, indent=1))
        print()


def cmd_approve(a):
    from . import approvals
    print(approvals.approve(a.id, a.note or ""))


def cmd_deny(a):
    from . import approvals
    print(approvals.deny(a.id, a.note))


def cmd_batches(a):
    for b in store.rec_all("batches"):
        if a.all or b.get("step") not in ("live", "published", "failed"):
            print(f"{b['id']:18} {b.get('kind', 'train'):8} {b.get('step', '?'):12} integration {str(b.get('integration') or '-')[:9]} "
                  f"includes {', '.join(b.get('includes') or []) or '-'}" + (f"  exception: {str(b.get('exception'))[:80]}" if b.get("exception") else ""))


def cmd_train(a):
    from . import batches
    for line in batches.advance(_obs(), now=a.now) or []:
        print(line)


def cmd_resume(a):
    from . import batches
    print(batches.resume(a.batch))


def cmd_release_request(a):
    from . import state_dir
    from .util import write_json
    write_json(os.path.join(state_dir(), "release.request"), {"tag": a.tag, "at": iso()})
    print(f"release {a.tag} requested; the next tick makes the batch")


def cmd_events(a):
    for e in events.read(kinds=[a.kind] if a.kind else None, change=a.change, limit=a.limit):
        rest = {k: v for k, v in e.items() if k not in ("ts", "kind")}
        print(f"{local(e['ts']):18} {e['kind']:9} " + json.dumps(rest, sort_keys=True)[:200])


def cmd_recount(a):
    from . import metrics
    print(", ".join(metrics.recount("hd recount")))


def cmd_kpi(a):
    from . import kpi
    print(json.dumps(kpi.compute(), indent=1, default=str))


def cmd_migrate(a):
    from . import migrate
    return migrate.main(apply=a.apply)


def main(argv=None):
    p = argparse.ArgumentParser(prog="hd", description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    sp = p.add_subparsers(dest="cmd", required=True)
    s = sp.add_parser("new"); s.add_argument("--track", required=True, choices=store.TRACKS); s.add_argument("--slug", required=True)
    s.add_argument("--title", required=True); s.add_argument("--prio", default="P2", choices=store.PRIOS); s.add_argument("--dod", action="append")
    s.add_argument("--deps"); s.add_argument("--branch"); s.add_argument("--spec"); s.add_argument("--network", action="store_true")
    s.add_argument("--local-only", action="store_true"); s.add_argument("--backlog", action="store_true"); s.add_argument("--text")
    s.add_argument("--server-dod", action="store_true"); s.set_defaults(f=cmd_new)
    s = sp.add_parser("list"); s.add_argument("--track"); s.add_argument("--stage"); s.add_argument("--all", action="store_true"); s.set_defaults(f=cmd_list)
    s = sp.add_parser("show"); s.add_argument("slug"); s.set_defaults(f=cmd_show)
    s = sp.add_parser("log"); s.add_argument("slug"); s.add_argument("message"); s.set_defaults(f=cmd_log)
    s = sp.add_parser("note"); s.add_argument("text"); s.add_argument("--kind", default="note"); s.set_defaults(f=cmd_note)
    s = sp.add_parser("handover"); s.add_argument("slug"); s.add_argument("--branch", required=True); s.add_argument("--commit", required=True)
    s.add_argument("--evidence"); s.add_argument("--summary"); s.add_argument("--pr"); s.add_argument("--ci"); s.add_argument("--repo")
    s.add_argument("--partial", action="store_true"); s.set_defaults(f=cmd_handover)
    s = sp.add_parser("block"); s.add_argument("slug"); s.add_argument("--by", required=True); s.add_argument("why", nargs="?"); s.set_defaults(f=cmd_block)
    s = sp.add_parser("fail"); s.add_argument("slug"); s.add_argument("why"); s.set_defaults(f=cmd_fail)
    s = sp.add_parser("hold"); s.add_argument("slug"); s.add_argument("kind", choices=["blocked", "decision", "failed", "exception"]); s.add_argument("reason"); s.set_defaults(f=cmd_hold)
    s = sp.add_parser("unhold"); s.add_argument("slug"); s.add_argument("why", nargs="?"); s.set_defaults(f=cmd_unhold)
    s = sp.add_parser("gate"); s.add_argument("slug"); s.add_argument("verdict", choices=["PASS", "FAIL"]); s.add_argument("note", nargs="?"); s.set_defaults(f=cmd_gate)
    s = sp.add_parser("resolve"); s.add_argument("batch"); s.add_argument("note"); s.set_defaults(f=cmd_resolve)
    s = sp.add_parser("check"); s.add_argument("slug"); s.set_defaults(f=cmd_check)
    s = sp.add_parser("brief"); s.add_argument("slug"); s.add_argument("--stage", default="dev"); s.set_defaults(f=cmd_brief)
    s = sp.add_parser("dispatch"); s.add_argument("slug"); s.add_argument("--stage"); s.add_argument("--print", action="store_true"); s.set_defaults(f=cmd_dispatch)
    sp.add_parser("lint").set_defaults(f=cmd_lint)
    s = sp.add_parser("agents"); s.add_argument("--all", action="store_true"); s.set_defaults(f=cmd_agents)
    s = sp.add_parser("tail"); s.add_argument("name"); s.add_argument("--turns", type=int, default=5); s.set_defaults(f=cmd_tail)
    s = sp.add_parser("status"); s.add_argument("--json", action="store_true"); s.set_defaults(f=cmd_status)
    s = sp.add_parser("tick"); s.add_argument("--dry-run", action="store_true"); s.set_defaults(f=cmd_tick)
    s = sp.add_parser("run"); s.add_argument("--interval", type=int, default=60); s.add_argument("--once", action="store_true"); s.set_defaults(f=cmd_run)
    s = sp.add_parser("start"); s.add_argument("--interval", type=int, default=60); s.add_argument("--port", type=int, default=7790); s.set_defaults(f=cmd_start)
    sp.add_parser("stop").set_defaults(f=cmd_stop)
    s = sp.add_parser("serve"); s.add_argument("--port", type=int, default=7790); s.add_argument("--detach", action="store_true"); s.set_defaults(f=cmd_serve)
    sp.add_parser("serve-stop").set_defaults(f=cmd_serve_stop)
    sp.add_parser("daemon-status").set_defaults(f=cmd_daemon_status)
    sp.add_parser("digest").set_defaults(f=cmd_digest)
    s = sp.add_parser("lease"); s.add_argument("action", choices=["grant", "take", "heartbeat", "release", "reclaim", "revoke", "status", "holds", "drain", "undrain"])
    s.add_argument("res", nargs="?"); s.add_argument("who", nargs="?"); s.add_argument("what", nargs="?")
    s.add_argument("--minutes", type=int); s.add_argument("--once", action="store_true"); s.add_argument("--pid", type=int); s.add_argument("--container")
    s.set_defaults(f=cmd_lease)
    s = sp.add_parser("approvals"); s.add_argument("--all", action="store_true"); s.set_defaults(f=cmd_approvals)
    s = sp.add_parser("approve"); s.add_argument("id"); s.add_argument("--note"); s.set_defaults(f=cmd_approve)
    s = sp.add_parser("deny"); s.add_argument("id"); s.add_argument("--note", required=True); s.set_defaults(f=cmd_deny)
    s = sp.add_parser("batches"); s.add_argument("--all", action="store_true"); s.set_defaults(f=cmd_batches)
    s = sp.add_parser("train"); s.add_argument("--now", action="store_true"); s.set_defaults(f=cmd_train)
    s = sp.add_parser("resume"); s.add_argument("batch"); s.set_defaults(f=cmd_resume)
    s = sp.add_parser("release-request"); s.add_argument("tag"); s.set_defaults(f=cmd_release_request)
    s = sp.add_parser("events"); s.add_argument("--kind"); s.add_argument("--change"); s.add_argument("--limit", type=int, default=50); s.set_defaults(f=cmd_events)
    sp.add_parser("kpi").set_defaults(f=cmd_kpi)
    sp.add_parser("recount").set_defaults(f=cmd_recount)
    s = sp.add_parser("migrate"); s.add_argument("--apply", action="store_true"); s.set_defaults(f=cmd_migrate)
    sp.add_parser("version").set_defaults(f=lambda a: print("hd 3.0"))
    a = p.parse_args(argv)
    try:
        rc = a.f(a)
    except NotImplementedError as e:
        sys.exit(f"hd: not implemented yet: {e or a.cmd}")
    return rc or 0
