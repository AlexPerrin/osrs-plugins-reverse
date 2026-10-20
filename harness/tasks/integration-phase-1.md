---
id: integration-phase-1
track: orch
title: 'Integration pipeline phase 1: queue, lock, trains, commit pins (ship.sh enqueues; integration.lock on local/integration-tools)'
status: ready
prio: P2
role: orchestrator
owner: null
instance: []
branch: null
commit: null
blocked_by:
- integration-phase-0
unblocks:
- integration-phase-2
created: '2026-10-20T05:21:50Z'
updated: '2026-10-20T05:30:13Z'
---
## Task
Integration pipeline phase 1: queue, lock, trains, commit pins (ship.sh enqueues; integration.lock on local/integration-tools)

## Definition of done
- [ ] as specified in docs/integration-pipeline.md (phase 1), with tests

## Log
- 2026-10-20T05:21:50Z created (ready)
- 2026-10-20T05:21:51Z blocked by integration-phase-0: phases run in order
- 2026-10-20T05:21:51Z unblocks integration-phase-2
- 2026-10-20T05:30:13Z unblocked by integration-phase-0 (done)
