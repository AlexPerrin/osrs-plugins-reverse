---
slug: content-forestry-events
track: server
title: Forestry woodcutting events (Rising Roots, Struggling Sapling, ...)
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
Found by client task hub-batch-next (the user's own Plugin Hub list, orchestrator 2026-10-20): the plugin builds, passes netcheck/stubcheck and starts on the device, but OpenRune has no content for it to read, so it can't be exercised. Survey 2026-10-20 of re/upstream-pr/or (docs tree e71437e and local integration e5b18a8): content/skills/woodcutting has plain Woodcutting.kt only. Wiki: Forestry. Check upstream again first, spec with accuracy-check (OSRS Wiki and cache are the baseline), one PR per change, no stacking. Client re-test after it is live: hub forestrycc tracks events (also via the Forestry CC clan chat)

## Definition of done
- [ ] upstream re-checked for an existing implementation or PR before starting
- [ ] feature works end to end on the test server as on the wiki
- [ ] accuracy spec on the fork's docs branch (wiki/cache baseline per rule)
- [ ] fork PR open with green CI at the handed-over commit

## Log
- 2026-10-21T00:25:19Z migrated from v2 tasks: content-forestry-events
- 2026-10-22T00:23:52Z RSProx recordings: 1723 (rev 227, 98 matches; npc 12119, npc 12519, npc 12543, npc 12537, npc 12163, text Forestry), 1104 (rev 237, 86 matches; npc 12119, npc 12514, npc 12543, npc 12537, npc 12163), 1098 (rev 237, 84 matches; npc 12119, npc 12514, npc 12543, npc 12537, npc 12163, text Forestry), 1718 (rev 227, 23 matches; npc 12119, npc 12514, text Forestry), 1108 (rev 237, 20 matches; npc 12119, npc 12514, npc 12519, npc 12163), 1105 (rev 237, 15 matches; npc 12519, npc 12543, npc 12537, npc 12163), 1855 (rev 227, 15 matches; npc 12119, npc 12514, npc 12519, npc 12537, npc 12163), 819 (rev 234, 14 matches; npc 12119) [+139 more] | 147 recordings; keys: npc 12119,12514,12519,12543,12537,12163; text Forestry
