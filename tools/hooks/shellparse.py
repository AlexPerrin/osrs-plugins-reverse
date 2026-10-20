"""Shell parsing shared by the Bash hooks (deny_publish.py, github_scope.py): split a command line into simple
commands, drop redirections, and resolve where a `git push` goes. Not a full shell parser; the hooks fail closed
where it can't tell (an unparsable segment, a push inside `bash -c`, `$(...)` or a script it can't see).
"""
import os
import re
import shlex
import subprocess

FORK = "alexperrin/openrune-server"
# Upstream OpenRune (the repos the agents never write to). Matches OpenRune/OpenRune-Server, github.com/OpenRune/...
# but not AlexPerrin/OpenRune-Server, and not the bare word "upstream" (file names, branch names, commit messages).
UPSTREAM_NAME = re.compile(r"(?<![A-Za-z0-9_-])OpenRune/OpenRune|github\.com[:/]+OpenRune/", re.I)

REDIR = re.compile(r"^\d*(>>?|<<?|&>>?|>&|<&)(.*)$")


def strip_heredocs(command):
    """Heredoc bodies are data (file contents, scripts fed to cat): drop them, keep the rest of the opening line."""
    return re.sub(r"<<-?\s*(['\"]?)(\w+)\1([^\n]*)\n.*?\n\s*\2\s*(?=\n|$)", r"\3", command, flags=re.S)


def segments(command):
    """Split at ; & && || | newline outside quotes. The & of a redirection (2>&1, &>f, <&3) doesn't split."""
    segs, cur, i, quote = [], [], 0, None
    while i < len(command):
        c = command[i]
        if quote:
            cur.append(c)
            if c == "\\" and quote == '"' and i + 1 < len(command):
                cur.append(command[i + 1])
                i += 1
            elif c == quote:
                quote = None
        elif c in "'\"":
            quote = c
            cur.append(c)
        elif c == "\\" and i + 1 < len(command):
            cur.append(command[i:i + 2])
            i += 1
        elif c == "&" and (cur and cur[-1] in "<>" or command[i + 1:i + 2] == ">"):
            cur.append(c)   # 2>&1, <&3, &>file
        elif c in ";&|\n":
            segs.append("".join(cur))
            cur = []
            if command[i:i + 2] in ("&&", "||"):
                i += 1
        else:
            cur.append(c)
        i += 1
    segs.append("".join(cur))
    return [s.strip().lstrip("({ ").rstrip(")} ").strip() for s in segs if s.strip()]


def opaque_substitutions(seg):
    """`$(...)` and backticks as one opaque word (__SUBST__), so `--force-with-lease=x:$(git -C d rev-parse y)` stays
    one option instead of leaking `-C d` into the outer command. A push *inside* a substitution is then not seen
    as a push of this segment, so the callers treat it as unparsable (deny / ask)."""
    prev = None
    while prev != seg:
        prev = seg
        seg = re.sub(r"\$\([^()]*\)", "__SUBST__", seg)
    return re.sub(r"`[^`]*`", "__SUBST__", seg)


def hidden_writes(command):
    """True when a `$(...)` or backtick substitution contains a git push or a gh call: the hooks can't tell its
    target, so they ask (github_scope) or deny (deny_publish)."""
    subs = re.findall(r"\$\(([^()]*)\)", command) + re.findall(r"`([^`]*)`", command)
    return any(re.search(r"\bgit\b.*\bpush\b|\bgh\b|fork\.sh", x) for x in subs)


def words(seg):
    """shlex words of one simple command without leading VAR=x assignments and redirections; None if unparsable."""
    seg = opaque_substitutions(seg)
    try:
        w = shlex.split(seg)
    except ValueError:
        return None
    while w and re.match(r"^[A-Za-z_][A-Za-z0-9_]*=", w[0]):
        w = w[1:]
    out, skip = [], False
    for t in w:
        if skip:
            skip = False
            continue
        m = REDIR.match(t)
        if m:
            skip = m.group(2) == ""   # `2> file`: the target is the next word
            continue
        out.append(t)
    return out


ASSIGN = re.compile(r"^([A-Za-z_][A-Za-z0-9_]*)=(.*)$", re.S)


def assignments(seg):
    """{name: value} when this simple command only assigns shell variables (`wt=re/x` or `a=1 b=2`), else {}."""
    try:
        w = shlex.split(seg)
    except ValueError:
        return {}
    pairs = [ASSIGN.match(t) for t in w]
    return {m.group(1): m.group(2) for m in pairs} if w and all(pairs) else {}


def substitute(seg, env):
    """Replace $NAME / ${NAME} with values assigned earlier in the same command (quoted); others stay as they are."""
    def rep(m):
        n = m.group(1) or m.group(2)
        return shlex.quote(env[n]) if n in env else m.group(0)
    return re.sub(r"\$\{([A-Za-z_][A-Za-z0-9_]*)\}|\$([A-Za-z_][A-Za-z0-9_]*)", rep, seg)


