# RSPS content coverage map

Presence only, not accuracy. "present, unverified" = code exists, behaviour not checked. Paths: OpenRune under
`content/` (or `api/`), LostCity under `re/ref/Content/scripts/`, Gielinor under
`re/ref/project-gielinor-rust-server/crates/`. Read 2026-10-19.

| Codebase | What it is | Licence | Read at |
|---|---|---|---|
| **OR main** | OpenRune upstream (RSMod fork, Kotlin), OSRS current rev | ISC (RS Mod) | `origin/main` bcdb03292, 2026-10-19 |
| **OR integ** | Our fork's `integration` = main + branches in `fork/local/integration-tools:integration.txt` (what the live server runs) | ISC | `fork/integration` 5cc473397, 2026-10-18 (lacks main's #312 Slayer commit) |
| **LostCity** | 2004 RuneScape 2 (Nov 23 2004), RuneScript content + TypeScript engine | MIT (assets Jagex) | Content 65b754f 2026-09-24; Engine-TS 1d25566 2026-08-20 |
| **Gielinor** | Project Gielinor Rust, Alpha 0.1.0, OSRS rev 239.1 | ISC (+RSProt MIT) | b811af5, checkpoint 2026-10-16 |

Gielinor's own README says "source presence is not live acceptance"; its RELEASE_NOTES list login, sync, inventory,
equipment, banking, movement, combat, runes, prayer, tutorial and the "Tower" (a custom floor-based mode, not OSRS
content) as implemented source. So every Gielinor cell below is at best "present, unverified".

Legend: **Y** implemented (present, unverified) · **P** partial · **–** not present · **#n** open upstream PR by
another contributor · **(#n)** our open upstream PR · **n/a** era (LostCity: postdates Nov 2004, or removed/reworked
in OSRS).

## Summary

