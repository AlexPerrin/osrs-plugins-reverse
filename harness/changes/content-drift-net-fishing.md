---
slug: content-drift-net-fishing
track: server
title: Drift net fishing (Fossil Island underwater)
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
Found by client task hub-batch-next (the user's own Plugin Hub list, orchestrator 2026-10-20): the plugin builds, passes netcheck/stubcheck and starts on the device, but OpenRune has no content for it to read, so it can't be exercised. Survey 2026-10-20 of re/upstream-pr/or (docs tree e71437e and local integration e5b18a8): no drift net code; PROGRESS.md '- [ ] Drift net fishing'. Wiki: Drift net fishing. Check upstream again first, spec with accuracy-check (OSRS Wiki and cache are the baseline), one PR per change, no stacking. Client re-test after it is live: hub drift-net-improved and core driftnet highlight the nets and shoals

## Definition of done
- [ ] upstream re-checked for an existing implementation or PR before starting
- [ ] feature works end to end on the test server as on the wiki
- [ ] accuracy spec on the fork's docs branch (wiki/cache baseline per rule)
- [ ] fork PR open with green CI at the handed-over commit

## Log
- 2026-10-21T00:25:19Z migrated from v2 tasks: content-drift-net-fishing
- 2026-10-22T00:23:52Z RSProx recordings: 715 (rev 231, 339 matches; npc 7782), 712 (rev 231, 180 matches; npc 7782), 3299 (rev 234, 172 matches; npc 7782), 3273 (rev 234, 24 matches; npc 7782), 312 (rev 230, 14 matches; npc 7782), 73 (rev 227, 9 matches; npc 7782), 1718 (rev 227, 1 matches; text drift net), 3035 (rev 231, 1 matches; text drift net) | 8 recordings; keys: npc 7782; loc 30952,30953,30954,30955; text drift net
