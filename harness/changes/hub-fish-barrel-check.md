---
slug: hub-fish-barrel-check
track: client
title: 'Fish Barrel hub plugin: Check path'
prio: P1
role: client-dev
stage: live
hold: null
deps:
- fish-barrel-check-and-destroy
branch: client/hub-fish-barrel-check
commit: 77c4aa391dff70fc9ff1168fc65a82e5898205a0
local_only: false
owner: null
instances: []
evidence: /home/alex/osrs-plugins-reverse/re/evidence/hub-fish-barrel-check
summary: No client code change. Rebuilt master a4f3740 with 7 telemetry lines and installed it. Check opens interface 193 with 3 x Raw shrimps and the overlay shows 3 instead of the question mark. Destroy
  then Yes on interface 584 removed the slot-15 barrel. Restore check all OK, device freed. Docs updated.
live_commit: 77c4aa391dff70fc9ff1168fc65a82e5898205a0
created: '2026-10-20T01:42:43Z'
updated: '2026-10-21T00:25:19Z'
---
## Change
Fish Barrel hub plugin's Check path on the device once the server sends the message box; then destroy the tester's slot-15 Fish barrel (docs/client-state.md Next tasks 1).

## Definition of done
- [x] Fish Barrel overlay shows the count after Check on the device
- [x] tester's slot-15 barrel destroyed, inventory restored
- [x] built (host/build.sh + ninja + repack); changed path tried on the device with a screenshot or logcat in re/evidence/hub-fish-barrel-check/

## Log
- 2026-10-21T00:25:19Z migrated from v2 tasks: hub-fish-barrel-check
