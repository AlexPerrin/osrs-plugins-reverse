---
slug: hub-ground-items-despawn
track: client
title: Ground Items despawn timers and Clue Juggling Timers
prio: P1
role: client-dev
stage: ready
hold: null
deps:
- ground-item-despawn-times
branch: null
commit: null
local_only: false
owner: null
instances: []
created: '2026-10-20T01:42:43Z'
updated: '2026-10-21T00:25:19Z'
---
## Change
Ground Items despawn timers and the Clue Juggling Timers plugin on the device once the server uses OSRS despawn times.

## Definition of done
- [ ] Ground Items despawn timer matches the actual despawn of a dropped tradeable on the device
- [ ] Clue Juggling Timers counts down a dropped clue correctly
- [ ] built (host/build.sh + ninja + repack); changed path tried on the device with a screenshot or logcat in re/evidence/hub-ground-items-despawn/

## Log
- 2026-10-21T00:25:19Z migrated from v2 tasks: hub-ground-items-despawn
