# Patches for upstream OpenRune-Server

One `git format-patch` file per fix, each against OpenRune-Server
[`9a8b338a8`](https://github.com/OpenRune/OpenRune-Server/commit/9a8b338a89cb87345e002bf83228e775a746ce69).
Each applies on its own (`git apply --check` on a clean checkout of the pin) and
all 18 apply together in number order (`git am *.patch`). Nothing has been sent
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
| 21 | `21-beginner-clue-scrolls` | `api/player`: `NpcOpOverrides.kt` (new), `NpcEvents.kt`, `NpcInteractions.kt`; `content/other/treasure-trails` + `treasure-trails/pack` (new modules) | New content: beginner Treasure Trails (clue text interface 203, map interfaces 346-356, 34 steps: talks, emotes with Uri, map digs, Charlie the Tramp, Reldo's hot-cold device; Check steps; the reward casket from the wiki table; beginner STASH units) with the texts and messages RuneLite's Clue Scroll and Loot Tracker plugins match; a small generic API to let content take an npc op ahead of its handler for the players it claims; integration tests | dbtables 60003-60005 (rows 60200-60322), varps 63840-63846; no scroll cases |

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

Patch 21 (beginner clue scrolls) was added afterwards: `git apply --check` on the pin passes on its
own (one api change in `api/player`, otherwise new files). Compiled, tested (`BeginnerClueScriptTest`
10, `StashUnitScriptTest` 2) and spotless-checked in our tree, not on a pin-only clone. Regenerated
2026-10-17 with Charlie the Tramp, hot-cold, Check steps and STASH units.
