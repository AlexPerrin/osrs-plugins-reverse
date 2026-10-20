#!/usr/bin/env python3
"""PreToolUse hook (Bash): who a GitHub write goes to decides whether it prompts (user, 2026-10-19).

- Our repos (AlexPerrin/osrs-plugins-reverse, AlexPerrin/OpenRune-Server): approved without a prompt.
- Upstream (OpenRune/*) and anything whose target can't be told: the user is asked.
- A push or branch deletion on the fork whose branch has an open upstream PR changes that PR: asked.

Writes recognised: `gh pr|issue|release|repo fork|gist` write subcommands, `gh api` with a write method or
fields, `git push`, `tools/fork.sh`, `tools/release-dev.sh`, `openrune/upstream/open-batch-*.sh`.
"allow" is returned only when every segment of the command is a recognised write to our repos or a plain
read-only helper (cd, echo, git fetch/log/...), so approval never extends to an unrelated command.
Deny rules and the other gate hooks (publish_gate.py, deny_publish.py) still apply; deny wins.
"""
import json
import os
import re
import subprocess
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import shellparse as sp  # noqa: E402

ROOT = os.environ.get("CLAUDE_PROJECT_DIR") or os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
OURS = {"alexperrin/osrs-plugins-reverse", "alexperrin/openrune-server"}
FORK = "AlexPerrin/OpenRune-Server"
UPSTREAM = "OpenRune/OpenRune-Server"
GH_WRITES = {
    "pr": {"create", "edit", "comment", "review", "close", "reopen", "merge", "ready"},
    "issue": {"create", "comment", "edit", "close", "reopen", "delete", "transfer", "lock", "unlock"},
    "release": {"create", "edit", "delete", "upload", "delete-asset"},
    "repo": {"fork", "create", "delete", "edit", "rename", "archive", "sync"},
    "gist": {"create", "edit", "delete", "clone"},
    "label": {"create", "edit", "delete", "clone"},
}
READ_ONLY = {"cd", "echo", "printf", "true", "tail", "head", "grep", "wc", "cat", "sort", "cut", "tr"}
GIT_READ = {"fetch", "log", "status", "rev-parse", "show", "ls-remote", "diff", "branch", "remote", "merge-base", "cherry"}


def out(decision, reason):
    print(json.dumps({"hookSpecificOutput": {"hookEventName": "PreToolUse", "permissionDecision": decision,
                                             "permissionDecisionReason": reason}}))
    sys.exit(0)


def norm(repo):
    repo = repo.strip().removesuffix(".git")
    m = re.search(r"github\.com[:/]+([^/\s]+/[^/\s]+)$", repo)
    return (m.group(1) if m else repo).lower()


def origin_of(path):
    r = subprocess.run(["git", "-C", path, "remote", "get-url", "origin"], capture_output=True, text=True)
    return norm(r.stdout) if r.returncode == 0 else None


def has_upstream_pr(branch):
    r = subprocess.run(["gh", "pr", "list", "-R", UPSTREAM, "--head", branch, "--state", "open", "--json", "number",
                        "--jq", "length"], capture_output=True, text=True, timeout=20)
    return r.returncode != 0 or r.stdout.strip() not in ("", "0")   # unknown counts as yes


