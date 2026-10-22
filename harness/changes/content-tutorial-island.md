---
slug: content-tutorial-island
track: server
title: 'Roadmap: content/tutorial-island (Learning the Ropes)'
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
created: '2026-10-20T01:42:42Z'
updated: '2026-10-22T00:23:52Z'
---
## Change
Server roadmap (docs/rsps-coverage.md, 2026-10-19): content/tutorial-island (Learning the Ropes). Missing upstream, no open upstream PR by anyone else. Reference: re/ref/ Project Gielinor world/src/tutorial_*.rs (OSRS-era); LostCity tutorial/ is the 2004 version (evidence for guesses only, never copied). Size L. Notes: OSRS 2021 rework is the target. Check upstream again first, spec with accuracy-check (OSRS Wiki and cache are the baseline), one PR per change, no stacking.

## Definition of done
- [ ] upstream re-checked for an existing implementation or PR before starting
- [ ] quest/feature completable end to end on the test server as on the wiki
- [ ] accuracy/content-tutorial-island.md on the fork's docs branch (wiki/cache baseline per rule, RSProx comparison recorded)
- [ ] fork PR open with green CI at the handed-over commit
- [ ] one change only, branch off upstream main (no stacking)

## Log
- 2026-10-21T00:25:19Z migrated from v2 tasks: content-tutorial-island
- 2026-10-22T00:23:52Z RSProx recordings: 379 (rev 231, 6 matches; npc 8503, npc 3312, text Gielinor Guide), 1556 (rev 225, 5 matches; npc 8503, npc 3312, text Gielinor Guide), 2634 (rev 229, 5 matches; npc 8503, npc 3312, text Gielinor Guide), 2661 (rev 229, 5 matches; npc 8503, npc 3312, text Gielinor Guide), 1403 (rev 223, 4 matches; npc 8503, npc 3312, text Gielinor Guide), 1559 (rev 225, 3 matches; npc 8503, npc 3312, text Gielinor Guide), 1561 (rev 225, 3 matches; npc 8503, npc 3312, text Gielinor Guide), 822 (rev 234, 2 matches; text Gielinor Guide) [+6 more] | 14 recordings; keys: npc 8503,3305,3312,3311,3307,3310,3309; text Gielinor Guide
