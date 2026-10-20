---
id: gate-acc-cannon
track: server
title: 'Upstream gate: acc/cannon (replaces draft #354/#355)'
status: gated
prio: P1
role: server-qa
owner: sq29
instance:
- desktop
branch: acc/cannon
commit: bc243d141fc27d5128d29d8266cf51e89a4f6c08
pr: https://github.com/OpenRune/OpenRune-Server/pull/354
evidence: /home/alex/osrs-plugins-reverse/re/evidence/gate-acc-cannon
blocked_by: []
unblocks:
- cleanup-scratchpad-qa-acc
created: '2026-10-20T01:42:40Z'
updated: '2026-10-20T23:10:13Z'
summary: 'PASS at bc243d141: PR text fixed (fork docs 4c75a5850), CI 39350604565/39350606847 green at the sha, gameval check clean, wiki re-read. In game on desktop vs own branch server: 25-min break (msg,
  loc 14916, firing stops), owner-offline break, Repair (msg, fires again). Decay/Nulodion replacement not seen in game (user: finish up), integration-covered as the PR text says. Record: fork docs ec733bc60
  qa/gate-acc-cannon.md. Left: the #354 merge-commit update after the user''s OK (orchestrator).'
---
## Task
acc/cannon bc243d141: dev run first (sd23's report: worktree wt/acc-cannon to bc243d141, delete .data/cache/SERVER, qa.sh dev; fill RESULTS_PENDING), then internal + upstream gates. Replaces the pre-audit draft (#354/#355); upstream merged the pre-audit #356/#357 on 2026-10-19 so the audit delta becomes follow-up fixes off main. Then the gated rewrite replaces the draft's content with a non-force-push merge commit after the user's OK. Evidence: scratchpad/acc/logs.

## Definition of done
- [x] RESULTS_PENDING in the cannon accuracy spec filled from the dev run at bc243d141
- [x] qa/gate-acc-cannon.md on the fork's docs branch with the verdict (PASS/FAIL) at the exact commit
- [x] independent accuracy re-check against the spec and an in-game run at that commit
- [x] fork CI green at the exact commit (build-test 'CI first')
- [x] PR text reviewed against CONTRIBUTING (pr-text-review); scope one change
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
- 2026-10-20T05:36:58Z Orchestrator: desktop2 granted to sq29 (desktop held by sd28 past its time).
- 2026-10-20T06:46:53Z handover acc/cannon@bc243d141: PASS at bc243d141: PR text fixed (fork docs 4c75a5850), CI 39350604565/39350606847 green at the sha, gameval check clean, wiki re-read. In game on desktop vs own branch server: 25-min break (msg, loc 14916, firing stops), owner-offline break, Repair (msg, fires again). Decay/Nulodion replacement not seen in game (user: finish up), integration-covered as the PR text says. Record: fork docs ec733bc60 qa/gate-acc-cannon.md. Left: the #354 merge-commit update after the user's OK (orchestrator).
- 2026-10-20T06:46:57Z set status=gated
- 2026-10-20T23:10:13Z set pr=https://github.com/OpenRune/OpenRune-Server/pull/354
- 2026-10-20T23:10:13Z User's OK (2026-10-19/20: leave draft after PASS): #354 content/cannon fast-forwarded 7b62ce683 -> 9c08b42ca (merge commit, tree = acc/cannon bc243d141, no force), description from fork docs pr-texts/cannon.upstream.body (4c75a5850; via gh api: gh 2.45 can't edit), comment https://github.com/OpenRune/OpenRune-Server/pull/354#issuecomment-6303832955. Ready waits for upstream CI at 9c08b42ca.
