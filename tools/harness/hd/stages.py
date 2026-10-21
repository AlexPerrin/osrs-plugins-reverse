"""The lifecycle (PRD sections 2-4): the same stages for every track, one precondition per stage per track, each a
fact `observe` can read. `derive()` gives the stage a change has reached from observations plus the recorded
stage for the parts only the daemon sets (dev, checked), and the first unmet precondition in words.

    backlog -> ready -> dev -> checked -> live -> gated -> published -> merged

Rules encoded here: BR-C2 (a new commit on the branch re-enters at dev), BR-C4 (ready needs every dep live or
later), BR-C7 (local/* ends at live), BR-C8 (upstream main contains the commit = merged), BR-S1 (gate only what is
live: gated needs live at the same commit), BR-S2 (stages are observed, never set by hand).
"""
import re

from .store import STAGES

TERMINAL = {"client": "live", "harness": "live", "server": "merged"}


def terminal(ch):
    if ch.get("local_only"):
        return "live"
    return TERMINAL.get(ch.get("track"), "live")


def deps_met(ch, idx):
    """BR-C4: every dep is live or later; returns (ok, [open deps])."""
    open_ = []
    for d in ch.get("deps") or []:
        dep = idx.get(d)
        if dep is None or not dep.reached("live"):
            open_.append(d)
    return (not open_), open_


RECORD_BRANCH = re.compile(r"^- Branch: `([^`]+)`", re.M)
RECORD_COMMIT = re.compile(r"^- Commit: `([0-9a-f]{7,40})`", re.M)
RECORD_GATE = re.compile(r"^## Upstream gate: \*\*(PASS|FAIL|PENDING)\*\*", re.M)
RECORD_VERDICT = re.compile(r"^## Verdict: \*\*(PASS|FAIL)\*\*", re.M)


def gate_record(records, branch, commit):
    """The QA record (name, verdict) for branch at commit among (name, text) records, or None. The upstream line
    wins; an older record with only a Verdict line was a full run and counts (publish_gate.py does the same)."""
    best = None
    for name, text in records or []:
        mb, mc = RECORD_BRANCH.search(text), RECORD_COMMIT.search(text)
        if not (mb and mc) or mb.group(1) != branch or not commit.startswith(mc.group(1)):
            continue
        m = RECORD_GATE.search(text) or RECORD_VERDICT.search(text)
        if m:
            best = (name, m.group(1))
            if m.group(1) == "PASS":
                return best
    return best


def observed(ch, obs):
    """What the external world says about this change, per stage: {stage: (met: True|False|None, why)}. None means
    the fact could not be read this tick (gh down, no clone); the caller never regresses on None."""
    out = {}
    track, branch, commit = ch.get("track"), ch.get("branch"), ch.get("commit")
    if not (branch and commit):
        return out
    repo = "fork" if track == "server" else "ours"
    tip = obs.branch_tip(repo, branch)
    out["tip"] = (tip, f"{repo}/{branch} at {tip[:9]}" if tip else f"{repo}/{branch} not found")
    # live
    if track == "server":
        lv = obs.live() or {}
        pin = obs.pin(branch)
        integ = lv.get("integration")
        if integ is None:
            out["live"] = (None, "live.json missing: nothing deployed by the daemon yet")
        elif pin != commit:
            out["live"] = (False, f"integration pin is {pin[:9] if pin else 'absent'}, not {commit[:9]} (next train)")
        else:
            c = obs.contains("fork", integ, commit)
            out["live"] = (c, f"live integration {integ[:9]} {'carries' if c else 'lacks'} {commit[:9]}" if c is not None
                           else "could not read the live integration commit")
    else:
        c = obs.contains("ours", "master", commit)
        out["live"] = (c, f"master {'contains' if c else 'does not contain'} {commit[:9]}" if c is not None
                       else "could not read master")
    if track != "server" or ch.get("local_only"):
        return out
    # gated: a PASS record at the commit on the fork's docs branch
    recs = obs.docs_records()
    if recs is None:
        out["gated"] = (None, "could not read the fork's docs branch")
    else:
        rec = gate_record(recs, branch, commit)
        if rec and rec[1] == "PASS":
            out["gated"] = (True, f"upstream gate PASS ({rec[0]})")
        elif rec:
            out["gated"] = (False, f"upstream gate {rec[1]} ({rec[0]})")
        else:
            out["gated"] = (False, f"no upstream gate record for {branch} at {commit[:9]}")
    # published: the OpenRune PR open, not draft, head = commit
    pr = obs.upstream_pr(ch.get("upstream_pr") or branch)
    if pr is None:
        out["published"] = (None, "could not read the upstream PR")
    elif not pr:
        out["published"] = (False, "no upstream PR")
    elif pr.get("merged"):
        out["published"] = (True, f"upstream PR #{pr['number']} merged")
    elif pr.get("state") != "open":
        out["published"] = (False, f"upstream PR #{pr['number']} is {pr.get('state')}")
    elif pr.get("draft"):
        out["published"] = (False, f"upstream PR #{pr['number']} is a draft")
    elif not str(pr.get("head") or "").startswith(commit) and not commit.startswith(str(pr.get("head") or "")):
        out["published"] = (False, f"upstream PR #{pr['number']} head is {str(pr.get('head'))[:9]}, not {commit[:9]}")
    else:
        out["published"] = (True, f"upstream PR #{pr['number']} open for review at {commit[:9]}")
    # merged: upstream main contains the commit
    m = obs.contains("upstream", "main", commit)
    out["merged"] = (m, "upstream main contains the commit" if m else "not merged upstream yet" if m is not None
                     else "could not read upstream main")
    return out


