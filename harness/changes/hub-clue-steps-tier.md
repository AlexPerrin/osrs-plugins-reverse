---
slug: hub-clue-steps-tier
track: client
title: Clue Steps hub plugin
prio: P2
role: client-dev
stage: ready
hold: null
deps:
- treasure-trails
branch: null
commit: null
local_only: false
owner: null
instances: []
created: '2026-10-20T01:42:43Z'
updated: '2026-10-21T00:25:19Z'
---
## Change
Clue Steps hub plugin reads the steps/tier message on the device once the server includes the tier (docs/client-state.md Next tasks 1).

## Definition of done
- [ ] Clue Steps shows the step count for a clue after Check on the device
- [ ] built (host/build.sh + ninja + repack); changed path tried on the device with a screenshot or logcat in re/evidence/hub-clue-steps-tier/

## Log
- 2026-10-21T00:25:19Z migrated from v2 tasks: hub-clue-steps-tier
