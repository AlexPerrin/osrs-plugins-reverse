---
id: gate-op-overrides
track: server
title: 'Upstream gate: op overrides'
status: ready
prio: P1
role: server-qa
owner: null
instance: []
branch: null
commit: null
blocked_by: []
unblocks: []
created: '2026-10-20T01:42:41Z'
updated: '2026-10-20T01:42:41Z'
---
## Task
Gate the op-overrides branch (shared base for every quest PR) and its PR text (UPSTREAM-PRS.md). Goes upstream with the fixture first.

## Definition of done
- [ ] qa/gate-op-overrides.md on the fork's docs branch with the verdict (PASS/FAIL) at the exact commit
- [ ] independent accuracy re-check against the spec and an in-game run at that commit
- [ ] fork CI green at the exact commit (build-test 'CI first')
- [ ] PR text reviewed against CONTRIBUTING (pr-text-review); scope one change

## Log
- 2026-10-20T01:42:41Z created (ready)
