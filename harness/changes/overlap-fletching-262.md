---
slug: overlap-fletching-262
track: server
title: 'Overlap: fletching vs upstream #262'
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
Our fletching overlaps upstream #262: upstream as a draft PR only, referencing #262 (user, 2026-10-18). fix/06, 07, 69 stay local until #299 merges. Depends on the gates.

## Definition of done
- [ ] internal + upstream gate PASS
- [ ] draft upstream PR referencing the other PR, opened only after the user's OK
- [ ] accuracy/overlap-fletching-262.md on the fork's docs branch (wiki/cache baseline per rule, RSProx comparison recorded)
- [ ] fork PR open with green CI at the handed-over commit
- [ ] one change only, branch off upstream main (no stacking)

## Log
- 2026-10-21T00:25:19Z migrated from v2 tasks: overlap-fletching-262
