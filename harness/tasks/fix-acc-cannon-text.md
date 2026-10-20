---
id: fix-acc-cannon-text
track: server
title: 'Cannon (#354): PR text and spec header wording for the upstream gate'
status: done
prio: P1
role: server-dev
owner: sd31
instance:
- desktop
branch: acc/cannon
commit: bc243d141fc27d5128d29d8266cf51e89a4f6c08
pr: https://github.com/AlexPerrin/OpenRune-Server/pull/20
ci: https://github.com/AlexPerrin/OpenRune-Server/actions/runs/39350604565
evidence: /home/alex/osrs-plugins-reverse/re/evidence/fix-acc-cannon-text
blocked_by: []
unblocks:
- gate-acc-cannon
created: '2026-10-20T05:16:26Z'
updated: '2026-10-20T05:19:18Z'
summary: 'Text only, fork docs 4c75a5850: PR text checklist and spec header now say none of the RSProx recordings we have (rsprox.net database) shows a player cannon; RESULTS_PENDING filled from CI 39350604565;
  in-game paragraph cites sq24''s run at bc243d141; boots box ticked; fork PR #20 body updated. sq24''s record 60f5e23ea was not on fork docs: cherry-picked as 3b27fffce and pushed. acc/cannon unchanged
  at bc243d141.'
spec: cannon
---
## Task
Cannon (#354): PR text and spec header wording for the upstream gate

## Definition of done
- [x] pr-texts/cannon.upstream.body (and .title if needed) and the spec header of the cannon accuracy spec on the fork's docs branch say 'none of the RSProx recordings we have (rsprox.net database) shows a player cannon' instead of 'we have no dumps of the official game'; rest of the text re-read against acc/cannon bc243d141 (pr-text-review)
- [x] no code change; acc/cannon stays at bc243d141 with its green CI

## Log
- 2026-10-20T05:16:26Z created (ready)
- 2026-10-20T05:16:27Z set branch=acc/cannon commit=bc243d141 spec=cannon
- 2026-10-20T05:16:27Z User 2026-10-20: prioritize #354/#355. From sq24's gate FAIL at bc243d141 (PR text only; code, CI 39350604565 and in-game pass; the 25-min break/repair/decay path is integration-covered and the re-gate checks it in game). sq24's record 60f5e23ea was in re/agents/sq24/docs (check it reached fork docs; push it if not).
- 2026-10-20T05:16:27Z unblocks gate-acc-cannon
- 2026-10-20T05:16:35Z claimed by sd31 (instances desktop)
- 2026-10-20T05:18:52Z handover acc/cannon@bc243d141: Text only, fork docs 4c75a5850: PR text checklist and spec header now say none of the RSProx recordings we have (rsprox.net database) shows a player cannon; RESULTS_PENDING filled from CI 39350604565; in-game paragraph cites sq24's run at bc243d141; boots box ticked; fork PR #20 body updated. sq24's record 60f5e23ea was not on fork docs: cherry-picked as 3b27fffce and pushed. acc/cannon unchanged at bc243d141.
- 2026-10-20T05:19:10Z handover-check PASS
- 2026-10-20T05:19:18Z handover-check PASS; text-only (no ship: integration carries the cannon as fin/cannon). Docs 4c75a5850 + sq24 record 3b27fffce on fork docs; fork PR #20 description updated.
- 2026-10-20T05:19:18Z set status=done
