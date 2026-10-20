---
id: gate-acc-genie
track: server
title: 'Upstream gate: acc/genie e9c7bcac3 (fork #18)'
status: blocked
prio: P1
role: server-qa
owner: null
instance:
- desktop2
branch: acc/genie
commit: 3ccb86bd85c6d96e1e067761d0bb72d64a7d47fd
evidence: /home/alex/osrs-plugins-reverse/re/evidence/gate-acc-genie
blocked_by:
- spec-genie
- fix-genie-gate-findings
unblocks:
- cleanup-scratchpad-qa-acc
created: '2026-10-20T01:42:41Z'
updated: '2026-10-20T06:33:13Z'
summary: 'FAIL at 3ccb86bd8 (qa/genie.md, fork docs 970ba88ec). Must change: (1) after Talk-to he must leave on his next call tick (add''s 10-tick grid; 1718 G1 del add+10, G3 add+20 after face clear t1062/t7156;
  3056 t4040), not HAND_OVER_TICKS=10 after Talk-to (in game: Talk-to t808, del t818, recordings say t814); fix code+test+spec rule 15+PR text. (2) Rub mesbox says 10 Attack XP but 1500 given (world rate
  x150): give unscaled XP or print what''s added; update PR lamp bullet/C7. (3) PR ''How it was tested'': replace ''still need an in-game run'' with the gate''s run. Passing: call interval, wave, smoke/sounds,
  reward message, Rub mesbox form, sd30''s Maybe later +100/leave +103, CI 39375088118 + Formatting 39375089748, gamevals check, scope, template. Left: zones item (not reachable in a session; next gate),
  PR update (orchestrator, after a PASS).'
---
## Task
acc/genie e9c7bcac3 (fork PR #18): full re-gate; zones moved into dbtable.random_event_zones. Search RSProx by the Genie NPC for the comparison. Replaces the pre-audit draft (#354/#355) with a non-force-push merge commit after the user's OK. Note: no accuracy spec exists yet (task spec-genie). Evidence: scratchpad/acc/logs.

## Definition of done
- [x] qa/gate-acc-genie.md on the fork's docs branch with the verdict (PASS/FAIL) at the exact commit
- [x] independent accuracy re-check against the spec and an in-game run at that commit
- [x] fork CI green at the exact commit (build-test 'CI first')
- [x] PR text reviewed against CONTRIBUTING (pr-text-review); scope one change
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
- 2026-10-20T06:32:17Z sq30: record is qa/genie.md on fork docs 970ba88ec (the slug the hooks read), not qa/gate-acc-genie.md. Zones DoD item not ticked: the dbtable zones are read only by the 5-min schedule check, not reachable in a session; e9c7bcac3 zone rows checked by sq24 by code + build.
- 2026-10-20T06:32:38Z handover acc/genie@3ccb86bd8: FAIL at 3ccb86bd8 (qa/genie.md, fork docs 970ba88ec). Must change: (1) after Talk-to he must leave on his next call tick (add's 10-tick grid; 1718 G1 del add+10, G3 add+20 after face clear t1062/t7156; 3056 t4040), not HAND_OVER_TICKS=10 after Talk-to (in game: Talk-to t808, del t818, recordings say t814); fix code+test+spec rule 15+PR text. (2) Rub mesbox says 10 Attack XP but 1500 given (world rate x150): give unscaled XP or print what's added; update PR lamp bullet/C7. (3) PR 'How it was tested': replace 'still need an in-game run' with the gate's run. Passing: call interval, wave, smoke/sounds, reward message, Rub mesbox form, sd30's Maybe later +100/leave +103, CI 39375088118 + Formatting 39375089748, gamevals check, scope, template. Left: zones item (not reachable in a session; next gate), PR update (orchestrator, after a PASS).
- 2026-10-20T06:32:42Z FAIL: (1) leave on the next call tick after Talk-to, not HAND_OVER_TICKS=10 (1718 G1/G3, 3056); (2) Rub message must state the XP actually given (10 shown vs 1500 given at x150) or give unscaled XP; (3) PR 'How it was tested' in-game paragraph. Record qa/genie.md fork docs 970ba88ec.
- 2026-10-20T06:32:42Z set status=failed
- 2026-10-20T06:33:13Z set status=ready owner=
- 2026-10-20T06:33:13Z blocked by fix-genie-gate-findings: re-gate after the two fixes
