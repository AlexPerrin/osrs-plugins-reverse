"""The tick (problem 3f, invariants I1, I2, I9; BR-C2, BR-C4, BR-C5, BR-S5, BR-R4): each step with the agent,
batch, approval and heal modules stubbed, FakeObserver for the world, a temp git repo for the client merge."""
import os
import subprocess
import unittest
from unittest import mock

import hd_helpers as H
from hd_helpers import SHA, SHB

from hd import events, leases, store, tick
from hd.observe import FakeObserver


def stub_modules(test, **over):
    """Stub the modules the tick calls that other tests cover; return the call log."""
    calls = []
    stubs = {
        "agents.poll": lambda: [], "agents.running": lambda: [], "agents.spawn": None,
        "batches.enqueue": lambda ch: calls.append(("enqueue", ch["slug"])) or True,
        "batches.advance": lambda obs: [], "batches.deploy_ready": lambda obs: None,
        "batches.deploy": lambda b, obs: (True, "deploying"), "batches.repin_due": lambda obs: None,
        "batches.repin": lambda obs: "repinned", "batches.release_due": lambda idx: None,
        "batches.new_release": lambda slugs: {"id": "rel-1"}, "batches.status": lambda: [],
        "approvals.ensure": lambda ch, obs: None, "approvals.ensure_release": lambda b: None,
        "approvals.execute": lambda obs: [], "heal.run": lambda obs, idx: [],
    }

    def spawn(ch, role, ex, need):
        calls.append(("spawn", ch["slug"], role, ex))
        ch["owner"] = f"{role}-x"
        store.set_stage(ch, "dev", "dispatched") if ex == "dev" else None
        store.save(ch)
        return {"name": f"{role}-x", "role": role, "pid": None}
    stubs["agents.spawn"] = spawn
    stubs.update(over)
    for dotted, fn in stubs.items():
        mod, name = dotted.split(".")
        p = mock.patch.object(getattr(tick, mod), name, fn)
        p.start()
        test.addCleanup(p.stop)
    return calls


class ReconcileTest(unittest.TestCase):
    def setUp(self):
        H.sandbox(self)
        self.calls = stub_modules(self)

    def test_drift_repaired_and_logged(self):
        """I1: the recorded stage follows the observation, both ways, with an event."""
        ch = H.change("plug", "client", stage="checked", commit=SHA)
        obs = FakeObserver(tips={("ours", "client/plug"): SHA}, contains={("ours", "master", SHA): True})
        tick.tick(obs)
        self.assertEqual(store.load("plug").stage, "live")
        self.assertEqual(store.load("plug")["live_commit"], SHA)
        ev = [e for e in events.read(kinds=["stage"]) if e.get("change") == "plug"]
        self.assertEqual((ev[-1]["from"], ev[-1]["to"]), ("checked", "live"))
        # a revert: master no longer contains it -> drift back to checked
        obs = FakeObserver(tips={("ours", "client/plug"): SHA}, contains={("ours", "master", SHA): False})
        tick.tick(obs)
        self.assertEqual(store.load("plug").stage, "checked")
        self.assertTrue(events.read(kinds=["drift"]))

    def test_new_commit_reenters_at_ready(self):
        """BR-C2."""
        ch = H.change("feat", "server", stage="live", commit=SHA)
        obs = FakeObserver(tips={("fork", "content/feat"): SHB}, pins={"content/feat": SHA}, live={"integration": "c" * 40},
                           contains={("fork", "c" * 40, SHA): True}, records=[])
        tick.tick(obs)
        ch = store.load("feat")
        self.assertIsNone(ch["commit"])
        self.assertEqual(ch["live_commit"], SHA)
        ev = [e for e in events.read(kinds=["stage"]) if e.get("change") == "feat"]
        self.assertEqual((ev[0]["from"], ev[0]["to"], ev[0]["commit"]), ("live", "ready", SHB))
        self.assertIn(("spawn", "feat", "server-dev", "dev"), self.calls)   # re-dispatched in the same tick
        # a dev working on a failed-hold fix pushes to the branch: not a re-entry while it owns the change
        owned = H.change("owned", "server", stage="live", commit=SHA, owner="sd9")
        store.set_hold(owned, "failed", "gate FAIL"); store.save(owned)
        obs = FakeObserver(tips={("fork", "content/owned"): SHB}, pins={"content/owned": SHA}, live={"integration": "c" * 40},
                           contains={("fork", "c" * 40, SHA): True}, records=[])
        tick.tick(obs)
        self.assertEqual(store.load("owned")["commit"], SHA)
        self.assertEqual(store.load("owned")["owner"], "sd9")

    def test_deps_hold_set_and_cleared(self):
        """BR-C4."""
        srv = H.change("srv", "server", stage="checked", commit=SHA)
        H.change("cli", "client", deps=["srv"])
        tick.tick(FakeObserver(tips={("fork", "content/srv"): SHA}, pins={}, live={"integration": "c" * 40}))
        cli = store.load("cli")
        self.assertEqual(cli.hold, "blocked")
        self.assertEqual([c for c in self.calls if c[0] == "spawn"], [])   # not dispatched while blocked
        srv = store.load("srv"); srv["stage"] = "live"; store.save(srv)
        tick.tick(FakeObserver(tips={("fork", "content/srv"): SHA}, pins={"content/srv": SHA}, live={"integration": "c" * 40},
                               contains={("fork", "c" * 40, SHA): True}, records=[]))
        self.assertIsNone(store.load("cli").hold)
        self.assertIn(("spawn", "cli", "client-dev", "dev"), self.calls)


