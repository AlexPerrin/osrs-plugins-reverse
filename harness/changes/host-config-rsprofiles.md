---
slug: host-config-rsprofiles
track: client
title: 'Host: ConfigManager.getRSProfiles'
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
ConfigManager.getRSProfiles() was missing; Time Tracking's farming completion check threw NoSuchMethodError (v0.1.0 gate). Done: 77baa50, merged 4a572ce. Device restore: restore settings.properties first (the login now writes rsprofile keys).

## Definition of done
- [ ] built (host/build.sh + ninja + repack); changed path tried on the device with a screenshot or logcat in re/evidence/host-config-rsprofiles/

## Log
- 2026-10-21T00:25:19Z migrated from v2 tasks: host-config-rsprofiles
