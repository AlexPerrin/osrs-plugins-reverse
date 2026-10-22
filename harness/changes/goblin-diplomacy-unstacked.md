---
slug: goblin-diplomacy-unstacked
track: server
title: 'Unstack #367 Goblin Diplomacy and #368 Witch''s Potion'
prio: P2
role: server-dev
stage: checked
hold:
  kind: decision
  reason: 'content/goblin-diplomacy-unstacked conflicts with fin/goblin-diplomacy, which the live server runs (upstream #367 uses the unstacked branch): fold fin/goblin-diplomacy into content/goblin-diplomacy-unstacked
    (your OK: it changes the #367 head), then point integration at it'
  since: '2026-10-22T00:03:28Z'
deps: []
branch: content/goblin-diplomacy-unstacked
commit: d77ac2b76eefe47211f4d15138563c19ddf19bfd
local_only: false
owner: null
instances: []
ci: https://github.com/AlexPerrin/OpenRune-Server/actions/runs/39353936705
upstream_pr: 367
spec: goblin-diplomacy
summary: GD d77ac2b76 CI green; second branch content/witchs-potion-unstacked 23e1b7c5d (CI rerun 39349238661 queued). No fork PR yet; docs commit ce5e5c8bb + uncommitted text edits in re/agents/sd25/docs.
  See log.
live_commit: d77ac2b76eefe47211f4d15138563c19ddf19bfd
created: '2026-10-20T01:42:40Z'
updated: '2026-10-22T00:06:00Z'
---
## Change
#367 Goblin Diplomacy (draft since 2026-10-18, user) and #368 Witch's Potion are stacked on #366's old f69c8ed4c (old Sheep Shearer + R&J). Rebuild each off upstream main alone (the -829 branches 4f5e59627 / 4dc1ac9c7 are the candidates, internal PASS); any shared base (QuestTalkFixture) merges upstream first. Then replace #367/#368 with a non-force-push merge commit after the user's OK (sq24).

## Definition of done
- [x] content/goblin-diplomacy and content/witchs-potion each build off upstream main alone with no commit from #366
- [ ] QuestTalkFixture (or whatever shared base) merged upstream before either PR is updated
- [ ] #367 and #368 updated with a merge commit (no force-push) only after the user's OK
- [ ] accuracy/unstack-367-368.md on the fork's docs branch (wiki/cache baseline per rule, RSProx comparison recorded)
- [ ] fork PR open with green CI at the handed-over commit
- [x] one change only, branch off upstream main (no stacking)

## Log
- 2026-10-21T00:25:19Z migrated from v2 tasks: unstack-367-368, gate-witchs-potion-goblin-diplomacy
- 2026-10-22T00:00:51Z live -> checked: drift: integration pin is absent, not d77ac2b76 (next train)
- 2026-10-22T00:03:28Z hold decision: content/goblin-diplomacy-unstacked conflicts with fin/goblin-diplomacy, which the live server runs (upstream #367 uses the unstacked branch): fold fin/goblin-diplomacy into content/goblin-diplomacy-unstacked (your OK: it changes the #367 head), then point integration at it
- 2026-10-22T00:06:00Z dropped from train int-20261022-1 by the integrator (content/goblin-diplomacy-unstacked not in integration.txt)
