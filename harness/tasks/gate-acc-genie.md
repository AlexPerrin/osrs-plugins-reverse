---
id: gate-acc-genie
track: server
title: 'Upstream gate: acc/genie e9c7bcac3 (fork #18)'
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
created: '2026-10-20T01:42:41Z'
updated: '2026-10-20T01:42:59Z'
---
## Task
acc/genie e9c7bcac3 (fork PR #18): full re-gate; zones moved into dbtable.random_event_zones. Search RSProx by the Genie NPC for the comparison. Replaces the pre-audit draft (#354/#355) with a non-force-push merge commit after the user's OK. Note: no accuracy spec exists yet (task spec-genie). Evidence: scratchpad/acc/logs.

## Definition of done
- [ ] qa/gate-acc-genie.md on the fork's docs branch with the verdict (PASS/FAIL) at the exact commit
- [ ] independent accuracy re-check against the spec and an in-game run at that commit
- [ ] fork CI green at the exact commit (build-test 'CI first')
- [ ] PR text reviewed against CONTRIBUTING (pr-text-review); scope one change
- [ ] dbtable.random_event_zones checked against the spec's zone list in game
- [ ] draft PR updated with a merge commit only after the user's OK

## Log
- 2026-10-20T01:42:41Z created (ready)
- 2026-10-20T01:42:59Z unblocks cleanup-scratchpad-qa-acc
