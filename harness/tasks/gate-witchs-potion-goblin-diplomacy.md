---
id: gate-witchs-potion-goblin-diplomacy
track: server
title: 'Upstream gates: Witch''s Potion and Goblin Diplomacy'
status: done
prio: P2
role: server-qa
owner: null
instance:
- desktop
branch: content/goblin-diplomacy-unstacked
commit: d77ac2b76eefe47211f4d15138563c19ddf19bfd
pr: https://github.com/OpenRune/OpenRune-Server/pull/367
evidence: /home/alex/osrs-plugins-reverse/re/evidence/gate-witchs-potion-goblin-diplomacy
blocked_by: []
unblocks: []
created: '2026-10-20T01:42:41Z'
updated: '2026-10-20T04:27:25Z'
summary: 'Goblin Diplomacy upstream gate PASS at d77ac2b76 (record qa/goblin-diplomacy.md, fork docs 238550879). CI 39353936705 + Formatting 39353938933 green at the sha; fix_gameval_conflicts --check clean
  on main and branch; own boot 14.1s, no errors. Net diff = quest + #328 file deletions (40/40 strings moved verbatim) + synth 2392 name; GoblinCaveLadders untouched; merges clean with #373. Wiki re-check
  (osrs-mcp) and RSProx 3243/1694 at the source match. In game (fresh account, desktop) every claim of How it was tested reproduced. Previous FAIL items fixed. Recommended, not blocking: reword the re-dyeing
  ''not included'' note (the GD walkthrough describes re-dyeing; not LotG-only); objbox zoom 400. Left: DoD 5 (Witch''s Potion RSProx in its spec; GD''s is in sd25 docs ce5e5c8bb, NOT yet pushed to fork
  docs) and DoD 6 (#367 ff after the user''s OK; #368 later). Witch''s Potion not in this run.'
spec: goblin-diplomacy
---
## Task
Goblin Diplomacy 4f5e59627 and Witch's Potion 4dc1ac9c7 (the -829 branches): internal PASS; the upstream gate needs the RSProx comparison (search by NPC id / TEXT; nothing transcribed yet) and, for Goblin Diplomacy, the text fix (gate + in-game run + cheats; 'moved verbatim from #328'). PR texts. Then the user's OK to replace #367/#368 with a merge commit. Depends on the quest fixture merging upstream first (unstack-367-368).

## Definition of done
- [x] qa/gate-witchs-potion-goblin-diplomacy.md on the fork's docs branch with the verdict (PASS/FAIL) at the exact commit (Goblin Diplomacy: qa/goblin-diplomacy.md PASS at d77ac2b76, docs 238550879; Witch's Potion not yet)
- [x] independent accuracy re-check against the spec and an in-game run at that commit (GD only)
- [x] fork CI green at the exact commit (build-test 'CI first') (GD: CI 39353936705, Formatting 39353938933)
- [x] PR text reviewed against CONTRIBUTING (pr-text-review); scope one change (GD only)
- [x] RSProx comparison recorded in accuracy/goblin-diplomacy.md (Witch's Potion moved to gate-witchs-potion)
- [x] #367/#368 update moves to the approval step after the gate (Witch's Potion: gate-witchs-potion)

## Log
- 2026-10-20T01:42:41Z created (ready)
- 2026-10-20T03:38:57Z set branch=content/goblin-diplomacy-unstacked commit=d77ac2b76 spec=goblin-diplomacy
- 2026-10-20T03:38:57Z User 2026-10-20: #367 quest only. This run: Goblin Diplomacy only, at content/goblin-diplomacy-unstacked d77ac2b76 (fast-forwards #367 head 5398c6087; net diff = quest only, GoblinCaveLadders untouched since #373 has the ladder; fork CI + Formatting green). Text: re/agents/sd25/docs ce5e5c8bb pr-texts/goblin-diplomacy.{title,upstream.body,comment}. Witch's Potion stays for a later run. On PASS the orchestrator fast-forwards #367 (user OK given).
- 2026-10-20T03:38:57Z claimed by sq27 (instances desktop)
- 2026-10-20T04:09:40Z handover content/goblin-diplomacy-unstacked@d77ac2b76: Goblin Diplomacy upstream gate PASS at d77ac2b76 (record qa/goblin-diplomacy.md, fork docs 238550879). CI 39353936705 + Formatting 39353938933 green at the sha; fix_gameval_conflicts --check clean on main and branch; own boot 14.1s, no errors. Net diff = quest + #328 file deletions (40/40 strings moved verbatim) + synth 2392 name; GoblinCaveLadders untouched; merges clean with #373. Wiki re-check (osrs-mcp) and RSProx 3243/1694 at the source match. In game (fresh account, desktop) every claim of How it was tested reproduced. Previous FAIL items fixed. Recommended, not blocking: reword the re-dyeing 'not included' note (the GD walkthrough describes re-dyeing; not LotG-only); objbox zoom 400. Left: DoD 5 (Witch's Potion RSProx in its spec; GD's is in sd25 docs ce5e5c8bb, NOT yet pushed to fork docs) and DoD 6 (#367 ff after the user's OK; #368 later). Witch's Potion not in this run.
- 2026-10-20T04:10:33Z handover-check FAIL (1 items; see the check output)
- 2026-10-20T04:10:47Z Orchestrator: sd25's docs ce5e5c8bb published to fork docs as 1c6ee11bf (rebased onto sq27's record 238550879). Witch's Potion split into gate-witchs-potion. This task = Goblin Diplomacy only: gate PASS at d77ac2b76 -> gated; next the #367 fast-forward push + description + comment (upstream, user's OK).
- 2026-10-20T04:10:47Z set status=gated
- 2026-10-20T04:11:50Z set pr=https://github.com/OpenRune/OpenRune-Server/pull/367
- 2026-10-20T04:11:50Z User OK 2026-10-20: #367 fast-forwarded 5398c6087 -> d77ac2b76 (quest only), title 'feat: Goblin Diplomacy quest', description from fork docs 1c6ee11bf with #373 named for the ladder, comment https://github.com/OpenRune/OpenRune-Server/pull/367#issuecomment-6283189432. Still draft.
- 2026-10-20T04:11:50Z done
- 2026-10-20T04:27:25Z User OK 2026-10-20 ('If ready move from draft'): upstream CI green at d77ac2b76 (build & boot, spotless, gameval-conflicts); #367 marked ready for review.
