"""Approvals (BR-A1..A8): the user's yes or no on one outward action. Record `harness/state/approvals/<id>.json`:
  {id, kind: upstream|release|decision, change, batch, action: open-pr|update-pr|ready-pr|comment|tag-release,
   commands: [[argv]...], text: {title, body, comment}, record (the gate record name), diff: "base..head",
   caveats: [..], created, state: pending|approved|denied|done|stale|failed, note, result, decided, executed}
Kinds: `upstream` (open, update, mark ready, comment on an OpenRune PR; a push to a branch backing an open PR),
`release` (tag + GitHub release with the APK), `decision` (a question with options from an agent or the daemon).

  ensure(ch, obs)        for a gated server change: the one approval its state needs (open a PR when none; mark
                         ready when it is a draft at the commit; update = comment + push when the PR head is
                         behind the commit), created only when gated at that commit (BR-A2), with the exact
                         commands and the full text from the fork's docs branch pr-texts/<slug>.title and
                         pr-texts/<slug>.upstream.body (BR-A3). Returns the record or None. Marks an existing
                         pending approval `stale` when GitHub already shows the action done (BR-A5).
  ensure_release(batch)  the release approval for a gated release batch
  decision(ch, question, options, by)   a decision card; puts the change on a `decision` hold
  pending() / all()      records
  approve(aid, note) / deny(aid, note)   the dashboard's only writes; events `approval approved|denied`
  execute(obs)           BR-A4: runs every approved approval's commands once, records the result and the link,
                         marks `done` (or `failed` with the output); a denied one puts its change on a `decision`
                         hold with the note. Returns the records it touched. HD_GH overrides gh (tests).
  render(a)              the card as text (the dashboard, the digest)
Never edits an existing upstream PR description (BR-A8): updates are comments, unless the approval text says the
description is replaced and the user approved that text.
Files the commands read (the PR body, the comment, the release notes) are written next to the record as
`<id>.<part>.md`; HD_GIT overrides git (tests).
"""
import os
import re
import subprocess

from . import ROOT, events, policy, stages, state_dir, store
from .util import iso, local, read_json, short

APPROVALS = "approvals"
PR_URL = re.compile(r"/pull/(\d+)")


def _dir():
    return os.path.join(state_dir(), APPROVALS)


def _text_file(aid, part, text):
    p = os.path.join(_dir(), f"{aid}.{part}.md")
    os.makedirs(_dir(), exist_ok=True)
    with open(p, "w") as f:
        f.write(text.rstrip("\n") + "\n")
    return p


def _pr_slug(ch):
    """The docs-branch name of the change's PR text: the branch after its prefix (BR-C1)."""
    b = ch.get("branch") or ""
    return b.split("/", 1)[1] if "/" in b else ch["slug"]


def _caveats(body):
    out, inside = [], False
    for line in (body or "").splitlines():
        if line.startswith("## "):
            inside = line.strip().lower() == "## caveats"
        elif inside and line.startswith("- "):
            out.append(line[2:].strip())
    return out


def _head_at(pr, commit):
    head = str(pr.get("head") or "")
    return bool(head) and (head.startswith(commit) or commit.startswith(head))


def _action(pr, commit):
    """What a gated change needs from its upstream PR state, or None (nothing: done, closed, or unreadable)."""
    if pr is None:
        return None
    if not pr:
        return "open-pr"
    if pr.get("merged") or pr.get("state") != "open":
        return None
    if _head_at(pr, commit):
        return "ready-pr" if pr.get("draft") else None
    return "update-pr"


def _stale(ch, keep=None):
    """BR-A5: pending approvals of a change that GitHub has overtaken."""
    out = []
    for a in pending():
        if a.get("change") == ch["slug"] and a.get("id") != keep:
            a["state"] = "stale"
            store.rec_save(APPROVALS, a["id"], a)
            events.emit("approval", action="stale", approval=a["id"], approval_kind=a.get("kind"), change=ch["slug"])
            out.append(a)
    return out


def _new(aid, rec):
    rec = dict(rec, id=aid, created=iso(), state="pending", note=rec.get("note"), result=None, decided=None,
               executed=None)
    store.rec_save(APPROVALS, aid, rec)
    events.emit("approval", action="created", approval=aid, approval_kind=rec["kind"], change=rec.get("change"),
                batch=rec.get("batch"), what=rec.get("action"))
    return rec


