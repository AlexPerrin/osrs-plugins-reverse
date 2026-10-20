---
id: server-tooling-to-fork
track: orch
title: Move the server build/deploy tooling to the fork (part 2 of the server split)
status: ready
prio: P3
role: orchestrator
owner: null
instance: []
branch: null
commit: null
blocked_by: []
unblocks:
- integration-phase-5
created: '2026-10-20T05:00:39Z'
updated: '2026-10-20T05:21:51Z'
---
## Task
Move the server build/deploy tooling to the fork (part 2 of the server split)

## Definition of done
- [ ] openrune/upstream/qa/ (qa.sh, gate-steps.sh, qa-members.sh), openrune/upstream/QA.md, openrune/scripts/ (add_varcs, fix_long_varps, fix_reftable, check_cache, openrune.sh), openrune/{Dockerfile,docker-compose.yml,docker/entrypoint.sh,switch.env,.dockerignore,README.md,LICENSE.OpenRune} and openrune/openrune.patch live on the fork's local/integration-tools branch (or a sibling local/ branch), with history kept
- [ ] every caller updated or given a thin wrapper: tools/buildq.sh, tools/harness/ship.sh, tools/repack.sh, tools/openrune/*.sh, tools/multiclient/*, tools/osrs-mcp/run.sh, .claude/skills/build-test, the server STATE notes, and the permission rules in .claude/settings.local.json
- [ ] proved with one gate (qa.sh gate on integration) and one deploy (rollback tag first, both instances free) from the new location
- [ ] the copies removed from the client repo; openrune/README.md points at the fork

## Log
- 2026-10-20T05:00:39Z created (ready)
- 2026-10-20T05:00:40Z User 2026-10-20: 'do part 1 now, part 2 as a task'. Part 1 done the same day: server history archived to fork docs (8510e763d, archive/ + server-handoff.md), 209 files removed from the client repo. openrune.patch and QA.md stayed for this part (openrune.sh init builds openrune/src from the patch; qa.sh copies .m2-rsprot from openrune/src).
- 2026-10-20T05:21:51Z unblocks integration-phase-5
