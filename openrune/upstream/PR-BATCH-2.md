**Discussion issue opened 2026-10-18:** https://github.com/OpenRune/OpenRune-Server/issues/353. The 8 fix PRs are held until batch 1 (#343-#351) gets a first review.

# Upstream PR batch 2: the remaining small fixes, and a content discussion issue

Prepared 2026-10-18 against OpenRune-Server main
[`1be5dfb5f`](https://github.com/OpenRune/OpenRune-Server/commit/1be5dfb5f1a2efa314b93d6c1e2ae3dac6db41d1)
(unchanged since batch 1). Nothing has been pushed, forked or opened. Batch 1 (#343-#351, #352) is still open
with no review comments at the time of writing.

Same setup as batch 1: branches in the local clone `re/upstream-pr/or` (git-ignored), one commit per branch off
`origin/main`, author Alex Perrin <44126061+AlexPerrin@users.noreply.github.com>, `Co-Authored-By: Claude Opus 5.5
<noreply@anthropic.com>` trailer, commit subjects in CONTRIBUTING's `fix: ...` form. Each PR body (in
`batch-2-texts/NN.body`, title in `NN.title`) has Problem / Change / Tests / Caveats, a Data section where data is
involved, a release note and the CONTRIBUTING checklist. No wiki-derived data or text is in any batch 2 diff; the
sources cited are the rev 241 cache and BSD-licensed RuneLite / Plugin Hub / Quest Helper code.

To open one later: `git push <fork> fix/NN-...`, then `gh pr create -R OpenRune/OpenRune-Server --base main --head
AlexPerrin:fix/NN-... --title "$(cat batch-2-texts/NN.title)" --body-file batch-2-texts/NN.body`
(as `open-batch-1.sh` does). The discussion issue is `batch-2-texts/issue.title` / `issue.body`.
Suggestion: open the issue first, and the PRs once batch 1 has had a first review, so feedback on style carries over.

## Status

| # | Branch (commit) | Diff vs main | Old patch | spotless | tests | boot |
|---|---|---|---|---|---|---|
| 09 | `fix/09-skilling-message-wording` (e90d6bf4d) | 5 files, +7 / -7 | 09 minus its `SmeltingScript.kt` hunk (moved to 38) | pass | pass | ready 10.2 s |
| 23 | `fix/23-openable-drawers-chests` (e0534db4e) | 2 files, +42 / -18 | 23 without the Draynor wardrobe | pass | pass | ready 7.4 s |
| 27 | `fix/27-essence-pouch-check-wording` (2f96e1e45) | 1 file, +28 / -6 | 27, comment cut, 100+ guard | pass | pass | ready 7.3 s |
| 38 | `fix/38-varrock-platebody-smelting` (7b53cbb3a) | 1 file, +61 / -40 | 38 + 09's smelting line, message order fixed; applies on main alone | pass | pass | ready 7.4 s |
| 57 | `fix/57-gameframe-fullscreen-map` (a01ebb28b) | 2 files, +30 / -5 | 57 and 61 in one commit | pass | pass | ready 7.3 s |
| 58 | `fix/58-children-of-the-sun-report-stage` (3f0992970) | 3 files, +36 / -6 | 58, KDoc cut | pass | pass (`ChildrenOfTheSunInteractionTest` +2) | ready 8.1 s (second run, see below) |
| 60 | `fix/60-daddys-home-waxwood-cap` (d6a000c86) | 2 files, +26 / -2 | 60, wiki comment cut | pass | pass (`DaddysHomeInteractionTest` +1) | ready 7.4 s |
| 62 | `fix/62-item-command-cache-name-fallback` (60a6e067e) | 1 file, +9 / -1 | 62, comment cut | pass | pass | ready 8.6 s |
| 17 | - | - | **dropped**: superseded by open #312 | | | |
| 45 | - | - | **held**: needs 44 (jewellery teleports, in the discussion issue) | | | |
| 59, 63 | - | - | **held**: wiki transcript text | | | |
| 10, 11 | - | - | recommendation only, see below | | | |

All eight branches are one commit on `origin/main` (`1be5dfb5f`), apply independently of each other and of batch 1,
and pass `:or-cache:buildCache :or-cache:mergePluginGamevals`, `spotlessCheck` and `assemble test integration`.
58's first boot failed with `BindException: Address already in use` while another session's Gradle container was
running (environment, not the branch); it booted on a second run with nothing else running.

How it was tested: as batch 1 (Docker `eclipse-temurin:21`, upstream's `./gradlew`, `--cpus=4 --memory=4g`,
`--max-workers=2`, `-Xmx2g`, one container at a time). `:or-cache:freshCache :or-cache:mergePluginGamevals` once on
main (`JAVA_TOOL_OPTIONS=-Xmx2600m`), a copy of that cache kept aside, then per branch: restore the clean cache,
`:or-cache:buildCache :or-cache:mergePluginGamevals`, `spotlessCheck`, `assemble test integration`, and `run` until
"Server ready in" (every branch, since 23 and 57 change script startup bindings). For 23 the loc ops were read from
the built server cache with a throwaway test (not committed).

## What changed against the old patches

- **09**: the smelting line ("You retrieve a bar of ..." to spam) moved to 38, which rewrites the same lines, so 09
  and 38 no longer depend on each other. Commit message names the RuneLite plugins as the source.
- **23**: the Draynor Manor wardrobe is left out (its open/close fits `RimmingtonLocScript`'s wardrobe pattern, and its
  search line was a guess; the PR offers to add it). With it gone no new message text is left. The KDoc is gone
  (AGENTS.md: no comments on small classes), the data class is private and renamed `Drawers`.
- **27**: comment removed; numbers of 100+ fall back to digits instead of throwing.
- **38**: message order fixed. RuneLite's Item Charges plugin only takes two ring of forging charges when the platebody
  line comes *before* "You retrieve a bar of iron." (its `varrockPlatebodySmeltTwo` flag is set by the previous
  message), so the old patch's order (retrieve, then platebody) was wrong. Both bars are added in one `invAdd`. The
  code comment citing the wiki is gone.
- **57 + 61**: one branch and one PR (`fix/57-gameframe-fullscreen-map`); 61 only made sense on top of 57. KDoc cut,
  helper renamed `isFallbackCandidate` (it is also used for the intermediate-frame lookup, not just login).
- **58**: KDoc on the stage constant removed; branch named `fix/58-children-of-the-sun-report-stage`.
- **60**: the code comment citing the wiki is removed; the PR text mentions the wiki page as the basis for "no
  duplicates" (a fact, nothing copied). The "nothing interesting" line is the one main already uses, so no guess left.
- **62**: comment removed.

## Dropped or held

- **17 slayer streak and wording: dropped.** Open #312 (estesthebestes, "Slayer: kill credit, rewards, requirements
  and missing spawns") moves the streaks to `slayer_tasks_completed` (4069) / `slayer_wilderness_tasks_completed`
  (5617), migrates old saves, and rewrites the completion messages, which is 17 and more. What 17 has that #312
  doesn't is the gem check line ("...; only N more to go."), but current RuneLite's Slayer plugin reads the task
  from varps, not that line, and we have no non-wiki source for the wording, so it isn't worth a PR on its own.
- **45 jewellery charge keeps its slot: held.** It patches `content/travel/jewellery-teleports`, which only exists
  with 44. Goes with 44 if maintainers want the jewellery teleports (listed in the discussion issue).
- **59 Gertrude's Cat Fluffs: held.** The Stroke line ("Perhaps the cat wants something?") is from the wiki
  transcript, so it is wiki text. The other half (the shared Fluffs hide from 100 to 10 ticks) has no source at all
  (the OSRS delay is unknown). Can go in once maintainers answer the licence question; upstream's Gertrude's Cat
  (#292) was itself written from the wiki transcript, so they may well be fine with it.
- **63 Gertrude's Shilop line: held.** Same reason: the new exchange is the wiki transcript's dialogue.
- **22 music tab track list: not branched (not on the batch 2 list), but not covered upstream.** #336 ("Handle the
  music tab's play mode buttons") is the pin itself (`9a8b338a8`) and only does the Area/Shuffle/Single buttons; 22
  is the track list (Play, Unlock hint), still applies cleanly on main and is not in any open PR. It is a dependency
  of the Treasure Trails music steps. Its two messages ("You have not unlocked this piece of music yet!", "This track
  unlocks ...") have no non-wiki source; the hint itself comes from the cache. Could go with Treasure Trails or as
  its own small PR once the licence question is answered.
- Not in this batch and not asked about: 14 (dragon dagger/warhammer specials; open #313 registers both, e.g.
  `registerMelee("obj.dragon_dagger", puncture)`, so 14 is likely superseded), 25 (Ice Mountain music area: polygon from the wiki map), 31 (friend list update after add),
  36 (Giant Mole kill count varp), 37 (kitten growth wording: guesses and a wiki rule), 54 (Ernest stage 2). 31, 36
  and 54 look like batch 3 candidates (no wiki data); 14 should wait for #313 and probably be dropped.

## 10 and 11: recommendation (no branches)

- **10 login welcome message: don't send.** On main the login message comes from the realm's `login_message` column
  (`RealmConfigLoader`), so it is an operator setting, not code. The patch replaces any message starting with
  "Welcome to " by "Welcome to Old School RuneScape.", which is a policy hack upstream has no reason to take. For our
  server, set the realm's `login_message` to "Welcome to Old School RuneScape." instead and drop the patch. If
  wanted, one line in the discussion issue or a docs PR could mention that RuneLite's Chat History restores the chat
  only after that exact message.
- **11 dense clientscript ids: don't send now.** It only matters for clients that size their script table from the
  archive (the official Android client crashes; the desktop client doesn't), i.e. our Android support, which stays
  ours. The guard alone would fail upstream's build, so it can't be sent without the renumbering, and upstream adds
  custom scripts in other packs too. If the maintainers ever ask about non-desktop clients, it could be a short issue
  ("custom clientscript ids far above the stock maximum crash clients that size the table from the archive") with the
  patch offered.

## Discussion issue (content)

Target: OpenRune/OpenRune-Server issues. Files: `batch-2-texts/issue.title`, `batch-2-texts/issue.body`.

**Title:** Larger content we could contribute: which parts do you want, and how should we split them?

**Body:**

We've been running RuneLite and Plugin Hub plugins against an OpenRune server, and along the way built some larger content on OpenRune's own frameworks (quest manager, packs, dbtables, the test harness). The small fixes went up as #343-#351. CONTRIBUTING asks to discuss larger changes first, so this lists the rest before any PR is opened. Nothing below is open as a PR.

All of it is Kotlin in OpenRune's module layout, driven by cache data where the cache has it (e.g. the `cluehelper_*` and `fairyring` dbtables), and uses the client's own varps/varbits so RuneLite plugins (Quest Helper, Time Tracking, Clue Scroll, Herbiboar, Ess Pouch...) read the state. Most of it has integration tests. Sizes are from our patches against `9a8b338a8`, so they would change a bit on current main.

**Quests**

| Content | Size | Notes |
|---|---|---|
| Sheep Shearer | ~380 lines with its test; shares one 8-file / +1,293 patch and a quest test fixture (+164) with Romeo & Juliet | Quest varp 179 as Quest Helper reads it |
| Romeo & Juliet | ~750 lines with its test (same patch) | Overlaps #299, which is open. Happy to drop ours, or compare notes on #299 |
| Doric's Quest | 5 files, +486 | Overlaps #334, which is open. Same as above |
| Witch's Potion | 6 files, +315 / -7 | The rat's tail uses the drop-table quest gate |
| Goblin Diplomacy | 6 files, +878 / -152 | Replaces `GoblinGenerals.kt` / `Grubfoot.kt` from #328 with the quest's varbit-driven versions |
| Vampyre Slayer | 7 files, +824 / -22 | Count Draynor's coffin, garlic, stake; the Blue Moon Inn bartender |

**Activities and skills**

| Content | Size | Notes |
|---|---|---|
| Treasure Trails, beginner to hard | 6 patches, ~4,300 lines | Steps from the cache's `cluehelper_*` dbtables (texts, targets, emotes, STASH units), puzzle boxes, hard-clue foes. Adds two small generic hooks in `api/player` (npc/loc op overrides) so content can claim an op for one player. Music steps need the music tab's track list to play tracks (a small separate fix we also have) |
| Farming | 9 files, +1,084 | Allotment, flower and herb patches at six farms, with the `farming_transmit` values Time Tracking reads |
| Bird houses | 7 files, +561 | Fossil Island; needs the hunter module below |
| Hunter: implings | 12 files, +1,048 | New `hunter` module + pack; jar loot (see the data question) |
| Hunter: traps, butterfly netting | 2 patches, ~13 files, ~1,070 lines | |
| Hunter: Herbiboar | 8 files, +789 | The trail varbits RuneLite's Herbiboar plugin reads |
| Hunter: Hunters' Rumours, guild dialogues | 2 patches, ~20 files, ~1,190 lines | |
| Fairy rings | 7 files, +265 | `fairyring` dbtable, dial and travel log, Zanaris |

**Systems and items**

| Content | Size | Notes |
|---|---|---|
| Grand Exchange | 4 patches: core +1,317, partial fills and buy limits +4,725 (mostly a generated limits table), trade history +1,108, tax exemptions +80 | Needs the long-varp compiler fix (#352) and the rsprot bump to `1.0.0-ALPHA-20261014` (stock market slot reset) |
| Achievement diaries | 3 patches: journals +385, all areas + progress +1,315, task hooks +275 | Journals for all 12 areas; progress for Lumbridge & Draynor and Varrock tasks via one-line hooks in existing content |
| Jewellery teleports | 2 patches, +451 | Games necklace, ring of dueling, glory, skills necklace, combat bracelet: Rub menu, worn ops, charges |
| Dwarf multicannon | 4 files, +642 | |
| Genie random event | 4 files, +251 | |
| Rune pouch | 4 files, +408 | Store/withdraw/reorder on the client-visible `rune_pouch_*` varbits; no loadouts |
| Plank sack | 6 files, +249 | Fill/Check/Empty and a storage hook for building with planks from the sack |

**Questions**

1. Which of these do you want upstream, and are any already being worked on by someone else?
2. How would you like them split? Our default would be one PR per row (e.g. Grand Exchange core, then fills/limits, then history), each building and passing `assemble test integration` on its own.
3. Ids: our server-only dbtables, varps and timers use ranges we picked (dbtables from 60000, varps from 63740, timers from 440). Is there a range you'd like new content to use, or should we renumber against what's on main?
4. Licence of wiki-derived data. Some pieces use data or text from the OSRS Wiki, which is CC BY-NC-SA 3.0 (OpenRune is ISC):
   - Grand Exchange buy limits (generated from the wiki's `Module:GELimits/data.json`, 4,186 items) and the tax-exempt item list;
   - impling jar loot tables, the beginner clue casket table, Hunters' Rumours rare-part rates, butterfly netting success charts, bird house nest chances;
   - achievement diary task lists, some quest and hunter guild dialogue transcripts, jewellery teleport messages.

   We'd like to know how you handle that before sending any of it. We can keep it out and send only the parts that come from the cache, attribute it in separate data files, or wait for another source if you have one (we have no live captures). `tools/wiki-dumping` suggests you use wiki data already, so maybe there is a policy we missed.

## Fork PRs (2026-10-18)

Pushed to the fork and opened as PRs within it (base AlexPerrin:main); upstream PRs follow, each confirmed.

| Patch | Branch | Fork PR | Upstream PR |
|---|---|---|---|
| 09 | fix/09-skilling-message-wording | AlexPerrin/OpenRune-Server#1 | OpenRune/OpenRune-Server#358 |
| 23 | fix/23-openable-drawers-chests | AlexPerrin/OpenRune-Server#2 | OpenRune/OpenRune-Server#359 |
| 27 | fix/27-essence-pouch-check-wording | AlexPerrin/OpenRune-Server#3 | OpenRune/OpenRune-Server#360 (merged 2026-10-18) |
| 38 | fix/38-varrock-platebody-smelting | AlexPerrin/OpenRune-Server#4 | OpenRune/OpenRune-Server#361 |
| 57 | fix/57-gameframe-fullscreen-map | AlexPerrin/OpenRune-Server#5 | OpenRune/OpenRune-Server#362 |
| 58 | fix/58-children-of-the-sun-report-stage | AlexPerrin/OpenRune-Server#6 | OpenRune/OpenRune-Server#363 (merged 2026-10-18) |
| 60 | fix/60-daddys-home-waxwood-cap | AlexPerrin/OpenRune-Server#7 | OpenRune/OpenRune-Server#364 (merged 2026-10-18) |
| 62 | fix/62-item-command-cache-name-fallback | AlexPerrin/OpenRune-Server#8 | OpenRune/OpenRune-Server#365 (merged 2026-10-18) |

The 16 `content/*` branches (pre-renumbering) are pushed too, without PRs; the renumbered branches replace them.
