---
id: hub-fish-barrel-check
track: client
title: 'Fish Barrel hub plugin: Check path'
status: live
prio: P1
role: client-dev
owner: cd50
instance:
- device
branch: client/hub-fish-barrel-check
commit: 77c4aa391dff70fc9ff1168fc65a82e5898205a0
evidence: /home/alex/osrs-plugins-reverse/re/evidence/hub-fish-barrel-check
blocked_by:
- fish-barrel-check-and-destroy
- repin-2
unblocks: []
created: '2026-10-20T01:42:43Z'
updated: '2026-10-20T05:45:02Z'
summary: No client code change. Rebuilt master a4f3740 with 7 telemetry lines and installed it. Check opens interface 193 with 3 x Raw shrimps and the overlay shows 3 instead of the question mark. Destroy
  then Yes on interface 584 removed the slot-15 barrel. Restore check all OK, device freed. Docs updated.
---
## Task
Fish Barrel hub plugin's Check path on the device once the server sends the message box; then destroy the tester's slot-15 Fish barrel (docs/client-state.md Next tasks 1).

## Definition of done
- [x] Fish Barrel overlay shows the count after Check on the device
- [x] tester's slot-15 barrel destroyed, inventory restored
- [x] built (host/build.sh + ninja + repack); changed path tried on the device with a screenshot or logcat in re/evidence/hub-fish-barrel-check/

## Log
- 2026-10-20T01:42:43Z created (ready)
- 2026-10-20T01:42:58Z blocked by fish-barrel-check-and-destroy: server must send the Check message box and implement Destroy first
- 2026-10-20T04:40:56Z blocked by repin-2: waits for the deploy of integration e5b18a87b (fish barrel shipped there)
- 2026-10-20T04:44:46Z set status=ready
- 2026-10-20T04:44:46Z Unblocked: fish barrel Check/Destroy live 2026-10-20 04:44 UTC. The tester's slot-15 barrel can now be destroyed.
- 2026-10-20T05:14:04Z claimed by cd50 (instances device)
- 2026-10-20T05:44:37Z handover client/hub-fish-barrel-check@77c4aa391: No client code change. Rebuilt master a4f3740 with 7 telemetry lines and installed it. Check opens interface 193 with 3 x Raw shrimps and the overlay shows 3 instead of the question mark. Destroy then Yes on interface 584 removed the slot-15 barrel. Restore check all OK, device freed. Docs updated.
- 2026-10-20T05:44:54Z handover-check PASS
- 2026-10-20T05:45:02Z set status=live
- 2026-10-20T05:45:02Z shipped: merged into master 0abb7be, pushed
