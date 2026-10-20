---
id: ground-item-despawn-times
track: server
title: Ground item despawn times (OSRS Drop rules)
status: specced
prio: P1
role: server-dev
owner: sd28
instance:
- desktop
branch: null
commit: null
blocked_by: []
unblocks:
- hub-ground-items-despawn
created: '2026-10-20T01:42:40Z'
updated: '2026-10-20T05:00:13Z'
---
## Task
Every player drop currently despawns after 200 ticks (constants.lootdrop_duration). Match OSRS Wiki 'Drop': tradeables become public after 60 s and despawn after a further 120 s (300 ticks total); untradeables 180 s; clue scrolls 61 min. Repro: ::item 10180 1, drop, gone 115 s later. Unblocks client: Clue Juggling Timers, Ground Items despawn (gen 44, scratchpad/g44/).

## Definition of done
- [ ] Drop of a tradeable on the test server becomes public after 100 ticks and despawns after 300 ticks
- [ ] Drop of an untradeable despawns after 300 ticks (180 s); a dropped clue scroll stays 61 min
- [ ] Unblocks client task hub-ground-items-despawn, verified by the client dev
- [ ] accuracy/ground-item-despawn-times.md on the fork's docs branch (wiki/cache baseline per rule, RSProx comparison recorded)
- [ ] fork PR open with green CI at the handed-over commit
- [ ] one change only, branch off upstream main (no stacking)

## Log
- 2026-10-20T01:42:40Z created (ready)
- 2026-10-20T01:42:59Z unblocks hub-ground-items-despawn
- 2026-10-20T04:32:06Z claimed by sd28 (instances desktop)
- 2026-10-20T04:59:56Z set status=specced
- 2026-10-20T04:59:56Z spec accuracy/ground-item-despawn-times.md on fork docs: drops 100/300 tradeable, 300 never-public untradeable, 6100 clue/challenge/Mimic; plus OBJ_ADD timers (reveal/despawn/ownership/neverBecomesPublic) matched to RSProx 73/91/1621/1702/1718; one PR, splittable at the commit boundary
- 2026-10-20T05:00:02Z set status=specced
- 2026-10-20T05:00:02Z spec accuracy/ground-item-despawn-times.md on fork docs: drops 100/300 tradeable, 300 never-public untradeable, 6100 clue/challenge/Mimic; plus OBJ_ADD timers (reveal/despawn/ownership/neverBecomesPublic) matched to RSProx 73/91/1621/1702/1718; one PR, splittable at the commit boundary
- 2026-10-20T05:00:13Z set status=specced
- 2026-10-20T05:00:13Z spec accuracy/ground-item-despawn-times.md on fork docs (45379d98d): drops 100/300 tradeable, 300 never-public untradeable, 6100 clue/challenge/Mimic; plus OBJ_ADD timers (reveal/despawn/ownership/neverBecomesPublic) matched to RSProx 73/91/1621/1702/1718; one PR, splittable at the commit boundary
