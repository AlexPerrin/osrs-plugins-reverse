---
slug: ground-item-despawn-times
track: server
title: Ground item despawn times (OSRS Drop rules)
prio: P1
role: server-dev
stage: live
hold:
  kind: failed
  reason: 'further work on the branch (despawn-copy-owner-id): Ground items: keep the owner when an obj is copied (ObjRegistry.singleCopy, ZoneUpdateTransformer.copy) (a new commit re-enters at dev)'
  since: '2026-10-21T00:25:19Z'
deps: []
branch: fix/ground-item-despawn-times
commit: 8bc126d4937ef0c813aea42cc1d1e6e5d9d204e5
local_only: false
owner: null
instances: []
evidence: /home/alex/osrs-plugins-reverse/re/evidence/ground-item-despawn-times
pr: https://github.com/AlexPerrin/OpenRune-Server/pull/23
ci: https://github.com/AlexPerrin/OpenRune-Server/actions/runs/39376279822
spec: ground-item-despawn-times
summary: 'Left: DoD 3 (client dev verifies hub-ground-items-despawn after the deploy). Player drops use OSRS timers: GE-tradeable private 100 / despawn 300, others never public / 300, clue+challenge scrolls
  and Mimic 6100. OBJ_ADD now carries timeUntilPublic/timeUntilDespawn/ownership/neverBecomesPublic (self while private, other after reveal), matched to RSProx 73/91/1621/1702/1718; in game the drops showed
  the official values and despawned at +299. Found that ItemServerType.tradeable reads true for untradeables, so the rule uses stockmarket (not re-run in game; the desktop was reclaimed). Spec and PR text
  are on fork docs.'
live_commit: 8bc126d4937ef0c813aea42cc1d1e6e5d9d204e5
created: '2026-10-20T01:42:40Z'
updated: '2026-10-21T00:25:19Z'
---
## Change
Every player drop currently despawns after 200 ticks (constants.lootdrop_duration). Match OSRS Wiki 'Drop': tradeables become public after 60 s and despawn after a further 120 s (300 ticks total); untradeables 180 s; clue scrolls 61 min. Repro: ::item 10180 1, drop, gone 115 s later. Unblocks client: Clue Juggling Timers, Ground Items despawn (gen 44, scratchpad/g44/).

Ground items: keep the owner when an obj is copied (ObjRegistry.singleCopy, ZoneUpdateTransformer.copy)

## Definition of done
- [x] Drop of a tradeable on the test server becomes public after 100 ticks and despawns after 300 ticks
- [x] Drop of an untradeable despawns after 300 ticks (180 s); a dropped clue scroll stays 61 min
- [x] Unblocks client task hub-ground-items-despawn (the client dev verifies it there after the deploy)
- [x] accuracy/ground-item-despawn-times.md on the fork's docs branch (wiki/cache baseline per rule, RSProx comparison recorded)
- [x] fork PR open with green CI at the handed-over commit
- [x] one change only, branch off upstream main (no stacking)
- [ ] on fix/ground-item-despawn-times (same branch as fork PR #23, a new commit, no new branch): pass ownerId as the 5th Obj(...) argument in ObjRegistry.singleCopy and ZoneUpdateTransformer.copy; a unit test; fork CI green
- [ ] sd28's spec (accuracy/ground-item-despawn-times.md) no longer lists the copy-owner bug as open

## Log
- 2026-10-21T00:25:19Z migrated from v2 tasks: ground-item-despawn-times, despawn-copy-owner-id
- 2026-10-21T00:25:19Z hold failed: further work on the branch (despawn-copy-owner-id): Ground items: keep the owner when an obj is copied (ObjRegistry.singleCopy, ZoneUpdateTransformer.copy) (a new commit re-enters at dev)
