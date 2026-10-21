"""hd.dashboard: the view model, the page and the terminal text (PRD section 9), offline on a sandbox."""
import json
import os
import re
import unittest

import hd_helpers as H
from hd_helpers import SHA

from hd import dashboard, events, store, util
from hd.observe import FakeObserver


def at(ts, kind, **f):
    util.freeze(ts)
    return events.emit(kind, **f)


class DashboardTest(unittest.TestCase):
    def setUp(self):
        H.sandbox(self)
        self.addCleanup(util.freeze, None)
        util.freeze("2026-10-20T20:00:00Z")
        H.change("held", track="server", stage="dev", prio="P3", commit=SHA, owner="sd1",
                 hold={"kind": "failed", "reason": "spotless", "since": "2026-10-20T19:00:00Z"})
        H.change("urgent", track="client", stage="ready", prio="P0")
        H.change("old", track="client", stage="ready", prio="P1")
        H.change("newer", track="client", stage="ready", prio="P1")
        H.change("shipped", track="client", stage="live", prio="P2", commit=SHA)   # terminal, 3 days old -> done
        at("2026-10-17T10:00:00Z", "stage", change="shipped", **{"from": "checked", "to": "live"})
        at("2026-10-20T10:00:00Z", "stage", change="old", **{"from": "backlog", "to": "ready"})
        at("2026-10-20T18:00:00Z", "stage", change="newer", **{"from": "backlog", "to": "ready"})
        at("2026-10-13T00:00:00Z", "metric", name="RuneLite API implemented", value=600, total=866, pct=69.3)
        at("2026-10-20T19:00:00Z", "metric", name="RuneLite API implemented", value=661, total=866, pct=76.3)
        at("2026-10-20T19:00:00Z", "metric", name="Plugins running on Android", value=89, total=90, pct=98.9)
        at("2026-10-20T19:00:00Z", "note", by="upstream", text="3 PRs open")
        store.rec_save("approvals", "ap1", {"kind": "upstream", "change": "held", "action": "open-pr",
                                            "commands": [["gh", "pr", "create", "--title", "x y"]],
                                            "text": {"title": "T", "body": "B"}, "state": "pending",
                                            "created": "2026-10-20T19:30:00Z", "caveats": ["one"]})
        store.rec_save("approvals", "ap0", {"kind": "release", "state": "done", "created": "2026-10-19T19:30:00Z",
                                            "decided": "2026-10-19T20:00:00Z"})
        store.rec_save("batches", "t1", {"kind": "train", "step": "gating", "includes": ["held"], "integration": SHA,
                                         "created": "2026-10-20T19:40:00Z"})
        store.rec_save("batches", "t0", {"kind": "train", "step": "live"})
        store.rec_save("runs", "sd1", {"role": "server-dev", "change": "held", "stage": "dev", "pid": os.getpid(),
                                       "started": "2026-10-20T18:00:00Z"})
        store.rec_save("runs", "sd0", {"role": "server-dev", "change": "x", "ended": "2026-10-20T18:00:00Z"})
        store.set_live({"integration": SHA, "image": "osrs-openrune:241", "rollback": "rollback-e5b18a8",
                        "since": "2026-10-20T15:00:00Z", "upstream_main": "c" * 40})
        util.freeze("2026-10-20T20:00:00Z")
        self.v = dashboard.view(FakeObserver(upstream_main="d" * 40))

    def test_rows_sorted_holds_prio_age(self):
        slugs = [r["slug"] for r in self.v["changes"]]
        self.assertEqual(slugs, ["held", "urgent", "old", "newer"])
        self.assertEqual(self.v["done"], 1)
        held = self.v["changes"][0]
        self.assertEqual((held["hold"], held["reason"], held["owner"]), ("failed", "spotless", "sd1"))
        old = self.v["changes"][2]
        self.assertEqual(old["age"], "10.0 h")
        self.assertEqual(old["since"], "2026-10-20T10:00:00Z")
        self.assertEqual(old["waiting"], "a free slot and lease")
        self.assertEqual(held["waiting"], "the hand-over check")

    def test_waiting_empty_without_observer(self):
        v = dashboard.view()
        self.assertEqual({r["waiting"] for r in v["changes"]}, {""})

    def test_progress_from_metrics(self):
        api = self.v["progress"]["client"]["api"]
        self.assertEqual((api["value"], api["total"], api["pct"]), (661, 866, 76.3))
        self.assertEqual(api["delta_week"], 7.0)
        self.assertEqual(len(api["trend"]), 2)
        self.assertEqual(self.v["progress"]["client"]["packets"]["note"], "no recount yet")
        self.assertIsNone(self.v["progress"]["server"]["parity_live"])
        self.assertEqual(self.v["progress"]["upstream"]["note"], "3 PRs open")
        self.assertEqual(self.v["progress"]["rate"]["metric_delta_week"]["api"], 7.0)
        self.assertEqual(len(self.v["progress"]["trends"]["live"]), 14)

    def test_status_sections(self):
        self.assertEqual([a["id"] for a in self.v["approvals"]], ["ap1", "ap0"])
        self.assertEqual(self.v["pending"], 1)
        self.assertEqual([b["id"] for b in self.v["batches"]], ["t1"])
        self.assertEqual([a["name"] for a in self.v["agents"]], ["sd1"])
        self.assertTrue(self.v["agents"][0]["alive"])
        self.assertTrue(self.v["live"]["repin_due"])
        self.assertEqual(self.v["live"]["upstream_tip"], "d" * 40)
        self.assertEqual(self.v["resources"]["buildq"], "n/a")
        self.assertIn("device", [r["resource"] for r in self.v["resources"]["leases"]])
        self.assertEqual(self.v["stalls"][0]["slug"], "urgent")   # client-dev has 1 slot, none used
        self.assertEqual(len(self.v["stalls"]), 3)
        self.assertFalse(self.v["daemon"]["alive"])
        self.assertEqual(self.v["generated"], "2026-10-20T20:00:00Z")
        self.assertEqual(self.v["generated_local"], "4:00 PM EDT 10-20")
        json.dumps(self.v)   # serialisable

    def test_html(self):
        h = dashboard.html(self.v)
        self.assertIn("<title>harness (1)</title>", h)
        self.assertIn('<script id="data" type="application/json">', h)
        data = json.loads(re.search(r'<script id="data" type="application/json">(.*?)</script>', h, re.S).group(1))
        self.assertEqual(data["pending"], 1)
        self.assertIn("/api/approve", h)
        self.assertIn("/api/deny", h)
        self.assertIn("Approve</button>", h)
        self.assertNotIn("http://", h)
        self.assertNotIn("https://", h)
        self.assertIn("prefers-color-scheme", h)
        self.assertIn("America/New_York", h)
        self.assertIn("/events?kind=", h)
        self.assertLess(h.count("\n"), 500)
        self.v["pending"] = 0
        self.assertIn("<title>harness</title>", dashboard.html(self.v))

    def test_text(self):
        t = dashboard.text(self.v)
        self.assertIn("RuneLite API implemented", t)
        self.assertIn("661/866", t)
        self.assertIn("not measured yet", t)
        self.assertIn("[pending] ap1", t)
        self.assertIn("$ gh pr create --title x y", t)
        self.assertIn("held", t)
        self.assertIn("spotless", t)
        self.assertIn("RE-PIN DUE", t)
        self.assertIn("unattended hours", t)
        self.assertIn("not running", t)


if __name__ == "__main__":
    unittest.main()
