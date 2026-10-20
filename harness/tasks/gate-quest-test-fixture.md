---
id: gate-quest-test-fixture
track: server
title: 'Upstream gate: quest test fixture'
status: ready
prio: P1
role: server-qa
owner: null
instance: []
branch: null
commit: null
blocked_by: []
unblocks:
- gate-vampyre-slayer
- cleanup-pirates-treasure
created: '2026-10-20T01:42:41Z'
updated: '2026-10-20T01:42:59Z'
---
## Task
Gate the QuestTalkFixture branch (shared base for every quest PR) and its PR text (UPSTREAM-PRS.md). Goes upstream first in the PR order (waiting item: order of new upstream PRs).

## Definition of done
- [ ] qa/gate-quest-test-fixture.md on the fork's docs branch with the verdict (PASS/FAIL) at the exact commit
- [ ] independent accuracy re-check against the spec and an in-game run at that commit
- [ ] fork CI green at the exact commit (build-test 'CI first')
- [ ] PR text reviewed against CONTRIBUTING (pr-text-review); scope one change

## Log
- 2026-10-20T01:42:41Z created (ready)
- 2026-10-20T01:42:59Z unblocks unstack-367-368
- 2026-10-20T01:42:59Z unblocks gate-vampyre-slayer
- 2026-10-20T01:42:59Z unblocks cleanup-pirates-treasure
