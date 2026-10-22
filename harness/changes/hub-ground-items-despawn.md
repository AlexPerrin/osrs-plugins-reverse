---
slug: hub-ground-items-despawn
track: client
title: Ground Items despawn timers and Clue Juggling Timers
prio: P1
role: client-dev
stage: live
hold: null
deps:
- ground-item-despawn-times
branch: client/ground-items-despawn
commit: 0c44c3de1644e4f2abd1fc40cf87a1e158ccd573
local_only: false
owner: null
instances:
- device
evidence: /home/alex/osrs-plugins-reverse/re/evidence/hub-ground-items-despawn
summary: 'On the live server (fix/ground-item-despawn-times in image e27ff4d08): a dropped tradeable (Lobster) reads visible/despawn = tick+99/299 and left the scene exactly at its despawn tick (0 late)
  -> Ground Items'' TICKS timer matches the real despawn; Clue Juggling Timers counts a dropped easy clue (6099-tick private timer) down correctly (infobox 59m->58m, resumes 55m/51m/49m after relogs, pausing
  while logged out as OSRS does). Built on client/ground-items-despawn (host SceneObjects logs ground-item timers on spawn/despawn + an infoboxes probe; no native change), 7 telemetry lines, installed and
  tried on the device; logcat in evidence. Found and filed server task ground-item-relogin-timers (P2): after a relog the server re-sends the player''s own drops with timeUntilDespawn 0xFFFF. Docs rewritten
  (client-state, plugin-host, offsets).'
live_commit: 0c44c3de1644e4f2abd1fc40cf87a1e158ccd573
cost:
  runs: 1
  turns: 137
  minutes: 81.9
  usd: 16.24
created: '2026-10-20T01:42:43Z'
updated: '2026-10-22T01:19:49Z'
---
## Change
Ground Items despawn timers and the Clue Juggling Timers plugin on the device once the server uses OSRS despawn times.

## Definition of done
- [x] Ground Items despawn timer matches the actual despawn of a dropped tradeable on the device
- [x] Clue Juggling Timers counts down a dropped clue correctly
- [x] built (host/build.sh + ninja + repack); changed path tried on the device with a screenshot or logcat in re/evidence/hub-ground-items-despawn/

## Log
- 2026-10-21T00:25:19Z migrated from v2 tasks: hub-ground-items-despawn
- 2026-10-22T00:01:06Z ready -> dev: dispatched to cd48
- 2026-10-22T00:01:06Z run cd48 started (client-dev, stage dev, instances device)
- 2026-10-22T00:26:47Z hold blocked: waiting for ground-item-despawn-times
- 2026-10-22T00:51:11Z Device: Lobster drop reads visible 99/despawn 299 and despawned at its despawn tick (0 late); easy clue reads despawn 6099 private, Clue Juggling infobox 59m/58m. Found: after a relog the clue is re-sent with timeUntilDespawn 0xFFFF (no timer) -> filed server task ground-item-relogin-timers (P2, not blocking the DoD cases). Waiting for the clue's actual despawn (~21:32-21:44 EDT) with a keep-alive key.
- 2026-10-22T01:18:16Z hold blocked cleared: handed over (BR-S3)
- 2026-10-22T01:18:16Z handover client/ground-items-despawn@0c44c3de1: On the live server (fix/ground-item-despawn-times in image e27ff4d08): a dropped tradeable (Lobster) reads visible/despawn = tick+99/299 and left the scene exactly at its despawn tick (0 late) -> Ground Items' TICKS timer matches the real despawn; Clue Juggling Timers counts a dropped easy clue (6099-tick private timer) down correctly (infobox 59m->58m, resumes 55m/51m/49m after relogs, pausing while logged out as OSRS does). Built on client/ground-items-despawn (host SceneObjects logs ground-item timers on spawn/despawn + an infoboxes probe; no native change), 7 telemetry lines, installed and tried on the device; logcat in evidence. Found and filed server task ground-item-relogin-timers (P2): after a relog the server re-sends the player's own drops with timeUntilDespawn 0xFFFF. Docs rewritten (client-state, plugin-host, offsets).
- 2026-10-22T01:18:57Z hold blocked: waiting for ground-item-despawn-times
- 2026-10-22T01:18:57Z run cd48 ended: 137 turns, 81.9 min, $16.24, exit ok
- 2026-10-22T01:18:57Z hold blocked cleared: handed over
- 2026-10-22T01:18:57Z dev -> checked: hand-over check PASS
- 2026-10-22T01:18:59Z ship: merged into master d691e08, pushed
- 2026-10-22T01:19:49Z checked -> live: observed
