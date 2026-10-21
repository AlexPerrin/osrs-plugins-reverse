---
slug: plank-sack-bank-use
track: server
title: 'Plank sack: Use in the bank fills/empties'
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
created: '2026-10-20T01:42:42Z'
updated: '2026-10-21T00:25:19Z'
---
## Change
Plank sack gap vs OSRS (merged #357): Use in the bank fills the sack from the inventory / empties it into the bank (wiki, 27 Aug 2025). One fix, one PR; no tests-only PR (maintainer on #348).

## Definition of done
- [ ] behaviour matches the wiki text and date cited in the spec, checked in game
- [ ] accuracy/plank-sack-bank-use.md on the fork's docs branch (wiki/cache baseline per rule, RSProx comparison recorded)
- [ ] fork PR open with green CI at the handed-over commit
- [ ] one change only, branch off upstream main (no stacking)

## Log
- 2026-10-21T00:25:19Z migrated from v2 tasks: plank-sack-bank-use
