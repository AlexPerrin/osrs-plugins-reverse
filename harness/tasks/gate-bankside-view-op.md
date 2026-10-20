---
id: gate-bankside-view-op
track: server
title: 'Upstream gate: bankside-view-op'
status: in-progress
prio: P2
role: server-qa
owner: sq28
instance:
- desktop
branch: null
commit: null
blocked_by: []
unblocks: []
created: '2026-10-20T01:42:41Z'
updated: '2026-10-20T04:32:07Z'
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
