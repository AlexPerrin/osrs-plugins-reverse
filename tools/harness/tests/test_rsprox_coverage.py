"""rsprox_coverage: one cached search per key (never twice), the Log line names the recordings or 'none', `--log`
writes it to every content-* change in the manifest, and a content-* change missing from the manifest is reported."""
import io
import json
import os
import unittest
import urllib.error

import hd_helpers as H

import rsprox_coverage as rc
from hd import store


def result(rid, rev, matches):
    return {"submission": {"id": rid, "revision": rev}, "matches": matches}


class CacheTest(unittest.TestCase):
    def setUp(self):
        d = H.sandbox(self)
        H.env(self, HD_RSPROX_SEARCH_DIR=os.path.join(d, "search"))

    def test_cache_name_follows_the_hand_searches(self):
        self.assertEqual(rc.cache_name("npc", 5045), "t0_5045.json")
        self.assertEqual(rc.cache_name("text", "Witch's Potion"), "t14_Witch_s_Potion.json")

    def test_search_fetches_once_then_reads_the_cache(self):
        calls = []

        def fetch(table, key, page):
            calls.append((table, key, page))
            return {"results": [result(10, 233, 4)], "hasNext": page == 0}
        first = rc.search("npc", 5045, fetch=fetch, sleep=lambda s: None)
        second = rc.search("npc", 5045, fetch=fetch, sleep=lambda s: None)
        self.assertEqual(calls, [("npc", 5045, 0), ("npc", 5045, 1)])   # both pages, once
        self.assertEqual(len(first), 2)
        self.assertEqual(second, first)
        with open(os.path.join(rc.search_dir(), "t0_5045.json")) as f:
            self.assertEqual(json.load(f)["query"], {"type": "npc", "key": 5045})

    def test_429_is_waited_out_then_retried(self):
        calls, waits = [], []

        def fetch(table, key, page):
            calls.append(page)
            if len(calls) == 1:
                raise urllib.error.HTTPError(rc.API, 429, "Too Many Requests", {"Retry-After": "7"}, io.BytesIO())
            return {"results": [result(3, 230, 1)], "hasNext": False}
        out = rc.search("text", "x", fetch=fetch, sleep=waits.append)
        self.assertEqual([r["submission"]["id"] for r in out], [3])
        self.assertEqual(calls, [0, 0])
        self.assertEqual(waits[0], 7.0)                       # Retry-After honoured
        self.assertEqual(waits[1:], [rc.PAUSE])               # then the pause between requests

    def test_429_gives_up_after_retries(self):
        waits = []

        def fetch(table, key, page):
            raise urllib.error.HTTPError(rc.API, 429, "Too Many Requests", {}, io.BytesIO())
        with self.assertRaises(urllib.error.HTTPError):
            rc.search("text", "y", fetch=fetch, sleep=waits.append)
        self.assertEqual(waits, [rc.BACKOFF * 2 ** i for i in range(rc.RETRIES)])
        self.assertFalse(os.path.exists(os.path.join(rc.search_dir(), "t14_y.json")))   # nothing cached

    def test_offline_uncached_is_unknown(self):
        self.assertIsNone(rc.search("text", "x", fetch=None, offline=True))


class LineTest(unittest.TestCase):
    def test_none(self):
        entry = {"npc": [5045, 5044], "text": ["Druidic Ritual"]}
        self.assertEqual(rc.line(entry, {}),
                         "RSProx recordings: none (rsprox.net search; keys: npc 5045,5044; text Druidic Ritual)")

    def test_recordings_ranked_by_matches_with_their_keys(self):
        entry = {"npc": [963], "text": ["Kalphite Queen"]}
        table = {("npc", 963): [result(1846, 227, 133), result(3088, 231, 15)],
                 ("text", "Kalphite Queen"): [result(3088, 231, 2)]}
        hits = rc.coverage(entry, lambda t, k: table.get((t, k)))
        text = rc.line(entry, hits, top=1)
        self.assertTrue(text.startswith("RSProx recordings: 1846 (rev 227, 133 matches; npc 963) [+1 more] | "
                                        "2 recordings; keys: npc 963; text Kalphite Queen"), text)
        self.assertEqual(hits[3088]["matches"], 17)
        self.assertEqual(hits[3088]["keys"], ["npc 963", "text Kalphite Queen"])

    def test_unknown_key_is_skipped(self):
        hits = rc.coverage({"npc": [1], "text": ["x"]}, lambda t, k: None if t == "text" else [result(5, 230, 1)])
        self.assertEqual(list(hits), [5])


class LogTest(unittest.TestCase):
    def setUp(self):
        d = H.sandbox(self)
        self.manifest = os.path.join(d, "rsprox-coverage.yaml")
        H.env(self, HD_RSPROX_MANIFEST=self.manifest, HD_RSPROX_SEARCH_DIR=os.path.join(d, "search"))
        H.change("content-a", "server", stage="ready", ticked=False)
        H.change("content-b", "server", stage="ready", ticked=False)
        H.change("other", "server", stage="ready", ticked=False)

    def write_manifest(self, text):
        with open(self.manifest, "w") as f:
            f.write(text)

    def test_log_writes_one_line_per_content_change(self):
        self.write_manifest("content-a:\n  npc: [1]\ncontent-b:\n  text: [nothing]\n")
        table = {("npc", 1): [result(7, 230, 3)], ("text", "nothing"): []}
        out = []
        rc_ = rc.run(log=True, lookup=lambda t, k: table[(t, k)], out=out.append)
        self.assertEqual(rc_, 0)
        a, b = store.load("content-a"), store.load("content-b")
        self.assertTrue(rc.has_line(a) and rc.has_line(b))
        self.assertIn("RSProx recordings: 7 (rev 230, 3 matches; npc 1) | 1 recordings; keys: npc 1", a.body)
        self.assertIn("RSProx recordings: none (rsprox.net search; keys: text nothing)", b.body)
        self.assertFalse(rc.has_line(store.load("other")))
        self.assertEqual(out[0], "content-a: " + rc.line({"npc": [1]}, rc.coverage({"npc": [1]}, lambda t, k: table[(t, k)])))

    def test_missing_manifest_entry_is_reported(self):
        self.write_manifest("content-a:\n  npc: [1]\n")
        out = []
        rc_ = rc.run(log=False, lookup=lambda t, k: [], out=out.append)
        self.assertEqual(rc_, 1)
        self.assertTrue(any(l.startswith("content-b: no manifest entry") for l in out), out)
        self.assertFalse(rc.has_line(store.load("content-a")))   # no --log, no write

    def test_merged_content_changes_need_no_entry(self):
        ch = store.load("content-b")
        ch["stage"] = "merged"
        store.save(ch)
        self.write_manifest("content-a:\n  npc: [1]\n")
        self.assertEqual(rc.run(lookup=lambda t, k: [], out=lambda s: None), 0)


if __name__ == "__main__":
    unittest.main()
