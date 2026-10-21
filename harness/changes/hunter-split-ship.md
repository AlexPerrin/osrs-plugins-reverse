---
slug: hunter-split-ship
track: server
title: 'Hunter: split the 7 fin/hunter-* branches and ship each'
prio: P2
role: server-dev
stage: ready
hold: null
deps: []
branch: null
commit: null
local_only: false
owner: null
instances: []
created: '2026-10-20T01:42:41Z'
updated: '2026-10-21T00:25:19Z'
---
## Change
7 fin/hunter-* branches (local only, re/upstream-pr/dev-hunter): linearise rumours (the older linear rumours are archived as refs/archive/sd22/fin-hunter-rumours dcf92ca13 in re/upstream-pr/or), gate each, write texts; integration still uses fin/hunter.

## Definition of done
- [ ] 7 branches each off upstream main alone, pushed to the fork, with PR text
- [ ] integration.txt uses the split branches instead of fin/hunter
- [ ] internal gate PASS per branch
- [ ] accuracy/hunter-split-ship.md on the fork's docs branch (wiki/cache baseline per rule, RSProx comparison recorded)
- [ ] fork PR open with green CI at the handed-over commit
- [ ] one change only, branch off upstream main (no stacking)

## Log
- 2026-10-21T00:25:19Z migrated from v2 tasks: hunter-split-ship