| Category (denominator) | OR main | OR integ | LostCity | Gielinor |
|---|---|---|---|---|
| Skills (23 + Sailing = 24) | 17 Y + 3 P | 19 Y + 4 P | 19 Y, 5 n/a | 10 Y + 7 P |
| F2P quests (24) | 7 | 14 | 18 Y, 6 n/a | 1 (Learning the Ropes / Tutorial Island) |
| Members quests (160) | 2 + 2 miniquests | 2 + 2 miniquests | 44 Y + 1 stub | 0 |
| Minigames (52, OR's PROGRESS list) | 1 | 1 | 8 (1 n/a removed); rest mostly n/a | 0 |
| Bosses (169, OR's list) | 23 encounters + 76 drop-table only | same | ~16 (mostly quest bosses); most n/a | 0 |
| Raids (4) | 0 (#342, #274 ToA) | 0 | n/a | 0 |
| Random events (current OSRS list, ~24) | 0 | 1 (Genie, (#355)) | 6 OSRS-form + ~17 n/a (discontinued) | 0 |
| Core systems (19 rows below) | 12 Y + 4 P | 16 Y + 3 P | 12 Y, 7 n/a | 6 Y + 3 P |

Open upstream PRs by others that add content (`gh pr list`, 2026-10-19): #342 ToA (a-thoren), #334 Doric's Quest,
#333 The Knight's Sword, #325 Giants' Foundry + Sleeping Giants, #319 gravestones/Death's Office/Death's Coffer,
#315 The Ides of Milk + Brutus (all eaglepurerocketgrape), #313 special-attack conversion (jelqedagain),
#310 Thieving (ThePopple), #299 Romeo & Juliet, #298 Prince Ali Rescue + Al Kharid toll gate, #297 Waterfall Quest
(estesthebestes), #274 ToA rework (MouldyToast), #262 Fletching (a-thoren), #237 Zulrah (Dwinterheart),
#231 Farming patches, #228 Construction, #227 Agility (catesweb), #182 Sailing helm (MouldyToast).
Ours open upstream: #366 Sheep Shearer, #367 Goblin Diplomacy, #368 Witch's Potion, #355 Genie, #354 cannon
(+ fixes #348, #351, #362).

## Skills

OR's PROGRESS.md counts 20/23 by gameval references; Construction and Farming are counted there only from NPC
dialogue in `areas/city`, so they are not counted as implemented here.

| Skill | OR main | OR integ | LostCity | Gielinor |
|---|---|---|---|---|
| Attack/Strength/Defence/HP | Y `api/combat`, `interfaces/combat-tab` | Y | Y `skill_combat` | Y `world/src/melee*.rs`, `combat*.rs` |
| Ranged | Y `api/combat` | Y | Y `skill_combat/scripts/player/player_ranged.rs2` | Y `world/src/ranged*.rs` |
| Magic | Y `skills/magic/*` | Y | Y `skill_magic` | Y `world/src/spell_*.rs`, `autocast*.rs` |
| Prayer | Y `skills/prayer`, `interfaces/prayer-tab` | Y | Y `skill_prayer` | Y `world/src/prayer*.rs` |
| Runecraft | Y `skills/runecrafting` | Y | Y `skill_runecraft` | P (Tower task only) |
| Slayer | Y `skills/slayer` (#312 merged) | Y (pre-#312) | n/a | – (slayer-helm modifiers only) |
| Mining | Y `skills/mining` | Y | Y `skill_mining` | Y `world/src/mining.rs`, `gathering.rs` |
| Smithing | Y `skills/smithing` | Y | Y `skill_smithing` | Y `world/src/smelting.rs`, `anvil*.rs`, `smithing_make.rs` |
| Woodcutting | Y `skills/woodcutting` | Y | Y `skill_woodcutting` | Y `world/src/woodcutting.rs` |
| Firemaking | Y `skills/firemaking` | Y | Y `skill_firemaking` | P (`tutorial_fire.rs`) |
| Cooking | Y `skills/cooking` | Y | Y `skill_cooking` | P (`tutorial_bread.rs`, `tutorial_shrimp.rs`, `tower_food.rs`) |
| Herblore | Y `skills/herblore` | Y | Y `skill_herblore` | P (potions consumable `potions.rs`; Tower task) |
| Fishing | Y `skills/fishing` | Y | Y `skill_fishing` | P (`tutorial_fishing.rs`) |
| Crafting | Y `skills/crafting` | Y | Y `skill_crafting` | P (`tower_tanning.rs`) |
| Thieving | P `skills/thieving` (419 loc) · #310 | P | Y `skill_thieving` | – |
| Agility | P (shortcuts in `areas/city/*`) · #227 | P | Y `skill_agility` | – |
| Fletching | – · #262 | P `skills/fletching` (knife only) | Y `skill_fletching` | P (Tower arrows task) |
| Construction | P (`other/sawmill`, Daddy's Home furniture) · #228 | P | n/a | – |
| Farming | – · #231 | Y `skills/farming` | n/a | – |
| Hunter | – | Y `skills/hunter` | n/a | – |
| Sailing | – · #182 | – | n/a | – |

## Quests: free-to-play (wiki Quests/Free-to-play, 24)

| Quest | OR main | OR integ | LostCity | Gielinor |
|---|---|---|---|---|
| Cook's Assistant | Y `quest/.../lumbridge/CooksAssistant.kt` | Y | Y `quests/quest_cook` | – |
| Demon Slayer | – | – | Y `quest_demon` | – |
| The Restless Ghost | Y `quest/.../lumbridge/TheRestlessGhost*.kt` | Y | Y `quest_priest` | – |
| Romeo & Juliet | – · #299 | Y `quest/.../romeoandjuliet` (local) | Y `quest_romeojuliet` | – |
| Sheep Shearer | – (#366) | Y `quest/.../lumbridge/SheepShearer.kt` | Y `quest_sheep` | – |
| Shield of Arrav | – | – | Y `quest_blackarmgang` | – |
| Ernest the Chicken | Y `quest/.../draynor/ErnestTheChicken.kt` | Y | Y `quest_haunted` | – |
| Vampyre Slayer | – | Y `quest/.../draynor/VampyreSlayer.kt` | Y `quest_vampire` | – |
| Imp Catcher | Y `quest/.../wizardstower/ImpCatcher.kt` | Y | Y `quest_imp` | – |
| Prince Ali Rescue | – · #298 | – | Y `quest_prince` | – |
| Doric's Quest | – · #334 | Y `quest/.../falador/DoricsQuest.kt` | Y `quest_doric` | – |
| Black Knights' Fortress | – (area only `areas/misc/black-knights-fortress`) | – | Y `quest_blackknight` | – |
| Witch's Potion | – (#368) | Y `quest/.../rimmington/WitchsPotion.kt` | Y `quest_hetty` | – |
| The Knight's Sword | – · #333 | – | Y `quest_squire` | – |
| Goblin Diplomacy | – (#367) | Y `quest/.../goblinvillage/GoblinDiplomacy.kt` | Y `quest_gobdip` | – |
| Pirate's Treasure | – | Y `areas/city/port-sarim/.../piratestreasure` | Y `quest_hunt` | – |
| Dragon Slayer I | – | – | Y `quest_dragon` | – |
| Rune Mysteries | Y `quest/.../lumbridge/RuneMysteries.kt` | Y | Y `quest_runemysteries` | – |
| Misthalin Mystery | Y `quest/.../misthalin` | Y | n/a | – |
| The Corsair Curse | – | – | n/a | – |
| X Marks the Spot | Y `quest/.../xmarksthespot` | Y | n/a | – |
| Below Ice Mountain | – | – | n/a | – |
| Learning the Ropes (Tutorial Island) | – | – | n/a (2004 Tutorial Island in `tutorial/`) | Y `world/src/tutorial_*.rs` (~30 files) |
| The Ides of Milk | – · #315 | – | n/a | – |
| **Total** | **7** | **14** | **18 / 18 era-valid** | **1** |

## Quests: members (wiki: 160)

| | OR main | OR integ | LostCity | Gielinor |
|---|---|---|---|---|
| Count | 2 quests + 2 miniquests | same | 44 + 1 stub | 0 |
| Present | Gertrude's Cat `quest/.../gertrudescat`; Children of the Sun `quest/.../childrenofthesun`; miniquests Daddy's Home `quest/.../daddyshome`, His Faithful Servants `quest/.../morytania` | same | see list below | – |
| Open PR (others) | Waterfall Quest #297 | | | |

LostCity members quests (`quests/quest_<dir>`; all missing upstream except Gertrude's Cat; Waterfall has #297):
Fight Arena `arena`, Merlin's Crystal `arthur`, Witch's House `ball`, Biohazard `biohazard`, Big Chompy Bird Hunting
`chompybird`, Clock Tower `cog`, Family Crest `crest`, Death Plateau `death`, The Tourist Trap `desertrescue`,
Druidic Ritual `druid`, Nature Spirit `druidspirit`, Monk's Friend `drunkmonk`, Eadgar's Ruse `eadgar`, Elemental
Workshop I `elemental_workshop`, Plague City `elena`, Fishing Contest `fishingcompo`, Gertrude's Cat `fluffs`,
Holy Grail `grail`, The Grand Tree `grandtree`, Hazeel Cult `hazeelcult`, Heroes' Quest `hero`, Horror from the Deep
`horror`, Temple of Ikov `ikov`, The Dig Site `itexam`, Observatory Quest `itgronigen`, Watchtower `itwatchtower`,
Jungle Potion `junglepotion`, Legends' Quest `legends`, Dwarf Cannon `mcannon`, Shades of Mort'ton `mortton`, Murder
Mystery `murder`, Priest in Peril `priestperil`, Regicide `regicide`, Scorpion Catcher `scorpcatcher`, Sea Slug
`seaslug`, Sheep Herder `sheepherder`, Tai Bwo Wannai Trio `tbwt`, Tribal Totem `totem`, Tree Gnome Village `tree`,
Underground Pass `upass`, The Fremennik Trials `viking`, Waterfall Quest `waterfall`, Lost City `zanaris`, Shilo
Village `zombiequeen`; stub: `troll` (questlist entry, empty dir). Miniquest: Alfred Grimhand's Barcrawl `barcrawl`.

## Minigames (OR PROGRESS.md list, 52)

Only rows where any codebase has something; the other 43 are – everywhere (LostCity mostly n/a: Pest Control,
Castle Wars, Barbarian Assault, TzHaar, NMZ, etc. postdate 2004).

| Minigame | OR main | OR integ | LostCity | Gielinor |
|---|---|---|---|---|
| The Gauntlet | Y `minigames/gauntlet` | Y | n/a | – |
| Giants' Foundry | – · #325 | – | n/a | – |
| Brimhaven Agility Arena | – | – | Y `minigames/game_agilityarena` | – |
| Burthorpe Games Room | – | – | Y `minigames/game_gamesroom` | – |
| Gnome Ball | – | – | Y `minigames/game_gnomeball` | – |
| Shades of Mort'ton (minigame) | – | – | Y `minigames/game_mortton` | – |
| Fishing Trawler | – | – | Y `minigames/game_trawler` | – |
| Mage Arena | – | – | Y `areas/area_mage_arena` (Kolodion) | – |
| Chompy bird hunting | – | – | P (quest `quest_chompybird` only) | – |
| Duel Arena | n/a (now Emir's Arena) | – | Y `minigames/game_duelarena` (n/a: staking removed in OSRS) | – |
| Emir's Arena | – | – | n/a | – |
| (not in list) Falador Party Room | – | – | Y `minigames/game_partyroom` | – |
| (not in list) Ranging Guild | – | – | Y `minigames/game_ranging` | – |

## Bosses (OR PROGRESS.md list, 169) and raids

OR main/integ: 23 with encounter code — Amoxliatl, Artio, Barrows, Callisto, Commander Zilyana, Corrupted and
Crystalline Hunllef, Doom of Mokhaiotl, Duke Sucellus, Gemstone Crab, General Graardor, K'ril Tsutsaroth, King Black
Dragon, Kraken, Kree'arra, Phantom Muspah, Scurrius, Spindel, The Leviathan, The Whisperer, Vanguard, Vardorvis,
Venenatis (`bosses/*`, plus `bosses/demonic-gorilla`, `bosses/tormented-demon` outside the list). 76 more are
**drop table only** (`drops/`, e.g. Giant Mole, Kalphite Queen, Zulrah, Vorkath, DKs, Sarachnis, Cerberus, Chaos
Elemental, Corp). 70 nothing. Integration adds no bosses (dragon specials, Giant Mole KC fix only).

| Boss | OR main | OR integ | LostCity | Gielinor |
|---|---|---|---|---|
| King Black Dragon | Y `bosses/kbd` | Y | Y `areas/area_wilderness/scripts/king_black_dragon.rs2` | – |
| Kalphite Queen | drop table only | same | Y `areas/area_kalphite/scripts/kalphite_queen.rs2` | – |
| Elvarg | – | – | Y `quests/quest_dragon` | – |
| Melzar the Mad | – | – | Y `quests/quest_dragon` | – |
| Delrith | – | – | Y `quests/quest_demon` | – |
| Count Draynor | – | – | Y `quests/quest_vampire` | – |
| Black Knight Titan | drop table only | same | Y `quests/quest_grail` | – |
| Tree spirit (Lost City) | – | – | Y `quests/quest_zanaris` | – |
| Sir Mordred | – | – | Y `quests/quest_arthur` | – |
| Chronozon | drop table only | same | Y `quests/quest_crest` | – |
| Salarin the twisted | drop table only | same | Y `areas/area_yanille` | – |
| General Khazard / Bouncer | – | – | Y `quests/quest_arena` | – |
| Nezikchened | – | – | Y `quests/quest_legends` | – |
| Black demon (Grand Tree) | – | – | Y `quests/quest_grandtree` | – |
| The Draugen | – | – | Y `quests/quest_viking` | – |
| Zulrah | drop table only · #237 | same | n/a | – |
| Brutus | drop table only · #315 | same | n/a | – |
| Raids: CoX, ToB, ToA, Fractured Archive | – (ToA #342, #274) | – | n/a | – |

Gielinor: no boss or minigame content; NPC combat/death is generic (`world/src/npc_*.rs`).

## Random events (wiki Random events)

| Event | OR main | OR integ | LostCity | Gielinor |
|---|---|---|---|---|
| Genie | – (#355) | Y `events/random-events` | Y `macro events/scripts/general/macro_event_genie.rs2` | – |
| Drunken Dwarf | – | – | Y `…/general/macro_event_drunken_dwarf.rs2` | – |
| Maze | – | – | Y `…/general/macro_event_maze.rs2` | – |
| Mime | – | – | Y `…/general/macro_event_mime.rs2` | – |
| Mysterious Old Man | – | – | Y `…/general/macro_event_mysterious_old_man.rs2` | – |
| Strange plant | – | – | Y `…/general/macro_event_triffid.rs2` | – |
| Spawn scheduler | – | P (Genie-only) | Y `macro events/scripts/macro_events.rs2` | – |
| Beekeeper, Arnav, Certers, Count Check, Drill Demon, Evil Bob, Evil twin, Freaky Forester, Gravedigger, Jekyll, Kiss the frog, Pillory, Pinball, Prison Pete, Quiz Master, Rick Turpentine, Sandwich lady, Surprise Exam | – | – | n/a (postdate 2004) | – |
| Strange box, Swarm, skilling guardians (big fish, river troll, whirlpool, rock golem, gas, lost pickaxe/axe, shade, zombie, watchman, poisonous gas, dryad, ent) | n/a | n/a | present, n/a (discontinued in OSRS) `macro events/scripts/{fishing,mining,prayer,thieving,woodcutting}` | – |

## Core systems

| System | OR main | OR integ | LostCity | Gielinor |
|---|---|---|---|---|
| Banking | Y `interfaces/bank`, `interfaces/deposit-box` | Y | Y `interface_bank` | Y `world/src/bank*.rs` |
| Grand Exchange | – (`api/market` prices only) | Y `interfaces/grand-exchange` | n/a | – |
| Shops | Y `interfaces/omnishop`, `api/shops` | Y | Y `shop` | Y `world/src/shop.rs` |
| Combat styles | Y `interfaces/combat-tab` | Y | Y `skill_combat/scripts/player/player_attackstyles.rs2` | Y `world/src/combat_stance.rs`, `server/src/combat_tab.rs` |
| Special attacks | Y `other/special-attacks`, `api/specials` · #313 | Y (+ dragon specials) | Y `skill_combat/scripts/player/specs` | – (spec varp only) |
| Prayer | Y `skills/prayer` | Y | Y `skill_prayer` | Y |
| Spellbooks: standard | Y `skills/magic/spell-attacks`, `spell-teleports` | Y | Y `skill_magic` | P (elemental combat spells, autocast) |
| Spellbooks: ancient / lunar / arceuus | Y / – / Y `spell-attacks/ancient`, `arceuus-spells` | same | n/a | – |
| Clue scrolls | P (clue drops in `drops/` only) | Y `other/treasure-trails` (beginner–hard) | Y `minigames/game_trail` (easy/medium/hard) | – |
| Achievement diaries | – | Y `interfaces/achievement-diary` | n/a | – (Tower "diary" is custom) |
| Farming patches | – · #231 | Y `skills/farming` | n/a | – |
| Construction / POH | P (sawmill only) · #228 | P | n/a | – |
| Pets | Y `other/pets` | Y | n/a | – |
| Friends / clans / chat | P (friend packets `api/net`; no clans) | P | Y friends `Engine-TS/src/server/friend`; clans n/a | – (protocol tables only) |
| Trading | – (player op only, `other/login`) | – | Y `interface_trade` | – |
| Dueling / PvP | PvP Y `api/death` PvP hooks, `areas/wilderness`; duel n/a | same | Y `skill_combat/scripts/pvp`, `game_duelarena` | – |
| Music | Y `api/music`, `api/music-plugin` | Y (+ music tab fix) | Y `music` | Y `world/src/music*.rs` |
| Emotes | Y `interfaces/emotes` | Y | Y `interface_controls/scripts/player_controls.rs2` | P (emote list `server/src/interface_hooks.rs`) |
| Death / gravestone | death Y `api/death`, `api/death-plugin`; gravestone – · #319 | same | death Y `player/scripts/death.rs2`; gravestone n/a | death Y (`Deferred::PlayerDeath`); gravestone – |
| Slayer | Y `skills/slayer` | Y | n/a | – |
| Travel extras | canoe `travel/canoe` | + fairy rings, jewellery teleports, cannon, looting bag | – | – |
| Tutorial Island | – | – | n/a (2004 version `tutorial/`) | Y `world/src/tutorial_*.rs` |

## Where references help

Missing on OR main, defined in LostCity or Gielinor, with no open upstream PR by another contributor. Items we
already have on integration are marked "ours". Era caveat: LostCity is 2004 logic/dialogue; OSRS may differ
(check the wiki/cache with the accuracy-check skill; LostCity and Gielinor are evidence, never copied).

| # | Item | Reference | Notes |
|---|---|---|---|
| 1 | Demon Slayer (F2P) | LC `quests/quest_demon` | Delrith encounter included |
| 2 | Shield of Arrav (F2P) | LC `quests/quest_blackarmgang` | two-player gang split |
| 3 | Black Knights' Fortress (F2P) | LC `quests/quest_blackknight` | OR already has `areas/misc/black-knights-fortress` guards |
| 4 | Dragon Slayer I (F2P) | LC `quests/quest_dragon` | Elvarg, Melzar's Maze; larger |
| 5 | Druidic Ritual (members) | LC `quests/quest_druid` | gates Herblore on OSRS; short |
| 6 | Player trading | LC `interface_trade` | OR main sets the op only |
| 7 | Learning the Ropes / Tutorial Island | Gielinor `world/src/tutorial_*.rs`; LC `tutorial/` (2004 form) | Gielinor targets rev 239 |
| 8 | Random events: Drunken Dwarf, Mysterious Old Man, Mime, Maze, Strange plant | LC `macro events/scripts/general` | Genie ours (#355) provides the spawner pattern |
| 9 | Kalphite Queen encounter | LC `areas/area_kalphite/scripts/kalphite_queen.rs2` | OR has the drop table |
| 10 | Fishing Trawler | LC `minigames/game_trawler` | self-contained minigame |
| 11 | Short members quests: Witch's House, Monk's Friend, Fishing Contest, Sheep Herder, Plague City, Clock Tower, Scorpion Catcher, Sea Slug, Tree Gnome Village | LC `quests/quest_{ball,drunkmonk,fishingcompo,sheepherder,elena,cog,scorpcatcher,seaslug,tree}` | |
| 12 | Gnome Ball, Burthorpe Games Room, Shades of Mort'ton, Mage Arena (Kolodion), Party Room | LC `minigames/game_{gnomeball,gamesroom,mortton,partyroom}`, `areas/area_mage_arena` | |
| 13 | Brimhaven Agility Arena | LC `minigames/game_agilityarena` | depends on Agility skill (#227) |
| 14 | Clue scrolls (easy/medium/hard steps) | LC `minigames/game_trail` | ours on integration (`other/treasure-trails`) |
| 15 | Vampyre Slayer, Pirate's Treasure | LC `quest_vampire`, `quest_hunt` | ours on integration, no upstream PR yet |
| 16 | Remaining LC members quests (~30, e.g. Fight Arena, Merlin's Crystal, Holy Grail, Heroes', Legends', Regicide, Underground Pass, Fremennik Trials) | LC `quests/` | large; bring their quest bosses (Bouncer, Khazard, Mordred, Draugen, …) |

Excluded (open PR by others): Romeo & Juliet #299, Prince Ali #298, Knight's Sword #333, Doric's #334 (also ours on
integration), Waterfall #297, Thieving #310, Agility #227, Fletching #262, Farming #231, Construction #228, Sailing
#182, gravestones #319, Giants' Foundry #325, ToA #342/#274, Zulrah #237, Ides of Milk/Brutus #315.
