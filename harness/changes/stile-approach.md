---
slug: stile-approach
track: server
title: 'Stile: approach from the outside tile'
prio: P1
role: server-dev
stage: live
hold: null
deps: []
branch: fix/stile-approach
commit: 513144d3dfcfcfd5613123bbe3457b952192c384
local_only: false
owner: null
instances: []
evidence: /home/alex/osrs-plugins-reverse/re/evidence/fix-stile-approach
pr: https://github.com/AlexPerrin/OpenRune-Server/pull/21
ci: https://github.com/AlexPerrin/OpenRune-Server/actions/runs/39363600299
summary: '513144d3d on fork (PR #21), CI+Formatting green; 17 in-game crossings from every side/angle walk to the in-line tile, step on T+1, climb T+2, step off T+5 = RSProx 1718; dev''s check PASS. Docs
  554bd95c7 (spec, PR text, qa record, STATE) local in re/agents/sd27/or branch docs: orchestrator to publish it and update PR #21''s body (hook denied both).'
live_commit: 513144d3dfcfcfd5613123bbe3457b952192c384
created: '2026-10-20T01:42:40Z'
updated: '2026-10-21T00:25:19Z'
---
## Change
fix/stile-approach 8a8c0ca7a failed the internal gate: the player walks onto the stile's own blocking tile, so it never climbs. Fix: start the climb from the tile outside the stile on the player's side, in line with it; check in game; update the PR text. Covers the #366 review item 'stile jump from too far away (player not always in the pen)'.

## Definition of done
- [x] Climbing the stile from either side, from any approach angle, walks to the in-line tile outside the stile first and then climbs; never from afar (17 crossings at 513144d3d, own RSProx transcript)
- [x] internal gate PASS (qa/stile-approach.md on fork docs) after the FAIL at 8a8c0ca7a (dev's check PASS at 513144d3d in fork docs 52233ff59)
- [x] PR text updated and reviewed (pr-texts/stile-approach.upstream.body in fork docs 52233ff59; fork PR #21 title and body updated)
- [x] accuracy/fix-stile-approach.md on the fork's docs branch (wiki/cache baseline per rule, RSProx comparison recorded) (as accuracy/stile-approach.md, the branch slug handover-check reads; in fork docs 52233ff59)
- [x] fork PR open with green CI at the handed-over commit (#21 head 513144d3d; CI 39363600299, Formatting 39363603819)
- [x] one change only, branch off upstream main (no stacking) (3 commits on 829b9477d, 3 files in generic-locs/stile)

## Log
- 2026-10-21T00:25:19Z migrated from v2 tasks: fix-stile-approach
