"""Integration pipeline phase 0 (docs/integration-pipeline.md): the `live` status and dependents' unblocking in task.py,
the one-off `migrate-live`, client ship marking a task live, the integration deny rules in deny_publish.py, and
LOCK_DIR in device-lock.sh. Temp task stores, repos and lock dirs only."""
import os
import shutil
import subprocess
import sys
import unittest

from helpers import GIT_ENV, HARNESS_TOOLS, HOOKS, ROOT, commit_file, git, init_repo, read_task, tempdir, write_task

sys.path.insert(0, HOOKS)
import deny_publish  # noqa: E402

LIVE_LINE = "- 2026-10-20T04:44:42Z Live 2026-10-20 04:44 UTC (integration e5b18a87b, image integration-e5b18a8).\n"


class TaskStore:
    def setUp(self):
        self.harness = os.path.join(tempdir(self), "harness")

    def task_py(self, *args, check=True):
        r = subprocess.run([sys.executable, os.path.join(HARNESS_TOOLS, "task.py"), *args], capture_output=True,
                           text=True, env=dict(os.environ, HARNESS_DIR=self.harness))
        if check:
            self.assertEqual(r.returncode, 0, r.stdout + r.stderr)
        return r

    def status(self, tid):
        return read_task(self.harness, tid)[0]["status"]

    def server(self, tid, status="shipped", unblocks=(), deployed=False, **kw):
        write_task(self.harness, tid, track="server", role="server-dev", status=status, owner=None,
                   branch=f"fix/{tid}", commit="a" * 40, unblocks=list(unblocks), **kw)
        if deployed:   # the orchestrator's deploy line, at the end of the log
            with open(os.path.join(self.harness, "tasks", f"{tid}.md"), "a") as f:
                f.write(LIVE_LINE)

    def client(self, tid, status="blocked", blocked_by=(), owner=None, **kw):
        write_task(self.harness, tid, status=status, owner=owner, blocked_by=list(blocked_by), **kw)

    def ready_ids(self):
        return [line.split()[3] for line in self.task_py("ready").stdout.splitlines() if line.strip()]


class LiveStatusTest(TaskStore, unittest.TestCase):
    def test_shipped_blocks_live_and_done_do_not(self):
        for st, blocks in (("shipped", True), ("handover", True), ("live", False), ("done", False), ("qa", False),
                           ("gated", False)):
            with self.subTest(st):
                self.setUp()
                self.server("srv", status=st, unblocks=["cli"])
                self.client("cli", status="ready", blocked_by=["srv"])
                self.assertEqual("cli" in self.ready_ids(), not blocks)

    def test_set_live_unblocks(self):
        self.server("srv", unblocks=["cli"])
        self.client("cli", blocked_by=["srv"])
        self.task_py("set", "srv", "status=shipped")
        self.assertEqual(self.status("cli"), "blocked")
        r = self.task_py("set", "srv", "status=live")
        self.assertIn("cli: ready", r.stdout)
        self.assertEqual(self.status("cli"), "ready")
        self.assertIn("unblocked by srv (live)", read_task(self.harness, "cli")[1])
        self.assertEqual(self.ready_ids(), ["cli"])

    def test_set_done_unblocks(self):
        self.server("srv", status="live", unblocks=["cli"])
        self.client("cli", blocked_by=["srv"])
        self.task_py("set", "srv", "status=done")
        self.assertEqual(self.status("cli"), "ready")

    def test_done_unblocks(self):
        self.server("srv", unblocks=["cli"])
        self.client("cli", blocked_by=["srv"])
        self.task_py("done", "srv")
        self.assertEqual(self.status("cli"), "ready")

    def test_waits_for_every_blocker(self):
        self.server("a", unblocks=["cli"])
        self.server("b", unblocks=["cli"])
        self.client("cli", blocked_by=["a", "b"])
        self.task_py("set", "a", "status=live")
        self.assertEqual(self.status("cli"), "blocked")
        self.task_py("set", "b", "status=live")
        self.assertEqual(self.status("cli"), "ready")

    def test_only_blocked_dependents_change(self):
        self.server("srv", unblocks=["cli"])
        self.client("cli", status="in-progress", blocked_by=["srv"], owner="cd1")
        self.task_py("set", "srv", "status=live")
        self.assertEqual(self.status("cli"), "in-progress")

    def test_unknown_status_still_refused(self):
        self.server("srv")
        r = self.task_py("set", "srv", "status=deployed", check=False)
        self.assertNotEqual(r.returncode, 0)
        self.assertIn("live", r.stderr)

    def test_lint_accepts_live_and_wants_branch_and_commit(self):
        self.server("srv", status="live")
        self.assertEqual(self.task_py("lint").returncode, 0)
        write_task(self.harness, "bare", track="server", status="live", owner=None)
        r = self.task_py("lint", check=False)
        self.assertIn("bare: live without branch and commit", r.stdout)


