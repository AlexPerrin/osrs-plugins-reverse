---
slug: cleanup-scratchpad-qa-acc
track: harness
title: 'Cleanup: scratchpad qa/ and acc/'
prio: P3
role: harness-dev
stage: ready
hold:
  kind: blocked
  reason: waiting for genie, cannon
  since: '2026-10-21T00:25:19Z'
deps:
- genie
- cannon
branch: null
commit: null
local_only: false
owner: null
instances: []
created: '2026-10-20T01:42:44Z'
updated: '2026-10-21T00:25:19Z'
---
## Change
Delete scratchpad qa/ and acc/ after the acc gates. sd22's unique refs are archived in re/upstream-pr/or as refs/archive/sd22/* (fin-hunter-rumours dcf92ca13 is the older linear rumours, useful for hunter-split-ship): keep them.

## Definition of done
- [ ] listed items gone; nothing else removed (checked before and after)

## Log
- 2026-10-21T00:25:19Z migrated from v2 tasks: cleanup-scratchpad-qa-acc
- 2026-10-21T00:25:19Z hold blocked: waiting for genie, cannon
