---
slug: 11-dense-clientscript-ids
track: server
title: fix/11-dense-clientscript-ids (in integration since before v3)
prio: P1
role: server-dev
stage: live
hold:
  kind: failed
  reason: 'blocks every train on upstream main 241.4 (integrator ig6, re/evidence/int-20261022-3/analysis.md): fix/11 hard-codes the stock clientscript max 9972 and numbers ours 9973-9985; the 241.4 cache
    has 10197 stock scripts, so ours shadow stock ones, PackCs2 fails, the LIVE cache is never built and 128 tests fail. Required change (option A): take the stock max from the cache (idx12 groups) instead
    of a constant, renumber our syms to 10197+ (quest 10197-10198, spawn 10199-10209), keep local/skip-cs2-packs (stacked on this branch) building, fork CI green at the new commit; hand over both. Upstream
    numbers its customs at 45553+.'
  since: '2026-10-22T01:10:20Z'
deps: []
branch: fix/11-dense-clientscript-ids
commit: 339d0f3fa8a96606e7846937c702af57503f3e66
local_only: false
owner: sd35
instances:
- desktop
live_commit: 339d0f3fa8a96606e7846937c702af57503f3e66
created: '2026-10-21T00:25:19Z'
updated: '2026-10-22T01:18:59Z'
---
## Change
Branch `fix/11-dense-clientscript-ids` carried by the live server at its pin; no v2 task.

## Definition of done
- [x] in integration

## Log
- 2026-10-21T00:25:19Z migrated from v2 tasks: (integration.txt)
- 2026-10-22T01:10:20Z hold failed: blocks every train on upstream main 241.4 (integrator ig6, re/evidence/int-20261022-3/analysis.md): fix/11 hard-codes the stock clientscript max 9972 and numbers ours 9973-9985; the 241.4 cache has 10197 stock scripts, so ours shadow stock ones, PackCs2 fails, the LIVE cache is never built and 128 tests fail. Required change (option A): take the stock max from the cache (idx12 groups) instead of a constant, renumber our syms to 10197+ (quest 10197-10198, spawn 10199-10209), keep local/skip-cs2-packs (stacked on this branch) building, fork CI green at the new commit; hand over both. Upstream numbers its customs at 45553+.
- 2026-10-22T01:18:59Z run sd35 started (server-dev, stage dev, instances desktop)
