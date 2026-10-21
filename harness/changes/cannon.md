---
slug: cannon
track: server
title: 'Cannon (#354): PR text and spec header wording for the upstream gate'
prio: P1
role: server-dev
stage: live
hold:
  kind: decision
  reason: 'branch acc/cannon is not one-change-one-branch (BR-C1): fold into content/cannon (changes the upstream PR head: your OK per PR), then point integration at it'
  since: '2026-10-21T00:25:19Z'
deps: []
branch: acc/cannon
commit: bc243d141fc27d5128d29d8266cf51e89a4f6c08
local_only: false
owner: null
instances: []
evidence: /home/alex/osrs-plugins-reverse/re/evidence/fix-acc-cannon-text
pr: https://github.com/AlexPerrin/OpenRune-Server/pull/20
ci: https://github.com/AlexPerrin/OpenRune-Server/actions/runs/39350604565
upstream_pr: 354
spec: cannon
summary: 'Text only, fork docs 4c75a5850: PR text checklist and spec header now say none of the RSProx recordings we have (rsprox.net database) shows a player cannon; RESULTS_PENDING filled from CI 39350604565;
  in-game paragraph cites sq24''s run at bc243d141; boots box ticked; fork PR #20 body updated. sq24''s record 60f5e23ea was not on fork docs: cherry-picked as 3b27fffce and pushed. acc/cannon unchanged
  at bc243d141.'
live_commit: bc243d141fc27d5128d29d8266cf51e89a4f6c08
created: '2026-10-20T05:16:26Z'
updated: '2026-10-21T00:25:19Z'
---
## Change
Cannon (#354): PR text and spec header wording for the upstream gate

## Definition of done
- [x] pr-texts/cannon.upstream.body (and .title if needed) and the spec header of the cannon accuracy spec on the fork's docs branch say 'none of the RSProx recordings we have (rsprox.net database) shows a player cannon' instead of 'we have no dumps of the official game'; rest of the text re-read against acc/cannon bc243d141 (pr-text-review)
- [x] no code change; acc/cannon stays at bc243d141 with its green CI

## Log
- 2026-10-21T00:25:19Z migrated from v2 tasks: fix-acc-cannon-text, gate-acc-cannon
- 2026-10-21T00:25:19Z hold decision: branch acc/cannon is not one-change-one-branch (BR-C1): fold into content/cannon (changes the upstream PR head: your OK per PR), then point integration at it
