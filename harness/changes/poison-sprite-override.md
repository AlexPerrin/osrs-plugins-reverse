---
slug: poison-sprite-override
track: client
title: 'Poison sprite override: crop canvas-size overrides'
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
26x26 override refused vs 15x14 (plugin-host row overstated it); crop canvas-size sprite overrides to the game's trimmed sprite for Poison's heart. Done: e2ee7b7, merged faac757 (gen 46).

## Definition of done
- [ ] built (host/build.sh + ninja + repack); changed path tried on the device with a screenshot or logcat in re/evidence/poison-sprite-override/

## Log
- 2026-10-21T00:25:19Z migrated from v2 tasks: poison-sprite-override
