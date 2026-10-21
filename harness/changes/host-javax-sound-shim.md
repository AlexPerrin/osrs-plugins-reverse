---
slug: host-javax-sound-shim
track: client
title: 'Host: javax.sound.sampled shim'
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
Drop Sounds threw NoClassDefFoundError: javax/sound/sampled/Clip at start (v0.1.0 gate); shim javax.sound.sampled onto the host audio path (as AudioPlayer), Drop Sounds pin dropped. Done: 1da314a / e8a3836, merged 788b384 (Network QA PASS).

## Definition of done
- [ ] built (host/build.sh + ninja + repack); changed path tried on the device with a screenshot or logcat in re/evidence/host-javax-sound-shim/

## Log
- 2026-10-21T00:25:19Z migrated from v2 tasks: host-javax-sound-shim
