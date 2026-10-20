---
id: gate-acc-cannon
track: server
title: 'Upstream gate: acc/cannon (replaces draft #354/#355)'
status: ready
prio: P1
role: server-qa
owner: null
instance: []
branch: null
commit: null
blocked_by: []
unblocks:
- cleanup-scratchpad-qa-acc
created: '2026-10-20T01:42:40Z'
updated: '2026-10-20T01:42:59Z'
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
