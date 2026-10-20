---
id: cleanup-docker
track: orch
title: 'Cleanup: Docker leftovers (~16 GB, user runs it)'
status: done
prio: P3
role: orchestrator
owner: null
instance: []
branch: null
commit: null
blocked_by: []
unblocks: []
created: '2026-10-20T01:42:44Z'
updated: '2026-10-20T04:51:56Z'
---
## Task
Docker: the exited osrs-rsps-test container; old bootcheck/rollback/cs2test/sidecar tags; sd22-gradle, upstream-pr-gradle-acc* and anonymous volumes (~16 GB). The command is for the user to run (the classifier blocks it for agents).

## Definition of done
- [ ] listed items gone; nothing else removed (checked before and after)

## Log
- 2026-10-20T01:42:44Z created (waiting-user)
- 2026-10-20T04:49:52Z User OK 2026-10-20: removed the exited osrs-rsps-test container, 16 old image tags (10 image layers deleted: server rollbacks back to ed9cd76 with their bootcheck tags, integration-5da8d3e, cs2test, sidecar urlfix/payfix/next/prepayfix) and 18 unused anonymous volumes. Kept: live 241 (integration-e5b18a8), rollback-5cc4733, sidecar 241/sd22-members, localauth, all named volumes (sd22-gradle and upstream-pr-gradle-acc* were already gone). Old agents' named gradle volumes (sd23*-sd26*, sq23-sq25) left in place.
- 2026-10-20T04:49:52Z set status=done
- 2026-10-20T04:51:56Z User 2026-10-20: also removed the 16 unused old agent Gradle volumes (gradle-sd23, -sd23b, -sd23c, sd23-dev, sd24-dev, sd24-gate x5, sd25-dev, sd26-dev, sd26-gate, sq23, sq24, sq25). Shared caches kept.
