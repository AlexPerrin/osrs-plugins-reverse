# Upstream content PRs (OpenRune/OpenRune-Server#353): branch record

State at the wrap-up of 2026-10-18 (fresh start ordered by the user). This file is the record of every `fin/*` and
`content/*` branch in the local clone `re/upstream-pr/or` (git-ignored). The `fin/*` refs are the current work;
`content/<x>` is the name a branch gets on the fork once it is green and has passed QA. Upstream main is
`c2492318d` (fetched at the wrap-up). Nothing here was pushed by the PR-prep agent; the orchestrator pushes.

Maintainer's answers in #353: all of it, one PR per row, ids renumbered against main, wiki data is fine; they
write clue scrolls themselves, so Treasure Trails is on hold. The user then asked for OSRS-accurate mechanics
(audits in `accuracy/<slug>.md`), independent PRs on shared infrastructure, all opened at once after QA
(`QA.md`).

## Gate

"Gate" = `scratchpad/test.sh <ref>`: a fresh `git clone --shared` of the branch, the clean `freshCache` of main
restored, then `clean`, `:or-cache:buildCache :or-cache:mergePluginGamevals`, `spotlessCheck`, `assemble`, `test`,
`integration` as separate Gradle runs, then `run` until "Server ready in". Docker `eclipse-temurin:21`,
`--cpus=4 --memory=4g`, `--max-workers=2`, `-Xmx2g`, `--no-build-cache --no-configuration-cache`,
`-Pkotlin.compiler.execution.strategy=in-process`, through `tools/buildq.sh`, one of two Gradle home volumes
(`upstream-pr-gradle-prs`, `-prs2`) per container. Why so strict: upstream's `gradle.properties` enables the build
and configuration caches, and branch switching in a shared tree gave FROM-CACHE test classes of other branches; two
containers on one Gradle home fail with "Timeout waiting to lock journal cache"; Gradle + the 2 GB Kotlin daemon +
a test JVM get OOM-killed (137) in 4 GB.

