---
slug: scene-item-despawn
track: client
title: 'SceneObjects: post ItemDespawned for items leaving the scene'
prio: P2
role: client-dev
stage: live
hold: null
deps: []
branch: null
commit: null
local_only: false
owner: null
instances: []
live_commit: null
created: '2026-10-20T01:42:42Z'
updated: '2026-10-21T00:25:19Z'
---
## Change
SceneObjects posted no ItemDespawned for ground items that leave the scene, so Ground Items double-counted after a teleport round trip (pre-existing; qa/client-hostfixes-gen45.md). Done: 9930e21 + 359aa21, merged 5c0a8dc (gen 46, dev's check).

## Definition of done
- [ ] built (host/build.sh + ninja + repack); changed path tried on the device with a screenshot or logcat in re/evidence/scene-item-despawn/

## Log
- 2026-10-21T00:25:19Z migrated from v2 tasks: scene-item-despawn
