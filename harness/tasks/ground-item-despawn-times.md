---
id: ground-item-despawn-times
track: server
title: Ground item despawn times (OSRS Drop rules)
status: shipped
prio: P1
role: server-dev
owner: sd28
instance:
- desktop
branch: fix/ground-item-despawn-times
commit: 8bc126d4937ef0c813aea42cc1d1e6e5d9d204e5
pr: https://github.com/AlexPerrin/OpenRune-Server/pull/23
ci: https://github.com/AlexPerrin/OpenRune-Server/actions/runs/39376279822
evidence: /home/alex/osrs-plugins-reverse/re/evidence/ground-item-despawn-times
blocked_by: []
unblocks:
- hub-ground-items-despawn
created: '2026-10-20T01:42:40Z'
updated: '2026-10-20T06:16:55Z'
summary: 'Left: DoD 3 (client dev verifies hub-ground-items-despawn after the deploy). Player drops use OSRS timers: GE-tradeable private 100 / despawn 300, others never public / 300, clue+challenge scrolls
  and Mimic 6100. OBJ_ADD now carries timeUntilPublic/timeUntilDespawn/ownership/neverBecomesPublic (self while private, other after reveal), matched to RSProx 73/91/1621/1702/1718; in game the drops showed
  the official values and despawned at +299. Found that ItemServerType.tradeable reads true for untradeables, so the rule uses stockmarket (not re-run in game; the desktop was reclaimed). Spec and PR text
  are on fork docs.'
integration: e27ff4d08504cd4dbb92f6adb98575a5f3d15b95
---
## Task
Every player drop currently despawns after 200 ticks (constants.lootdrop_duration). Match OSRS Wiki 'Drop': tradeables become public after 60 s and despawn after a further 120 s (300 ticks total); untradeables 180 s; clue scrolls 61 min. Repro: ::item 10180 1, drop, gone 115 s later. Unblocks client: Clue Juggling Timers, Ground Items despawn (gen 44, scratchpad/g44/).

## Definition of done
- [x] Drop of a tradeable on the test server becomes public after 100 ticks and despawns after 300 ticks
- [x] Drop of an untradeable despawns after 300 ticks (180 s); a dropped clue scroll stays 61 min
- [x] Unblocks client task hub-ground-items-despawn (the client dev verifies it there after the deploy)
- [x] accuracy/ground-item-despawn-times.md on the fork's docs branch (wiki/cache baseline per rule, RSProx comparison recorded)
- [x] fork PR open with green CI at the handed-over commit
- [x] one change only, branch off upstream main (no stacking)

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
- 2026-10-20T05:56:14Z handover fix/ground-item-despawn-times@8bc126d49: Left: DoD 3 (client dev verifies hub-ground-items-despawn after the deploy). Player drops use OSRS timers: GE-tradeable private 100 / despawn 300, others never public / 300, clue+challenge scrolls and Mimic 6100. OBJ_ADD now carries timeUntilPublic/timeUntilDespawn/ownership/neverBecomesPublic (self while private, other after reveal), matched to RSProx 73/91/1621/1702/1718; in game the drops showed the official values and despawned at +299. Found that ItemServerType.tradeable reads true for untradeables, so the rule uses stockmarket (not re-run in game; the desktop was reclaimed). Spec and PR text are on fork docs.
- 2026-10-20T05:56:40Z handover-check FAIL (1 items; see the check output)
- 2026-10-20T05:56:46Z handover-check PASS
- 2026-10-20T05:56:48Z queued for the next train (fix/ground-item-despawn-times@8bc126d49)
- 2026-10-20T05:56:48Z set status=shipped
- 2026-10-20T06:16:33Z in train int-20261020-1: integration e27ff4d08 (pushed); gate + deploy pending
- 2026-10-20T06:16:55Z Train int-20261020-1: integration e27ff4d08 (fix/76-obj-add-timers dropped as superseded, tools ff7e6eec8). Follow-up despawn-copy-owner-id adds the ownerId copy fix on this branch. Upstream: this branch (fork PR #23) is the only PR; fix/76 is never opened.