class ReapTest(unittest.TestCase):
    def setUp(self):
        H.sandbox(self)
        self.calls = stub_modules(self)

    def test_check_pass_then_enqueue(self):
        ch = H.change("feat", "server", stage="dev", commit=SHA, owner="sd1", evidence="re/evidence/feat",
                      pr="https://github.com/AlexPerrin/OpenRune-Server/pull/9")
        obs = FakeObserver(tips={("fork", "content/feat"): SHA}, evidence={"re/evidence/feat": ["x"]},
                           docs={"accuracy/feat.md": "x"}, ci={("content/feat", SHA): "success"}, pins={},
                           live={"integration": "c" * 40})
        acts = tick.tick(obs)
        ch = store.load("feat")
        self.assertEqual(ch.stage, "checked")
        self.assertIsNone(ch["owner"])
        self.assertIn(("enqueue", "feat"), self.calls)
        self.assertEqual(events.read(kinds=["check"])[0]["result"], "PASS")
        self.assertTrue(any("check PASS" in a for a in acts))

    def test_check_pass_keeps_a_blocked_hold(self):
        """A hand-over clears a failed hold (BR-S3), never a blocked one: the dep is still not live."""
        H.change("srv", "server", stage="checked", commit=SHA)
        ch = H.change("cli", "client", stage="dev", commit=SHB, owner="cd1", deps=["srv"], evidence="re/evidence/cli")
        store.set_hold(ch, "blocked", "waiting for srv"); store.save(ch)
        obs = FakeObserver(tips={("ours", "client/cli"): SHB, ("fork", "content/srv"): SHA}, contains={("ours", "master", SHB): False},
                           evidence={"re/evidence/cli": ["repack.log", "logcat.txt"]}, files={"repack.log": "telemetry: a\n" * 7},
                           pins={}, live={"integration": "c" * 40})
        tick.tick(obs)
        cli = store.load("cli")
        self.assertEqual((cli.stage, cli.hold), ("checked", "blocked"))
        self.assertNotIn(("enqueue", "cli"), self.calls)

    def test_check_fail_returns_to_ready_with_items(self):
        """BR-S5."""
        ch = H.change("feat", "server", stage="dev", commit=SHA, owner="sd1", evidence="re/evidence/feat")
        obs = FakeObserver(tips={("fork", "content/feat"): SHB}, evidence={"re/evidence/feat": []},
                           ci={("content/feat", SHA): "pending"})
        tick.tick(obs)
        ch = store.load("feat")
        self.assertTrue(any("not aaaaaaaaa" in t for t in ch["check"]))
        self.assertTrue(any("evidence dir" in t for t in ch["check"]))
        ev = [e for e in events.read(kinds=["stage"]) if e.get("change") == "feat"]
        self.assertEqual((ev[0]["from"], ev[0]["to"]), ("dev", "ready"))
        self.assertEqual(events.read(kinds=["check"])[0]["result"], "FAIL")
        self.assertIn(("spawn", "feat", "server-dev", "dev"), self.calls)   # the next dev fixes exactly those items


