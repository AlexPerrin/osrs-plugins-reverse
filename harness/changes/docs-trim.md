---
slug: docs-trim
track: client
title: Trim client docs
prio: P3
role: client-dev
stage: live
hold: null
deps: []
branch: client/docs-trim
commit: 82f263c59
local_only: false
owner: null
instances: []
evidence: /home/alex/osrs-plugins-reverse/re/evidence/client-docs-trim
summary: 'docs/client-state.md rewritten as current state only (202 -> 127 lines): releases/tooling, device install, host behaviour summary with plugin-host.md pointers, open gaps 1/3/5 + leftovers, gotchas.
  Its Recent state, install notes and resolved gaps 2/4/6/7/8 moved verbatim into a new client-handoff.md entry (gens 41-46 + host-menu-entry-removal). Diff in evidence/docs.diff. plugin-host.md and offsets.md
  left as they are (reference, not state). Docs only, no build or device.'
live_commit: 82f263c59
created: '2026-10-20T01:42:43Z'
updated: '2026-10-21T00:28:01Z'
---
## Change
Trim docs/client-state.md and companions: move history to docs/client-handoff.md, keep state current and short.

## Definition of done
- [x] docs/client-state.md reads as current state only; moved history kept in docs/client-handoff.md; no lost facts (diff reviewed)

## Log
- 2026-10-21T00:25:19Z migrated from v2 tasks: client-docs-trim
