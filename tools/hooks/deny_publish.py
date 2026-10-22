#!/usr/bin/env python3
"""PreToolUse hook (Bash) for the dev and QA agents (.claude/agents/*.md): denies what only the orchestrator
does (docs/workflow.md "Agents"): pushes, GitHub writes, tools/fork.sh, deploys, device grants and progress
writes. The orchestrator's own session doesn't load this hook.

Integration images and deploys (docs/integration-pipeline.md, phase 0) are the orchestrator's too, whatever the
flags: tools/harness/deploy.sh, image.sh, ship.sh and integrate.py (phase 1), `orchestrate.py
deploy|integrate|image`, `docker [image|container] tag|rm|rmi|stop|restart|kill` naming an osrs-openrune image or container, and `docker compose` / `docker-compose`
naming osrs-openrune or the live stack's compose service `openrune` (or openrune/docker-compose.yml, or run from
inside openrune/), except the read-only compose subcommands (ps, logs, config, ls, images, top, version).

`--fork-writes` (server-dev, user 2026-10-19): the dev does its own GitHub work on our fork
(AlexPerrin/OpenRune-Server): pushes whose remote resolves to the fork, tools/fork.sh (fork only by
construction), and gh writes that name `AlexPerrin/OpenRune-Server`. Contributions to OpenRune's own repo stay
denied: a push whose remote URL is not the fork (in re/upstream-pr/or `origin` IS OpenRune; in the agents' clones
`origin` is the fork), a bare `git push` (target unknown), and any write segment naming OpenRune/OpenRune.
github_scope.py still asks the user before a push to a fork branch that backs an open upstream PR.

`--docs-writes` (server-qa, user 2026-10-19): the companion pushes its own records to the fork's `docs` branch
(a non-forced push of one refspec onto `docs`, remote resolving to the fork). Nothing else.

The target of a push is resolved, not guessed from words: the remote after `push`, the directory from `git -C`
and/or the last `cd` before it, else the hook input's `cwd`; then `git remote get-url --push <remote>`.

Reads the hook JSON on stdin; prints a deny decision, or nothing (normal permission flow).
"""
import json
import os
import re
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import shellparse as sp  # noqa: E402

RULES = [
    (r"\bgit\b(\s+-\S+(\s+[^-\s]\S*)?)*\s+push\b", "pushes"),   # git [opts] push, not `git stash push`
    (r"\bgh\s+(pr|issue|release|gist|repo|label)\s+(create|edit|comment|close|reopen|merge|ready|review|delete|fork|upload|sync|transfer|lock|unlock|rename|archive|clone|delete-asset)\b",
     "GitHub writes"),
    (r"\bgh\s+api\b[^|;&]*(-X\s*(POST|PATCH|PUT|DELETE)|--method|\s-[fF]\s|--field|--raw-field|--input|\bgraphql\b)",
     "GitHub writes"),
    (r"\btools/fork\.sh\b|(^|[\s/])fork\.sh\b", "fork pushes and PRs"),
    (r"\bopenrune\.sh\s+deploy\b|--env-file\s+switch\.env\s+up\b", "live server deploys and restarts"),
    (r"\bdevice-lock\.sh\s+(grant|reclaim|revoke)\b", "device grants and lock reclaims"),
    (r"\bprogress\.py\b(?![^|;&]*--dry-run)", "progress writes (use --dry-run)"),
]
PUSH = re.compile(RULES[0][0])

# Orchestrator-only integration steps (checked per simple command; a `bash -c "..."` segment is checked as text).
INTEGRATION = "integration images, deploys and live server containers"
HARNESS_SCRIPT = re.compile(r"\bharness/(?:(?:deploy|image|ship)\.sh|integrate\.py)\b")
ORCH_SUB = re.compile(r"\borchestrate\.py\b[^|;&\n]*?\s(deploy|integrate|image)(\s|$|['\"])")
DOCKER_LIVE = re.compile(r"\bdocker\s+(?:(?:image|container)\s+)?(?:tag|rm|rmi|stop|restart|kill)\b[^;&|\n]*"
                         r"osrs-openrune")
