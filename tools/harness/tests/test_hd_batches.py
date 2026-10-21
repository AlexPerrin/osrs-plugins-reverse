"""hd batches (BR-B1..B6): the queue, trains (pins, rebuild, lease push, gate, image, deploy), exceptions and
resume, re-pin, releases, and the two scripts (image.sh, deploy.sh) with a stub docker. Temp bare "upstream" and
"fork" repos and the pinned build-integration.sh fixture as in test_integrate (the repo tests skip without it);
every external command (qa.sh, image.sh, deploy.sh, fork.sh, docker) is a stub that writes the expected files."""
import json
import os
import stat
import subprocess
import sys
import textwrap
import unittest
from unittest import mock

import hd_helpers as H
from hd_helpers import SHA, SHB
from helpers import GIT_ENV, HARNESS_TOOLS, commit_file, git, init_repo, tempdir

from hd import batches, events, leases, store, util
from hd.observe import FakeObserver

sys.path.insert(0, HARNESS_TOOLS)
import integrate  # noqa: E402

FIXTURE = os.path.join(os.path.dirname(os.path.abspath(__file__)), "fixtures", "build-integration.sh")
LIST = """# integration = main + these branches, merged in this order by scripts/build-integration.sh.
# Upstream fixes
fix/old
# Content
content/old

# Local only (never upstream)
local/integration-tools
"""
T0, T1 = "2026-10-21T01:00:00Z", "2026-10-21T01:11:00Z"
DEPLOY_RESOURCES = ("device", "desktop", "desktop2", "live-server")

STUBS = {
    "qa.sh": """#!/usr/bin/env bash
# clone <slug> <sha> -> $QA_ROOT/<slug>-<sha9>; gate <dir> -> <dir>.logs/summary ending rc=${STUB_GATE_RC:-0}
set -eu
case $1 in
clone) d=$QA_ROOT/$2-${3:0:9}; mkdir -p "$d"; echo "$d" ;;
gate) mkdir -p "$2.logs"; echo "gate of $2" >> "$2.logs/summary"
      echo "total 1s wall (queue wait included), rc=${STUB_GATE_RC:-0}" >> "$2.logs/summary" ;;
*) echo "stub qa.sh: $*" >&2; exit 2 ;;
esac
""",
    "image.sh": """#!/usr/bin/env bash
set -eu
clone=$1; batch=$2
[ -d "$clone" ] || { echo "stub image.sh: no clone $clone" >&2; exit 2; }
sha=$(python3 -c 'import json,sys; print(json.load(open(sys.argv[1]))["integration"])' "$HD_STATE_DIR/batches/$batch.json")
mkdir -p "$HD_STATE_DIR/images"
python3 - "$sha" "$HD_STATE_DIR/images/${sha:0:7}.json" "${STUB_KEY_HASH:-match}" <<'EOF'
import json, sys
sha, p, kh = sys.argv[1:4]
json.dump({"integration": sha, "image": "osrs-openrune:integration-" + sha[:7], "image_id": "sha256:new",
           "live_image_id": "sha256:live", "key_hash": kh, "boot": "ok", "seconds": 3}, open(p, "w"))
EOF
echo "key hash: ${STUB_KEY_HASH:-match}"
""",
    "deploy.sh": """#!/usr/bin/env bash
set -eu
mkdir -p "$HD_STATE_DIR/deploys"
python3 - "$HD_STATE_DIR/deploys/$1.json" "${STUB_DEPLOY_RESULT:-ok}" <<'EOF'
import json, sys
json.dump({"result": sys.argv[2], "image": "osrs-openrune:integration-stub", "rollback_tag": "osrs-openrune:rollback-0000000",
           "seconds": 2, "ready_line": "Server ready in 9.6 s"}, open(sys.argv[1], "w"))
EOF
""",
    "fork.sh": """#!/usr/bin/env bash
# sync: fast-forward the fake fork's main to the fake upstream's main
set -eu
[ "$1" = sync ] || { echo "stub fork.sh: $*" >&2; exit 2; }
git -C "$STUB_FORK" fetch -q "$STUB_UPSTREAM" main:main
""",
    "docker": """#!/usr/bin/env bash
# records every call; answers inspect/run/logs the way image.sh and deploy.sh read them
echo "$*" >> "$STUB_LOG"
case $1 in
inspect) if [ "$2" = osrs-openrune ]; then echo sha256:live; else echo sha256:new; fi ;;
run)
  if [ "$2" = -d ]; then echo cid; exit 0; fi
  img=$5; h=deadbeefcafe0000
  if [ "${STUB_MISMATCH:-}" = 1 ] && [ "$img" != sha256:live ]; then h=0123456789abcdef; fi
  echo "$h  /app/.data/rsa.properties" ;;
logs) [ "${STUB_NO_READY:-}" = 1 ] || echo "Server ready in 9.6 s" ;;
build|tag|rm|compose) ;;
*) echo "stub docker: $*" >&2; exit 2 ;;
esac
""",
}


