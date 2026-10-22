"""hd foundation: the store (BR-C1, BR-C3), the event log (BR-H4), the lifecycle preconditions (BR-C2, BR-C4,
BR-C7, BR-C8, BR-S1), the hand-over check, the lease service (BR-R2, BR-R3, BR-R4)."""
import os
import unittest

import hd_helpers as H
from hd_helpers import SHA, SHB

from hd import check, events, leases, policy, stages, store, util


class StoreTest(unittest.TestCase):
    def setUp(self):
        H.sandbox(self)

    def test_new_ready_needs_dod(self):
        with self.assertRaises(ValueError):
            store.new("xx", "server", "x")
        ch = store.new("xx", "server", "x", stage="backlog")
        self.assertEqual(ch.stage, "backlog")
        self.assertFalse(ch.has_dod())

    def test_roundtrip_and_log(self):
        ch = store.new("feat-a", "client", "Feat A", dod=["built", "tried on the device"], network=True)
        self.assertEqual(ch["role"], "client-dev")
        self.assertEqual(ch.dod_unticked(), ["- [ ] built", "- [ ] tried on the device"])
        store.set_stage(ch, "dev", "dispatched", commit=None)
        store.set_hold(ch, "failed", "fix the thing")
        store.save(ch)
        back = store.load("feat-a")
        self.assertEqual(back.stage, "dev")
        self.assertEqual(back.hold, "failed")
        self.assertEqual(back.hold_reason, "fix the thing")
        self.assertTrue(back["network"])
        self.assertIn("ready -> dev: dispatched", back.body)
        self.assertEqual([m for _, m in back.log_lines()][0], "created (ready)")
        store.clear_hold(back)
        self.assertIsNone(back.hold)

    def test_bad_slug(self):
        with self.assertRaises(ValueError):
            store.new("Bad Slug", "client", "x", dod=["a"])

    def test_state_records_and_live(self):
        store.rec_save("runs", "sd1", {"role": "server-dev"})
        self.assertEqual(store.rec_load("runs", "sd1")["role"], "server-dev")
        self.assertEqual([r["id"] for r in store.rec_all("runs")], ["sd1"])
        store.rec_delete("runs", "sd1")
        self.assertEqual(store.rec_all("runs"), [])
        store.set_live({"integration": SHA})
        self.assertEqual(store.live()["integration"], SHA)


class EventsTest(unittest.TestCase):
    def setUp(self):
        H.sandbox(self)

    def test_append_only_utc(self):
        util.freeze("2026-10-21T01:00:00Z")
        self.addCleanup(util.freeze, None)
        events.emit("stage", change="a", **{"from": "ready", "to": "dev"})
        util.freeze("2026-10-21T02:00:00Z")
        events.emit("lease", action="take", resource="device", holder="cd1")
        evs = events.read()
        self.assertEqual([e["kind"] for e in evs], ["stage", "lease"])
        self.assertEqual(evs[0]["ts"], "2026-10-21T01:00:00Z")
        self.assertEqual(events.read(kinds=["lease"])[0]["holder"], "cd1")
        self.assertEqual(len(events.read(since="2026-10-21T01:30:00Z")), 1)
        self.assertEqual(events.read(change="a")[0]["to"], "dev")
        with open(events.path()) as f:
            self.assertEqual(len(f.read().splitlines()), 2)


