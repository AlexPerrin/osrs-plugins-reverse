#!/usr/bin/env python3
"""PreToolUse hook (Bash), every session: the upstream publish gate (docs/workflow.md "Gates").

`gh pr create` against upstream (OpenRune/*, or --head AlexPerrin:<branch>) is denied unless a QA record for
<branch> says PASS for the branch's commit on the fork (fork/<branch> in re/upstream-pr/or, else the local
branch). A new commit voids the record. Records (server QA, while they move to the fork): qa/*.md on the
fork's `docs` branch (fork/docs, then the local docs), then openrune/upstream/qa/*.md here. Header lines read:
  - Branch: `content/<slug>`
  - Commit: `<full sha>`
  ## Upstream gate: **PASS**
Older records with only `## Verdict: **PASS**` were full QA runs and count as the upstream gate.
Fork PRs (tools/fork.sh) come before QA by design and aren't gated. Commands run inside scripts
(e.g. openrune/upstream/open-batch-*.sh) aren't visible to this hook; they still go through the ask rule.
"""
import glob
import json
import os
import re
import subprocess
import sys

ROOT = os.environ.get("CLAUDE_PROJECT_DIR") or os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
CLONE = os.path.join(ROOT, "re/upstream-pr/or")


def deny(reason):
    print(json.dumps({"hookSpecificOutput": {
        "hookEventName": "PreToolUse",
        "permissionDecision": "deny",
        "permissionDecisionReason": f"Publish gate: {reason} (docs/workflow.md \"Gates\").",
    }}))
    sys.exit(0)


def git(*args):
    r = subprocess.run(["git", "-C", CLONE, *args], capture_output=True, text=True)
    return r.stdout if r.returncode == 0 else None


def records():
    for ref in ("refs/remotes/fork/docs", "refs/heads/docs"):
        for name in (git("ls-tree", "--name-only", ref, "qa/") or "").split():
            if name.endswith(".md"):
                text = git("show", f"{ref}:{name}")
                if text:
                    yield f"{ref.split('/', 2)[2]}:{name}", text
    for path in sorted(glob.glob(os.path.join(ROOT, "openrune/upstream/qa/*.md"))):
        with open(path) as f:
            yield path, f.read()


def release_gate(cmd):
    """Client release gate: `git tag client-vX.Y [commit]` and `gh release create client-vX.Y` need
    qa/client-X.Y.md with `## Verdict: **PASS**` for the commit being tagged (the tag's, else HEAD)."""
    m = re.search(r"\b(?:git\s+tag|gh\s+release\s+create)\b(?:\s+-\S+)*\s+(client-v([0-9][0-9A-Za-z.\-]*))(?:\s+([0-9a-f]{7,40}|HEAD|master))?", cmd)
    if not m:
        return
    tag, version, target = m.group(1), m.group(2), m.group(3)
    run = lambda *a: (subprocess.run(["git", "-C", ROOT, *a], capture_output=True, text=True).stdout or "").strip()
    sha = run("rev-parse", "-q", "--verify", f"refs/tags/{tag}^{{commit}}") or run("rev-parse", target or "HEAD")
    path = os.path.join(ROOT, "qa", f"client-{version}.md")
    if not os.path.isfile(path):
        deny(f"no release record qa/client-{version}.md; client-qa runs the release gate on {sha[:9]} first")
    text = open(path).read()
    commit = re.search(r"^- Commit: `([0-9a-f]{7,40})`", text, re.M)
    verdict = re.search(r"^## Verdict: \*\*(PASS|FAIL)\*\*", text, re.M)
    if not (commit and verdict and verdict.group(1) == "PASS" and sha.startswith(commit.group(1))):
        deny(f"{tag} would tag {sha[:9]}, but qa/client-{version}.md says "
             f"{verdict.group(1) if verdict else 'no verdict'} at {commit.group(1)[:9] if commit else 'no commit'}")
    sys.exit(0)


def main():
    cmd = json.load(sys.stdin).get("tool_input", {}).get("command", "")
    release_gate(cmd)
    if not re.search(r"\bgh\s+pr\s+create\b", cmd):
        return
    head = re.search(r"--head[=\s]+[\"']?(?:AlexPerrin:)?([A-Za-z0-9._/-]+)", cmd)
    if not ("OpenRune/" in cmd or (head and "AlexPerrin:" in cmd)):
        return  # not an upstream PR
    if not head:
        deny("upstream `gh pr create` needs an explicit --head AlexPerrin:<branch> so its QA record can be checked")
    branch = head.group(1)
    sha = (git("rev-parse", "--verify", "-q", f"refs/remotes/fork/{branch}")
           or git("rev-parse", "--verify", "-q", f"refs/heads/{branch}") or "").strip()
    if not sha:
        deny(f"no branch {branch} on the fork or in re/upstream-pr/or")
    found = []
    for path, text in records():
        if not re.search(rf"^- Branch: `{re.escape(branch)}`", text, re.M):
            continue
        commit = re.search(r"^- Commit: `([0-9a-f]{7,40})`", text, re.M)
        verdict = (re.search(r"^## Upstream gate: \*\*(PASS|FAIL|PENDING)\*\*", text, re.M)
                   or re.search(r"^## Verdict: \*\*(PASS|FAIL)\*\*", text, re.M))
        if commit and verdict and verdict.group(1) == "PASS" and sha.startswith(commit.group(1)):
            return  # gate passed; the normal ask rule still applies
        found.append(f"{path}: upstream gate {verdict.group(1) if verdict else 'missing'} at "
                     f"{commit.group(1)[:9] if commit else 'no commit'}")
    if not found:
        deny(f"no QA record for {branch}; server-qa records the upstream gate PASS for {sha[:9]} first")
    deny(f"{branch} is at {sha[:9]}, but no record has an upstream gate PASS for it ({'; '.join(found)})")


main()
