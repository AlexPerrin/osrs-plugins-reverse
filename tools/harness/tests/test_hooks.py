"""deny_publish.py (the role guard), plus the shared shell parser, github_scope.py and publish_gate.py where they
share its target resolution. Temp repos whose remotes point at the fork or at OpenRune; no network."""
import json
import os
import subprocess
import sys
import unittest

from helpers import (FORK_SSH, FORK_URL, HOOKS, OURS_URL, UPSTREAM_URL, init_repo, tempdir)

sys.path.insert(0, HOOKS)
import deny_publish  # noqa: E402
import github_scope  # noqa: E402
import shellparse  # noqa: E402

FORK, DOCS, NONE = ["--fork-writes"], ["--docs-writes"], []
ALLOW, DENY = True, False


class DenyPublishTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        import tempfile
        cls._tmp = tempfile.TemporaryDirectory(prefix="harness-hooks-")
        t = os.path.realpath(cls._tmp.name)
        # re/upstream-pr/or: origin IS OpenRune, fork is ours.
        cls.upr = init_repo(os.path.join(t, "upstream-pr", "or"), remotes={"origin": UPSTREAM_URL, "fork": FORK_SSH})
        # re/agents/<name>/or and docs clones: origin is the fork.
        cls.agent = init_repo(os.path.join(t, "agents", "sd1", "or"), remotes={"origin": FORK_URL})
        cls.docs = init_repo(os.path.join(t, "agents", "sq1", "docs"), remotes={"origin": FORK_URL})
        cls.ours = init_repo(os.path.join(t, "main"), remotes={"origin": OURS_URL})
        # The real agent clones: origin is the local push clone (re/upstream-pr/or), fork is the fork.
        cls.local = init_repo(os.path.join(t, "agents", "sd2", "or"), remotes={"origin": cls.upr, "fork": FORK_SSH})
        cls._env = os.environ.get("HOOK_PUSH_CLONE")
        os.environ["HOOK_PUSH_CLONE"] = cls.upr

    @classmethod
    def tearDownClass(cls):
        cls._tmp.cleanup()
        if cls._env is None:
            os.environ.pop("HOOK_PUSH_CLONE", None)
        else:
            os.environ["HOOK_PUSH_CLONE"] = cls._env

    def decide(self, cmd, cwd, flags):
        cmd = cmd.format(upr=self.upr, agent=self.agent, docs=self.docs, ours=self.ours, local=self.local)
        return deny_publish.decide({"tool_input": {"command": cmd}, "cwd": getattr(self, cwd)}, flags)

    CASES = [
        # (command, cwd, flags, expected)
        # --- the legitimate fork actions the old bare-word `upstream` rule denied
        ("tools/fork.sh pr-edit 21 pr-texts/stile-approach.upstream.body", "agent", FORK, ALLOW),
        ('git commit -m "qa: upstream gate FAIL" && git push origin x:docs', "docs", DOCS, ALLOW),
        ("git -C {upr} push fork fix/x", "ours", FORK, ALLOW),
        # --- fork pushes, resolved through the remote URL
        ("git -C {upr} push fork fix/x 2>&1 | tail -n 3", "ours", FORK, ALLOW),
        ("cd {upr} && git push fork fix/x", "ours", FORK, ALLOW),
        ("cd {upr}; git push -u fork HEAD:refs/heads/fix/x", "ours", FORK, ALLOW),
        ("git push origin fix/x", "agent", FORK, ALLOW),
        ("git push --force-with-lease origin fix/x", "agent", FORK, ALLOW),
        ('git commit -m "address upstream review" && git push origin fix/x', "agent", FORK, ALLOW),
        ("git push " + FORK_SSH + " fix/x", "ours", FORK, ALLOW),
        ("git -C {upr} push -o ci.skip fork fix/x >/dev/null 2>&1", "ours", FORK, ALLOW),
        # --- agent clones whose origin is the local push clone (a folder, not GitHub)
        ("git push origin docs", "local", FORK, ALLOW),
        ("git push origin fix/x", "local", DOCS, DENY),
        ("git push origin docs", "local", DOCS, ALLOW),
        ("git push fork fix/x", "local", FORK, ALLOW),
        ("git push upstream fix/x", "local", FORK, DENY),
        # --- pushes to OpenRune upstream, or targets the hook can't tell
        ("git push origin fix/x", "upr", FORK, DENY),
        ("git -C {upr} push origin fix/x", "agent", FORK, DENY),
        ("cd {upr} && git push origin fix/x", "agent", FORK, DENY),
        ("cd {agent} && git -C {upr} push origin fix/x", "agent", FORK, DENY),
        ("git push", "agent", FORK, DENY),
        ("git push --set-upstream", "agent", FORK, DENY),
        ("git push " + UPSTREAM_URL + " fix/x", "agent", FORK, DENY),
        ("git push nosuchremote fix/x", "agent", FORK, DENY),
        ("git -C {upr} push fork main", "ours", FORK, DENY),
        ("git push --mirror origin", "agent", FORK, DENY),
        ('bash -c "git push origin fix/x"', "agent", FORK, DENY),
        ("echo $(git push origin fix/x)", "agent", FORK, DENY),
        ("git push origin fix/x && git -C {upr} push origin fix/x", "agent", FORK, DENY),
        # --- gh writes
        ("gh pr edit 362 -R OpenRune/OpenRune-Server --body-file b", "agent", FORK, DENY),
        ("gh api -X PATCH repos/OpenRune/OpenRune-Server/pulls/362 -F body=@b", "agent", FORK, DENY),
        ("gh pr comment 362 --repo https://github.com/OpenRune/OpenRune-Server -b hi", "agent", FORK, DENY),
        ("gh api -X PATCH repos/AlexPerrin/OpenRune-Server/pulls/21 -F body=@pr-texts/x.upstream.body", "agent", FORK, ALLOW),
        ("gh pr create -R AlexPerrin/OpenRune-Server --base main --head fix/x --title upstream-ready", "agent", FORK, ALLOW),
        ("gh pr edit 21 --body-file b", "agent", FORK, DENY),   # target not named
        ("gh pr comment 21 -R AlexPerrin/OpenRune-Server -b x && gh pr comment 362 -R OpenRune/OpenRune-Server -b x",
         "agent", FORK, DENY),
        # --- fork.sh is fork-only by construction, unless the command names OpenRune upstream
        ("tools/fork.sh push fix/x --force-with-lease", "ours", FORK, ALLOW),
        ("tools/fork.sh pr-comment 21 notes/upstream-review.md", "ours", FORK, ALLOW),
        ("tools/fork.sh pr-create fix/x t OpenRune/OpenRune-Server", "ours", FORK, DENY),
        ("tools/fork.sh push fix/x", "ours", NONE, DENY),
        ("tools/fork.sh push fix/x", "ours", DOCS, DENY),
        # --- --docs-writes: a non-forced, single refspec onto docs, remote resolving to the fork
        ("git push origin HEAD:refs/heads/docs", "docs", DOCS, ALLOW),
        ("git push origin docs", "docs", DOCS, ALLOW),
        ("git -C {upr} push fork local-docs:docs", "ours", DOCS, ALLOW),
        ("git push --force origin x:docs", "docs", DOCS, DENY),
        ("git push -f origin x:docs", "docs", DOCS, DENY),
        ("git push origin +x:docs", "docs", DOCS, DENY),
        ("git push origin fix/x", "docs", DOCS, DENY),
        ("git push origin x:docs y:docs2", "docs", DOCS, DENY),
        ("git push origin x:docs", "upr", DOCS, DENY),
        ("git push", "docs", DOCS, DENY),
        ("gh pr comment 21 -R AlexPerrin/OpenRune-Server -b x", "docs", DOCS, DENY),
        # --- client roles (no flags): no pushes or GitHub writes at all
        ("git push origin x", "agent", NONE, DENY),
        ("git -C {upr} push fork fix/x", "ours", NONE, DENY),
        ("tools/fork.sh pr-edit 21 b", "ours", NONE, DENY),
        ("gh pr edit 21 -R AlexPerrin/OpenRune-Server --body-file b", "ours", NONE, DENY),
        ("git push origin master:main", "ours", NONE, DENY),
        ("git stash push -m wip", "ours", NONE, ALLOW),
        ("git status && git log --oneline -3", "ours", NONE, ALLOW),
        ("gh pr view 362 -R OpenRune/OpenRune-Server", "ours", NONE, ALLOW),
        ("grep -rn upstream docs/", "ours", NONE, ALLOW),
        # --- orchestrator-only actions, whatever the flags
        ("openrune/upstream/openrune.sh deploy abc123", "ours", FORK, DENY),
        ("docker compose --env-file switch.env up -d", "ours", DOCS, DENY),
        ("LOCK_NAME=device tools/device-lock.sh grant cd1 60 reuse", "ours", FORK, DENY),
        ("python3 tools/progress.py", "ours", NONE, DENY),
        ("python3 tools/progress.py --dry-run", "ours", FORK, ALLOW),
    ]

    def test_table(self):
        for cmd, cwd, flags, expected in self.CASES:
            with self.subTest(cmd=cmd, cwd=cwd, flags=flags):
                reason = self.decide(cmd, cwd, flags)
                self.assertEqual(reason is None, expected, f"{'denied' if reason else 'allowed'}: {reason}")

    def test_reason_names_the_target(self):
        reason = self.decide("git push origin fix/x", "upr", FORK)
        self.assertIn("OpenRune/OpenRune-Server", reason)

    def test_hook_cli(self):
        """The hook as the agents run it: JSON on stdin, a deny decision or nothing on stdout."""
        def run(cmd, flags):
            data = json.dumps({"tool_name": "Bash", "tool_input": {"command": cmd}, "cwd": self.agent})
            return subprocess.run([sys.executable, os.path.join(HOOKS, "deny_publish.py"), *flags], input=data,
                                  capture_output=True, text=True).stdout
        self.assertEqual(run("git push origin fix/x", FORK), "")
        self.assertIn('"deny"', run("git push origin fix/x", NONE))


