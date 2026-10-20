#!/usr/bin/env python3
"""The harness driver (docs/harness.md): runs the loop over the task store without the orchestrator's chat turns.

  orchestrate.py status                       dashboard: tasks per track, runs, instances, build queue, approvals
  orchestrate.py next [--track t]             what would be dispatched now (slots, instances, blockers)
  orchestrate.py dispatch <id> [--owner n] [--instance a,b] [--minutes 300] [--run]
                                              name the agent, grant its instances (reuse), claim the task, print the
                                              brief for the Agent tool; --run starts `claude -p --agent <role>` in the
                                              background instead (headless mode)
  orchestrate.py reap                         finished runs -> hand-over check -> ship (client: master; server:
                                              integration, then a server-qa task); FAIL -> back to ready with the log
  orchestrate.py run [--once] [--interval 60] reap, dispatch while slots are free, repeat (the autonomous loop);
                                              --once does one pass
  orchestrate.py qa <id>                      create the QA follow-up task for a shipped server task
  orchestrate.py approvals                    actions waiting for the user (harness/approvals/)

Slots: HARNESS_MAX_CLIENT (1: one Android instance), HARNESS_MAX_SERVER (2), one QA companion per track. Headless
runs use HARNESS_PERMISSION_MODE (auto: the classifier decides, prompts are denied; bypassPermissions is refused by the
classifier when launched from a session); the role hooks
(deny_publish, qa_write_scope, slot_stop, task_stop) still apply. Nothing here touches upstream or releases: those
become approval files for the user's interactive session.
"""
import argparse
import datetime as dt
import glob
import json
import os
import re
import signal
import subprocess
import sys
import time

import yaml

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
HARNESS = os.environ.get("HARNESS_DIR") or os.path.join(ROOT, "harness")
TASKS = os.path.join(HARNESS, "tasks")
APPROVALS = os.path.join(HARNESS, "approvals")
RUNS = os.environ.get("HARNESS_RUNS") or os.path.join(ROOT, "re", "harness", "runs")
COUNTERS = os.path.join(RUNS, "counters.json")
TASK = [sys.executable, os.path.join(ROOT, "tools/harness/task.py")]
MAX = {"client": int(os.environ.get("HARNESS_MAX_CLIENT", "1")), "server": int(os.environ.get("HARNESS_MAX_SERVER", "2"))}
PREFIX = {"client-dev": "cd", "client-qa": "cq", "server-dev": "sd", "server-qa": "sq"}
DEFAULT_INSTANCES = {"client-dev": ["device"], "client-qa": ["device", "desktop"], "server-dev": ["desktop"], "server-qa": ["desktop"]}
ENV = dict(os.environ, HARNESS_DIR=HARNESS)
SHARED_IGNORED = ["re", "openrune/src", "openrune/rsprot"]


def sh(*args, check=False, env=None, **kw):
    return subprocess.run(args, capture_output=True, text=True, env=env or ENV, check=check, **kw)


def task(*args):
    r = sh(*TASK, *args)
    if r.returncode != 0:
        print(r.stdout + r.stderr, end="", file=sys.stderr)
    return r


def load(tid):
    text = open(os.path.join(TASKS, f"{tid}.md")).read()
    m = re.match(r"---\n(.*?)\n---\n(.*)", text, re.S)
    return yaml.safe_load(m.group(1)) or {}, m.group(2)


def tasks():
    return [load(os.path.basename(p)[:-3]) for p in sorted(glob.glob(os.path.join(TASKS, "*.md")))]


def runs():
    out = []
    for p in sorted(glob.glob(os.path.join(RUNS, "*.json"))):
        if os.path.basename(p) == "counters.json":
            continue
        with open(p) as f:
            r = json.load(f)
        r["_path"] = p
        r["alive"] = alive(r.get("pid"))
        out.append(r)
    return out


def alive(pid):
    if not pid:
        return False
    try:
        os.kill(pid, 0)
        return True
    except OSError:
        return False


def next_name(role):
    os.makedirs(RUNS, exist_ok=True)
    counters = {"cd": 46, "sd": 25, "sq": 25, "cq": 1}   # continues the gens in the handoff docs
    if os.path.exists(COUNTERS):
        counters.update(json.load(open(COUNTERS)))
    p = PREFIX[role]
    counters[p] += 1
    json.dump(counters, open(COUNTERS, "w"))
    return f"{p}{counters[p]}"


def in_flight(track=None, role=None):
    out = []
    for m, _ in tasks():
        if m["status"] in ("in-progress", "specced", "qa") and m.get("owner"):
            if (track is None or m["track"] == track) and (role is None or m.get("role") == role):
                out.append(m)
    return out


