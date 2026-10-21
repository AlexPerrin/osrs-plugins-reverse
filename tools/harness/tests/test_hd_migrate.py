"""v2 tasks -> v3 changes (migrate.py): one change per branch, QA/fix/spec tasks folded, integration-only branches
imported live, acc/fin branches as decisions, deps mapped. A synthetic v2 store; the integration list from files."""
import os
import unittest

import hd_helpers as H

from hd import migrate, store


def task(d, tid, **meta):
    import yaml
    os.makedirs(os.path.join(d, "tasks"), exist_ok=True)
    m = dict({"id": tid, "track": "server", "title": tid, "status": "ready", "prio": "P2", "role": "server-dev",
              "owner": None, "instance": [], "branch": None, "commit": None, "blocked_by": [], "unblocks": [],
              "created": "2026-10-19T00:00:00Z"}, **meta)
    body = m.pop("body", "## Task\nwork\n\n## Definition of done\n- [ ] done\n\n## Log\n- 2026-10-19T00:00:00Z created\n")
    with open(os.path.join(d, "tasks", f"{tid}.md"), "w") as f:
        f.write(f"---\n{yaml.safe_dump(m, sort_keys=False)}---\n{body}")


class MigrateTest(unittest.TestCase):
    def setUp(self):
        d = H.sandbox(self)
        self.h = os.environ["HD_HARNESS_DIR"]
        lst = os.path.join(d, "integration.txt")
        lock = os.path.join(d, "integration.lock")
        with open(lst, "w") as f:
            f.write("# Upstream fixes\nfix/old-fix\nfix/74-bankside\n# Content\nfin/cannon\ncontent/feat\n# Local only\nlocal/android\nlocal/integration-tools\n")
        with open(lock, "w") as f:
            f.write("fix/old-fix 1111111111111111111111111111111111111111\nfix/74-bankside 2222222222222222222222222222222222222222\n"
                    "fin/cannon 3333333333333333333333333333333333333333\ncontent/feat 4444444444444444444444444444444444444444\n"
                    "local/android 5555555555555555555555555555555555555555\n")
        os.environ["HD_INTEGRATION_LIST"], os.environ["HD_INTEGRATION_LOCK"] = lst, lock
        self.addCleanup(os.environ.pop, "HD_INTEGRATION_LIST", None)
        self.addCleanup(os.environ.pop, "HD_INTEGRATION_LOCK", None)
        task(self.h, "feat", status="live", branch="content/feat", commit="4" * 40, pr="https://github.com/AlexPerrin/OpenRune-Server/pull/5")
        task(self.h, "qa-feat", role="server-qa", status="gated", branch="content/feat", commit="4" * 40,
             body="## Task\ngate\n\n## Definition of done\n- [x] d\n\n## Log\n- 2026-10-19T01:00:00Z https://github.com/OpenRune/OpenRune-Server/pull/400\n")
        task(self.h, "fix-bankside-refresh", status="ready", branch="fix/74-bankside", title="Bankside refresh")
        task(self.h, "gate-bankside", role="server-qa", status="blocked", branch="fix/74-bankside", blocked_by=["fix-bankside-refresh"])
        task(self.h, "spec-old-fix", status="ready", title="Spec for old fix")
        task(self.h, "gate-acc-cannon", role="server-qa", status="failed", branch="acc/cannon", commit="9" * 40,
             body="## Task\ng\n\n## Definition of done\n- [x] d\n\n## Log\n- 2026-10-19T02:00:00Z FAIL: lamp xp wrong\n")
        task(self.h, "fix-acc-cannon-text", status="ready", branch="acc/cannon", title="Cannon text fix")
        task(self.h, "content-new-quest", status="backlog", prio="P3")
        task(self.h, "hub-thing", track="client", role="client-dev", status="blocked", blocked_by=["feat"])
        task(self.h, "client-done", track="client", role="client-dev", status="done", branch="client/client-done", commit="7" * 40)
        task(self.h, "wait-x", track="orch", role="orchestrator", status="done")
        task(self.h, "cleanup-refs", track="orch", role="orchestrator", status="ready", title="Cleanup refs")
        task(self.h, "int-20261020-1", track="orch", role="integration", status="done", includes=["feat"], integration="c" * 40, stage="built")

    def test_plan_and_write(self):
        changes, batches, notes = migrate.plan()
        self.assertEqual(changes["feat"]["stage"], "gated")   # the gated QA task was at the live commit
        self.assertEqual(changes["feat"]["gated_commit"], "4" * 40)
        self.assertEqual(changes["feat"]["upstream_pr"], 400)
        self.assertEqual(changes["feat"]["pr"], "https://github.com/AlexPerrin/OpenRune-Server/pull/5")
        self.assertIn("qa-feat", changes["feat"]["folded"])
        b = changes["74-bankside"]
        self.assertEqual(b["stage"], "live")
        self.assertEqual(b["hold"][0], "failed")
        self.assertIn("Bankside refresh", b["hold"][1])
        self.assertIn("gate-bankside", b["folded"])
        o = changes["old-fix"]
        self.assertEqual(o["hold"][0], "failed")
        self.assertIn("spec missing", o["hold"][1])
        c = changes["cannon"]
        self.assertEqual(c["hold"][0], "decision")
        self.assertIn("content/cannon", c["hold"][1])
        self.assertIn("lamp xp wrong", c["hold"][1])   # the gate FAIL is the hold; the fix task is its remedy
        self.assertIn("fix-acc-cannon-text", c["folded"])
        self.assertEqual(changes["content-new-quest"]["stage"], "backlog")
        self.assertEqual(changes["android"]["local_only"], True)
        self.assertEqual(changes["android"]["stage"], "live")
        self.assertEqual(changes["hub-thing"]["deps"], ["feat"])
        self.assertEqual(changes["hub-thing"]["hold"][0], "blocked")
        self.assertEqual(changes["client-done"]["stage"], "live")
        self.assertEqual(changes["cleanup-refs"]["track"], "harness")
        self.assertNotIn("wait-x", changes)
        self.assertNotIn("qa-feat", changes)
        self.assertEqual([b["id"] for b in batches], ["int-20261020-1"])
        n = migrate.write(changes, batches)
        self.assertEqual(n, len(changes))
        ch = store.load("feat")
        self.assertEqual(ch.stage, "gated")
        self.assertEqual(ch["commit"], "4" * 40)
        self.assertIn("migrated from v2 tasks", ch.body)
        self.assertEqual(store.load("cannon").hold, "decision")
        self.assertEqual(store.rec_load("batches", "int-20261020-1")["step"], "live")
        # idempotent
        self.assertEqual(migrate.write(changes, batches), 0)


if __name__ == "__main__":
    unittest.main()
