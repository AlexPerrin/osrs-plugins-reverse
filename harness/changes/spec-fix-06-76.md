---
slug: spec-fix-06-76
track: server
title: 'Dev''s check: fix/06-76 branches'
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
created: '2026-10-21T00:25:19Z'
updated: '2026-10-21T00:25:19Z'
---
## Change
Accuracy spec + internal gate for each fork fix/* branch 06-76 that still lacks it (fork STATE.md task 4); the internal PASS each is what upstream-fixes-batch-4 needs. Then the upstream gate where the branch goes upstream.

## Definition of done
- [ ] accuracy/<name>.md on the fork's docs branch with the RSProx comparison
- [ ] internal gate PASS recorded (qa/<name>.md)
- [ ] fork CI green at the commit; one change per branch off upstream main

## Log
- 2026-10-21T00:25:19Z migrated from v2 tasks: spec-fix-06-76
