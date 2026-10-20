---
id: wait-fork-default-branch
track: orch
title: 'User: switch the fork''s default branch to integration'
status: done
prio: P3
role: orchestrator
owner: null
instance: []
branch: null
commit: null
blocked_by: []
unblocks: []
created: '2026-10-20T01:42:43Z'
updated: '2026-10-20T04:49:10Z'
---
## Task
Waiting item 9: switch the fork's default branch to `integration` after the cutover (cutover done 2026-10-19).

## Definition of done
- [ ] the user's decision recorded in the Log and acted on (or passed to the task it gates)

## Log
- 2026-10-20T01:42:43Z created (waiting-user)
- 2026-10-20T04:49:10Z User 2026-10-20: yes. AlexPerrin/OpenRune-Server default branch is now integration (gh repo edit). fork.sh pr-create still bases fork PRs on main explicitly.
- 2026-10-20T04:49:10Z set status=done
