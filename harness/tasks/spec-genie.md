---
id: spec-genie
track: server
title: Accuracy spec for the Genie random event
status: in-progress
prio: P1
role: server-dev
owner: sd30
instance:
- desktop
branch: null
commit: null
blocked_by: []
unblocks:
- gate-acc-genie
created: '2026-10-20T01:42:41Z'
updated: '2026-10-20T05:16:35Z'
---
## Task
No accuracy spec exists for the Genie (acc/genie). Write accuracy/genie.md with accuracy-check (wiki + cache baseline, RSProx NPC search) so the gate-acc-genie companion has a spec to check against. Existing material: openrune/upstream/accuracy/.

## Definition of done
- [ ] accuracy/genie.md on the fork's docs branch: spawn rules, dialogue, lamp XP choice, zones, with wiki sources and the RSProx comparison or 'no recording' noted

## Log
- 2026-10-20T01:42:41Z created (ready)
- 2026-10-20T04:59:23Z Paths: the old openrune/upstream/ material named above is archived on the fork's docs branch under archive/openrune-upstream/ (2026-10-20).
- 2026-10-20T05:16:27Z set prio=P1
- 2026-10-20T05:16:27Z User 2026-10-20: prioritize #355. acc/genie is now a44a3daa0 on the fork (orchestrator pushed sd23's commit; CI 39373976449 + Formatting 39373978444 started). The spec is the gate's baseline; include the RSProx comparison (3056/1718 per sq24's FAIL).
- 2026-10-20T05:16:27Z unblocks gate-acc-genie
- 2026-10-20T05:16:35Z claimed by sd30 (instances desktop)