COMPOSE = re.compile(r"\bdocker(?:\s+compose|-compose)\b")
COMPOSE_LIVE = re.compile(r"osrs-openrune|(?<![\w.-])openrune(?![\w.-])")   # the service, -p, or openrune/<file>
COMPOSE_READ = {"ps", "logs", "config", "ls", "images", "top", "version"}
COMPOSE_VALUE_OPTS = {"-f", "--file", "-p", "--project-name", "--env-file", "--profile", "--project-directory",
                      "--ansi", "--progress", "--parallel"}


def compose_subcommand(w):
    """The compose subcommand in words w (`docker compose [opts] <sub>` or `docker-compose [opts] <sub>`), or None."""
    if not w:
        return None
    if os.path.basename(w[0]) == "docker" and len(w) > 1 and w[1] == "compose":
        i = 2
    elif os.path.basename(w[0]) == "docker-compose":
        i = 1
    else:
        return None
    while i < len(w):
        if w[i] in COMPOSE_VALUE_OPTS:
            i += 2
        elif w[i].startswith("-"):
            i += 1
        else:
            return w[i]
    return None


INTERPRETERS = {"python3", "python", "bash", "sh", "env"}


def runs_harness_script(w):
    """The segment runs one of the orchestrator-only harness scripts (as the command, or through an interpreter),
    not merely names one as an argument (`sed -n 1,40p tools/harness/integrate.py` is a read)."""
    if not w:
        return False
    i = 0
    while i < len(w) and (os.path.basename(w[i]) in INTERPRETERS or (i > 0 and w[i].startswith("-"))):
        i += 1
    return i < len(w) and bool(HARNESS_SCRIPT.search(w[i]))


def integration_only(cmd, cwd):
    """True when the command runs an orchestrator-only integration step (see the module doc)."""
    for seg, w, c in walk(cmd, cwd):
        if runs_harness_script(w) or ORCH_SUB.search(seg) or DOCKER_LIVE.search(seg):
            return True
        if w and w[0] in ("./deploy.sh", "./image.sh", "./ship.sh", "./integrate.py", "deploy.sh", "image.sh", "ship.sh") and \
                os.path.normpath(c).endswith(os.path.join("tools", "harness")):
            return True
        if COMPOSE.search(seg):
            sub = compose_subcommand(w)
            if sub in COMPOSE_READ:
                continue
            # A write: denied when it names the live stack, runs from openrune/, or hides inside `bash -c` etc.
            in_openrune = os.path.basename(os.path.normpath(c)) == "openrune"
            if COMPOSE_LIVE.search(seg) or in_openrune or sub is None:
                return True
    return False
FORK_NAME = re.compile(r"AlexPerrin/OpenRune-Server", re.I)


def walk(cmd, cwd):
    """(segment text, words, cwd at that segment) for each simple command, tracking `cd`."""
    out = []
    for seg in sp.resolved_segments(cmd):
        w = sp.words(seg)
        sp.note_worktree(w, cwd)
        out.append((seg, w, cwd))
        nd = sp.cd_target(w, cwd) if w else None
        if nd:
            cwd = nd
    return out


def local_push_clone(url, d):
    """The agent clones' `origin` is the local push clone re/upstream-pr/or (a folder on this machine): a push
    there never leaves the machine, and only the orchestrator pushes from that clone to GitHub."""
    if not url or "://" in url or re.match(r"^[\w.-]+@[\w.-]+:", url):
        return False
    clone = os.environ.get("HOOK_PUSH_CLONE") or os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..",
                                                             "re", "upstream-pr", "or")
    target = url if os.path.isabs(url) else os.path.join(d or ".", url)
    return os.path.realpath(target).rstrip("/") == os.path.realpath(clone).rstrip("/")