def write_stubs(d):
    os.makedirs(d, exist_ok=True)
    for name, text in STUBS.items():
        p = os.path.join(d, name)
        with open(p, "w") as f:
            f.write(text)
        os.chmod(p, os.stat(p).st_mode | stat.S_IXUSR)
    return d


def close_fds():
    for k in list(batches._FDS):
        os.close(batches._FDS.pop(k))


def take_deploy_leases():
    for r in DEPLOY_RESOURCES:
        leases.drain(r)
        ok, msg = leases.take(r, leases.DAEMON, purpose="deploy")
        assert ok, msg


class QueueTest(unittest.TestCase):
    """The queue, release batches and the deploy precondition: no repos needed."""

    def setUp(self):
        self.d = H.sandbox(self)
        self.addCleanup(close_fds)

    def test_newer_commit_replaces_entry(self):
        ch = H.change("ta", stage="checked", branch="fix/a", commit=SHA)
        e1 = batches.enqueue(ch)
        self.assertEqual(batches.enqueue(ch), e1)
        ch["commit"] = SHB
        store.save(ch)
        batches.enqueue(ch)
        q = batches.read_queue()["entries"]
        self.assertEqual([(e["slug"], e["commit"]) for e in q], [("ta", SHB)])
        ev = events.read(kinds=["batch"])
        self.assertEqual(ev[-1]["step"], "queued")
        self.assertIn("replaces ta@" + SHA[:9], ev[-1]["detail"])
        with self.assertRaises(ValueError):
            batches.enqueue(H.change("c1", track="client", stage="checked", commit=SHA))

    def test_deploy_needs_the_daemons_leases(self):
        b = store.rec_save("batches", "int-20261021-1", {"id": "int-20261021-1", "kind": "train", "step": "imaged",
                                                           "includes": [], "steps": [], "integration": SHA,
                                                           "evidence": os.path.join(self.d, "evidence", "x"),
                                                           "created": T0})
        self.assertEqual(batches.deploy_ready(FakeObserver())["id"], "int-20261021-1")
        ok, why = batches.deploy(b, FakeObserver())
        self.assertFalse(ok)
        for r in DEPLOY_RESOURCES:
            self.assertIn(r, why)

    def test_release_due_and_new_release(self):
        ours = init_repo(os.path.join(self.d, "ours"), branch="master")
        head = commit_file(ours, "README", "x\n")
        os.environ["HD_OURS"] = ours
        self.addCleanup(os.environ.pop, "HD_OURS", None)
        for i in range(5):
            H.change(f"c{i}", track="client", stage="live", commit=SHA)
        self.assertIsNone(batches.release_due(store.index()))
        H.change("c5", track="client", stage="live", commit=SHA)
        due = batches.release_due(store.index())
        self.assertEqual(due, [f"c{i}" for i in range(6)])
        b = batches.new_release(due)
        self.assertEqual((b["kind"], b["step"], b["commit"], b["includes"]), ("release", "queued", head, due))
        self.assertTrue(b["id"].startswith("rel-"))
        self.assertIsNone(batches.release_due(store.index()), "one open release at a time")
        b["step"] = "published"
        store.rec_save("batches", b["id"], b)
        self.assertIsNone(batches.release_due(store.index()), "those six are covered")
        open(os.path.join(self.d, "harness", "state", "release.request"), "w").close()
        self.assertEqual(batches.release_due(store.index()), [])
        batches.new_release([])
        self.assertFalse(os.path.exists(os.path.join(self.d, "harness", "state", "release.request")))
        self.assertEqual([s["id"] for s in batches.status()], [b["id"].replace("-1", "-2")])


