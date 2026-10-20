---
id: fix-bankside-view-op-refresh
track: server
title: 'Bankside view op: rebase onto upstream main, spec and PR text for the upstream gate'
status: ready
prio: P2
role: server-dev
owner: null
instance: []
branch: fix/74-bankside-view-op
commit: null
blocked_by: []
unblocks:
- gate-bankside-view-op
created: '2026-10-20T04:33:51Z'
updated: '2026-10-20T04:33:59Z'
spec: bankside-view-op
---
## Task
Bankside view op: rebase onto upstream main, spec and PR text for the upstream gate

## Definition of done
- [ ] fix/74-bankside-view-op on current upstream main (829b9477d or newer), one change, fork CI green at the new commit (no upstream PR exists yet, so a rebase is fine; squash the spotless commit into the fix)
- [ ] accuracy/bankside-view-op.md (wiki/cache baseline, RSProx comparison recorded) and pr-texts/bankside-view-op.{title,upstream.body} on the fork's docs branch, upstream PR template (#369); no Depends: line unless a real dependency exists (no stacked PRs)
- [ ] the fix checked in game at the new commit (sideInvOp op 1 no longer throws)

## Log
- 2026-10-20T04:33:51Z created (ready)
- 2026-10-20T04:33:51Z set branch=fix/74-bankside-view-op spec=bankside-view-op
- 2026-10-20T04:33:51Z From sq28's gate FAIL (2026-10-20, fork docs 100f0f23c): branch a86998e23 on c2492318d, 16 behind main; the fix is still needed (BankInvScript.sideInvOp throws on op 1 on main) and no upstream PR covers it. Old PR text only in batch-3-texts.
- 2026-10-20T04:33:59Z unblocks gate-bankside-view-op
