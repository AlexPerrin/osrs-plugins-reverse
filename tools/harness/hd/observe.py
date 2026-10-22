"""External truth (BR-H1), read fresh each tick and memoised for the tick: git refs in the two clones, fork CI, PR
state on the fork and upstream, docker images and containers, the live server, processes. Everything the stage
preconditions and the checks need goes through one `Observer`, so a test replaces it with `FakeObserver`.

Repos: `ours` = this checkout (master); `fork` = the push clone's `fork` remote (AlexPerrin/OpenRune-Server);
`upstream` = the push clone's `origin` remote (OpenRune/OpenRune-Server). `fetch()` refreshes the remotes once per
tick; HD_NO_FETCH=1 skips network (tests, offline)."""
import glob
import json
import os
import re
import subprocess

from . import ROOT, store
from . import policy
from .util import pid_alive


def _run(args, cwd=None, timeout=60):
    try:
        return subprocess.run(args, cwd=cwd, capture_output=True, text=True, timeout=timeout)
    except (OSError, subprocess.TimeoutExpired):
        return None


class Observer:
    def __init__(self, ours=None, clone=None, gh=None):
        rp = policy.repos()
        self.ours = ours or ROOT
        self.clone = clone or os.path.join(ROOT, rp.get("fork_clone", "re/upstream-pr/or"))
        self.fork_remote = rp.get("fork_remote", "fork")
        self.upstream_remote = rp.get("upstream_remote", "origin")
        self.fork_repo = rp.get("fork", "AlexPerrin/OpenRune-Server")
        self.upstream_repo = rp.get("upstream", "OpenRune/OpenRune-Server")
        self.docs_branch = rp.get("docs_branch", "docs")
        self.gh = gh or "gh"
        self._memo = {}
        self._fetched = False

    def reset(self):
        self._memo.clear()
        self._fetched = False

    def _m(self, key, fn):
        if key not in self._memo:
            self._memo[key] = fn()
        return self._memo[key]

    # --- git --------------------------------------------------------------------------------------------------------

    def fetch(self):
        if self._fetched or os.environ.get("HD_NO_FETCH"):
            return
        self._fetched = True
        if os.path.isdir(self.clone):
            _run(["git", "-C", self.clone, "fetch", "-q", "--prune", self.fork_remote], timeout=120)
            _run(["git", "-C", self.clone, "fetch", "-q", self.upstream_remote, "main"], timeout=120)

    def _repo(self, repo):
        return self.ours if repo == "ours" else self.clone

    def _ref(self, repo, name):
        if repo == "ours":
            return f"refs/heads/{name}"
        if repo == "fork":
            return f"refs/remotes/{self.fork_remote}/{name}"
        return f"refs/remotes/{self.upstream_remote}/{name}"

    def rev(self, repo, ref):
        def f():
            r = _run(["git", "-C", self._repo(repo), "rev-parse", "-q", "--verify", f"{ref}^{{commit}}"])
            return r.stdout.strip() if r and r.returncode == 0 else None
        return self._m(("rev", repo, ref), f)

    def branch_tip(self, repo, branch):
        self.fetch()
        return self.rev(repo, self._ref(repo, branch))

    def upstream_main(self):
        self.fetch()
        return self.rev("upstream", self._ref("upstream", "main"))

    def commit_exists(self, repo, sha):
        return self.rev(repo, sha) is not None

    def contains(self, repo, ref, sha):
        """ref (a branch name for ours/fork/upstream, or a sha) contains sha; None when either is unknown."""
        self.fetch()
        target = ref if re.fullmatch(r"[0-9a-f]{7,40}", ref or "") else self._ref(repo, ref)

        def f():
            d = self._repo(repo)
            if self.rev(repo, target) is None or self.rev(repo, sha) is None:
                return None
            r = _run(["git", "-C", d, "merge-base", "--is-ancestor", sha, target])
            return None if r is None else r.returncode == 0
        return self._m(("contains", repo, target, sha), f)

    def merge_base(self, repo, a, b):
        def f():
            r = _run(["git", "-C", self._repo(repo), "merge-base", a, b])
            return r.stdout.strip() if r and r.returncode == 0 else None
        return self._m(("mb", repo, a, b), f)

    def count_commits(self, repo, base, sha):
        r = _run(["git", "-C", self._repo(repo), "rev-list", "--count", f"{base}..{sha}"])
        return int(r.stdout.strip()) if r and r.returncode == 0 and r.stdout.strip().isdigit() else None

    def changed_files(self, repo, base, sha):
        r = _run(["git", "-C", self._repo(repo), "diff", "--name-only", base, sha])
        return r.stdout.split() if r and r.returncode == 0 else None

    def show(self, repo, ref, path_):
        def f():
            r = _run(["git", "-C", self._repo(repo), "show", f"{ref}:{path_}"])
            return r.stdout if r and r.returncode == 0 else None
        return self._m(("show", repo, ref, path_), f)

    def docs_file(self, path_):
        """A file on the fork's docs branch (remote first, then a local docs branch)."""
        self.fetch()
        for ref in (self._ref("fork", self.docs_branch), f"refs/heads/{self.docs_branch}"):
            t = self.show("fork", ref, path_)
            if t is not None:
                return t
        return None

    def docs_records(self):
        """[(name, text)] for every qa/*.md on the fork's docs branch; None when the branch can't be read."""
        def f():
            ref = self._ref("fork", self.docs_branch)
            r = _run(["git", "-C", self.clone, "ls-tree", "--name-only", ref, "qa/"])
            if not r or r.returncode != 0:
                return None
            out = []
            for name in r.stdout.split():
                if name.endswith(".md"):
                    t = self.show("fork", ref, name)
                    if t:
                        out.append((name, t))
            return out
        self.fetch()
        return self._m(("docs_records",), f)

    def pin(self, branch):
        """The integration pin of a branch (integration.lock on the fork's local/integration-tools)."""
        self.fetch()
        text = self.show("fork", self._ref("fork", "local/integration-tools"), "integration.lock") or ""
        for line in text.splitlines():
            w = line.split()
            if len(w) >= 2 and w[0] == branch:
                return w[1]
        return None

    def integration_list(self):
        self.fetch()
        text = self.show("fork", self._ref("fork", "local/integration-tools"), "integration.txt") or ""
        return [l.strip() for l in text.splitlines() if l.strip() and not l.strip().startswith("#")]

    # --- github -----------------------------------------------------------------------------------------------------

    def _gh_json(self, args, timeout=40):
        r = _run([self.gh, *args], timeout=timeout)
        if not r or r.returncode != 0:
            return None
        try:
            return json.loads(r.stdout or "null")
        except ValueError:
            return None

    def fork_ci(self, branch, sha):
        """'success' | 'failure' | 'pending' | 'none' (no run at the sha) | None (gh failed)."""
        def f():
            runs = self._gh_json(["run", "list", "-R", self.fork_repo, "--branch", branch, "--limit", "20", "--json",
                                  "headSha,conclusion,status,workflowName,url"])
            if runs is None:
                return None
            at = [r for r in runs if r.get("headSha") == sha and r.get("workflowName") == "CI"]
            if not at:
                return "none"
            c = at[0].get("conclusion")
            if c == "success":
                return "success"
            if c in ("failure", "cancelled", "timed_out", "startup_failure"):
                return "failure"
            return "pending"
        return self._m(("ci", branch, sha), f)

    def fork_pr(self, branch):
        def f():
            prs = self._gh_json(["pr", "list", "-R", self.fork_repo, "--head", branch, "--state", "all", "--limit", "5",
                                 "--json", "number,url,state,isDraft,headRefOid"])
            if prs is None:
                return None
            open_ = [p for p in prs if p.get("state") == "OPEN"] or prs
            if not open_:
                return {}
            p = open_[0]
            return {"number": p["number"], "url": p.get("url"), "state": p.get("state", "").lower(),
                    "draft": p.get("isDraft"), "head": p.get("headRefOid")}
        return self._m(("fork_pr", branch), f)

    def upstream_pr(self, number_or_branch):
        """{number, url, state (open|closed), draft, head, merged, title} or {} when none, None when gh failed."""
        def f():
            if isinstance(number_or_branch, int) or str(number_or_branch).isdigit():
                p = self._gh_json(["pr", "view", str(number_or_branch), "-R", self.upstream_repo, "--json",
                                   "number,url,state,isDraft,headRefOid,mergedAt,title,headRefName"])
                if p is None:
                    return None
                prs = [p]
            else:
                # a fork PR's head is `owner:branch` for gh's filter; a plain branch name finds only same-repo PRs
                owner = self.fork_repo.split("/")[0]
                prs = self._gh_json(["pr", "list", "-R", self.upstream_repo, "--head", f"{owner}:{number_or_branch}",
                                     "--state", "all", "--limit", "5", "--json",
                                     "number,url,state,isDraft,headRefOid,mergedAt,title,headRefName"])
                if prs is None:
                    return None
                prs = [p for p in prs if p.get("state") == "OPEN"] or [p for p in prs if p.get("mergedAt")] or prs
            if not prs:
                return {}
            p = prs[0]
            return {"number": p["number"], "url": p.get("url"), "state": str(p.get("state", "")).lower(),
                    "draft": bool(p.get("isDraft")), "head": p.get("headRefOid"), "merged": bool(p.get("mergedAt")),
                    "title": p.get("title"), "branch": p.get("headRefName")}
        return self._m(("upstream_pr", str(number_or_branch)), f)

    def upstream_pr_reviews(self, number):
        """Review comments and reviews on an upstream PR, newest last: [{author, body, at, kind}]."""
        def f():
            p = self._gh_json(["pr", "view", str(number), "-R", self.upstream_repo, "--json", "comments,reviews"])
            if p is None:
                return None
            out = []
            for c in p.get("comments") or []:
                out.append({"author": (c.get("author") or {}).get("login"), "body": c.get("body", ""),
                            "at": c.get("createdAt"), "kind": "comment"})
            for r in p.get("reviews") or []:
                out.append({"author": (r.get("author") or {}).get("login"), "body": r.get("body", ""),
                            "at": r.get("submittedAt"), "kind": f"review:{str(r.get('state', '')).lower()}"})
            return sorted(out, key=lambda x: x.get("at") or "")
        return self._m(("reviews", number), f)

    # --- docker and the live server ---------------------------------------------------------------------------------

    def live(self):
        return store.live()

    def docker_images(self, repo="osrs-openrune"):
        def f():
            r = _run(["docker", "images", repo, "--format", "{{.Repository}}:{{.Tag}}\t{{.ID}}"])
            if not r or r.returncode != 0:
                return None
            return {l.split("\t")[0]: l.split("\t")[1] for l in r.stdout.splitlines() if "\t" in l}
        return self._m(("images", repo), f)

    def container(self, name):
        def f():
            r = _run(["docker", "inspect", name, "--format", "{{.Config.Image}}\t{{.Image}}\t{{.State.Status}}\t{{.State.StartedAt}}"])
            if not r or r.returncode != 0:
                return None
            w = r.stdout.strip().split("\t")
            return {"image": w[0], "image_id": w[1], "status": w[2], "started": w[3]} if len(w) == 4 else None
        return self._m(("container", name), f)

    def containers(self, prefix=""):
        def f():
            r = _run(["docker", "ps", "-a", "--format", "{{.Names}}\t{{.Status}}\t{{.Image}}"])
            if not r or r.returncode != 0:
                return None
            out = []
            for l in r.stdout.splitlines():
                w = l.split("\t")
                if len(w) == 3 and w[0].startswith(prefix):
                    out.append({"name": w[0], "status": w[1], "image": w[2]})
            return out
        return self._m(("containers", prefix), f)

    # --- local facts --------------------------------------------------------------------------------------------------

    def evidence_files(self, d):
        if not d:
            return []
        if not os.path.isabs(d):
            d = os.path.join(ROOT, d)
        try:
            return sorted(os.listdir(d))
        except OSError:
            return []

    def evidence_glob(self, d, pattern):
        if not os.path.isabs(d):
            d = os.path.join(ROOT, d)
        return sorted(glob.glob(os.path.join(d, pattern)))

    def read_file(self, p):
        try:
            with open(p, errors="replace") as f:
                return f.read()
        except OSError:
            return None

    def alive(self, pid):
        return pid_alive(pid)