class ShipTest(unittest.TestCase):
    def setUp(self):
        d = H.sandbox(self)
        self.calls = stub_modules(self)
        from helpers import commit_file, git, init_repo
        self.repo = init_repo(os.path.join(d, "repo"))
        commit_file(self.repo, "host/a.txt", "a\n")
        git(self.repo, "checkout", "-q", "-b", "client/plug")
        self.sha = commit_file(self.repo, "host/b.txt", "b\n")
        git(self.repo, "checkout", "-q", "master")
        os.environ["HD_SHIP_REPO"] = self.repo
        os.environ["HD_NO_PUSH"] = "1"
        from helpers import GIT_ENV
        for k in ("GIT_AUTHOR_NAME", "GIT_AUTHOR_EMAIL", "GIT_COMMITTER_NAME", "GIT_COMMITTER_EMAIL"):
            os.environ[k] = GIT_ENV[k]
            self.addCleanup(os.environ.pop, k, None)
        self.addCleanup(os.environ.pop, "HD_SHIP_REPO", None)
        self.addCleanup(os.environ.pop, "HD_NO_PUSH", None)

    def test_checked_client_change_merges_into_master(self):
        H.change("plug", "client", stage="checked", commit=self.sha)
        obs = FakeObserver(tips={("ours", "client/plug"): self.sha}, contains={("ours", "master", self.sha): False})
        acts = tick.tick(obs)
        self.assertTrue(any("merged into master" in a for a in acts), acts)
        log = subprocess.run(["git", "-C", self.repo, "log", "-1", "--format=%s%n%b"], capture_output=True, text=True).stdout
        self.assertIn("Merge client/plug: plug", log)
        self.assertIn("Co-Authored-By: Claude", log)
        self.assertEqual(subprocess.run(["git", "-C", self.repo, "merge-base", "--is-ancestor", self.sha, "master"]).returncode, 0)
        self.assertTrue(events.read(kinds=["ship"]))

    def test_merge_failure_is_an_exception_hold(self):
        H.change("plug", "client", stage="checked", commit="f" * 40)
        obs = FakeObserver(tips={("ours", "client/plug"): "f" * 40}, contains={("ours", "master", "f" * 40): False})
        tick.tick(obs)
        ch = store.load("plug")
        self.assertEqual(ch.hold, "exception")


class DispatchTest(unittest.TestCase):
    def setUp(self):
        H.sandbox(self)
        self.calls = stub_modules(self)

    def test_priority_and_slots(self):
        """BR-C5 and BR-R5: P1 first; server has 2 dev slots, client 1; the rest are stalled."""
        H.change("s-p2", "server", prio="P2"); H.change("s-p1", "server", prio="P1"); H.change("s-p3", "server", prio="P3")
        H.change("c-a", "client"); H.change("c-b", "client")
        tick.tick(FakeObserver())
        spawned = [c[1] for c in self.calls if c[0] == "spawn"]
        self.assertEqual(spawned, ["s-p1", "c-a", "s-p2"])
        t = events.read(kinds=["tick"])[-1]
        self.assertEqual(sorted(t["stalled"]), ["c-b", "s-p3"])

    def test_held_instance_stalls_dispatch(self):
        H.change("c-a", "client")
        leases.grant("device", "other", 60); leases.take("device", "other", "busy")
        tick.tick(FakeObserver())
        self.assertEqual([c for c in self.calls if c[0] == "spawn"], [])
        self.assertIn("c-a", events.read(kinds=["tick"])[-1]["stalled"])

    def test_gate_executor_for_live_server_change(self):
        H.change("feat", "server", stage="live", commit=SHA)
        obs = FakeObserver(tips={("fork", "content/feat"): SHA}, pins={"content/feat": SHA}, live={"integration": "c" * 40},
                           contains={("fork", "c" * 40, SHA): True}, records=[])
        tick.tick(obs)
        self.assertIn(("spawn", "feat", "server-qa", "gate"), self.calls)

    def test_no_dod_no_dispatch(self):
        ch = store.new("nodod", "server", "x", stage="backlog")
        tick.tick(FakeObserver())
        self.assertEqual([c for c in self.calls if c[0] == "spawn"], [])


