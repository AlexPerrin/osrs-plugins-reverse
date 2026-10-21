"""v2 task store (harness/tasks/*.md) -> v3 changes (harness/changes/*.md), once. The daemon's first tick then
reconciles every stage from observations, so this only has to put each change near its real stage.

Rules (BR-C1: one change per branch):
- A server dev task with a branch becomes the change named by the branch slug (`content/x`, `fix/x`, `local/x` ->
  `x`); tasks on the same branch fold into one change (the newest open one wins the stage). acc/* and fin/*
  branches are kept as recorded with a `decision` hold: fold into the PR branch (the user decides per PR).
- A server task without a branch keeps its id as slug (backlog content tasks, specs to write).
- QA tasks (role *-qa, ids qa-*/gate-*): folded into their dev change: gated -> gated_commit; failed -> a `failed`
  hold with the FAIL log lines; otherwise dropped (the gate runs when the change is live).
- Fix tasks (`*-fix`, `fix-*-gate-findings`): folded as the `failed` hold's reason on their branch's change.
- `spec-*` tasks for live content: a `failed` hold "spec missing" on the branch's change (the dev writes it before
  the gate).
- Client tasks: slug = the task id; done -> live (the client's terminal stage); blocked -> ready + blocked hold.
- Orchestrator tasks: done and the integration-phase-* ones are history (v3 replaces them); the rest become
  harness-track changes. Train tasks (int-*) become batch records.
- Branches in integration.txt with no task at all become live changes at their pin (local/* local-only), so every
  branch the live server runs is one row.
Statuses: backlog->backlog, ready->ready, blocked->ready+blocked, in-progress/specced->ready (no agent runs now),
handover->dev with the commit (the check runs), shipped->checked, live/qa->live, gated->gated,
waiting-user->published, done->live (client) / live (server, observation decides), failed->ready+failed hold.
Dependencies: blocked_by edges mapped to change slugs.
"""
import os
import re
import sys

import yaml

from . import ROOT, changes_dir, events, store
from .util import iso

STATUS_STAGE = {"backlog": "backlog", "ready": "ready", "blocked": "ready", "in-progress": "ready", "specced": "ready",
                "handover": "dev", "shipped": "checked", "live": "live", "qa": "live", "gated": "gated",
                "waiting-user": "published", "done": "live", "failed": "ready"}
UPSTREAM_PR = re.compile(r"github\.com/OpenRune/OpenRune-Server/pull/(\d+)", re.I)
HISTORY_ORCH = re.compile(r"^(integration-phase-|wait-|repin-|int-|cleanup-docker|deny-publish-stash|edgeville-general-store-stock)")
# Known oddities of the v2 store
OVERRIDES = {
    "rune-pouch-accuracy": {"track": "server", "branch": "fix/rune-pouch-accuracy", "commit": "bee197ba90c7cec7a56e1174651b21662c70bf08",
                            "upstream_pr": 370, "stage": "published"},
}


def tasks_dir():
    return os.path.join(os.path.dirname(changes_dir()), "tasks")


def load_tasks():
    out = []
    d = tasks_dir()
    if not os.path.isdir(d):
        return out
    for name in sorted(os.listdir(d)):
        if not name.endswith(".md"):
            continue
        text = open(os.path.join(d, name)).read()
        m = re.match(r"---\n(.*?)\n---\n(.*)", text, re.S)
        if not m:
            continue
        meta = yaml.safe_load(m.group(1)) or {}
        out.append((meta, m.group(2)))
    return out


def branch_slug(branch):
    return branch.split("/", 1)[-1] if branch and "/" in branch else branch


def integration_branches(clone=None):
    """{branch: pin} from the fork's local/integration-tools (HD_INTEGRATION_LIST/HD_INTEGRATION_LOCK: files, tests)."""
    import subprocess
    lst = os.environ.get("HD_INTEGRATION_LIST")
    lock = os.environ.get("HD_INTEGRATION_LOCK")
    if lst:
        text = open(lst).read()
        pins_text = open(lock).read() if lock else ""
    else:
        c = clone or os.path.join(ROOT, "re", "upstream-pr", "or")
        ref = "refs/remotes/fork/local/integration-tools"
        g = lambda p: subprocess.run(["git", "-C", c, "show", f"{ref}:{p}"], capture_output=True, text=True).stdout
        text, pins_text = g("integration.txt"), g("integration.lock")
    pins = {}
    for l in pins_text.splitlines():
        w = l.split()
        if len(w) >= 2 and not w[0].startswith("#"):
            pins[w[0]] = w[1]
    out = {}
    for l in text.splitlines():
        b = l.strip()
        if b and not b.startswith("#") and b != "local/integration-tools":
            out[b] = pins.get(b)
    return out


