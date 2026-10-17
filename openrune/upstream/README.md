# Patches for upstream OpenRune-Server

One `git format-patch` file per fix, each against OpenRune-Server
[`9a8b338a8`](https://github.com/OpenRune/OpenRune-Server/commit/9a8b338a89cb87345e002bf83228e775a746ce69).
Each applies on its own (or on the patches its row says it needs) (`git apply --check` on a clean checkout of the pin) and
all apply together in number order (`git am *.patch`). Nothing has been sent
upstream.

They were taken from `../src` (our working tree, exported as `../openrune.patch`)
with our own parts left out: Android support, token login, shared key, mobile
gameframe, test commands, members/idle choices, `::quest` (QuestTestCommand and
`Quest.debugSetStage`/`debugStartStage`), the `OPENRUNE_SKIP_CS2_PACKS` skip and
the `osrs-plugins-reverse:` comments (reworded or dropped).

| # | Patch | Files | Why it is generic | Caveats |
|---|---|---|---|---|
| 01 | `01-db-serialize-transactions` | `api/db/.../jdbc/GameDatabase.kt` | The game thread (logins) and the account saver share one JDBC connection; interleaved transactions commit each other's work and lose `SET LOCAL search_path` ("schema 'main' does not exist" on a save) | One lock serializes all transactions on that connection |
| 02 | `02-bankers-content-group` | `generic-npcs/pack/.../bankers.toml`, `al-kharid/pack/.../al_kharid_npcs.toml` | Bankers without `content.banker` answer Bank/Collect/Talk-to across the booth with "I can't reach that!" | 94 banker types added by hand from the cache (every "Banker" with Talk-to/Bank/Collect) |
| 03 | `03-fishing-spot-content-groups` | `.data/raw-cache/server/npcs.toml` | The Lumbridge river Lure/Bait spot and `0_50_49_saltfish` had no content group, so their ops did nothing | |
| 04 | `04-fishing-stop-out-of-reach` | `fishing/.../Fishing.kt` | Fishing went on after a teleport | "Beside" is Chebyshev distance 1 on the same level |
| 05 | `05-lumbridge-castle-rfd-doors` | `.data/raw-cache/server/loc/misthalin/lumbridge-castle.toml` (new) | The map's kitchen/dining-room doors are the Recipe for Disaster (`hundred_`) variants, which had no door tag | Opens into `elfdooropen` / `opencastledoubledoor*` (same models), which close to the plain doors |
| 06 | `06-varrock-single-juliet` | `.data/raw-cache/map/npcs/varrock.toml` | The plain `juliet` spawned beside the multinpc `juliet_multi_visible` | |
| 07 | `07-juliet-house-stairs` | `generic-locs/.../StraightStaircaseScript.kt` | `fai_varrock_stairs_taller`/`_top` had no handler | |
| 08 | `08-obj-examine-chat-type` | `api/player-output/.../GameMessage.kt`, `ObjExamineTest.kt` (new) | Item examines went out through the `examine_item` script as chat type 0; OSRS uses 27 (ObjExamine), which RuneLite's Examine plugin matches | Behaviour change: the market price / alch line the script printed is gone; `ClientScripts.examineItem` is left unused |
| 09 | `09-osrs-skilling-message-wording` | `Mining.kt`, `StarMiningScript.kt`, `Fishing.kt`, `CookingEvents.kt`, `SmeltingScript.kt`, `CannonBallScript.kt` | Small text and chat-type (spam vs game) differences from OSRS that client plugins parse | Touches `Fishing.kt` like 04 (other hunks) |
| 10 | `10-login-welcome-message` | `login/.../LoginScript.kt` | RuneLite's Chat History restores the chat only after "Welcome to Old School RuneScape." | Policy: any configured login message starting with "Welcome to " is replaced; upstream may prefer a config value |
| 11 | `11-dense-clientscript-ids` | `or-cache/.../PluginPacks.kt`, spawn + quest `clientscript.sym`/`gamevals.toml`, `.data/gamevals/clientscript.rscm`, `QuestPolicySyncScriptTest.kt` | Renumbers the custom clientscripts 45500-45553 to 9973-9985 and fails the cache build on custom ids above 9999 | Only needed for clients that size the script table from the archive (the official Android client crashes; the desktop client does not). Optional for upstream; the renumbering is part of it, since the guard alone fails upstream's build |
| 12 | `12-quests-sheep-shearer-romeo-juliet` | `content/quest/...`: `SheepShearer.kt`, `RomeoAndJuliet.kt`, `sheep_shearer_vars.toml`, `romeo_juliet_vars.toml`, quest pack `gamevals.toml`, `QuestTalkFixture.kt`, 2 interaction tests | New content on OpenRune's quest framework, with the OSRS progress varps (179, 144) Quest Helper reads | Server-only varbits/varp 63740-63742 |
| 13 | `13-dwarf-multicannon` | `content/other/dwarf-multicannon` (new module) | New content with the OSRS cannon varps; integration tests | Timers 440/441 |
| 14 | `14-dragon-dagger-warhammer-specials` | `content/other/special-attacks` | New content | No tests |
| 15 | `15-implings` | `content/skills/hunter` + `hunter/pack` (new modules) | Impling catching (dbtable `impling_types`, 60000, rows 60124-60134), jar loot, crystal implings in Prifddinas; integration tests | Loot tables were generated from OSRS Wiki pages (wiki content is CC BY-NC-SA; upstream may want to check); lucky impling clue loot not covered; crystal implings use the generic catch curve |
| 16 | `16-genie-random-event` | `content/events/random-events` (new module) | Genie random event; integration tests | Timers 450/451; `::randomevent` summons him (like `::star`) |
| 17 | `17-slayer-osrs-streak-and-wording` | `content/skills/slayer`: `SlayerTaskManager.kt`, `SlayerContact.kt`, `build.gradle.kts`, `SlayerTaskVarsTest.kt` (new) | Streaks in the client-visible varbits `slayer_tasks_completed`/`slayer_wilderness_tasks_completed`; OSRS gem-check and completion wording RuneLite parses | Existing saves keep their streak in the old server-only varbits (no migration) |
| 18 | `18-idle-packet-logout` | `api/net/.../handlers/IdleHandler.kt` (new), `MessageConsumerProvider.kt`, `IdleHandlerTest.kt` (new) | The client's IDLE packet logs the player out in OSRS; handled like the logout button | Contains nothing of ours |
| 19 | `19-farming-patches` | `content/skills/farming` + `farming/pack` (new modules) | OpenRune had no farming: allotment, flower and herb patches at six farms (Catherby, Falador, Ardougne, Morytania, Hosidius, Civitas illa Fortis) with the farming_transmit values RuneLite's Time Tracking reads; integration tests | dbtable 60002 (rows 60160-60187), varps 63780-63827, timer 460; `::growpatches`; no disease/compost/payments |
| 20 | `20-bird-houses` | `content/skills/hunter`: `BirdHouseScript.kt`, `BirdHouseTypesTable.kt`, `HunterPluginPack.kt`, both `gamevals.toml`, `birdhouse_vars.toml`, `BirdHouseScriptTest.kt` | Bird house trapping on Fossil Island with the varps (1626-1629) Time Tracking reads and the Loot Tracker message | Needs 15 (applies on pin+15 and after 01-19); dbtable 60001 (rows 60140-60148), varps 63760-63767; nest chances from the wiki chart (per mille, half at level 50 and below) |
| 21 | `21-treasure-trails` | `api/player`: `NpcOpOverrides.kt`, `LocOpOverrides.kt` (new), `NpcEvents.kt`, `LocEvents.kt`, `NpcInteractions.kt`, `LocInteractions.kt`; `content/other/treasure-trails` + `treasure-trails/pack` (new modules) | Beginner and easy Treasure Trails driven by the cache's own `cluehelper_*` dbtables (texts, targets, emotes, outfits, STASH locs): npc talks, Charlie the Tramp, Reldo's hot-cold device, loc searches, digs, emotes with Uri, music steps with Cecilia, per-step easy scrolls (item param 623), STASH units, Check steps, the beginner casket from the wiki table; texts and messages RuneLite's Clue Scroll and Loot Tracker plugins match; generic APIs to take an npc or loc op for the players content claims; integration tests | dbtables 60003-60007, varps 63840-63855; needs 22 (music steps); 4 easy map interfaces and drawer searches not done |
| 22 | `22-music-tab-play` | `api/music-plugin/.../MusicTabScript.kt`, `api/player/.../music/MusicPlayer.kt` | The music tab's track list did nothing: Play (unlocked tracks, switches to manual mode) and Unlock hint; `MusicPlayer.isUnlocked`/`isPlaying` | List order follows `music_list_setup` (non-hidden music rows in row order); 21 uses `isPlaying` for music clue steps, so apply 22 with 21 |
| 23 | `23-openable-drawers-chests` | `generic-locs/.../DrawersScript.kt`, `PlainChestScript.kt` | drawers1/3, the Draynor wardrobe, Seers' Village drawers and the Camelot chest had Open with no handler | Messages for the new ones follow the existing pattern ("You search the wardrobe but find nothing." is a best guess) |
| 24 | `24-treasure-trails-drawers-and-maps` | `treasure-trails`: `TrailSteps.kt`, `TreasureTrailScript.kt`, `TrailDataTables.kt`, pack `gamevals.toml`, `TreasureTrailScriptTest.kt` | Easy searches on locs that open first (27 rows) target the open variant's Search; map rows 2232/2234 get their interfaces | Needs 21 and 23 |
| 25 | `25-ice-mountain-music-area` | `or-cache/.../impl/Music.kt`, `.data/gamevals/area.rscm`, `dbrow.rscm`, `.data/raw-cache/map/area/ice_mountain.toml` (new) | Only Lumbridge had a modern music area; Alone (an easy clue music step) never unlocked | Polygon from the OSRS Wiki's Map:Alone; area 32762, dbrow 60920 |
| 26 | `26-fairy-rings` | `content/travel/fairy-rings` + `fairy-rings/pack` (new modules) | Fairy rings on the cache's `fairyring` dbtable: dial 398 + travel log 381, Zanaris, Last-destination, staff check; integration tests | Server varps 63860-63861 (visited codes); no favourites, hideout sequence or per-ring quest requirements; teleport closes the log's mobile search input with `meslayer_close` (mode 28) |
| 27 | `27-essence-pouch-check-wording` | `runecrafting/.../EssencePouchScript.kt` | OSRS Check wording (number words, pure/daeyalt/guardian) that RuneLite's Ess Pouch plugin parses | |
| 28 | `28-treasure-trails-medium` | `content/other/treasure-trails` (12 files, incl. `MediumCasketTable.kt`, `TrailKeyDropHook.kt` new) | Medium Treasure Trails: challenge scrolls, coordinates, double emotes, keys, maps, music, medium STASH units and casket; integration tests | Needs 21-24; dbtable 60008 (rows 61000-61191), varps 63870-63875; challenge dialogue wording is a best guess |
| 29 | `29-rune-pouch` | `content/skills/magic/rune-pouch` (new module) | Rune pouches had no Open/Empty/store: the pouch interface (190) with Store/Withdraw quantities, drag reorder and Examine, Empty, rune-on-pouch, on the client-visible `rune_pouch_type_n`/`_quantity_n` varbits spells already read; integration tests | No loadouts (Configure); full/non-rune messages are best guesses |
| 30 | `30-plank-sack` | `content/other/plank-sack` (new module) | Plank sack Fill/Check/Empty on the `plank_sack_*` varbits, and a storage hook so content can use planks from the sack; integration tests | Fill/full/no-space messages are best guesses; the sawmill does not fill the sack |
| 31 | `31-friend-add-update` | `api/net/.../central/CentralSocialClientPackets.kt`, `api/net/.../handlers/FriendListAddHandler.kt` | After FRIENDLIST_ADD, OSRS sends the new friend's UPDATE_FRIENDLIST entry; Central pushed one only for online friends, so an offline friend appeared only after relogging | One extra social snapshot round trip per add |
| 32 | `32-treasure-trails-hard` | `content/other/treasure-trails` (TrailTier, TrailSteps, TreasureTrailScript, pack: `HardCasketTable.kt` new, maps, gamevals, vars, test) | Hard Treasure Trails on the cache's clue rows: challenge talks, cryptics, coordinates, the 7 hard maps, emotes + Uri, music, hard STASH, the hard casket | Needs 21-28; dbtable 60009 (rows 61200-61433), map rows 61440-61446, varps 63880-63885; no puzzle boxes, wizards or Double agent yet |
| 33 | `33-treasure-trails-puzzle-boxes` | `treasure-trails`: `TrailPuzzle.kt` (new), `TrailSteps.kt`, `TreasureTrailScript.kt`, pack `TrailDataTables.kt`, gamevals, test | Hard puzzle-box steps: scrambled box, slide puzzle 306 with server-side move mirroring, solved check | Needs 32; dbtable 60010 (rows 61450-61470); npc lines are best guesses |
| 34 | `34-treasure-trails-hard-foes` | `treasure-trails`: `TrailFoeHook.kt` (new), `TrailSteps.kt`, `TreasureTrailScript.kt`, `TreasureTrailsModule.kt`, test | Hard coordinate wizards and the Double agent (kill before the dig / Uri); `::clue solve` | Needs 33; foe lifetime 500 ticks and the wilderness box are our choices |
| 35 | `35-hunter-traps` | `content/skills/hunter`: `HunterTrapScript.kt`, pack `HunterTrapTables.kt` (new), `HunterPluginPack.kt`, `gamevals.toml`, `HunterTrapScriptTest.kt` (new) | Bird snares and box traps (5 birds, ferret, 3 chinchompas) with the trap locs RuneLite's Hunter plugin tracks; integration tests | Needs 15 and 20; dbtables 60011-60012 (rows 60350-60360), timer 470; the catch curve beyond the crimson swift, the 200-tick collapse and most messages are best guesses; no smoking/baiting, no Eagles' Peak requirement |
| 36 | `36-giant-mole-kill-count` | `.data/raw-cache/server/npcs.toml` | The Giant Mole had no `killcount_varp`, so no "Your Giant Mole kill count is:" (RuneLite chat commands, KPH Tracker) | Uses the OSRS varp `total_mole_kills` (1515) |
| 37 | `37-kitten-growth-and-wording` | `pets/.../cats/CatCare.kt` | Overhead text on each kitten/cat growth event (what RuneLite's Kitten Tracker counts), growth paused while a modal is open (wiki), OSRS run-away wording | "Meow!" and the hunger run-away line are best guesses |
| 38 | `38-varrock-platebody-smelting` | `smithing/.../smelting/SmeltingScript.kt` | The Varrock platebody bonus at the Edgeville furnace smelts the next ore at the same time (second set of ores, its own XP) with OSRS's spam line "The Varrock platebody enabled you to smelt your next ore simultaneously.", which RuneLite's Smelting plugin counts; the bar used to be free, with an invented game message | Needs 09; the second ore set, the Make-X count and no bonus on the last bar of a Make-X are inferred from the message, not confirmed |
| 39 | `39-herbiboar` | `content/skills/hunter`: `HerbiboarScript.kt`, `HerbiboarScriptTest.kt` (new), pack `HerbiboarTables.kt` (new), `HunterPluginPack.kt`, `herbiboar_vars.toml` (new), both `gamevals.toml`, pack `build.gradle.kts` | Herbiboar tracking on the client's own trail varbits (RuneLite's Herbiboar plugin reads them): start locs, clue inspects, tunnel, harvest; integration tests | Needs 15, 20 and 35; dbtables 60013-60016 (rows 60370-60462), varps 63890-63891; the network is read from the map and the plugin's lists; route length, which legs end a route and non-wiki messages are best guesses; no new-track warning dialog, pet or herb tuft |
| 40 | `40-hunter-rumours` | `content/skills/hunter`: `HunterRumours.kt`, `HunterRumourScript.kt`, `HunterRumoursTest.kt` (new), `HunterTrapScript.kt`, `HerbiboarScript.kt`, pack `HunterRumourTables.kt`, `hunter_rumour_vars.toml` (new), `HunterPluginPack.kt`, both `gamevals.toml` | Hunters' Rumours state, rare-part drops with the wiki's rates and pity, turn-in XP and loot sack, the quetzal whistle's Rumour op; integration tests | Needs 39; dbtables 60017-60018 (rows 60470-60483), varps 63892-63902; no guild hunter dialogues yet (`::rumour` stands in); only catchable creatures assigned; loot sacks don't open; most part wordings are best guesses |
| 41 | `41-butterfly-netting` | `content/skills/hunter`: `ButterflyScript.kt` (new), `ButterflyScriptTest.kt` (new), pack `HunterButterfliesTable.kt` (new), `HunterPluginPack.kt`, `HunterRumourTables.kt`, `HunterRumours.kt`, `HunterRumoursTest.kt`, `gamevals.toml` | Butterfly and moth netting (net, magic net, barehanded +10 levels; wiki success charts), jars and Release effects, and the five butterfly/moth Hunters' Rumours; rare-part message in the wiki's colour; integration tests | Needs 40; dbtable 60019 (rows 60484-60495); one swing per click, the barehanded animation, the respawn and the messages are best guesses; ruby/sapphire/snowy copy the black warlock's chart |
| 42 | `42-hunter-guild-dialogues` | `content/skills/hunter`: `HunterGuildScript.kt` (new), `HunterGuildScriptTest.kt` (new), `HunterRumours.kt`, `HunterRumoursTest.kt`, `HunterTrapScript.kt`, pack `HunterRumourTables.kt`, `HunterTrapTables.kt`, `hunter_rumour_vars.toml`, both `gamevals.toml` | Guild hunter and Guild Scribe Verity dialogues (wiki transcripts): assign, remind, switch, hand in, unlock, completed count, back-to-back toggle; Gilman's food and drink; "red chinchompa" rumour name; embertailed jerboas in box traps and as a rumour | Needs 41; varp 63903, rows 60361 and 60496; "one of its [part]" lines use "some of its"; no spare-gear trade, intel-reshuffle lines or At First Light check; a few connecting lines are best guesses |
| 43 | `43-varps-before-first-tick` | `engine/game` `Player.kt`, `api/player` `VarPlayerIntMapSetter.kt`, `VarPlayerIntMapDelegate.kt`, `PlayerCommonVarExtensions.kt`, `api/game-process` `PlayerMainProcess.kt` | Varps set between the login varp sync and the player's first processed tick were never sent (setters skip while processedMapClock <= 0); they are recorded and resent on the first processed tick. Fixes follower_npc (varp 447) = 0 after a relog (RuneLite's Kitten Tracker) | Device-checked with the Kitten Tracker |
| 44 | `44-jewellery-teleports` | `content/travel/jewellery-teleports` (new module + pack) | Games necklace, ring of dueling, amulet of glory, skills necklace, combat bracelet: Rub menu (interface 187, script 217, hotkeys; RuneLite's Better Teleport Menu), worn destination ops, charges and the wiki's messages; integration tests | dbtables 60020-60021 (rows 60500-60556); menu title, glory/skills/combat charge lines, purple value, timing and two planes are best guesses; no destination requirements or landing radius |
| 45 | `45-jewellery-charge-slot` | `jewellery-teleports`: `JewelleryTeleportScript.kt`, `JewelleryTeleportScriptTest.kt` | A charge swap put the next charge in the first free slot (a worn games necklace moved to the head slot, device-seen); it now replaces in place | Needs 44 |
| 46 | `46-default-dog-breeds` | `pets/.../dogs/Dogs.kt` | `puppy_colours` rows with `unlock_bit` 0 asked for `varbit.dog_unlock_0`, which does not exist: Chase's Adopt threw and disconnected the player (device-seen) | Reading 0 as "no unlock needed" (the quest's default labrador/pug/spaniel) is inferred from the wiki, not checked row by row |
| 47 | `47-achievement-diary-journals` | `content/interfaces/achievement-diary` (new module + pack) | Open <area> Journal in the Achievement Diaries tab did nothing; it now opens the journal scroll (741) with the Lumbridge & Draynor and Varrock tasks per tier, word-wrapped by p12 widths, which RuneLite's Diary Requirements plugin annotates (device-checked) | dbtables 60022-60023 (rows 60560-60652); other areas say the journal isn't available; no task progress tracked; title and tier heading wording are best guesses |
| 48 | `48-grand-exchange` | `content/interfaces/grand-exchange` (new module + pack) | OpenRune had no Grand Exchange: the clerks' Exchange opens `ge_offers` (465) + `ge_offers_side` (467); buy (item search) and sell offers with the setup buttons, Confirm, Abort, Collect / Collect-all, and the banker's Collection Box (402); slot state in UPDATE_STOCKMARKET_SLOT, items in the cache's per-slot invs (enum 150); integration tests | The server is the only counterparty: offers at or better than the guide price (store cost) fill at once at the guide, others stay open until aborted; 2% tax; no partial fills, buy limits, history, sets, custom % buttons or platinum tokens; varps 63920-63970, timer 480 |
| 49 | `49-achievement-diary-areas-and-progress` | `content/interfaces/achievement-diary` (+ pack), hooks in lumbridge `Hans.kt`, `SawmillOperatorScript.kt`, `CookingEvents.kt`, `BurnLogEvents.kt`, `SpellTeleportScript.kt`, `RunecraftAction.kt`, `RuneEssenceTeleport.kt` (+ their build files) | Journals for the other 10 areas (wiki task lists, the Diary Requirements plugin's wording where it has one); task progress for 11 Lumbridge & Draynor / Varrock tasks driving the client's diary count varbits and striking done tasks in the journal; integration tests | Needs 47. Per-task bits in server varps 63976-63984 (the real `*_achievement_diary` bit layout is not mapped); no task-complete chat message (wording unknown); area boxes for Lumbridge / the Al Kharid mine are guesses |
| 50 | `50-ge-partial-fills-and-buy-limits` | `content/interfaces/grand-exchange` (`GrandExchange.kt`, `GrandExchangeScript.kt`, pack `gamevals.toml`, `grand_exchange.toml`, new `ge_buy_limits.toml`, `GrandExchangeScriptTest.kt`) | Fills arrive in up to four parts (2 ticks after Confirm, then every 5 ticks) with partial slot updates and "Grand Exchange: Bought/Sold n / total x Item." lines; buys count against the item's 4-hour buy limit and an offer over it waits for the window; integration tests | Needs 48. Limits generated from the OSRS Wiki's `Module:GELimits/data.json` (by the wiki's contributors, CC BY-NC-SA 3.0, attributed in the file header and the patch description; a different licence from OpenRune's, so upstream may want another source); 4186 objs, unlisted tradeables have no limit; no connected limits (e.g. potion doses); the partial-fill wording is a guess; enum 65380, varps 63985-64032 |
| 51 | `51-ge-trade-history` | `content/interfaces/grand-exchange` (`GrandExchange.kt`, `GrandExchangeScript.kt`, pack `gamevals.toml`, `grand_exchange.toml`, `GrandExchangeScriptTest.kt`) | The clerks' History op and the GE History button opened nothing: `ge_history` (383) filled by scripts 1644-1646 with the last 40 trades (finished offers, and aborted ones with something traded), newest first, sale tax shown; Buy-offer on a line starts a buy offer; integration test | Needs 50. Varps 63000-63159 (obj+sold bit, count, gold, tax per line); recorded when the offer finishes or is aborted (OSRS's exact moment is unverified) |
| 52 | `52-ge-tax-exempt-items` | `content/interfaces/grand-exchange` (`GrandExchange.kt`, pack `gamevals.toml`, new `ge_tax_exempt.toml`, `GrandExchangeScriptTest.kt`) | OSRS exempts some items (bonds, low-level ammo/food, teleport tablets, basic tools) from the 2% tax; enum `ge_tax_exempt` (65381, 48 objs) | Needs 51. List from the OSRS Wiki's Grand Exchange page (by the wiki's contributors, CC BY-NC-SA 3.0, attributed in the file header and the patch description; a different licence from OpenRune's); "Energy potion" taken as all doses |

