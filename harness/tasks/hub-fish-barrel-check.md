---
id: hub-fish-barrel-check
track: client
title: 'Fish Barrel hub plugin: Check path'
status: in-progress
prio: P1
role: client-dev
owner: cd50
instance:
- device
branch: null
commit: null
blocked_by:
- fish-barrel-check-and-destroy
- repin-2
unblocks: []
created: '2026-10-20T01:42:43Z'
updated: '2026-10-20T05:14:04Z'
---
## Task
Fish Barrel hub plugin's Check path on the device once the server sends the message box; then destroy the tester's slot-15 Fish barrel (docs/client-state.md Next tasks 1).

## Definition of done
- [ ] Fish Barrel overlay shows the count after Check on the device
- [ ] tester's slot-15 barrel destroyed, inventory restored
- [ ] built (host/build.sh + ninja + repack); changed path tried on the device with a screenshot or logcat in re/evidence/hub-fish-barrel-check/

## Log
- 2026-10-20T01:42:43Z created (ready)
- 2026-10-20T01:42:58Z blocked by fish-barrel-check-and-destroy: server must send the Check message box and implement Destroy first
- 2026-10-20T04:40:56Z blocked by repin-2: waits for the deploy of integration e5b18a87b (fish barrel shipped there)
- 2026-10-20T04:44:46Z set status=ready
- 2026-10-20T04:44:46Z Unblocked: fish barrel Check/Destroy live 2026-10-20 04:44 UTC. The tester's slot-15 barrel can now be destroyed.
- 2026-10-20T05:14:04Z claimed by cd50 (instances device)
