---
id: scene-item-despawn
track: client
title: 'SceneObjects: post ItemDespawned for items leaving the scene'
status: done
prio: P2
role: client-dev
owner: null
instance: []
branch: null
commit: null
blocked_by: []
unblocks: []
created: '2026-10-20T01:42:42Z'
updated: '2026-10-20T01:42:42Z'
---
## Task
SceneObjects posted no ItemDespawned for ground items that leave the scene, so Ground Items double-counted after a teleport round trip (pre-existing; qa/client-hostfixes-gen45.md). Done: 9930e21 + 359aa21, merged 5c0a8dc (gen 46, dev's check).

## Definition of done
- [ ] built (host/build.sh + ninja + repack); changed path tried on the device with a screenshot or logcat in re/evidence/scene-item-despawn/

## Log
- 2026-10-20T01:42:42Z created (done)
