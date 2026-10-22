---
slug: ground-item-despawn-times
track: server
title: Ground item despawn times (OSRS Drop rules)
prio: P1
role: server-dev
stage: checked
hold: null
deps: []
branch: fix/ground-item-despawn-times
commit: d0fe09c630e7456433348ee273f3e9cd8353cc82
local_only: false
owner: null
instances:
- desktop
evidence: /home/alex/osrs-plugins-reverse/re/evidence/ground-item-despawn-times
pr: https://github.com/AlexPerrin/OpenRune-Server/pull/23
ci: https://github.com/AlexPerrin/OpenRune-Server/actions/runs/39701238580
spec: ground-item-despawn-times
summary: 'sd33''s 4th commit (ObjRegistry.singleCopy and ZoneUpdateTransformer.copy pass ownerId; ObjRegistrySplitOwnerTest + ObjAddTimersTest) is on the branch; sd34 rebased the 4 commits onto upstream
  main e85278c9a (241) with identical patches, head d0fe09c63 on fork PR #23, CI green. The two CI failures before that were this branch''s pre-241 game-data actions cache (key hashes only game.example.yml):
  entry deleted, bug filed as ci-game-cache-key. Spec, PR text and STATE.md on fork docs (696f55e2f); spec no longer lists the copy-owner bug as open.'
live_commit: 8bc126d4937ef0c813aea42cc1d1e6e5d9d204e5
batch: int-20261022-4
cost:
  runs: 3
  turns: 88
  minutes: 40.2
  usd: 7.78
created: '2026-10-20T01:42:40Z'
updated: '2026-10-22T01:10:43Z'
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
- [x] on fix/ground-item-despawn-times (same branch as fork PR #23, a new commit, no new branch): pass ownerId as the 5th Obj(...) argument in ObjRegistry.singleCopy and ZoneUpdateTransformer.copy; a unit test; fork CI green
- [x] sd28's spec (accuracy/ground-item-despawn-times.md) no longer lists the copy-owner bug as open

## Log
- 2026-10-21T00:25:19Z migrated from v2 tasks: ground-item-despawn-times, despawn-copy-owner-id
- 2026-10-21T00:25:19Z hold failed: further work on the branch (despawn-copy-owner-id): Ground items: keep the owner when an obj is copied (ObjRegistry.singleCopy, ZoneUpdateTransformer.copy) (a new commit re-enters at dev)
- 2026-10-22T00:01:06Z run sd31 started (server-dev, stage dev, instances desktop)
- 2026-10-22T00:06:38Z run sd31 ended: ? turns, 0.0 min, $0, exit stopped:lingering: change at live for 6 min
- 2026-10-22T00:07:37Z run sd33 started (server-dev, stage dev, instances desktop)
- 2026-10-22T00:26:43Z live -> ready: branch moved to a704e582b past 8bc126d49: hand over again (BR-C2)
- 2026-10-22T00:26:49Z run sd33 ended: 32 turns, 6.5 min, $2.81, exit stopped:lingering: owner is None (change at ready)
- 2026-10-22T00:27:42Z ready -> dev: dispatched to sd34
- 2026-10-22T00:27:42Z run sd34 started (server-dev, stage dev, instances desktop)
- 2026-10-22T00:42:34Z sd34: rebased the 4 commits onto upstream main e85278c9a (241), identical patches, head d0fe09c63 pushed to PR #23; CI at a704e582b/d0fe09c63 failed in or-cache:buildCache from this branch's stale 240 actions-cache entry (key unchanged by the 241 bump); deleted entry 9200352329, CI re-dispatched; filed ci-game-cache-key
- 2026-10-22T00:59:28Z hold failed cleared: handed over (BR-S3)
- 2026-10-22T00:59:28Z handover fix/ground-item-despawn-times@d0fe09c63: sd33's 4th commit (ObjRegistry.singleCopy and ZoneUpdateTransformer.copy pass ownerId; ObjRegistrySplitOwnerTest + ObjAddTimersTest) is on the branch; sd34 rebased the 4 commits onto upstream main e85278c9a (241) with identical patches, head d0fe09c63 on fork PR #23, CI green. The two CI failures before that were this branch's pre-241 game-data actions cache (key hashes only game.example.yml): entry deleted, bug filed as ci-game-cache-key. Spec, PR text and STATE.md on fork docs (696f55e2f); spec no longer lists the copy-owner bug as open.
- 2026-10-22T01:00:02Z run sd34 ended: 56 turns, 33.7 min, $4.97, exit ok
- 2026-10-22T01:00:03Z dev -> checked: hand-over check PASS
- 2026-10-22T01:10:43Z in train int-20261022-4: integration 749d6dd58 pushed; gate, image and deploy pending