def dispatchable(track=None):
    """Ready tasks that fit the free slots, in priority order, one list per role."""
    r = task("ready", *(["--track", track] if track else []))
    ids = [line.split()[3] for line in r.stdout.splitlines() if line.strip()]
    out = []
    for tid in ids:
        m, _ = load(tid)
        role = m.get("role") or ""
        if role not in PREFIX:
            continue   # orchestrator chores aren't dispatched to agents
        if role.endswith("-dev"):
            if len(in_flight(m["track"], role)) + sum(1 for o in out if o["track"] == m["track"] and o["role"] == role) >= MAX[m["track"]]:
                continue
        else:
            if in_flight(m["track"], role) or any(o["role"] == role for o in out):
                continue
        out.append(m)
    return out


def cmd_status(a):
    print(task("status").stdout, end="")
    rs = runs()
    if rs:
        print("## runs")
        for r in rs:
            print(f"  {r['owner']:6} {r['role']:11} {r['task']:40} {'running' if r['alive'] else 'ended':8} since {r['started']}  log {os.path.relpath(r['log'], ROOT)}")
    print("## instances")
    print(sh("bash", os.path.join(ROOT, "tools/device-lock.sh"), "status-all").stdout.rstrip())
    print("## build queue")
    print(sh("bash", os.path.join(ROOT, "tools/buildq.sh"), "status").stdout.rstrip())
    ap = sorted(glob.glob(os.path.join(APPROVALS, "*.md")))
    if ap:
        print(f"## approvals waiting for the user ({len(ap)}): " + ", ".join(os.path.basename(p) for p in ap))


def cmd_next(a):
    for m in dispatchable(a.track):
        print(f"{m['prio']:3} {m['track']:6} {m['role']:11} {m['id']:44} {m['title'][:60]}")


def dispatch(tid, owner=None, instances=None, minutes=300, run=False):
    m, body = load(tid)
    role = m.get("role")
    if role not in PREFIX:
        sys.exit(f"orchestrate: {tid} has role {role}; only the four agent roles are dispatched")
    if "(write the definition of done" in body:
        sys.exit(f"orchestrate: {tid} has no definition of done yet")
    owner = owner or next_name(role)
    instances = instances if instances is not None else DEFAULT_INSTANCES[role]
    for inst in instances:
        r = sh("bash", os.path.join(ROOT, "tools/device-lock.sh"), "grant", owner, str(minutes), "reuse", env=dict(ENV, LOCK_NAME=inst))
        print(r.stdout.strip() or r.stderr.strip())
    task("claim", tid, "--owner", owner, *(["--instance", ",".join(instances)] if instances else []))
    brief = task("brief", tid).stdout
    if not run:
        print(f"--- spawn with the Agent tool: subagent_type={role}" + (", isolation=worktree" if role.startswith("client") else "") + " ---")
        print(brief)
        return owner
    os.makedirs(RUNS, exist_ok=True)
    log = os.path.join(RUNS, f"{owner}.log")
    cwd = ROOT
    if role.startswith("client"):
        cwd = os.path.join(ROOT, "re/harness/wt", owner)
        if not os.path.isdir(cwd):
            sh("git", "-C", ROOT, "worktree", "add", "-q", "--detach", cwd, "master", check=True)
        # Git-ignored inputs the build needs, shared with the main checkout: re/ (outputs, Ghidra, sources), the
        # official APKs to repack, and the OpenRune tree (game key for the repack's modulus, osrs-mcp data).
        for name in SHARED_IGNORED + [f for f in os.listdir(ROOT) if f.endswith(".apk")]:
            if os.path.exists(os.path.join(ROOT, name)) and not os.path.lexists(os.path.join(cwd, name)):
                os.symlink(os.path.join(ROOT, name), os.path.join(cwd, name))
    cmd = ["claude", "-p", "--agent", role, "--permission-mode", os.environ.get("HARNESS_PERMISSION_MODE", "auto"),
           "--output-format", "stream-json", "--verbose", "--no-session-persistence", brief]
    with open(log, "w") as f:
        proc = subprocess.Popen(cmd, cwd=cwd, stdout=f, stderr=subprocess.STDOUT, stdin=subprocess.DEVNULL,
                                env=dict(ENV, CLAUDE_PROJECT_DIR=cwd), start_new_session=True)
    rec = {"task": tid, "owner": owner, "role": role, "pid": proc.pid, "started": dt.datetime.now().strftime("%Y-%m-%d %H:%M"),
           "log": log, "cwd": cwd}
    json.dump(rec, open(os.path.join(RUNS, f"{owner}.json"), "w"), indent=1)
    task("log", tid, f"run {owner} started (pid {proc.pid}, headless {role})")
    print(f"started {owner} for {tid} (pid {proc.pid}); log {os.path.relpath(log, ROOT)}")
    return owner


