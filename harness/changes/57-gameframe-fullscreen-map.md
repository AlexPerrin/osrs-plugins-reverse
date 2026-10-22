---
slug: 57-gameframe-fullscreen-map
track: server
title: '#362 fix/57: merge upstream main, unstack'
prio: P1
role: server-dev
stage: merged
hold: null
deps: []
branch: fix/57-gameframe-fullscreen-map
commit: 113b4bbac39dd3d100658765d0281a73f536747b
local_only: false
owner: null
instances: []
upstream_pr: 362
live_commit: 113b4bbac39dd3d100658765d0281a73f536747b
published_commit: 113b4bbac39dd3d100658765d0281a73f536747b
created: '2026-10-20T01:42:40Z'
updated: '2026-10-22T00:18:20Z'
---
## Change
#362 (fix/57) conflicts with main since #315 added Gameframe.isGameplay (same fallback exclusion as our a01ebb28b); moved to draft 2026-10-19. Merge main into fix/57 (no force-push), keep isGameplay, keep the stone-arrangement varbit restore. The merge exists as e551ba7ce in re/agents/sd24/or (worktree w57, clone g57), NOT on the fork yet. Push it to the fork, update the PR text and the appended comment (pr-texts/gameframe-fullscreen-map.comment); sq25 re-gates both gates in game; then the user's OK for the fast-forward push onto #362, the comment, and ready.

## Definition of done
- [ ] fix/57 merge commit (e551ba7ce or successor) on the fork; no force-push; isGameplay and the stone-arrangement varbit restore both kept
- [ ] internal + upstream gate PASS at the merge commit by sq25 (qa/gameframe-fullscreen-map.md), in game
- [ ] PR text and pr-texts/gameframe-fullscreen-map.comment reviewed; fast-forward push and comment only after the user's OK; #362 out of draft
- [ ] fork CI green at the merge commit

## Log
- 2026-10-21T00:25:19Z migrated from v2 tasks: pr-362-merge-main
- 2026-10-22T00:00:43Z live -> published: observed
- 2026-10-22T00:18:20Z published -> merged: observed
