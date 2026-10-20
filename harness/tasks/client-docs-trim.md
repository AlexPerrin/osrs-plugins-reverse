---
id: client-docs-trim
track: client
title: Trim client docs
status: live
prio: P3
role: client-dev
owner: null
instance:
- device
branch: client/docs-trim
commit: 82f263c59
evidence: /home/alex/osrs-plugins-reverse/re/evidence/client-docs-trim
blocked_by: []
unblocks: []
created: '2026-10-20T01:42:43Z'
updated: '2026-10-20T05:29:22Z'
summary: 'docs/client-state.md rewritten as current state only (202 -> 127 lines): releases/tooling, device install, host behaviour summary with plugin-host.md pointers, open gaps 1/3/5 + leftovers, gotchas.
  Its Recent state, install notes and resolved gaps 2/4/6/7/8 moved verbatim into a new client-handoff.md entry (gens 41-46 + host-menu-entry-removal). Diff in evidence/docs.diff. plugin-host.md and offsets.md
  left as they are (reference, not state). Docs only, no build or device.'
---
## Task
Trim docs/client-state.md and companions: move history to docs/client-handoff.md, keep state current and short.

## Definition of done
- [x] docs/client-state.md reads as current state only; moved history kept in docs/client-handoff.md; no lost facts (diff reviewed)

## Log
- 2026-10-20T01:42:43Z created (ready)
- 2026-10-20T03:16:26Z claimed by cd48 (instances device)
- 2026-10-20T03:18:53Z handover client/docs-trim@82f263c59: docs/client-state.md rewritten as current state only (202 -> 127 lines): releases/tooling, device install, host behaviour summary with plugin-host.md pointers, open gaps 1/3/5 + leftovers, gotchas. Its Recent state, install notes and resolved gaps 2/4/6/7/8 moved verbatim into a new client-handoff.md entry (gens 41-46 + host-menu-entry-removal). Diff in evidence/docs.diff. plugin-host.md and offsets.md left as they are (reference, not state). Docs only, no build or device.
- 2026-10-20T03:19:09Z handover-check FAIL (2 items; see the check output)
- 2026-10-20T03:19:09Z set status=ready owner=
- 2026-10-20T03:19:09Z back to ready after the hand-over check FAIL; the next dev fixes exactly those items
- 2026-10-20T03:27:48Z set status=shipped branch=client/docs-trim commit=82f263c59
- 2026-10-20T03:27:48Z Merged to master by hand at the user's request (bffc7a4): docs only, the hand-over check's repack log and device evidence don't apply to a markdown change.
- 2026-10-20T05:29:22Z migrate-live: shipped -> live (client ship = merged into master)
