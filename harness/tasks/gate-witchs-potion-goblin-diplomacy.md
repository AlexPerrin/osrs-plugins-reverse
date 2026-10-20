---
id: gate-witchs-potion-goblin-diplomacy
track: server
title: 'Upstream gates: Witch''s Potion and Goblin Diplomacy'
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
updated: '2026-10-20T01:42:41Z'
---
## Task
Goblin Diplomacy 4f5e59627 and Witch's Potion 4dc1ac9c7 (the -829 branches): internal PASS; the upstream gate needs the RSProx comparison (search by NPC id / TEXT; nothing transcribed yet) and, for Goblin Diplomacy, the text fix (gate + in-game run + cheats; 'moved verbatim from #328'). PR texts. Then the user's OK to replace #367/#368 with a merge commit. Depends on the quest fixture merging upstream first (unstack-367-368).

## Definition of done
- [ ] qa/gate-witchs-potion-goblin-diplomacy.md on the fork's docs branch with the verdict (PASS/FAIL) at the exact commit
- [ ] independent accuracy re-check against the spec and an in-game run at that commit
- [ ] fork CI green at the exact commit (build-test 'CI first')
- [ ] PR text reviewed against CONTRIBUTING (pr-text-review); scope one change
- [ ] RSProx comparison for both quests recorded in their accuracy specs
- [ ] #367/#368 replaced with a merge commit only after the user's OK

## Log
- 2026-10-20T01:42:41Z created (ready)