@unittest.skipUnless(os.path.isfile(FIXTURE), "no build-integration.sh fixture (copy it from the fork's local/integration-tools)")
class TrainTest(unittest.TestCase):
    """Trains against fake repos: the fixture's branches fix/a and content/b merge cleanly, fix/c and content/d
    conflict in src/a.txt."""

    def setUp(self):
        self.d = H.sandbox(self)
        t = self.t = tempdir(self)
        self.up = init_repo(os.path.join(t, "upstream.git"), branch="main", bare=True)
        self.fork = init_repo(os.path.join(t, "fork.git"), branch="main", bare=True)
        s = self.seed = init_repo(os.path.join(t, "seed"), branch="main")
        commit_file(s, "README", "base\n")
        self.main0 = commit_file(s, "src/a.txt", "one\nshared\nthree\n")
        git(s, "push", "-q", self.up, "main")
        self.branch("fix/old", "old.txt", "old 1\n")
        self.old1 = git(s, "rev-parse", "fix/old")
        self.branch("content/old", "cold.txt", "content old\n")
        self.sha = {}
        for name, rel, text in (("fix/a", "a-fix.txt", "a\n"), ("content/b", "b.txt", "b\n"),
                                ("fix/c", "src/a.txt", "one\nshared by c\nthree\n"),
                                ("content/d", "src/a.txt", "one\nshared by d\nthree\n")):
            self.sha[name] = self.branch(name, rel, text)
        git(s, "checkout", "-q", "-b", "local/integration-tools", "main")
        with open(FIXTURE) as f:
            commit_file(s, "scripts/build-integration.sh", f.read())
        commit_file(s, "integration.txt", LIST)
        commit_file(s, "integration.lock", f"# pins\nfix/old {self.old1}\n")
        git(s, "checkout", "-q", "main")
        git(s, "push", "-q", self.fork, "main", "main:integration", "fix/old", "content/old", "fix/a", "content/b",
            "fix/c", "content/d", "local/integration-tools")
        self.clone = init_repo(os.path.join(t, "clone"), branch="main", remotes={"fork": self.fork})
        git(self.clone, "fetch", "-q", "fork")
        stubs = write_stubs(os.path.join(t, "stubs"))
        env = {k: v for k, v in GIT_ENV.items() if k.startswith("GIT_")}
        env.update(INTEGRATION_CLONE=self.clone, INTEGRATION_TOOLS_WT=os.path.join(t, "tools-wt"),
                   INTEGRATION_REMOTE="fork", HD_QA_SH=os.path.join(stubs, "qa.sh"),
                   HD_IMAGE_SH=os.path.join(stubs, "image.sh"), HD_DEPLOY_SH=os.path.join(stubs, "deploy.sh"),
                   HD_FORK_SH=os.path.join(stubs, "fork.sh"), HD_QA_ROOT=os.path.join(t, "qa"),
                   STUB_FORK=self.fork, STUB_UPSTREAM=self.up)
        for k in ("STUB_GATE_RC", "STUB_KEY_HASH", "STUB_DEPLOY_RESULT"):
            os.environ.pop(k, None)
        p = mock.patch.dict(os.environ, env)
        p.start()
        self.addCleanup(p.stop)
        self.addCleanup(close_fds)
        self.addCleanup(self.wait_all)
        self.pushes = []
        real_git = integrate.git

        def counting_git(cwd, *args, **kw):
            if args and args[0] == "push":
                self.pushes.append(args)
            return real_git(cwd, *args, **kw)
        g = mock.patch.object(integrate, "git", counting_git)
        g.start()
        self.addCleanup(g.stop)
        util.freeze(T0)
        self.addCleanup(util.freeze, None)
        self.obs = FakeObserver(upstream_main=self.main0)

    def wait_all(self):
        """No stub keeps writing into the temp dir after the test."""
        for p in list(batches._PROCS.values()):
            p.wait(30)

    def branch(self, name, rel, text):
        git(self.seed, "checkout", "-q", "-b", name, "main")
        sha = commit_file(self.seed, rel, text, f"{name}: {rel}")
        git(self.seed, "checkout", "-q", "main")
        return sha

    def checked(self, slug, branch):
        return H.change(slug, stage="checked", branch=branch, commit=self.sha[branch])

    def fork_rev(self, ref):
        return git(self.fork, "rev-parse", ref)

    def fork_show(self, path):
        return git(self.fork, "show", f"local/integration-tools:{path}")

    def contains(self, sha, ref="integration"):
        return subprocess.run(["git", "-C", self.fork, "merge-base", "--is-ancestor", sha, ref]).returncode == 0

    def only_batch(self):
        bs = store.rec_all("batches")
        self.assertEqual(len(bs), 1, [b["id"] for b in bs])
        return bs[0]

    def integration_pushes(self):
        return [p for p in self.pushes if "HEAD:refs/heads/integration" in p]

    def step_events(self, bid):
        return [e["step"] for e in events.read(kinds=["batch"]) if e.get("batch") == bid]

    def run_to(self, step):
        """advance() and wait for background steps until the one batch reaches `step`; returns it."""
        for _ in range(8):
            b = self.only_batch()
            if b["step"] == step:
                return b
            if b["step"] in batches.BACKGROUND:
                batches.wait_step(b)
            batches.advance(self.obs)
        self.fail(f"never reached {step}: {self.only_batch()['step']}")

    # --- BR-B1: two checked changes, one train, to live ----------------------------------------------------------

    def test_train_to_live(self):
        batches.enqueue(self.checked("ta", "fix/a"))
        batches.enqueue(self.checked("tb", "content/b"))
        self.assertEqual(batches.advance(self.obs), [], "debounce: the oldest entry is 0 min old")
        self.assertEqual(store.rec_all("batches"), [])
        util.freeze(T1)
        acts = batches.advance(self.obs)
        b = self.only_batch()
        self.assertEqual(b["step"], "gating", acts)
        self.assertEqual(b["includes"], ["ta", "tb"])
        self.assertTrue(leases.holds("integration", leases.DAEMON))
        self.assertEqual(batches.read_queue()["entries"], [])
        lst = self.fork_show("integration.txt")
        self.assertEqual(integrate.listed(lst), ["fix/old", "fix/a", "content/old", "content/b", "local/integration-tools"])
        pins = integrate.parse_pins(self.fork_show("integration.lock"))
        self.assertEqual((pins["fix/a"], pins["content/b"], pins["fix/old"]), (self.sha["fix/a"], self.sha["content/b"], self.old1))
        self.assertEqual(len(self.integration_pushes()), 1)
        self.assertEqual(self.fork_rev("integration"), b["integration"])
        self.assertTrue(self.contains(self.sha["fix/a"]) and self.contains(self.sha["content/b"]))
        self.assertEqual(b["tools"], self.fork_rev("local/integration-tools"))
        self.assertIn("in train " + b["id"], store.load("ta").body)
        # the gate (stub qa.sh) -> the image (stub image.sh) -> imaged
        batches.wait_step(b)
        acts = batches.advance(self.obs)
        self.assertEqual(self.only_batch()["step"], "imaging", acts)
        self.assertTrue(os.path.isfile(os.path.join(b["gate_dir"] + ".logs", "summary")))
        batches.wait_step(self.only_batch())
        acts = batches.advance(self.obs)
        b = self.only_batch()
        self.assertEqual(b["step"], "imaged", acts)
        self.assertEqual(b["image"], "osrs-openrune:integration-" + b["integration"][:7])
        self.assertEqual(store.rec_load("images", b["integration"][:7])["key_hash"], "match")
        # a deploy is refused until the daemon holds every instance and the live server
        self.assertTrue(any(a.startswith("deploy refused") for a in acts), acts)
        self.assertEqual(batches.deploy_ready(self.obs)["id"], b["id"])
        take_deploy_leases()
        acts = batches.advance(self.obs)
        b = self.only_batch()
        self.assertEqual(b["step"], "deploying", acts)
        batches.wait_step(b)
        acts = batches.advance(self.obs)
        b = self.only_batch()
        self.assertEqual(b["step"], "live", acts)
        for slug, branch in (("ta", "fix/a"), ("tb", "content/b")):
            ch = store.load(slug)
            self.assertEqual((ch.stage, ch["live_commit"], ch["batch"]), ("live", self.sha[branch], b["id"]))
        live = store.live()
        self.assertEqual((live["integration"], live["image"], live["upstream_main"], live["rollback"]),
                         (b["integration"], "osrs-openrune:integration-stub", self.main0, "osrs-openrune:rollback-0000000"))
        for r in DEPLOY_RESOURCES + ("integration",):
            self.assertIsNone(leases.status(r)["holder"], r)
            self.assertIsNone(leases.status(r)["drain"], r)
        dep = events.read(kinds=["deploy"])
        self.assertEqual((dep[-1]["result"], dep[-1]["batch"], dep[-1]["changes"]), ("ok", b["id"], ["ta", "tb"]))
        self.assertEqual(self.step_events(b["id"]),
                         ["queued", "pinning", "rebuilding", "gating", "imaging", "imaged", "deploying", "live"])
        self.assertEqual([e["to"] for e in events.read(kinds=["stage"])], ["live", "live"])
        self.assertEqual(batches.status(), [])
        self.assertEqual(batches.open_batches(), [])

    # --- BR-B2: one train at a time -------------------------------------------------------------------------------

    def test_one_train_at_a_time(self):
        batches.enqueue(self.checked("ta", "fix/a"))
        batches.advance(self.obs, now=True)
        b = self.only_batch()
        self.assertEqual(b["step"], "gating")
        batches.enqueue(self.checked("tb", "content/b"))
        acts = batches.advance(self.obs, now=True)
        self.assertEqual(len(store.rec_all("batches")), 1, acts)
        self.assertEqual([e["slug"] for e in batches.read_queue()["entries"]], ["tb"], "joins the next train")
        self.assertEqual(len(self.integration_pushes()), 1)

    # --- BR-B4: an unrecorded conflict -> exception; resume after the integrator dropped a branch -----------------

    def test_conflict_exception_and_resume(self):
        batches.enqueue(self.checked("tc", "fix/c"))
        batches.enqueue(self.checked("td", "content/d"))
        integration0 = self.fork_rev("integration")
        acts = batches.advance(self.obs, now=True)
        b = self.only_batch()
        self.assertEqual(b["step"], "exception", acts)
        self.assertIn("UNRECORDED CONFLICT merging content/d", b["exception"])
        self.assertIn("src/a.txt", b["exception"])
        self.assertEqual(self.fork_rev("integration"), integration0)
        self.assertEqual(self.integration_pushes(), [])
        self.assertFalse(leases.holds("integration", leases.DAEMON), "the lease is freed on exception")
        self.assertEqual(self.step_events(b["id"])[-2:], ["rebuilding", "exception"])
        self.assertEqual([s["id"] for s in batches.status()], [b["id"]])
        self.assertEqual(batches.advance(self.obs, now=True), [], "an exception train is not restarted by itself")
        # the integrator drops content/d from integration.txt and cleans the stopped merge
        git(self.seed, "fetch", "-q", self.fork, "local/integration-tools")
        git(self.seed, "checkout", "-q", "-B", "local/integration-tools", "FETCH_HEAD")
        lst = git(self.seed, "show", "HEAD:integration.txt")
        commit_file(self.seed, "integration.txt", batches.remove_from_list(lst + "\n", "content/d"), "drop content/d")
        git(self.seed, "checkout", "-q", "main")
        git(self.seed, "push", "-q", self.fork, "local/integration-tools")
        git(batches.build_wt(), "merge", "--abort")
        b = batches.resume(b["id"])
        self.assertEqual(b["step"], "gating", b.get("exception"))
        self.assertEqual(b["includes"], ["tc"])
        self.assertIn("dropped from train", store.load("td").body)
        self.assertTrue(self.contains(self.sha["fix/c"]))
        self.assertFalse(self.contains(self.sha["content/d"]))
        self.assertNotIn("content/d", integrate.listed(self.fork_show("integration.txt")))
        self.assertEqual(len(self.integration_pushes()), 1)
        self.assertTrue(leases.holds("integration", leases.DAEMON))
        self.assertEqual(self.run_to("imaged")["step"], "imaged")

    def test_gate_failure_is_an_exception(self):
        batches.enqueue(self.checked("ta", "fix/a"))
        os.environ["STUB_GATE_RC"] = "1"
        batches.advance(self.obs, now=True)
        b = self.run_to("exception")
        self.assertIn("gate rc=1", b["exception"])
        self.assertFalse(leases.holds("integration", leases.DAEMON))
        del os.environ["STUB_GATE_RC"]
        b = batches.resume(b["id"])
        self.assertEqual(b["step"], "gating")
        self.assertEqual(self.run_to("imaged")["step"], "imaged")

    # --- deploy rollback -----------------------------------------------------------------------------------------

    def test_deploy_rollback(self):
        batches.enqueue(self.checked("ta", "fix/a"))
        batches.advance(self.obs, now=True)
        self.run_to("imaged")
        take_deploy_leases()
        os.environ["STUB_DEPLOY_RESULT"] = "rollback"
        b = self.run_to("exception")
        self.assertIn("deploy rollback", b["exception"])
        self.assertEqual(store.live(), {})
        self.assertEqual(store.load("ta").stage, "checked")
        self.assertEqual(events.read(kinds=["deploy"])[-1]["result"], "rollback")
        for r in DEPLOY_RESOURCES + ("integration",):
            self.assertIsNone(leases.status(r)["holder"], r)
            self.assertIsNone(leases.status(r)["drain"], r)
        self.assertEqual(self.step_events(b["id"])[-2:], ["deploying", "exception"])
        b = batches.resume(b["id"])
        self.assertEqual(b["step"], "imaged")

    # --- BR-B5: re-pin -----------------------------------------------------------------------------------------------

    def test_repin_drops_merged_branch_and_starts_a_train(self):
        git(self.seed, "merge", "-q", "--no-ff", "-m", "merge fix/old", "fix/old")
        main1 = git(self.seed, "rev-parse", "main")
        git(self.seed, "push", "-q", self.up, "main")
        store.set_live({"integration": self.fork_rev("integration"), "upstream_main": self.main0})
        obs = FakeObserver(upstream_main=main1, contains={("upstream", "main", self.old1): True})
        self.assertIsNone(batches.repin_due(FakeObserver(upstream_main=self.main0)))
        self.assertEqual(batches.repin_due(obs), main1)
        b = batches.repin(obs)
        self.assertEqual(b["step"], "gating", b.get("exception"))
        self.assertEqual(b["includes"], [])
        self.assertEqual(self.fork_rev("main"), main1, "fork.sh sync fast-forwarded the fork's main")
        self.assertNotIn("fix/old", integrate.listed(self.fork_show("integration.txt")))
        self.assertNotIn("fix/old", integrate.parse_pins(self.fork_show("integration.lock")))
        self.assertTrue(self.contains(main1))
        rp = events.read(kinds=["repin"])[-1]
        self.assertEqual((rp["from"], rp["to"], rp["dropped"]), (self.main0, main1, ["fix/old"]))
        self.assertIsNone(batches.repin_due(obs), "already re-pinned to that tip")
        self.assertEqual(self.run_to("imaged")["step"], "imaged")


