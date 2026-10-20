---
id: gate-op-overrides
track: server
title: 'Upstream gate: op overrides'
status: failed
prio: P1
role: server-qa
owner: sq26
instance:
- desktop
branch: null
commit: null
blocked_by: []
unblocks: []
created: '2026-10-20T01:42:41Z'
updated: '2026-10-20T03:18:49Z'
---
## Task
Gate the op-overrides branch (shared base for every quest PR) and its PR text (UPSTREAM-PRS.md). Goes upstream with the fixture first.

## Definition of done
- [ ] qa/gate-op-overrides.md on the fork's docs branch with the verdict (PASS/FAIL) at the exact commit
- [ ] independent accuracy re-check against the spec and an in-game run at that commit
- [ ] fork CI green at the exact commit (build-test 'CI first')
- [ ] PR text reviewed against CONTRIBUTING (pr-text-review); scope one change

## Log
- 2026-10-20T01:42:41Z created (ready)
- 2026-10-20T03:16:26Z claimed by sq26 (instances desktop)
- 2026-10-20T03:18:49Z FAIL at 46bc3ab31: branch not on current upstream main (c2492318d vs 829b9477d); upstream #334 gates Doric's anvils via DoricsQuest.mayUseAnvil so the API has no user/test upstream; PR text stale (claims base 1be5dfb5f, stacked on Doric, not on #369 template, Ap-path caveat missing); no fork CI at the commit. Record committed locally as 8c4a29bfa in re/agents/sq26/docs (qa/gate-op-overrides.md) but push to fork docs was DENIED by hook ('Only the orchestrator does pushes'); orchestrator please push. Desktop not taken (in-game not run: stopped at scope fail).
- 2026-10-20T03:18:49Z set status=failed
