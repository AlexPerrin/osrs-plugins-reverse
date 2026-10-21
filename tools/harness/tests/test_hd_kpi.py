"""hd.kpi: every KPI from a synthetic events.jsonl (problem 5, handoff 6a-kpi)."""
import os
import unittest

import hd_helpers as H

from hd import events, kpi, util


def at(ts, kind, **f):
    util.freeze(ts)
    return events.emit(kind, **f)


class KpiTest(unittest.TestCase):
    def setUp(self):
        H.sandbox(self)
        self.addCleanup(util.freeze, None)
        # ticks at 00:00, 00:01, 00:02 (2 unattended min); a user note; 00:10, 00:11 (1 min); a gap > 2*interval;
        # 01:00, 01:01 (1 min)
        for m in (0, 1, 2):
            at(f"2026-10-20T00:0{m}:00Z", "tick", n=m, interval=60, stalled=["a"] if m == 1 else [])
        at("2026-10-20T00:05:00Z", "note", by="user", text="hello")
        at("2026-10-20T00:10:00Z", "tick", n=3, interval=60, stalled=["a", "b"])
        at("2026-10-20T00:11:00Z", "tick", n=4, interval=60)
        at("2026-10-20T01:00:00Z", "tick", n=5, interval=60)
        at("2026-10-20T01:01:00Z", "tick", n=6, interval=60)
        # change a: ready 00:00 -> live 02:00 (2 h); b: ready 00:00 -> live 08:00 (8 h); c: live without a ready
        at("2026-10-20T00:00:00Z", "stage", change="a", **{"from": "backlog", "to": "ready"})
        at("2026-10-20T00:00:00Z", "stage", change="b", **{"from": "backlog", "to": "ready"})
        at("2026-10-20T02:00:00Z", "stage", change="a", **{"from": "checked", "to": "live"})
        at("2026-10-20T08:00:00Z", "stage", change="b", **{"from": "checked", "to": "live"})
        at("2026-10-19T08:00:00Z", "stage", change="c", **{"from": "checked", "to": "live"})
        # checks: a PASS first, b FAIL then PASS
        at("2026-10-20T01:00:00Z", "check", change="a", result="PASS")
        at("2026-10-20T01:00:00Z", "check", change="b", result="FAIL")
        at("2026-10-20T01:30:00Z", "check", change="b", result="PASS")
        # gates: a gated first; b failed from the gate, then gated
        at("2026-10-20T03:00:00Z", "stage", change="a", **{"from": "live", "to": "gated"})
        at("2026-10-20T09:00:00Z", "hold", change="b", hold="failed", source="gate", reason="accuracy")
        at("2026-10-20T10:00:00Z", "stage", change="b", **{"from": "live", "to": "gated"})
        # denials, agents, deadlocks, builds
        at("2026-10-20T04:00:00Z", "denial", source="hook", rule="no-push")
        at("2026-10-20T04:30:00Z", "denial", source="hook", rule="no-push")
        at("2026-10-20T05:00:00Z", "denial", source="classifier", rule="x")
        at("2026-10-20T06:00:00Z", "agent", action="end", name="sd1", role="server-dev", change="a", usd=4.0, minutes=30, turns=40)
        at("2026-10-20T06:00:00Z", "agent", action="end", name="sd2", role="server-dev", change="b", usd=8.0, minutes=50, turns=60)
        at("2026-10-20T06:00:00Z", "agent", action="end", name="cd1", role="client-dev", change="c", usd=2.0, minutes=10, turns=5)
        at("2026-10-20T07:00:00Z", "heal", action="deadlock", resource="device", detect_min=1.0, recover_min=4.0)
        at("2026-10-20T07:30:00Z", "heal", action="deadlock", resource="desktop", detect_min=3.0, recover_min=10.0)
        at("2026-10-20T07:40:00Z", "batch", batch="t1", step="gating", seconds=600)
        at("2026-10-20T07:50:00Z", "batch", batch="t2", step="gating", seconds=900)
        util.freeze("2026-10-20T12:00:00Z")
        self.k = kpi.compute()

    def test_unattended_hours(self):
        # 2 min + 1 min + 1 min; the user note breaks 00:02->00:10 and 00:11->01:00 is past 2*interval
        self.assertAlmostEqual(self.k["unattended_hours"], 4 / 60, places=2)

    def test_throughput_and_lead_time(self):
        lpd = self.k["live_per_day"]
        self.assertEqual(lpd["by_day"]["2026-10-20"], 2)
        self.assertEqual(lpd["by_day"]["2026-10-19"], 1)
        self.assertEqual(len(lpd["by_day"]), 7)
        self.assertAlmostEqual(lpd["mean"], 3 / 7, places=2)
        lt = self.k["lead_time_h"]
        self.assertEqual(lt["n"], 3)           # c counts from its first event
        self.assertEqual(lt["median"], 2.0)
        self.assertEqual(lt["p90"], 8.0)

    def test_first_pass_rates(self):
        self.assertEqual(self.k["check_first_pass"], {"rate": 0.5, "pass": 1, "n": 2})
        self.assertEqual(self.k["gate_first_pass"], {"rate": 0.5, "pass": 1, "n": 2})

    def test_stalls_and_interventions(self):
        self.assertEqual(self.k["stall_minutes"], 3.0)   # 1 slug + 2 slugs at 60 s ticks
        self.assertEqual(self.k["interventions_per_day"]["by_day"]["2026-10-20"], 1)
        self.assertEqual(self.k["interventions_per_day"]["total"], 1)

    def test_denials_by_source(self):
        d = self.k["denials_per_hour"]
        self.assertEqual(d["n"], 3)
        self.assertEqual(d["hours"], 26.0)     # the log spans 2026-10-19T08:00 .. 2026-10-20T10:00
        self.assertAlmostEqual(d["by_source"]["hook"], 2 / 26, places=3)
        self.assertAlmostEqual(d["total"], 3 / 26, places=3)

    def test_cost_deadlocks_builds(self):
        t = self.k["tokens_per_change"]
        self.assertEqual(t["server-dev"], {"usd": 6.0, "minutes": 40.0, "turns": 50.0, "n": 2})
        self.assertEqual(t["client-dev"]["n"], 1)
        self.assertEqual(self.k["deadlocks"], {"count": 2, "detect_min": 2.0, "recover_min": 7.0})
        self.assertEqual(self.k["builds"], {"median": 750.0, "last": 900.0, "n": 2})

    def test_trend(self):
        self.assertEqual(dict(kpi.trend("live", 3)), {"2026-10-18": 0, "2026-10-19": 1, "2026-10-20": 2})
        self.assertEqual(dict(kpi.trend("usd", 2))["2026-10-20"], 14.0)
        self.assertEqual(dict(kpi.trend("denials", 1))["2026-10-20"], 3)
        self.assertEqual(dict(kpi.trend("interventions", 1))["2026-10-20"], 1)
        with self.assertRaises(ValueError):
            kpi.trend("nope")

    def test_empty_log(self):
        os.remove(events.path())
        k = kpi.compute()
        self.assertEqual(k["unattended_hours"], 0)
        self.assertIsNone(k["lead_time_h"]["median"])
        self.assertIsNone(k["check_first_pass"]["rate"])
        self.assertEqual(k["tokens_per_change"], {})
        self.assertIsNone(k["builds"]["last"])


if __name__ == "__main__":
    unittest.main()
