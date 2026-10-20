---
id: hub-clue-steps-tier
track: client
title: Clue Steps hub plugin
status: blocked
prio: P2
role: client-dev
owner: null
instance: []
branch: null
commit: null
blocked_by:
- clue-check-steps-tier
unblocks: []
created: '2026-10-20T01:42:43Z'
updated: '2026-10-20T01:42:58Z'
---
## Task
Clue Steps hub plugin reads the steps/tier message on the device once the server includes the tier (docs/client-state.md Next tasks 1).

## Definition of done
- [ ] Clue Steps shows the step count for a clue after Check on the device
- [ ] built (host/build.sh + ninja + repack); changed path tried on the device with a screenshot or logcat in re/evidence/hub-clue-steps-tier/

## Log
- 2026-10-20T01:42:43Z created (ready)
- 2026-10-20T01:42:58Z blocked by clue-check-steps-tier: server's Check message must include the tier first
