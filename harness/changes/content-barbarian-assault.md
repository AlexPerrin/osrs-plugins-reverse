---
slug: content-barbarian-assault
track: server
title: Barbarian Assault minigame (waves, role calls)
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
Found by client task hub-batch-next (the user's own Plugin Hub list, orchestrator 2026-10-20): the plugin builds, passes netcheck/stubcheck and starts on the device, but OpenRune has no content for it to read, so it can't be exercised. Survey 2026-10-20 of re/upstream-pr/or (docs tree e71437e and local integration e5b18a8): no minigame module (content/minigames has gauntlet only). Wiki: Barbarian Assault. Check upstream again first, spec with accuracy-check (OSRS Wiki and cache are the baseline), one PR per change, no stacking. Client re-test after it is live: hub ba-minigame and ba-call-highlight show calls/timers in a wave

## Definition of done
- [ ] upstream re-checked for an existing implementation or PR before starting
- [ ] feature works end to end on the test server as on the wiki
- [ ] accuracy spec on the fork's docs branch (wiki/cache baseline per rule)
- [ ] fork PR open with green CI at the handed-over commit

## Log
- 2026-10-21T00:25:19Z migrated from v2 tasks: content-barbarian-assault
- 2026-10-22T00:23:52Z RSProx recordings: 1434 (rev 224, 6 matches; npc 1656, npc 1657), 1533 (rev 224, 6 matches; npc 1656, npc 1657), 346 (rev 230, 3 matches; npc 1656, npc 1657), 724 (rev 231, 3 matches; npc 1656, npc 1657), 1060 (rev 235, 3 matches; npc 1656, npc 1657), 1062 (rev 235, 3 matches; npc 1656, npc 1657), 1441 (rev 224, 3 matches; npc 1656, npc 1657), 2875 (rev 230, 3 matches; npc 1656, npc 1657) [+14 more] | 22 recordings; keys: npc 1656,1657,1666,5739,5777; text Barbarian Assault
