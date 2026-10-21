"""hd.daemon: the loop (run once, errors as events), the server routes, approve/deny over HTTP, the digest."""
import json
import os
import sys
import types
import unittest
import urllib.error
import urllib.request

import hd_helpers as H

from hd import daemon, events, store, util
from hd import tick as tick_mod


def at(ts, kind, **f):
    util.freeze(ts)
    return events.emit(kind, **f)


class RunTest(unittest.TestCase):
    def setUp(self):
        H.sandbox(self)
        self.addCleanup(util.freeze, None)
        self.orig = tick_mod.tick
        self.addCleanup(setattr, tick_mod, "tick", self.orig)

    def test_once_writes_daemon_json(self):
        tick_mod.tick = lambda obs=None, dry_run=False: ["x"]
        util.freeze("2026-10-20T20:00:00Z")
        rec = daemon.run(interval=1, once=True)
        d = store.read_json(os.path.join(store.state_dir(), "daemon.json"))
        self.assertEqual(d["ticks"], 1)
        self.assertEqual(d["pid"], os.getpid())
        self.assertEqual(d["interval"], 1)
        self.assertEqual(d["last_tick"], "2026-10-20T20:00:00Z")
        self.assertEqual(d["actions"], 1)
        self.assertIsNotNone(d["tick_seconds"])
        self.assertEqual(rec["ticks"], 1)
        self.assertEqual([e["kind"] for e in events.read(kinds=["error"])], [])
        st = daemon.status()
        self.assertTrue(st["alive"])
        self.assertEqual(st["last_tick_local"], "4:00 PM EDT 10-20")

    def test_tick_error_is_an_event(self):
        def boom(obs=None, dry_run=False):
            raise RuntimeError("no")
        tick_mod.tick = boom
        daemon.run(interval=1, once=True)
        errs = events.read(kinds=["error"])
        self.assertEqual(len(errs), 1)
        self.assertEqual(errs[0]["step"], "tick")
        self.assertIn("RuntimeError", errs[0]["error"])
        self.assertEqual(daemon.status()["ticks"], 1)

    def test_not_implemented_tick_is_an_event(self):
        daemon.run(interval=1, once=True)   # the real stub raises NotImplementedError
        self.assertEqual(events.read(kinds=["error"])[0]["step"], "tick")

    def test_stop_without_daemon(self):
        self.assertTrue(daemon.stop()["stopped"])
        self.assertFalse(daemon.status()["alive"])


class DigestTest(unittest.TestCase):
    def setUp(self):
        H.sandbox(self)
        self.addCleanup(util.freeze, None)

    def test_news_once(self):
        util.freeze("2026-10-20T20:00:00Z")
        self.assertEqual(daemon.digest(), "")
        at("2026-10-20T20:01:00Z", "approval", action="created", approval="ap1", approval_kind="upstream", change="feat")
        at("2026-10-20T20:01:00Z", "lease", action="take", resource="device", holder="cd1")   # not news
        at("2026-10-20T20:02:00Z", "hold", change="feat", hold="failed", reason="spotless")
        at("2026-10-20T20:03:00Z", "deploy", result="ok", integration="a" * 40, image="osrs-openrune:241")
        at("2026-10-20T20:04:00Z", "batch", batch="t1", step="rolled-back", detail="timeout")
        at("2026-10-20T20:05:00Z", "heal", action="deadlock", resource="device", detect_min=1, recover_min=3)
        at("2026-10-20T20:06:00Z", "approval", action="created", approval="ap2", approval_kind="decision", change="feat")
        util.freeze("2026-10-20T20:07:00Z")
        text = daemon.digest()
        self.assertIn("new approval: ap1 (upstream feat)", text)
        self.assertIn("decision needed: ap2", text)
        self.assertIn("feat failed: spotless", text)
        self.assertIn("deploy ok", text)
        self.assertIn("batch t1 rolled-back: timeout", text)
        self.assertIn("deadlock resolved", text)
        self.assertNotIn("lease", text)
        self.assertIn("4:01 PM EDT 10-20", text)
        with open(os.path.join(store.state_dir(), "digest.md")) as f:
            self.assertIn(text, f.read())
        self.assertEqual(daemon.digest(), "")
        at("2026-10-20T20:08:00Z", "approval", action="approved", approval="ap1", note="go")
        self.assertIn("approval ap1 approved: go", daemon.digest())
        self.assertEqual(daemon.digest(), "")
        self.assertIn("new approval: ap1", daemon.digest(since="2026-10-20T20:00:00Z"))


