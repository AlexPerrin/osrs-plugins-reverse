---
id: gate-acc-cannon
track: server
title: 'Upstream gate: acc/cannon (replaces draft #354/#355)'
status: in-progress
prio: P1
role: server-qa
owner: sq29
instance:
- desktop
branch: acc/cannon
commit: bc243d141
blocked_by: []
unblocks:
- cleanup-scratchpad-qa-acc
created: '2026-10-20T01:42:40Z'
updated: '2026-10-20T05:19:18Z'
---
## Task
acc/cannon bc243d141: dev run first (sd23's report: worktree wt/acc-cannon to bc243d141, delete .data/cache/SERVER, qa.sh dev; fill RESULTS_PENDING), then internal + upstream gates. Replaces the pre-audit draft (#354/#355); upstream merged the pre-audit #356/#357 on 2026-10-19 so the audit delta becomes follow-up fixes off main. Then the gated rewrite replaces the draft's content with a non-force-push merge commit after the user's OK. Evidence: scratchpad/acc/logs.

## Definition of done
- [ ] RESULTS_PENDING in the cannon accuracy spec filled from the dev run at bc243d141
- [ ] qa/gate-acc-cannon.md on the fork's docs branch with the verdict (PASS/FAIL) at the exact commit
- [ ] independent accuracy re-check against the spec and an in-game run at that commit
- [ ] fork CI green at the exact commit (build-test 'CI first')
- [ ] PR text reviewed against CONTRIBUTING (pr-text-review); scope one change
- [ ] draft PR updated with a merge commit only after the user's OK

## Log
- 2026-10-20T01:42:40Z created (ready)
- 2026-10-20T01:42:59Z unblocks cleanup-scratchpad-qa-acc
- 2026-10-20T02:39:48Z sq24 upstream gate FAIL at bc243d141, PR text only: 'we have no dumps of the official game' -> 'none of the RSProx recordings we have (rsprox.net database) shows a player cannon'; same in spec header. Code/CI (39350604565) pass; in-game checks pass except the 25-min break/repair/decay path (integration-covered). Record 60f5e23ea in re/agents/sq24/docs, NOT pushed. Next: sd text fix, re-check text + the 35-min path in game.
- 2026-10-20T02:39:48Z set status=failed
- 2026-10-20T05:16:27Z set status=ready prio=P1 owner= branch=acc/cannon commit=bc243d141
- 2026-10-20T05:16:27Z blocked by fix-acc-cannon-text: re-gate after the text fix; check the 25-35 min break/repair/decay path in game
- 2026-10-20T05:16:27Z User 2026-10-20: prioritize #354. Re-gate at bc243d141 once fix-acc-cannon-text lands. On PASS: merge commit onto content/cannon (#354), description, ready (user's OK given; announce each upstream step).
- 2026-10-20T05:19:18Z set blocked_by= status=ready
- 2026-10-20T05:19:18Z claimed by sq29 (instances desktop)
