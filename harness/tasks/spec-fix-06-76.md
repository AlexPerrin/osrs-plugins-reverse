---
id: spec-fix-06-76
track: server
title: 'Dev''s check: fix/06-76 branches'
status: ready
prio: P2
role: server-dev
owner: null
instance: []
branch: null
commit: null
blocked_by: []
unblocks:
- upstream-fixes-batch-4
created: '2026-10-20T01:42:41Z'
updated: '2026-10-20T01:42:59Z'
---
## Task
Accuracy spec + internal gate for each fork fix/* branch 06-76 that still lacks it (fork STATE.md task 4); the internal PASS each is what upstream-fixes-batch-4 needs. Then the upstream gate where the branch goes upstream.

## Definition of done
- [ ] accuracy/<name>.md on the fork's docs branch with the RSProx comparison
- [ ] internal gate PASS recorded (qa/<name>.md)
- [ ] fork CI green at the commit; one change per branch off upstream main

## Log
- 2026-10-20T01:42:41Z created (ready)
- 2026-10-20T01:42:59Z unblocks upstream-fixes-batch-4