class ShellParseTest(unittest.TestCase):
    def test_segments_keep_redirections(self):
        self.assertEqual(shellparse.segments("git push fork x 2>&1 | tail -3 && echo ok"),
                         ["git push fork x 2>&1", "tail -3", "echo ok"])
        self.assertEqual(shellparse.segments("a &>/dev/null; b"), ["a &>/dev/null", "b"])

    def test_words_drop_redirections_and_assignments(self):
        self.assertEqual(shellparse.words("FOO=1 git push fork x 2> /dev/null >out"), ["git", "push", "fork", "x"])

    def test_parse_push(self):
        p = shellparse.parse_push(["git", "-C", "/r", "-c", "a=b", "push", "-o", "x", "--force", "fork", "a:b"], "/")
        self.assertEqual((p["dir"], p["remote"], p["refspecs"]), ("/r", "fork", ["a:b"]))
        self.assertTrue(shellparse.forced(p))
        self.assertIsNone(shellparse.parse_push(["git", "stash", "push"], "/"))

    def test_upstream_name(self):
        self.assertTrue(shellparse.UPSTREAM_NAME.search("gh pr view -R OpenRune/OpenRune-Server"))
        self.assertTrue(shellparse.UPSTREAM_NAME.search("https://github.com/OpenRune/x"))
        self.assertFalse(shellparse.UPSTREAM_NAME.search("AlexPerrin/OpenRune-Server upstream re/upstream-pr/or"))


