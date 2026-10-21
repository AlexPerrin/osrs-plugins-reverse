---
slug: rebuild-double-logged-in
track: client
title: 'Rebuild: don''t post LOGGED_IN twice'
prio: P3
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
created: '2026-10-20T01:42:43Z'
updated: '2026-10-21T00:25:19Z'
---
## Change
A native 30 arriving after the rebuild release tick could post LOGGED_IN twice (unguarded, never seen). Done: 910f450 + f01f851, merged 7ec40ca (gen 46).

## Definition of done
- [ ] built (host/build.sh + ninja + repack); changed path tried on the device with a screenshot or logcat in re/evidence/rebuild-double-logged-in/

## Log
- 2026-10-21T00:25:19Z migrated from v2 tasks: rebuild-double-logged-in
