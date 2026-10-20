"""handover-check.sh and the QA path of ship.sh, on a temp task store, a temp client repo and a temp server push
clone whose `fork` and `origin` remotes are local bare repos; gh is a stub script. No network."""
import json
import os
import stat
import subprocess
import unittest

from helpers import (HARNESS_TOOLS, commit_file, git, init_repo, read_task, tempdir, write_task)

TELEMETRY = "".join(f"telemetry: patch {i} OK\n" for i in range(7))


class HandoverCheckTest(unittest.TestCase):
    def setUp(self):
        self.t = tempdir(self)
        self.harness = os.path.join(self.t, "harness")
        os.makedirs(os.path.join(self.harness, "tasks"))
        self.env = dict(os.environ, HARNESS_DIR=self.harness, HANDOVER_EVIDENCE_ROOT=self.t)

    def evidence(self, tid, files):
        d = os.path.join(self.t, "evidence", tid)
        os.makedirs(d, exist_ok=True)
        for name, text in files.items():
            with open(os.path.join(d, name), "w") as f:
                f.write(text)
        return d

    def check(self, tid):
        r = subprocess.run(["bash", os.path.join(HARNESS_TOOLS, "handover-check.sh"), tid], capture_output=True,
                           text=True, env=self.env)
        return r.returncode, r.stdout + r.stderr

    # ---------------------------------------------------------------- client
    def client_repo(self):
        repo = init_repo(os.path.join(self.t, "client"))
        commit_file(repo, "host/A.java", "class A {}\n")
        commit_file(repo, "docs/x.md", "x\n")
        self.env["HANDOVER_REPO"] = repo
        return repo

    def test_client_docs_only_passes_without_repack_log(self):
        repo = self.client_repo()
        git(repo, "checkout", "-q", "-b", "docs/trim")
        sha = commit_file(repo, "docs/x.md", "shorter\n")
        commit_file(repo, "CLAUDE.md", "rules\n")
        sha = git(repo, "rev-parse", "HEAD")
        git(repo, "checkout", "-q", "master")
        write_task(self.harness, "client-docs-trim", branch="docs/trim", commit=sha,
                   evidence=self.evidence("client-docs-trim", {"notes.txt": "docs only\n"}))
        rc, out = self.check("client-docs-trim")
        self.assertEqual(rc, 0, out)
        self.assertIn("docs-only change (2 markdown files", out)

    def test_client_code_change_without_repack_log_fails(self):
        repo = self.client_repo()
        git(repo, "checkout", "-q", "-b", "feat/x")
        sha = commit_file(repo, "host/A.java", "class A { int x; }\n")
        commit_file(repo, "docs/x.md", "notes\n")
        sha = git(repo, "rev-parse", "HEAD")
        git(repo, "checkout", "-q", "master")
        write_task(self.harness, "feat-x", branch="feat/x", commit=sha,
                   evidence=self.evidence("feat-x", {"shot.png": "png"}))
        rc, out = self.check("feat-x")
        self.assertEqual(rc, 1, out)
        self.assertIn("no repack log", out)
        meta, body = read_task(self.harness, "feat-x")
        self.assertIn("handover-check FAIL", body)

    def test_client_code_change_with_evidence_passes(self):
        repo = self.client_repo()
        git(repo, "checkout", "-q", "-b", "feat/y")
        sha = commit_file(repo, "native/hook.cpp", "int y;\n")
        git(repo, "checkout", "-q", "master")
        write_task(self.harness, "feat-y", branch="feat/y", commit=sha, network=True,
                   evidence=self.evidence("feat-y", {"repack.log": TELEMETRY, "logcat-plugin.txt": "x",
                                                     "netqa-run.md": "PASS"}))
        rc, out = self.check("feat-y")
        self.assertEqual(rc, 0, out)

    def test_client_unticked_dod_fails(self):
        repo = self.client_repo()
        sha = git(repo, "rev-parse", "HEAD")
        git(repo, "branch", "feat/z", sha)
        write_task(self.harness, "feat-z", ticked=False, branch="feat/z", commit=sha,
                   evidence=self.evidence("feat-z", {"repack.log": TELEMETRY, "a.png": "x"}))
        rc, out = self.check("feat-z")
        self.assertEqual(rc, 1, out)
        self.assertIn("unticked", out)

    def test_client_qa_on_master_passes_and_ship_closes_it(self):
        repo = self.client_repo()
        sha = git(repo, "rev-parse", "master")
        write_task(self.harness, "netqa-ge-digest", role="client-qa", branch="master", commit=sha,
                   evidence=self.evidence("netqa-ge-digest", {"repack.log": TELEMETRY, "notes.txt": "PASS"}))
        rc, out = self.check("netqa-ge-digest")
        self.assertEqual(rc, 0, out)
        self.assertNotIn("already on master", out)
        self.assertIn("ships nothing", out)
        r = subprocess.run(["bash", os.path.join(HARNESS_TOOLS, "ship.sh"), "netqa-ge-digest"], capture_output=True,
                           text=True, env=self.env)
        self.assertEqual(r.returncode, 0, r.stdout + r.stderr)
        meta, body = read_task(self.harness, "netqa-ge-digest")
        self.assertEqual(meta["status"], "done")
        self.assertIn("nothing to ship", body)

    def test_client_release_gate_goes_to_gated(self):
        repo = self.client_repo()
        sha = git(repo, "rev-parse", "master")
        write_task(self.harness, "release-v9.9.9", role="client-qa", branch="master", commit=sha,
                   evidence=self.evidence("release-v9.9.9", {"repack.log": TELEMETRY, "notes.txt": "PASS"}))
        rc, out = self.check("release-v9.9.9")
        self.assertEqual(rc, 0, out)
        r = subprocess.run(["bash", os.path.join(HARNESS_TOOLS, "ship.sh"), "release-v9.9.9"], capture_output=True,
                           text=True, env=self.env)
        self.assertEqual(r.returncode, 0, r.stdout + r.stderr)
        meta, _ = read_task(self.harness, "release-v9.9.9")
        self.assertEqual(meta["status"], "gated")

    def test_client_qa_network_needs_netqa_report(self):
        repo = self.client_repo()
        sha = git(repo, "rev-parse", "master")
        write_task(self.harness, "netqa-x", role="client-qa", branch="master", commit=sha, network=True,
                   evidence=self.evidence("netqa-x", {"repack.log": TELEMETRY}))
        rc, out = self.check("netqa-x")
        self.assertEqual(rc, 1, out)
        self.assertIn("no netqa", out)

    # ---------------------------------------------------------------- server
    def server_clone(self, specs):
        """A push clone: origin = bare 'upstream' with main, fork = bare 'fork' with main, the task branches at one
        commit on top of main, and a docs branch holding accuracy/<name>.md for each name in specs."""
        up = init_repo(os.path.join(self.t, "up.git"), branch="main", bare=True)
        fork = init_repo(os.path.join(self.t, "fork.git"), branch="main", bare=True)
        work = init_repo(os.path.join(self.t, "work"), branch="main", remotes={"origin": up, "fork": fork})
        commit_file(work, "README", "server\n")
        git(work, "push", "-q", "origin", "main")
        git(work, "push", "-q", "fork", "main")
        sha = commit_file(work, "content/stile.kt", "stile\n")
        for b in ("fix/stile-approach", "fix/b-slug", "fix/c-branch", "fix/d-branch"):
            git(work, "push", "-q", "fork", f"HEAD:refs/heads/{b}")
        git(work, "checkout", "-q", "--orphan", "docs")
        git(work, "rm", "-rq", "--cached", ".")
        commit_file(work, "STATE.md", "state\n")
        for name in specs:
            commit_file(work, f"accuracy/{name}.md", "spec\n")
        git(work, "push", "-q", "fork", "docs")
        clone = init_repo(os.path.join(self.t, "or"), branch="main", remotes={"origin": up, "fork": fork})
        git(clone, "fetch", "-q", "origin")
        git(clone, "fetch", "-q", "fork")
        gh = os.path.join(self.t, "gh")
        with open(gh, "w") as f:
            f.write("#!/bin/sh\necho '" + json.dumps([{"headSha": sha, "conclusion": "success", "workflowName": "CI",
                                                      "url": "https://example.invalid/run/1"}]) + "'\n")
        os.chmod(gh, os.stat(gh).st_mode | stat.S_IEXEC)
        self.env.update(HANDOVER_CLONE=clone, HANDOVER_GH=gh)
        return sha

    def server_task(self, tid, branch, sha, **extra):
        write_task(self.harness, tid, track="server", role="server-dev", branch=branch, commit=sha,
                   pr="https://github.com/AlexPerrin/OpenRune-Server/pull/21",
                   evidence=self.evidence(tid, {"ingame.txt": "17 crossings"}), **extra)

    def test_server_spec_by_key_slug_or_id(self):
        sha = self.server_clone(["custom-spec", "b-slug", "fix-task-c"])
        self.server_task("task-a", "fix/stile-approach", sha, spec="custom-spec")
        self.server_task("task-b", "fix/b-slug", sha)
        self.server_task("fix-task-c", "fix/c-branch", sha)
        for tid, name in (("task-a", "custom-spec"), ("task-b", "b-slug"), ("fix-task-c", "fix-task-c")):
            with self.subTest(tid=tid):
                rc, out = self.check(tid)
                self.assertEqual(rc, 0, out)
                self.assertIn(f"accuracy/{name}.md on docs", out)
                self.assertIn("fork CI success", out)

    def test_server_missing_spec_fails_naming_every_candidate(self):
        sha = self.server_clone(["other"])
        self.server_task("task-d", "fix/d-branch", sha, spec="d-spec")
        rc, out = self.check("task-d")
        self.assertEqual(rc, 1, out)
        self.assertIn("accuracy/{d-spec,d-branch,task-d}.md", out)

    def test_server_qa_task_ships_nothing(self):
        sha = self.server_clone([])
        write_task(self.harness, "qa-x", track="server", role="server-qa", branch="fix/d-branch", commit=sha,
                   evidence=self.evidence("qa-x", {"record.md": "PASS"}))
        rc, out = self.check("qa-x")
        self.assertEqual(rc, 0, out)

    def test_server_qa_gate_pass_goes_to_gated_not_done(self):
        sha = self.server_clone([])
        write_task(self.harness, "qa-g", track="server", role="server-qa", branch="fix/d-branch", commit=sha,
                   evidence=self.evidence("qa-g", {"record.md": "PASS"}))
        rc, out = self.check("qa-g")
        self.assertEqual(rc, 0, out)
        r = subprocess.run(["bash", os.path.join(HARNESS_TOOLS, "ship.sh"), "qa-g"], capture_output=True,
                           text=True, env=self.env)
        self.assertEqual(r.returncode, 0, r.stdout + r.stderr)
        meta, body = read_task(self.harness, "qa-g")
        self.assertEqual(meta["status"], "gated")
        self.assertIn("waiting for the user's approval", body)


if __name__ == "__main__":
    unittest.main()