def push_problem(seg, w, cwd, docs, run):
    """Why this push segment isn't allowed (None = allowed) under --fork-writes (docs=False) / --docs-writes."""
    p = sp.parse_push(w, cwd) if w else None
    if p is None:
        return "a push the hook can't parse (inside a script, a subshell or a substitution)"
    if sp.UPSTREAM_NAME.search(seg):
        return "it names OpenRune upstream"
    if not p["remote"]:
        return "a bare `git push` (no remote: the target can't be told)"
    url = sp.push_url(p, run)
    if local_push_clone(url, p["dir"]):
        return None if not docs or (len(p["refspecs"]) == 1 and sp.dest(p["refspecs"][0]) == "docs"
                                    and not sp.forced(p)) else "not a single non-forced refspec onto docs"
    if sp.norm_repo(url) != sp.FORK:
        return f"remote {p['remote']} in {p['dir']} is {url or 'unknown'}, not AlexPerrin/OpenRune-Server"
    if docs:
        if sp.forced(p):
            return "a forced or deleting push"
        if len(p["refspecs"]) != 1 or sp.dest(p["refspecs"][0]) != "docs":
            return "not a single refspec onto docs"
        return None
    if "--mirror" in p["opts"]:
        return "a mirror push"
    if any(sp.dest(r) == "main" for r in p["refspecs"]):
        return "a push onto the fork's main (fork.sh sync only)"
    return None


def allowed(what, cmd, cwd, fork_ok, docs_ok, run):
    """Is this rule's match an allowed fork/docs write? (fork_ok: --fork-writes, docs_ok: --docs-writes)"""
    segs = walk(cmd, cwd)
    if what == "pushes":
        if not (fork_ok or docs_ok):
            return False
        hits = [(s, w, c) for s, w, c in segs if PUSH.search(s)]
        if not hits:
            return False   # the push is somewhere the parser didn't split out (heredoc fed to a shell, ...)
        return all(push_problem(s, w, c, not fork_ok, run) is None for s, w, c in hits)
    if what == "fork pushes and PRs":
        hits = [s for s, w, c in segs if re.search(RULES[3][0], s)]
        return fork_ok and bool(hits) and not any(sp.UPSTREAM_NAME.search(s) for s in hits)
    if what == "GitHub writes":
        hits = [s for s, w, c in segs if re.search(r"\bgh\b", s)]
        return fork_ok and bool(hits) and all(FORK_NAME.search(s) and not sp.UPSTREAM_NAME.search(s) for s in hits)
    return False


def decide(data, argv, run=None):
    """The deny reason for this hook input, or None."""
    import subprocess
    run = run or subprocess.run
    fork_ok, docs_ok = "--fork-writes" in argv, "--docs-writes" in argv
    cmd = data.get("tool_input", {}).get("command", "")
    cwd = data.get("cwd") or os.getcwd()
    if integration_only(cmd, cwd):
        return (f"Only the orchestrator does {INTEGRATION} (docs/integration-pipeline.md). "
                "Send the request to the orchestrator instead; don't work around this.")
    for pattern, what in RULES:
        if re.search(pattern, cmd) and not allowed(what, cmd, cwd, fork_ok, docs_ok, run):
            detail = ""
            if what == "pushes" and (fork_ok or docs_ok):
                probs = [push_problem(s, w, c, not fork_ok, run) for s, w, c in walk(cmd, cwd) if PUSH.search(s)]
                detail = " (" + "; ".join(p for p in probs if p) + ")" if any(probs) else ""
            return (f"Only the orchestrator does {what}{detail} (docs/workflow.md). "
                    "Send the request to the orchestrator instead; don't work around this.")
    return None


def log_denial(data, reason):
    """BR-P3: every denial is an event (fail-safe: a missing hd never blocks the hook)."""
    try:
        if os.environ.get("HARNESS_DIR") and not os.environ.get("HD_HARNESS_DIR"):
            os.environ["HD_HARNESS_DIR"] = os.environ["HARNESS_DIR"]   # the v2 tests' temp store: no real event
        root = os.environ.get("CLAUDE_PROJECT_DIR") or os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
        sys.path.insert(0, os.path.join(root, "tools", "harness"))
        from hd import ROOT as main_root
        sys.path.insert(0, os.path.join(main_root, "tools", "harness"))
        from hd import events
        events.emit("denial", source="deny_publish", rule=reason[:120], agent=os.environ.get("HD_AGENT"),
                    command=str(data.get("tool_input", {}).get("command", ""))[:200])
    except Exception:
        pass


def main():
    data = json.load(sys.stdin)
    reason = decide(data, sys.argv[1:])
    if reason:
        log_denial(data, reason)
        print(json.dumps({"hookSpecificOutput": {
            "hookEventName": "PreToolUse",
            "permissionDecision": "deny",
            "permissionDecisionReason": reason,
        }}))


if __name__ == "__main__":
    main()
