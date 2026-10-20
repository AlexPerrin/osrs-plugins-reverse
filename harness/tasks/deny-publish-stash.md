---
id: deny-publish-stash
track: orch
title: 'deny_publish: git stash push false positive'
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
updated: '2026-10-20T01:42:43Z'
---
## Task
tools/hooks/deny_publish.py denied `git stash push` because it contains 'push'; match `git push` as a command, not the word (sd24, 2026-10-19). Fixed 2026-10-19.

## Definition of done
- [ ] `git stash push` allowed, `git push` still denied by the hook

## Log
- 2026-10-20T01:42:43Z created (done)