class FakeObserver(Observer):
    """Tests: answers from dicts. facts keys: tips {(repo, branch): sha}, contains {(repo, ref, sha): bool},
    ci {(branch, sha): str}, fork_prs {branch: dict}, upstream_prs {key: dict}, records [(name, text)], pins
    {branch: sha}, live {...}, evidence {dir: [names]}, files {path: text}, alive {pid: bool}, upstream_main sha,
    changed {(base, sha): [paths]}, docs {path: text}, images {tag: id}, containers {name: dict}, reviews {n: [...]}."""

    def __init__(self, **facts):
        self.f = facts
        self._memo = {}

    def reset(self):
        pass

    def fetch(self):
        pass

    def rev(self, repo, ref):
        return self.f.get("revs", {}).get((repo, ref))

    def branch_tip(self, repo, branch):
        return self.f.get("tips", {}).get((repo, branch))

    def upstream_main(self):
        return self.f.get("upstream_main")

    def commit_exists(self, repo, sha):
        return sha in self.f.get("commits", set()) or any(v == sha for v in self.f.get("tips", {}).values())

    def contains(self, repo, ref, sha):
        return self.f.get("contains", {}).get((repo, ref, sha))

    def merge_base(self, repo, a, b):
        return self.f.get("merge_base", {}).get((repo, a, b), "base000")

    def count_commits(self, repo, base, sha):
        return self.f.get("count", {}).get((base, sha), 1)

    def changed_files(self, repo, base, sha):
        return self.f.get("changed", {}).get((base, sha), ["src/x.kt"])

    def show(self, repo, ref, path_):
        return self.f.get("show", {}).get((repo, ref, path_))

    def docs_file(self, path_):
        return self.f.get("docs", {}).get(path_)

    def docs_records(self):
        return self.f.get("records")

    def pin(self, branch):
        return self.f.get("pins", {}).get(branch)

    def integration_list(self):
        return self.f.get("integration_list", list(self.f.get("pins", {})))

    def fork_ci(self, branch, sha):
        return self.f.get("ci", {}).get((branch, sha), "none")

    def fork_pr(self, branch):
        return self.f.get("fork_prs", {}).get(branch, {})

    def upstream_pr(self, key):
        u = self.f.get("upstream_prs", {})
        return u.get(key, u.get(str(key), {}))

    def upstream_pr_reviews(self, number):
        return self.f.get("reviews", {}).get(number, [])

    def live(self):
        return self.f.get("live", store.live())

    def docker_images(self, repo="osrs-openrune"):
        return self.f.get("images", {})

    def container(self, name):
        return self.f.get("containers", {}).get(name)

    def containers(self, prefix=""):
        return [dict(v, name=k) for k, v in self.f.get("containers", {}).items() if k.startswith(prefix)]

    def evidence_files(self, d):
        return self.f.get("evidence", {}).get(d, [])

    def evidence_glob(self, d, pattern):
        import fnmatch
        return [os.path.join(d, n) for n in self.evidence_files(d) if fnmatch.fnmatch(n, pattern)]

    def read_file(self, p):
        return self.f.get("files", {}).get(p, self.f.get("files", {}).get(os.path.basename(p)))

    def alive(self, pid):
        return self.f.get("alive", {}).get(pid, False)
