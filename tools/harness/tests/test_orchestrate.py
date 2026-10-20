"""orchestrate.py reap: approvals for gated tasks, with the upstream check's gh call stubbed; and task.py (lint on the
live task store, read-only; the server DoD template on a temp store)."""
import os
import subprocess
import sys
import unittest

from helpers import HARNESS_TOOLS, main_checkout, read_task, tempdir, write_task

sys.path.insert(0, HARNESS_TOOLS)
import orchestrate  # noqa: E402

SHA = "513144d3dfcfcfd5613123bbe3457b952192c384"


def pr_json(state="open", draft=False, sha=SHA):
    return {"state": state, "draft": draft, "head": {"sha": sha}}


class ReapApprovalTest(unittest.TestCase):
    def setUp(self):
        t = tempdir(self)
        self.harness = os.path.join(t, "harness")
        os.makedirs(os.path.join(self.harness, "tasks"))
        saved = {k: getattr(orchestrate, k) for k in ("HARNESS", "TASKS", "APPROVALS", "RUNS", "ENV", "gh_api")}
        self.addCleanup(lambda: [setattr(orchestrate, k, v) for k, v in saved.items()])
        orchestrate.HARNESS = self.harness
        orchestrate.TASKS = os.path.join(self.harness, "tasks")
        orchestrate.APPROVALS = os.path.join(self.harness, "approvals")
        orchestrate.RUNS = os.path.join(t, "runs")
        orchestrate.ENV = dict(os.environ, HARNESS_DIR=self.harness)
        self.calls = []

    def stub(self, result):
        def api(path):
            self.calls.append(path)
            if isinstance(result, Exception):
                raise result
            return result
        orchestrate.gh_api = api

    def gated(self, tid, **meta):
        write_task(self.harness, tid, track="server", role="server-qa", status="gated", owner=None,
                   branch="fix/stile-approach", commit=SHA, **meta)

    def approval(self, tid):
        return os.path.exists(os.path.join(self.harness, "approvals", f"{tid}.md"))

    def test_ready_upstream_pr_files_no_approval(self):
        self.gated("qa-stile", pr="https://github.com/OpenRune/OpenRune-Server/pull/366")
        self.stub(pr_json())
        orchestrate.cmd_reap(None)
        self.assertEqual(self.calls, ["repos/OpenRune/OpenRune-Server/pulls/366"])
        self.assertFalse(self.approval("qa-stile"))
        _, body = read_task(self.harness, "qa-stile")
        self.assertIn("upstream action already done (PR #366 ready at 513144d3d)", body)
        # A second reap neither asks gh again nor files the approval.
        orchestrate.cmd_reap(None)
        self.assertEqual(len(self.calls), 1)
        self.assertFalse(self.approval("qa-stile"))

    def test_pr_url_in_log(self):
        self.gated("qa-lookout", body_extra="Covers the review on https://github.com/OpenRune/OpenRune-Server/pull/366\n")
        orchestrate.task("log", "qa-lookout", "User OK: opened https://github.com/OpenRune/OpenRune-Server/pull/373, marked ready")
        self.stub(pr_json())
        orchestrate.cmd_reap(None)
        self.assertEqual(self.calls, ["repos/OpenRune/OpenRune-Server/pulls/373"])
        self.assertFalse(self.approval("qa-lookout"))

    def test_files_approval_when_not_done(self):
        cases = {"draft": pr_json(draft=True), "closed": pr_json(state="closed"), "moved": pr_json(sha="f" * 40),
                 "gh-error": RuntimeError("HTTP 502"), "bad-json": {"message": "Not Found"}}
        for name, result in cases.items():
            with self.subTest(name):
                tid = f"qa-{name}"
                self.gated(tid, pr="https://github.com/OpenRune/OpenRune-Server/pull/366")
                self.stub(result)
                orchestrate.cmd_reap(None)
                self.assertTrue(self.approval(tid), name)

    def test_fork_pr_only_files_approval_without_gh(self):
        self.gated("qa-fork", pr="https://github.com/AlexPerrin/OpenRune-Server/pull/21")
        self.stub(pr_json())
        orchestrate.cmd_reap(None)
        self.assertEqual(self.calls, [])
        self.assertTrue(self.approval("qa-fork"))


class TaskStoreTest(unittest.TestCase):
    def task_py(self, *args, harness):
        return subprocess.run([sys.executable, os.path.join(HARNESS_TOOLS, "task.py"), *args], capture_output=True,
                              text=True, env=dict(os.environ, HARNESS_DIR=harness))

    def test_lint_live_store(self):
        harness = os.path.join(main_checkout(), "harness")
        if not os.path.isdir(os.path.join(harness, "tasks")):
            self.skipTest(f"no task store at {harness}")
        r = self.task_py("lint", harness=harness)
        self.assertEqual(r.returncode, 0, r.stdout + r.stderr)
        self.assertIn(", 0 problems", r.stdout)

    def test_server_dod_names_the_checked_spec(self):
        harness = os.path.join(tempdir(self), "harness")
        r = self.task_py("new", "--track", "server", "--id", "fix-stile-approach", "--title", "Stile", "--server-dod",
                         harness=harness)
        self.assertEqual(r.returncode, 0, r.stderr)
        meta, body = read_task(harness, "fix-stile-approach")
        self.assertEqual(meta["spec"], "fix-stile-approach")
        self.assertIn("- [ ] accuracy/fix-stile-approach.md on the fork's docs branch", body)
        r = self.task_py("new", "--track", "server", "--id", "fix-x", "--title", "X", "--server-dod", "--spec", "x-spec",
                         harness=harness)
        meta, body = read_task(harness, "fix-x")
        self.assertEqual(meta["spec"], "x-spec")
        self.assertIn("accuracy/x-spec.md", body)
        self.assertEqual(self.task_py("lint", harness=harness).returncode, 0)


if __name__ == "__main__":
    unittest.main()
