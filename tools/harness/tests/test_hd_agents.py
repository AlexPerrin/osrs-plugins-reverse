"""hd agents (BR-G1..G4): the brief, spawn (HD_SPAWN=fake), poll of an ended run, stop, lingering and budget."""
import json
import os
import unittest

import hd_helpers as H
from hd_helpers import SHA

from hd import agents, events, leases, store, util

HD = "python3 tools/harness/hd.py"


class AgentsTest(unittest.TestCase):
    def setUp(self):
        self.d = H.sandbox(self)
        H.env(self, HD_SPAWN="fake", HD_WORKTREE="0")
        self.addCleanup(util.freeze, None)

    def test_brief_dev(self):
        ch = H.change("feat", "server")
        store.set_hold(ch, "failed", "gate FAIL: the drop rate is 1/128, not 1/64")
        ch["owner"] = "sd31"
        store.save(ch)
        ch = store.load("feat")
        b = agents.brief(ch, "server-dev", "dev", ["desktop"])
        self.assertTrue(b.startswith("Harness change `feat`"), b.splitlines()[0])
        for want in ("Your name is `sd31`", f"Change file: {ch.path}",
                     f'take with: {HD} lease take <instance> sd31 "<what>"',
                     f"heartbeat: {HD} lease heartbeat <instance> sd31", f"free: {HD} lease release <instance> sd31",
                     f"{HD} handover feat --branch content/feat --commit <sha> --evidence "
                     f"{os.environ['HD_EVIDENCE_DIR']}/feat --summary",
                     f"{HD} block feat --by <other-slug>", f"{HD} new --track server --slug <x>", f"{HD} fail feat",
                     f"Evidence goes in {os.environ['HD_EVIDENCE_DIR']}/feat/",
                     "Hold `failed`: gate FAIL: the drop rate is 1/128, not 1/64", "SAME branch `content/feat`",
                     "accuracy/<slug>.md", "green CI", "<scratchpad>/<your-name>/", "never screenshots",
                     "## Definition of done", "- [x] done"):
            self.assertIn(want, b, want)
        self.assertNotIn("## Log", b)

    def test_brief_gate_and_train(self):
        ch = H.change("feat", "server", stage="live", commit=SHA, owner="sq31")
        b = agents.brief(ch, "server-qa", "gate", ["desktop"])
        self.assertIn(f"{HD} gate feat PASS|FAIL", b)
        self.assertIn(f"`content/feat` at `{SHA}`", b)
        batch = {"id": "int-20261021-1", "kind": "train", "step": "exception", "exception": "CONFLICT in Foo.kt",
                 "owner": "ig1"}
        b = agents.brief(batch, "integrator", "train", [])
        self.assertTrue(b.startswith("Harness change `int-20261021-1`"))
        self.assertIn(f"{HD} resolve int-20261021-1", b)
        self.assertIn("CONFLICT in Foo.kt", b)
        self.assertIn("Instances granted to you: none.", b)

    def test_spawn_fake(self):
        ch = H.change("feat", "server")
        rec = agents.spawn(ch, "server-dev", "dev")
        self.assertEqual(rec["name"], "sd31")
        self.assertEqual((rec["instances"], rec["pid"], rec["fake"]), (["desktop"], None, True))
        self.assertEqual(rec["budget"], {"usd": 25, "minutes": 240})
        self.assertIn("sd31", leases.status("desktop")["grants"])
        self.assertEqual(leases.status("desktop")["grants"]["sd31"]["change"], "feat")
        back = store.load("feat")
        self.assertEqual((back.stage, back["owner"], back["instances"]), ("dev", "sd31", ["desktop"]))
        self.assertIn("ready -> dev: dispatched to sd31", back.body)
        self.assertEqual(store.rec_load(agents.RUNS, "sd31")["change"], "feat")
        with open(rec["log"]) as f:
            self.assertTrue(f.read().startswith("Harness change `feat`"))
        ev = events.read(kinds=["agent"])[-1]
        self.assertEqual((ev["action"], ev["name"], ev["stage"]), ("start", "sd31", "dev"))
        self.assertEqual(agents.running(), [])   # a fake run has no process
        self.assertEqual([r["name"] for r in agents._open()], ["sd31"])

    def test_spawn_gate_instances_and_failed_hold_kept(self):
        ch = H.change("feat", "server", stage="live", commit=SHA)
        store.set_hold(ch, "failed", "fix it")
        store.save(ch)
        rec = agents.spawn(store.load("feat"), "server-qa", "gate")
        self.assertEqual(rec["name"], "sq31")
        self.assertEqual(rec["instances"], ["desktop"])
        back = store.load("feat")
        self.assertEqual((back.stage, back.hold), ("live", "failed"))
        rec = agents.spawn(H.change("plug", "client"), "client-dev", "dev", ["device"])
        self.assertEqual(rec["name"], "cd48")
        self.assertIn("cd48", leases.status("device")["grants"])

    def write_run(self, name, pid, result=None, **kw):
        log = os.path.join(os.environ["HD_STATE_DIR"], "runs", f"{name}.log")
        os.makedirs(os.path.dirname(log), exist_ok=True)
        with open(log, "w") as f:
            f.write('{"type":"system","subtype":"init"}\n{"type":"assistant","message":{}}\n')
            if result:
                f.write(json.dumps(result) + "\n")
        rec = {"name": name, "role": "server-dev", "change": "feat", "stage": "dev", "pid": pid, "pgid": pid,
               "started": util.iso(), "cwd": "/x", "log": log, "budget": {"usd": 25, "minutes": 240},
               "instances": ["desktop"], "ended": None, "exit": None}
        rec.update(kw)
        store.rec_save(agents.RUNS, name, rec)
        return rec

    def test_poll_parses_result_and_frees(self):
        ch = H.change("feat", "server", stage="dev", owner="sd5")
        leases.grant("desktop", "sd5", 60, change="feat")
        leases.take("desktop", "sd5", "ab test")
        result = {"type": "result", "subtype": "success", "is_error": False, "num_turns": 42, "duration_ms": 1500000,
                  "total_cost_usd": 3.456, "usage": {"input_tokens": 1000, "output_tokens": 200,
                                                     "cache_read_input_tokens": 5000}, "result": "handed over"}
        self.write_run("sd5", H.dead_pid(), result)
        ended = agents.poll()
        self.assertEqual([r["name"] for r in ended], ["sd5"])
        r = store.rec_load(agents.RUNS, "sd5")
        self.assertEqual((r["exit"], r["turns"], r["minutes"], r["usd"], r["tokens"]), ("ok", 42, 25.0, 3.46, 6200))
        self.assertTrue(r["ended"])
        back = store.load("feat")
        self.assertEqual(back["cost"], {"runs": 1, "turns": 42, "minutes": 25.0, "usd": 3.46})
        self.assertIsNone(back["owner"])
        self.assertIn("run sd5 ended: 42 turns, 25.0 min, $3.46, exit ok", back.body)
        ev = events.read(kinds=["agent"])[-1]
        self.assertEqual((ev["action"], ev["name"], ev["usd"], ev["exit"]), ("end", "sd5", 3.46, "ok"))
        self.assertIsNone(leases.status("desktop")["holder"])
        self.assertNotIn("sd5", leases.status("desktop")["grants"])
        self.assertEqual(agents.poll(), [])   # ended once

    def test_poll_no_result_and_error(self):
        H.change("feat", "server", stage="dev")
        self.write_run("sd6", H.dead_pid())
        self.write_run("sd7", H.dead_pid(), {"type": "result", "is_error": True, "num_turns": 3, "duration_ms": 60000,
                                             "total_cost_usd": 0.5, "result": "budget"})
        exits = {r["name"]: r["exit"] for r in agents.poll()}
        self.assertEqual(exits, {"sd6": "no-result", "sd7": "error"})
        self.assertEqual(store.load("feat")["cost"]["runs"], 2)

    def test_stop_fake_run(self):
        ch = H.change("feat", "server")
        rec = agents.spawn(ch, "server-dev", "dev")
        r = agents.stop(rec["name"], "test")
        self.assertEqual(r["exit"], "stopped:test")
        self.assertTrue(store.rec_load(agents.RUNS, rec["name"])["ended"])
        self.assertEqual(events.read(kinds=["agent"])[-1]["exit"], "stopped:test")
        self.assertEqual(agents._open(), [])
        self.assertIsNone(agents.stop("nobody", "x"))

    def test_lingering_after_grace(self):
        util.freeze("2026-10-21T01:00:00Z")
        ch = H.change("feat", "server")
        rec = agents.spawn(ch, "server-dev", "dev")
        self.assertEqual(agents.lingering(store.index()), [])
        ch = store.load("feat")
        store.set_stage(ch, "checked", "hand-over check PASS", commit=SHA)
        store.save(ch)
        util.freeze("2026-10-21T01:01:00Z")
        self.assertEqual(agents.lingering(store.index()), [])   # within the grace
        util.freeze("2026-10-21T01:10:00Z")
        got = agents.lingering(store.index())
        self.assertEqual([(r["name"], why) for r, why in got], [(rec["name"], "lingering: change at checked for 10 min")])
        self.assertEqual(agents.over_budget(), [])
        util.freeze("2026-10-21T06:00:00Z")
        self.assertEqual(agents.over_budget()[0][1], "over budget: 300 min > 240")

    def test_lingering_gate_and_train(self):
        util.freeze("2026-10-21T01:00:00Z")
        ch = H.change("feat", "server", stage="live", commit=SHA)
        agents.spawn(ch, "server-qa", "gate")
        store.rec_save("batches", "int-1", {"id": "int-1", "kind": "train", "step": "exception", "exception": "x"})
        batch = store.rec_load("batches", "int-1")
        agents.spawn(batch, "integrator", "train", [])
        util.freeze("2026-10-21T01:30:00Z")
        self.assertEqual(agents.lingering(store.index()), [])
        ch = store.load("feat")
        ch["gated_commit"] = SHA
        store.save(ch)
        store.rec_save("batches", "int-1", dict(batch, step="rebuilding"))
        util.freeze("2026-10-21T01:40:00Z")
        whys = sorted(why for _, why in agents.lingering(store.index()))
        self.assertEqual(whys, ["lingering: batch at rebuilding for 10 min", "lingering: gate recorded for 10 min"])


if __name__ == "__main__":
    unittest.main()
