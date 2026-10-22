"""The hand-over check: the `checked` precondition (PRD section 4), deterministic, on evidence that exists. It
checks the claims a hand-over makes, not the feature. Every item is (ok, text); the change is checked when all
are ok. Rules are those of v2's handover-check.sh (reused because they are right), read through `observe`.

Every track: branch and commit set; definition of done all ticked; evidence dir non-empty (docs-only client
changes excepted).
Client / harness: the branch exists at the commit in this repo; the commit is not on master yet; a repack log with
the policy's 7 `telemetry:` lines and device evidence (a logcat extract or a data-tool dump); `netqa*.md` when
`network: true`. A docs-only change (every file since merge-base(master) is *.md) needs none of the build evidence.
Server: the commit is on the fork at the branch tip; off upstream main; accuracy/<spec>.md on the fork's docs
branch (spec: key, the branch slug, then the change slug); a fork PR recorded (not for local/*); fork CI `success`
at exactly the commit (or `gate:` a local gate log with rc=0)."""
import os
import re

from . import ROOT, policy


def items(ch, obs):
    out = []
    ok = lambda t: out.append((True, t))
    fail = lambda t: out.append((False, t))
    pending = lambda t: out.append((None, t))   # not decidable yet (CI running): the check waits, it does not fail
    track, branch, commit = ch.get("track"), ch.get("branch"), ch.get("commit")
    if not (branch and commit):
        fail("branch or commit missing")
        return out
    ok(f"branch {branch} at {commit[:9]}")
    unticked = ch.dod_unticked()
    if unticked:
        fail(f"definition of done has {len(unticked)} unticked item(s)")
    else:
        ok("definition of done ticked")
    ev = ch.get("evidence") or os.path.join("re", "evidence", ch["slug"])
    files = obs.evidence_files(ev)
    evp = ev if os.path.isabs(ev) else os.path.join(ROOT, ev)
    pol = policy.get().get("evidence", {})
    if track in ("client", "harness"):
        tip = obs.branch_tip("ours", branch)
        if tip is None:
            fail(f"no local branch {branch}")
        elif tip != commit:
            fail(f"branch {branch} is at {tip[:9]}, not {commit[:9]}")
        else:
            ok("branch is at the commit")
        on_master = obs.contains("ours", "master", commit)
        if on_master:
            fail("commit already on master")
        elif on_master is False:
            ok("not yet on master")
        else:
            fail("could not read master")
        mb = obs.merge_base("ours", "master", commit)
        changed = obs.changed_files("ours", mb, commit) if mb else None
        docs_only = bool(changed) and all(f.endswith(".md") for f in changed)
        if docs_only:
            ok(f"docs-only change ({len(changed)} markdown files): no build or device evidence needed")
            return out
        if files:
            ok(f"evidence dir {ev} ({len(files)} files)")
        else:
            fail(f"evidence dir {ev} missing or empty")
        if track == "harness":
            return out
        logs = [f for f in files if re.match(r"repack.*\.log$|.*repack.*\.txt$", f)]
        if logs:
            text = obs.read_file(os.path.join(evp, logs[0])) or ""
            n = len(re.findall(r"^telemetry:", text, re.M))
            want = int(pol.get("client_repack_telemetry_lines", 7))
            (ok if n == want else fail)(f"repack log {logs[0]}: {n} telemetry lines" + ("" if n == want else f", not {want}"))
        else:
            fail(f"no repack log in {ev} (copy the build's repack output there as repack.log)")
        pats = pol.get("client_device_evidence", ["*logcat*.txt"])
        hits = [f for p in pats for f in files if _match(f, p)]
        (ok if hits else fail)("device evidence present: " + ", ".join(hits[:3]) if hits
                               else f"no device evidence ({', '.join(pats)}) in {ev}")
        if ch.get("network"):
            nq = [f for f in files if _match(f, pol.get("client_network_report", "netqa*.md"))]
            (ok if nq else fail)("Network QA report present" if nq else f"network: true but {ev} has no netqa*.md")
        return out
    # server
    if files:
        ok(f"evidence dir {ev} ({len(files)} files)")
    else:
        fail(f"evidence dir {ev} missing or empty")
    tip = obs.branch_tip("fork", branch)
    if tip is None:
        fail(f"fork/{branch} not found (push the branch to the fork)")
    elif tip != commit:
        fail(f"fork/{branch} is at {tip[:9]}, not {commit[:9]}")
    else:
        ok("fork branch is at the commit")
    mb = obs.merge_base("fork", f"refs/remotes/{obs.upstream_remote}/main" if hasattr(obs, "upstream_remote") else "main", commit)
    if mb:
        ok(f"off upstream main (merge base {mb[:9]})")
    else:
        fail("no merge base with upstream main")
    found = None
    tried = []
    for name in (ch.get("spec"), branch.split("/", 1)[-1], ch["slug"]):
        if name and name not in tried:
            tried.append(name)
            if obs.docs_file(f"accuracy/{name}.md") is not None:
                found = name
                break
    if found:
        ok(f"accuracy/{found}.md on the fork's docs branch")
    elif branch.startswith("local/") or ch.get("local_only"):
        ok("local/* branch: no accuracy spec needed (never upstream; the change file says what it is for)")
    else:
        fail(f"no accuracy spec on the fork's docs branch (tried {', '.join(tried)})")
    if branch.startswith("local/") or ch.get("local_only"):
        ok("local/* branch: never upstream, no fork PR needed")
    elif ch.get("pr"):
        ok(f"fork PR {ch['pr']}")
    else:
        pr = obs.fork_pr(branch)
        if pr:
            ok(f"fork PR {pr.get('url') or pr.get('number')} (observed)")
        else:
            fail("no fork PR recorded or found for the branch")
    gate = ch.get("gate")
    if gate:
        text = obs.read_file(gate if os.path.isabs(gate) else os.path.join(ROOT, gate)) or ""
        (ok if "rc=0" in text else fail)(f"local gate log {gate}" + ("" if "rc=0" in text else " missing or not rc=0"))
    else:
        ci = obs.fork_ci(branch, commit)
        if ci == "success":
            ok("fork CI success at the commit")
        elif ci is None:
            pending("fork CI could not be read (gh); retried next tick")
        elif ci == "none":
            fail(f"no CI run at {commit[:9]} on the fork (gh workflow run ci.yml --ref {branch})")
        elif ci == "pending":
            pending("fork CI still running at the commit")
        else:
            fail(f"fork CI at the commit: {ci}")
    return out


def _match(name, pattern):
    import fnmatch
    return fnmatch.fnmatch(name, pattern)


def run(ch, obs):
    """(passed, items); passed is True, False, or None while an item is still pending and none failed."""
    its = items(ch, obs)
    if any(o is False for o, _ in its):
        return False, its
    if any(o is None for o, _ in its):
        return None, its
    return True, its


def render(ch, its):
    lines = [f"check {ch['slug']} ({ch.get('track')}) {ch.get('branch')}@{str(ch.get('commit') or '')[:9]}"]
    for o, t in its:
        lines.append(f"  {'OK  ' if o else 'WAIT' if o is None else 'FAIL'} {t}")
    fails = sum(1 for o, _ in its if o is False)
    lines.append(f"FAIL ({fails})" if fails else "PENDING" if any(o is None for o, _ in its) else "PASS")
    return "\n".join(lines)