**No branch below has a complete gate on its final commit.** Every failure seen on the final commits was
infrastructure (OOM 137 in modules the branch doesn't touch, the journal-lock clash, killed at wrap-up); the
helpers' module-level runs in their own clones passed. Next step for each: run the full gate once, then QA.

## Branches

| Push as | Ref | Base | Commits (oldest first) | Patches | Gate on final commit | Accuracy fixes | Left |
|---|---|---|---|---|---|---|---|
| `content/quest-test-fixture` | `fin/quest-test-fixture` | c2492318d | `35900b277` | 12-quest-test-fixture | clean, cache, spotless, assemble, test pass; integration FAIL (OOM 137 in `:api:stats-plugin`, not touched); boot pass 8.2 s | n/a (test code) | integration re-run; QA; new PR (text not written) |
| `content/op-overrides` | `fin/op-overrides` | c2492318d | `46bc3ab31` | 20a | not run on this commit (runs hit the lock clash / cache FAIL); the same diff on 1be5dfb5f passed all steps (clean tree, 7.7 s boot) | y (Ap-path caveat in the message) | gate; QA; PR text: add the caveat |
| `content/quests-sheep-romeo` (keeps #366) | `fin/sheep-shearer` | c2492318d | `35900b277` `9c5213810` | 12a | not run on 9c5213810 (the earlier 36b9858e8: clean..assemble pass, test OOM 137); clone: SheepShearer 9/9 | y (D1-D5, coin-overflow refund) | gate; QA; retitle #366 and new body; force-push needs the user's OK |
| `content/romeo-juliet` | `fin/romeo-juliet` | c2492318d | `35900b277` `67dbb95e7` | 12b | not run; clone: R&J 14/14, ApothecaryPotions 6/6 | y (D6-D14, potions interface 556, cadava) | gate; QA; new PR; cutscene staging caveated |
| `content/witchs-potion` (#368) | `fin/witchs-potion` | c2492318d | `35900b277` `fddf71474` | 65 | clean, cache, spotless, assemble pass; test FAIL (journal lock); clone: WitchsPotion 9/9 | y (rat's tail kill hook in every mode for all wiki rats, mesboxes) | gate (boot checks the new QuestModule binding); QA; force-push #368 with the user's OK |
| `content/goblin-diplomacy` (#367) | `fin/goblin-diplomacy` | c2492318d | `35900b277` `c23023014` | 66 | clean, cache, spotless, assemble, test pass; integration, boot not run | y (D1-D4, all wiki dye colours) | integration + boot; QA; force-push #367 with the user's OK |
| `content/doric` | `fin/doric` | c2492318d | `35900b277` `8d05fb9de` (op-overrides, same patch-id as 46bc3ab31) `01539b85a` | 64 | not run (killed twice); clone: Doric 13/13 | y (D1-D3, whetstone, coin-overflow refund) | gate; QA; new PR (merge after op-overrides and the fixture) |
| `content/vampyre-slayer` | `fin/vampyre-slayer` | c2492318d | `35900b277` `db97ed310` | 67 | clean, cache, spotless, assemble, test pass (first run); integration, boot not run; clone: quest tests 190 pass (VS 12) | y (garlic per Mod Ash, +1 from a504b83, regenRate 5, warning, wording, return to coffin) | integration + boot; QA; in-client check of the fight |
| `content/farming` | `fin/farming` | c2492318d | `1788d0561` | 19 | clean, cache, spotless, assemble, test, boot (8.2 s) pass; integration FAIL (OOM 137 in `:api:stats-plugin`); clone: farming integration 11/11 | y (growth offset varp 64053, Inspect line, ripe countdown, wrong-patch message, caveats) | integration re-run; QA |
| `content/fairy-rings` | `fin/fairy-rings` | c2492318d | `140f9d393` | 26 | clean, cache, spotless pass; rest not run; clone: fairy-rings integration 7/7 | y (Lunar staff, Fairytale I/II gate via `QuestRequirements` like Martin in Draynor, elite L&D diary no-staff, caveats) | gate; QA |
| `content/jewellery` | `fin/jewellery` | c2492318d | `4def51e83` `eb28c879b` | 44, 45 | clean, cache, spotless, assemble, test pass; integration, boot not run; clone: jewellery integration 5/5 | y (Corp 2964,4382,2 per Shortest Path, Farming Guild inside at 45 Farming, charge-line prefixes) | integration + boot; QA |
| `content/diaries` | `fin/diaries` | c2492318d | `3f9410900` `a5643b737` `c42291076` | 47, 49, 53 | clean, cache, spotless, assemble pass; test, integration, boot not run; clone: diary integration 10/10, varrock test 22/22 | y (D1-D6 from the TSPS captures: text, layout, 331 px wrap, open/close scripts, task message, 3-tick tier box, kitten paths) | gate; QA; PR text update (no longer "wording unknown") |
| `content/grand-exchange` | `fin/grand-exchange` | c2492318d | `41bcba9a4` `e17641646` `b472c4de3` `a9a8d1ade` | 48, 50, 51, 52 | clean, cache, spotless, assemble, boot (8.0 s) pass; test + integration FAIL (OOM 137 in `:content:quest:test`, `:api:stats-plugin`); clone: GE integration 13/13 | y (bank shortfall + buy-max with bank coins, live limit windows kept, offline fills via varp 64054) | test + integration re-run; QA; in game it still needs #352 and the RSProt bump |
| `content/hunter-module` | `fin/hunter-module` | c2492318d | `28dbbf734` | 15a | not run | n/a (skeleton) | gate; QA; PR text (base for the hunter PRs, no content) |
| `content/hunter-implings` | `fin/hunter-implings` | c2492318d | `28dbbf734` `d302ecc3e` | 15 | clean pass, cache FAIL (lock clash); rest not run | y (crystal curve 8/141 + 28/161, MM2 gate, no clue while one of the tier is held) | gate; QA |
| `content/hunter-bird-houses` | `fin/hunter-bird-houses` | c2492318d | `28dbbf734` `4843ba93d` | 20 | not run | y (5-seed rule, meat to the ground, nest chance, early Empty, Reset rebuilds) | gate; QA |
| `content/hunter-traps` | `fin/hunter-traps` | c2492318d | `28dbbf734` `18e5ae439` | 35 | not run | y (per-creature curves, 7-tick respawn, stack-aware Check, jerboa moved in) | gate; QA |
| `content/hunter-herbiboar` | `fin/hunter-herbiboar` | c2492318d | `28dbbf734` `ed2c91db7` | 39 | not run | y (none needed beyond the split) | gate; QA |
| `content/hunter-butterflies` | `fin/hunter-butterflies` | c2492318d | `28dbbf734` `790b9f046` | 41 | not run | y (sunlight moth: no Sailing restore, jarless +5% run) | gate; QA |
| `content/hunter-rumours` | `fin/hunter-rumours` | c2492318d | traps `18e5ae439` + octopus merge `a629d0f93` (Herbiboar, butterflies) + `0edc4a533` | 40 (rumours + guild + butterfly rumours; 42 folded in) | not run | y (rumour quest gates, dialogue fixes, XP conflicts caveated) | gate; QA. The linear fixed tree (`re/upstream-pr/dev-hunter`, branch `work`) passed hunter integration 56/56 |
| (retired) | `fin/hunter` | c2492318d | 7 commits, tip `b51135e3d` | - | - | n (pre-audit) | superseded by the 7 hunter branches; integration may use it until those are green |
| (retired) | `fin/quests-sheep-romeo` = `content/quests-sheep-romeo` | 1be5dfb5f | `f69c8ed4c` | - | old cached run green | n | superseded by fixture + Sheep + R&J; delete after #366's head moves to the Sheep commit |
| accuracy agent | `content/cannon` = `fin/cannon` | 1be5dfb5f | `7b62ce683` | 13 | green (no-cache re-verify) | see `acc/cannon` below | owned by the accuracy agent |
| accuracy agent | `content/genie` = `fin/genie` | 1be5dfb5f | `1d9b6fa49` | 16 | green (cached run only) | see `acc/genie` | accuracy agent |
| accuracy agent | `content/rune-pouch` = `fin/rune-pouch` | 1be5dfb5f | `af57c4ad8` | 29 | green (no-cache re-verify) | see `acc/rune-pouch` | accuracy agent |
| accuracy agent | `content/plank-sack` = `fin/plank-sack` | 1be5dfb5f | `081f7af6c` | 30 | green (no-cache re-verify) | see `acc/plank-sack` | accuracy agent |
| (old, pre-renumber) | `content/doric`, `content/vampyre-slayer`, `content/farming`, `content/fairy-rings`, `content/jewellery`, `content/diaries`, `content/grand-exchange`, `content/hunter`, `content/goblin-diplomacy` `5398c6087`, `content/witchs-potion` `18afab0a7` | 1be5dfb5f | old commits (also on the fork, pushed by the orchestrator) | - | - | n | replace with the `fin/*` above (`--force-with-lease`) once green; goblin/witch are open as #367/#368 |
| on hold | `content/treasure-trails` | 1be5dfb5f | `544a89fd1` | 21-24, 28, 32-34 | old run green | n | not renumbered (its ids collide with the new allocation); keep out of integration |

Details per branch (what was fixed, sources, caveats) are in each commit message (CONTRIBUTING format) and in
the regenerated patch files. Accuracy items listed as not done are caveats in those messages.

## PR texts

`pr-texts/<slug>.title` / `.body` / `.upstream.body` were written for the pre-audit commits. They are **stale**
for every branch above except the four owned by the accuracy agent: the bodies need the new Change / Tests /
Data sources / Caveats from the commit messages, the gate results, the new stacking (`QuestTalkFixture` PR,
op-overrides PR, hunter module PR) and new texts for `quest-test-fixture`, `romeo-juliet`, `hunter-module`,
`hunter-implings`, `hunter-bird-houses`, `hunter-traps`, `hunter-herbiboar`, `hunter-butterflies` and
`hunter-rumours`. The helpers' PR-text bullets are summarised in the commit messages.

## Merge order constraints

All 24 current branches (the 20 above plus the four accuracy-agent `content/*`) merge with each other over
`c2492318d` without conflicts in forward, reverse and a shuffled order (checked 2026-10-18), and
`tools/scripts/fix_gameval_conflicts.py --check` is clean on the merged tree. Real dependencies:
- every quest PR (Sheep Shearer, R&J, Witch's Potion, Goblin Diplomacy, Doric, Vampyre Slayer) needs
  `quest-test-fixture` (tests only);
- Doric's Quest needs `op-overrides` (code);
- every hunter PR needs `hunter-module`; `hunter-rumours` needs `hunter-traps`, `hunter-herbiboar` and
  `hunter-butterflies` (it contains their commits through the octopus merge);
- everything else is independent.
The quest pack and hunter `gamevals.toml` lines of each PR sit at their own anchors, so these files don't
conflict in any order.

## Commands (per row, in this order; the orchestrator runs them after QA PASS)

```
git -C re/upstream-pr/or branch -f content/<x> fin/<x>
git -C re/upstream-pr/or push --force-with-lease fork content/<x>
gh pr create -R AlexPerrin/OpenRune-Server --base main --head content/<x> --title "$(cat openrune/upstream/pr-texts/<x>.title)" --body-file openrune/upstream/pr-texts/<x>.body
#   fill FORK_PR_URL in pr-texts/<x>.upstream.body
gh pr create -R OpenRune/OpenRune-Server --base main --head AlexPerrin:content/<x> --title "$(cat openrune/upstream/pr-texts/<x>.title)" --body-file openrune/upstream/pr-texts/<x>.upstream.body
```
Sheep Shearer goes to the existing `content/quests-sheep-romeo` name (keeps #366); Witch's Potion and Goblin
Diplomacy update #368/#367 (pushes to open upstream PRs need the user's confirmation).

Open PRs so far: cannon fork #9 / upstream #354 (draft), genie #10 / #355 (draft), rune pouch #11 / #356,
plank sack #12 / #357, sheep/romeo #13 / #366 (draft), goblin #14 / #367, witch #15 / #368 (draft).

## Id allocation

Rule: for each kind, the next free ids directly after main's highest id below our old range (so they continue
main's own blocks), skipping anything main uses, allocated row by row; within a row the old relative order is
kept. All ids are above `.data/gamevals-binary/max-ids.toml` and at most 65535 (upstream's
`fix_gameval_conflicts.py` range). Ids are matched by gameval name; no code uses the numbers.

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
| farming | varp | 64053 (`farming_growth_offset`, new in the audit fix) | - | 1 |
| grand-exchange | varp | 64054 (`ge_logout_minute`, new in the audit fix) | - | 1 |
| pirates-treasure (not prepared) | varp | 64083 (`pirates_treasure_state`) | 63747 | 1 |

The old rows "quests-sheep-romeo" now belong to Sheep Shearer (varp 63712, varbits 63715-63716) and Romeo & Juliet
(varbit 63717); "hunter" is spread over the hunter branches with the same values. Rows with no server-only ids:
rune pouch, plank sack, Goblin Diplomacy, op-overrides, the fixture. Reserved for the audit fixes but unused:
varbits 63722-63727, varps 64055-64059, 64065-64082, 64084-64085, dbrows 60855-60969, dbtables
56017-56018, timers 438-439. Name-by-name table: `id-allocation.json`.

Our own server tree (`../openrune.patch`, the live server) keeps the old ids, because saved player data uses them
(see the README); with the integration-branch migration the live server moves to these branches and their ids.

## Accuracy fixes (OSRS-accuracy agent, 2026-10-18)

Branches in `re/upstream-pr/or` as `acc/<slug>`, each one commit on `c2492318d` (main), amended from the
original patch commit (authorship kept); the `NN-*.patch` files here are regenerated from them. The audits
behind them are in `accuracy/<slug>.md`. None has passed the QA gate yet; none has been pushed.

| Branch | Patch | Commit | Gate status | Left to do |
|---|---|---|---|---|
| `acc/genie` | 16 | `7f7badcf2` | freshCache, buildCache, spotlessApply/spotlessCheck, assemble pass; `test` passed on the previous tree state and was rerunning at wrap-up; `integration` not yet passed (the earlier failures were a broken `.data` from a restored tarball, in upstream modules, not this module); boot not run | `integration` (expect `GenieRandomEventTest` 10), boot, QA; fill `RESULTS_PENDING` in `pr-texts/genie*.body` |
| `acc/cannon` | 13 | `490129985` | spotlessApply/spotlessCheck pass on an older tree; the last assemble failed on a `mapClock` name clash inside `ProtectedAccess` (fixed in this commit, not recompiled) | full build from `clean freshCache` (needs `game.yml` copied from `game.example.yml`), `DwarfMulticannonTest` (15) + `NulodionCannonSaleTest`, boot, QA; fill `RESULTS_PENDING` in `pr-texts/cannon*.body` |
| `acc/rune-pouch` | 29 | `ebc653612` | freshCache, buildCache, spotlessApply pass; spotlessCheck, assemble, test, integration not run | the rest of the build, QA (QA's items from `qa/rune-pouch.md` are folded into `pr-texts/rune-pouch*.body`); update the body's results line |
| `acc/plank-sack` | 30 | `0bca2214d` | not built | full build (spotlessApply first), QA; update the body's results line |

Changes per branch (details in the PR texts):
- genie: the wiki's spawn mechanics: real-time 1-2 h schedule in the persistent attribute
  `random_event_next_at`, 5-minute checks, the 10-70 min offline extension, misses in banks / GE / listed
  dungeons / instances and while transformed, delay while an interface or Make-X runs, one event NPC at a
  time, the genie leaving on transform, the lamp dropped for 3 h with a full inventory, the owner's pronoun
  in the other-player line. Timers unchanged (433, 434).
- cannon: break at 25 min (loc 14916, Repair), decay 10 min later, Nulodion's replacement, the cannon
  surviving logout/death (loc-duration timers), `mcannon_world`, the full accuracy roll vs heavy ranged
  defence, single-way attacker targeting, line of sight, target regions with the double-hit overlap, the
  Dwarf Cannon quest gate, Load X with the remembered amount, the captured Empty wording, captured set-up
  timing and auto-load. Timer `mcannon_decay` (432) dropped; `mcannon_fire` 431 kept.
- rune pouch: "Don't be silly." for the pouch itself; caveats per QA.
- plank sack: "Basic planks" in Check; caveats; corrected data-source line.

Build scripts and logs: the accuracy agent's scratchpad `acc/` (`pipeline2.sh`, `boot.sh`, `logs/`), via
`tools/buildq.sh`, one Gradle home volume per clone (`upstream-pr-gradle-acc-<slug>-or`).
