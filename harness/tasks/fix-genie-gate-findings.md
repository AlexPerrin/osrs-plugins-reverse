---
id: fix-genie-gate-findings
track: server
title: 'Genie (#355): leave on the next call tick after Talk-to; lamp XP equal to the message'
status: ready
prio: P1
role: server-dev
owner: null
instance: []
branch: acc/genie
commit: null
blocked_by: []
unblocks:
- gate-acc-genie
created: '2026-10-20T06:33:13Z'
updated: '2026-10-20T06:33:13Z'
spec: genie
---
## Task
Genie (#355): leave on the next call tick after Talk-to; lamp XP equal to the message

## Definition of done
- [ ] on acc/genie (new commits on 3ccb86bd8, no new branch): after Talk-to the Genie leaves on his next 10-tick call tick (call timer keeps running), as RSProx 1718 shows (G1 deleted add+10, G3 add+20); test, spec rule 15 and PR text updated
- [ ] lamp Rub: XP given equals the message box (no world rate multiplier, as recorded), or the message prints the XP actually given; lamp bullet and caveat C7 updated
- [ ] PR text 'How it was tested' carries sq30's gate result; fork CI green

## Log
- 2026-10-20T06:33:13Z created (ready)
- 2026-10-20T06:33:13Z set branch=acc/genie spec=genie
- 2026-10-20T06:33:13Z From sq30's upstream gate FAIL at 3ccb86bd8 (fork docs 970ba88ec, qa/genie.md; evidence re/evidence/gate-acc-genie/). Everything else passed: five of sq24's six fixes, sd30's 'Maybe later.' change, CI, scope.
- 2026-10-20T06:33:13Z unblocks gate-acc-genie