New modules need no `settings.gradle.kts` entry (upstream discovers them).
No patch touches `gradle/libs.versions.toml`.

## Not patches: issues outside OpenRune-Server

**Reference-table sizes and name hash for added groups.** Our workaround is
`../scripts/fix_reftable.py`. Clientscript groups that the cache build *adds*
(not ones it rewrites) end up in archive 12's reference table with compressed
and uncompressed sizes (0, 0) and name hash 0; every stock group has real
values. The official Android client (241.4) crashes with a null dereference
during cache sync as soon as one such group exists (device-verified; filling in
the values fixes it). The code is not in this repo: `or-cache` calls
`PackCs2` (`dev.or2:tools:3.0.4`), which writes each script with
`Cache.write(12, id, data)` (no name) through `dev.openrune.cache.CacheDelegate`
into `com.displee.cache.CacheLibrary.put(index, archive, data, xtea)`
(Displee rs-cache-library 2.2); that library leaves the size fields of a new
archive at 0. Fix belongs in the cache tools: set sizes when a group is
written, and pass the script name (the compiler embeds `[clientscript,name]`)
so the name hash is set. Reproduce: `:or-cache:buildCache` with any pack that
adds a clientscript (the quest pack does), then decode archive 255 group 12
and look at the entries for 45550/45553 (or 9973/9974 with patch 11).

