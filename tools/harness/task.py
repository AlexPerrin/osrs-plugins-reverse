#!/usr/bin/env python3
"""The harness task store (docs/harness.md): one file per task in harness/tasks/<id>.md, YAML frontmatter plus a
markdown body (Task, Definition of done, Log). It is the single source of truth for what each track works on; the
orchestrator, the driver (orchestrate.py), the agents and the hooks all read and write it through this CLI.

  task.py new --track client|server|orch --title "..." [--id slug] [--prio P1] [--role client-dev]
              [--blocked-by id,...] [--dod "item" ...] [--body-file f] [--status backlog|ready]
              [--spec name] [--server-dod]    server: accuracy/<spec>.md; the standard DoD naming that file
  task.py list [--track t] [--status s,...] [--owner o]        one line per task
  task.py show <id>                                            the file
  task.py ready [--track t]                                    dispatchable: status ready, every blocked_by done
  task.py set <id> key=value ...                               any frontmatter key (status, owner, branch, commit, ...)
  task.py log <id> "message"                                   dated line under ## Log
  task.py claim <id> --owner cd47 [--instance device,desktop]  -> in-progress
  task.py handover <id> --branch b --commit sha [--evidence dir] [--pr url] [--ci url] --summary "..."
  task.py block <id> --by <other-id> ["why"]                   blocked_by edge both ways (unblocks on the other)
  task.py fail <id> "why" | done <id> | status                 verdicts; status = the per-track dashboard
  task.py lint                                                 schema, dangling refs, cycles, dod boxes
  task.py migrate-live [id ...] [--dry-run]                    one-off: shipped tasks already live -> live
  task.py brief <id>                                           the spawn brief for the Agent tool / claude -p
  task.py metrics                                              cycle times and rework counts from the logs

Statuses: backlog (not yet dispatchable), ready, in-progress, specced (server: spec committed, coding), blocked,
handover (dev finished; handover-check + ship next), shipped (server: in `integration`, not deployed yet), live
(deployed: on the live server, or for the client track merged into master), qa (companion running a gate), gated
(gate PASS; waiting for the user's OK), waiting-user, done, failed.
A blocker is closed (its dependents can run) once it is done, live, qa or gated; `shipped` is not closed, since a
server change in `integration` isn't on the live server until the deploy. Every path that sets live or done
(`done`, `set status=...`, `migrate-live`) re-readies the blocked tasks it unblocks.
HARNESS_DIR overrides harness/ (tests).
"""
import argparse
import datetime as dt
import os
import re
import subprocess
import sys

import yaml

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))


def main_repo(root):
    """From a git worktree (an agent's), the main checkout: the task store and evidence live there, once."""
    r = subprocess.run(["git", "-C", root, "rev-parse", "--git-common-dir"], capture_output=True, text=True)
    common = r.stdout.strip()
    if r.returncode == 0 and common.endswith(".git"):
        return os.path.dirname(os.path.abspath(os.path.join(root, common)))
    return root


HARNESS = os.environ.get("HARNESS_DIR") or os.path.join(main_repo(ROOT), "harness")
EVIDENCE = os.path.join(os.path.dirname(HARNESS), "re", "evidence")
TASKS = os.path.join(HARNESS, "tasks")
TRACKS = ("client", "server", "orch")
STATUSES = ("backlog", "ready", "in-progress", "specced", "blocked", "handover", "shipped", "live", "qa", "gated",
            "waiting-user", "done", "failed")
# A blocker in these states no longer blocks: it is deployed (live), past dev, or closed. Not `shipped`: a server task
# in `integration` isn't on the live server until the deploy (docs/integration-pipeline.md, phase 0).
CLOSED = ("done", "live", "gated", "qa")
UNBLOCKING = ("live", "done")   # entering one of these re-readies the blocked tasks this one unblocks
ROLES = {"client": "client-dev", "server": "server-dev", "orch": "orchestrator"}
KEYS = ("id", "track", "title", "status", "prio", "role", "owner", "instance", "branch", "commit", "pr", "ci",
        "evidence", "blocked_by", "unblocks", "created", "updated", "summary", "gate", "network", "spec")


def server_dod(spec):
    """The standard server dev DoD. The spec item names accuracy/<spec>.md, the same file handover-check.sh reads
    (it tries the task's spec: key, the branch slug, then the task id)."""
    return [f"accuracy/{spec}.md on the fork's docs branch (accuracy-check: sources, RSProx comparison, caveats)",
            "change built and checked in game on the branch server (evidence in re/evidence/<id>/)",
            "one change only, branch off upstream main (no stacking)",
            "fork PR open (tools/fork.sh pr-create) with green CI at the handed-over commit",
            "PR text reviewed (pr-text-review)"]


