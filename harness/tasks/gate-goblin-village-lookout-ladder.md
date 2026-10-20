---
id: gate-goblin-village-lookout-ladder
track: server
title: 'Gates: fix/goblin-village-lookout-ladder 475eefb76'
status: in-progress
prio: P2
role: server-qa
owner: sd24
instance: []
branch: null
commit: null
blocked_by: []
unblocks: []
created: '2026-10-20T01:42:43Z'
updated: '2026-10-20T02:11:42Z'
---
## Task
fix/goblin-village-lookout-ladder 475eefb76: both gates (internal + upstream), in game. Whether it goes upstream as its own fix/<slug> is a user question (wait-goblin-ladder-own-fix).

## Definition of done
- [ ] qa/gate-goblin-village-lookout-ladder.md on the fork's docs branch with the verdict (PASS/FAIL) at the exact commit
- [ ] independent accuracy re-check against the spec and an in-game run at that commit
- [ ] fork CI green at the exact commit (build-test 'CI first')
- [ ] PR text reviewed against CONTRIBUTING (pr-text-review); scope one change

## Log
- 2026-10-20T01:42:43Z created (ready)
- 2026-10-20T02:11:42Z set status=in-progress owner=sd24
- 2026-10-20T02:11:42Z old harness (session 73): sd24 moved the ladder fix to b87819158 (land on the tile in front); gate pending
