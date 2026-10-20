---
id: wait-upstream-pr-order
track: orch
title: 'User: order of new upstream PRs'
status: done
prio: P3
role: orchestrator
owner: null
instance: []
branch: null
commit: null
blocked_by: []
unblocks: []
created: '2026-10-20T01:42:43Z'
updated: '2026-10-20T04:49:10Z'
---
## Task
Waiting item 6: order of the new upstream PRs (quest test fixture and op-overrides first).

## Definition of done
- [ ] the user's decision recorded in the Log and acted on (or passed to the task it gates)

## Log
- 2026-10-20T01:42:43Z created (waiting-user)
- 2026-10-20T03:29:14Z op-overrides dropped from upstream by the user (2026-10-20); only the quest test fixture remains from this item's 'first' pair.
- 2026-10-20T04:49:10Z User 2026-10-20: no order. Each upstream PR goes up on its own when gated (no stacking).
- 2026-10-20T04:49:10Z set status=done