def resolved_segments(command):
    """The command's simple commands with variables assigned earlier in it substituted (`wt=x && git -C $wt ...`)."""
    env, out = {}, []
    for seg in segments(strip_heredocs(command)):
        seg = substitute(seg, env)
        env.update(assignments(seg))
        out.append(seg)
    return out


def expand(path, cwd):
    return os.path.normpath(os.path.join(cwd, os.path.expandvars(os.path.expanduser(path))))


def cd_target(w, cwd):
    """The new cwd if this simple command is `cd <dir>`, else None."""
    if w and w[0] == "cd" and len(w) == 2:
        return expand(w[1], cwd)
    return None


def norm_repo(url):
    """owner/name, lowercased, from a GitHub URL or owner/name; '' when it isn't one."""
    url = (url or "").strip().removesuffix("/").removesuffix(".git")
    m = re.search(r"github\.com[:/]+([^/\s]+/[^/\s]+)$", url)
    return (m.group(1) if m else url if re.fullmatch(r"[^/\s:]+/[^/\s:]+", url) else url).lower()


VALUE_OPTS = {"-o", "--push-option", "--receive-pack", "--exec", "--repo"}


def parse_push(w, cwd):
    """For `git [-C d] [-c k=v] push [opts] [remote [refspec...]]` (w = words), a dict with dir, remote, refspecs
    and opts; None when w isn't a git push."""
    if not w or os.path.basename(w[0]) != "git":
        return None
    i, path = 1, cwd
    while i < len(w) and w[i].startswith("-"):
        if w[i] == "-C" and i + 1 < len(w):
            path = expand(w[i + 1], path)
            i += 2
        elif w[i] in ("-c", "--git-dir", "--work-tree", "--namespace") and i + 1 < len(w):
            if w[i] != "-c":
                path = None   # can't tell the repo from a bare git dir option: unknown target
            i += 2
        else:
            if w[i].startswith(("--git-dir=", "--work-tree=")):
                path = None
            i += 1
    if i >= len(w) or w[i] != "push":
        return None
    opts, pos, remote, args, j = [], [], None, w[i + 1:], 0
    while j < len(args):
        a = args[j]
        if a == "--":
            pos += args[j + 1:]
            break
        if a.startswith("-"):
            opts.append(a)
            if a in VALUE_OPTS and j + 1 < len(args):
                if a == "--repo":
                    remote = args[j + 1]
                j += 2
                continue
            if a.startswith("--repo="):
                remote = a.split("=", 1)[1]
        else:
            pos.append(a)
        j += 1
    if remote is None and pos:
        remote, pos = pos[0], pos[1:]
    return {"dir": path, "remote": remote, "refspecs": pos, "opts": opts}


# Worktrees created earlier in the same command: {new worktree path: the repo it was added from}. When the hook runs,
# such a folder doesn't exist yet, so its remotes are read from the source repo (a worktree shares them).
WORKTREES = {}


def note_worktree(w, cwd):
    """Record `git [-C repo] worktree add [opts] <path> [<commit-ish>]` so a later push from <path> resolves."""
    if not w or os.path.basename(w[0]) != "git" or "worktree" not in w:
        return
    i = w.index("worktree")
    if i + 1 >= len(w) or w[i + 1] != "add":
        return
    repo = cwd
    if "-C" in w[:i]:
        repo = expand(w[w.index("-C") + 1], cwd)
    args, skip = [], False
    for t in w[i + 2:]:
        if skip:
            skip = False
            continue
        if t in ("-b", "-B", "--reason"):
            skip = True
            continue
        if t.startswith("-"):
            continue
        args.append(t)
    if args:
        WORKTREES[expand(args[0], cwd)] = repo


def source_dir(d):
    """d, or the repo a not-yet-existing worktree d (or a folder inside it) was added from."""
    if d is None or os.path.isdir(d):
        return d
    for wt, repo in WORKTREES.items():
        if d == wt or d.startswith(wt.rstrip("/") + "/"):
            return repo
    return d


def push_url(p, run=subprocess.run):
    """The push URL of a parsed push ('' when it can't be told: no remote, unknown dir, unknown remote)."""
    if not p or not p["remote"] or p["dir"] is None:
        return ""
    if "/" in p["remote"] or ":" in p["remote"]:
        return p["remote"]
    r = run(["git", "-C", source_dir(p["dir"]), "remote", "get-url", "--push", p["remote"]], capture_output=True,
            text=True)
    return r.stdout.strip() if r.returncode == 0 else ""


def forced(p):
    for o in p["opts"]:
        if o.startswith("--force") or o in ("--mirror", "--delete", "--prune"):
            return True
        if re.fullmatch(r"-[A-Za-z]+", o) and re.search(r"[fd]", o):
            return True
    return any(r.startswith("+") or r.startswith(":") for r in p["refspecs"])


def dest(refspec):
    """The destination branch name of a refspec (src:dst, or src alone)."""
    return refspec.lstrip("+").split(":")[-1].removeprefix("refs/heads/")