def ensure(ch, obs):
    if ch.stage != "gated" or not (ch.get("branch") and ch.get("commit")):
        return None
    branch, commit = ch["branch"], ch["commit"]
    pr = obs.upstream_pr(ch.get("upstream_pr") or branch)
    action = _action(pr, commit)
    if action is None:
        if pr:
            _stale(ch)
        return None
    aid = f"{ch['slug']}-{action}-{short(commit, 7)}"
    have = store.rec_load(APPROVALS, aid)
    if have:   # pending: idempotent; decided or failed: asked once, never re-asked
        return have if have.get("state") == "pending" else None
    _stale(ch, keep=aid)
    rp = policy.repos()
    upstream, clone = rp["upstream"], os.path.join(ROOT, rp.get("fork_clone", "re/upstream-pr/or"))
    slug = _pr_slug(ch)
    title = obs.docs_file(f"pr-texts/{slug}.title")
    body = obs.docs_file(f"pr-texts/{slug}.upstream.body")
    text, commands, note = {"title": (title or "").strip(), "body": body or ""}, [], None
    if action == "open-pr":
        if title and body:
            commands = [["gh", "pr", "create", "-R", upstream, "--base", "main", "--head", f"AlexPerrin:{branch}",
                         "--title", text["title"], "--body-file", _text_file(aid, "body", body)]]
        else:
            note = f"PR text missing on docs: pr-texts/{slug}.*"
    elif action == "ready-pr":
        commands = [["gh", "pr", "ready", str(pr["number"]), "-R", upstream]]
    else:
        comment = obs.docs_file(f"pr-texts/{slug}.update.md") or f"Updated to {commit}: {ch.get('summary') or ''}".rstrip()
        text["comment"] = comment
        commands = [["git", "-C", clone, "push", rp.get("fork_remote", "fork"), f"{commit}:refs/heads/{branch}"],
                    ["gh", "pr", "comment", str(pr["number"]), "-R", upstream, "--body-file",
                     _text_file(aid, "comment", comment)]]
    mb = obs.merge_base("fork", f"refs/remotes/{rp.get('upstream_remote', 'origin')}/main", commit)
    rec = stages.gate_record(obs.docs_records(), branch, commit)
    return _new(aid, {"kind": "upstream", "change": ch["slug"], "batch": None, "action": action, "commands": commands,
                      "text": text, "record": rec[0] if rec else None, "diff": f"{mb}..{commit}" if mb else None,
                      "caveats": _caveats(body), "commit": commit, "pr": (pr or {}).get("number"), "note": note})


def ensure_release(batch):
    req = read_json(os.path.join(state_dir(), "release.request"), {}) or {}
    tag, note = req.get("tag"), None
    if not tag:
        tag, note = "v0.x.y", "tag placeholder: write harness/state/release.request {\"tag\": \"vX.Y.Z\"} and re-tick"
    commit = batch.get("commit") or ""
    aid = f"{batch['id']}-tag-release-{tag}"
    have = store.rec_load(APPROVALS, aid)
    if have and have.get("state") == "pending":
        return have
    includes = batch.get("includes") or []
    notes = f"# {tag}\n\n" + "\n".join(f"- {s}" for s in includes) + "\n"
    cmd = ["gh", "release", "create", tag, "--repo", policy.repos()["ours"], "--title", tag, "--notes-file",
           _text_file(aid, "notes", notes)]
    apk = batch.get("apk")
    if apk and os.path.isfile(apk):
        cmd.append(apk)
    return _new(aid, {"kind": "release", "change": None, "batch": batch["id"], "action": "tag-release",
                      "commands": [["git", "tag", "-a", tag, commit, "-m", f"{tag}: {len(includes)} changes"], cmd],
                      "text": {"title": tag, "body": notes}, "record": batch.get("record"), "diff": None,
                      "caveats": [], "commit": commit, "note": note})


def decision(ch, question, options, by="harnessd"):
    aid = f"{ch['slug']}-decision-{iso().replace(':', '').replace('-', '')}"
    store.set_hold(ch, "decision", question)
    store.save(ch)
    return _new(aid, {"kind": "decision", "change": ch["slug"], "batch": None, "action": "decision", "commands": [],
                      "text": {"question": question, "options": list(options or [])}, "by": by, "record": None,
                      "diff": None, "caveats": []})


def pending():
    return [a for a in store.rec_all(APPROVALS) if a.get("state") == "pending"]


def all_():
    return store.rec_all(APPROVALS)


def _decide(aid, state, note):
    a = store.rec_load(APPROVALS, aid)
    if not a:
        raise KeyError(aid)
    if a.get("state") != "pending":
        raise ValueError(f"{aid} is {a.get('state')}, not pending")
    a.update(state=state, note=note or a.get("note"), decided=iso())
    store.rec_save(APPROVALS, aid, a)
    events.emit("approval", action=state, approval=aid, approval_kind=a.get("kind"), change=a.get("change"), note=note)
    return a


def approve(aid, note=""):
    return _decide(aid, "approved", note)


def deny(aid, note=""):
    return _decide(aid, "denied", note)


