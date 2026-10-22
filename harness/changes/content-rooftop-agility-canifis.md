---
slug: content-rooftop-agility-canifis
track: server
title: Canifis Rooftop Agility course and marks of grace
prio: P3
role: server-dev
stage: backlog
hold: null
deps: []
branch: null
commit: null
local_only: false
owner: null
instances: []
created: '2026-10-20T04:57:23Z'
updated: '2026-10-22T00:23:52Z'
---
## Change
Found by client task hub-batch-next (the user's own Plugin Hub list, orchestrator 2026-10-20): the plugin builds, passes netcheck/stubcheck and starts on the device, but OpenRune has no content for it to read, so it can't be exercised. Survey 2026-10-20 of re/upstream-pr/or (docs tree e71437e and local integration e5b18a8): no agility module; PROGRESS.md 'Agility | nothing yet', '- [ ] Rooftop Agility courses'. Wiki: Canifis Rooftop Course, Mark of grace. Check upstream again first, spec with accuracy-check (OSRS Wiki and cache are the baseline), one PR per change, no stacking. Client re-test after it is live: hub afk-marks-canafis (also needs main's NET-RAW decision: NTP UDP client) and core agility marks

## Definition of done
- [ ] upstream re-checked for an existing implementation or PR before starting
- [ ] feature works end to end on the test server as on the wiki
- [ ] accuracy spec on the fork's docs branch (wiki/cache baseline per rule)
- [ ] fork PR open with green CI at the handed-over commit

## Log
- 2026-10-21T00:25:19Z migrated from v2 tasks: content-rooftop-agility-canifis
- 2026-10-22T00:23:52Z RSProx recordings: 91 (rev 227, 158 matches; loc 14843, loc 14844, loc 14845, loc 14846, loc 14847, loc 14848, loc 14894, loc 14897), 90 (rev 227, 101 matches; loc 14843, loc 14844, loc 14845, loc 14846, loc 14847, loc 14848, loc 14894, loc 14897), 1135 (rev 237, 12 matches; loc 14843, loc 14844, loc 14845, loc 14846, loc 14847, loc 14848, loc 14894, loc 14897, text Mark of grace), 1718 (rev 227, 2 matches; text Mark of grace), 787 (rev 234, 1 matches; text Mark of grace), 1098 (rev 237, 1 matches; text Mark of grace) | 6 recordings; keys: loc 14843,14844,14845,14846,14847,14848,14894,14897; text Mark of grace
