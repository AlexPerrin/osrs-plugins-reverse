---
id: repin-2
track: orch
title: Re-pin integration after the upstream merges
status: ready
prio: P1
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
Live: integration 5cc473397 since 2026-10-19 04:05 UTC. Upstream merged #312 Slayer, #297 Waterfall, our #348, Doric #334 (another contributor), Knight's Sword #333, Ides of Milk #315, PR template #369 (main 829b9477d). Drop fix/17 (superseded by #312), fix/18 (merged) and fin/doric (upstream's #334 replaces it: check saved Doric varp names resolve) from integration.txt; add fix/rune-pouch-accuracy and fix/edgeville-general-store-stock (internal PASS) and fix/55 back after it; check for conflicts with our branches; re-pin on the new main; gate; deploy (rollback tag first). Was 'repin-after-merges'.

## Definition of done
- [ ] integration.txt without fix/17, fix/18, fin/doric; with fix/rune-pouch-accuracy, fix/edgeville-general-store-stock and fix/55 after it; pinned on main 829b9477d
- [ ] saved Doric varp names resolve on the live DB
- [ ] boot-checked image gated (qa.sh gate) at the pinned commit
- [ ] deployed with a rollback tag while both devices are free; the user told

## Log
- 2026-10-20T01:42:43Z created (ready)
