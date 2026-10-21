---
slug: sheep-shearer-thing-runaway
track: server
title: 'Sheep Shearer: The Thing runs away when sheared'
prio: P3
role: server-dev
stage: ready
hold: null
deps: []
branch: null
commit: null
local_only: false
owner: null
instances: []
created: '2026-10-20T01:42:40Z'
updated: '2026-10-21T00:25:19Z'
---
## Change
Implement the penguin (The Thing) running away on shear per the OSRS Wiki, lifting the caveat in #366's PR text.

## Definition of done
- [ ] Shearing The Thing makes it run away as on the wiki (message, movement, no wool), with the RSProx recording as evidence where it covers it
- [ ] accuracy/sheep-shearer-thing-runaway.md on the fork's docs branch (wiki/cache baseline per rule, RSProx comparison recorded)
- [ ] fork PR open with green CI at the handed-over commit
- [ ] one change only, branch off upstream main (no stacking)

## Log
- 2026-10-21T00:25:19Z migrated from v2 tasks: sheep-shearer-thing-runaway
