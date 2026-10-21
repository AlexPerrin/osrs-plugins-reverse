---
slug: treasure-trails
track: server
title: Clue scroll Check steps shows the tier
prio: P2
role: server-dev
stage: live
hold: null
deps: []
branch: local/treasure-trails
commit: 48b320359a91f9f0974c1ffc562e510c315bacf5
local_only: true
owner: null
instances: []
evidence: /home/alex/osrs-plugins-reverse/re/evidence/clue-check-steps-tier
ci: https://github.com/AlexPerrin/OpenRune-Server/actions/runs/39372316037
summary: 'Left: client verification (hub-clue-steps-tier) after the deploy. Check steps now says ''You have completed N step(s) on this <tier> clue scroll.'' (beginner/easy/medium/hard), matching RSProx
  91/1034/1106/3931. One commit 48b320359 on fork local/treasure-trails (local-only, no upstream PR; scope note replaces ''fork PR'' and ''off upstream main''). Tests: unit message test + integration test
  giving a clue of each tier and asserting the sent message; dev build integration 25 tests, 0 failures. Fork CI run 39372316037 in progress at hand-over; Formatting (advisory) failed only on pre-existing
  files in other modules (or-cache, api:player, generic-npcs, gameframe, slayer), not treasure-trails. Spec accuracy/clue-check-steps-tier.md on docs dd46760cb, STATE 9890c05b4. No desktop in-game check
  (devtools-desktop MCP not connected; lock never taken).'
live_commit: 48b320359a91f9f0974c1ffc562e510c315bacf5
created: '2026-10-20T01:42:40Z'
updated: '2026-10-21T00:25:19Z'
---
## Change
Clue scroll Check should say 'You have completed N steps on this <tier> clue scroll.'; local/treasure-trails checkSteps lacks the tier. Unblocks client: Clue Steps hub plugin (gen 44). Gate: internal.

## Definition of done
- [x] Check on a clue scroll of each tier prints 'You have completed N steps on this <tier> clue scroll.' with the right tier word
- [x] Unblocks client task hub-clue-steps-tier (the client dev verifies it there after the deploy)
- [x] accuracy/clue-check-steps-tier.md on the fork's docs branch (wiki/cache baseline per rule, RSProx comparison recorded)
- [x] fork PR open with green CI at the handed-over commit (scope note: commit pushed to fork local/treasure-trails, fork CI started on that ref; no PR, local-only)
- [x] one change only, branch off upstream main (no stacking) (scope note: one commit on local/treasure-trails, never upstream)

## Log
- 2026-10-21T00:25:19Z migrated from v2 tasks: clue-check-steps-tier
