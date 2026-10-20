# Progress

Generated 2026-10-20 06:07 UTC by tools/harness/progress_report.py (task store, gh, tools/progress.py). Regenerate; don't edit by hand.

## Tasks by track

| Track | Done + live | In pipeline | Active | Ready | Blocked / waiting | Backlog | Completion (excl. backlog) |
|---|---|---|---|---|---|---|---|
| client | 13 | 0 | 0 | 3 | 3 | 0 | 68% (68% with pipeline) |
| server | 13 | 2 | 2 | 25 | 2 | 27 | 30% (34% with pipeline) |
| orch | 14 | 0 | 1 | 5 | 5 | 0 | 56% (56% with pipeline) |

## Finished or shipped on 2026-10-20 (UTC)

- `cleanup-docker` (orch, done): Cleanup: Docker leftovers (~16 GB, user runs it)
- `client-docs-trim` (client, live): Trim client docs
- `client-gpu-117hd-feasibility` (client, done): Stretch goal: GPU plugin and 117 HD on the Android app (feasibility spike)
- `clue-check-steps-tier` (server, shipped): Clue scroll Check steps shows the tier
- `docs-branch-migrate` (server, done): Migrate accuracy/qa docs to the fork's docs branch
- `edgeville-general-store-stock` (orch, done): Upstream #371: Edgeville general store stock
- `fish-barrel-check-and-destroy` (server, live): Fish barrel: Check message box and Destroy
- `fix-acc-cannon-text` (server, done): Cannon (#354): PR text and spec header wording for the upstream gate
- `fix-stile-approach` (server, live): Stile: approach from the outside tile
- `gate-goblin-village-lookout-ladder` (server, done): Gates: fix/goblin-village-lookout-ladder 475eefb76
- `gate-op-overrides` (server, done): Upstream gate: op overrides
- `gate-quest-test-fixture` (server, done): Upstream gate: quest test fixture
- `gate-witchs-potion-goblin-diplomacy` (server, done): Upstream gates: Witch's Potion and Goblin Diplomacy
- `ground-item-despawn-times` (server, shipped): Ground item despawn times (OSRS Drop rules)
- `host-menu-entry-removal` (client, live): Host gap 2: native menu entry removal (No Examine)
- `hub-batch-next` (client, live): Next hub plugin batch from the ranking
- `hub-fish-barrel-check` (client, live): Fish Barrel hub plugin: Check path
- `integration-phase-0` (orch, done): Integration pipeline phase 0: live status, deny rules for deploy, LOCK_DIR override
- `integration-phase-1` (orch, done): Integration pipeline phase 1: queue, lock, trains, commit pins (ship.sh enqueues; integration.lock on local/integration-tools)
- `pr-362-merge-main` (server, done): #362 fix/57: merge upstream main, unstack
- `repin-2` (orch, done): Re-pin integration after the upstream merges
- `review-348-362` (server, done): Maintainer review fixes for #348 and #362
- `sheep-shearer-review` (server, done): Sheep Shearer #366: review, RSProx comparison, caveat
- `spec-genie` (server, done): Accuracy spec for the Genie random event
- `unstack-367-368` (server, done): Unstack #367 Goblin Diplomacy and #368 Witch's Potion
- `wait-354-355-undraft` (orch, done): User: move #354/#355 out of draft after gates?
- `wait-client-pr-1-autofix` (orch, done): User: client repo PR #1 (agentic-autofix)
- `wait-fork-default-branch` (orch, done): User: switch the fork's default branch to integration
- `wait-rsprot-bump` (orch, done): User: rsprot bump to 1.0.0-ALPHA-20261014
- `wait-rsprox-template-na` (orch, done): User: RSProx item in the PR template, N/A on non-content PRs?
- `wait-upstream-pr-order` (orch, done): User: order of new upstream PRs

## Upstream

Upstream PRs (OpenRune/OpenRune-Server): 17 merged, 6 open for review, 3 draft, 2 closed (superseded). Merged share of non-superseded: 65%.

- Open for review: #362 fix: keep the classic/modern stone choice across the fu, #366 feat: Sheep Shearer quest, #367 feat: Goblin Diplomacy quest, #370 fix: answer "Don't be silly." when storing the rune pou, #372 fix: general store stock on the official inventories, a, #373 fix: climb the Goblin Village lookout ladder
- Draft: #354 feat: dwarf multicannon, #355 feat: Genie random event, #368 feat: Witch's Potion quest

## Client coverage (tools/progress.py)

| Metric | Value |
|---|---|
| Mobile packets mapped | 240/253 (94.9%) |
| Live packets mapped | 240/240 (100.0%) |
| Mobile packets mapped, verified | 239/253 (94.5%) |
| Mobile packets implemented | 233/253 (92.1%) |
| Server→client mapped | 147/151 (97.4%) |
| Client→server mapped | 93/102 (91.2%) |
| Zone sub-prots implemented | 15/15 (100.0%) |
| RuneLite API implemented | 661/866 (76.3%) |
| Plugins running on Android | 89/90 (98.9%) |
| Plugin Hub plugins running | 84/109 (77.1%) |

## Harness

Integration pipeline (docs/integration-pipeline.md): 2 of 6 phases done (33%).
- integration-phase-0: done
- integration-phase-1: done
- integration-phase-2: ready
- integration-phase-3: blocked
- integration-phase-4: blocked
- integration-phase-5: blocked