def body_parts(body):
    task = body.split("## Definition of done")[0].replace("## Task", "").strip()
    dod = re.findall(r"^- \[([ x])\] (.*)$", body.split("## Definition of done")[-1].split("## Log")[0], re.M)
    log = body.split("## Log")[-1] if "## Log" in body else ""
    return task, dod, log


def plan():
    """[(slug, change dict, body, notes)] and [batch dicts]; nothing written."""
    ts = load_tasks()
    index = {m["id"]: (m, b) for m, b in ts}
    changes, notes, batches, by_branch = {}, [], [], {}
    id_to_slug = {}

    def fails_of(log):
        return [l.strip("- ").strip() for l in log.splitlines() if "FAIL" in l][-3:]

    def ensure(slug, track, title, prio, text, dod, branch=None):
        if slug in changes:
            c = changes[slug]
            if text and text not in c["text"]:
                c["text"] += "\n\n" + text
            for ok, item in dod:
                if item not in [i for _, i in c["dod"]]:
                    c["dod"].append((ok, item))
            return c
        c = {"slug": slug, "track": track, "title": title, "prio": prio or "P2", "text": text, "dod": list(dod),
             "stage": "backlog", "hold": None, "deps": [], "branch": branch, "commit": None, "local_only": False,
             "folded": [], "created": None}
        changes[slug] = c
        if branch:
            by_branch[branch] = slug
        return c

    def better(stage_a, stage_b):
        return store.STAGES.index(stage_b) > store.STAGES.index(stage_a)

    # pass 1: dev tasks (server and client) and harness tasks, the furthest-along first so pending work on a live
    # branch is seen as such
    rank = lambda mb: -store.STAGES.index(STATUS_STAGE.get(mb[0]["status"], "ready"))
    for m, b in sorted(ts, key=rank):
        tid, track, role, status = m["id"], m["track"], m.get("role") or "", m["status"]
        text, dod, log = body_parts(b)
        branch = m.get("branch")
        if tid in OVERRIDES:
            o = OVERRIDES[tid]
            c = ensure(tid, o["track"], m["title"], m.get("prio"), text, dod, o["branch"])
            c.update(stage=o["stage"], commit=o["commit"], upstream_pr=o.get("upstream_pr"), created=m.get("created"))
            id_to_slug[tid] = tid
            continue
        if role == "integration" or tid.startswith("int-"):
            batches.append({"id": tid, "kind": "train", "includes": list(m.get("includes") or []),
                            "integration": m.get("integration"), "tools": m.get("tools"),
                            "step": "live" if status in ("done", "live") else m.get("stage") or "built", "created": m.get("created"),
                            "migrated": True})
            continue
        if role.endswith("-qa") or re.match(r"^(qa-|gate-)", tid) or tid.endswith("-fix") or re.match(r"^fix-.*-gate-findings$", tid) \
                or (tid.startswith("spec-") and status != "backlog" and branch is None):
            continue   # folded in pass 2
        if track == "orch":
            if status == "done" or HISTORY_ORCH.match(tid):
                notes.append(f"skip {tid} (orch history: {status})")
                continue
            track = "harness"
        if track == "server" and status == "done" and not branch:
            notes.append(f"skip {tid} (server done, no branch)")
            continue
        slug = branch_slug(branch) if (track == "server" and branch) else (branch_slug(branch) if track == "client" and branch and branch.startswith("client/") else tid)
        if track == "client" and branch and not branch.startswith("client/"):
            branch = None   # master or odd names: the slug is the id
        c = ensure(slug, track, m["title"], m.get("prio"), text, dod, branch)
        id_to_slug[tid] = slug
        stage = STATUS_STAGE.get(status, "ready")
        if track in ("client", "harness") and status == "done":
            stage = "live"
        if better(c["stage"], stage) or c["commit"] is None:
            c["stage"] = stage if better(c["stage"], stage) else c["stage"]
        if m.get("commit") and (c["commit"] is None or status not in ("done",)):
            c["commit"] = m["commit"]
        for k in ("pr", "ci", "evidence", "summary", "spec", "network"):
            if m.get(k) and not c.get(k):
                c[k] = m[k]
        if branch and branch.startswith("local/"):
            c["local_only"] = True
        n = UPSTREAM_PR.search(str(m.get("pr") or "")) or UPSTREAM_PR.search(log)
        if n and not c.get("upstream_pr"):
            c["upstream_pr"] = int(n.group(1))
        if status in ("ready", "backlog") and c["folded"] and store.STAGES.index(c["stage"]) >= store.STAGES.index("checked"):
            c["hold"] = ("failed", f"further work on the branch ({tid}): {m['title']} (a new commit re-enters at dev)")
        elif status == "blocked":
            c["hold"] = ("blocked", "waiting for " + ", ".join(m.get("blocked_by") or []))
        elif status == "failed":
            c["hold"] = ("failed", "; ".join(fails_of(log)) or "failed in v2 (see the log)")
        elif status in ("in-progress", "specced"):
            c["text"] += f"\n\n(v2: was {status} with {m.get('owner')} when v3 started; no agent runs now)"
        c["created"] = min(filter(None, [c["created"], m.get("created")]), default=None)
        c["folded"].append(tid)
        c["_deps_raw"] = list(set(c.get("_deps_raw", []) + list(m.get("blocked_by") or [])))
    # pass 3: integration.txt branches without a change
    try:
        ib = integration_branches()
    except Exception as e:   # no clone (tests)
        ib, _ = {}, notes.append(f"integration list unreadable: {e}")
    for branch, pin in ib.items():
        if branch in by_branch:
            c = changes[by_branch[branch]]
            if c["stage"] in ("backlog", "ready", "dev", "checked") and c.get("commit") in (None, pin):
                if c["stage"] in ("backlog", "ready") and not c["hold"]:
                    c["hold"] = ("failed", f"further work on the branch ({', '.join(c['folded'])}): {c['title']} "
                                           "(a new commit re-enters at dev)")
                c["stage"] = "live"
                c["commit"] = c["commit"] or pin
            continue
        slug = branch_slug(branch)
        if slug in changes:
            slug = f"{slug}-{branch.split('/')[0]}"
        c = ensure(slug, "server", f"{branch} (in integration since before v3)", "P3", f"Branch `{branch}` carried by the live server at its pin; no v2 task.", [(True, "in integration")], branch)
        c.update(stage="live", commit=pin, local_only=branch.startswith("local/"), created=iso())
        c["folded"].append("(integration.txt)")
    # pass 2: QA, fix and spec tasks fold into their branch's change
    for m, b in ts:
        tid, role, status, branch = m["id"], m.get("role") or "", m["status"], m.get("branch")
        text, dod, log = body_parts(b)
        is_qa = role.endswith("-qa") or re.match(r"^(qa-|gate-)", tid)
        is_spec = tid.startswith("spec-") and status != "backlog" and branch is None
        is_fix = not is_spec and (tid.endswith("-fix") or re.match(r"^fix-.*-gate-findings$", tid))
        if not (is_qa or is_fix or is_spec) or tid in OVERRIDES:
            continue
        target = by_branch.get(branch) if branch else None
        if not target:
            # by name: gate-<x>, qa-<x>, spec-<x>, <x>-fix
            cand = re.sub(r"-fix\d*$", "", tid) if is_fix and not re.match(r"^fix-.*-gate-findings$", tid) else tid
            cand = re.sub(r"^fix-(.*)-gate-findings$", r"\1", cand)
            cand = re.sub(r"^(qa-|gate-|spec-)", "", cand)
            cand = re.sub(r"^acc-", "", cand)
            target = cand if cand in changes else None
            if not target:
                for s_ in changes:
                    if s_.endswith("-" + cand) or cand.endswith("-" + s_) or re.sub(r"^\d+-", "", s_) == cand:
                        target = s_
                        break
        if not target:
            if status in ("done",):
                notes.append(f"skip {tid} ({status}, nothing to fold into)")
                continue
            slug = tid
            c = ensure(slug, "server", m["title"], m.get("prio"), text, dod, branch)
            c["stage"] = STATUS_STAGE.get(status, "ready")
            c["commit"] = m.get("commit")
            c["folded"].append(tid)
            id_to_slug[tid] = slug
            notes.append(f"{tid}: no branch change found; kept as its own change")
            continue
        c = changes[target]
        c["folded"].append(tid)
        if is_qa:
            if status == "gated" and m.get("commit"):
                c["gated_commit"] = m["commit"]
                if better(c["stage"], "gated") and c["commit"] == m["commit"]:
                    c["stage"] = "gated"
            elif status == "failed":
                c["hold"] = ("failed", f"upstream gate FAIL ({tid}): " + ("; ".join(fails_of(log)) or "see the record"))
            elif status in ("ready", "blocked", "qa"):
                notes.append(f"{tid} ({status}) folded into {target}: the gate runs when it is live")
            n = UPSTREAM_PR.search(str(m.get("pr") or "")) or UPSTREAM_PR.search(log)
            if n and not c.get("upstream_pr"):
                c["upstream_pr"] = int(n.group(1))
        elif is_fix and status not in ("done",):
            c["hold"] = ("failed", f"{m['title']} ({tid}): {text[:300]}")
            if m.get("prio") == "P1":
                c["prio"] = "P1"
        elif is_spec and status not in ("done",):
            c["hold"] = ("failed", f"spec missing ({tid}): {text[:200]}")
        id_to_slug[tid] = target
    # holds for acc/* and fin/* branches (BR-C1)
    for c in changes.values():
        b = c.get("branch") or ""
        if c["track"] == "server" and b and not any(b.startswith(p) for p in ("content/", "fix/", "local/")):
            pr_branch = "content/" + branch_slug(b)
            then = f"; then: {c['hold'][1]}" if c["hold"] and c["hold"][0] != "decision" else ""
            c["hold"] = ("decision", f"branch {b} is not one-change-one-branch (BR-C1): fold into {pr_branch} "
                                     f"(changes the upstream PR head: your OK per PR), then point integration at it{then}")
    # deps
    for c in changes.values():
        for d in c.pop("_deps_raw", []):
            s = id_to_slug.get(d)
            if s and s != c["slug"] and s not in c["deps"]:
                c["deps"].append(s)
        if c["hold"] and c["hold"][0] == "blocked":
            c["hold"] = ("blocked", "waiting for " + ", ".join(c["deps"]) if c["deps"] else "blocked in v2")
    return changes, batches, notes


