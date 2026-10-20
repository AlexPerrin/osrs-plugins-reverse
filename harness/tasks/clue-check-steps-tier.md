---
id: clue-check-steps-tier
track: server
title: Clue scroll Check steps shows the tier
status: in-progress
prio: P2
role: server-dev
owner: sd29
instance:
- desktop
branch: null
commit: null
blocked_by: []
unblocks:
- hub-clue-steps-tier
created: '2026-10-20T01:42:40Z'
updated: '2026-10-20T04:32:07Z'
---
## Task
Clue scroll Check should say 'You have completed N steps on this <tier> clue scroll.'; local/treasure-trails checkSteps lacks the tier. Unblocks client: Clue Steps hub plugin (gen 44). Gate: internal.

## Definition of done
- [ ] Check on a clue scroll of each tier prints 'You have completed N steps on this <tier> clue scroll.' with the right tier word
- [ ] Unblocks client task hub-clue-steps-tier, verified by the client dev
- [ ] accuracy/clue-check-steps-tier.md on the fork's docs branch (wiki/cache baseline per rule, RSProx comparison recorded)
- [ ] fork PR open with green CI at the handed-over commit
- [ ] one change only, branch off upstream main (no stacking)

## Log
- 2026-10-20T01:42:40Z created (ready)
- 2026-10-20T01:42:58Z unblocks hub-clue-steps-tier
- 2026-10-20T04:32:07Z claimed by sd29 (instances desktop)