class StagesTest(unittest.TestCase):
    def setUp(self):
        H.sandbox(self)

    def obs(self, **facts):
        from hd.observe import FakeObserver
        return FakeObserver(**facts)

    def test_deps_block_ready(self):
        dep = H.change("srv", "server", stage="checked", commit=SHA)
        cli = H.change("cli", "client", deps=["srv"])
        idx = store.index()
        st, why, _ = stages.derive(cli, self.obs(), idx)
        self.assertEqual(st, "ready")
        self.assertIn("waiting for srv (checked)", why)
        dep["stage"] = "live"
        store.save(dep)
        st, why, _ = stages.derive(cli, self.obs(), store.index())
        self.assertEqual(why, "a free slot and lease")

    def test_server_live_needs_pin_and_live_integration(self):
        ch = H.change("feat", "server", stage="checked", commit=SHA)
        live = {"integration": "c" * 40}
        o = self.obs(tips={("fork", "content/feat"): SHA}, pins={}, live=live)
        st, why, _ = stages.derive(ch, o, {})
        self.assertEqual((st, why), ("checked", "integration pin is absent, not aaaaaaaaa (next train)"))
        o = self.obs(tips={("fork", "content/feat"): SHA}, pins={"content/feat": SHA}, live=live,
                     contains={("fork", "c" * 40, SHA): True}, records=[])
        st, why, _ = stages.derive(ch, o, {})
        self.assertEqual(st, "live")
        self.assertIn("the upstream gate (no upstream gate record", why)

    def test_gate_only_what_is_live(self):
        """BR-S1: a PASS record at a commit that is not live does not reach gated."""
        rec = [("qa/feat.md", "# QA: content/feat\n- Branch: `content/feat`\n- Commit: `" + SHA + "`\n## Upstream gate: **PASS**\n")]
        ch = H.change("feat", "server", stage="checked", commit=SHA)
        o = self.obs(tips={("fork", "content/feat"): SHA}, pins={"content/feat": SHB}, live={"integration": "c" * 40},
                     records=rec)
        st, _, _ = stages.derive(ch, o, {})
        self.assertEqual(st, "checked")
        o = self.obs(tips={("fork", "content/feat"): SHA}, pins={"content/feat": SHA}, live={"integration": "c" * 40},
                     contains={("fork", "c" * 40, SHA): True}, records=rec)
        st, why, _ = stages.derive(ch, o, {})
        self.assertEqual(st, "gated")
        self.assertTrue(why.startswith("the user's approval"), why)

    def test_published_and_merged(self):
        rec = [("qa/feat.md", "- Branch: `content/feat`\n- Commit: `" + SHA[:9] + "`\n## Upstream gate: **PASS**\n")]
        ch = H.change("feat", "server", stage="gated", commit=SHA, upstream_pr=354)
        base = dict(tips={("fork", "content/feat"): SHA}, pins={"content/feat": SHA}, live={"integration": "c" * 40},
                    contains={("fork", "c" * 40, SHA): True}, records=rec)
        o = self.obs(upstream_prs={354: {"number": 354, "state": "open", "draft": True, "head": SHA}}, **base)
        st, why, _ = stages.derive(ch, o, {})
        self.assertEqual(st, "gated")
        self.assertIn("draft", why)
        o = self.obs(upstream_prs={354: {"number": 354, "state": "open", "draft": False, "head": SHA}}, **base)
        self.assertEqual(stages.derive(ch, o, {})[0], "published")
        base["contains"][("upstream", "main", SHA)] = True
        o = self.obs(upstream_prs={354: {"number": 354, "state": "closed", "merged": True, "head": SHA}}, **base)
        self.assertEqual(stages.derive(ch, o, {})[0], "merged")

    def test_local_only_ends_at_live(self):
        ch = H.change("loc", "server", stage="checked", commit=SHA, branch="local/loc", local_only=True)
        o = self.obs(tips={("fork", "local/loc"): SHA}, pins={"local/loc": SHA}, live={"integration": "c" * 40},
                     contains={("fork", "c" * 40, SHA): True})
        st, why, _ = stages.derive(ch, o, {})
        self.assertEqual((st, why), ("live", ""))
        self.assertEqual(stages.terminal(ch), "live")

    def test_client_live_is_master(self):
        ch = H.change("plug", "client", stage="checked", commit=SHA)
        o = self.obs(tips={("ours", "client/plug"): SHA}, contains={("ours", "master", SHA): False})
        self.assertEqual(stages.derive(ch, o, {})[0], "checked")
        o = self.obs(tips={("ours", "client/plug"): SHA}, contains={("ours", "master", SHA): True})
        self.assertEqual(stages.derive(ch, o, {})[:2], ("live", ""))

    def test_new_commit_reenters(self):
        """BR-C2: the branch tip moved past the handed-over commit."""
        ch = H.change("feat", "server", stage="live", commit=SHA)
        o = self.obs(tips={("fork", "content/feat"): SHB}, pins={"content/feat": SHA}, live={"integration": "c" * 40},
                     contains={("fork", "c" * 40, SHA): True}, records=[])
        _, _, moved = stages.derive(ch, o, {})
        self.assertEqual(moved, SHB)

    def test_unknown_never_regresses(self):
        ch = H.change("feat", "server", stage="live", commit=SHA)
        o = self.obs(tips={("fork", "content/feat"): SHA}, pins={"content/feat": SHA}, live={"integration": "c" * 40},
                     contains={}, records=None)   # contains unknown, docs unreadable
        st, _, _ = stages.derive(ch, o, {})
        self.assertEqual(st, "live")

    def test_next_executor(self):
        self.assertEqual(stages.next_executor(H.change("ch-a", "server")), "dev")
        self.assertEqual(stages.next_executor(H.change("ch-b", "server", stage="live", commit=SHA)), "gate")
        self.assertIsNone(stages.next_executor(H.change("ch-c", "server", stage="live", commit=SHA, local_only=True)))
        self.assertIsNone(stages.next_executor(H.change("ch-d", "client", stage="live", commit=SHA)))
        ch = H.change("ch-e", "server", stage="live", commit=SHA)
        store.set_hold(ch, "failed", "gate FAIL"); store.save(ch)
        self.assertEqual(stages.next_executor(store.load("ch-e")), "dev")
        ch = H.change("ch-f", "server")
        store.set_hold(ch, "blocked", "deps"); store.save(ch)
        self.assertIsNone(stages.next_executor(store.load("ch-f")))