class DeployTest(unittest.TestCase):
    def setUp(self):
        H.sandbox(self)

    def test_drain_then_deploy(self):
        """BR-R4 / I9: a pending deploy drains the instances, waits for holders, reclaims past the cap, deploys."""
        batch = {"id": "int-1", "step": "imaged"}
        deployed = []
        self.calls = stub_modules(self, **{"batches.deploy_ready": lambda obs: batch,
                                           "batches.deploy": lambda b, obs: deployed.append(b["id"]) or (True, "ok")})
        leases.grant("device", "cd1", 600); leases.take("device", "cd1", "check", pid=os.getpid())
        acts = tick.tick(FakeObserver())
        self.assertTrue(leases.status("device")["drain"])
        self.assertTrue(any("waits for: device by cd1" in a for a in acts), acts)
        self.assertEqual(deployed, [])
        # a new taker is refused while draining
        leases.grant("desktop", "sd1", 60)
        self.assertTrue(leases.take("desktop", "sd1", "x")[0])   # desktop not drained yet? it is: every resource drains
        # the holder frees -> the daemon takes everything and deploys
        leases.release("device", "cd1"); leases.release("desktop", "sd1")
        tick.tick(FakeObserver())
        self.assertEqual(deployed, ["int-1"])
        for r in ("device", "desktop", "desktop2", "live-server"):
            self.assertEqual(leases.status(r)["holder"], leases.DAEMON)

    def test_drain_cap_reclaims(self):
        batch = {"id": "int-2", "step": "imaged"}
        self.calls = stub_modules(self, **{"batches.deploy_ready": lambda obs: batch, "batches.deploy": lambda b, obs: (True, "ok")})
        leases.grant("device", "cd1", 6000); leases.take("device", "cd1", "check", pid=os.getpid())
        tick.tick(FakeObserver())
        from hd import util
        util.freeze(util.now() + __import__("datetime").timedelta(hours=4))
        self.addCleanup(util.freeze, None)
        acts = tick.tick(FakeObserver())
        self.assertTrue(any("reclaimed for the deploy" in a for a in acts), acts)
        self.assertEqual(leases.status("device")["holder"], leases.DAEMON)


class IsolationTest(unittest.TestCase):
    def setUp(self):
        H.sandbox(self)

    def test_a_failing_step_does_not_stop_the_tick(self):
        def boom(obs):
            raise RuntimeError("gate exploded")
        calls = stub_modules(self, **{"batches.advance": boom})
        H.change("c-a", "client")
        acts = tick.tick(FakeObserver())
        self.assertTrue(any("error" in a for a in acts))
        self.assertIn(("spawn", "c-a", "client-dev", "dev"), calls)
        self.assertEqual(events.read(kinds=["error"])[0]["step"], "ship")

    def test_dry_run_changes_nothing(self):
        calls = stub_modules(self)
        H.change("c-a", "client")
        acts = tick.tick(FakeObserver(), dry_run=True)
        self.assertTrue(any("[dry run]" in a for a in acts))
        self.assertEqual([c for c in calls if c[0] == "spawn"], [])
        self.assertEqual(events.read(kinds=["tick"]), [])


if __name__ == "__main__":
    unittest.main()