class GithubScopeTest(unittest.TestCase):
    def test_push_targets(self):
        t = tempdir(self)
        upr = init_repo(os.path.join(t, "or"), remotes={"origin": UPSTREAM_URL, "fork": FORK_SSH})
        self.assertEqual(github_scope.classify("git push origin fix/x 2>&1", upr)[0], "upstream")
        self.assertEqual(github_scope.classify("git push", upr)[0], "unknown")
        self.assertEqual(github_scope.classify("git status", upr)[0], "read")

    def run_scope(self, cmd, cwd):
        import subprocess
        data = json.dumps({"tool_name": "Bash", "tool_input": {"command": cmd}, "cwd": cwd})
        r = subprocess.run([sys.executable, os.path.join(HOOKS, "github_scope.py")], input=data, capture_output=True,
                           text=True)
        return json.loads(r.stdout)["hookSpecificOutput"]["permissionDecision"] if r.stdout.strip() else None

    def test_variables_assigned_in_the_command(self):
        # The command that made the user approve a fork push (2026-10-20): `wt=...` then `git -C $wt push fork ...`.
        t = tempdir(self)
        os.makedirs(os.path.join(t, "re", "harness"))
        init_repo(os.path.join(t, "re", "harness", "integration-tools"), remotes={"fork": FORK_SSH})
        upr = init_repo(os.path.join(t, "or"), remotes={"origin": UPSTREAM_URL, "fork": FORK_SSH})
        self.assertEqual(self.run_scope("wt=re/harness/integration-tools && git -C $wt commit -q -m x -- a && "
                                        "git -C $wt push -q fork local/integration-tools:local/integration-tools", t),
                         None)   # no prompt (mixed command: the normal permission flow)
        self.assertEqual(self.run_scope("wt=re/harness/integration-tools && "
                                        "git -C $wt push -q fork local/integration-tools:local/integration-tools", t),
                         "allow")
        self.assertEqual(self.run_scope('d="' + upr + '"; git -C "${d}" push origin fix/x', t), "ask")

    def test_command_substitution_in_an_option(self):
        # The second fork push that prompted (2026-10-20): the lease value comes from $(git -C <dir> rev-parse ...).
        t = tempdir(self)
        os.makedirs(os.path.join(t, "re", "harness"))
        init_repo(os.path.join(t, "re", "harness", "integration"), remotes={"fork": FORK_SSH})
        upr = init_repo(os.path.join(t, "or"), remotes={"origin": UPSTREAM_URL, "fork": FORK_SSH})
        cmd = ("git -C re/harness/integration push -q --force-with-lease=integration:$(git -C re/harness/integration "
               "rev-parse fork/integration) fork HEAD:refs/heads/integration")
        self.assertEqual(self.run_scope(cmd, t), "allow")
        self.assertEqual(self.run_scope("git -C " + upr + " push --force-with-lease=x:$(git rev-parse y) origin x", t),
                         "ask")
        self.assertEqual(self.run_scope("echo $(git -C " + upr + " push origin x)", t), "ask")


class PublishGateTest(unittest.TestCase):
    def run_gate(self, cmd):
        t = tempdir(self)
        data = json.dumps({"tool_name": "Bash", "tool_input": {"command": cmd}})
        return subprocess.run([sys.executable, os.path.join(HOOKS, "publish_gate.py")], input=data, capture_output=True,
                              text=True, env=dict(os.environ, CLAUDE_PROJECT_DIR=t)).stdout

    def test_fork_pr_not_gated(self):
        self.assertEqual(self.run_gate("gh pr create -R AlexPerrin/OpenRune-Server --head AlexPerrin:fix/x --title t"), "")
        self.assertEqual(self.run_gate("gh pr create --title 'upstream gate notes' --body-file re/upstream-pr/b"), "")

    def test_upstream_pr_gated(self):
        self.assertIn('"deny"', self.run_gate("gh pr create -R OpenRune/OpenRune-Server --head AlexPerrin:fix/x"))
        self.assertIn('"deny"', self.run_gate("gh pr create --head AlexPerrin:fix/x --title t"))


if __name__ == "__main__":
    unittest.main()