def _argv(cmd):
    swap = {"gh": os.environ.get("HD_GH"), "git": os.environ.get("HD_GIT")}
    head = swap.get(cmd[0]) or cmd[0]
    return [head] + list(cmd[1:])


def _run_commands(a):
    """(ok, output, last stdout line)."""
    out, last = [], ""
    for cmd in a.get("commands") or []:
        try:
            r = subprocess.run(_argv(cmd), capture_output=True, text=True, timeout=300)
        except (OSError, subprocess.TimeoutExpired) as e:
            out.append(f"$ {' '.join(cmd)}\n{e}")
            return False, "\n".join(out), last
        out.append(f"$ {' '.join(cmd)}\n{r.stdout}{r.stderr}".rstrip())
        if r.returncode != 0:
            return False, "\n".join(out), last
        lines = r.stdout.strip().splitlines()
        last = lines[-1] if lines else last
    return True, "\n".join(out), last


def _overtaken(a, obs):
    """BR-A5 at execution time: the upstream action is already done."""
    if a.get("kind") != "upstream" or not store.exists(a.get("change") or ""):
        return False
    ch = store.load(a["change"])
    pr = obs.upstream_pr(ch.get("upstream_pr") or ch.get("branch"))
    return pr is not None and _action(pr, a.get("commit") or ch.get("commit") or "") != a.get("action")


def _done(a, last):
    a.update(state="done", result=last, executed=iso())
    store.rec_save(APPROVALS, a["id"], a)
    if a.get("change") and store.exists(a["change"]):
        ch = store.load(a["change"])
        m = PR_URL.search(last or "")
        if a.get("action") == "open-pr" and m:
            ch["upstream_pr"] = int(m.group(1))
        store.add_log(ch, f"approval {a['id']} executed ({a.get('action')}): {last or 'ok'}")
        store.save(ch)
    events.emit("approval", action="done", approval=a["id"], approval_kind=a.get("kind"), change=a.get("change"), result=last)


def execute(obs):
    touched = []
    for a in all_():
        if a.get("state") == "approved":
            if _overtaken(a, obs):
                a.update(state="stale", executed=iso())
                store.rec_save(APPROVALS, a["id"], a)
                events.emit("approval", action="stale", approval=a["id"], approval_kind=a.get("kind"), change=a.get("change"))
            else:
                ok, output, last = _run_commands(a)
                if ok:
                    _done(a, last)
                else:
                    a.update(state="failed", result=output[-2000:], executed=iso())
                    store.rec_save(APPROVALS, a["id"], a)
                    events.emit("approval", action="failed", approval=a["id"], approval_kind=a.get("kind"),
                                change=a.get("change"), result=output[-500:])
            touched.append(a)
        elif a.get("state") == "denied" and not a.get("executed"):
            if a.get("change") and store.exists(a["change"]):
                ch = store.load(a["change"])
                store.set_hold(ch, "decision", f"approval {a['id']} denied: {a.get('note') or 'no note'}")
                store.save(ch)
            a["executed"] = iso()
            store.rec_save(APPROVALS, a["id"], a)
            touched.append(a)
    return touched


def render(a):
    t = a.get("text") or {}
    lines = [f"[{a.get('state')}] {a.get('id')}: {a.get('kind')} {a.get('action')}"
             + (f" for change {a['change']}" if a.get("change") else "") + (f" batch {a['batch']}" if a.get("batch") else ""),
             f"  created {local(a.get('created'))}" + (f", decided {local(a['decided'])}" if a.get("decided") else "")]
    if a.get("note"):
        lines.append(f"  note: {a['note']}")
    for cmd in a.get("commands") or []:
        lines.append("  $ " + " ".join(_quote(w) for w in cmd))
    if not a.get("commands") and a.get("kind") != "decision":
        lines.append("  (no command: nothing can run until the note above is fixed)")
    if a.get("record"):
        lines.append(f"  gate record: {a['record']}")
    if a.get("diff"):
        lines.append(f"  diff: {a['diff']}")
    if a.get("caveats"):
        lines.append("  caveats:")
        lines += [f"    - {c}" for c in a["caveats"]]
    if t.get("question"):
        lines.append(f"  question: {t['question']}")
        lines += [f"    {i + 1}. {o}" for i, o in enumerate(t.get("options") or [])]
    if t.get("title"):
        lines.append(f"  title: {t['title']}")
    for part in ("body", "comment"):
        if t.get(part):
            lines.append(f"  {part}:")
            lines += ["    " + l for l in t[part].rstrip().splitlines()]
    if a.get("result"):
        lines.append(f"  result: {a['result'].strip().splitlines()[-1] if a['result'].strip() else ''}")
    return "\n".join(lines)


def _quote(w):
    return w if re.fullmatch(r"[A-Za-z0-9_@%+=:,./-]+", w) else "'" + w.replace("'", "'\\''") + "'"
