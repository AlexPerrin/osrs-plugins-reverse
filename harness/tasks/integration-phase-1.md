---
id: integration-phase-1
track: orch
title: 'Integration pipeline phase 1: queue, lock, trains, commit pins (ship.sh enqueues; integration.lock on local/integration-tools)'
status: done
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
updated: '2026-10-20T06:07:50Z'
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
- 2026-10-20T06:07:50Z Merged 010db76 (60 tests); fork pins 2538df34e; first ship enqueued and first train run (int-20261020-1, stopped on a conflict as designed: needs-agent).
- 2026-10-20T06:07:50Z set status=done
