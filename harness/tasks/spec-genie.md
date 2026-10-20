---
id: spec-genie
track: server
title: Accuracy spec for the Genie random event
status: done
prio: P1
role: server-dev
owner: sd30
instance:
- desktop
branch: acc/genie
commit: 3ccb86bd85c6d96e1e067761d0bb72d64a7d47fd
pr: https://github.com/AlexPerrin/OpenRune-Server/pull/18
ci: https://github.com/AlexPerrin/OpenRune-Server/actions/runs/39375088118
evidence: /home/alex/osrs-plugins-reverse/re/evidence/spec-genie
blocked_by: []
unblocks:
- gate-acc-genie
created: '2026-10-20T01:42:41Z'
updated: '2026-10-20T05:42:30Z'
summary: 'accuracy/genie.md rewritten on fork docs 9eb87c281 (27 rules, RSProx 3056/1718 tick table, caveats C1-C7). New commit 3ccb86bd8 on acc/genie: an ignored Genie was deleted the same tick as ''Maybe
  later.'' (line never sent); now says it on the 10th call and leaves 3 ticks later (1718), + test (9/9). PR text updated for sd23''s fixes (sq24''s list) and fork PR #18 body. CI 39375088118 + Formatting
  39375089748 green at 3ccb86bd8. In-game not run (desktop MCP down); left to the gate.'
---
## Task
No accuracy spec exists for the Genie (acc/genie). Write accuracy/genie.md with accuracy-check (wiki + cache baseline, RSProx NPC search) so the gate-acc-genie companion has a spec to check against. Existing material: openrune/upstream/accuracy/.

## Definition of done
- [x] accuracy/genie.md on the fork's docs branch: spawn rules, dialogue, lamp XP choice, zones, with wiki sources and the RSProx comparison or 'no recording' noted

## Log
- 2026-10-20T01:42:41Z created (ready)
- 2026-10-20T04:59:23Z Paths: the old openrune/upstream/ material named above is archived on the fork's docs branch under archive/openrune-upstream/ (2026-10-20).
- 2026-10-20T05:16:27Z set prio=P1
- 2026-10-20T05:16:27Z User 2026-10-20: prioritize #355. acc/genie is now a44a3daa0 on the fork (orchestrator pushed sd23's commit; CI 39373976449 + Formatting 39373978444 started). The spec is the gate's baseline; include the RSProx comparison (3056/1718 per sq24's FAIL).
- 2026-10-20T05:16:27Z unblocks gate-acc-genie
- 2026-10-20T05:16:35Z claimed by sd30 (instances desktop)
- 2026-10-20T05:41:49Z handover acc/genie@3ccb86bd8: accuracy/genie.md rewritten on fork docs 9eb87c281 (27 rules, RSProx 3056/1718 tick table, caveats C1-C7). New commit 3ccb86bd8 on acc/genie: an ignored Genie was deleted the same tick as 'Maybe later.' (line never sent); now says it on the 10th call and leaves 3 ticks later (1718), + test (9/9). PR text updated for sd23's fixes (sq24's list) and fork PR #18 body. CI 39375088118 + Formatting 39375089748 green at 3ccb86bd8. In-game not run (desktop MCP down); left to the gate.
- 2026-10-20T05:42:20Z handover-check PASS
- 2026-10-20T05:42:30Z handover-check PASS; no ship (integration carries fin/genie). Spec docs 9eb87c281; acc/genie 3ccb86bd8 (sd30 fix: 'Maybe later.' on the 10th call, leaves 3 ticks later), CI 39375088118 + Formatting 39375089748 green; fork PR #18 body updated.
- 2026-10-20T05:42:30Z set status=done
