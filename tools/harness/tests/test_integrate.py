"""Integration trains (docs/integration-pipeline.md, phase 1): integrate.py's queue, lock, trains and pins, and
ship.sh's server path (enqueue only). Temp bare "upstream" and "fork" repos, a push clone whose `fork` remote is the
bare fork, and the pinned build-integration.sh copied from the fork's local/integration-tools
(fixtures/build-integration.sh; the tests skip without it). No network."""
import contextlib
import io
import os
import subprocess
import sys
import unittest
from unittest import mock

from helpers import GIT_ENV, HARNESS_TOOLS, commit_file, git, init_repo, read_task, tempdir, write_task

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


@unittest.skipUnless(os.path.isfile(FIXTURE), "no build-integration.sh fixture (copy it from the fork's local/integration-tools)")
class IntegrateTest(unittest.TestCase):
    def setUp(self):
        t = self.t = tempdir(self)
        self.up = init_repo(os.path.join(t, "upstream.git"), branch="main", bare=True)
        self.fork = init_repo(os.path.join(t, "fork.git"), branch="main", bare=True)
        s = self.seed = init_repo(os.path.join(t, "seed"), branch="main")
        commit_file(s, "README", "base\n")
        commit_file(s, "src/a.txt", "one\nshared\nthree\n")
        git(s, "push", "-q", self.up, "main")
        self.branch("fix/old", "old.txt", "old 1\n")
        self.old1 = git(s, "rev-parse", "fix/old")
        self.branch("content/old", "cold.txt", "content old\n")
        self.branch("fix/a", "a-fix.txt", "a\n")
        self.branch("content/b", "b.txt", "b\n")
        self.branch("fix/c", "src/a.txt", "one\nshared by c\nthree\n")
        self.branch("content/d", "src/a.txt", "one\nshared by d\nthree\n")
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
        self.harness = os.path.join(t, "harness")
        self.env = dict(GIT_ENV, HARNESS_DIR=self.harness, INTEGRATION_DIR=os.path.join(t, "state"),
                        INTEGRATION_CLONE=self.clone, INTEGRATION_TOOLS_WT=os.path.join(t, "tools-wt"),
                        INTEGRATION_REMOTE="fork", INTEGRATION_EVIDENCE_ROOT=os.path.join(t, "evidence"),
                        INTEGRATION_DEBOUNCE_MIN="10")
        p = mock.patch.dict(os.environ, self.env)
        p.start()
        self.addCleanup(p.stop)
        self.addCleanup(self.close_fds)
        self.pushes = []
        real_git = integrate.git

        def counting_git(cwd, *args, **kw):
            if args and args[0] == "push":
                self.pushes.append(args)
            return real_git(cwd, *args, **kw)
        g = mock.patch.object(integrate, "git", counting_git)
        g.start()
        self.addCleanup(g.stop)

    def close_fds(self):
        for k in list(integrate._FDS):
            os.close(integrate._FDS.pop(k))

    def branch(self, name, rel, text):
        git(self.seed, "checkout", "-q", "-b", name, "main")
        commit_file(self.seed, rel, text, f"{name}: {rel}")
        git(self.seed, "checkout", "-q", "main")

    def advance(self, name, rel, text):
        """A push to a fork branch after its hand-over."""
        git(self.seed, "checkout", "-q", name)
        sha = commit_file(self.seed, rel, text, f"{name}: later push")
        git(self.seed, "checkout", "-q", "main")
        git(self.seed, "push", "-q", self.fork, name)
        return sha

    def task(self, tid, branch):
        write_task(self.harness, tid, track="server", role="server-dev", status="shipped", owner=None, branch=branch,
                   commit=git(self.seed, "rev-parse", branch))
        return tid

    def run_quiet(self, fn, *a, **kw):
        out = io.StringIO()
        with contextlib.redirect_stdout(out):
            rc = fn(*a, **kw)
        self.out = out.getvalue()
        return rc

    def enqueue(self, tid):
        self.assertEqual(self.run_quiet(integrate.enqueue, tid), 0, self.out)

    def queue(self):
        return [(e["task"], e["commit"]) for e in integrate.read_queue()["entries"]]

    def fork_rev(self, ref):
        return git(self.fork, "rev-parse", ref)

    def fork_show(self, ref, path):
        return git(self.fork, "show", f"{ref}:{path}")

    def train(self, tid=None):
        ts = integrate.trains()
        self.assertTrue(ts, "no train")
        m, body = ts[-1] if tid is None else read_task(self.harness, tid)
        return m, body

    def cli(self, *args, **env):
        return subprocess.run([sys.executable, os.path.join(HARNESS_TOOLS, "integrate.py"), *args], capture_output=True,
                              text=True, env=dict(os.environ, **env))

    def contains(self, sha, ref="integration"):
        return subprocess.run(["git", "-C", self.fork, "merge-base", "--is-ancestor", sha, ref]).returncode == 0

    # --- the tests -------------------------------------------------------------------------------------------------

    def test_two_enqueues_one_train(self):
        tools0 = self.fork_rev("local/integration-tools")
        self.enqueue(self.task("ta", "fix/a"))
        self.enqueue(self.task("tb", "content/b"))
        self.assertIn("queued for the next train (fix/a@", read_task(self.harness, "ta")[1])
        self.assertEqual(self.run_quiet(integrate.build, True), 0, self.out)
        # one train, one integration.txt commit (with the pins), one tools push and one lease push
        self.assertEqual(len(integrate.trains()), 1)
        tools1 = self.fork_rev("local/integration-tools")
        self.assertEqual(git(self.fork, "rev-list", "--count", f"{tools0}..{tools1}"), "1")
        self.assertEqual(sorted(git(self.fork, "diff", "--name-only", tools0, tools1).split()),
                         ["integration.lock", "integration.txt"])
        self.assertEqual([p for p in self.pushes if "HEAD:refs/heads/integration" in p and
                          any(a.startswith("--force-with-lease=") for a in p)], self.pushes[1:])
        self.assertEqual(len(self.pushes), 2)
        isha = self.fork_rev("integration")
        for b in ("fix/a", "content/b", "fix/old", "content/old", "local/integration-tools"):
            self.assertTrue(self.contains(self.fork_rev(b)), b)
        m, body = self.train()
        self.assertRegex(m["id"], r"^int-\d{8}-1$")
        self.assertEqual((m["track"], m["role"], m["stage"], m["status"]), ("orch", "integration", "built", "shipped"))
        self.assertEqual(m["includes"], ["ta", "tb"])
        self.assertEqual((m["integration"], m["tools"]), (isha, tools1))
        self.assertTrue(os.path.isfile(os.path.join(m["evidence"], "rebuild.log")))
        for tid in ("ta", "tb"):
            tm, tb = read_task(self.harness, tid)
            self.assertEqual((tm["status"], tm["integration"]), ("shipped", isha))
            self.assertIn(f"in train {m['id']}: integration {isha[:9]}", tb)
        self.assertEqual(self.queue(), [])
        self.assertIn("qa.sh clone integration " + isha[:9], self.out)
        # the train task passes the store's lint; dispatch skips it (role integration)
        lint = subprocess.run([sys.executable, os.path.join(HARNESS_TOOLS, "task.py"), "lint"], capture_output=True,
                              text=True, env=os.environ)
        self.assertEqual(lint.returncode, 0, lint.stdout)
        # status prints the train and the gate as its next step
        self.run_quiet(integrate.status)
        self.assertIn(f"train {m['id']}: built", self.out)
        self.assertIn("qa.sh gate", self.out)

    def test_sections_and_pins(self):
        self.enqueue(self.task("ta", "fix/a"))
        self.enqueue(self.task("tb", "content/b"))
        self.run_quiet(integrate.build, True)
        lst = self.fork_show("local/integration-tools", "integration.txt").splitlines()
        self.assertEqual(lst[lst.index("# Upstream fixes") + 1:lst.index("# Content")], ["fix/old", "fix/a"])
        self.assertEqual(lst[lst.index("# Content") + 1:lst.index("# Content") + 3], ["content/old", "content/b"])
        self.assertEqual(lst[-2:], ["# Local only (never upstream)", "local/integration-tools"])
        pins = integrate.parse_pins(self.fork_show("local/integration-tools", "integration.lock"))
        self.assertEqual(pins, {"fix/old": self.old1, "fix/a": self.fork_rev("fix/a"), "content/b": self.fork_rev("content/b")})

    def test_debounce(self):
        self.enqueue(self.task("ta", "fix/a"))
        self.assertEqual(self.run_quiet(integrate.build), 0)
        self.assertIn("waiting", self.out)
        self.assertEqual(integrate.trains(), [])
        self.assertEqual(self.pushes, [])
        with mock.patch.dict(os.environ, INTEGRATION_DEBOUNCE_MIN="0"):
            self.assertEqual(self.run_quiet(integrate.build), 0, self.out)
        self.assertEqual(self.train()[0]["stage"], "built")

    def test_enqueue_while_building_and_second_build_busy(self):
        self.task("ta", "fix/a")
        self.task("tb", "content/b")
        holder = subprocess.Popen([sys.executable, "-c", "import sys; sys.path.insert(0, sys.argv[1]); import integrate; "
                                   "assert integrate.try_build_lock(); print('held', flush=True); sys.stdin.read()",
                                   HARNESS_TOOLS], stdin=subprocess.PIPE, stdout=subprocess.PIPE, text=True, env=os.environ)
        def stop_holder():
            holder.kill()
            holder.wait(10)
            for f in (holder.stdin, holder.stdout):
                if not f.closed:
                    f.close()
        self.addCleanup(stop_holder)
        self.assertEqual(holder.stdout.readline().strip(), "held")
        r = self.cli("enqueue", "ta")
        self.assertEqual(r.returncode, 0, r.stdout + r.stderr)
        self.assertIn("goes in the next one", r.stdout)
        r = self.cli("build", "--now")
        self.assertEqual(r.returncode, integrate.BUSY, r.stdout + r.stderr)
        self.assertIn("busy", r.stdout)
        self.assertEqual(self.queue(), [("ta", self.fork_rev("fix/a"))])
        holder.stdin.close()
        holder.wait(10)
        # an enqueue during a train (after it drained the queue) waits for the next train
        real = integrate.rebuild

        def rebuild_and_enqueue(*a):
            r = real(*a)
            with contextlib.redirect_stdout(io.StringIO()):
                integrate.enqueue("tb")
            return r
        with mock.patch.object(integrate, "rebuild", rebuild_and_enqueue):
            self.assertEqual(self.run_quiet(integrate.build, True), 0, self.out)
        self.assertEqual(self.train()[0]["includes"], ["ta"])
        self.assertFalse(self.contains(self.fork_rev("content/b")))
        self.assertEqual(self.queue(), [("tb", self.fork_rev("content/b"))])
        self.assertIn("1 entries wait for the next train", self.out)
        self.assertEqual(self.run_quiet(integrate.build, True), 0, self.out)
        self.assertEqual([m["includes"] for m, _ in integrate.trains()], [["ta"], ["tb"]])
        self.assertRegex(integrate.trains()[-1][0]["id"], r"-2$")
        self.assertTrue(self.contains(self.fork_rev("content/b")))

    def test_tip_moved_past_pin_not_taken(self):
        a1 = self.fork_rev("fix/a")
        self.enqueue(self.task("ta", "fix/a"))
        a2 = self.advance("fix/a", "a-fix.txt", "a, pushed after the hand-over\n")
        o2 = self.advance("fix/old", "old.txt", "old 2, never handed over\n")
        self.assertEqual(self.run_quiet(integrate.build, True), 0, self.out)
        self.assertTrue(self.contains(a1))
        self.assertTrue(self.contains(self.old1))
        self.assertFalse(self.contains(a2))
        self.assertFalse(self.contains(o2))
        with open(os.path.join(self.train()[0]["evidence"], "rebuild.log")) as f:
            log = f.read()
        self.assertRegex(log, rf"merged fix/a at pin {a1[:7]}\w* \(tip {a2[:7]}\w* not taken\)")
        self.assertRegex(log, rf"merged fix/old at pin {self.old1[:7]}\w* \(tip {o2[:7]}\w* not taken\)")
        git(self.clone, "fetch", "-q", "fork")
        self.run_quiet(integrate.status)
        self.assertIn(f"fix/a: tip {a2[:9]} moved past its pin {a1[:9]} without a hand-over", self.out)
        self.assertIn(f"fix/old: tip {o2[:9]} moved past its pin {self.old1[:9]}", self.out)
        self.assertIn("content/old: no pin", self.out)
        self.assertNotIn("local/integration-tools", self.out.split("pin warning")[-1])

    def test_unrecorded_conflict_needs_agent(self):
        self.enqueue(self.task("tc", "fix/c"))
        self.enqueue(self.task("td", "content/d"))
        before = self.queue()
        integration0 = self.fork_rev("integration")
        self.assertEqual(self.run_quiet(integrate.build, True), 1, self.out)
        m, body = self.train()
        self.assertEqual((m["stage"], m["status"]), ("needs-agent", "in-progress"))
        self.assertIn("UNRECORDED CONFLICT merging content/d", body)
        self.assertIn("src/a.txt", body)
        self.assertEqual(self.queue(), before)
        self.assertEqual(self.fork_rev("integration"), integration0)
        self.assertEqual([p for p in self.pushes if "HEAD:refs/heads/integration" in p], [])
        self.run_quiet(integrate.status)
        self.assertIn("needs-agent", self.out)
        self.assertIn("resolve the stopped rebuild", self.out)

    def test_lease_failure_keeps_queue(self):
        self.enqueue(self.task("ta", "fix/a"))
        self.enqueue(self.task("tb", "content/b"))
        before = self.queue()
        other = git(self.seed, "rev-parse", "content/old")
        real = integrate.push_integration

        def someone_else_rebuilds_first(cwd, lease):
            git(self.seed, "push", "-q", "-f", self.fork, f"{other}:refs/heads/integration")
            return real(cwd, lease)
        with mock.patch.object(integrate, "push_integration", someone_else_rebuilds_first):
            self.assertEqual(self.run_quiet(integrate.build, True), 1, self.out)
        m, body = self.train()
        self.assertEqual(m["stage"], "failed")
        self.assertIn("lease push of integration", body)
        self.assertEqual(self.queue(), before)
        self.assertEqual(self.fork_rev("integration"), other)
        self.assertNotIn("integration", read_task(self.harness, "ta")[0])
        # a re-run reuses the open train and builds over the new tip
        self.assertEqual(self.run_quiet(integrate.build, True), 0, self.out)
        self.assertEqual(len(integrate.trains()), 1)
        self.assertEqual(self.train()[0]["stage"], "built")
        self.assertEqual(self.queue(), [])

    def test_enqueue_refuses_a_moved_branch(self):
        self.task("ta", "fix/a")
        self.advance("fix/a", "a-fix.txt", "moved\n")
        with self.assertRaises(SystemExit) as cm, contextlib.redirect_stdout(io.StringIO()):
            integrate.enqueue("ta")
        self.assertIn("not the handed-over", str(cm.exception))
        self.assertEqual(self.queue(), [])

    def test_old_worktree_layout_refused(self):
        os.makedirs(os.environ["INTEGRATION_DIR"], exist_ok=True)
        with open(os.path.join(os.environ["INTEGRATION_DIR"], ".git"), "w") as f:
            f.write("gitdir: elsewhere\n")
        with self.assertRaises(SystemExit) as cm:
            integrate.build(True)
        self.assertIn("worktree remove", str(cm.exception))

    def test_ship_server_enqueues(self):
        sha = self.fork_rev("fix/a")
        write_task(self.harness, "ta", track="server", role="server-dev", status="handover", branch="fix/a", commit=sha,
                   body_extra="- 2026-10-20T00:00:01Z handover-check PASS\n")
        integration0 = self.fork_rev("integration")
        tools0 = self.fork_rev("local/integration-tools")
        r = subprocess.run(["bash", os.path.join(HARNESS_TOOLS, "ship.sh"), "ta"], capture_output=True, text=True,
                           env=os.environ)
        self.assertEqual(r.returncode, 0, r.stdout + r.stderr)
        m, body = read_task(self.harness, "ta")
        self.assertEqual(m["status"], "shipped")
        self.assertIn("queued for the next train (fix/a@", body)
        self.assertEqual(self.queue(), [("ta", sha)])
        # no rebuild and no push at ship time
        self.assertEqual(self.fork_rev("integration"), integration0)
        self.assertEqual(self.fork_rev("local/integration-tools"), tools0)
        self.assertIn("integrate build", r.stdout)


if __name__ == "__main__":
    unittest.main()
