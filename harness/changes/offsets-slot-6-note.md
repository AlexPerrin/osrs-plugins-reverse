---
slug: offsets-slot-6-note
track: client
title: 'offsets.md: Client vtable slot 6 draw thread note'
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
created: '2026-10-20T01:42:42Z'
updated: '2026-10-21T00:25:19Z'
---
## Change
offsets.md: Client vtable slot 6 0x421a20 is the draw on doCycle's thread (frame fn 0x86bdc8), per qa/client-world-entry-thread-safety.md. Done in f01f851 (gen 46).

## Definition of done
- [ ] docs/offsets.md row for slot 6 cites the frame function and the QA record

## Log
- 2026-10-21T00:25:19Z migrated from v2 tasks: offsets-slot-6-note
