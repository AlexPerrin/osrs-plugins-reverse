---
id: fix-stile-approach
track: server
title: 'Stile: approach from the outside tile'
status: in-progress
prio: P1
role: server-dev
owner: sd23
instance:
- desktop
branch: null
commit: null
blocked_by: []
unblocks: []
created: '2026-10-20T01:42:40Z'
updated: '2026-10-20T02:11:42Z'
---
## Task
fix/stile-approach 8a8c0ca7a failed the internal gate: the player walks onto the stile's own blocking tile, so it never climbs. Fix: start the climb from the tile outside the stile on the player's side, in line with it; check in game; update the PR text. Covers the #366 review item 'stile jump from too far away (player not always in the pen)'.

## Definition of done
- [ ] Climbing the stile from either side, from any approach angle, walks to the in-line tile outside the stile first and then climbs; never from afar
- [ ] internal gate PASS (qa/stile-approach.md on fork docs) after the FAIL at 8a8c0ca7a
- [ ] PR text updated and reviewed
- [ ] accuracy/fix-stile-approach.md on the fork's docs branch (wiki/cache baseline per rule, RSProx comparison recorded)
- [ ] fork PR open with green CI at the handed-over commit
- [ ] one change only, branch off upstream main (no stacking)

## Log
- 2026-10-20T01:42:40Z created (ready)
- 2026-10-20T02:11:42Z set status=in-progress owner=sd23 instance=desktop
- 2026-10-20T02:11:42Z old harness (session 73): sd23 on fix/stile-approach 513144d3d, in-game on branch server 43598; sq24 recorded upstream gate FAIL at 8646e5ff0 (climb 2 ticks)
