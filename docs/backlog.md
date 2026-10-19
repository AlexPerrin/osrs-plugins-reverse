# Backlog (triage of pre-migration leftovers, 2026-10-18)

One row per deliverable. The orchestrator moves rows into the STATE docs' "Next tasks" when they start, and
deletes them here when done. Slugs are task names (docs/workflow.md "Changes for upstream").

## Server
| prio | task | role | gate | depends on | evidence |
|---|---|---|---|---|---|
| P1 | `ground-item-despawn-times` (unblocks client: Clue Juggling Timers, Ground Items despawn): every player drop despawns after 200 ticks (constants.lootdrop_duration). OSRS Wiki "Drop": tradeables public after 60 s, despawn after a further 120 s; untradeables 180 s; clue scrolls 61 min. Repro: ::item 10180 1, drop, gone 115 s later | server-dev | internal + upstream | – | gen 44, scratchpad/g44/ |
| P1 | `fish-barrel-check-and-destroy` (unblocks client: Fish Barrel): Check sends GAMEMESSAGE lines instead of the chatbox message box (interface 193 comp 2: "The barrel is empty." / "The barrel contains: 3 x Raw shrimps" / "The barrel is full. It may be emptied at a bank."); Destroy (op 5) does nothing (FishBarrelScript dispatch `else -> {}`). A spawned barrel sits in the tester's slot 15 until Destroy works | server-dev | internal + upstream | – | gen 44 |
| P2 | `clue-check-steps-tier` (unblocks client: Clue Steps): "You have completed N steps on this <tier> clue scroll." (local/treasure-trails checkSteps lacks the tier) | server-dev | internal | – | gen 44 |
| P3 | `farming-compost` (unblocks client: Did I Compost): Compost on a patch gives "Nothing interesting happens."; expected "You treat the allotment with compost." (wiki Compost) | server-dev | internal + upstream | – | gen 44 |
| P1 | `repin-2` (live: integration 5cc473397 since 2026-10-19 04:05 UTC): upstream merged #312 Slayer and #297 Waterfall (main 731fd392a); drop fix/17 (superseded by #312) from integration.txt, add fix/rune-pouch-accuracy and fix/edgeville-general-store-stock (internal PASS) and fix/55 back after it, check for conflicts with our branches, re-pin on the new main, gate, deploy | orch → server-qa | internal | repin-after-merges | – |
| P1 | `review-348-362` (sd24, after the store fix): maintainer review. #348 fix/18: drop IdleHandlerTest, server config toggle for idle logout (default on). #362 fix/57: stone-before-fullscreen as a server-side varbit, not an attribute. New commits; QA gates; push to the PR branches only after the user OKs | sd24 → server-qa | internal + upstream | – | upstream reviews 2026-10-19 |
| P1 | `edgeville-general-store-stock` (sd24): rebuild upstream's truncated generalshop2.toml (size 0 → crash on Trade). #351 (fix/55) moved to draft 2026-10-18 and depends on it: it waits in the queue until this merges upstream; fix/55 is out of integration until then | sd24 → server-qa | internal + upstream | – | sq23 |
| P1 | `sheep-shearer-caveat`: upstream gate FAIL, PR text needs The Thing caveat (no run-away, generic shearing); then re-run the upstream gate and force-push onto #366 (confirm) | sd23 → server-qa | upstream | – | qa/sheep-shearer.md |
| P2 | `unstack-367-368`: #367 Goblin Diplomacy (draft since 2026-10-18, user) and #368 Witch's Potion are stacked on #366's old f69c8ed4c (old Sheep Shearer + R&J). Rebuild each off upstream main alone; any shared base (QuestTalkFixture) merges upstream first | server-dev | upstream | fixture merged | sq24 |
| P3 | `sheep-shearer-thing-runaway`: implement the penguin running away on shear (wiki) | server-dev | internal + upstream | – | – |
| P1 | `gate-acc-{cannon,genie}` (replace drafts #354/#355) + `fix/rune-pouch-accuracy` ("Don't be silly." on storing the pouch; merged-module gaps as caveats) (upstream merged the pre-audit #356/#357 on 2026-10-19; the audit delta becomes follow-up fixes off main): replace fin/* in integration | orch pushes, server-qa | internal + upstream | cutover | qa/rune-pouch.md FAIL; scratchpad/acc/logs |
| P1 | `gate-quest-test-fixture`, `gate-op-overrides` + their PR texts (shared bases for every quest PR) | server-qa, server-dev | internal + upstream | – | UPSTREAM-PRS.md |
| P2 | `spec-genie` (no accuracy spec exists) | server-dev | – | – | openrune/upstream/accuracy/ |
| P2 | specs + internal gates: reconnect-login (Android > 60 s check), dragon-specials, pirates-treasure, looting-bag, fletching, fix/06-76 | server-dev, server-qa | internal | cutover | fork STATE.md task 4 |
| P2 | `gate-{heldu-dispatch-order,bankside-view-op,coal-bag-messages}` (QA stopped at wrap-up; heldu text needs a named herblore pair) | server-qa | internal + upstream | cutover | openrune/upstream/qa/ |
| P2 | `gate-{witchs-potion,goblin-diplomacy,vampyre-slayer}` + PR texts; #367/#368 still stack on #366's old f69c8ed4c | server-qa, server-dev | upstream | fixture | fin/* heads |
| P2 | `hunter-split-ship`: 7 fin/hunter-* branches (local only), linearise rumours, gate each, texts; integration still uses fin/hunter | server-dev, server-qa | internal + upstream | cutover | re/upstream-pr/dev-hunter |
| P2 | `overlap-{doric #334, farming #231, specials #313, fletching #262}`: upstream as draft PRs only, each referencing the other PR (user, 2026-10-18); fix/06, 07, 69 local until #299 | server-dev, server-qa | upstream | gates | gh pr list |
| P3 | `gate-{fairy-rings,jewellery,diaries,grand-exchange}` + texts; GE needs #352 and an rsprot bump (caveat) | server-qa, server-dev | internal + upstream | – | UPSTREAM-PRS.md |
| P3 | plank sack gaps vs OSRS (merged #357), one fix each: Use in the bank fills from the inventory / empties into the bank (wiki, 27 Aug 2025), sawmill auto-fill (1 Nov 2023), death keeps contents. No tests-only PR (maintainer on #348) | server-dev | internal + upstream | – | sd23 |
| P3 | `fix/new-account-stone-arrangement`: upstream's account default sets varbit 4607 to 1 after the login frame is picked, so a new account starts on toplevel 161 with 4607 = 1 (sq25, qa/gameframe-fullscreen-map.md); check against OSRS's new-account frame | server-dev | internal + upstream | – | sq25 |
| P3 | `upstream-fixes-batch-4`: 10, 11, 22, 25, 31, 36, 37, 54, 56, 59/63 (held), 69, 70, 71, 76 | server-dev, server-qa | upstream | internal PASS each | fork fix/* |
| P3 | `cleanup-pirates-treasure` (squash the fixup, base on the fixture) | server-dev | internal | fixture | fork content/pirates-treasure |
| P3 | `docs-branch-migrate`: accuracy/*.md, qa/*.md, QA.md from openrune/upstream/ to the fork's docs | server-dev | – | – | – |
| P2 | rewrite fork STATE.md after the cutover | orch | – | cutover | – |

## Server roadmap (content; from docs/rsps-coverage.md, 2026-10-19)

Missing upstream, defined in a reference implementation, no open upstream PR by anyone else. Each: check upstream
again first, spec with accuracy-check (OSRS Wiki and cache are the baseline; the reference is evidence for guesses
only, never copied), one PR per change, no stacking. Reference paths are under re/ref/.

| # | task | reference | size | notes |
|---|---|---|---|---|
| 1 | `content/demon-slayer` (F2P) | LostCity quests/quest_demon | M | Delrith fight, Silverlight |
| 2 | `content/shield-of-arrav` (F2P) | LostCity quests/quest_blackarmgang (+ Phoenix gang) | M | two-player quest in OSRS (one gang each) |
| 3 | `content/black-knights-fortress` (F2P) | LostCity quests/quest_blackknight | S-M | fortress area module exists upstream |
| 4 | `content/dragon-slayer` (F2P) | LostCity quests/quest_dragon | L | Elvarg, Melzar's Maze, Crandor; largest |
| 5 | `content/druidic-ritual` (members) | LostCity quests/quest_druid | S | gates Herblore |
| 6 | `content/player-trading` (core) | LostCity interface_trade | M | trade request, two-screen confirm, both inventories |
| 7 | `content/tutorial-island` (Learning the Ropes) | Project Gielinor world/src/tutorial_*.rs (OSRS-era); LostCity tutorial/ is the 2004 version | L | OSRS 2021 rework is the target |
| 8 | `content/random-events-<name>` (Drunken Dwarf, Mysterious Old Man, Mime, Maze, Strange Plant; one PR each) | LostCity macro events/scripts/general | S each | reuse the Genie's spawner once it's merged |
| 9 | `content/kalphite-queen` (boss) | LostCity areas/area_kalphite/scripts/kalphite_queen.rs2 | M | drop table already upstream; two phases |
| 10 | `content/fishing-trawler` (minigame) | LostCity minigames/game_trawler | M-L | OSRS rewards and timings changed since 2004 |

## Client
| prio | task | role | gate | depends on |
|---|---|---|---|---|
| P2 | `release-client-v1.0` (first release) | client-qa → orch | release | – |
| P3 | offsets.md: Client vtable slot 6 0x421a20 is the draw on doCycle's thread (frame fn 0x86bdc8), per qa/client-world-entry-thread-safety.md | client-dev | – | – |
| P2 | `scene-item-despawn`: SceneObjects posts no ItemDespawned for ground items that leave the scene, so Ground Items double-counts after a teleport round trip (pre-existing; qa/client-hostfixes-gen45.md) | client-dev | merge | – |
| P3 | `rebuild-double-logged-in`: a native 30 arriving after the rebuild release tick could post LOGGED_IN twice (unguarded, never seen) | client-dev | merge | – |
| P3 | `host-menu-entry-removal` (No Examine, gap 2) | client-dev | merge | – |
| P3 | `hub-batch-next` (scratchpad/client38/ranking.json; keep it) | client-dev | merge | – |
| P3 | `poison-sprite-override` (26x26 override refused vs 15x14; plugin-host row overstates it) | client-dev | merge | – |
| P3 | `netqa-ge-digest`, `client-docs-trim` | client-qa, client-dev | – | – |
| P3 | `host-javax-sound-shim`: Drop Sounds throws `NoClassDefFoundError: javax/sound/sampled/Clip` at start (v0.1.0 gate); shim javax.sound.sampled onto the host audio path (as AudioPlayer), or drop the pin | client-dev | merge | – |
| P3 | `host-config-rsprofiles`: `ConfigManager.getRSProfiles()` missing; Time Tracking's farming completion check throws NoSuchMethodError (v0.1.0 gate) | client-dev | merge | – |

## Upstream queue

Fork PRs whose upstream gate passed. Each goes upstream only after the user approves it (user, 2026-10-18).

| fork PR | branch @ commit | upstream PR | gates | note |
|---|---|---|---|---|
| #16 | content/sheep-shearer @ 19aefd90a | #366 (draft) | internal + upstream PASS (fork docs qa/sheep-shearer.md) | on #366's branch; title/body updated 2026-10-18 (user approved), stays draft; no reply posted |
| #17 | fix/rune-pouch-accuracy @ bee197ba9 | none yet (follow-up to merged #356) | internal + upstream PASS (fork docs qa/rune-pouch-accuracy.md) | "Don't be silly." for storing a pouch in itself; one commit on upstream main 731fd392a |
| #19 | fix/edgeville-general-store-stock @ ca527418a | none yet; #351 (fix/55, draft) depends on it | internal + upstream PASS (fork docs qa/edgeville-general-store-stock.md) | rebuilds upstream's truncated generalshop2.toml; data only |

## Waiting on the user
Decided 2026-10-18: updated versions go onto the existing upstream PRs (#354-#357, #366-#368) after their gates,
each force-push still confirmed at the time; overlapping content (Doric #334, farming #231, specials #313, fletching
#262) goes upstream as draft PRs only, referencing the other PR. Old fork branches deleted, fork PRs #3/#6/#7/#8 closed.

1. #366: reply comment and ready — part of the upstream discussion.
2. Move #354/#355 out of draft after their gates?
3. Goblin's lookout ladder as its own fix/<slug>?
5. rsprot bump to 1.0.0-ALPHA-20261014: our PR/issue, or leave to maintainers?
6. Order of new upstream PRs (fixture and op-overrides first).
7. Client repo PR #1 (agentic-autofix): merge or close?
9. Switch the fork's default branch to `integration` after the cutover.

## Orchestrator cleanup
- Local copies of the merged fix/02, 03, 04, 27, 58, 60, 62 (deleted on the fork 2026-10-18).
- Local refs in re/upstream-pr/or: new/*, pre-rebase/*, refs/qa/fix/73-75.
- Scratchpad: qa/, acc/ (after the acc gates). sd22's unique refs are archived in re/upstream-pr/or as refs/archive/sd22/* (fin-hunter-rumours dcf92ca13 is the older linear rumours, useful for hunter-split-ship).
- Docker: the exited osrs-rsps-test container; old bootcheck/rollback/cs2test/sidecar tags; sd22-gradle, upstream-pr-gradle-acc* and anonymous volumes.
