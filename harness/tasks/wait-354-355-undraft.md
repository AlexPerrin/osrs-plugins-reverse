---
id: wait-354-355-undraft
track: orch
title: 'User: move #354/#355 out of draft after gates?'
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
updated: '2026-10-20T04:49:09Z'
---
## Task
Waiting item 2: move #354/#355 (acc/cannon, acc/genie) out of draft after their gates? Decided 2026-10-18: gated rewrites replace their content with a non-force-push merge commit; each needs the user's OK when it arrives.

## Definition of done
- [ ] the user's decision recorded in the Log and acted on (or passed to the task it gates)

## Log
- 2026-10-20T01:42:43Z created (waiting-user)
- 2026-10-20T04:49:09Z User 2026-10-20: yes. When the rewritten cannon/genie branches pass their upstream gates, update #354/#355 (non-force-push merge commit) and move them out of draft; the push and the ready still get a one-line announcement first.
- 2026-10-20T04:49:09Z set status=done