def now():
    return dt.datetime.now(dt.timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")


def path(tid):
    return os.path.join(TASKS, f"{tid}.md")


def slug_ok(s):
    return bool(re.fullmatch(r"[a-z0-9][a-z0-9.-]{1,60}", s))


def load(tid):
    p = path(tid)
    if not os.path.isfile(p):
        sys.exit(f"task.py: no task {tid} ({p})")
    text = open(p).read()
    m = re.match(r"---\n(.*?)\n---\n(.*)", text, re.S)
    if not m:
        sys.exit(f"task.py: {p} has no frontmatter")
    meta = yaml.safe_load(m.group(1)) or {}
    return meta, m.group(2)


def save(meta, body):
    meta["updated"] = now()
    for k in ("blocked_by", "unblocks", "instance"):
        if k in meta and meta[k] is None:
            meta[k] = []
    ordered = {k: meta[k] for k in KEYS if k in meta}
    ordered.update({k: v for k, v in meta.items() if k not in ordered})
    front = yaml.safe_dump(ordered, sort_keys=False, allow_unicode=True, width=200).rstrip("\n")
    tmp = path(meta["id"]) + ".tmp"
    with open(tmp, "w") as f:
        f.write(f"---\n{front}\n---\n{body.rstrip()}\n")
    os.replace(tmp, path(meta["id"]))


def all_tasks():
    if not os.path.isdir(TASKS):
        return []
    out = []
    for name in sorted(os.listdir(TASKS)):
        if name.endswith(".md"):
            out.append(load(name[:-3]))
    return out


def add_log(body, msg):
    line = f"- {now()} {msg}"
    if re.search(r"^## Log\s*$", body, re.M):
        return body.rstrip() + "\n" + line
    return body.rstrip() + "\n\n## Log\n" + line


def blockers_open(meta, index):
    return [b for b in meta.get("blocked_by") or [] if b not in index or index[b][0].get("status") not in CLOSED]


def unblock_dependents(tid):
    """Tasks `tid` unblocks that are `blocked` and have no open blocker left go back to `ready`. Called whenever a
    task becomes live or done (cmd_done, cmd_set, cmd_migrate_live)."""
    meta, _ = load(tid)
    out = []
    for other in meta.get("unblocks") or []:
        if not os.path.exists(path(other)):
            continue
        om, ob = load(other)
        if om["status"] != "blocked":
            continue
        index = {m["id"]: (m, b) for m, b in all_tasks()}
        if not blockers_open(om, index):
            om["status"] = "ready"
            save(om, add_log(ob, f"unblocked by {tid} ({meta['status']})"))
            print(f"{other}: ready")
            out.append(other)
    return out


def cmd_new(a):
    tid = a.id or re.sub(r"[^a-z0-9]+", "-", a.title.lower()).strip("-")[:60]
    if not slug_ok(tid):
        sys.exit(f"task.py: bad id '{tid}'")
    if os.path.exists(path(tid)):
        sys.exit(f"task.py: {tid} exists")
    os.makedirs(TASKS, exist_ok=True)
    meta = {"id": tid, "track": a.track, "title": a.title, "status": a.status, "prio": a.prio,
            "role": a.role or ROLES[a.track], "owner": None, "instance": [], "branch": None, "commit": None,
            "blocked_by": [s for s in (a.blocked_by or "").split(",") if s], "unblocks": [], "created": now()}
    if a.network:
        meta["network"] = True
    if a.spec:
        meta["spec"] = a.spec
    body = open(a.body_file).read() if a.body_file else ""
    if not re.search(r"^## Task", body, re.M):
        body = "## Task\n" + (a.task or a.title) + "\n\n" + body
    if not re.search(r"^## Definition of done", body, re.M):
        items = list(a.dod or [])
        if a.server_dod:
            if a.track != "server":
                sys.exit("task.py: --server-dod is for --track server")
            meta["spec"] = meta.get("spec") or tid   # the DoD and the check name the same file
            items = server_dod(meta["spec"]) + items
        dod = "\n".join(f"- [ ] {d}" for d in (items or ["(write the definition of done before dispatch)"]))
        body = body.rstrip() + "\n\n## Definition of done\n" + dod + "\n"
    body = add_log(body, f"created ({a.status})")
    save(meta, body)
    for b in meta["blocked_by"]:
        if os.path.exists(path(b)):
            bm, bb = load(b)
            if tid not in (bm.get("unblocks") or []):
                bm.setdefault("unblocks", []).append(tid)
                save(bm, add_log(bb, f"unblocks {tid}"))
    print(tid)


def fmt(meta, index=None):
    bl = ""
    if index is not None:
        o = blockers_open(meta, index)
        if o:
            bl = f"  blocked by {','.join(o)}"
    own = f"  @{meta['owner']}" if meta.get("owner") else ""
    return f"{meta.get('prio') or '--':3} {meta['track']:6} {meta['status']:12} {meta['id']:44}{own}{bl}  {meta['title'][:70]}"


def cmd_list(a):
    index = {m["id"]: (m, b) for m, b in all_tasks()}
    want = set(a.status.split(",")) if a.status else None
    for m, _ in all_tasks():
        if a.track and m["track"] != a.track:
            continue
        if want and m["status"] not in want:
            continue
        if a.owner and m.get("owner") != a.owner:
            continue
        print(fmt(m, index))


def ready_tasks(track=None):
    index = {m["id"]: (m, b) for m, b in all_tasks()}
    out = []
    for m, b in all_tasks():
        if track and m["track"] != track:
            continue
        if m["status"] == "ready" and not blockers_open(m, index):
            out.append((m, b))
    order = {"P0": 0, "P1": 1, "P2": 2, "P3": 3}
    out.sort(key=lambda t: (order.get(t[0].get("prio"), 9), t[0]["created"]))
    return out


def cmd_ready(a):
    index = {m["id"]: (m, b) for m, b in all_tasks()}
    for m, _ in ready_tasks(a.track):
        print(fmt(m, index))


def cmd_show(a):
    print(open(path(a.id)).read(), end="")


def cmd_set(a):
    meta, body = load(a.id)
    before = meta.get("status")
    changes = []
    for kv in a.pairs:
        k, _, v = kv.partition("=")
        if not _:
            sys.exit(f"task.py: '{kv}' is not key=value")
        if k == "status" and v not in STATUSES:
            sys.exit(f"task.py: unknown status '{v}' ({', '.join(STATUSES)})")
        if k in ("blocked_by", "unblocks", "instance"):
            v = [s for s in v.split(",") if s]
        elif v in ("", "null", "none"):
            v = None
        elif v in ("true", "false"):
            v = v == "true"
        meta[k] = v
        changes.append(kv)
    save(meta, add_log(body, "set " + " ".join(changes)))
    if meta.get("status") in UNBLOCKING and meta.get("status") != before:
        unblock_dependents(a.id)


def cmd_log(a):
    meta, body = load(a.id)
    save(meta, add_log(body, a.message))


def cmd_claim(a):
    meta, body = load(a.id)
    if meta["status"] not in ("ready", "blocked", "failed", "specced", "in-progress"):
        sys.exit(f"task.py: {a.id} is {meta['status']}, not claimable")
    meta.update(status="in-progress", owner=a.owner)
    if a.instance:
        meta["instance"] = a.instance.split(",")
    save(meta, add_log(body, f"claimed by {a.owner}" + (f" (instances {a.instance})" if a.instance else "")))


def cmd_handover(a):
    meta, body = load(a.id)
    sha = subprocess.run(["git", "-C", a.repo or ROOT, "rev-parse", "--verify", "-q", a.commit + "^{commit}"],
                         capture_output=True, text=True).stdout.strip()
    if not sha and not a.no_verify:
        sys.exit(f"task.py: commit {a.commit} not found in {a.repo or ROOT} (use --repo for a fork clone)")
    unticked = re.findall(r"^- \[ \] .*", body, re.M)
    if unticked and not a.partial:
        sys.exit("task.py: the definition of done still has unticked items (tick them with [x], or --partial "
                 "and say what is left in --summary):\n" + "\n".join(unticked))
    meta.update(status="handover", branch=a.branch, commit=sha or a.commit, summary=a.summary)
    if a.evidence:
        meta["evidence"] = a.evidence
    if a.pr:
        meta["pr"] = a.pr
    if a.ci:
        meta["ci"] = a.ci
    save(meta, add_log(body, f"handover {a.branch}@{(sha or a.commit)[:9]}: {a.summary}"))
    print(f"{a.id}: handover recorded; next: tools/harness/handover-check.sh {a.id}")


def cmd_block(a):
    meta, body = load(a.id)
    if a.by not in (meta.get("blocked_by") or []):
        meta.setdefault("blocked_by", []).append(a.by)
    meta["status"] = "blocked"
    meta["owner"] = None
    save(meta, add_log(body, f"blocked by {a.by}" + (f": {a.why}" if a.why else "")))
    if os.path.exists(path(a.by)):
        om, ob = load(a.by)
        if a.id not in (om.get("unblocks") or []):
            om.setdefault("unblocks", []).append(a.id)
            save(om, add_log(ob, f"unblocks {a.id}"))
    else:
        print(f"task.py: note: {a.by} doesn't exist yet; create it with `task.py new --id {a.by} ...`", file=sys.stderr)


def cmd_fail(a):
    meta, body = load(a.id)
    meta.update(status="failed", owner=None)
    save(meta, add_log(body, f"FAILED: {a.why}"))


def cmd_done(a):
    meta, body = load(a.id)
    meta.update(status="done", owner=None)
    save(meta, add_log(body, "done"))
    unblock_dependents(a.id)   # tasks this one blocked become dispatchable again


LIVE_LINE = re.compile(r"^- \S+ Live (\d{4}-\d\d-\d\d[^\n]*)$", re.M)


def live_reason(meta, body):
    """Why a `shipped` task is already live, or None: a client task's ship merged it into master (the client's live
    target); a server task's log has the orchestrator's `Live <date> ...` deploy line."""
    if meta.get("status") != "shipped":
        return None
    if meta.get("track") == "client":
        return "client ship = merged into master"
    hits = LIVE_LINE.findall(body.split("## Log")[-1])
    return f"deployed: Live {hits[-1].strip().rstrip('.')}" if hits else None


def cmd_migrate_live(a):
    """One-off for the `live` status (2026-10-20): shipped tasks that are already live become `live`, and the
    tasks they block are re-checked. Named ids must qualify too; a shipped server task without a Live log line (not
    deployed) stays shipped."""
    tasks = {m["id"]: (m, b) for m, b in all_tasks()}
    for tid in a.ids:
        if tid not in tasks:
            sys.exit(f"task.py: no task {tid}")
    moved = 0
    for tid, (m, b) in tasks.items():
        if a.ids and tid not in a.ids:
            continue
        why = live_reason(m, b)
        if not why:
            if a.ids:
                print(f"{tid}: stays {m['status']} (not live: no Live log line)")
            continue
        moved += 1
        print(f"{tid}: shipped -> live ({why})" + (" [dry run]" if a.dry_run else ""))
        if a.dry_run:
            continue
        m["status"] = "live"
        save(m, add_log(b, f"migrate-live: shipped -> live ({why})"))
        unblock_dependents(tid)
    print(f"migrate-live: {moved} task(s) {'would move' if a.dry_run else 'moved'} to live")


def cmd_status(a):
    tasks = all_tasks()
    index = {m["id"]: (m, b) for m, b in tasks}
    for track in TRACKS:
        rows = [m for m, _ in tasks if m["track"] == track]
        if not rows:
            continue
        counts = {}
        for m in rows:
            counts[m["status"]] = counts.get(m["status"], 0) + 1
        print(f"## {track}: " + ", ".join(f"{k} {v}" for k, v in sorted(counts.items())))
        for st in ("in-progress", "specced", "handover", "shipped", "qa", "gated", "waiting-user"):
            for m in rows:
                if m["status"] == st:
                    print("  " + fmt(m, index))
        rd = [m for m, _ in ready_tasks(track)]
        if rd:
            print(f"  ready ({len(rd)}): " + ", ".join(m["id"] for m in rd[:6]) + (" ..." if len(rd) > 6 else ""))
        bl = [m for m in rows if m["status"] == "blocked" or (m["status"] == "ready" and blockers_open(m, index))]
        if bl:
            print(f"  blocked ({len(bl)}): " + ", ".join(f"{m['id']}<-{','.join(blockers_open(m, index))}" for m in bl[:6]))


def cmd_lint(a):
    tasks = all_tasks()
    index = {m["id"]: (m, b) for m, b in tasks}
    bad = 0

    def err(msg):
        nonlocal bad
        bad += 1
        print("lint: " + msg)

    for m, b in tasks:
        tid = m.get("id")
        if not tid or not slug_ok(tid):
            err(f"{tid}: bad id")
        if m.get("track") not in TRACKS:
            err(f"{tid}: track {m.get('track')}")
        if m.get("status") not in STATUSES:
            err(f"{tid}: status {m.get('status')}")
        if not m.get("title"):
            err(f"{tid}: no title")
        if m.get("prio") and not re.fullmatch(r"P[0-3]", str(m["prio"])):
            err(f"{tid}: prio {m['prio']}")
        for k in ("blocked_by", "unblocks"):
            for o in m.get(k) or []:
                if o not in index:
                    err(f"{tid}: {k} names unknown task {o}")
        for o in m.get("blocked_by") or []:
            if o in index and tid not in (index[o][0].get("unblocks") or []):
                err(f"{tid}: blocked_by {o}, but {o} doesn't list it under unblocks")
        if not re.search(r"^## Definition of done", b, re.M):
            err(f"{tid}: no '## Definition of done' section")
        if m.get("status") in ("ready", "in-progress") and "(write the definition of done" in b:
            err(f"{tid}: {m['status']} without a definition of done")
        if m.get("status") in ("in-progress", "specced", "qa") and not m.get("owner"):
            err(f"{tid}: {m['status']} without an owner")
        if m.get("status") in ("handover", "shipped", "live", "qa", "gated") and not (m.get("branch") and m.get("commit")):
            err(f"{tid}: {m['status']} without branch and commit")

    # cycles in blocked_by
    def dfs(n, stack):
        if n in stack:
            err("cycle: " + " -> ".join(stack + [n]))
            return
        for o in index.get(n, ({}, ""))[0].get("blocked_by") or []:
            dfs(o, stack + [n])
    for tid in index:
        dfs(tid, [])
    print(f"lint: {len(tasks)} tasks, {bad} problems")
    sys.exit(1 if bad else 0)


def cmd_brief(a):
    meta, body = load(a.id)
    inst = ",".join(meta.get("instance") or []) or "none"
    print(f"""Harness task `{meta['id']}` ({meta['track']} track). Your name is `{meta.get('owner') or '<set at claim>'}`.
Read the task file with `tools/harness/task.py show {meta['id']}`: it holds the task, its definition of done and the log.
Instances granted to you (take them with tools/device-lock.sh, no need to ask): {inst}.
Evidence goes in `{EVIDENCE}/{meta['id']}/` (durable; never a session scratchpad or a worktree's re/).
Read game state through the MCP tools, not screenshots (user, 2026-10-19): `osrs-devtools` for the Android app
(get_widget, get_npc_menu, list_npcs, get_var, get_chat_history, dump_interface; push devtools.jar + `setprop
debug.osrsrl.devtools 1` + restart first, device-test skill), `osrs-devtools-desktop` for desktop RuneLite,
`osrs-mcp` for ids, wiki and cache. A screenshot is evidence only for how something is drawn.
When done: tick the definition of done in the task file, then
`tools/harness/task.py handover {meta['id']} --branch <branch> --commit <sha> --evidence {EVIDENCE}/{meta['id']} --summary "..."`
(add --pr/--ci for a server branch; --repo <clone> when the commit is in your fork clone).
Blocked by the other track: `task.py block {meta['id']} --by <new-task-id> "repro, expected behaviour, source"` and
create that task with `task.py new`. Can't finish: `task.py fail {meta['id']} "why"`. The stop hook won't let you
finish while the task is still in-progress.
Ending (user, 2026-10-20): once you have handed over, blocked or failed the task, you are done. Stop every background
job you started (TaskStop for background shells and Monitors), stop your dev container (`qa.sh dev-stop <name>`) and
any branch-server containers you started, free every lock, then send your one-line final message and end. Start no new
background work or waits after the hand-over; don't wait on CI after it either (the hand-over check reads CI itself).
""")
    print(body.split("## Log")[0].rstrip())


def cmd_metrics(a):
    tasks = all_tasks()
    rows = []
    for m, b in tasks:
        logs = re.findall(r"^- (\d{4}-\d\d-\d\dT\d\d:\d\d:\d\dZ) (.*)$", b, re.M)
        t = {k: None for k in ("claimed", "handover", "shipped", "done")}
        fails = 0
        for ts, msg in logs:
            if msg.startswith("claimed") and not t["claimed"]:
                t["claimed"] = ts
            elif msg.startswith("handover") and not t["handover"]:
                t["handover"] = ts
            elif msg.startswith("set status=shipped") or msg.startswith("shipped"):
                t["shipped"] = t["shipped"] or ts
            elif msg == "done":
                t["done"] = ts
            if msg.startswith("FAILED") or "gate FAIL" in msg:
                fails += 1
        rows.append((m, t, fails))

    def hours(x, y):
        if not (x and y):
            return None
        f = lambda s: dt.datetime.strptime(s, "%Y-%m-%dT%H:%M:%SZ")
        return (f(y) - f(x)).total_seconds() / 3600

    print("id | track | status | dev h (claim->handover) | ship h (handover->shipped) | fails")
    for m, t, fails in rows:
        d, s = hours(t["claimed"], t["handover"]), hours(t["handover"], t["shipped"])
        print(f"{m['id']} | {m['track']} | {m['status']} | {d if d is None else round(d, 1)} | "
              f"{s if s is None else round(s, 1)} | {fails}")
    by = {}
    for m, t, fails in rows:
        by.setdefault(m["track"], []).append((hours(t["claimed"], t["handover"]), fails))
    for track, vals in by.items():
        devs = [v for v, _ in vals if v is not None]
        print(f"{track}: {len(vals)} tasks, {len(devs)} with dev time, mean {round(sum(devs) / len(devs), 1) if devs else '-'} h, "
              f"rework events {sum(f for _, f in vals)}")


def main():
    p = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    sp = p.add_subparsers(dest="cmd", required=True)
    s = sp.add_parser("new"); s.add_argument("--track", required=True, choices=TRACKS); s.add_argument("--title", required=True)
    s.add_argument("--id"); s.add_argument("--prio", default="P2"); s.add_argument("--role"); s.add_argument("--blocked-by")
    s.add_argument("--dod", action="append"); s.add_argument("--task"); s.add_argument("--body-file")
    s.add_argument("--status", default="ready", choices=STATUSES); s.add_argument("--network", action="store_true")
    s.add_argument("--spec", help="server: the accuracy spec name (accuracy/<spec>.md), when not the branch slug or id")
    s.add_argument("--server-dod", action="store_true", help="server: the standard DoD, its spec item naming accuracy/<spec or id>.md")
    s.set_defaults(f=cmd_new)
    s = sp.add_parser("list"); s.add_argument("--track"); s.add_argument("--status"); s.add_argument("--owner"); s.set_defaults(f=cmd_list)
    s = sp.add_parser("ready"); s.add_argument("--track"); s.set_defaults(f=cmd_ready)
    s = sp.add_parser("show"); s.add_argument("id"); s.set_defaults(f=cmd_show)
    s = sp.add_parser("set"); s.add_argument("id"); s.add_argument("pairs", nargs="+"); s.set_defaults(f=cmd_set)
    s = sp.add_parser("log"); s.add_argument("id"); s.add_argument("message"); s.set_defaults(f=cmd_log)
    s = sp.add_parser("claim"); s.add_argument("id"); s.add_argument("--owner", required=True); s.add_argument("--instance"); s.set_defaults(f=cmd_claim)
    s = sp.add_parser("handover"); s.add_argument("id"); s.add_argument("--branch", required=True); s.add_argument("--commit", required=True)
    s.add_argument("--evidence"); s.add_argument("--pr"); s.add_argument("--ci"); s.add_argument("--summary", required=True)
    s.add_argument("--repo"); s.add_argument("--partial", action="store_true"); s.add_argument("--no-verify", action="store_true")
    s.set_defaults(f=cmd_handover)
    s = sp.add_parser("block"); s.add_argument("id"); s.add_argument("--by", required=True); s.add_argument("why", nargs="?"); s.set_defaults(f=cmd_block)
    s = sp.add_parser("fail"); s.add_argument("id"); s.add_argument("why"); s.set_defaults(f=cmd_fail)
    s = sp.add_parser("done"); s.add_argument("id"); s.set_defaults(f=cmd_done)
    s = sp.add_parser("status"); s.set_defaults(f=cmd_status)
    s = sp.add_parser("lint"); s.set_defaults(f=cmd_lint)
    s = sp.add_parser("brief"); s.add_argument("id"); s.set_defaults(f=cmd_brief)
    s = sp.add_parser("metrics"); s.set_defaults(f=cmd_metrics)
    s = sp.add_parser("migrate-live"); s.add_argument("ids", nargs="*"); s.add_argument("--dry-run", action="store_true")
    s.set_defaults(f=cmd_migrate_live)
    a = p.parse_args()
    a.f(a)


if __name__ == "__main__":
    main()
