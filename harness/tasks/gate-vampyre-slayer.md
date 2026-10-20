---
id: gate-vampyre-slayer
track: server
title: 'Upstream gate: Vampyre Slayer'
status: ready
prio: P2
role: server-qa
owner: null
instance: []
branch: null
commit: null
blocked_by: []
unblocks: []
created: '2026-10-20T01:42:41Z'
updated: '2026-10-20T04:32:06Z'
---
## Task
Gate the Vampyre Slayer branch (fin/* head) and its PR text; depends on the quest fixture (gate-quest-test-fixture, then its upstream merge).

## Definition of done
- [ ] qa/gate-vampyre-slayer.md on the fork's docs branch with the verdict (PASS/FAIL) at the exact commit
- [ ] independent accuracy re-check against the spec and an in-game run at that commit
- [ ] fork CI green at the exact commit (build-test 'CI first')
- [ ] PR text reviewed against CONTRIBUTING (pr-text-review); scope one change

## Log
- 2026-10-20T01:42:41Z created (backlog)
- 2026-10-20T01:42:59Z blocked by gate-quest-test-fixture: depends on the quest fixture
- 2026-10-20T04:31:54Z set blocked_by=
- 2026-10-20T04:31:54Z Unblocked (2026-10-20): no QuestTalkFixture upstream (option A). Before the gate, the branch must drop its interaction tests and the fixture, as #366/#367 did, and merge on its own off main.
- 2026-10-20T04:32:06Z set status=ready
