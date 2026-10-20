---
id: server-parity-metric
track: server
title: 'Server progress metric: parity with the official OSRS server (tools/harness/server_parity.py)'
status: ready
prio: P2
role: server-dev
owner: null
instance: []
branch: null
commit: null
blocked_by: []
unblocks: []
created: '2026-10-20T06:10:54Z'
updated: '2026-10-20T06:10:54Z'
---
## Task
Server progress metric: parity with the official OSRS server (tools/harness/server_parity.py)

## Definition of done
- [ ] a written definition (docs/server-parity.md) of what counts toward an identical copy of the OSRS server, per area: quests, skills (incl. Sailing), minigames, bosses, random events, shops, NPC dialogue, and cache coverage (locs/NPCs/objs with ops, interfaces) that have a server handler; each area's denominator from the cache (osrs-mcp, gamevals) or the OSRS Wiki, its numerator from the integration tree (content modules, onOpLoc/onOpNpc/onOpObj bindings, quest list)
- [ ] tools/harness/server_parity.py --markdown prints a table per area (implemented / total, %), an overall weighted %, and the source of every number; it reads the integration tree, never the live server; runs in under 10 min
- [ ] upstream main vs integration shown side by side (how much of our parity is still only on the fork)
- [ ] docs/progress.md shows it (progress_report.py already calls the tool when present); a test for the counting on a fixture tree

## Log
- 2026-10-20T06:10:54Z created (ready)
- 2026-10-20T06:10:54Z User 2026-10-20: 'Server (Upstream's goal) is to emulate OSRS, server progress is the 100% mapping is an identical copy of the OSRS server.' Client progress is RuneLite compatibility on mobile (tools/progress.py). Accuracy of what's implemented is checked by the accuracy specs and gates; this metric is about coverage.