def full_sha(short, pins):
    """A 40-char sha for a short one: the pin that starts with it, else `git rev-parse` in the push clone."""
    if not short or len(short) >= 40:
        return short
    for pin in pins.values():
        if pin and pin.startswith(short):
            return pin
    import subprocess
    c = os.path.join(ROOT, "re", "upstream-pr", "or")
    r = subprocess.run(["git", "-C", c, "rev-parse", "-q", "--verify", f"{short}^{{commit}}"], capture_output=True, text=True)
    return r.stdout.strip() or short


def write(changes, batches):
    os.makedirs(changes_dir(), exist_ok=True)
    try:
        pins = integration_branches()
    except Exception:
        pins = {}
    n = 0
    for slug, c in changes.items():
        c["commit"] = full_sha(c.get("commit"), pins)
        if c.get("gated_commit"):
            c["gated_commit"] = full_sha(c["gated_commit"], pins)
        if store.exists(slug):
            continue
        role = store.ROLES[c["track"]]
        dod = c["dod"] or [("x" if c["stage"] not in ("backlog", "ready") else " ", "definition of done (from v2)")]
        body = f"## Change\n{c['text'] or c['title']}\n\n## Definition of done\n" + "\n".join(
            f"- [{'x' if ok in ('x', True) else ' '}] {item}" for ok, item in dod) + "\n"
        ch = store.Change({"slug": slug, "track": c["track"], "title": c["title"][:120], "prio": c["prio"], "role": role,
                           "stage": c["stage"], "hold": None, "deps": c["deps"], "branch": c["branch"], "commit": c["commit"],
                           "local_only": bool(c["local_only"]), "owner": None, "instances": [],
                           "created": c.get("created") or iso()}, body)
        for k in ("pr", "ci", "evidence", "summary", "spec", "network", "upstream_pr", "gated_commit"):
            if c.get(k):
                ch[k] = c[k]
        if ch.stage == "live":
            ch["live_commit"] = ch.get("commit")
        store.add_log(ch, f"migrated from v2 tasks: {', '.join(c['folded'])}")
        if c["hold"]:
            store.set_hold(ch, c["hold"][0], c["hold"][1])
        store.save(ch)
        events.emit("stage", change=slug, **{"from": None, "to": ch.stage}, why="migrated from v2")
        n += 1
    for b in batches:
        if not store.rec_load("batches", b["id"]):
            store.rec_save("batches", b["id"], b)
    return n


def main(apply=False):
    changes, batches, notes = plan()
    for slug, c in sorted(changes.items(), key=lambda kv: (kv[1]["track"], kv[0])):
        hold = f" [{c['hold'][0]}: {c['hold'][1][:60]}]" if c["hold"] else ""
        print(f"{c['track']:8} {c['stage']:9} {slug:42} {str(c['branch'] or '-'):40} {str(c['commit'] or '')[:9]:9}{hold}  <- {', '.join(c['folded'])}")
    print(f"\n{len(changes)} changes, {len(batches)} batches")
    for n in notes:
        print("note:", n)
    if not apply:
        print("\n(dry run; --apply writes harness/changes/)")
        return 0
    n = write(changes, batches)
    print(f"wrote {n} changes")
    return 0
