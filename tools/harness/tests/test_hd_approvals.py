"""hd approvals (BR-A1..A8): created only when gated, the exact commands and the full text, stale detection,
idempotence, execution through a stub gh, deny -> decision hold, render."""
import os
import unittest

import hd_helpers as H
from hd_helpers import SHA, SHB

from hd import approvals, events, store
from hd.observe import FakeObserver

RECORD = [("qa/feat.md", "- Branch: `content/feat`\n- Commit: `" + SHA[:9] + "`\n## Upstream gate: **PASS**\n")]
DOCS = {"pr-texts/feat.title": "Add the feat\n",
        "pr-texts/feat.upstream.body": "## Summary\nThe feat.\n\n## Caveats\n- drop rate from the wiki\n- no RSProx\n\n## Testing\n- ok\n"}
UPSTREAM = "OpenRune/OpenRune-Server"


class ApprovalsTest(unittest.TestCase):
    def setUp(self):
        self.d = H.sandbox(self)

    def obs(self, pr=None, docs=DOCS):
        prs = {} if pr is None else {"content/feat": pr, 354: pr}
        return FakeObserver(upstream_prs=prs, docs=docs, records=RECORD, merge_base={("fork", "refs/remotes/origin/main", SHA): "m" * 40})

    def gated(self, **kw):
        return H.change("feat", "server", stage="gated", commit=SHA, summary="the feat", **kw)

    def test_only_when_gated(self):
        for stage in ("ready", "dev", "checked", "live", "published"):
            ch = H.change(f"c-{stage}", "server", stage=stage, commit=SHA)
            self.assertIsNone(approvals.ensure(ch, self.obs()))
        self.assertEqual(approvals.all_(), [])

    def test_open_pr(self):
        a = approvals.ensure(self.gated(), self.obs())
        self.assertEqual((a["kind"], a["action"], a["state"], a["change"]), ("upstream", "open-pr", "pending", "feat"))
        self.assertEqual(a["id"], f"feat-open-pr-{SHA[:7]}")
        cmd = a["commands"][0]
        self.assertEqual(cmd[:9], ["gh", "pr", "create", "-R", UPSTREAM, "--base", "main", "--head", "AlexPerrin:content/feat"])
        self.assertEqual(cmd[9:11], ["--title", "Add the feat"])
        self.assertEqual(cmd[11], "--body-file")
        with open(cmd[12]) as f:
            self.assertEqual(f.read(), DOCS["pr-texts/feat.upstream.body"])
        self.assertEqual(a["text"]["body"], DOCS["pr-texts/feat.upstream.body"])
        self.assertEqual(a["caveats"], ["drop rate from the wiki", "no RSProx"])
        self.assertEqual(a["record"], "qa/feat.md")
        self.assertEqual(a["diff"], "m" * 40 + ".." + SHA)
        ev = events.read(kinds=["approval"])[-1]
        self.assertEqual((ev["action"], ev["approval"], ev["what"]), ("created", a["id"], "open-pr"))
        # idempotent
        b = approvals.ensure(store.load("feat"), self.obs())
        self.assertEqual(b["id"], a["id"])
        self.assertEqual(len(approvals.all_()), 1)
        self.assertEqual(len(approvals.pending()), 1)

    def test_open_pr_text_missing(self):
        a = approvals.ensure(self.gated(), self.obs(docs={}))
        self.assertEqual(a["commands"], [])
        self.assertEqual(a["note"], "PR text missing on docs: pr-texts/feat.*")
        self.assertEqual(a["state"], "pending")
        self.assertIn("nothing can run", approvals.render(a))

    def test_draft_at_commit_is_ready_pr(self):
        pr = {"number": 354, "url": f"https://github.com/{UPSTREAM}/pull/354", "state": "open", "draft": True, "head": SHA}
        a = approvals.ensure(self.gated(), self.obs(pr))
        self.assertEqual(a["action"], "ready-pr")
        self.assertEqual(a["commands"], [["gh", "pr", "ready", "354", "-R", UPSTREAM]])
        self.assertEqual(a["pr"], 354)

    def test_head_behind_is_update_pr(self):
        pr = {"number": 354, "state": "open", "draft": False, "head": SHB}
        a = approvals.ensure(self.gated(upstream_pr=354), self.obs(pr))
        self.assertEqual(a["action"], "update-pr")
        push, comment = a["commands"]
        self.assertEqual(push[0:2] + push[3:], ["git", "-C", "push", "fork", f"{SHA}:refs/heads/content/feat"])
        self.assertTrue(push[2].endswith("re/upstream-pr/or"))
        self.assertEqual(comment[:6], ["gh", "pr", "comment", "354", "-R", UPSTREAM])
        self.assertEqual(comment[6], "--body-file")
        with open(comment[7]) as f:
            self.assertEqual(f.read().strip(), f"Updated to {SHA}: the feat")
        self.assertFalse(any("edit" in c for cmd in a["commands"] for c in cmd))   # BR-A8
        # an update note on docs wins
        docs = dict(DOCS, **{"pr-texts/feat.update.md": "Round 2: fixed the rate.\n"})
        store.rec_delete(approvals.APPROVALS, a["id"])
        a = approvals.ensure(store.load("feat"), self.obs(pr, docs))
        self.assertEqual(a["text"]["comment"], "Round 2: fixed the rate.\n")

    def test_already_done_is_stale(self):
        """BR-A5: GitHub shows the action done -> no approval, and a pending one goes stale."""
        a = approvals.ensure(self.gated(), self.obs())
        self.assertEqual(a["state"], "pending")
        pr = {"number": 354, "state": "open", "draft": False, "head": SHA}
        self.assertIsNone(approvals.ensure(store.load("feat"), self.obs(pr)))
        self.assertEqual(store.rec_load(approvals.APPROVALS, a["id"])["state"], "stale")
        self.assertEqual(approvals.pending(), [])
        self.assertEqual(events.read(kinds=["approval"])[-1]["action"], "stale")
        # a pending open-pr is replaced by the ready-pr the draft needs
        b = approvals.ensure(H.change("two", "server", stage="gated", commit=SHA), self.obs())
        self.assertEqual(b["action"], "open-pr")
        prs = {"content/two": {"number": 360, "state": "open", "draft": True, "head": SHA}}
        c = approvals.ensure(store.load("two"), FakeObserver(upstream_prs=prs, docs=DOCS, records=RECORD))
        self.assertEqual(c["action"], "ready-pr")
        self.assertEqual(store.rec_load(approvals.APPROVALS, b["id"])["state"], "stale")

    def test_approve_execute(self):
        gh = H.stub(self.d, "gh", 'echo "$@" >> "$(dirname "$0")/gh.log"\necho "https://github.com/OpenRune/OpenRune-Server/pull/360"\n')
        H.env(self, HD_GH=gh)
        a = approvals.ensure(self.gated(), self.obs())
        self.assertEqual(approvals.execute(self.obs()), [])   # pending: nothing runs
        approvals.approve(a["id"], "go")
        self.assertEqual(store.rec_load(approvals.APPROVALS, a["id"])["state"], "approved")
        self.assertEqual(events.read(kinds=["approval"])[-1]["action"], "approved")
        with self.assertRaises(ValueError):
            approvals.approve(a["id"])
        touched = approvals.execute(self.obs())
        self.assertEqual([(t["id"], t["state"]) for t in touched], [(a["id"], "done")])
        self.assertEqual(touched[0]["result"], "https://github.com/OpenRune/OpenRune-Server/pull/360")
        with open(os.path.join(self.d, "gh.log")) as f:
            self.assertIn("pr create -R OpenRune/OpenRune-Server --base main --head AlexPerrin:content/feat --title Add the feat --body-file", f.read())
        ch = store.load("feat")
        self.assertEqual(ch["upstream_pr"], 360)
        self.assertIn("approval feat-open-pr-aaaaaaa executed (open-pr): https://github.com/OpenRune/OpenRune-Server/pull/360", ch.body)
        self.assertEqual(events.read(kinds=["approval"])[-1]["action"], "done")
        self.assertEqual(approvals.execute(self.obs()), [])   # once

    def test_execute_failure(self):
        H.env(self, HD_GH=H.stub(self.d, "gh", 'echo "boom" >&2\nexit 1\n'))
        a = approvals.ensure(self.gated(), self.obs())
        approvals.approve(a["id"])
        t = approvals.execute(self.obs())[0]
        self.assertEqual(t["state"], "failed")
        self.assertIn("boom", t["result"])
        self.assertIsNone(store.load("feat").get("upstream_pr"))

    def test_execute_overtaken_is_stale(self):
        H.env(self, HD_GH=H.stub(self.d, "gh", 'echo should-not-run > "$(dirname "$0")/ran"\n'))
        a = approvals.ensure(self.gated(), self.obs())
        approvals.approve(a["id"])
        pr = {"number": 354, "state": "open", "draft": False, "head": SHA}
        t = approvals.execute(self.obs(pr))[0]
        self.assertEqual(t["state"], "stale")
        self.assertFalse(os.path.exists(os.path.join(self.d, "ran")))

    def test_deny_holds_decision(self):
        a = approvals.ensure(self.gated(), self.obs())
        approvals.deny(a["id"], "wait for the maintainer's answer first")
        t = approvals.execute(self.obs())[0]
        self.assertEqual(t["state"], "denied")
        ch = store.load("feat")
        self.assertEqual(ch.hold, "decision")
        self.assertIn("wait for the maintainer's answer first", ch.hold_reason)
        self.assertEqual(approvals.execute(self.obs()), [])
        self.assertIsNone(approvals.ensure(store.load("feat"), self.obs()))   # never re-asked

    def test_decision_and_release(self):
        ch = H.change("plug", "client", stage="dev")
        a = approvals.decision(ch, "Keep the hub plugin or the built-in?", ["hub", "built-in"], by="cd48")
        self.assertEqual((a["kind"], a["by"], a["text"]["options"]), ("decision", "cd48", ["hub", "built-in"]))
        self.assertEqual(store.load("plug").hold, "decision")
        r = approvals.render(a)
        self.assertIn("question: Keep the hub plugin or the built-in?", r)
        self.assertIn("2. built-in", r)
        batch = {"id": "rel-1", "kind": "release", "step": "gated", "includes": ["plug", "other"], "commit": SHA}
        b = approvals.ensure_release(batch)
        self.assertEqual((b["kind"], b["action"], b["batch"]), ("release", "tag-release", "rel-1"))
        self.assertEqual(b["commands"][0], ["git", "tag", "-a", "v0.x.y", SHA, "-m", "v0.x.y: 2 changes"])
        self.assertEqual(b["commands"][1][:6], ["gh", "release", "create", "v0.x.y", "--repo", "AlexPerrin/osrs-plugins-reverse"])
        self.assertIn("placeholder", b["note"])
        self.assertEqual(approvals.ensure_release(batch)["id"], b["id"])
        with open(os.path.join(os.environ["HD_STATE_DIR"], "release.request"), "w") as f:
            f.write('{"tag": "v0.9.0"}')
        c = approvals.ensure_release(batch)
        self.assertEqual(c["commands"][0][3], "v0.9.0")
        self.assertIsNone(c["note"])

    def test_render(self):
        a = approvals.ensure(self.gated(), self.obs())
        r = approvals.render(a)
        self.assertIn("[pending] feat-open-pr-aaaaaaa: upstream open-pr for change feat", r)
        self.assertIn("$ gh pr create -R OpenRune/OpenRune-Server --base main --head AlexPerrin:content/feat --title 'Add the feat' --body-file", r)
        self.assertIn("gate record: qa/feat.md", r)
        self.assertIn("    - drop rate from the wiki", r)
        self.assertIn("    ## Caveats", r)
        self.assertRegex(r, r"created \d{1,2}:\d\d [AP]M E[DS]T \d\d-\d\d")


if __name__ == "__main__":
    unittest.main()
