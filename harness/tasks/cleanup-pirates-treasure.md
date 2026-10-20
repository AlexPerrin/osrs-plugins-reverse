---
id: cleanup-pirates-treasure
track: server
title: 'Pirate''s Treasure: squash fixup, base on the fixture'
status: blocked
prio: P3
role: server-dev
owner: null
instance: []
branch: null
commit: null
blocked_by:
- gate-quest-test-fixture
unblocks: []
created: '2026-10-20T01:42:42Z'
updated: '2026-10-20T01:42:59Z'
---
## Task
content/pirates-treasure on the fork: squash the fixup commit and rebase onto the quest test fixture once it is merged upstream.

## Definition of done
- [ ] one clean commit off upstream main (fixture included upstream), fork CI green
- [ ] internal gate still PASS

## Log
- 2026-10-20T01:42:42Z created (backlog)
- 2026-10-20T01:42:59Z blocked by gate-quest-test-fixture: rebase onto the fixture once it is merged upstream
