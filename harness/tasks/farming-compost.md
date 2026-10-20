---
id: farming-compost
track: server
title: Compost on a farming patch
status: ready
prio: P3
role: server-dev
owner: null
instance: []
branch: null
commit: null
blocked_by: []
unblocks:
- hub-did-i-compost
created: '2026-10-20T01:42:40Z'
updated: '2026-10-20T01:42:59Z'
---
## Task
Using Compost on a patch gives 'Nothing interesting happens.'; expected 'You treat the allotment with compost.' (wiki Compost), with the patch's compost state set. Unblocks client: Did I Compost hub plugin (gen 44).

## Definition of done
- [ ] Compost on an allotment prints 'You treat the allotment with compost.' and consumes one compost; the patch's compost varbit changes
- [ ] Unblocks client task hub-did-i-compost, verified by the client dev
- [ ] accuracy/farming-compost.md on the fork's docs branch (wiki/cache baseline per rule, RSProx comparison recorded)
- [ ] fork PR open with green CI at the handed-over commit
- [ ] one change only, branch off upstream main (no stacking)

## Log
- 2026-10-20T01:42:40Z created (ready)
- 2026-10-20T01:42:59Z unblocks hub-did-i-compost
