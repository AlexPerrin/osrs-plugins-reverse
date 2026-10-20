---
id: repin-2
track: orch
title: Re-pin integration after the upstream merges
status: in-progress
prio: P1
role: orchestrator
owner: orch-repin
instance: []
branch: null
commit: null
blocked_by: []
unblocks: []
created: '2026-10-20T01:42:43Z'
updated: '2026-10-20T04:12:23Z'
---
## Task
Live: integration 5cc473397 since 2026-10-19 04:05 UTC. Upstream merged #312 Slayer, #297 Waterfall, our #348, Doric #334 (another contributor), Knight's Sword #333, Ides of Milk #315, PR template #369 (main 829b9477d). Drop fix/17 (superseded by #312), fix/18 (merged) and fin/doric (upstream's #334 replaces it: check saved Doric varp names resolve) from integration.txt; add fix/rune-pouch-accuracy and fix/edgeville-general-store-stock (internal PASS) and fix/55 back after it; check for conflicts with our branches; re-pin on the new main; gate; deploy (rollback tag first). Was 'repin-after-merges'.

## Definition of done
- [x] integration.txt without fix/17, fix/18, fin/doric; with fix/rune-pouch-accuracy, fix/edgeville-general-store-stock and fix/55 after it; pinned on main 829b9477d
- [x] saved Doric varp names resolve on the live DB
- [ ] boot-checked image gated (qa.sh gate) at the pinned commit
- [ ] deployed with a rollback tag while both devices are free; the user told

## Log
- 2026-10-20T01:42:43Z created (ready)
- 2026-10-20T03:29:14Z fin/op-overrides is no longer an upstream branch (user dropped it 2026-10-20) but local/treasure-trails uses LocOpOverrides/NpcOpOverrides. When re-pinning: keep it under 'Local only' (e.g. rename to local/op-overrides or fold into local/treasure-trails) and rebase onto main 829b9477d; fin/doric is being dropped anyway.
- 2026-10-20T03:30:58Z claimed by orch-repin
- 2026-10-20T03:30:58Z User 2026-10-20: keep our test server's OpenRune up to date with upstream main. fix/55 no longer comes back (#351 closed; its work is in #372, fix/edgeville-general-store-stock). Build and gate delegated to a fork of the orchestrator; deploy stays with the orchestrator when the devices are free.
- 2026-10-20T04:12:23Z orch-repin: integration a07ec2d8f pushed to fork (lease over 5cc473397); local/integration-tools 507866f6a; gate PASS at a07ec2d8f (test 844/0, integration 214/0, spotless, boot 'Server ready in 9.95s' clean); gameval check clean. Dropped fix/17, fix/18, fin/doric, fin/op-overrides; added fix/rune-pouch-accuracy, fix/edgeville-general-store-stock, fix/stile-approach 513144d3d, local/quest-test-fixture dfdd87590, local/op-overrides 46bc3ab31. 5 conflicts recorded; fin/hunter a4a74b5a9 herbiboar dbrow ids off #312's. All 563 live saved names resolve, no id changes; Doric varp layout identical. Image NOT built: copying the live login key into the build context was denied (credential materialization). Evidence re/evidence/repin-2/.
