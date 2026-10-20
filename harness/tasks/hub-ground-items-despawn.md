---
id: hub-ground-items-despawn
track: client
title: Ground Items despawn timers and Clue Juggling Timers
status: blocked
prio: P1
role: client-dev
owner: null
instance: []
branch: null
commit: null
blocked_by:
- ground-item-despawn-times
unblocks: []
created: '2026-10-20T01:42:43Z'
updated: '2026-10-20T01:42:59Z'
---
## Task
Ground Items despawn timers and the Clue Juggling Timers plugin on the device once the server uses OSRS despawn times.

## Definition of done
- [ ] Ground Items despawn timer matches the actual despawn of a dropped tradeable on the device
- [ ] Clue Juggling Timers counts down a dropped clue correctly
- [ ] built (host/build.sh + ninja + repack); changed path tried on the device with a screenshot or logcat in re/evidence/hub-ground-items-despawn/

## Log
- 2026-10-20T01:42:43Z created (ready)
- 2026-10-20T01:42:59Z blocked by ground-item-despawn-times: server must use OSRS despawn times first