**Varclient configs for custom varcs.** Workaround `../scripts/add_varcs.py`.
The quest pack declares varcstr 1293/1294 (`quest_available_ids`,
`quest_policy_complete_ids`, `content/quest/pack/.../varc.sym`) for its
clientscripts, but nothing packs varclient configs (archive 2, group 19) for
them and rev 241 defines none. The client crash turned out to be the
reference-table issue above, not this, so this is low priority.

## Verification

On a clean clone checked out at the pin, 2026-10-16:

- `git apply --check` passes for every patch on its own, and `git am *.patch`
  applies all 18 in order.
- With all 18 applied, in Docker (`eclipse-temurin:21`, Gradle via `./gradlew`):
  `:or-cache:buildCache`, `generateRsa`, and compile of every touched module
  plus `:server:app:compileKotlin` succeed.
- Tests, all passing: `ObjExamineTest` (2), `IdleHandlerTest` (2),
  `SheepShearerInteractionTest` (4), `RomeoAndJulietInteractionTest` (3),
  `QuestPolicySyncScriptTest` (3), and the integration suites
  `DwarfMulticannonTest` (7), `ImplingScriptTest` (4), `ImplingLootScriptTest` (4),
  `CrystalImplingScriptTest` (2), `GenieRandomEventTest` (2), `SlayerTaskVarsTest` (2).
