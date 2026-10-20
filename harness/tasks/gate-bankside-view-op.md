---
id: gate-bankside-view-op
track: server
title: 'Upstream gate: bankside-view-op'
status: blocked
prio: P2
role: server-qa
owner: null
instance:
- desktop
branch: null
commit: null
blocked_by:
- fix-bankside-view-op-refresh
unblocks: []
created: '2026-10-20T01:42:41Z'
updated: '2026-10-20T04:33:59Z'
---
## Task
Finish the gate for bankside-view-op (QA stopped at wrap-up; records in openrune/upstream/qa/).

## Definition of done
- [ ] qa/gate-bankside-view-op.md on the fork's docs branch with the verdict (PASS/FAIL) at the exact commit
- [ ] independent accuracy re-check against the spec and an in-game run at that commit
- [ ] fork CI green at the exact commit (build-test 'CI first')
- [ ] PR text reviewed against CONTRIBUTING (pr-text-review); scope one change

## Log
- 2026-10-20T01:42:41Z created (ready)
- 2026-10-20T04:32:07Z claimed by sq28 (instances desktop)
- 2026-10-20T04:33:34Z FAILED: FAIL: fix/74-bankside-view-op (a86998e23) is based on c2492318d, not upstream main 829b9477d; rebase (clean, not superseded), push spec accuracy/bankside-view-op.md and PR text pr-texts/bankside-view-op.md to fork docs, get CI green, hand over again. Record qa/gate-bankside-view-op.md on fork docs 100f0f23c
- 2026-10-20T04:33:51Z set status=ready owner=
- 2026-10-20T04:33:59Z blocked by fix-bankside-view-op-refresh: re-gate after the rebase, spec and PR text