class CheckTest(unittest.TestCase):
    def setUp(self):
        H.sandbox(self)

    def obs(self, **facts):
        from hd.observe import FakeObserver
        return FakeObserver(**facts)

    def test_client_full(self):
        ch = H.change("plug", "client", stage="dev", commit=SHA, evidence="re/evidence/plug")
        o = self.obs(tips={("ours", "client/plug"): SHA}, contains={("ours", "master", SHA): False},
                     evidence={"re/evidence/plug": ["repack.log", "logcat-plug.txt"]},
                     files={"repack.log": "telemetry: a\n" * 7})
        ok, its = check.run(ch, o)
        self.assertTrue(ok, check.render(ch, its))
        o.f["files"]["repack.log"] = "telemetry: a\n" * 6
        ok, its = check.run(ch, o)
        self.assertFalse(ok)
        self.assertTrue(any("6 telemetry lines" in t for _, t in its))

    def test_client_docs_only(self):
        ch = H.change("docs", "client", stage="dev", commit=SHA)
        o = self.obs(tips={("ours", "client/docs"): SHA}, contains={("ours", "master", SHA): False},
                     changed={("base000", SHA): ["docs/a.md", "README.md"]})
        ok, its = check.run(ch, o)
        self.assertTrue(ok, check.render(ch, its))

    def test_client_network_and_master(self):
        ch = H.change("net", "client", stage="dev", commit=SHA, network=True, evidence="re/evidence/net")
        o = self.obs(tips={("ours", "client/net"): SHA}, contains={("ours", "master", SHA): True},
                     evidence={"re/evidence/net": ["repack.log", "logcat.txt"]}, files={"repack.log": "telemetry: x\n" * 7})
        ok, its = check.run(ch, o)
        self.assertFalse(ok)
        texts = [t for o_, t in its if not o_]
        self.assertTrue(any("already on master" in t for t in texts))
        self.assertTrue(any("netqa" in t for t in texts))

    def test_server(self):
        ch = H.change("feat", "server", stage="dev", commit=SHA, evidence="re/evidence/feat",
                      pr="https://github.com/AlexPerrin/OpenRune-Server/pull/9")
        o = self.obs(tips={("fork", "content/feat"): SHA}, evidence={"re/evidence/feat": ["accuracy.md"]},
                     docs={"accuracy/feat.md": "# spec"}, ci={("content/feat", SHA): "success"})
        ok, its = check.run(ch, o)
        self.assertTrue(ok, check.render(ch, its))
        o.f["ci"][("content/feat", SHA)] = "pending"
        ok, its = check.run(ch, o)
        self.assertFalse(ok)
        o.f["ci"][("content/feat", SHA)] = "success"
        o.f["tips"][("fork", "content/feat")] = SHB
        ok, its = check.run(ch, o)
        self.assertTrue(any("not aaaaaaaaa" in t for _, t in its))

    def test_server_local_needs_no_pr(self):
        ch = H.change("loc", "server", stage="dev", commit=SHA, branch="local/loc", local_only=True, evidence="re/evidence/loc")
        o = self.obs(tips={("fork", "local/loc"): SHA}, evidence={"re/evidence/loc": ["x"]},
                     docs={"accuracy/loc.md": "x"}, ci={("local/loc", SHA): "success"})
        self.assertTrue(check.run(ch, o)[0])


