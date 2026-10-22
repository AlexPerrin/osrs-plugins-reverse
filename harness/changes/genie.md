---
slug: genie
track: server
title: Accuracy spec for the Genie random event
prio: P1
role: server-dev
stage: checked
hold:
  kind: decision
  reason: 'branch acc/genie is not one-change-one-branch (BR-C1): fold into content/genie (changes the upstream PR head: your OK per PR), then point integration at it; then: Genie (#355): leave on the next
    call tick after Talk-to; lamp XP equal to the message (fix-genie-gate-findings): Genie (#355): leave on the next call tick after Talk-to; lamp XP equal to the message'
  since: '2026-10-21T00:25:19Z'
deps: []
branch: acc/genie
commit: 3ccb86bd85c6d96e1e067761d0bb72d64a7d47fd
local_only: false
owner: null
instances: []
evidence: /home/alex/osrs-plugins-reverse/re/evidence/spec-genie
pr: https://github.com/AlexPerrin/OpenRune-Server/pull/18
ci: https://github.com/AlexPerrin/OpenRune-Server/actions/runs/39375088118
summary: 'accuracy/genie.md rewritten on fork docs 9eb87c281 (27 rules, RSProx 3056/1718 tick table, caveats C1-C7). New commit 3ccb86bd8 on acc/genie: an ignored Genie was deleted the same tick as ''Maybe
  later.'' (line never sent); now says it on the 10th call and leaves 3 ticks later (1718), + test (9/9). PR text updated for sd23''s fixes (sq24''s list) and fork PR #18 body. CI 39375088118 + Formatting
  39375089748 green at 3ccb86bd8. In-game not run (desktop MCP down); left to the gate.'
live_commit: 3ccb86bd85c6d96e1e067761d0bb72d64a7d47fd
created: '2026-10-20T01:42:41Z'
updated: '2026-10-22T00:00:51Z'
---
## Change
No accuracy spec exists for the Genie (acc/genie). Write accuracy/genie.md with accuracy-check (wiki + cache baseline, RSProx NPC search) so the gate-acc-genie companion has a spec to check against. Existing material: openrune/upstream/accuracy/.

## Definition of done
- [x] accuracy/genie.md on the fork's docs branch: spawn rules, dialogue, lamp XP choice, zones, with wiki sources and the RSProx comparison or 'no recording' noted

## Log
- 2026-10-21T00:25:19Z migrated from v2 tasks: spec-genie, fix-genie-gate-findings, gate-acc-genie
- 2026-10-21T00:25:19Z hold decision: branch acc/genie is not one-change-one-branch (BR-C1): fold into content/genie (changes the upstream PR head: your OK per PR), then point integration at it; then: Genie (#355): leave on the next call tick after Talk-to; lamp XP equal to the message (fix-genie-gate-findings): Genie (#355): leave on the next call tick after Talk-to; lamp XP equal to the message
- 2026-10-22T00:00:51Z live -> checked: drift: integration pin is absent, not 3ccb86bd8 (next train)