def cmd_dispatch(a):
    dispatch(a.id, a.owner, a.instance.split(",") if a.instance is not None else None, a.minutes, a.run)


def run_summary(log):
    try:
        j = None
        for line in open(log, errors="replace"):
            if line.startswith("{"):
                try:
                    d = json.loads(line)
                except ValueError:
                    continue
                if d.get("type") == "result":
                    j = d
        if j is None:
            raise IndexError
        return (f"{j.get('num_turns', '?')} turns, {round(j.get('duration_ms', 0) / 60000)} min, "
                f"${round(j.get('total_cost_usd', 0), 2)}" + (", is_error" if j.get("is_error") else "")), j.get("result", "")
    except (IndexError, ValueError, OSError):
        return "no result json in the log", ""


def free_instances(owner, instances):
    for inst in instances or []:
        env = dict(ENV, LOCK_NAME=inst)
        if sh("bash", os.path.join(ROOT, "tools/device-lock.sh"), "status", env=env).stdout.find(f"({owner} dev,") >= 0:
            print(sh("bash", os.path.join(ROOT, "tools/device-lock.sh"), "free", owner, env=env).stdout.strip())
        gp = f"/tmp/osrs-{inst}.grant.d/{owner}"
        if os.path.exists(gp):
            os.remove(gp)


def after_handover(tid):
    """The evaluator and the ship step, for a task a dev handed over."""
    chk = sh("bash", os.path.join(ROOT, "tools/harness/handover-check.sh"), tid)
    print(chk.stdout.rstrip())
    if chk.returncode != 0:
        task("set", tid, "status=ready", "owner=")
        task("log", tid, "back to ready after the hand-over check FAIL; the next dev fixes exactly those items")
        return False
    ship = sh("bash", os.path.join(ROOT, "tools/harness/ship.sh"), tid)
    print(ship.stdout.rstrip(), ship.stderr.rstrip())
    if ship.returncode != 0:
        task("log", tid, "ship.sh failed; orchestrator action needed (see the driver output)")
        return False
    m, _ = load(tid)
    if m["track"] == "server":
        make_qa(tid)
    return True


def make_qa(tid):
    m, _ = load(tid)
    qid = f"qa-{tid}"[:60]
    if os.path.exists(os.path.join(TASKS, f"{qid}.md")):
        return qid
    body = (f"## Task\nUpstream gate (server-qa) for `{m['branch']}` at `{m['commit']}` (dev task `{tid}`: {m.get('summary', '')}). "
            f"Fork PR: {m.get('pr', '?')}. Record `qa/{m['branch'].split('/', 1)[-1]}.md` on the fork's docs branch and push it; "
            f"then `task.py set {qid} status=gated` (PASS) or `status=failed` (FAIL, with what must change in the log).\n\n"
            "## Definition of done\n- [ ] Independent accuracy-check incl. RSProx comparison, against the sources\n"
            "- [ ] In-game re-check on the branch server (desktop)\n- [ ] CI green at the exact commit; one change only; off current main; "
            "fix_gameval_conflicts --check clean\n- [ ] CONTRIBUTING, AGENTS.md, PR template; pr-text-review; caveats complete\n"
            "- [ ] Record pushed to fork docs with `## Upstream gate: **PASS**` or **FAIL** at the commit\n")
    bf = os.path.join(RUNS, f"{qid}.body.md")
    os.makedirs(RUNS, exist_ok=True)
    open(bf, "w").write(body)
    task("new", "--track", "server", "--id", qid, "--title", f"Upstream gate: {m['title']}", "--prio", m.get("prio") or "P2",
         "--role", "server-qa", "--body-file", bf)
    task("set", qid, f"branch={m['branch']}", f"commit={m['commit']}", "status=ready")
    print(f"created {qid}")
    return qid


def make_fix(qid):
    """A QA FAIL becomes a fix task for the dev role, carrying the FAIL log lines; the gate re-runs on the new commit."""
    m, body = load(qid)
    dev_id = qid[3:] if qid.startswith("qa-") else qid
    dev_role = (m.get("role") or "").replace("-qa", "-dev")
    track = m["track"]
    fid = f"{dev_id}-fix"[:60]
    n = 2
    while os.path.exists(os.path.join(TASKS, f"{fid}.md")):
        fid = f"{dev_id}-fix{n}"[:60]; n += 1
    fails = "\n".join(l for l in body.split("## Log")[-1].splitlines() if "FAIL" in l) or "(see the QA record)"
    text = (f"## Task\nFix what the gate found on `{m.get('branch')}` at `{m.get('commit')}` (QA task `{qid}`, record on the "
            f"fork's docs branch), on the same branch; the gate re-runs on the new commit.\n\nGate FAIL lines:\n{fails}\n\n"
            f"## Definition of done\n- [ ] Every FAIL item fixed, with the evidence the gate asked for\n- [ ] Branch pushed, CI green at the new commit\n")
    bf = os.path.join(RUNS, f"{fid}.body.md")
    os.makedirs(RUNS, exist_ok=True)
    open(bf, "w").write(text)
    task("new", "--track", track, "--id", fid, "--title", f"Fix after gate FAIL: {m['title']}", "--prio", m.get("prio") or "P1",
         "--role", dev_role, "--body-file", bf)
    task("log", qid, f"fix task {fid} created")
    print(f"created {fid}")
    return fid


