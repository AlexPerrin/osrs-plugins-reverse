---
id: cleanup-scratchpad-qa-acc
track: orch
title: 'Cleanup: scratchpad qa/ and acc/'
status: blocked
prio: P3
role: orchestrator
owner: null
instance: []
branch: null
commit: null
blocked_by:
- gate-acc-cannon
- gate-acc-genie
unblocks: []
created: '2026-10-20T01:42:44Z'
updated: '2026-10-20T01:42:59Z'
---
## Task
Delete scratchpad qa/ and acc/ after the acc gates. sd22's unique refs are archived in re/upstream-pr/or as refs/archive/sd22/* (fin-hunter-rumours dcf92ca13 is the older linear rumours, useful for hunter-split-ship): keep them.

## Definition of done
- [ ] listed items gone; nothing else removed (checked before and after)

## Log
- 2026-10-20T01:42:44Z created (ready)
- 2026-10-20T01:42:59Z blocked by gate-acc-cannon: scratchpad/acc logs are the gate's evidence until it finishes
- 2026-10-20T01:42:59Z blocked by gate-acc-genie: scratchpad/acc logs are the gate's evidence until it finishes
