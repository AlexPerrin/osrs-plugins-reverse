---
id: rebuild-double-logged-in
track: client
title: 'Rebuild: don''t post LOGGED_IN twice'
status: done
prio: P3
role: client-dev
owner: null
instance: []
branch: null
commit: null
blocked_by: []
unblocks: []
created: '2026-10-20T01:42:43Z'
updated: '2026-10-20T01:42:43Z'
---
## Task
A native 30 arriving after the rebuild release tick could post LOGGED_IN twice (unguarded, never seen). Done: 910f450 + f01f851, merged 7ec40ca (gen 46).

## Definition of done
- [ ] built (host/build.sh + ninja + repack); changed path tried on the device with a screenshot or logcat in re/evidence/rebuild-double-logged-in/

## Log
- 2026-10-20T01:42:43Z created (done)
