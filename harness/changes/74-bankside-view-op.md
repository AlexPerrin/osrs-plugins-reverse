---
slug: 74-bankside-view-op
track: server
title: 'Bankside view op: rebase onto upstream main, spec and PR text for the upstream gate'
prio: P2
role: server-dev
stage: live
hold:
  kind: failed
  reason: 'further work on the branch (fix-bankside-view-op-refresh): Bankside view op: rebase onto upstream main, spec and PR text for the upstream gate (a new commit re-enters at dev)'
  since: '2026-10-21T00:25:19Z'
deps: []
branch: fix/74-bankside-view-op
commit: a86998e235e16fa332af0b6e7fc461a8aa1c4ee0
local_only: false
owner: null
instances: []
spec: bankside-view-op
live_commit: a86998e235e16fa332af0b6e7fc461a8aa1c4ee0
created: '2026-10-20T04:33:51Z'
updated: '2026-10-21T00:25:19Z'
---
## Change
Bankside view op: rebase onto upstream main, spec and PR text for the upstream gate

## Definition of done
- [ ] fix/74-bankside-view-op on current upstream main (829b9477d or newer), one change, fork CI green at the new commit (no upstream PR exists yet, so a rebase is fine; squash the spotless commit into the fix)
- [ ] accuracy/bankside-view-op.md (wiki/cache baseline, RSProx comparison recorded) and pr-texts/bankside-view-op.{title,upstream.body} on the fork's docs branch, upstream PR template (#369); no Depends: line unless a real dependency exists (no stacked PRs)
- [ ] the fix checked in game at the new commit (sideInvOp op 1 no longer throws)

## Log
- 2026-10-21T00:25:19Z migrated from v2 tasks: fix-bankside-view-op-refresh, gate-bankside-view-op
- 2026-10-21T00:25:19Z hold failed: further work on the branch (fix-bankside-view-op-refresh): Bankside view op: rebase onto upstream main, spec and PR text for the upstream gate (a new commit re-enters at dev)
