---
id: docs-branch-migrate
track: server
title: Migrate accuracy/qa docs to the fork's docs branch
status: done
prio: P3
role: server-dev
owner: null
instance: []
branch: null
commit: null
blocked_by: []
unblocks: []
created: '2026-10-20T01:42:42Z'
updated: '2026-10-20T05:08:37Z'
---
## Task
Move accuracy/*.md, qa/*.md and QA.md from openrune/upstream/ to the fork's docs branch (git -C re/upstream-pr/or ... docs).

## Definition of done
- [x] accuracy/*.md and qa/*.md on fork docs (archive/openrune-upstream/, 8510e763d); QA.md moves with the QA tooling in server-tooling-to-fork
- [x] openrune/upstream/ copies removed (b7edafd); its README points at the fork's docs branch

## Log
- 2026-10-20T01:42:42Z created (ready)
- 2026-10-20T05:08:37Z Done by the server history archive (2026-10-20): accuracy/qa copies on fork docs archive/openrune-upstream (8510e763d), removed here (b7edafd). QA.md stays with qa.sh until server-tooling-to-fork.
- 2026-10-20T05:08:37Z set status=done
