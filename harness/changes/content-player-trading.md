---
slug: content-player-trading
track: server
title: 'Roadmap: content/player-trading (core)'
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
Server roadmap (docs/rsps-coverage.md, 2026-10-19): content/player-trading (core). Missing upstream, no open upstream PR by anyone else. Reference: re/ref/ LostCity interface_trade (evidence for guesses only, never copied). Size M. Notes: trade request, two-screen confirm, both inventories. Check upstream again first, spec with accuracy-check (OSRS Wiki and cache are the baseline), one PR per change, no stacking.

## Definition of done
- [ ] upstream re-checked for an existing implementation or PR before starting
- [ ] quest/feature completable end to end on the test server as on the wiki
- [ ] accuracy/content-player-trading.md on the fork's docs branch (wiki/cache baseline per rule, RSProx comparison recorded)
- [ ] fork PR open with green CI at the handed-over commit
- [ ] one change only, branch off upstream main (no stacking)

## Log
- 2026-10-21T00:25:19Z migrated from v2 tasks: content-player-trading
- 2026-10-22T00:23:52Z RSProx recordings: 3055 (rev 231, 132 matches; interface 334, interface 335, interface 336), 1139 (rev 237, 104 matches; interface 334, interface 335, interface 336), 3080 (rev 231, 65 matches; interface 334, interface 335, interface 336), 2508 (rev 229, 55 matches; interface 334, interface 335, interface 336), 2632 (rev 229, 53 matches; interface 334, interface 335, interface 336), 3865 (rev 235, 53 matches; interface 334, interface 335, interface 336), 2510 (rev 229, 52 matches; interface 334, interface 335, interface 336), 3942 (rev 236, 34 matches; interface 335, interface 336) [+12 more] | 20 recordings; keys: interface 334,335,336; text wishes to trade with you
