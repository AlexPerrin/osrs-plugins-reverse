---
id: gate-goblin-village-lookout-ladder
track: server
title: 'Gates: fix/goblin-village-lookout-ladder 475eefb76'
status: done
prio: P2
role: server-qa
owner: null
instance: []
branch: fix/goblin-village-lookout-ladder
commit: b87819158
pr: https://github.com/OpenRune/OpenRune-Server/pull/373
blocked_by: []
unblocks: []
created: '2026-10-20T01:42:43Z'
updated: '2026-10-20T03:34:09Z'
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
- 2026-10-20T02:12:35Z sd24 fix b87819158 (fixed landing tile 2955,3497), pushed to fork (CI 39353952217 + Formatting 39353954027 green). sq25 upstream gate PASS (record 238fb7bff, not pushed). Condition: open together with the quest-only #367 update (draft #367 still contains the ladder). Needs user OK to open.
- 2026-10-20T02:12:35Z set status=gated
- 2026-10-20T02:47:55Z set branch=fix/goblin-village-lookout-ladder commit=b87819158
- 2026-10-20T03:13:49Z approval requested: harness/approvals/gate-goblin-village-lookout-ladder.md
- 2026-10-20T03:34:09Z set pr=https://github.com/OpenRune/OpenRune-Server/pull/373
- 2026-10-20T03:34:09Z User OK 2026-10-20 ('goblin village now'): opened upstream #373 at b87819158, ready for review, text sd24 docs aac2488f1 (gated by sq25). Draft #367 still carries the same GoblinCaveLadders change; the quest-only update (unstack-367-368, d77ac2b76 doesn't touch the file) removes the overlap.
- 2026-10-20T03:34:09Z done
