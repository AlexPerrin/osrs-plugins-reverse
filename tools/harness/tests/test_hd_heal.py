"""hd heal (BR-H2, BR-H3): idle leases, lingering agents, dead runs, stale containers, dead batch steps, deadlock;
a broken step never stops the others."""
import os
import unittest

import hd_helpers as H
from hd_helpers import SHA

from hd import agents, batches, events, heal, leases, store, util
from hd.observe import FakeObserver


class HealTest(unittest.TestCase):
    def setUp(self):
        self.d = H.sandbox(self)
        H.env(self, HD_SPAWN="fake", HD_WORKTREE="0", HD_DOCKER=H.stub(self.d, "docker", 'echo "$@" >> "$(dirname "$0")/docker.log"\n'))
        self.addCleanup(util.freeze, None)

    def docker_log(self):
        try:
            with open(os.path.join(self.d, "docker.log")) as f:
                return f.read().splitlines()
        except OSError:
            return []

    def test_idle_lease_reclaimed_with_evidence(self):
        util.freeze("2026-10-21T01:00:00Z")
        leases.grant("desktop", "sq1", 600)
        leases.take("desktop", "sq1", "gate", container="qa-or-feat")
        obs = FakeObserver(containers={"qa-or-feat": {"status": "running"}})
        self.assertEqual(heal.run(obs, {}), [])   # fresh heartbeat
        util.freeze("2026-10-21T02:00:00Z")
        self.assertEqual(heal.run(obs, {}), [])   # the attached container runs
        obs = FakeObserver(containers={"qa-or-feat": {"status": "exited"}})
        acts = heal.run(obs, {})
        self.assertEqual(len(acts), 2)   # the reclaim, then the exited container's removal
        self.assertIn("lease desktop reclaimed from sq1: no attached process or container, last heartbeat 60 min ago", acts[0])
        self.assertEqual(acts[1], "container qa-or-feat removed (exited)")
        self.assertIsNone(leases.status("desktop")["holder"])
        ev = [e for e in events.read(kinds=["lease"]) if e["action"] == "reclaim"][-1]
        self.assertEqual((ev["holder"], ev["resource"]), ("sq1", "desktop"))
        self.assertIn("heartbeat", ev)
        self.assertIn("last heartbeat 60 min ago", ev["why"])

    def test_lingering_agent_stopped(self):
        util.freeze("2026-10-21T01:00:00Z")
        ch = H.change("feat", "server")
        rec = agents.spawn(ch, "server-dev", "dev")
        ch = store.load("feat")
        store.set_stage(ch, "checked", "check PASS", commit=SHA)
        store.save(ch)
        util.freeze("2026-10-21T01:01:00Z")
        self.assertEqual(heal.run(FakeObserver(), store.index()), [])
        util.freeze("2026-10-21T01:05:00Z")
        acts = heal.run(FakeObserver(), store.index())
        self.assertEqual(acts, [f"agent {rec['name']} stopped: lingering: change at checked for 5 min"])
        r = store.rec_load(agents.RUNS, rec["name"])
        self.assertEqual(r["exit"], "lingering: change at checked for 5 min".join(["stopped:", ""]))
        self.assertNotIn(rec["name"], leases.status("desktop")["grants"])
        self.assertEqual(store.load("feat").stage, "checked")

    def test_dead_run_without_handover_back_to_ready(self):
        ch = H.change("feat", "server", stage="dev", owner="sd9")
        log = os.path.join(os.environ["HD_STATE_DIR"], "runs", "sd9.log")
        os.makedirs(os.path.dirname(log), exist_ok=True)
        open(log, "w").close()
        store.rec_save(agents.RUNS, "sd9", {"name": "sd9", "role": "server-dev", "change": "feat", "stage": "dev",
                                            "pid": H.dead_pid(), "started": util.iso(), "log": log, "cwd": "/x",
                                            "budget": {"usd": 25, "minutes": 240}, "ended": None})
        agents.poll()
        self.assertIsNone(store.load("feat")["owner"])
        acts = heal.run(FakeObserver(), store.index())
        self.assertEqual(acts, ["change feat back to ready: run sd9 ended without a hand-over"])
        back = store.load("feat")
        self.assertEqual((back.stage, back["owner"]), ("ready", None))
        self.assertIn("dev -> ready: run sd9 ended without a hand-over", back.body)
        ev = events.read(kinds=["stage"])[-1]
        self.assertEqual((ev["change"], ev["from"], ev["to"]), ("feat", "dev", "ready"))
        # a change in dev whose agent is alive, or handed over, stays
        H.change("busy", "server")
        agents.spawn(store.load("busy"), "server-dev", "dev")
        H.change("handed", "server", stage="dev", commit=SHA)
        self.assertEqual(heal.run(FakeObserver(), store.index()), [])

    def test_exited_containers_removed(self):
        obs = FakeObserver(containers={"qa-or-feat": {"status": "Exited (0) 2 hours ago", "image": "x"},
                                       "qa-or-live": {"status": "Up 3 hours", "image": "x"},
                                       "hd-boot-int-1": {"status": "Exited (137) 1 hour ago", "image": "y"},
                                       "osrs-openrune": {"status": "Exited (1) 5 min ago", "image": "z"}})
        acts = heal.run(obs, {})
        self.assertEqual(acts, ["container qa-or-feat removed (Exited (0) 2 hours ago)",
                                "container hd-boot-int-1 removed (Exited (137) 1 hour ago)"])
        self.assertEqual(self.docker_log(), ["rm -f qa-or-feat", "rm -f hd-boot-int-1"])
        self.assertEqual([e["container"] for e in events.read(kinds=["heal"])], ["qa-or-feat", "hd-boot-int-1"])

    def test_dead_batch_step_is_exception(self):
        store.rec_save(batches.BATCHES, "int-1", {"id": "int-1", "kind": "train", "step": "gating", "pid": H.dead_pid(),
                                                  "includes": ["feat"], "steps": []})
        store.rec_save(batches.BATCHES, "int-2", {"id": "int-2", "kind": "train", "step": "gating", "pid": os.getpid()})
        store.rec_save(batches.BATCHES, "int-3", {"id": "int-3", "kind": "train", "step": "queued", "pid": H.dead_pid()})
        obs = FakeObserver(alive={os.getpid(): True})
        acts = heal.run(obs, {})
        self.assertEqual(len(acts), 1)
        self.assertIn("batch int-1 exception: process", acts[0])
        b = store.rec_load(batches.BATCHES, "int-1")
        self.assertEqual((b["step"], b["pid"]), ("exception", None))
        self.assertIn("died during gating", b["exception"])
        self.assertEqual(b["steps"][-1]["step"], "exception")
        self.assertEqual(store.rec_load(batches.BATCHES, "int-2")["step"], "gating")
        self.assertEqual(store.rec_load(batches.BATCHES, "int-3")["step"], "queued")
        ev = events.read(kinds=["batch"])[-1]
        self.assertEqual((ev["batch"], ev["step"]), ("int-1", "exception"))
        self.assertEqual(heal.run(obs, {}), [])

    def test_deadlock_drains(self):
        """BR-H3: cli (deps: srv) holds the device while srv's image waits for a deploy that needs the device."""
        H.change("srv", "server", stage="checked", commit=SHA)
        cd1 = agents.spawn(H.change("cli", "client", deps=["srv"]), "client-dev", "dev")["name"]
        cd2 = agents.spawn(H.change("other", "client"), "client-dev", "dev", ["desktop"])["name"]
        leases.take("device", cd1, "check")
        leases.take("desktop", cd2, "check")
        self.assertEqual(heal.run(FakeObserver(), store.index()), [])   # deploy_ready not implemented: skipped
        orig = batches.deploy_ready
        batches.deploy_ready = lambda obs: {"id": "int-1", "includes": ["srv"]}
        self.addCleanup(setattr, batches, "deploy_ready", orig)
        acts = heal.run(FakeObserver(), store.index())
        self.assertEqual(acts, [f"deadlock: device held by {cd1} for cli, which waits for int-1 (deps in the batch); draining device"])
        self.assertTrue(leases.status("device")["drain"])
        self.assertIsNone(leases.status("desktop")["drain"])
        ev = events.read(kinds=["heal"])[-1]
        self.assertEqual((ev["action"], ev["resource"], ev["batch"]), ("deadlock", "device", "int-1"))
        self.assertIn("detected", ev)
        self.assertEqual(heal.run(FakeObserver(), store.index()), [])   # already draining

    def test_a_broken_step_never_stops_the_others(self):
        orig = batches.status
        batches.status = lambda: (_ for _ in ()).throw(RuntimeError("corrupt batch"))
        self.addCleanup(setattr, batches, "status", orig)
        obs = FakeObserver(containers={"qa-or-x": {"status": "Exited (0) 1 hour ago"}})
        acts = heal.run(obs, {})
        self.assertEqual(acts, ["container qa-or-x removed (Exited (0) 1 hour ago)"])
        ev = events.read(kinds=["error"])[-1]
        self.assertEqual((ev["step"], ev["error"]), ("dead_batches", "RuntimeError: corrupt batch"))


if __name__ == "__main__":
    unittest.main()
