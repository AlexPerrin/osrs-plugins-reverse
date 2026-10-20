---
id: gate-grand-exchange
track: server
title: 'Upstream gate: grand-exchange'
status: ready
prio: P3
role: server-qa
owner: null
instance: []
branch: null
commit: null
blocked_by: []
unblocks: []
created: '2026-10-20T01:42:42Z'
updated: '2026-10-20T01:42:42Z'
---
## Task
Gate the grand-exchange branch and its PR text (UPSTREAM-PRS.md). GE needs #352 and an rsprot bump (list as caveats).

## Definition of done
- [ ] qa/gate-grand-exchange.md on the fork's docs branch with the verdict (PASS/FAIL) at the exact commit
- [ ] independent accuracy re-check against the spec and an in-game run at that commit
- [ ] fork CI green at the exact commit (build-test 'CI first')
- [ ] PR text reviewed against CONTRIBUTING (pr-text-review); scope one change

## Log
- 2026-10-20T01:42:42Z created (ready)
