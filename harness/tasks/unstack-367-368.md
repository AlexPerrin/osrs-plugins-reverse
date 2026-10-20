---
id: unstack-367-368
track: server
title: 'Unstack #367 Goblin Diplomacy and #368 Witch''s Potion'
status: in-progress
prio: P2
role: server-dev
owner: sd25
instance: []
branch: null
commit: null
blocked_by: []
unblocks: []
created: '2026-10-20T01:42:40Z'
updated: '2026-10-20T02:47:39Z'
---
## Task
#367 Goblin Diplomacy (draft since 2026-10-18, user) and #368 Witch's Potion are stacked on #366's old f69c8ed4c (old Sheep Shearer + R&J). Rebuild each off upstream main alone (the -829 branches 4f5e59627 / 4dc1ac9c7 are the candidates, internal PASS); any shared base (QuestTalkFixture) merges upstream first. Then replace #367/#368 with a non-force-push merge commit after the user's OK (sq24).

## Definition of done
- [ ] content/goblin-diplomacy and content/witchs-potion each build off upstream main alone with no commit from #366
- [ ] QuestTalkFixture (or whatever shared base) merged upstream before either PR is updated
- [ ] #367 and #368 updated with a merge commit (no force-push) only after the user's OK
- [ ] accuracy/unstack-367-368.md on the fork's docs branch (wiki/cache baseline per rule, RSProx comparison recorded)
- [ ] fork PR open with green CI at the handed-over commit
- [ ] one change only, branch off upstream main (no stacking)

## Log
- 2026-10-20T01:42:40Z created (backlog)
- 2026-10-20T01:42:59Z blocked by gate-quest-test-fixture: shared base QuestTalkFixture must be gated and merged upstream first
- 2026-10-20T02:11:42Z set status=in-progress owner=sd25
- 2026-10-20T02:11:42Z old harness (session 73): sd25 content/goblin-diplomacy-unstacked d77ac2b76, witchs-potion 23e1b7c5d (run-wp); docs ce5e5c8bb
- 2026-10-20T02:12:35Z User rule 2026-10-19: no stacked PRs. Option A chosen: no QuestTalkFixture (quests have no tests). Fork branches content/goblin-diplomacy-unstacked d77ac2b76 (CI 39353936705) and content/witchs-potion-unstacked 23e1b7c5d (CI 39349238661 rerun after infra boot flake); each = PR head + merge commit, quest-only diff, merges clean in any order with #366. Gameval clash: witchs_potion_progress 65449 = sheep_shearer_progress; second to merge reruns fix_gameval_conflicts. Both quests completed in game. Final report pending; then upstream gate, then user OK for the ff pushes onto #367/#368. Not blocked by the fixture any more.
- 2026-10-20T02:47:39Z set blocked_by=
- 2026-10-20T02:47:39Z edge to gate-quest-test-fixture dropped: option A, no fixture (user rule: no stacked PRs)
