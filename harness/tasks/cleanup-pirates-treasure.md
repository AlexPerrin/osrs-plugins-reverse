---
id: cleanup-pirates-treasure
track: server
title: 'Pirate''s Treasure: squash fixup, base on the fixture'
status: ready
prio: P3
role: server-dev
owner: null
instance: []
branch: null
commit: null
blocked_by: []
unblocks: []
created: '2026-10-20T01:42:42Z'
updated: '2026-10-20T04:32:06Z'
---
## Task
content/pirates-treasure on the fork: squash the fixup commit and rebase onto the quest test fixture once it is merged upstream.

## Definition of done
- [ ] one clean commit off upstream main (fixture included upstream), fork CI green
- [ ] internal gate still PASS

## Log
- 2026-10-20T01:42:42Z created (backlog)
- 2026-10-20T01:42:59Z blocked by gate-quest-test-fixture: rebase onto the fixture once it is merged upstream
- 2026-10-20T04:31:54Z set blocked_by=
- 2026-10-20T04:31:54Z Unblocked (2026-10-20): no QuestTalkFixture upstream (option A). Before the gate, the branch must drop its interaction tests and the fixture, as #366/#367 did, and merge on its own off main.
- 2026-10-20T04:32:06Z set status=ready
