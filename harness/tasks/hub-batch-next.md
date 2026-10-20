---
id: hub-batch-next
track: client
title: Next hub plugin batch from the ranking
status: in-progress
prio: P3
role: client-dev
owner: cd49
instance:
- device
branch: null
commit: null
blocked_by: []
unblocks: []
created: '2026-10-20T01:42:43Z'
updated: '2026-10-20T04:32:07Z'
---
## Task
More hub plugins from the ranking (scratchpad/client38/ranking.json, keep it, or refetch); netcheck + stubcheck each (docs/client-state.md Next tasks 2).

## Definition of done
- [ ] each new plugin passes netcheck and stubcheck and starts on the device
- [ ] built (host/build.sh + ninja + repack); changed path tried on the device with a screenshot or logcat in re/evidence/hub-batch-next/
- [ ] plugin-host table in docs updated per plugin

## Log
- 2026-10-20T01:42:43Z created (ready)
- 2026-10-20T04:32:07Z claimed by cd49 (instances device)