def derive(ch, obs, idx):
    """(stage, waiting_on, moved_commit): the stage the change has reached and the first unmet precondition.
    Below `checked` the recorded stage stands (the daemon set it from the check or a dispatch); from `live` on the
    observations decide, never regressing on a fact that could not be read. `moved_commit` is the branch tip when
    it differs from the recorded commit (BR-C2: the change re-enters at dev)."""
    track = ch.get("track")
    rec = ch.stage
    obs_ = observed(ch, obs)
    tip = obs_.get("tip", (None, ""))[0]
    moved = tip if (tip and ch.get("commit") and tip != ch.get("commit")) else None
    if rec in ("backlog", "ready", "dev"):
        ok, open_ = deps_met(ch, idx)
        if rec == "backlog":
            return "backlog", "no definition of done" if not ch.has_dod() else "backlog", moved
        if not ok:
            return rec, "waiting for " + ", ".join(f"{d} ({idx[d].stage if d in idx else 'missing'})" for d in open_), moved
        if rec == "ready":
            return "ready", "a free slot and lease", moved
        if ch.get("commit"):
            return "dev", "the hand-over check", moved
        return "dev", f"agent {ch.get('owner') or '?'}", moved
    # checked and above: climb while observed true
    stage = "checked"
    waiting = "the next train" if track == "server" else "the merge into master"
    end = terminal(ch)
    for s in ("live", "gated", "published", "merged"):
        if STAGES.index(s) > STAGES.index(end):
            break
        met, why = obs_.get(s, (None, "not observed"))
        if met is None:
            # unknown: keep the recorded stage if it is at least s, else stop here
            if ch.reached(s):
                stage, waiting = s, why
                continue
            waiting = why
            break
        if not met:
            waiting = why
            break
        stage, waiting = s, ""
    labels = {"live": "the upstream gate", "gated": "the user's approval", "published": "the maintainers"}
    if stage == end:
        waiting = ""
    elif stage in labels:
        waiting = labels[stage] + (f" ({waiting})" if waiting else "")
    return stage, waiting, moved


def next_executor(ch):
    """Which agent role a change needs now, if any: dev (ready without a blocking hold, or a failed hold), gate
    (server: live, not local-only, no PASS record yet)."""
    if ch.hold in ("blocked", "decision", "exception"):
        return None
    if ch.stage == "ready" or (ch.hold == "failed" and ch.stage in ("ready", "live", "published")):
        return "dev"
    if ch.stage == "live" and ch.get("track") == "server" and not ch.get("local_only") and not ch.get("gated_commit"):
        return "gate"
    return None
