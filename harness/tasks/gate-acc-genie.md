---
id: gate-acc-genie
track: server
title: 'Upstream gate: acc/genie e9c7bcac3 (fork #18)'
status: in-progress
prio: P1
role: server-qa
owner: sq30
instance:
- desktop2
branch: acc/genie
commit: 3ccb86bd8
blocked_by:
- spec-genie
unblocks:
- cleanup-scratchpad-qa-acc
created: '2026-10-20T01:42:41Z'
updated: '2026-10-20T05:42:30Z'
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
- 2026-10-20T02:11:42Z set status=in-progress owner=sq24 instance=desktop
- 2026-10-20T02:11:43Z old harness (session 73): sq24 clone at e9c7bcac3; desktop grant pending
- 2026-10-20T02:12:35Z sq24 upstream gate FAIL at e9c7bcac3 (RSProx 3056/1718: call interval, wave, smokepuff+sounds, reward msg, Rub mesbox, leave timing). Record qa/genie.md on fork docs 9a451125c. sd23 fixed: acc/genie 9a222bd53 (pushed, CI 39353948424 + Formatting 39353950406 green) + a44a3daa0 (skill name capitalised; NOT pushed, stale hook). Needs re-gate at a44a3daa0 after push+CI.
- 2026-10-20T02:12:35Z set status=failed
- 2026-10-20T05:16:27Z set status=ready prio=P1 owner= branch=acc/genie commit=a44a3daa0
- 2026-10-20T05:16:27Z blocked by spec-genie: re-gate at a44a3daa0 against the new spec
- 2026-10-20T05:16:27Z User 2026-10-20: prioritize #355. sd23's fixes for the 6 RSProx divergences are 9a222bd53 + a44a3daa0 (pushed 2026-10-20). Re-gate after spec-genie. On PASS: merge commit onto content/genie (#355), description, ready (user's OK given; announce each upstream step).
- 2026-10-20T05:42:30Z unblocked by spec-genie (done)
- 2026-10-20T05:42:30Z set commit=3ccb86bd8
- 2026-10-20T05:42:30Z claimed by sq30 (instances desktop2)