class MigrateLiveTest(TaskStore, unittest.TestCase):
    def setUp(self):
        super().setUp()
        # The live store on 2026-10-20: two server tasks deployed at 04:44 UTC, one shipped but not deployed.
        self.server("fish-barrel-check-and-destroy", unblocks=["hub-fish-barrel-check"], deployed=True)
        self.server("fix-stile-approach", deployed=True)
        self.server("clue-check-steps-tier", unblocks=["hub-clue-steps-tier"])
        self.client("hub-fish-barrel-check", blocked_by=["fish-barrel-check-and-destroy"])
        self.client("hub-clue-steps-tier", blocked_by=["clue-check-steps-tier"])
        write_task(self.harness, "hub-batch-next", status="shipped", owner=None, branch="client/x", commit="b" * 40)

    def snapshot(self):
        d = os.path.join(self.harness, "tasks")
        out = {}
        for t in os.listdir(d):
            with open(os.path.join(d, t)) as f:
                out[t] = f.read()
        return out

    def test_dry_run_changes_nothing(self):
        before = self.snapshot()
        r = self.task_py("migrate-live", "--dry-run")
        self.assertIn("3 task(s) would move", r.stdout)
        self.assertEqual(before, self.snapshot())

    def test_named_ids(self):
        r = self.task_py("migrate-live", "fish-barrel-check-and-destroy", "fix-stile-approach", "clue-check-steps-tier")
        self.assertIn("clue-check-steps-tier: stays shipped", r.stdout)
        self.assertEqual(self.status("fish-barrel-check-and-destroy"), "live")
        self.assertEqual(self.status("fix-stile-approach"), "live")
        self.assertEqual(self.status("clue-check-steps-tier"), "shipped")
        self.assertEqual(self.status("hub-batch-next"), "shipped")   # not named
        self.assertEqual(self.status("hub-fish-barrel-check"), "ready")
        self.assertEqual(self.status("hub-clue-steps-tier"), "blocked")
        self.assertIn("migrate-live: shipped -> live (deployed: Live 2026-10-20 04:44 UTC",
                      read_task(self.harness, "fix-stile-approach")[1])
        self.assertEqual(self.task_py("lint").returncode, 0)

    def test_all(self):
        self.task_py("migrate-live")
        self.assertEqual(self.status("hub-batch-next"), "live")   # a client ship merged it into master
        self.assertEqual(self.status("clue-check-steps-tier"), "shipped")
        # Idempotent: nothing left to move.
        self.assertIn("0 task(s) moved", self.task_py("migrate-live").stdout)

    def test_unknown_id(self):
        self.assertNotEqual(self.task_py("migrate-live", "nope", check=False).returncode, 0)


