#!/usr/bin/env python3
"""RSProx coverage for the roadmap: which rsprox.net recordings of the official game contain each content-* change's
search keys, so a spec starts from real traffic (BR-X1: recordings beat the wiki). Not part of the daemon; run by
hand (or by the harness dev) when the roadmap changes.

  python3 tools/harness/rsprox_coverage.py [slug...] [--log] [--offline] [--manifest f] [--top n]

The manifest (harness/rsprox-coverage.yaml) names, per content-* slug, the keys to search by: ids per table (npc,
obj, loc, interface, varp, varbit, seq, spotanim) and strings (text: any string in a packet; message: MESSAGE_GAME).
A key is one rsprox.net search (POST /api/search, one condition); the reply is cached as
re/ref/rsprox/search/t<type>_<key>.json, the convention the hand searches already use, so a re-run is offline and a
new key costs one request. Per slug the recordings are the union over its keys, ranked by matches, and summarised in
one line: "RSProx recordings: <id> (rev <r>, <n> matches; <keys>), ... | keys: ..." or "RSProx recordings: none
(keys: ...)". `--log` appends that line to the change's Log (store.add_log), once per run, so the rule
"each content-* change has a Log line naming its RSProx recordings or 'none'" holds. A content-* change at
backlog/ready/dev without a manifest entry is reported and makes the exit code 1 (the manifest is incomplete, not
the recordings). Recordings may contain other players' names: this tool records ids, revisions and counts only.
"""
import argparse
import json
import os
import re
import sys
import time
import urllib.error
import urllib.request

import yaml

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from hd import ROOT, store  # noqa: E402

API = "https://rsprox.net/api/search"
TYPES = {"npc": 0, "obj": 1, "loc": 2, "mapsquare": 3, "message": 4, "varp": 5, "varbit": 6, "interface": 7,
         "seq": 8, "spotanim": 9, "text": 14}
PAGE = 100
MAX_PAGES = 5
RETRIES = 4          # on HTTP 429: wait Retry-After (default BACKOFF s, doubling), then try again
BACKOFF = 15
PAUSE = 1.0          # between requests, so a full run stays under rsprox.net's rate limit
LINE_PREFIX = "RSProx recordings"


REPO = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))   # this checkout (a worktree too)


def manifest_path():
    """The manifest of the checkout the tool runs from (a harness worktree carries its own); the store and the
    search cache are the main checkout's (ROOT)."""
    return os.environ.get("HD_RSPROX_MANIFEST") or os.path.join(REPO, "harness", "rsprox-coverage.yaml")


def search_dir():
    return os.environ.get("HD_RSPROX_SEARCH_DIR") or os.path.join(ROOT, "re", "ref", "rsprox", "search")


def cache_name(table, key):
    """t<type>_<key>.json with every non-alphanumeric run of the key as one underscore (the hand searches' names)."""
    safe = re.sub(r"[^A-Za-z0-9]+", "_", str(key)).strip("_")
    return f"t{TYPES[table]}_{safe}.json"


def fetch_page(table, key, page):
    body = json.dumps({"conditions": [{"type": TYPES[table], "query": str(key)}], "mode": "ANY", "page": page,
                       "limit": PAGE, "sort": "RELEVANCE"}).encode()
    req = urllib.request.Request(API, data=body, headers={"Content-Type": "application/json",
                                                           "User-Agent": "osrs-plugins-reverse rsprox_coverage"})
    with urllib.request.urlopen(req, timeout=30) as r:
        return json.loads(r.read().decode())


def fetch_patient(fetch, table, key, page, sleep=time.sleep):
    """fetch(...) with a wait and retry on HTTP 429 (Retry-After when given, else BACKOFF doubling); other errors
    propagate. Raises the last 429 after RETRIES."""
    wait = BACKOFF
    for attempt in range(RETRIES + 1):
        try:
            return fetch(table, key, page)
        except urllib.error.HTTPError as e:
            if e.code != 429 or attempt == RETRIES:
                raise
            retry_after = e.headers.get("Retry-After") if e.headers else None
            sleep(float(retry_after) if retry_after and retry_after.isdigit() else wait)
            wait *= 2


