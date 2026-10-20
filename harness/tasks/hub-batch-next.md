---
id: hub-batch-next
track: client
title: Next hub plugin batch from the ranking
status: shipped
prio: P3
role: client-dev
owner: cd49
instance:
- device
branch: client/hub-batch-next
commit: 0c7431bf0595bcf279c2db7a060d1617a2264f39
evidence: /home/alex/osrs-plugins-reverse/re/evidence/hub-batch-next
blocked_by: []
unblocks: []
created: '2026-10-20T01:42:43Z'
updated: '2026-10-20T05:13:54Z'
summary: 'User''s plugins first (re/evidence/hub-batch-next/user-plugins.md): 16 of their hub plugins pinned, netcheck clean, start on the device; Camera Smoothing now runs (host: setCameraPitchTarget/YawTarget
  write Client +0x8949c/+0x894a0; camlog on/off logs). Plus 9 ranking plugins (venom/spec-regen/afk timers, player highlighter, marks of grace, tick counter, virtual level ups, idle alarm, dynamic inventory
  tags) start. Report Button starts but mobile has no report button; Stretched Mode n/a; Animation Smoothing and Camera need stub setters. Left: afk-marks-canafis (NET-RAW NTP, needs main OK), wikisync
  (new host sync.runescape.wiki + local WebSocket, needs OK), quest-voiceover (sqlite/mp3 deps, github fetches); host gaps ItemManager.getItemStats (null; Dynamic Inventory Tags throws; a static.runelite.net
  fetch = Network QA), WorldEntity API (sailing), DynamicObject.getAnimation; 11 server content tasks filed (content-drift-net-fishing, -barbarian-assault, -guardians-of-the-rift, -rooftop-agility-canifis,
  -forestry-events, -stealing-artefacts, -hunter-kebbit-tracking, -moons-of-peril, -trouble-brewing, -gnome-restaurant, -sailing). 117HD/GPU: stretch tasks. Restore check all OK; note the host writes 3
  rsprofile keys to settings.properties at each login (slot backup lacked them; backup pushed back while logged in).'
---
## Task
More hub plugins from the ranking (scratchpad/client38/ranking.json, keep it, or refetch); netcheck + stubcheck each (docs/client-state.md Next tasks 2).

## Definition of done
- [x] each new plugin passes netcheck and stubcheck and starts on the device
- [x] built (host/build.sh + ninja + repack); changed path tried on the device with a screenshot or logcat in re/evidence/hub-batch-next/
- [x] plugin-host table in docs updated per plugin

## Log
- 2026-10-20T01:42:43Z created (ready)
- 2026-10-20T04:32:07Z claimed by cd49 (instances device)
- 2026-10-20T05:13:36Z handover client/hub-batch-next@0c7431bf0: User's plugins first (re/evidence/hub-batch-next/user-plugins.md): 16 of their hub plugins pinned, netcheck clean, start on the device; Camera Smoothing now runs (host: setCameraPitchTarget/YawTarget write Client +0x8949c/+0x894a0; camlog on/off logs). Plus 9 ranking plugins (venom/spec-regen/afk timers, player highlighter, marks of grace, tick counter, virtual level ups, idle alarm, dynamic inventory tags) start. Report Button starts but mobile has no report button; Stretched Mode n/a; Animation Smoothing and Camera need stub setters. Left: afk-marks-canafis (NET-RAW NTP, needs main OK), wikisync (new host sync.runescape.wiki + local WebSocket, needs OK), quest-voiceover (sqlite/mp3 deps, github fetches); host gaps ItemManager.getItemStats (null; Dynamic Inventory Tags throws; a static.runelite.net fetch = Network QA), WorldEntity API (sailing), DynamicObject.getAnimation; 11 server content tasks filed (content-drift-net-fishing, -barbarian-assault, -guardians-of-the-rift, -rooftop-agility-canifis, -forestry-events, -stealing-artefacts, -hunter-kebbit-tracking, -moons-of-peril, -trouble-brewing, -gnome-restaurant, -sailing). 117HD/GPU: stretch tasks. Restore check all OK; note the host writes 3 rsprofile keys to settings.properties at each login (slot backup lacked them; backup pushed back while logged in).
- 2026-10-20T05:13:48Z handover-check PASS
- 2026-10-20T05:13:53Z set status=shipped
- 2026-10-20T05:13:54Z shipped: merged into master a4f3740, pushed
