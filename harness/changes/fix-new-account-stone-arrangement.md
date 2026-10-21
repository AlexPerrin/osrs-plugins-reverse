---
slug: fix-new-account-stone-arrangement
track: server
title: 'New account: stone arrangement varbit 4607 default'
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
Upstream's account default sets varbit 4607 to 1 after the login frame is picked, so a new account starts on toplevel 161 with 4607 = 1 (sq25, qa/gameframe-fullscreen-map.md). Check against OSRS's new-account frame and fix the default (fix/new-account-stone-arrangement).

## Definition of done
- [ ] a fresh account's varbit 4607 and toplevel after first login match OSRS's new-account frame (source cited)
- [ ] accuracy/fix-new-account-stone-arrangement.md on the fork's docs branch (wiki/cache baseline per rule, RSProx comparison recorded)
- [ ] fork PR open with green CI at the handed-over commit
- [ ] one change only, branch off upstream main (no stacking)

## Log
- 2026-10-21T00:25:19Z migrated from v2 tasks: fix-new-account-stone-arrangement