def search(table, key, fetch=fetch_page, offline=False, sleep=time.sleep):
    """The results list for one key: from the cache when present, else from rsprox.net (every page, then cached;
    429s waited out, PAUSE between requests). Offline and uncached: None (unknown, not 'no recordings')."""
    path = os.path.join(search_dir(), cache_name(table, key))
    if os.path.exists(path):
        with open(path) as f:
            data = json.load(f)
        return data.get("results", []) if isinstance(data, dict) else []
    if offline:
        return None
    results, page = [], 0
    while True:
        data = fetch_patient(fetch, table, key, page, sleep=sleep)
        results.extend(data.get("results", []))
        page += 1
        if not data.get("hasNext") or page >= MAX_PAGES:
            break
        sleep(PAUSE)
    sleep(PAUSE)
    os.makedirs(search_dir(), exist_ok=True)
    with open(path, "w") as f:
        json.dump({"results": results, "totalResults": len(results), "query": {"type": table, "key": key}}, f)
    return results


def keys_of(entry):
    """(table, key) pairs of a manifest entry, tables in TYPES order."""
    out = []
    for table in TYPES:
        for key in entry.get(table) or []:
            out.append((table, key))
    return out


def coverage(entry, lookup):
    """{recording id: {"rev": r, "matches": n, "keys": ["npc 5045", ...]}} over the entry's keys; lookup(table, key)
    returns a results list or None (unknown key, skipped)."""
    hits = {}
    for table, key in keys_of(entry):
        results = lookup(table, key)
        if results is None:
            continue
        for r in results:
            sub = r.get("submission") or {}
            rid = sub.get("id")
            if rid is None:
                continue
            h = hits.setdefault(rid, {"rev": sub.get("revision"), "matches": 0, "keys": []})
            h["matches"] += int(r.get("matches") or 0)
            h["keys"].append(f"{table} {key}")
    return hits


def key_text(entry):
    parts = []
    for table in TYPES:
        vals = entry.get(table) or []
        if vals:
            parts.append(f"{table} " + ",".join(str(v) for v in vals))
    return "; ".join(parts)


def line(entry, hits, top=8):
    """The Log line: the top recordings by matches (ids, revision, counts, the keys that hit) or 'none'."""
    keys = key_text(entry)
    if not hits:
        return f"{LINE_PREFIX}: none (rsprox.net search; keys: {keys})"
    ranked = sorted(hits.items(), key=lambda kv: (-kv[1]["matches"], kv[0]))
    shown = [f"{rid} (rev {h['rev']}, {h['matches']} matches; {', '.join(h['keys'])})" for rid, h in ranked[:top]]
    more = f" [+{len(ranked) - top} more]" if len(ranked) > top else ""
    return f"{LINE_PREFIX}: {', '.join(shown)}{more} | {len(ranked)} recordings; keys: {keys}"


def content_changes():
    """Slugs of the content-* changes still ahead of a spec (backlog, ready, dev)."""
    return sorted(s for s, ch in store.index().items()
                  if s.startswith("content-") and ch.stage in ("backlog", "ready", "dev"))


def has_line(ch):
    return any(m.startswith(LINE_PREFIX) for _, m in ch.log_lines())


def run(slugs=None, log=False, offline=False, top=8, lookup=None, out=print):
    """Print one line per slug (or every manifest entry); `--log` appends it to the change. Returns the exit code:
    1 when a content-* change ahead of a spec has no manifest entry."""
    with open(manifest_path()) as f:
        manifest = yaml.safe_load(f) or {}
    lookup = lookup or (lambda t, k: search(t, k, offline=offline))
    missing = [s for s in content_changes() if s not in manifest]
    for slug in slugs or sorted(manifest):
        entry = manifest.get(slug)
        if entry is None:
            out(f"{slug}: not in the manifest")
            continue
        text = line(entry, coverage(entry, lookup), top=top)
        out(f"{slug}: {text}")
        if log and store.exists(slug):
            ch = store.load(slug)
            store.add_log(ch, text)
            store.save(ch)
    for slug in missing:
        out(f"{slug}: no manifest entry (add its keys to {os.path.relpath(manifest_path(), ROOT)})")
    return 1 if missing else 0


def main(argv=None):
    ap = argparse.ArgumentParser(description=__doc__.split("\n")[0])
    ap.add_argument("slugs", nargs="*", help="content-* slugs (default: every manifest entry)")
    ap.add_argument("--log", action="store_true", help="append the line to each change's Log")
    ap.add_argument("--offline", action="store_true", help="cached searches only; an uncached key is skipped")
    ap.add_argument("--manifest", help="manifest path (default harness/rsprox-coverage.yaml)")
    ap.add_argument("--top", type=int, default=8, help="recordings named per line (default 8)")
    a = ap.parse_args(argv)
    if a.manifest:
        os.environ["HD_RSPROX_MANIFEST"] = a.manifest
    return run(a.slugs, log=a.log, offline=a.offline, top=a.top)


if __name__ == "__main__":
    sys.exit(main())
