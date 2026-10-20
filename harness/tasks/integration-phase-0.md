---
id: integration-phase-0
track: orch
title: 'Integration pipeline phase 0: live status, deny rules for deploy, LOCK_DIR override'
status: done
prio: P1
role: orchestrator
owner: null
instance: []
branch: null
commit: null
blocked_by: []
unblocks:
- integration-phase-1
created: '2026-10-20T05:21:50Z'
updated: '2026-10-20T05:30:13Z'
---
## Task
Integration pipeline phase 0: live status, deny rules for deploy, LOCK_DIR override

## Definition of done
- [ ] task.py: new status live; shipped removed from CLOSED; dependents unblock on live (and done); lint migrates deployed shipped tasks (fish barrel, stile) to live
- [ ] deny_publish.py denies agents deploy.sh, image.sh, orchestrate.py deploy/integrate/image and docker tag/compose on osrs-openrune
- [ ] device-lock.sh honours LOCK_DIR (default unchanged) for tests
- [ ] tests for all of the above pass; docs/harness.md updated

## Log
- 2026-10-20T05:21:50Z created (ready)
- 2026-10-20T05:21:51Z unblocks integration-phase-1
- 2026-10-20T05:22:26Z Bug 3 fixed by the orchestrator (2026-10-20): removed the three allow rules that permitted a --build deploy (compose up --build x2, openrune.sh deploy) from .claude/settings.local.json; a deploy.sh allow rule comes with phase 3.
- 2026-10-20T05:30:13Z Merged 714daec (50 tests). migrate-live run: fish barrel, stile -> live (deployed 04:44 UTC); client-docs-trim, host-menu-entry-removal, hub-batch-next -> live (client ship = master). clue-check-steps-tier stays shipped until its deploy. Orchestrator added ship.sh to the agent deny list (the script's header claimed it; no rule existed).
- 2026-10-20T05:30:13Z set status=done