class ServeTest(unittest.TestCase):
    def setUp(self):
        H.sandbox(self)
        self.addCleanup(util.freeze, None)
        H.change("feat", track="server", stage="ready")
        store.rec_save("approvals", "ap1", {"kind": "upstream", "change": "feat", "state": "pending",
                                            "commands": [["gh", "pr", "create"]]})
        at("2026-10-20T20:00:00Z", "stage", change="feat", **{"from": "backlog", "to": "ready"})
        self.srv = daemon.serve(0)
        self.addCleanup(self.srv.shutdown)
        self.addCleanup(self.srv.server_close)
        self.base = f"http://127.0.0.1:{self.srv.server_address[1]}"

    def get(self, path):
        with urllib.request.urlopen(self.base + path, timeout=10) as r:
            return r.status, r.headers.get("Content-Type"), r.read().decode()

    def post(self, path, body):
        req = urllib.request.Request(self.base + path, data=json.dumps(body).encode(),
                                     headers={"Content-Type": "application/json"}, method="POST")
        try:
            with urllib.request.urlopen(req, timeout=10) as r:
                return r.status, json.loads(r.read().decode())
        except urllib.error.HTTPError as e:
            return e.code, json.loads(e.read().decode())

    def test_routes(self):
        code, ctype, body = self.get("/")
        self.assertEqual((code, ctype), (200, "text/html; charset=utf-8"))
        self.assertIn("<title>harness (1)</title>", body)
        code, ctype, body = self.get("/api/view")
        self.assertEqual(code, 200)
        self.assertIn("application/json", ctype)
        v = json.loads(body)
        self.assertEqual([r["slug"] for r in v["changes"]], ["feat"])
        self.assertEqual(v["pending"], 1)
        code, _, body = self.get("/events?kind=stage&change=feat&limit=5")
        self.assertEqual(code, 200)
        evs = json.loads(body)
        self.assertEqual(len(evs), 1)
        self.assertEqual(evs[0]["to"], "ready")
        self.assertEqual(json.loads(self.get("/events?kind=lease")[2]), [])
        code, ctype, body = self.get("/text")
        self.assertEqual((code, ctype), (200, "text/plain; charset=utf-8"))
        self.assertIn("== Changes", body)
        with self.assertRaises(urllib.error.HTTPError) as cm:
            self.get("/nope")
        self.assertEqual(cm.exception.code, 404)
        self.assertEqual(self.post("/api/nope", {})[0], 404)
        self.assertEqual(daemon.PORT, self.srv.server_address[1])

    def test_approve_and_deny(self):
        calls = []
        stub = types.ModuleType("hd.approvals")
        stub.approve = lambda aid, note="": calls.append(("approve", aid, note)) or {"id": aid, "state": "approved"}
        stub.deny = lambda aid, note="": calls.append(("deny", aid, note)) or {"id": aid, "state": "denied"}
        import hd
        real = sys.modules.get("hd.approvals")
        sys.modules["hd.approvals"] = stub
        old_attr = getattr(hd, "approvals", None)
        hd.approvals = stub
        def restore():
            if real is not None:
                sys.modules["hd.approvals"] = real
            else:
                sys.modules.pop("hd.approvals", None)
            if old_attr is not None:
                hd.approvals = old_attr
        self.addCleanup(restore)
        code, j = self.post("/api/approve", {"id": "ap1", "note": "ok"})
        self.assertEqual((code, j["ok"], j["state"]), (200, True, "approved"))
        code, j = self.post("/api/deny", {"id": "ap1", "note": "no"})
        self.assertEqual((code, j["ok"], j["state"]), (200, True, "denied"))
        self.assertEqual(calls, [("approve", "ap1", "ok"), ("deny", "ap1", "no")])
        self.assertEqual(self.post("/api/approve", {"note": "x"})[0], 400)

    def test_not_implemented_is_501(self):
        stub = types.ModuleType("hd.approvals")
        def ni(aid, note=""):
            raise NotImplementedError
        stub.approve = stub.deny = ni
        import hd
        real = sys.modules.get("hd.approvals")
        sys.modules["hd.approvals"] = stub
        old_attr = getattr(hd, "approvals", None)
        hd.approvals = stub
        def restore():
            if real is not None:
                sys.modules["hd.approvals"] = real
            if old_attr is not None:
                hd.approvals = old_attr
        self.addCleanup(restore)
        code, j = self.post("/api/approve", {"id": "ap1"})
        self.assertEqual((code, j["ok"]), (501, False))


if __name__ == "__main__":
    unittest.main()