class ScriptTest(unittest.TestCase):
    """image.sh and deploy.sh with a stub docker on PATH that records its argv."""

    def setUp(self):
        self.d = H.sandbox(self)
        self.stubs = write_stubs(os.path.join(self.d, "stubs"))
        self.log = os.path.join(self.d, "docker.log")
        self.state = os.path.join(self.d, "harness", "state")
        self.sha = "e27ff4d08" + "0" * 31
        os.makedirs(os.path.join(self.state, "batches"))
        with open(os.path.join(self.state, "batches", "int-t-1.json"), "w") as f:
            json.dump({"id": "int-t-1", "integration": self.sha}, f)

    def env(self, **extra):
        env = dict(os.environ, PATH=self.stubs + os.pathsep + os.environ.get("PATH", ""), STUB_LOG=self.log,
                   HD_STATE_DIR=self.state, HD_POLL_SECONDS="1", DOCKER=os.path.join(self.stubs, "docker"))
        env.update(extra)
        return env

    def calls(self):
        try:
            with open(self.log) as f:
                return f.read().splitlines()
        except OSError:
            return []

    def run_script(self, name, *args, **extra):
        return subprocess.run([os.path.join(HARNESS_TOOLS, name), *args], capture_output=True, text=True, env=self.env(**extra))

    # --- image.sh ----------------------------------------------------------------------------------------------------

    def clone(self, key=False):
        c = os.path.join(self.d, "qa", "integration-" + self.sha[:9])
        for rel in ("server/app/build/install/app/bin/app", ".data/cache/LIVE/main_file_cache.dat2", ".data/game.key",
                    ".data/postgres/pg", ".data/gamevals.txt"):
            p = os.path.join(c, rel)
            os.makedirs(os.path.dirname(p), exist_ok=True)
            open(p, "w").close()
        if key:
            open(os.path.join(c, ".data", "rsa.properties"), "w").close()
        os.makedirs(c + ".logs")
        with open(c + ".logs/summary", "w") as f:
            f.write("total 5s wall (queue wait included), rc=0\n")
        scripts = os.path.join(self.d, "cache-scripts")
        os.makedirs(scripts, exist_ok=True)
        for s in ("add_varcs", "fix_long_varps", "fix_reftable", "check_cache"):
            with open(os.path.join(scripts, s + ".py"), "w") as f:
                f.write(textwrap.dedent(f"""\
                    import os, sys
                    with open(os.path.join(sys.argv[1], ".fixed"), "a") as f:
                        f.write("{s}\\n")
                    """))
        self.image_env = dict(HD_CACHE_SCRIPTS=scripts, HD_IMAGE_ROOT=os.path.join(self.d, "images-ctx"), HD_IMAGE_TIMEOUT="5")
        return c

    def test_image_ok(self):
        c = self.clone()
        r = self.run_script("image.sh", c, "int-t-1", **self.image_env)
        self.assertEqual(r.returncode, 0, r.stdout + r.stderr)
        calls = self.calls()
        sha7 = self.sha[:7]
        tag = f"osrs-openrune:integration-{sha7}"
        ctx = os.path.join(self.d, "images-ctx", f"image-{sha7}")
        self.assertIn(f"inspect osrs-openrune --format {{{{.Image}}}}", calls)
        self.assertIn(f"build --build-arg LIVE_IMAGE=sha256:live -t {tag} {ctx}", calls)
        self.assertIn("run --rm --entrypoint sha256sum sha256:live /app/.data/rsa.properties", calls)
        self.assertIn(f"run --rm --entrypoint sha256sum {tag} /app/.data/rsa.properties", calls)
        boot = [l for l in calls if l.startswith("run -d")]
        self.assertEqual(boot, [f"run -d --name hd-boot-{sha7} --tmpfs /app/.data/postgres {tag}"])
        self.assertNotIn(" -p ", boot[0])
        self.assertIn(f"rm -f hd-boot-{sha7}", calls)
        self.assertIn("key hash: match", r.stdout)
        self.assertNotIn("deadbeef", r.stdout + r.stderr, "the hash is never printed")
        self.assertTrue(os.path.isfile(os.path.join(ctx, "server", "bin", "app")))
        self.assertTrue(os.path.isfile(os.path.join(ctx, "data", "gamevals.txt")))
        self.assertFalse(os.path.exists(os.path.join(ctx, "data", "game.key")))
        self.assertFalse(os.path.exists(os.path.join(ctx, "data", "postgres")))
        with open(os.path.join(ctx, "Dockerfile")) as f:
            self.assertIn("ARG LIVE_IMAGE", f.read())
        with open(os.path.join(ctx, "data", "cache", "LIVE", ".fixed")) as f:
            self.assertEqual(f.read().split(), ["add_varcs", "fix_long_varps", "fix_reftable", "check_cache"])
        rec = store.rec_load("images", sha7)
        self.assertEqual((rec["integration"], rec["image"], rec["image_id"], rec["live_image_id"], rec["key_hash"], rec["boot"]),
                         (self.sha, tag, "sha256:new", "sha256:live", "match", "ok"))
        self.assertIn("Server ready in", rec["ready_line"])

    def test_image_refuses_a_key_file(self):
        c = self.clone(key=True)
        r = self.run_script("image.sh", c, "int-t-1", **self.image_env)
        self.assertEqual(r.returncode, 3, r.stderr)
        self.assertEqual(self.calls(), [], "nothing staged or built")
        self.assertFalse(os.path.exists(os.path.join(self.d, "images-ctx")))

    def test_image_key_hash_mismatch(self):
        c = self.clone()
        r = self.run_script("image.sh", c, "int-t-1", STUB_MISMATCH="1", **self.image_env)
        self.assertEqual(r.returncode, 4, r.stdout + r.stderr)
        self.assertIn("key hash: MISMATCH", r.stdout)
        self.assertNotIn("deadbeef", r.stdout + r.stderr)
        self.assertNotIn("0123456789abcdef", r.stdout + r.stderr)
        self.assertEqual([l for l in self.calls() if l.startswith("run -d")], [], "no boot after a mismatch")
        self.assertIsNone(store.rec_load("images", self.sha[:7]))

    def test_image_boot_failure_removes_the_container(self):
        c = self.clone()
        r = self.run_script("image.sh", c, "int-t-1", STUB_NO_READY="1", **dict(self.image_env, HD_IMAGE_TIMEOUT="1"))
        self.assertEqual(r.returncode, 5, r.stdout + r.stderr)
        self.assertEqual(self.calls()[-1], f"rm -f hd-boot-{self.sha[:7]}", "the trap removes the boot container")
        self.assertIsNone(store.rec_load("images", self.sha[:7]))

    # --- deploy.sh ---------------------------------------------------------------------------------------------------

    def deploy_setup(self, leased=True, live=True):
        store.rec_save("images", self.sha[:7], {"integration": self.sha, "image": f"osrs-openrune:integration-{self.sha[:7]}",
                                                "image_id": "sha256:new", "key_hash": "match", "boot": "ok"})
        if leased:
            take_deploy_leases()
        if live:
            store.set_live({"integration": "5cc4733aa" + "0" * 31, "image": "osrs-openrune:integration-5cc4733"})
        od = os.path.join(self.d, "openrune")
        os.makedirs(od)
        open(os.path.join(od, "switch.env"), "w").close()
        return dict(HD_OPENRUNE_DIR=od, HD_DEPLOY_TIMEOUT="5")

    def test_deploy_ok(self):
        env = self.deploy_setup()
        r = self.run_script("deploy.sh", "int-t-1", **env)
        self.assertEqual(r.returncode, 0, r.stdout + r.stderr)
        calls = self.calls()
        new = f"osrs-openrune:integration-{self.sha[:7]}"
        self.assertEqual([l for l in calls if l.startswith("tag ")],
                         ["tag osrs-openrune:241 osrs-openrune:rollback-5cc4733", f"tag {new} osrs-openrune:241"])
        compose = [l for l in calls if l.startswith("compose")]
        self.assertEqual(compose, ["compose --env-file switch.env up -d --no-deps openrune"])
        self.assertLess(calls.index("tag osrs-openrune:241 osrs-openrune:rollback-5cc4733"), calls.index(compose[0]))
        self.assertFalse(any("--build" in l for l in calls))
        self.assertTrue(any(l.startswith("logs --since ") and l.endswith(" osrs-openrune") for l in calls))
        rec = store.rec_load("deploys", "int-t-1")
        self.assertEqual((rec["result"], rec["image"], rec["rollback_tag"], rec["integration"]),
                         ("ok", new, "osrs-openrune:rollback-5cc4733", self.sha))
        self.assertIn("Server ready in", rec["ready_line"])

    def test_deploy_refused_without_the_leases(self):
        env = self.deploy_setup(leased=False)
        r = self.run_script("deploy.sh", "int-t-1", **env)
        self.assertEqual(r.returncode, 75, r.stdout + r.stderr)
        for res in DEPLOY_RESOURCES:
            self.assertIn(res, r.stderr)
        self.assertEqual(self.calls(), [])
        self.assertIsNone(store.rec_load("deploys", "int-t-1"))
        leases.release("device", leases.DAEMON)
        r = self.run_script("deploy.sh", "int-t-1", **env)
        self.assertEqual(r.returncode, 75)

    def test_deploy_rolls_back_on_timeout(self):
        env = self.deploy_setup()
        r = self.run_script("deploy.sh", "int-t-1", STUB_NO_READY="1", **dict(env, HD_DEPLOY_TIMEOUT="1"))
        self.assertEqual(r.returncode, 1, r.stdout + r.stderr)
        calls = self.calls()
        new = f"osrs-openrune:integration-{self.sha[:7]}"
        self.assertEqual([l for l in calls if l.startswith("tag ")],
                         ["tag osrs-openrune:241 osrs-openrune:rollback-5cc4733", f"tag {new} osrs-openrune:241",
                          "tag osrs-openrune:rollback-5cc4733 osrs-openrune:241"])
        self.assertEqual(len([l for l in calls if l.startswith("compose")]), 2)
        self.assertFalse(any("--build" in l for l in calls))
        rec = store.rec_load("deploys", "int-t-1")
        self.assertEqual((rec["result"], rec["rollback_tag"]), ("rollback", "osrs-openrune:rollback-5cc4733"))


if __name__ == "__main__":
    unittest.main()
