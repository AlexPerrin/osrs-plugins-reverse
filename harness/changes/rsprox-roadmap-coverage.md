---
slug: rsprox-roadmap-coverage
track: harness
title: RSProx coverage for the roadmap items
prio: P3
role: harness-dev
stage: live
hold: null
deps: []
branch: harness/rsprox-roadmap-coverage
commit: 979726975a2e1ae6767cd6fa5cce3491e35b3401
local_only: false
owner: null
instances: []
evidence: /home/alex/osrs-plugins-reverse/re/evidence/rsprox-roadmap-coverage
summary: '21/21 content-* changes have an ''RSProx recordings: ...'' Log line (20 with recordings, ids/rev/matches/keys; none had zero after Khaled''s multi NPC was added). New tools/harness/rsprox_coverage.py
  + harness/rsprox-coverage.yaml (search keys per change, gameval ids from osrs-mcp) + test_rsprox_coverage.py (11 tests: one cached search per key, 429 backoff, line format, --log per content-* change,
  missing manifest entry reported); docs/harness-v3.md. 104 new cached searches under re/ref/rsprox/search/. Suite 181 OK at 979726975a2e1ae6767cd6fa5cce3491e35b3401 (rebased on master 8ed1d5a). Note: test_live.test_lock_dir_holds_every_file
  fails whenever a real /tmp/osrs-desktop.lock exists (sq31 held it during one run); it passed in the final run; not changed (unrelated).'
live_commit: 979726975a2e1ae6767cd6fa5cce3491e35b3401
cost:
  runs: 1
  turns: 99
  minutes: 12.0
  usd: 5.09
created: '2026-10-20T01:42:44Z'
updated: '2026-10-22T00:25:50Z'
---
## Change
Find which roadmap items (content-* tasks) have RSProx recordings in re/ref/rsprox/ (search by NPC id / TEXT) and note them in each task's Log, so specs start from real traffic.

## Definition of done
- [x] each content-* roadmap task has a Log line naming its RSProx recordings or 'none'

## Log
- 2026-10-21T00:25:19Z migrated from v2 tasks: rsprox-roadmap-coverage
- 2026-10-22T00:13:09Z ready -> dev: dispatched to hd3
- 2026-10-22T00:13:09Z run hd3 started (harness-dev, stage dev, instances none)
- 2026-10-22T00:24:35Z handover harness/rsprox-roadmap-coverage@979726975: 21/21 content-* changes have an 'RSProx recordings: ...' Log line (20 with recordings, ids/rev/matches/keys; none had zero after Khaled's multi NPC was added). New tools/harness/rsprox_coverage.py + harness/rsprox-coverage.yaml (search keys per change, gameval ids from osrs-mcp) + test_rsprox_coverage.py (11 tests: one cached search per key, 429 backoff, line format, --log per content-* change, missing manifest entry reported); docs/harness-v3.md. 104 new cached searches under re/ref/rsprox/search/. Suite 181 OK at 979726975a2e1ae6767cd6fa5cce3491e35b3401 (rebased on master 8ed1d5a). Note: test_live.test_lock_dir_holds_every_file fails whenever a real /tmp/osrs-desktop.lock exists (sq31 held it during one run); it passed in the final run; not changed (unrelated).
- 2026-10-22T00:24:52Z run hd3 ended: 99 turns, 12.0 min, $5.09, exit ok
- 2026-10-22T00:24:52Z dev -> checked: hand-over check PASS
- 2026-10-22T00:24:54Z ship: merged into master f61820f, pushed
- 2026-10-22T00:25:50Z checked -> live: observed
