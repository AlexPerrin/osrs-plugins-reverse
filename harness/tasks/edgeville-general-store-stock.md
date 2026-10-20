---
id: edgeville-general-store-stock
track: orch
title: 'Upstream #371: Edgeville general store stock'
status: in-progress
prio: P1
role: orchestrator
owner: sq25
instance: []
branch: null
commit: null
blocked_by: []
unblocks: []
created: '2026-10-20T01:42:43Z'
updated: '2026-10-20T02:11:42Z'
---
## Task
Rebuild upstream's truncated generalshop2.toml (size 0 -> crash on Trade), data only (sd24, sq23). Fork PR #19 fix/edgeville-general-store-stock @ ca527418a -> upstream #371 (opened 2026-10-19); internal + upstream PASS (fork docs qa/edgeville-general-store-stock.md). #351 (fix/55, draft since 2026-10-18) depends on it and leaves draft after #371 merges; fix/55 is out of integration until then.

## Definition of done
- [ ] #371 merged upstream
- [ ] #351 (fix/55) taken out of draft afterwards with the user's OK; fix/55 back in integration (repin-2)

## Log
- 2026-10-20T01:42:43Z created (waiting-user)
- 2026-10-20T02:11:42Z set status=in-progress owner=sq25
- 2026-10-20T02:11:42Z old harness (session 73): sd24 follow-up a695b985f (general stores without a handler, fix/edgeville-general-store-stock); sq25 gate running on re/agents/sq25/r-store
