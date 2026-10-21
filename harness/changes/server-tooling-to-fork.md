---
slug: server-tooling-to-fork
track: harness
title: Move the server build/deploy tooling to the fork (part 2 of the server split)
prio: P3
role: harness-dev
stage: ready
hold: null
deps: []
branch: null
commit: null
local_only: false
owner: null
instances: []
created: '2026-10-20T05:00:39Z'
updated: '2026-10-21T00:25:19Z'
---
## Change
Move the server build/deploy tooling to the fork (part 2 of the server split)

## Definition of done
- [ ] openrune/upstream/qa/ (qa.sh, gate-steps.sh, qa-members.sh), openrune/upstream/QA.md, openrune/scripts/ (add_varcs, fix_long_varps, fix_reftable, check_cache, openrune.sh), openrune/{Dockerfile,docker-compose.yml,docker/entrypoint.sh,switch.env,.dockerignore,README.md,LICENSE.OpenRune} and openrune/openrune.patch live on the fork's local/integration-tools branch (or a sibling local/ branch), with history kept
- [ ] every caller updated or given a thin wrapper: tools/buildq.sh, tools/harness/ship.sh, tools/repack.sh, tools/openrune/*.sh, tools/multiclient/*, tools/osrs-mcp/run.sh, .claude/skills/build-test, the server STATE notes, and the permission rules in .claude/settings.local.json
- [ ] proved with one gate (qa.sh gate on integration) and one deploy (rollback tag first, both instances free) from the new location
- [ ] the copies removed from the client repo; openrune/README.md points at the fork

## Log
- 2026-10-21T00:25:19Z migrated from v2 tasks: server-tooling-to-fork
