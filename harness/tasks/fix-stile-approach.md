---
id: fix-stile-approach
track: server
title: 'Stile: approach from the outside tile'
status: shipped
prio: P1
role: server-dev
owner: sd27
instance:
- desktop
branch: fix/stile-approach
commit: 513144d3dfcfcfd5613123bbe3457b952192c384
pr: https://github.com/AlexPerrin/OpenRune-Server/pull/21
ci: https://github.com/AlexPerrin/OpenRune-Server/actions/runs/39363600299
evidence: /home/alex/osrs-plugins-reverse/re/evidence/fix-stile-approach
blocked_by: []
unblocks: []
created: '2026-10-20T01:42:40Z'
updated: '2026-10-20T04:21:24Z'
summary: '513144d3d on fork (PR #21), CI+Formatting green; 17 in-game crossings from every side/angle walk to the in-line tile, step on T+1, climb T+2, step off T+5 = RSProx 1718; dev''s check PASS. Docs
  554bd95c7 (spec, PR text, qa record, STATE) local in re/agents/sd27/or branch docs: orchestrator to publish it and update PR #21''s body (hook denied both).'
integration: a07ec2d8f
---
## Task
fix/stile-approach 8a8c0ca7a failed the internal gate: the player walks onto the stile's own blocking tile, so it never climbs. Fix: start the climb from the tile outside the stile on the player's side, in line with it; check in game; update the PR text. Covers the #366 review item 'stile jump from too far away (player not always in the pen)'.

## Definition of done
- [x] Climbing the stile from either side, from any approach angle, walks to the in-line tile outside the stile first and then climbs; never from afar (17 crossings at 513144d3d, own RSProx transcript)
- [x] internal gate PASS (qa/stile-approach.md on fork docs) after the FAIL at 8a8c0ca7a (dev's check PASS at 513144d3d in fork docs 52233ff59)
- [x] PR text updated and reviewed (pr-texts/stile-approach.upstream.body in fork docs 52233ff59; fork PR #21 title and body updated)
- [x] accuracy/fix-stile-approach.md on the fork's docs branch (wiki/cache baseline per rule, RSProx comparison recorded) (as accuracy/stile-approach.md, the branch slug handover-check reads; in fork docs 52233ff59)
- [x] fork PR open with green CI at the handed-over commit (#21 head 513144d3d; CI 39363600299, Formatting 39363603819)
- [x] one change only, branch off upstream main (no stacking) (3 commits on 829b9477d, 3 files in generic-locs/stile)

## Log
- 2026-10-20T01:42:40Z created (ready)
- 2026-10-20T02:11:42Z set status=in-progress owner=sd23 instance=desktop
- 2026-10-20T02:11:42Z old harness (session 73): sd23 on fix/stile-approach 513144d3d, in-game on branch server 43598; sq24 recorded upstream gate FAIL at 8646e5ff0 (climb 2 ticks)
- 2026-10-20T02:12:35Z 8646e5ff0 pushed (CI 39353940765 green); sq24 upstream gate FAIL (climb 2 ticks after step-on, 1718 says 1; record commit 2a5305fb9 in re/agents/sq24/docs, NOT pushed). sd23 fix 513144d3d (STEP_ON_TICKS=1 + timing test) in re/agents/sd23/or, NOT pushed; in-game cross pending (desktop grant). sd23 docs commit 65cc94f05 (detached, re/agents/sd23/docs) NOT pushed.
- 2026-10-20T02:12:44Z sd23 done: 513144d3d (STEP_ON_TICKS=1, StileScriptTest 6/6, generic-locs integration 10/0, crossed in game from S, N and along fence). Docs de3dac3fa on top of 65cc94f05 (detached in re/agents/sd23/docs). Both NOT pushed (stale hook in old session). Next: push 513144d3d + docs to fork, CI, then sq24 re-gate. Also pending push: acc/genie a44a3daa0. Sheep Shearer 01da94f17 and acc/cannon bc243d141 are with sq24 for the upstream gate.
- 2026-10-20T02:12:44Z set status=handover branch=fix/stile-approach commit=513144d3d
- 2026-10-20T02:39:48Z sq24 FAIL record for 8646e5ff0 = 2a5305fb9 (re/agents/sq24/docs docs-publish, NOT pushed); 513144d3d not re-gated yet.
- 2026-10-20T02:47:39Z handover-check FAIL (7 items; see the check output)
- 2026-10-20T02:47:55Z set status=ready owner=
- 2026-10-20T02:47:55Z back to ready after the hand-over check: 513144d3d is only in re/agents/sd23/or; next dev pushes it to the fork (fix/stile-approach), opens/updates the fork PR, waits for CI at the sha, copies the evidence (tests, in-game) to re/evidence/fix-stile-approach/, ticks the DoD, then task.py handover --repo <clone> --pr --ci
- 2026-10-20T03:16:26Z claimed by sd27 (instances desktop)
- 2026-10-20T03:35:11Z sd27: 513144d3d is on the fork (PR #21 head), CI 39363600299 + Formatting 39363603819 green; in game 17 crossings on the branch server, own RSProx transcript matches the 1718 ticks; docs commit 554bd95c7 (local branch docs in re/agents/sd27/or, on fork/docs 8c59858aa) is NOT on the fork: the hook denied publishing docs and editing PR #21 (orchestrator only). Orchestrator: publish 554bd95c7 to fork docs and update #21 from pr-texts/stile-approach.{upstream.body,title}.
- 2026-10-20T03:35:16Z handover fix/stile-approach@513144d3d: 513144d3d on fork (PR #21), CI+Formatting green; 17 in-game crossings from every side/angle walk to the in-line tile, step on T+1, climb T+2, step off T+5 = RSProx 1718; dev's check PASS. Docs 554bd95c7 (spec, PR text, qa record, STATE) local in re/agents/sd27/or branch docs: orchestrator to publish it and update PR #21's body (hook denied both).
- 2026-10-20T03:57:55Z handover-check PASS
- 2026-10-20T03:57:59Z sd27: docs rebased onto fork/docs 8c4a29bfa and published as 52233ff59; fork PR #21 title and body updated from pr-texts; handover-check PASS.
- 2026-10-20T04:21:24Z set status=shipped integration=a07ec2d8f
- 2026-10-20T04:21:24Z Shipped in the re-pin: integration a07ec2d8f (gate PASS 844/214, boot clean; repin-2). Deploy pending (image needs the live key).
