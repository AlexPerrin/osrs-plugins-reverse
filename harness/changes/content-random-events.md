---
slug: content-random-events
track: server
title: 'Roadmap: content/random-events-<name> (one PR each)'
prio: P3
role: server-dev
stage: backlog
hold: null
deps: []
branch: null
commit: null
local_only: false
owner: null
instances: []
created: '2026-10-20T01:42:42Z'
updated: '2026-10-22T00:23:52Z'
---
## Change
Server roadmap (docs/rsps-coverage.md, 2026-10-19): content/random-events-<name> (one PR each). Missing upstream, no open upstream PR by anyone else. Reference: re/ref/ LostCity macro events/scripts/general (evidence for guesses only, never copied). Size S each. Notes: Drunken Dwarf, Mysterious Old Man, Mime, Maze, Strange Plant; reuse the Genie's spawner once it's merged. Check upstream again first, spec with accuracy-check (OSRS Wiki and cache are the baseline), one PR per change, no stacking.

## Definition of done
- [ ] upstream re-checked for an existing implementation or PR before starting
- [ ] quest/feature completable end to end on the test server as on the wiki
- [ ] accuracy/content-random-events.md on the fork's docs branch (wiki/cache baseline per rule, RSProx comparison recorded)
- [ ] fork PR open with green CI at the handed-over commit
- [ ] one change only, branch off upstream main (no stacking)

## Log
- 2026-10-21T00:25:19Z migrated from v2 tasks: content-random-events
- 2026-10-22T00:23:52Z RSProx recordings: 127 (rev 227, 89 matches; npc 322, npc 6750, npc 321), 555 (rev 227, 76 matches; npc 322, npc 6750), 4030 (rev 237, 56 matches; npc 322, npc 6750), 1098 (rev 237, 50 matches; npc 322, npc 6750, npc 321), 4031 (rev 237, 39 matches; npc 322, npc 6750), 1105 (rev 237, 36 matches; npc 322, npc 6750), 1102 (rev 237, 31 matches; npc 322, npc 6750), 1545 (rev 224, 30 matches; npc 322, npc 6750) [+168 more] | 176 recordings; keys: npc 322,6750,321,325; text Strange plant
