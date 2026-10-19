# Backlog (triage of pre-migration leftovers, 2026-10-18)

One row per deliverable. The orchestrator moves rows into the STATE docs' "Next tasks" when they start, and
deletes them here when done. Slugs are task names (docs/workflow.md "Changes for upstream").

## Server
| prio | task | role | gate | depends on | evidence |
|---|---|---|---|---|---|
| P0 | `integration-cutover`: spotless fixes on fix/71, content/looting-bag, fix/74, fix/76, fix/75, local/android; re-gate integration; deploy | sd23 → sq23 → orch | internal | – | fork docs qa/integration-cutover.md |
| P1 | `gate-acc-{cannon,genie,plank-sack,rune-pouch}`: the audited rewrites (acc/* in re/upstream-pr/or, not on the fork) replace fin/* in integration | orch pushes, server-qa | internal + upstream | cutover | qa/rune-pouch.md FAIL; scratchpad/acc/logs |
| P1 | `gate-quest-test-fixture`, `gate-op-overrides` + their PR texts (shared bases for every quest PR) | server-qa, server-dev | internal + upstream | – | UPSTREAM-PRS.md |
| P2 | `spec-genie` (no accuracy spec exists) | server-dev | – | – | openrune/upstream/accuracy/ |
| P2 | specs + internal gates: reconnect-login (Android > 60 s check), dragon-specials, pirates-treasure, looting-bag, fletching, fix/06-76 | server-dev, server-qa | internal | cutover | fork STATE.md task 4 |
| P2 | `gate-{heldu-dispatch-order,bankside-view-op,coal-bag-messages}` (QA stopped at wrap-up; heldu text needs a named herblore pair) | server-qa | internal + upstream | cutover | openrune/upstream/qa/ |
| P2 | `gate-{witchs-potion,goblin-diplomacy,vampyre-slayer}` + PR texts; #367/#368 still stack on #366's old f69c8ed4c | server-qa, server-dev | upstream | fixture | fin/* heads |
| P2 | `hunter-split-ship`: 7 fin/hunter-* branches (local only), linearise rumours, gate each, texts; integration still uses fin/hunter | server-dev, server-qa | internal + upstream | cutover | re/upstream-pr/dev-hunter |
| P2 | `overlap-doric` (#334), `overlap-review-{farming #231, specials #313, fletching #262}`; fix/06, 07, 69 vs #299 | server-dev | – | user decision | gh pr list |
| P3 | `gate-{fairy-rings,jewellery,diaries,grand-exchange}` + texts; GE needs #352 and an rsprot bump (caveat) | server-qa, server-dev | internal + upstream | – | UPSTREAM-PRS.md |
| P3 | `upstream-fixes-batch-4`: 10, 11, 22, 25, 31, 36, 37, 54, 56, 59/63 (held), 69, 70, 71, 76 | server-dev, server-qa | upstream | internal PASS each | fork fix/* |
| P3 | `cleanup-pirates-treasure` (squash the fixup, base on the fixture) | server-dev | internal | fixture | fork content/pirates-treasure |
| P3 | `docs-branch-migrate`: accuracy/*.md, qa/*.md, QA.md from openrune/upstream/ to the fork's docs | server-dev | – | – | – |
| P2 | rewrite fork STATE.md after the cutover | orch | – | cutover | – |

## Client
| prio | task | role | gate | depends on |
|---|---|---|---|---|
| P2 | `hub-exercise-after-cutover`: Clue Juggling Timers, Ground Items despawn (fix/76), Coal Bag (fix/75), Herbi AFK, Did I Compost, Cannon Highlighter, Fish Barrel, Clue Steps, Decimal Prices with a real "." | client-dev | merge | cutover deploy |
| P2 | `host-synthetic-world` (Current World NPE, gap 3) | client-dev | merge | – |
| P2 | `release-client-v1.0` (first release) | client-qa → orch | release | – |
| P3 | `host-menu-entry-removal` (No Examine, gap 2) | client-dev | merge | – |
| P3 | `hub-batch-next` (scratchpad/client38/ranking.json; keep it) | client-dev | merge | – |
| P3 | `poison-sprite-override` (26x26 override refused vs 15x14; plugin-host row overstates it) | client-dev | merge | – |
| P3 | `netqa-ge-digest`, `client-docs-trim` | client-qa, client-dev | – | – |

## Waiting on the user
1. #366: force-push content/sheep-shearer onto content/quests-sheep-romeo, retitle and new body (after its upstream gate); reply comment.
2. #354, #355, #356, #357: force-push the acc/* rewrites after their gates; move #354/#355 out of draft?
3. #367, #368: force-push fin/* after gates; Goblin's lookout ladder as its own fix/<slug>?
4. Doric (#334), farming (#231), specials (#313), fletching (#262): contribute to theirs, or keep ours local?
5. rsprot bump to 1.0.0-ALPHA-20261014: our PR/issue, or leave to maintainers?
6. Order of new upstream PRs (fixture and op-overrides first).
7. Client repo PR #1 (agentic-autofix): merge or close?
8. Old fork branches 241, content-drop-staging, memory, newcache, old-base, pets, production, toolbelt, worldtypes: delete?
9. Switch the fork's default branch to `integration` after the cutover.

## Orchestrator cleanup
- Close fork PRs #3, #6, #7, #8 and delete the merged fix/02, 03, 04, 27, 58, 60, 62 branches.
- Local refs in re/upstream-pr/or: new/*, pre-rebase/*, refs/qa/fix/73-75.
- Scratchpad (13 GB): sd22/, qa/, acc/ (after the acc gates), ctx/, b2/, e2e/, forkpr/, up/, up71/, amall/, amchk/, chk/, clean/, g29-g31/.
- Docker: the exited osrs-rsps-test container; old bootcheck/rollback/cs2test/sidecar tags; sd22-gradle, upstream-pr-gradle-acc* and anonymous volumes.
