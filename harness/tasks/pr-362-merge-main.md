---
id: pr-362-merge-main
track: server
title: '#362 fix/57: merge upstream main, unstack'
status: in-progress
prio: P1
role: server-dev
owner: sd24
instance: []
branch: null
commit: null
blocked_by: []
unblocks: []
created: '2026-10-20T01:42:40Z'
updated: '2026-10-20T02:11:42Z'
---
## Task
#362 (fix/57) conflicts with main since #315 added Gameframe.isGameplay (same fallback exclusion as our a01ebb28b); moved to draft 2026-10-19. Merge main into fix/57 (no force-push), keep isGameplay, keep the stone-arrangement varbit restore. The merge exists as e551ba7ce in re/agents/sd24/or (worktree w57, clone g57), NOT on the fork yet. Push it to the fork, update the PR text and the appended comment (pr-texts/gameframe-fullscreen-map.comment); sq25 re-gates both gates in game; then the user's OK for the fast-forward push onto #362, the comment, and ready.

## Definition of done
- [ ] fix/57 merge commit (e551ba7ce or successor) on the fork; no force-push; isGameplay and the stone-arrangement varbit restore both kept
- [ ] internal + upstream gate PASS at the merge commit by sq25 (qa/gameframe-fullscreen-map.md), in game
- [ ] PR text and pr-texts/gameframe-fullscreen-map.comment reviewed; fast-forward push and comment only after the user's OK; #362 out of draft
- [ ] fork CI green at the merge commit

## Log
- 2026-10-20T01:42:40Z created (ready)
- 2026-10-20T02:11:42Z set status=in-progress owner=sd24
- 2026-10-20T02:11:42Z old harness (session 73): sd24 clone re/agents/sd24 (w57 fix/57 113b4bbac, g362)