class ClientShipLiveTest(TaskStore, unittest.TestCase):
    """ship.sh (client) from a copy of the harness in a temp checkout: the merge into master marks the task live and
    unblocks its dependents."""

    def test_client_ship_sets_live(self):
        t = tempdir(self)
        repo = init_repo(os.path.join(t, "checkout"))
        os.makedirs(os.path.join(repo, "tools", "harness"))
        for f in ("ship.sh", "task.py"):
            shutil.copy(os.path.join(HARNESS_TOOLS, f), os.path.join(repo, "tools", "harness", f))
        with open(os.path.join(repo, ".gitignore"), "w") as f:
            f.write("harness/\n")
        git(repo, "add", "-A")
        git(repo, "commit", "-q", "-m", "base")
        git(repo, "checkout", "-q", "-b", "client/x")
        sha = commit_file(repo, "host/A.java", "class A {}\n")
        git(repo, "checkout", "-q", "master")
        self.harness = os.path.join(repo, "harness")
        write_task(self.harness, "cx", branch="client/x", commit=sha, unblocks=["dep"],
                   body_extra="- 2026-10-20T00:00:01Z handover-check PASS\n")
        write_task(self.harness, "dep", status="blocked", owner=None, blocked_by=["cx"])
        r = subprocess.run(["bash", os.path.join(repo, "tools", "harness", "ship.sh"), "cx", "--no-push"],
                           capture_output=True, text=True, env=dict(GIT_ENV, HARNESS_DIR=self.harness))
        self.assertEqual(r.returncode, 0, r.stdout + r.stderr)
        self.assertEqual(self.status("cx"), "live")
        self.assertEqual(self.status("dep"), "ready")
        self.assertEqual(git(repo, "rev-parse", "HEAD^2"), sha)


class IntegrationDenyTest(unittest.TestCase):
    """The orchestrator-only integration steps, denied to every agent role whatever its flags."""
    CASES = [
        # (command, cwd (relative to a temp root), denied)
        ("tools/harness/deploy.sh int-20261020-1", ".", True),
        ("tools/harness/ship.sh fix-x", ".", True),
        ("LOCK_NAME=desktop tools/device-lock.sh reclaim idle", ".", True),
        ("LOCK_NAME=desktop tools/device-lock.sh revoke sd28", ".", True),
        ("tools/harness/ship.sh fix-x --dry-run", ".", True),
        ("bash tools/harness/image.sh re/harness/qa/x int-20261020-1", ".", True),
        ("/home/alex/osrs-plugins-reverse/tools/harness/deploy.sh", ".", True),
        ("./deploy.sh int-1", "tools/harness", True),
        ("./image.sh clone int-1", "tools/harness", True),
        ("cd tools/harness && ./deploy.sh int-1", ".", True),
        ("python3 tools/harness/orchestrate.py deploy", ".", True),
        ("tools/harness/orchestrate.py deploy int-20261020-1", ".", True),
        ("tools/harness/orchestrate.py integrate build --now", ".", True),
        ("tools/harness/orchestrate.py integrate status", ".", True),
        ("tools/harness/integrate.py build --now", ".", True),
        ("python3 tools/harness/integrate.py enqueue fix-x", ".", True),
        ("./integrate.py status", "tools/harness", True),
        ("tools/harness/orchestrate.py image int-20261020-1", ".", True),
        ("docker tag osrs-openrune:integration-e5b18a8 osrs-openrune:latest", ".", True),
        ("docker image tag osrs-openrune:x osrs-openrune:rollback-1", ".", True),
        ("docker rm -f osrs-openrune", ".", True),
        ("docker container rm osrs-openrune-http", ".", True),
        ("docker rmi osrs-openrune:bootcheck23", ".", True),
        ("docker image rm osrs-openrune:old", ".", True),
        ("docker restart osrs-openrune", ".", True),
        ("docker stop osrs-openrune-http-desktop", ".", True),
        ("docker compose -f openrune/docker-compose.yml up -d --no-deps openrune", ".", True),
        ("docker compose up -d --no-deps openrune", "openrune", True),
        ("cd openrune && docker compose up -d", ".", True),
        ("docker-compose -p osrs-openrune down", ".", True),
        ("docker compose --project-directory openrune restart", ".", True),
        ('bash -c "docker compose -f openrune/docker-compose.yml up -d"', ".", True),
        ("docker compose -f openrune/docker-compose.yml build openrune", ".", True),
        # --- allowed: reads, the agents' own containers, mentions that run nothing
        ("tools/harness/orchestrate.py status", ".", False),
        ("tools/harness/orchestrate.py next --track server", ".", False),
        ("python3 tools/harness/orchestrate.py status | grep deploy", ".", False),
        ("docker ps --filter name=osrs-openrune", ".", False),
        ("docker logs --tail 50 osrs-openrune", ".", False),
        ("docker exec osrs-openrune-http test -e /tmp/local-auth-user", ".", False),
        ("docker images osrs-openrune", ".", False),
        ("docker image ls osrs-openrune", ".", False),
        ("docker inspect osrs-openrune", ".", False),
        ("docker rm -f qa-or-server qa-or-http", ".", False),
        ("docker rmi qa-branch:sd31", ".", False),
        ("docker compose -f openrune/docker-compose.yml ps", ".", False),
        ("docker compose logs --tail 20 openrune", "openrune", False),
        ("docker compose -f openrune/docker-compose.yml config", ".", False),
        ("grep -n deploy docs/integration-pipeline.md", ".", False),
        ("ls tools/harness", ".", False),
        ("./deploy.sh", "elsewhere", False),
    ]

    def setUp(self):
        self.root = tempdir(self)
        for d in ("tools/harness", "openrune", "elsewhere"):
            os.makedirs(os.path.join(self.root, d))

    def test_table(self):
        for flags in ([], ["--fork-writes"], ["--docs-writes"]):
            for cmd, cwd, denied in self.CASES:
                with self.subTest(cmd=cmd, cwd=cwd, flags=flags):
                    reason = deny_publish.decide({"tool_input": {"command": cmd},
                                                  "cwd": os.path.normpath(os.path.join(self.root, cwd))}, flags)
                    self.assertEqual(reason is not None, denied, reason)
                    if denied:
                        self.assertIn("Only the orchestrator", reason)

    def test_hook_cli(self):
        import json
        data = json.dumps({"tool_name": "Bash", "tool_input": {"command": "tools/harness/deploy.sh x"}, "cwd": self.root})
        out = subprocess.run([sys.executable, os.path.join(HOOKS, "deny_publish.py"), "--fork-writes"], input=data,
                             capture_output=True, text=True, env=dict(os.environ, HD_EVENTS=os.devnull)).stdout
        self.assertIn('"deny"', out)
        self.assertIn("integration images, deploys", out)


