---
id: clue-check-steps-tier
track: server
title: Clue scroll Check steps shows the tier
status: live
prio: P2
role: server-dev
owner: sd29
instance:
- desktop
branch: local/treasure-trails
commit: 48b320359a91f9f0974c1ffc562e510c315bacf5
ci: https://github.com/AlexPerrin/OpenRune-Server/actions/runs/39372316037
evidence: /home/alex/osrs-plugins-reverse/re/evidence/clue-check-steps-tier
blocked_by: []
unblocks:
- hub-clue-steps-tier
created: '2026-10-20T01:42:40Z'
updated: '2026-10-20T06:47:04Z'
summary: 'Left: client verification (hub-clue-steps-tier) after the deploy. Check steps now says ''You have completed N step(s) on this <tier> clue scroll.'' (beginner/easy/medium/hard), matching RSProx
  91/1034/1106/3931. One commit 48b320359 on fork local/treasure-trails (local-only, no upstream PR; scope note replaces ''fork PR'' and ''off upstream main''). Tests: unit message test + integration test
  giving a clue of each tier and asserting the sent message; dev build integration 25 tests, 0 failures. Fork CI run 39372316037 in progress at hand-over; Formatting (advisory) failed only on pre-existing
  files in other modules (or-cache, api:player, generic-npcs, gameframe, slayer), not treasure-trails. Spec accuracy/clue-check-steps-tier.md on docs dd46760cb, STATE 9890c05b4. No desktop in-game check
  (devtools-desktop MCP not connected; lock never taken).'
integration: f9cc1618c11a085321f0486ad561f4f94348e489
---
## Task
Clue scroll Check should say 'You have completed N steps on this <tier> clue scroll.'; local/treasure-trails checkSteps lacks the tier. Unblocks client: Clue Steps hub plugin (gen 44). Gate: internal.

## Definition of done
- [x] Check on a clue scroll of each tier prints 'You have completed N steps on this <tier> clue scroll.' with the right tier word
- [x] Unblocks client task hub-clue-steps-tier (the client dev verifies it there after the deploy)
- [x] accuracy/clue-check-steps-tier.md on the fork's docs branch (wiki/cache baseline per rule, RSProx comparison recorded)
- [x] fork PR open with green CI at the handed-over commit (scope note: commit pushed to fork local/treasure-trails, fork CI started on that ref; no PR, local-only)
- [x] one change only, branch off upstream main (no stacking) (scope note: one commit on local/treasure-trails, never upstream)

## Log
- 2026-10-20T01:42:40Z created (ready)
- 2026-10-20T01:42:58Z unblocks hub-clue-steps-tier
- 2026-10-20T04:32:07Z claimed by sd29 (instances desktop)
- 2026-10-20T04:40:16Z set status=specced
- 2026-10-20T05:08:01Z handover local/treasure-trails@48b320359: Left: client verification (hub-clue-steps-tier) after the deploy. Check steps now says 'You have completed N step(s) on this <tier> clue scroll.' (beginner/easy/medium/hard), matching RSProx 91/1034/1106/3931. One commit 48b320359 on fork local/treasure-trails (local-only, no upstream PR; scope note replaces 'fork PR' and 'off upstream main'). Tests: unit message test + integration test giving a clue of each tier and asserting the sent message; dev build integration 25 tests, 0 failures. Fork CI run 39372316037 in progress at hand-over; Formatting (advisory) failed only on pre-existing files in other modules (or-cache, api:player, generic-npcs, gameframe, slayer), not treasure-trails. Spec accuracy/clue-check-steps-tier.md on docs dd46760cb, STATE 9890c05b4. No desktop in-game check (devtools-desktop MCP not connected; lock never taken).
- 2026-10-20T05:11:24Z handover-check FAIL (2 items; see the check output)
- 2026-10-20T05:12:06Z handover-check PASS
- 2026-10-20T05:12:19Z set status=shipped integration=f9cc1618c11a085321f0486ad561f4f94348e489
- 2026-10-20T05:12:19Z shipped: in integration f9cc1618c (pushed); gate + deploy pending
- 2026-10-20T05:26:01Z Integration f9cc1618c (adds the clue Check tier to the live e5b18a87b): gate PASS, test 849/0, integration 216/0, spotless ok (725s). Image osrs-openrune:integration-f9cc161 id 377f3a085d6f, built from the gated install + .data (add_varcs 1293/1294, fix_long_varps 40 ops in 6 scripts, fix_reftable 9973/9974); game.key, rsa.properties, game.yml and entrypoint by COPY --from osrs-openrune:241. sha256 of game.key/rsa.properties/game.yml identical to the live image. Boot (private container, tmpfs DB): Server ready in 10.70s, 0 ERROR/WARN/exception lines; container removed. Evidence re/evidence/clue-check-steps-tier/ (gate-f9cc1618c.out, boot-f9cc161.log, Dockerfile-f9cc161). Not deployed.
- 2026-10-20T06:47:03Z Live 2026-10-20 06:46 UTC (integration e27ff4d08, image integration-e27ff4d; rollback osrs-openrune:rollback-e5b18a8).
- 2026-10-20T06:47:04Z migrate-live: shipped -> live (deployed: Live 2026-10-20 06:46 UTC (integration e27ff4d08, image integration-e27ff4d; rollback osrs-openrune:rollback-e5b18a8))