class LeaseTest(unittest.TestCase):
    def setUp(self):
        H.sandbox(self)

    def test_take_needs_grant_and_free(self):
        ok, msg = leases.take("device", "cd1", "check")
        self.assertFalse(ok)
        self.assertIn("no unexpired grant", msg)
        leases.grant("device", "cd1", 60, change="plug")
        ok, _ = leases.take("device", "cd1", "check", pid=os.getpid())
        self.assertTrue(ok)
        ok, msg = leases.take("device", "cd2", "other")
        self.assertFalse(ok)
        self.assertIn("held by cd1", msg)
        self.assertTrue(leases.holds("device", "cd1"))
        self.assertEqual(leases.status("device")["change"], "plug")
        # the v2 mirror
        with open(os.path.join(os.environ["HD_LOCK_DIR"], "osrs-device.lock", "owner")) as f:
            self.assertIn("taking device (cd1 dev, check)", f.read())
        ok, _ = leases.release("device", "cd2")
        self.assertFalse(ok)
        ok, _ = leases.release("device", "cd1")
        self.assertTrue(ok)
        self.assertFalse(os.path.exists(os.path.join(os.environ["HD_LOCK_DIR"], "osrs-device.lock")))
        kinds = [e["action"] for e in events.read(kinds=["lease"])]
        self.assertEqual(kinds, ["deny", "grant", "take", "deny", "release"])

    def test_idle_and_reclaim(self):
        leases.grant("desktop", "sq1", 600)
        leases.take("desktop", "sq1", "gate")
        self.assertEqual(leases.idle("desktop")[0], False)   # heartbeat just now
        util.freeze(util.now() + __import__("datetime").timedelta(hours=4))
        self.addCleanup(util.freeze, None)
        idle, why = leases.idle("desktop")
        self.assertTrue(idle)
        self.assertIn("last heartbeat", why)
        leases.reclaim("desktop", why)
        self.assertIsNone(leases.status("desktop")["holder"])
        ev = events.read(kinds=["lease"])[-1]
        self.assertEqual((ev["action"], ev["holder"]), ("reclaim", "sq1"))
        self.assertIn("heartbeat", ev)

    def test_attached_pid_is_not_idle(self):
        leases.grant("desktop", "sd1", 600)
        leases.take("desktop", "sd1", "ab", pid=os.getpid())
        util.freeze(util.now() + __import__("datetime").timedelta(hours=4))
        self.addCleanup(util.freeze, None)
        self.assertEqual(leases.idle("desktop")[0], False)

    def test_drain_blocks_agents_not_daemon(self):
        """BR-R4: a pending deploy stops new grants from being taken; the daemon still takes."""
        leases.grant("device", "cd1", 60)
        leases.drain("device")
        ok, msg = leases.take("device", "cd1", "check")
        self.assertFalse(ok)
        self.assertIn("draining", msg)
        ok, _ = leases.take("device", leases.DAEMON, "deploy")
        self.assertTrue(ok)
        leases.release("device", leases.DAEMON)
        leases.undrain("device")
        ok, _ = leases.take("device", "cd1", "check")
        self.assertTrue(ok)

    def test_grant_expires(self):
        leases.grant("device", "cd1", 1)
        util.freeze(util.now() + __import__("datetime").timedelta(hours=4))
        self.addCleanup(util.freeze, None)
        ok, msg = leases.take("device", "cd1", "late")
        self.assertFalse(ok)

    def test_unknown_resource(self):
        with self.assertRaises(ValueError):
            leases.grant("toaster", "x", 1)


class PolicyTest(unittest.TestCase):
    def setUp(self):
        H.sandbox(self)

    def test_roles_and_tracks(self):
        self.assertEqual(policy.role_for("server", "dev"), "server-dev")
        self.assertEqual(policy.role_for("server", "gate"), "server-qa")
        self.assertEqual(policy.role_for("client", "gate"), "client-qa")
        self.assertEqual(policy.branch_track("fix/x"), "server")
        self.assertEqual(policy.branch_track("client/x"), "client")
        self.assertIsNone(policy.branch_track("acc/x"))
        self.assertEqual(policy.budget("server-dev")["usd"], 25)
        self.assertIn("device", policy.instances())


class UtilTest(unittest.TestCase):
    def test_local_time(self):
        self.assertEqual(util.local("2026-10-20T23:50:00Z"), "7:50 PM EDT 10-20")
        self.assertEqual(util.ago("2026-10-20T23:50:00Z", util.parse("2026-10-21T00:13:00Z")), "23 min")
        self.assertTrue(util.slug_ok("fix-genie"))
        self.assertFalse(util.slug_ok("Fix Genie"))


if __name__ == "__main__":
    unittest.main()
