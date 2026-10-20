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
updated: '2026-10-20T04:51:15Z'
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
- 2026-10-20T04:51:15Z rsprot bump to 1.0.0-ALPHA-20261014 decided here (user 2026-10-20: leave it until this gate): the GE PR either carries the one-line bump or waits for upstream's.
