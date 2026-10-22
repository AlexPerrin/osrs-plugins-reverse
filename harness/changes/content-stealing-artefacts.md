---
slug: content-stealing-artefacts
track: server
title: Stealing artefacts (Port Piscarilius, Captain Khaled)
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
Found by client task hub-batch-next (the user's own Plugin Hub list, orchestrator 2026-10-20): the plugin builds, passes netcheck/stubcheck and starts on the device, but OpenRune has no content for it to read, so it can't be exercised. Survey 2026-10-20 of re/upstream-pr/or (docs tree e71437e and local integration e5b18a8): nothing for artefact/khaled in skills/thieving. Wiki: Stealing artefacts. Check upstream again first, spec with accuracy-check (OSRS Wiki and cache are the baseline), one PR per change, no stacking. Client re-test after it is live: hub stealing-artefacts marks the target house

## Definition of done
- [ ] upstream re-checked for an existing implementation or PR before starting
- [ ] feature works end to end on the test server as on the wiki
- [ ] accuracy spec on the fork's docs branch (wiki/cache baseline per rule)
- [ ] fork PR open with green CI at the handed-over commit

## Log
- 2026-10-21T00:25:19Z migrated from v2 tasks: content-stealing-artefacts
- 2026-10-22T00:23:52Z RSProx recordings: 1849 (rev 227, 1 matches; text Captain Khaled), 3270 (rev 234, 1 matches; npc 6764), 3467 (rev 235, 1 matches; npc 6764) | 3 recordings; keys: npc 6971,6972,6764; text Stealing artefacts,Captain Khaled
