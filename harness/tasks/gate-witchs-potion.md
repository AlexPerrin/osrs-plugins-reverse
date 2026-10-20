---
id: gate-witchs-potion
track: server
title: 'Upstream gate: Witch''s Potion (content/witchs-potion-unstacked 23e1b7c5d, replaces #368)'
status: ready
prio: P2
role: server-qa
owner: null
instance: []
branch: content/witchs-potion-unstacked
commit: 23e1b7c5d
blocked_by: []
unblocks: []
created: '2026-10-20T04:10:47Z'
updated: '2026-10-20T04:10:47Z'
spec: witchs-potion
---
## Task
Upstream gate: Witch's Potion (content/witchs-potion-unstacked 23e1b7c5d, replaces #368)

## Definition of done
- [ ] qa/witchs-potion.md on the fork's docs branch with the verdict (PASS/FAIL) at the exact commit
- [ ] independent accuracy re-check against accuracy/witchs-potion.md (RSProx comparison recorded) and an in-game run at that commit
- [ ] fork CI green at the exact commit (the 23e1b7c5d boot failure was infra; check a rerun)
- [ ] PR text pr-texts/witchs-potion.{upstream.body,comment} reviewed (pr-text-review); scope one change; gameval 65449 clash with #366 noted

## Log
- 2026-10-20T04:10:47Z created (ready)
- 2026-10-20T04:10:47Z set branch=content/witchs-potion-unstacked commit=23e1b7c5d spec=witchs-potion
- 2026-10-20T04:10:47Z Split from gate-witchs-potion-goblin-diplomacy (2026-10-20): Goblin Diplomacy passed alone (sq27, d77ac2b76); Witch's Potion still needs its gate. Texts on fork docs 1c6ee11bf.