- `spotlessCheck` (upstream's ktlint, ratcheted against the pin) passes. The
  ktlint fixes are folded into 08, 11, 12, 13 and 17; in `SlayerContact.kt`,
  `PluginPacks.kt` and `GameMessage.kt` they include import-order and
  blank-line fixes to lines that were already there, which the ratchet flags
  as soon as a file is touched.

Limits: to save the multi-minute cache download, the clone used the packed
cache and `api/generated` sources from our own tree (`../src`, a superset of
these patches), and `buildCache` then ran incrementally over them. So the tests
ran against a cache that also holds our own changes. A from-scratch `install`
on the pin with only these patches was not run. Each patch was not compiled on
its own, only all together (they touch separate code, except 04 and 09 in
`Fishing.kt` and 11 and 12 in the quest pack, which use separate hunks and files).
Nothing was tested in game from these exact patches. In our tree, on the Android
client: crystal impling catch and the Genie lamp Rub work (2026-10-16); jar Loot
with the op fix below is not yet tried in game.

Patch 15 was updated afterwards (Loot registers on the cache op that carries
"Loot", which is the third one) and re-checked: apply-check on the pin, hunter
`spotlessCheck` and the hunter integration suites (10 tests) pass.

Patch 19 (farming) was added afterwards: `git apply --check` on the pin passes; it is new files
only and was compiled, tested (`FarmingScriptTest`, 8) and spotless-checked in our tree, not on a
pin-only clone.

Patch 20 (bird houses) was added afterwards: `git apply --check` passes on the pin with 15 applied,
and it was generated on top of 01-19 (`git am`). Device-verified in our tree (Build, Seeds, Empty,
the Loot Tracker message); the nest-chance fix in it ran only in the integration tests (7).

Patch 21 (Treasure Trails, was `21-beginner-clue-scrolls`) was added afterwards: `git apply --check` on the pin passes on its
own (one api change in `api/player`, otherwise new files). Compiled, tested (`TreasureTrailScriptTest`,
11) and spotless-checked in our tree, not on a pin-only clone. Regenerated 2026-10-17: rebuilt on the
cache's cluehelper tables, with easy clues and `LocOpOverrides`.

Patch 22 (music tab Play) was added 2026-10-17: `git apply --check` on the pin passes; compiled and
boot-checked in our tree.

Patches 29 (rune pouch) and 30 (plank sack) were added 2026-10-17 (eleventh server dev): new files
only; `git apply --check` on the pin passes for each, and `git am` applies all 30 in order. Compiled,
tested (`RunePouchScriptTest` 5, `PlankSackScriptTest` 3), spotless-checked and boot-checked in our
tree. Patch 26 was regenerated the same day (the meslayer close on teleport; the module no longer
needs `libs.rsprot.api`).

Patch 31 (friend add) was added 2026-10-17: `git apply --check` on the pin passes and all 31 apply
with `git am`. Compiled, `:api:net:test` and spotless pass in our tree; not yet tried on a client.

Patch 32 (hard clues, core tier) was added 2026-10-17: `git am` of 21-28 then 32 on the pin, and of
all 32, applies. Compiled, tested (`TreasureTrailScriptTest`, 18) and spotless-checked in our tree.

Patch 33 (puzzle boxes) was added 2026-10-17: `git am` of all 33 on the pin applies. Compiled,
tested (`TreasureTrailScriptTest`, 21), spotless- and boot-checked in our tree.

Patch 34 (wizards, Double agent) was added 2026-10-17: `git am` of all 34 on the pin applies.
Compiled, tested (`TreasureTrailScriptTest`, 23), spotless- and boot-checked in our tree.

Patch 41 (butterfly netting) was added 2026-10-17 (fourteenth server dev): `git am` of all 41 on the
pin applies. Compiled and tested (`ButterflyScriptTest` 5, `HunterRumoursTest` 5) in our tree.

Patch 42 (guild hunter dialogues, jerboas) was added 2026-10-17: `git am` of all 42 on the pin
applies. Compiled and tested (hunter integration suite, 41 tests) in our tree.

Patches 43 (varps before the first tick) and 44 (jewellery teleports) were added 2026-10-17: `git am` of all 44 on
the pin applies. Compiled and tested (`JewelleryTeleportScriptTest` 3) in our tree.

Patch 48 (Grand Exchange) was added 2026-10-17: it applies alone on the pin, and `git am` of all 48 on the pin applies.
Compiled and tested (`GrandExchangeScriptTest` 7) in our tree.

Patch 49 (diary areas and progress) was added 2026-10-17: `git am` of all 49 on the pin applies.
Compiled and tested (`DiaryTasksTest` 4) in our tree.
