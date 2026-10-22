---
slug: content-hunter-kebbit-tracking
track: server
title: Razor-backed kebbit tracking (Varlamore hunter trails)
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
Found by client task hub-batch-next (the user's own Plugin Hub list, orchestrator 2026-10-20): the plugin builds, passes netcheck/stubcheck and starts on the device, but OpenRune has no content for it to read, so it can't be exercised. Survey 2026-10-20 of re/upstream-pr/or (docs tree e71437e and local integration e5b18a8): integration's hunter has herbiboar, traps, rumours but no kebbit trails. Wiki: Razor-backed kebbit. Check upstream again first, spec with accuracy-check (OSRS Wiki and cache are the baseline), one PR per change, no stacking. Client re-test after it is live: hub razor-kebbit-tracking shows the trail

## Definition of done
- [ ] upstream re-checked for an existing implementation or PR before starting
- [ ] feature works end to end on the test server as on the wiki
- [ ] accuracy spec on the fork's docs branch (wiki/cache baseline per rule)
- [ ] fork PR open with green CI at the handed-over commit

## Log
- 2026-10-21T00:25:19Z migrated from v2 tasks: content-hunter-kebbit-tracking
- 2026-10-22T00:23:52Z RSProx recordings: 948 (rev 235, 74 matches; text kebbit), 944 (rev 235, 55 matches; text kebbit), 940 (rev 235, 46 matches; text kebbit), 3650 (rev 235, 36 matches; text kebbit), 939 (rev 235, 28 matches; text kebbit), 672 (rev 229, 27 matches; text kebbit), 3719 (rev 235, 23 matches; text kebbit), 3721 (rev 235, 23 matches; text kebbit) [+37 more] | 45 recordings; keys: npc 7210; loc 19456,19457,19966; text kebbit
