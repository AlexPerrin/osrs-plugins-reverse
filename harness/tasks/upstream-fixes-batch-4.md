---
id: upstream-fixes-batch-4
track: server
title: Upstream fixes batch 4
status: blocked
prio: P3
role: server-dev
owner: null
instance: []
branch: null
commit: null
blocked_by:
- spec-fix-06-76
unblocks: []
created: '2026-10-20T01:42:42Z'
updated: '2026-10-20T01:42:59Z'
---
## Task
Upstream the fork fix/* branches 10, 11, 22, 25, 31, 36, 37, 54, 56, 59/63 (held), 69, 70, 71, 76, one PR each, after the internal PASS of each (spec-fix-06-76).

## Definition of done
- [ ] each fix has internal PASS and an upstream gate PASS
- [ ] each PR stands alone on upstream main; opened only after the user's OK per item

## Log
- 2026-10-20T01:42:42Z created (backlog)
- 2026-10-20T01:42:59Z blocked by spec-fix-06-76: needs internal PASS for each fix first
