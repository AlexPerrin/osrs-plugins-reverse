---
slug: content-sailing
track: server
title: 'Sailing skill: boats and Barracuda Trials'
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
Found by client task hub-batch-next (the user's own Plugin Hub list, orchestrator 2026-10-20): the plugin builds, passes netcheck/stubcheck and starts on the device, but OpenRune has no content for it to read, so it can't be exercised. Survey 2026-10-20 of re/upstream-pr/or (docs tree e71437e and local integration e5b18a8): only sea-creature drop tables. Wiki: Sailing. Large; also needs the client host's WorldEntity API (boats) and probably a newer RuneLite API than our 1.13.1. Check upstream again first, spec with accuracy-check (OSRS Wiki and cache are the baseline), one PR per change, no stacking. Client re-test after it is live: hub sailing and barracuda-trials-pathfinder

## Definition of done
- [ ] upstream re-checked for an existing implementation or PR before starting
- [ ] feature works end to end on the test server as on the wiki
- [ ] accuracy spec on the fork's docs branch (wiki/cache baseline per rule)
- [ ] fork PR open with green CI at the handed-over commit

## Log
- 2026-10-21T00:25:19Z migrated from v2 tasks: content-sailing
- 2026-10-22T00:23:52Z RSProx recordings: 381 (rev 231, 41 matches; text Sailing), 3467 (rev 235, 36 matches; text Sailing), 907 (rev 235, 32 matches; npc 15378, text Sailing), 3455 (rev 235, 27 matches; npc 15378, text Sailing), 948 (rev 235, 25 matches; npc 15378, text Sailing), 1584 (rev 225, 24 matches; text Sailing), 3457 (rev 235, 21 matches; text Sailing), 3461 (rev 235, 19 matches; npc 15378, text Sailing) [+505 more] | 513 recordings; keys: npc 15378,15241,15496; text Sailing