def request_approval(tid, text):
    os.makedirs(APPROVALS, exist_ok=True)
    p = os.path.join(APPROVALS, f"{tid}.md")
    open(p, "w").write(f"# Approval: {tid}\n\nStatus: pending (the user decides; the orchestrator runs it in the interactive session, "
                       f"then `task.py done {tid}` and deletes this file)\n\n{text}\n")
    task("log", tid, f"approval requested: harness/approvals/{tid}.md")
    print(f"approval requested: {os.path.relpath(p, ROOT)}")


def cmd_reap(a):
    for r in runs():
        if r["alive"]:
            continue
        summary, result = run_summary(r["log"])
        tid, owner = r["task"], r["owner"]
        m, _ = load(tid)
        task("log", tid, f"run {owner} ended: {summary}")
        free_instances(owner, m.get("instance"))
        if m["status"] in ("in-progress", "specced"):
            task("set", tid, "status=ready", "owner=")
            task("log", tid, f"run ended without a hand-over; last words: {result[-300:].strip()!r}")
        elif m["status"] == "handover":
            after_handover(tid)
        elif m["status"] == "gated":
            request_approval(tid, f"Upstream PR for `{m['branch']}` at `{m['commit']}` (gate PASS, record on fork docs). "
                                  f"Open with the exact command in the interactive session; publish_gate.py checks the record.")
        elif m["status"] == "failed" and (m.get("role") or "").endswith("-qa"):
            make_fix(tid)
        if r.get("cwd", ROOT) != ROOT and os.path.isdir(r["cwd"]):
            sh("git", "-C", ROOT, "worktree", "remove", "--force", r["cwd"])
        os.replace(r["_path"], r["_path"] + ".done")
        print(f"reaped {owner} ({tid}): {summary}")
    # tasks handed over by in-session agents (no run record)
    for m, body in tasks():
        if m["status"] == "handover" and "handover-check" not in body.split("## Log")[-1].split(m["commit"][:9])[-1]:
            print(f"hand-over without a check: {m['id']}")
            after_handover(m["id"])
        elif m["status"] == "gated" and not os.path.exists(os.path.join(APPROVALS, f"{m['id']}.md")) and "approval requested" not in body:
            request_approval(m["id"], f"Upstream PR for `{m.get('branch')}` at `{m.get('commit')}` (gate PASS).")


def cmd_run(a):
    stop = {"flag": False}
    signal.signal(signal.SIGINT, lambda *_: stop.__setitem__("flag", True))
    while True:
        cmd_reap(a)
        for m in dispatchable():
            dispatch(m["id"], run=True)
        if a.once or stop["flag"]:
            break
        time.sleep(a.interval)


def cmd_qa(a):
    make_qa(a.id)


def cmd_approvals(a):
    for p in sorted(glob.glob(os.path.join(APPROVALS, "*.md"))):
        print(f"== {os.path.relpath(p, ROOT)}")
        print(open(p).read().rstrip())


def main():
    p = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    sp = p.add_subparsers(dest="cmd", required=True)
    sp.add_parser("status").set_defaults(f=cmd_status)
    s = sp.add_parser("next"); s.add_argument("--track"); s.set_defaults(f=cmd_next)
    s = sp.add_parser("dispatch"); s.add_argument("id"); s.add_argument("--owner"); s.add_argument("--instance")
    s.add_argument("--minutes", type=int, default=300); s.add_argument("--run", action="store_true"); s.set_defaults(f=cmd_dispatch)
    s = sp.add_parser("reap"); s.set_defaults(f=cmd_reap)
    s = sp.add_parser("run"); s.add_argument("--once", action="store_true"); s.add_argument("--interval", type=int, default=60); s.set_defaults(f=cmd_run)
    s = sp.add_parser("qa"); s.add_argument("id"); s.set_defaults(f=cmd_qa)
    sp.add_parser("approvals").set_defaults(f=cmd_approvals)
    a = p.parse_args()
    a.f(a)


if __name__ == "__main__":
    main()