def classify(seg, cwd):
    """('ours'|'upstream'|'unknown'|'read'|None, detail): None = not a GitHub write and not a known read."""
    w = sp.words(seg)
    if w is None:
        # A fragment of a quoted string or script split at a | or ;: no opinion unless it names upstream.
        if re.search(r"OpenRune/OpenRune", seg) and re.search(r"\bgh\b|\bpush\b", seg):
            return "unknown", "unparsable command naming upstream"
        return None, ""
    if not w:
        return "read", ""
    cmd = os.path.basename(w[0])
    if cmd in READ_ONLY:
        return "read", ""
    if cmd in ("gh", "git") and any(t in ("--help", "-h") for t in w[1:]) or w[:2] == ["gh", "help"]:
        return "read", ""   # help text only, nothing is sent
    if cmd == "release-dev.sh" or w[0].endswith("tools/release-dev.sh"):
        return "ours", "dev release of osrs-plugins-reverse"
    if re.search(r"open-batch-[^/]*\.sh$", w[0]):
        return "upstream", "opens upstream PRs"
    if w[0].endswith("fork.sh"):
        if len(w) > 2 and w[1] == "push" and has_upstream_pr(w[2]):
            return "upstream", f"fork branch {w[2]} has an open upstream PR"
        return "ours", "fork.sh (fork only)"
    if cmd == "gh":
        args = w[1:]
        repo = None
        for i, a in enumerate(args):
            if a in ("-R", "--repo") and i + 1 < len(args):
                repo = norm(args[i + 1])
            elif a.startswith("--repo="):
                repo = norm(a.split("=", 1)[1])
        if not args:
            return None, ""
        if args[0] == "api":
            method = None
            for i, a in enumerate(args):
                if a in ("-X", "--method") and i + 1 < len(args):
                    method = args[i + 1].upper()
                elif a.startswith(("-X", "--method=")) and len(a) > 2:
                    method = a.split("=", 1)[-1].removeprefix("-X").upper()
            fields = any(a in ("-f", "-F", "--field", "--raw-field", "--input")
                         or a.startswith(("--field=", "--raw-field=", "--input=")) for a in args)
            write = "graphql" in args or (method not in (None, "GET")) or (fields and method != "GET")
            if not write:
                return None, ""
            path = next((a for a in args[1:] if not a.startswith("-") and "/" in a), "")
            m = re.match(r"/?repos/([^/]+/[^/]+)", path)
            if m:
                repo = norm(m.group(1))
            elif "graphql" in args:
                return "unknown", "GraphQL mutation"
        elif args[0] in GH_WRITES and len(args) > 1 and args[1] in GH_WRITES[args[0]]:
            if args[0] == "gist":
                return "unknown", "gist (public on the account)"
            if args[0] == "repo" and args[1] in ("fork", "create", "delete", "rename", "archive"):
                return "unknown", f"gh repo {args[1]}"
            if args[0] == "repo" and args[1] == "sync" and len(args) > 2 and not args[2].startswith("-"):
                repo = norm(args[2])
        else:
            return None, ""
        repo = repo or origin_of(cwd)
        if repo in OURS:
            return "ours", repo
        if repo and repo.startswith("openrune/"):
            return "upstream", repo
        return "unknown", f"target repo {repo or 'not known'}"
    if cmd == "git":
        p = sp.parse_push(w, cwd)
        if p is None:
            args = w[1:]
            while args and args[0] in ("-C", "-c"):
                args = args[2:]
            return ("read", "") if args and args[0] in GIT_READ else (None, "")
        if not p["remote"]:
            return "unknown", "bare git push (no remote named)"
        url = sp.push_url(p)
        repo = norm(url) if url else None
        if repo == FORK.lower():
            for spec in p["refspecs"]:
                dest = sp.dest(spec)
                if dest and not dest.startswith("refs/tags/") and has_upstream_pr(dest):
                    return "upstream", f"fork branch {dest} has an open upstream PR"
        if repo in OURS:
            return "ours", repo
        if repo and repo.startswith("openrune/"):
            return "upstream", repo
        return "unknown", f"push to {url or p['remote']}"
    return None, ""


def main():
    data = json.load(sys.stdin)
    if data.get("tool_name") != "Bash":
        return
    command = data.get("tool_input", {}).get("command", "")
    if not re.search(r"\bgh\b|\bgit\b[^|;&]*\bpush\b|fork\.sh|release-dev\.sh|open-batch-", command):
        return
    cwd = data.get("cwd") or ROOT
    # Heredoc bodies are data (scripts, file contents), not commands: drop them before splitting.
    command = sp.strip_heredocs(command)
    kinds, asks = [], []
    if sp.hidden_writes(command):
        asks.append("a push or GitHub call inside a command substitution")
    for seg in sp.resolved_segments(command):
        nd = sp.cd_target(sp.words(seg), cwd)
        if nd:
            cwd = nd
        kind, detail = classify(seg, cwd)
        kinds.append(kind)
        if kind in ("upstream", "unknown"):
            asks.append(detail)
    if asks:
        out("ask", "Upstream or unknown GitHub target: " + "; ".join(asks))
    if "ours" in kinds and all(k in ("ours", "read") for k in kinds):
        out("allow", "GitHub write to our own repos")


if __name__ == "__main__":
    main()
