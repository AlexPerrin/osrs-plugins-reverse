---
id: unstack-367-368
track: server
title: 'Unstack #367 Goblin Diplomacy and #368 Witch''s Potion'
status: ready
prio: P2
role: server-dev
owner: null
instance: []
branch: content/goblin-diplomacy-unstacked
commit: d77ac2b76eefe47211f4d15138563c19ddf19bfd
ci: https://github.com/AlexPerrin/OpenRune-Server/actions/runs/39353936705
blocked_by: []
unblocks: []
created: '2026-10-20T01:42:40Z'
updated: '2026-10-20T02:50:16Z'
summary: GD d77ac2b76 CI green; second branch content/witchs-potion-unstacked 23e1b7c5d (CI rerun 39349238661 queued). No fork PR yet; docs commit ce5e5c8bb + uncommitted text edits in re/agents/sd25/docs.
  See log.
spec: goblin-diplomacy
---
## Task
#367 Goblin Diplomacy (draft since 2026-10-18, user) and #368 Witch's Potion are stacked on #366's old f69c8ed4c (old Sheep Shearer + R&J). Rebuild each off upstream main alone (the -829 branches 4f5e59627 / 4dc1ac9c7 are the candidates, internal PASS); any shared base (QuestTalkFixture) merges upstream first. Then replace #367/#368 with a non-force-push merge commit after the user's OK (sq24).

## Definition of done
- [x] content/goblin-diplomacy and content/witchs-potion each build off upstream main alone with no commit from #366
- [ ] QuestTalkFixture (or whatever shared base) merged upstream before either PR is updated
- [ ] #367 and #368 updated with a merge commit (no force-push) only after the user's OK
- [ ] accuracy/unstack-367-368.md on the fork's docs branch (wiki/cache baseline per rule, RSProx comparison recorded)
- [ ] fork PR open with green CI at the handed-over commit
- [x] one change only, branch off upstream main (no stacking)

## Log
- 2026-10-20T01:42:40Z created (backlog)
- 2026-10-20T01:42:59Z blocked by gate-quest-test-fixture: shared base QuestTalkFixture must be gated and merged upstream first
- 2026-10-20T02:11:42Z set status=in-progress owner=sd25
- 2026-10-20T02:11:42Z old harness (session 73): sd25 content/goblin-diplomacy-unstacked d77ac2b76, witchs-potion 23e1b7c5d (run-wp); docs ce5e5c8bb
- 2026-10-20T02:12:35Z User rule 2026-10-19: no stacked PRs. Option A chosen: no QuestTalkFixture (quests have no tests). Fork branches content/goblin-diplomacy-unstacked d77ac2b76 (CI 39353936705) and content/witchs-potion-unstacked 23e1b7c5d (CI 39349238661 rerun after infra boot flake); each = PR head + merge commit, quest-only diff, merges clean in any order with #366. Gameval clash: witchs_potion_progress 65449 = sheep_shearer_progress; second to merge reruns fix_gameval_conflicts. Both quests completed in game. Final report pending; then upstream gate, then user OK for the ff pushes onto #367/#368. Not blocked by the fixture any more.
- 2026-10-20T02:47:39Z set blocked_by=
- 2026-10-20T02:47:39Z edge to gate-quest-test-fixture dropped: option A, no fixture (user rule: no stacked PRs)
- 2026-10-20T02:49:28Z sd25 final: option A (no QuestTalkFixture: the quests have no tests; DoD item 2 obsolete). GD content/goblin-diplomacy-unstacked d77ac2b76 = #367 head 5398c6087 + merge f81121fa7 (tree 4f5e59627) + RSProx fix (crate one-'but' msg, synth 2581, dye synth 2392); CI 39353936705 + Formatting 39353938933 green. WP content/witchs-potion-unstacked 23e1b7c5d = #368 head 18afab0a7 + merge (tree 4dc1ac9c7); Formatting green, CI 39349238661 failed at boot only (embedded Postgres infra), rerun still QUEUED at handover; boots locally. Both quests completed in game. Docs: re/agents/sd25/docs commit ce5e5c8bb (NOT pushed) + uncommitted worktree edits to pr-texts/{goblin-diplomacy,witchs-potion}.{upstream.body,comment} and accuracy/{goblin-diplomacy,witchs-potion}.md. Gameval clash 65449 with #366. Next: WP CI green; commit+push docs; upstream gate at d77ac2b76 / 23e1b7c5d; user OK for ff pushes onto content/goblin-diplomacy (#367, retitle 'feat: Goblin Diplomacy quest') and content/witchs-potion (#368), together with opening the lookout-ladder PR.
- 2026-10-20T02:49:28Z handover content/goblin-diplomacy-unstacked@d77ac2b76: GD d77ac2b76 CI green; second branch content/witchs-potion-unstacked 23e1b7c5d (CI rerun 39349238661 queued). No fork PR yet; docs commit ce5e5c8bb + uncommitted text edits in re/agents/sd25/docs. See log.
- 2026-10-20T02:49:57Z handover-check FAIL (4 items; see the check output)
- 2026-10-20T02:50:16Z set spec=goblin-diplomacy status=ready owner=
- 2026-10-20T02:50:16Z back to ready after the hand-over check (partial): next dev records the fork PR URLs (--pr), copies the in-game and CI evidence to re/evidence/unstack-367-368/, ticks the DoD items met, lists Witch's Potion 23e1b7c5d (CI rerun 39349238661) in the summary, then task.py handover; specs: accuracy/goblin-diplomacy.md, accuracy/witchs-potion.md
