---
slug: hub-did-i-compost
track: client
title: 'Did I Compost hub plugin: treated path'
prio: P3
role: client-dev
stage: ready
hold:
  kind: blocked
  reason: waiting for farming-compost
  since: '2026-10-21T00:25:19Z'
deps:
- farming-compost
branch: null
commit: null
local_only: false
owner: null
instances: []
created: '2026-10-20T01:42:43Z'
updated: '2026-10-21T00:25:19Z'
---
## Change
Did I Compost's treated path on the device once Compost on a patch works on the server (docs/client-state.md Next tasks 1).

## Definition of done
- [ ] Did I Compost marks the patch as treated after composting on the device
- [ ] built (host/build.sh + ninja + repack); changed path tried on the device with a screenshot or logcat in re/evidence/hub-did-i-compost/

## Log
- 2026-10-21T00:25:19Z migrated from v2 tasks: hub-did-i-compost
- 2026-10-21T00:25:19Z hold blocked: waiting for farming-compost
