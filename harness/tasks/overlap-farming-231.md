---
id: overlap-farming-231
track: server
title: 'Overlap: farming vs upstream #231'
status: backlog
prio: P2
role: server-dev
owner: null
instance: []
branch: null
commit: null
blocked_by: []
unblocks: []
created: '2026-10-20T01:42:41Z'
updated: '2026-10-20T01:42:41Z'
---
## Task
Our farming work overlaps upstream #231: upstream as a draft PR only, referencing #231 (user, 2026-10-18). Depends on the gates.

## Definition of done
- [ ] internal + upstream gate PASS
- [ ] draft upstream PR referencing the other PR, opened only after the user's OK
- [ ] accuracy/overlap-farming-231.md on the fork's docs branch (wiki/cache baseline per rule, RSProx comparison recorded)
- [ ] fork PR open with green CI at the handed-over commit
- [ ] one change only, branch off upstream main (no stacking)

## Log
- 2026-10-20T01:42:41Z created (backlog)