class LockDirTest(unittest.TestCase):
    """device-lock.sh is a wrapper over `hd lease` (harness v3): the lease lives in the hd state dir and LOCK_DIR
    holds only the v2 mirror files (osrs-<name>.lock/owner while held, osrs-<name>-status)."""

    def setUp(self):
        import hd_helpers
        self.d = hd_helpers.sandbox(self)
        self.lock_dir = os.environ["HD_LOCK_DIR"]

    def run_lock(self, *args, name="device"):
        env = dict(os.environ, LOCK_NAME=name, LOCK_DIR=self.lock_dir)
        return subprocess.run(["bash", os.path.join(ROOT, "tools", "device-lock.sh"), *args], capture_output=True, text=True, env=env)

    def test_lock_dir_holds_every_file(self):
        who = "sd99"
        self.assertEqual(self.run_lock("grant", who, "30", "reuse", name="desktop").returncode, 0)
        r = self.run_lock("take", who, "ab test", name="desktop")
        self.assertEqual(r.returncode, 0, r.stdout + r.stderr)
        self.assertTrue(os.path.isfile(os.path.join(self.lock_dir, "osrs-desktop.lock", "owner")))
        self.assertIn("taking desktop (sd99 dev, ab test)", open(os.path.join(self.lock_dir, "osrs-desktop.lock", "owner")).read())
        self.assertNotEqual(self.run_lock("status", name="desktop").returncode, 0)
        self.assertEqual(self.run_lock("take", "other", "x", name="desktop").returncode, 1)
        self.assertEqual(self.run_lock("free", who, name="desktop").returncode, 0)
        self.assertFalse(os.path.exists(os.path.join(self.lock_dir, "osrs-desktop.lock")))
        self.assertEqual(self.run_lock("status", name="desktop").returncode, 0)

    def test_default_is_tmp(self):
        src = open(os.path.join(ROOT, "tools", "device-lock.sh")).read()
        self.assertIn("HD_LOCK_DIR=${LOCK_DIR:-${HD_LOCK_DIR:-/tmp}}", src)
