---
slug: overlap-farming-231
track: server
title: 'Overlap: farming vs upstream #231'
prio: P2
role: server-dev
stage: backlog
hold: null
deps: []
branch: null
commit: null
local_only: false
owner: null
instances: []
created: '2026-10-20T01:42:41Z'
updated: '2026-10-21T00:25:19Z'
---
## Change
Our farming work overlaps upstream #231: upstream as a draft PR only, referencing #231 (user, 2026-10-18). Depends on the gates.

## Definition of done
- [ ] internal + upstream gate PASS
- [ ] draft upstream PR referencing the other PR, opened only after the user's OK
- [ ] accuracy/overlap-farming-231.md on the fork's docs branch (wiki/cache baseline per rule, RSProx comparison recorded)
- [ ] fork PR open with green CI at the handed-over commit
- [ ] one change only, branch off upstream main (no stacking)

## Log
- 2026-10-21T00:25:19Z migrated from v2 tasks: overlap-farming-231
