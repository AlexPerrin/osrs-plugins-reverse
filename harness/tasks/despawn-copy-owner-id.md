---
id: despawn-copy-owner-id
track: server
title: 'Ground items: keep the owner when an obj is copied (ObjRegistry.singleCopy, ZoneUpdateTransformer.copy)'
status: ready
prio: P2
role: server-dev
owner: null
instance: []
branch: fix/ground-item-despawn-times
commit: null
blocked_by: []
unblocks: []
created: '2026-10-20T06:16:54Z'
updated: '2026-10-20T06:16:55Z'
spec: ground-item-despawn-times
---
## Task
Ground items: keep the owner when an obj is copied (ObjRegistry.singleCopy, ZoneUpdateTransformer.copy)

## Definition of done
- [ ] on fix/ground-item-despawn-times (same branch as fork PR #23, a new commit, no new branch): pass ownerId as the 5th Obj(...) argument in ObjRegistry.singleCopy and ZoneUpdateTransformer.copy; a unit test; fork CI green
- [ ] sd28's spec (accuracy/ground-item-despawn-times.md) no longer lists the copy-owner bug as open

## Log
- 2026-10-20T06:16:54Z created (ready)
- 2026-10-20T06:16:54Z set branch=fix/ground-item-despawn-times spec=ground-item-despawn-times
- 2026-10-20T06:16:55Z From sd32's train resolution (re/evidence/int-20261020-1/resolution.md): the one part of fix/76-obj-add-timers worth keeping. One change, one branch: it goes on the despawn branch, not a new one.
