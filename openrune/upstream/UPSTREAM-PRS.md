# Upstream content PRs (OpenRune/OpenRune-Server#353)

Prepared 2026-10-18 after the maintainer's answers in #353: all of it, one PR per row, ids renumbered against
upstream `main`, wiki data is fine. Treasure Trails is **on hold** (the maintainer is writing clue scrolls), so
it has no branch here. Pirate's Treasure (68) is not prepared either (not in the list).

Branches live in the local clone `re/upstream-pr/or` (git-ignored). Bases: the first ten rows (cannon to Vampyre
Slayer) are on upstream `main` `1be5dfb5f` (the quest PRs must share #366's commit `f69c8ed4c`); the standalone
branches not yet pushed when main moved (farming to Grand Exchange) were rebased onto `c2492318d` (after #344-#346,
#360, #363-#365 merged; no gamevals changed, so no id moved). Every branch merges cleanly with `c2492318d`.
The `NN-*.patch` files here are the source of truth: each content patch is `git format-patch` of its branch commit
(against that branch's base). Commits keep the patch authorship. The pre-renumbering branch tips are kept in the clone
as `refs/backup/pre-renumber/content/*`. The orchestrator pushes and opens the PRs; the agent that prepared this did not.

PR texts: `pr-texts/<slug>.title`, `pr-texts/<slug>.body` (fork PR, the full text) and `pr-texts/<slug>.upstream.body`
(the same plus a `FORK_PR_URL` line to fill in). Slug = branch name without `content/`.

## Order and status

Posting order is the table order. Tests per branch, each in its own `git worktree` with its own Gradle home volume and
`clean`, `--no-build-cache --no-configuration-cache` (see the note below): Docker `eclipse-temurin:21`, upstream's `./gradlew`,
`--cpus=4 --memory=4g`, `--max-workers=2`, `-Xmx2g` (cache tasks `-Xmx2600m`), one container at a time; a clean
`:or-cache:freshCache :or-cache:mergePluginGamevals` of main restored before each branch, then
`:or-cache:buildCache :or-cache:mergePluginGamevals`, `spotlessCheck`, `assemble`, `test`, `integration` as
separate runs, then `run` until "Server ready in". Nothing was checked in game from these branches.

Why no caches: upstream's `gradle.properties` enables the build cache and the configuration cache. Switching branches
in one shared working tree made `compileTestKotlin` come back FROM-CACHE with another branch's classes (tests of
quests not on the branch ran, and upstream tests failed with `NoSuchMethodError` on the old `NpcInteractions` /
`LocInteractions` constructors). The first seven PRs were re-verified this way after it was found.

| # | Branch | Patches | Commits (oldest first) | Tests | Fork PR | Upstream PR |
|---|---|---|---|---|---|---|
| 1 | `content/cannon` | 13 | `7b62ce683` | pending (no-cache run) | https://github.com/AlexPerrin/OpenRune-Server/pull/9 | https://github.com/OpenRune/OpenRune-Server/pull/354 |
| 2 | `content/genie` | 16 | `1d9b6fa49` | pending (no-cache run) | https://github.com/AlexPerrin/OpenRune-Server/pull/10 | https://github.com/OpenRune/OpenRune-Server/pull/355 |
| 3 | `content/rune-pouch` | 29 | `af57c4ad8` | pending (no-cache run) | https://github.com/AlexPerrin/OpenRune-Server/pull/11 | https://github.com/OpenRune/OpenRune-Server/pull/356 |
| 4 | `content/plank-sack` | 30 | `081f7af6c` | pending (no-cache run) | https://github.com/AlexPerrin/OpenRune-Server/pull/12 | https://github.com/OpenRune/OpenRune-Server/pull/357 |
| 5 | `content/quests-sheep-romeo` | 12 | `f69c8ed4c` | pending (no-cache run) | https://github.com/AlexPerrin/OpenRune-Server/pull/13 | https://github.com/OpenRune/OpenRune-Server/pull/366 |
| 6 | `content/witchs-potion` | 12, 65 | `f69c8ed4c` `18afab0a7` | pending (no-cache run) | https://github.com/AlexPerrin/OpenRune-Server/pull/15 | https://github.com/OpenRune/OpenRune-Server/pull/368 |
| 7 | `content/goblin-diplomacy` | 12, 66 | `f69c8ed4c` `5398c6087` | pending (no-cache run) | https://github.com/AlexPerrin/OpenRune-Server/pull/14 | https://github.com/OpenRune/OpenRune-Server/pull/367 |
| 8 | `content/op-overrides` | 20a | `3200f1758` | all pass; boot ready 7.742870071s | _(URL)_ | _(URL)_ |
| 9 | `content/doric` | 12, 20a, 64 | `f69c8ed4c` `1bc35aa3d` `e71437e69` | pending (no-cache run) | _(URL)_ | _(URL)_ |
| 10 | `content/vampyre-slayer` | 12, 67 | `f69c8ed4c` `b3f1f321d` | pending (no-cache run) | _(URL)_ | _(URL)_ |
| 11 | `content/farming` | 19 | `b5f8feebe` | pending (no-cache run) | _(URL)_ | _(URL)_ |
| 12 | `content/hunter` | 15, 20, 35, 39, 40, 41, 42 | `9b9276007` `6d8ff309e` `6a258ebe7` `e84d12543` `b89eda818` `087b60931` `b51135e3d` | pending (no-cache run) | _(URL)_ | _(URL)_ |
| 13 | `content/fairy-rings` | 26 | `ad64f7816` | pending (no-cache run) | _(URL)_ | _(URL)_ |
| 14 | `content/jewellery` | 44, 45 | `96b6393e5` `6ece8ea13` | pending (no-cache run) | _(URL)_ | _(URL)_ |
| 15 | `content/diaries` | 47, 49, 53 | `9ba3600fe` `033772a95` `2feb89602` | pending (no-cache run) | _(URL)_ | _(URL)_ |
| 16 | `content/grand-exchange` | 48, 50, 51, 52 | `c96e927ff` `32a33c527` `d748554aa` `437d07c98` | pending (no-cache run) | _(URL)_ | _(URL)_ |

Stacking: the four quest branches after Sheep Shearer / Romeo & Juliet carry its commit (same hash) for
`QuestTalkFixture`; Doric's Quest also carries the op-overrides change as its own commit on top of that one (same diff
as `content/op-overrides`, different hash, since `content/op-overrides` itself sits on main).
Once those are merged, the stacked PRs show only their own commit. Doric's Quest and Vampyre Slayer no longer
carry Treasure Trails (21) or the music tab (22): `NpcOpOverrides`/`LocOpOverrides` moved out of 21 into the new
small patch `20a-npc-loc-op-overrides` (its own PR), and Vampyre Slayer turned out to need only 12 (it was stacked on
64 and 66 for patch context only). Witch's Potion needs 12 (test fixture), as found before.

Merging: the 16 branches merge with each other without conflicts in table order, reverse order and a shuffled
order (checked), and `tools/scripts/fix_gameval_conflicts.py --check` (upstream's CI check) is clean on the merged
tree. The quest pack `gamevals.toml` is the one file several quest PRs touch; each quest's lines sit at a
different place in its `[gamevals.varp]` / `[gamevals.varbit]` sections (at least three unchanged lines apart), so
they merge in any order.

## Commands (per row, in this order)

```
# cannon
git -C re/upstream-pr/or push --force-with-lease fork content/cannon
gh pr create -R AlexPerrin/OpenRune-Server --base main --head content/cannon --title "$(cat openrune/upstream/pr-texts/cannon.title)" --body-file openrune/upstream/pr-texts/cannon.body
#   then put the fork PR URL into pr-texts/cannon.upstream.body (FORK_PR_URL)
gh pr create -R OpenRune/OpenRune-Server --base main --head AlexPerrin:content/cannon --title "$(cat openrune/upstream/pr-texts/cannon.title)" --body-file openrune/upstream/pr-texts/cannon.upstream.body

# genie
git -C re/upstream-pr/or push --force-with-lease fork content/genie
gh pr create -R AlexPerrin/OpenRune-Server --base main --head content/genie --title "$(cat openrune/upstream/pr-texts/genie.title)" --body-file openrune/upstream/pr-texts/genie.body
#   then put the fork PR URL into pr-texts/genie.upstream.body (FORK_PR_URL)
gh pr create -R OpenRune/OpenRune-Server --base main --head AlexPerrin:content/genie --title "$(cat openrune/upstream/pr-texts/genie.title)" --body-file openrune/upstream/pr-texts/genie.upstream.body

# rune-pouch
git -C re/upstream-pr/or push --force-with-lease fork content/rune-pouch
gh pr create -R AlexPerrin/OpenRune-Server --base main --head content/rune-pouch --title "$(cat openrune/upstream/pr-texts/rune-pouch.title)" --body-file openrune/upstream/pr-texts/rune-pouch.body
#   then put the fork PR URL into pr-texts/rune-pouch.upstream.body (FORK_PR_URL)
gh pr create -R OpenRune/OpenRune-Server --base main --head AlexPerrin:content/rune-pouch --title "$(cat openrune/upstream/pr-texts/rune-pouch.title)" --body-file openrune/upstream/pr-texts/rune-pouch.upstream.body

# plank-sack
git -C re/upstream-pr/or push --force-with-lease fork content/plank-sack
gh pr create -R AlexPerrin/OpenRune-Server --base main --head content/plank-sack --title "$(cat openrune/upstream/pr-texts/plank-sack.title)" --body-file openrune/upstream/pr-texts/plank-sack.body
#   then put the fork PR URL into pr-texts/plank-sack.upstream.body (FORK_PR_URL)
gh pr create -R OpenRune/OpenRune-Server --base main --head AlexPerrin:content/plank-sack --title "$(cat openrune/upstream/pr-texts/plank-sack.title)" --body-file openrune/upstream/pr-texts/plank-sack.upstream.body

# quests-sheep-romeo
git -C re/upstream-pr/or push --force-with-lease fork content/quests-sheep-romeo
gh pr create -R AlexPerrin/OpenRune-Server --base main --head content/quests-sheep-romeo --title "$(cat openrune/upstream/pr-texts/quests-sheep-romeo.title)" --body-file openrune/upstream/pr-texts/quests-sheep-romeo.body
#   then put the fork PR URL into pr-texts/quests-sheep-romeo.upstream.body (FORK_PR_URL)
gh pr create -R OpenRune/OpenRune-Server --base main --head AlexPerrin:content/quests-sheep-romeo --title "$(cat openrune/upstream/pr-texts/quests-sheep-romeo.title)" --body-file openrune/upstream/pr-texts/quests-sheep-romeo.upstream.body

# witchs-potion
git -C re/upstream-pr/or push --force-with-lease fork content/witchs-potion
gh pr create -R AlexPerrin/OpenRune-Server --base main --head content/witchs-potion --title "$(cat openrune/upstream/pr-texts/witchs-potion.title)" --body-file openrune/upstream/pr-texts/witchs-potion.body
#   then put the fork PR URL into pr-texts/witchs-potion.upstream.body (FORK_PR_URL)
gh pr create -R OpenRune/OpenRune-Server --base main --head AlexPerrin:content/witchs-potion --title "$(cat openrune/upstream/pr-texts/witchs-potion.title)" --body-file openrune/upstream/pr-texts/witchs-potion.upstream.body

# goblin-diplomacy
git -C re/upstream-pr/or push --force-with-lease fork content/goblin-diplomacy
gh pr create -R AlexPerrin/OpenRune-Server --base main --head content/goblin-diplomacy --title "$(cat openrune/upstream/pr-texts/goblin-diplomacy.title)" --body-file openrune/upstream/pr-texts/goblin-diplomacy.body
#   then put the fork PR URL into pr-texts/goblin-diplomacy.upstream.body (FORK_PR_URL)
gh pr create -R OpenRune/OpenRune-Server --base main --head AlexPerrin:content/goblin-diplomacy --title "$(cat openrune/upstream/pr-texts/goblin-diplomacy.title)" --body-file openrune/upstream/pr-texts/goblin-diplomacy.upstream.body

# op-overrides
git -C re/upstream-pr/or push --force-with-lease fork content/op-overrides
gh pr create -R AlexPerrin/OpenRune-Server --base main --head content/op-overrides --title "$(cat openrune/upstream/pr-texts/op-overrides.title)" --body-file openrune/upstream/pr-texts/op-overrides.body
#   then put the fork PR URL into pr-texts/op-overrides.upstream.body (FORK_PR_URL)
gh pr create -R OpenRune/OpenRune-Server --base main --head AlexPerrin:content/op-overrides --title "$(cat openrune/upstream/pr-texts/op-overrides.title)" --body-file openrune/upstream/pr-texts/op-overrides.upstream.body

# doric
git -C re/upstream-pr/or push --force-with-lease fork content/doric
gh pr create -R AlexPerrin/OpenRune-Server --base main --head content/doric --title "$(cat openrune/upstream/pr-texts/doric.title)" --body-file openrune/upstream/pr-texts/doric.body
#   then put the fork PR URL into pr-texts/doric.upstream.body (FORK_PR_URL)
gh pr create -R OpenRune/OpenRune-Server --base main --head AlexPerrin:content/doric --title "$(cat openrune/upstream/pr-texts/doric.title)" --body-file openrune/upstream/pr-texts/doric.upstream.body

# vampyre-slayer
git -C re/upstream-pr/or push --force-with-lease fork content/vampyre-slayer
gh pr create -R AlexPerrin/OpenRune-Server --base main --head content/vampyre-slayer --title "$(cat openrune/upstream/pr-texts/vampyre-slayer.title)" --body-file openrune/upstream/pr-texts/vampyre-slayer.body
#   then put the fork PR URL into pr-texts/vampyre-slayer.upstream.body (FORK_PR_URL)
gh pr create -R OpenRune/OpenRune-Server --base main --head AlexPerrin:content/vampyre-slayer --title "$(cat openrune/upstream/pr-texts/vampyre-slayer.title)" --body-file openrune/upstream/pr-texts/vampyre-slayer.upstream.body

# farming
git -C re/upstream-pr/or push --force-with-lease fork content/farming
gh pr create -R AlexPerrin/OpenRune-Server --base main --head content/farming --title "$(cat openrune/upstream/pr-texts/farming.title)" --body-file openrune/upstream/pr-texts/farming.body
#   then put the fork PR URL into pr-texts/farming.upstream.body (FORK_PR_URL)
gh pr create -R OpenRune/OpenRune-Server --base main --head AlexPerrin:content/farming --title "$(cat openrune/upstream/pr-texts/farming.title)" --body-file openrune/upstream/pr-texts/farming.upstream.body

# hunter
git -C re/upstream-pr/or push --force-with-lease fork content/hunter
gh pr create -R AlexPerrin/OpenRune-Server --base main --head content/hunter --title "$(cat openrune/upstream/pr-texts/hunter.title)" --body-file openrune/upstream/pr-texts/hunter.body
#   then put the fork PR URL into pr-texts/hunter.upstream.body (FORK_PR_URL)
gh pr create -R OpenRune/OpenRune-Server --base main --head AlexPerrin:content/hunter --title "$(cat openrune/upstream/pr-texts/hunter.title)" --body-file openrune/upstream/pr-texts/hunter.upstream.body

# fairy-rings
git -C re/upstream-pr/or push --force-with-lease fork content/fairy-rings
gh pr create -R AlexPerrin/OpenRune-Server --base main --head content/fairy-rings --title "$(cat openrune/upstream/pr-texts/fairy-rings.title)" --body-file openrune/upstream/pr-texts/fairy-rings.body
#   then put the fork PR URL into pr-texts/fairy-rings.upstream.body (FORK_PR_URL)
gh pr create -R OpenRune/OpenRune-Server --base main --head AlexPerrin:content/fairy-rings --title "$(cat openrune/upstream/pr-texts/fairy-rings.title)" --body-file openrune/upstream/pr-texts/fairy-rings.upstream.body

# jewellery
git -C re/upstream-pr/or push --force-with-lease fork content/jewellery
gh pr create -R AlexPerrin/OpenRune-Server --base main --head content/jewellery --title "$(cat openrune/upstream/pr-texts/jewellery.title)" --body-file openrune/upstream/pr-texts/jewellery.body
#   then put the fork PR URL into pr-texts/jewellery.upstream.body (FORK_PR_URL)
gh pr create -R OpenRune/OpenRune-Server --base main --head AlexPerrin:content/jewellery --title "$(cat openrune/upstream/pr-texts/jewellery.title)" --body-file openrune/upstream/pr-texts/jewellery.upstream.body

# diaries
git -C re/upstream-pr/or push --force-with-lease fork content/diaries
gh pr create -R AlexPerrin/OpenRune-Server --base main --head content/diaries --title "$(cat openrune/upstream/pr-texts/diaries.title)" --body-file openrune/upstream/pr-texts/diaries.body
#   then put the fork PR URL into pr-texts/diaries.upstream.body (FORK_PR_URL)
gh pr create -R OpenRune/OpenRune-Server --base main --head AlexPerrin:content/diaries --title "$(cat openrune/upstream/pr-texts/diaries.title)" --body-file openrune/upstream/pr-texts/diaries.upstream.body

# grand-exchange
git -C re/upstream-pr/or push --force-with-lease fork content/grand-exchange
gh pr create -R AlexPerrin/OpenRune-Server --base main --head content/grand-exchange --title "$(cat openrune/upstream/pr-texts/grand-exchange.title)" --body-file openrune/upstream/pr-texts/grand-exchange.body
#   then put the fork PR URL into pr-texts/grand-exchange.upstream.body (FORK_PR_URL)
gh pr create -R OpenRune/OpenRune-Server --base main --head AlexPerrin:content/grand-exchange --title "$(cat openrune/upstream/pr-texts/grand-exchange.title)" --body-file openrune/upstream/pr-texts/grand-exchange.upstream.body
```

## Id allocation

Rule: for each kind, the next free ids directly after main's highest id below our old range (so they continue
main's own blocks), skipping anything main uses, allocated row by row in the order above; within a row the old
relative order is kept. All ids are above `.data/gamevals-binary/max-ids.toml` and at most 65535 (upstream's
`fix_gameval_conflicts.py` range). Ids are matched by gameval name, so code and data that use names are
unchanged; no code used the numbers.

Main's neighbours: timers ... 400-403, 420, 430; varps 63700-63705, 63711 (quest pack), next used 64060;
varbits 63700-63707, 63711-63714, next used 64060; dbtables 55520-55537, 56000, next used 63510; dbrows
60000-60123 (collection log), next used 62000; enums 64440, next used 65400.

| Row | Kind | New ids | Old ids (our live server) | Count |
|---|---|---|---|---|
| cannon | timer | 431-432 | 440-441 | 2 |
| genie | timer | 433-434 | 450-451 | 2 |
| quests-sheep-romeo | varbit | 63715-63717 | 63740-63742 | 3 |
| quests-sheep-romeo | varp | 63712 | 63740 | 1 |
| witchs-potion | varbit | 63718 | 63744 | 1 |
| doric | varbit | 63719 | 63743 | 1 |
| vampyre-slayer | varbit | 63720-63721 | 63745-63746 | 2 |
| vampyre-slayer | varp | 63713 | 63745 | 1 |
| farming | dbrow | 60124-60151 | 60160-60187 | 28 |
| farming | dbtable | 56001 | 60002 | 1 |
| farming | timer | 435 | 460 | 1 |
| farming | varp | 63714-63761 | 63780-63827 | 48 |
| hunter | dbrow | 60152-60293 | 60124-60496 (with gaps) | 142 |
| hunter | dbtable | 56002-56012 | 60000-60019 (with gaps) | 11 |
| hunter | timer | 436 | 470 | 1 |
| hunter | varp | 63762-63783 | 63760-63903 (with gaps) | 22 |
| fairy-rings | varp | 63784-63785 | 63860-63861 | 2 |
| jewellery | dbrow | 60294-60350 | 60500-60556 | 57 |
| jewellery | dbtable | 56013-56014 | 60020-60021 | 2 |
| diaries | dbrow | 60351-60854 | 60560-61918 (with gaps) | 504 |
| diaries | dbtable | 56015-56016 | 60022-60023 | 2 |
| diaries | varp | 63786-63795 | 63160-63984 (with gaps) | 10 |
| grand-exchange | enum | 64441-64442 | 65380-65381 | 2 |
| grand-exchange | timer | 437 | 480 | 1 |
| grand-exchange | varp | 63796-64052 | 63000-64032 (with gaps) | 257 |

Rows with no server-only ids: rune pouch, plank sack, goblin diplomacy, op-overrides (client varbits or none).
The full name-by-name table is `id-allocation.json` next to this file.

Next free after this allocation: timer 438, varp 64053 (to 64059), varbit 63722, dbtable 56017, dbrow 60855, enum 64443.

Our own server tree (`../openrune.patch`, the live server) keeps the old ids, because saved player data uses them;
see the README.
