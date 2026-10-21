---
slug: upstream-fixes-batch-4
track: server
title: Upstream fixes batch 4
prio: P3
role: server-dev
stage: ready
hold:
  kind: blocked
  reason: waiting for spec-fix-06-76
  since: '2026-10-21T00:25:19Z'
deps:
- spec-fix-06-76
branch: null
commit: null
local_only: false
owner: null
instances: []
created: '2026-10-20T01:42:42Z'
updated: '2026-10-21T00:25:19Z'
---
## Change
Upstream the fork fix/* branches 10, 11, 22, 25, 31, 36, 37, 54, 56, 59/63 (held), 69, 70, 71, 76, one PR each, after the internal PASS of each (spec-fix-06-76).

## Definition of done
- [ ] each fix has internal PASS and an upstream gate PASS
- [ ] each PR stands alone on upstream main; opened only after the user's OK per item

## Log
- 2026-10-21T00:25:19Z migrated from v2 tasks: upstream-fixes-batch-4
- 2026-10-21T00:25:19Z hold blocked: waiting for spec-fix-06-76
