# Hunter: implings, bird houses, traps, Herbiboar, Hunters' Rumours, butterflies, guild dialogues (content/hunter, patches 15, 20, 35, 39, 40, 41, 42, unpushed)

Audited 2026-10-18 against the OSRS Wiki (pages: Impling, Baby/Gourmet/Dragon/Crystal impling jar, Impling jar,
Crystal impling, Baby impling, Bird house trapping, Bird house (item), Birdhouse (scenery), Space (birdhouse),
Strung rabbit foot, Clue nest, Bird snare, Box trap, Crimson swift, Golden warbler, Copper longtail, Cerulean twitch,
Tropical wagtail, Ferret (Hunter), Chinchompa (Hunter), Carnivorous chinchompa, Black chinchompa (Hunter),
Embertailed jerboa, Herbiboar, Tunnel (herbiboar), Muddy patch, Smelly mushroom, Hunters' Rumours, Basic quetzal
whistle, Black warlock, Black warlock (item), Ruby harvest, Sapphire glacialis, Snowy knight, Sunlight Moth,
Moonlight moth, Transcript:Huntmaster Gilman (Novice), Transcript:Guild Hunter Cervus (Adept), Transcript:Guild
Hunter Teco (Expert), Transcript:Guild Hunter Wolf (Master), Transcript:Guild Scribe Verity), plus cache item ops
(quetzal whistle, butterfly jars). Report only.

## Summary
127 mechanics checked across the seven patches: 77 match, 17 diverge (14 gameplay-visible, 3 cosmetic), 33 unsourced.
Most data tables (levels, XP, jar loot, fossil table, herb chart, rumour rates and pity, butterfly charts,
dialogue text) are right. The divergences are in the mechanics the patches had to guess. The fixes that matter most:
1. Per-creature catch curves for bird snares and box traps (35). The patch shifts the crimson swift curve, which
   leaves black chinchompas at 39% when the wiki gives 58% at level 73.
2. Box-trap creature respawn: the patch uses 50 ticks, but chinchompas and jerboas respawn in 7 (35).
3. Bird houses (20): high-level seeds need only 5; raw bird meat always goes to the ground; early Empty and the
   Reset auto-build are not implemented; the seed-nest chance is 1/256 low.
4. Crystal impling catch chance: the patch uses the generic curve (81% at 80); the wiki gives 45% to 55% (15).
5. Impling tertiary clue scrolls are given even when the player already has a clue of that tier (15).

Live tree: all 17 divergences are also in openrune/openrune.patch (its hunter module has the same code; 17 same, 0 differ, 0 not present).

Separate note: README.md row 20 still lists the old ids (dbtable 60001, rows 60140-60148, varps 63760-63767), and
row 35 lists timer 470. The patches use 56003, 60163-60171, 63762-63769 and timer 436.

---

## 15 Implings

### Mechanics
| # | Mechanic | Official behaviour (source) | Patch behaviour | Verdict |
|---|---|---|---|---|
| 1 | Catch levels | Baby 17, young 22, gourmet 28, earth 36, essence 42, eclectic 50, nature 58, magpie 65, ninja 74, dragon 83, lucky 89 (Impling, Types of implings) | `ImplingTypesTable` LEVEL column, same values | matches |
| 2 | XP Puro-Puro / Gielinor | 18/20, 20/22, 22/24, 25/27, 27/29, 30/32, 34/36, 44/216, 50/240, 65/300, 80/380 (Impling) | XP_PURO_PURO / XP_GIELINOR columns; `catch(npc, impling, puroPuro)` picks by the `_maze` npc | matches |
| 3 | Barehanded | Allowed at +10 levels (Impling) | `BAREHANDED_EXTRA_LEVELS = 10`, net = wielded righthand only | matches |
| 4 | Catch chance | 12% at 1 to 98% at 99, linear (Impling ref 1, Mod Ash) | `catchChance()` `12 + 86*(L-1)/98` | matches |
| 5 | Barehanded / magic net bonus | +8 percentage points for both (same ref) | `bonus = net != NET` adds 8 | matches |
| 6 | Jar requirement in Puro-Puro | Jar required in Puro-Puro (Impling) | jar required everywhere (see gaps) | matches |
| 7 | Crystal impling level and XP | 80 (90 barehanded), 280 XP (Crystal impling) | `CrystalImplingScript` LEVEL 80, XP 280, generic +10 barehanded | matches |
| 8 | Crystal impling timing | One per world; respawns at :00/:30, visible 3 minutes later (Crystal impling) | `tick()` spawns a visible impling at :03/:33 UTC, plus one at server start | matches |
| 9 | Crystal impling catch chance | ~45.3% at 80 to ~55.5% at 99 with a net; +7.8% barehanded or magic net (Crystal impling chart) | generic curve: 81% at 80, 98% at 99 (+8) | diverges |
| 10 | Puro-Puro fixed respawns | 50 ticks for earth and essence, 7 for the rest (Impling, Puro-Puro) | RESPAWN_TICKS 50/7 via `npcRepo.despawn` | matches |
| 11 | Empty jar on loot | Returned, 10% break chance (Impling jar, Mod Ash) | `JAR_BREAK_NUM/DEN = 1/10`, jar back in the same slot | matches |
| 12 | Jar loot tables | Baby, gourmet (grubby key 1/500 pre-roll), dragon, crystal (elven signet 1/128, elite 1/50), main and tertiary as in the jar pages | `ImplingLootTables` (spot-checked baby, gourmet, dragon, crystal: items, quantities, noted flags, rates match) | matches |
| 13 | Dragon javelin tips | Only after Monkey Madness II; otherwise rolls the rest of the table (Dragon impling jar, note d1 and Mod Ash) | always on the table | diverges |
| 14 | Clue scroll tertiaries | A clue is not given when one of that tier is held (Clue nest: "Clues are not awarded if the player already has a clue of that tier"); jars warn when you are at the stackable clue cap (Impling jar, Changes 2023/2025) | `roll()` always adds the tertiary clue; no warning | diverges |
| 15 | Loot op index | "Loot" is a cache op (jar options Loot, Destroy) | `ImplingLootScript.startup` registers on the op that carries "Loot" | matches |
| 16 | Catch message | Wiki quotes only the no-jar line "You manage to catch the impling and acquire some loot." (Baby impling ref) | "You manage to catch the impling and squeeze it into a jar." (spam) | unsourced |
| 17 | Level-fail message | not on the wiki | "You need a Hunter level of N to catch this impling[ barehanded]." | unsourced |
| 18 | No-jar message | not on the wiki (in OSRS this case loots at once outside Puro-Puro) | "You need an empty impling jar to keep the impling in." | unsourced |
| 19 | Attempt cadence | "continue trying until it is caught" (Baby impling) | 2-tick attempts, at most 20 per click | unsourced |
| 20 | Crystal impling spots | 12 spots on the wiki map (image only) | `SPOTS` list of 12 coords | unsourced |
| 21 | Loot into a nearly full inventory | not documented | needs 1 free slot; later drops are lost if the inventory fills | unsourced |

### Divergences
#### D1. Crystal impling catch chance uses the generic impling curve
- Severity: gameplay-visible
- Official: about 45.3% at 80 and 55.5% at 99 with a butterfly net, +7.8% barehanded or with a magic net (Crystal impling, Hunter info chart; e.g. 116/256 at 80, 142/256 at 99).
- Patch: `ImplingScript.catchChance` gives 81% at 80 and 98% at 99 (the KDoc declares this as a deviation; the PR text does not).
- Fix: give `catch()` a chance function parameter. Crystal: `statRandom(HUNTER, low, high)` with the chart's numerators (net about 1+ (0.453*256) at 80 rising 1/256 per ~1.3 levels; fit low/high out of 256 to the chart points, about low=56/high=142 net and low=76/high=162 barehanded/magic). Then check it against the chart's 80 and 99 values.
- Live tree (openrune.patch): same (CrystalImplingScript calls the generic `ImplingScript.catch`, `catchChance` unchanged)

#### D2. Dragon javelin tips without Monkey Madness II
- Severity: gameplay-visible
- Official: dragon javelin tips are only dropped after MM2; without it the roll uses the remaining items (Dragon impling jar, note d1 and the Mod Ash reference).
- Patch: always on the table.
- Fix: in `pick()`, filter out obj 19582 while the MM2 quest varp is incomplete, and re-weight over the remaining entries.
- Live tree (openrune.patch): same (`ImplingLootTables` keeps 19582 unconditionally)

#### D3. Tertiary clue scrolls ignore clues already held
- Severity: gameplay-visible
- Official: one clue per tier (Clue nest); jars warn before opening when the clue cap is reached (Impling jar, Changes 16 Aug 2023 and 17 Sep 2025).
- Patch: `ImplingLootScript.roll` adds the clue regardless, so duplicates can be held.
- Fix: skip a tertiary clue (or its scroll box) when the player owns one of that tier (inventory and bank, or the stackable cap once scroll boxes exist), and add the pre-open warning.
- Live tree (openrune.patch): same (`roll()` adds `table.tertiary` unconditionally)

### Unsourced
- Rows 16-21: catch, level-fail and no-jar messages; 2-tick attempts with a 20-attempt cap; crystal spawn coordinates; extra drops lost on a full inventory. Needs a game capture or a transcript of the messages.
- Crystal impling spawn at server start (the wiki only gives the clock schedule).

### Not implemented (scope gaps)
- Catching on Gielinor without a jar (immediate loot) or barehanded with a jar outside Puro-Puro without a net. Only the KDoc declares this, not the PR text.
- Spawn system: Gielinor 30-minute cycles with high/mid/low tiers and 2-minute invisible roaming; Puro-Puro's 35 tiered invisible spawns (21 low, 12 mid, 2 high) that respawn invisible and re-roll their type. The patch relies on map spawns with fixed respawns, so any non-fixed spawn that `respawns` comes back in 7 ticks as the same type. Not declared.
- Lucky impling loot (declared). Impling tracker, Elnock's exchange, Bind/Snare grace period (not declared).

---

## 20 Bird houses

### Mechanics
| # | Mechanic | Official behaviour (source) | Patch behaviour | Verdict |
|---|---|---|---|---|
| 1 | Placement level per tier | 5, 14, 24, 34, 44, 49, 59, 74, 89 (Bird house trapping, table) | `BirdHouseTypesTable` HUNTER_LEVEL | matches |
| 2 | Hunter XP per tier | 112, 168, 224, 280, 369, 480, 612, 969, 1,200 | HUNTER_XP | matches |
| 3 | Four spaces, client varps | 4 spaces on Fossil Island; Time Tracking reads varps 1626-1629 (RuneLite, secondary) | `SPACES`, `tier*3+1/+2/+3` values | matches |
| 4 | Seeds needed | 10 low-level seeds, or 5 high-level seeds (wildblood hop, ranarr and higher herbs) (Bird house trapping, Seeds; Bird house (item)) | `SEEDS_NEEDED = 10` for every seed | diverges |
| 5 | Mixing seeds | Allowed (e.g. 7 barley + 3 hammerstone) | `addSeeds` tops up across seed types | matches |
| 6 | Fill time | ~50 minutes | `FULL_MINUTES = 50` (wall clock) | matches |
| 7 | Guaranteed loot | Clockwork, 10 raw bird meat, 30/40/50/60 feathers | `empty()` / `rollLoot` | matches |
| 8 | Raw bird meat destination | "Always dropped to the ground, even if there is space in the inventory" (Guaranteed drops, note d1) | `invAddOrDrop` (inventory first) | diverges |
| 9 | Seed nest chance | 1/256 at level 1 to 201/256 at 99 (chart: the skilling formula with low 0, high 200, i.e. `1 + floor(0.5 + 200*(L-1)/98)`) | `chance(level, 0, 200)` = `200*(L-1)/98` out of 256 (0 at 1, 200 at 99) | diverges |
| 10 | Other nests: 5 rolls, flat below 50, linear to 99 | Regular 15% to 30%, oak 18.7% at <=50 (Bird house trapping chart) | `nestChance(level, high)`, high/2 at <=50 (regular 300, oak 375 per mille) | matches |
| 11 | Nest table split | 0-2 eggs, 3-34 ring, rest empty (out of 100; Strung rabbit foot, Mod Ash) | 32 ring / 3 egg / 65 empty | matches |
| 12 | Emptying early | Allowed after a warning; keeps the clockwork, loses the house, seeds and loot | refuses: "The bird house isn't full of birds yet." | diverges |
| 13 | Reset | Empties, then builds and places a new house from logs and clockwork (prompts for the log type), one tick slower than doing it by hand | `reset()` only re-places a carried bird house item | diverges |
| 14 | Dismantle level | No Hunter level needed to empty | no check in `empty()` | matches |
| 15 | Collect message | Loot Tracker BIRDHOUSE_PATTERN (RuneLite, secondary) | `collectMessage()` | matches |
| 16 | Other messages | not on the wiki | "You place the bird house.", "You fill the bird house with seeds.", "You add some seeds...", "Your bird house trap is full of birds." etc. | unsourced |
| 17 | Op mapping per state | Empty "Seeds"; seeded "Seeds, Dismantle"; full "Interact, Seeds, Empty, Reset" (Birdhouse (scenery)) | op1 build/interact, op2 seeds, op3 empty/dismantle, op4 reset | unsourced |
| 18 | Nest chance for willow and higher tiers | only the first two chart series were readable | 384, 390, 420, 450, 480, 510, 525 per mille at 99 | unsourced |

### Divergences
#### D1. High-level seeds count as 1, not 2
- Severity: gameplay-visible
- Official: 5 wildblood hop seeds or 5 ranarr-and-higher herb seeds fill a house (Bird house trapping, Seeds table; Bird house (item)).
- Patch: `addSeeds` needs 10 of any seed.
- Fix: add a seed weight (2 for wildblood, ranarr, toadflax, irit, avantoe, kwuarm, snapdragon, cadantine, lantadyme, dwarf weed, torstol; 1 otherwise). Count `have` in weight units against 10, and delete `ceil(remaining / weight)` seeds.
- Live tree (openrune.patch): same (`SEEDS_NEEDED = 10`)

#### D2. Raw bird meat goes to the inventory
- Severity: gameplay-visible
- Official: always dropped to the ground (Guaranteed drops, note d1).
- Patch: `invAddOrDrop(objRepo, RAW_BIRD_MEAT, BIRDS)`. The KDoc declares this; the PR text does not.
- Fix: `objRepo.add(RAW_BIRD_MEAT, player.coords, ..., receiver = player)`.
- Live tree (openrune.patch): same (`invAddOrDrop(objRepo, RAW_BIRD_MEAT, BIRDS)`)

#### D3. Seed-nest chance 1/256 low
- Severity: gameplay-visible (small)
- Official: chart values 1/256 at 1, 101/256 at 50, 201/256 at 99.
- Patch: 0, 100, 200.
- Fix: use the standard skilling formula `1 + floor(0.5 + (low*(99-L) + high*(L-1))/98)` with low 0, high 200 (the trap patch's `catchChance256` already has it).
- Live tree (openrune.patch): same (`chance(level, 0, 200)`)

#### D4. Early Empty refuses instead of destroying the house
- Severity: gameplay-visible
- Official: a warning, then the clockwork is returned and the house, seeds and loot are lost.
- Patch: "The bird house isn't full of birds yet." The KDoc declares this; the PR text does not.
- Fix: in `empty()` when not full, show a Yes/No warning dialogue, then `clear()` and return only the clockwork. The warning text needs sourcing.
- Live tree (openrune.patch): same ("The bird house isn't full of birds yet.")

#### D5. Reset does not construct a bird house
- Severity: gameplay-visible
- Official: Reset builds a new house from logs and clockwork (with a log-type prompt if several are carried), one tick slower (Catching birds).
- Patch: only re-places a carried bird house item.
- Fix: when no matching bird house is carried, but a clockwork, hammer, chisel and logs of a placeable tier are, build that tier (the Crafting level and XP of the Crafting recipe), then place it.
- Live tree (openrune.patch): same (`reset()` only re-places a carried bird house)

### Unsourced
- Rows 16-18: game messages; which op index each multiloc state uses (check the cache loc ops of 30553-30555 and the tier variants); the per-mille nest highs for willow and above (read the full "Other nests" chart).
- The exact fill moment (the wiki gives random captures averaging every 5 minutes, up to 10). The patch is a flat 50 minutes, which is what Time Tracking assumes.

### Not implemented (scope gaps)
- Clue nests and the strung rabbit foot / twitcher's gloves effects (declared in the KDoc; the PR text only has "pet and herb tuft"). Crafting bird houses (logs on clockwork, Crafting 5-90, 15-55 XP), which is not declared.

---

## 35 Bird snares and box traps

### Mechanics
| # | Mechanic | Official behaviour (source) | Patch behaviour | Verdict |
|---|---|---|---|---|
| 1 | Levels | Crimson swift 1, golden warbler 5, copper longtail 9, cerulean twitch 11, tropical wagtail 19; ferret 27, chinchompa 53, carnivorous 63, black 73 (Bird snare, Box trap) | `HunterTrapCreaturesTable` | matches |
| 2 | XP (consistent values) | 34, 47, 61.2, 198.4, 265, 315 (creature pages) | XP_TENTHS 340, 470, 612, 1984, 2650, 3150 | matches |
| 3 | XP (conflicting wiki values) | Cerulean twitch 64.5 (creature) vs 64.6 (Bird snare); tropical wagtail 95.2 vs 95; ferret 115.2 vs 115 (Box trap) | 64.6, 95, 115 | unsourced |
| 4 | Bird loot | Bones, raw bird meat, 5-10 feathers of the bird's colour (each bird page) | bones + raw bird meat + 5-10 feathers | matches |
| 5 | Trap limit | 1, +1 at 20, 40, 60, 80 (Bird snare, Box trap) | `maxTraps()` | matches |
| 6 | Attempt cadence (box) | Every ~3 ticks (Box trap, Mechanics, Mod Ash) | `ATTEMPT_TICKS = 3` | matches |
| 7 | Attempt cadence (snare) | not documented | 3 ticks | unsourced |
| 8 | Range and standing on trap | 2-tile radius (5x5); no catch while the player stands on the trap (Box trap; Bird snare) | `RANGE = 2`, `if (coords == trap.coords) return` | matches |
| 9 | Catch chance | Per creature, at the player's own level: crimson swift 39%-164%, golden warbler 36%-157%, copper longtail 33%-152%, cerulean twitch 32%-149%, tropical wagtail 29%-144% (lvl 1 to 99); grey chinchompa `(floor(262(L-1)/98)+6)/255` (145/255 at 53); carnivorous and black `(floor(306(L-1)/98)-78)/255` (115/255 at 63, 148/255 at 73, 228/255 at 99) | crimson swift curve at `level - creature.level + 1` for every creature | diverges |
| 10 | Miss | Prey evades, trap closes and must be reset (Box trap; Bird snare) | Failing then broken loc | matches |
| 11 | Creature respawn after catch | Chinchompa, carnivorous, black chinchompa, embertailed jerboa: 7 ticks (infobox respawn time) | `CREATURE_RESPAWN_TICKS = 50` | diverges |
| 12 | Check with stackable traps and loot | Bird snares and box traps stack since 20 Aug 2025 (Bird snare, Box trap Changes) | `check()` needs `loot.size + 1` free slots even when the trap and chinchompas stack | diverges |
| 13 | Jerboa check message (patch 42) | "You've caught an embertailed jerboa... or maybe not." (Embertailed jerboa) | same | matches |
| 14 | Collapse | not documented | 200 ticks, item dropped, "Your trap has collapsed." | unsourced |
| 15 | Other messages | not documented | "You begin setting up the trap.", "You can't lay a trap here.", "This isn't your trap.", "You don't have a high enough Hunter level to set up more than N traps.", "You've caught a <creature>." | unsourced |
| 16 | Logout | not documented | traps dropped as ground items for 200 ticks | unsourced |

### Divergences
#### D1. One shifted curve for every trap creature
- Severity: gameplay-visible
- Official: each creature has its own curve at the player's absolute level (creature pages above). At the requirement: warbler 41.4% (106/256), wagtail 50.8% (130/256), grey chinchompa 57%, carnivorous 45%, black 58%. At 99: chinchompa 100%, carnivorous and black 89%.
- Patch: `catchChance256(level - creature.level + 1)` gives 39.5% at every requirement, and black chinchompas only 73% at 99. The PR text declares the curves as best guesses.
- Fix: add `low`/`high` columns (out of 256, OSRS formula at the player's level) to `hunter_trap_creatures`: crimson 100/419 (verified), warbler about 92/402, longtail about 84/389, cerulean about 82/381, wagtail about 74/368. Fit these exactly to the chart points on each page. For chinchompas, use the wiki's /255 formulas directly, or fit the equivalent low/high (grey about 6/268, red/black about -78/228 over 255). Jerboa and ferret have no chart, so keep them as declared guesses.
- Live tree (openrune.patch): same (`catchChance256(level - creature.level + 1)`)

#### D2. Box-trap creatures respawn after 50 ticks
- Severity: gameplay-visible
- Official: 7 ticks for chinchompas (all three) and jerboas (infobox Respawn time; the "stand and shoot" strategy on Chinchompa (Hunter) depends on it).
- Patch: 50.
- Fix: use the npc type's own respawn rate (`npcRepo.despawn(npc, npc.type.respawnRate)` or a per-creature column with 7 for these).
- Live tree (openrune.patch): same (`CREATURE_RESPAWN_TICKS = 50`)

#### D3. Check demands free slots that stacking makes unnecessary
- Severity: gameplay-visible (minor)
- Official: traps stack (2025); chinchompas stack.
- Patch: `inv.freeSpace() < creature.loot.size + 1` refuses with a full inventory even when both items would stack.
- Fix: check `inv` can take each item (`invAdd` dry run or `canAdd`) instead of counting free slots.
- Live tree (openrune.patch): same (`inv.freeSpace() < creature.loot.size + 1`)

### Unsourced
- Rows 3, 7, 14-16: the three conflicting XP values (the creature infoboxes say 64.5/95.2/115.2; confirm in game), the snare cadence, collapse time and wording, every other trap message, and what happens to traps on logout.
- Ferret and jerboa catch chance (no wiki chart).
- Lay timing (`LAY_TICKS = 3`), the "step off" tile choice.

### Not implemented (scope gaps)
- Eagles' Peak requirement, smoking (+2%) and baiting (+3%) (declared). The extra trap in the Wilderness for black chinchompas and the prohibited-area list are not declared. Baby chinchompa pet (not declared).

---

## 39 Herbiboar

### Mechanics
| # | Mechanic | Official behaviour (source) | Patch behaviour | Verdict |
|---|---|---|---|---|
| 1 | Hunter requirement | 80, boostable, only for starting; XP from the base level (Herbiboar, Notes) | `inspectStart` checks `stat()`; `tunnelXp(statBase())` | matches |
| 2 | Herblore requirement | 31 | `HERBLORE_LEVEL = 31` | matches |
| 3 | Tunnel XP | 1,950 at 80, +30/level to 2,370 at 94, 2,385 at 95, +19/level to 2,461 at 99 (Herbiboar; Tunnel (herbiboar)) | `tunnelXp()` | matches |
| 4 | Inspect XP | "can grant 50 experience"; "Occasionally ... may be awarded 50 Hunter experience" (Herbiboar; Muddy patch) | 50 XP on every inspect except the first and last | unsourced |
| 5 | Fossil table | Numulite 5-24 63/175, small 7/350, medium 7/700, large 7/875, rare 7/3,500 | `FOSSILS` out of 3,500 (1260/70/35/28/7) | matches |
| 6 | Rolls on first/last inspect | None (Herbiboar, Trail inspection) | no roll on the start or the last clue | matches |
| 7 | Herb count | 1-3, 2-4 with magic secateurs equipped or held | `random.of(1, 3) + secateurs` | matches |
| 8 | Herblore XP | 25 per herb after the first | `HERB_XP * (count - 1)` | matches |
| 9 | Herb by Herblore level | Herbiboar herb chance chart (e.g. ranarr 21/256 at 99) | `HerbiboarHerbsTable` low/high cascade; ranarr 1/256 at 31-32 against the chart's 0 (integer rounding) | matches |
| 10 | Confusion message | "The creature has successfully confused you with its tracks, leading you round in circles. You'll need to start again." | `confuse()` verbatim | matches |
| 11 | Muddy patch / smelly mushroom messages | "You find a partial footprint in the disturbed earth. Something has passed this way." / "This mushroom is oozing where it has recently been bitten. Something has passed this way." | `SPOT_MESSAGES` | matches |
| 12 | Seaweed / mushroom messages | not checked (pages not read) | "Something has disturbed this seaweed recently, it smells." / "The mushroom cap looks freshly eaten. Something has passed this way." | unsourced |
| 13 | New track before harvesting | The herbiboar disappears | `inspectStart` -> `reset()` clears `fossil_herbiboar_visible` | matches |
| 14 | Start points and tunnels | 5 start spots, 9 tunnel spawns (Herbiboar, Location) | 5 `START_LOCS`, 9 tunnels | matches |
| 15 | Route length and leg choice | not documented (only the "Guaranteed tracks" list) | 3-5 legs, random among unused legs from the group | unsourced |
| 16 | Guaranteed tracks | 11 cases, e.g. "A - approached from the south, always continues at the smelly mushroom" | not checked against `HerbiboarLegsTable` | unsourced |
| 17 | Tunnel ops | Attack, Search | both run `attackTunnel` | unsourced |
| 18 | Other messages | not documented | "You find some fresh tracks.", "Nothing seems to be out of place here.", "The creature's tracks lead into a nearby tunnel.", "You stun the herbiboar.", "You harvest herbs from the herbiboar, whereupon it escapes.", "You have harvested N Herbiboars." | unsourced |

### Divergences
None found.

### Unsourced
- Rows 4, 12, 15-18. The 50 XP may only come with the fossil roll ("Occasionally"), which needs a game check. Check each of the 11 "Guaranteed tracks" against the leg table (with only one outgoing unused leg from that group in that approach direction). The Search op and the non-wiki messages need a capture.
- Route length 3-5 (the wiki gives an average of 2.56 inspection rolls per herbiboar, which suggests ~4.5 clue locs per route, consistent with 3-5 legs plus first and last, but this is not a source).

### Not implemented (scope gaps)
- The new-track warning dialogue (declared; the toggle op is there), the Herbi pet 1/6,500 (declared), the horn of plenty's 10% chance of +1-3 herbs (not declared).

---

## 40 Hunters' Rumours

### Mechanics
| # | Mechanic | Official behaviour (source) | Patch behaviour | Verdict |
|---|---|---|---|---|
| 1 | Tiers | Novice 46 Gilman; Adept 57 Cervus/Ornus; Expert 72 Aco/Teco; Master 91 Wolf (Hunters' Rumours) | `HunterRumourHuntersTable` | matches |
| 2 | Level check | Unboostable | `assign()` uses `statBase` | matches |
| 3 | Per-hunter lists (implemented creatures) | Wagtail: Gilman. Grey chinchompa: Gilman, Cervus, Aco, Teco. Red chinchompa: all six. Herbiboar: Gilman, Teco, Wolf. Butterflies and moths, jerboa: see 41/42 (List of rumours) | `HUNTERS` bitmasks | matches |
| 4 | No duplicate across hunters | "Players cannot receive the same task at once from several guild masters" | `taken` filter | matches |
| 5 | Rates and pity | Bird snare 1/20, pity 40; box trap 1/50, 100; herbiboar 1/7, 14 (Drop rates) | RATE/PITY columns | matches |
| 6 | Held part blocks; pity keeps counting | A held part blocks any other; hunts still count toward pity | `onCatch` increments pity before the held-part check | matches |
| 7 | Full inventory | Rolled part dropped; pity part destroyed until there is space | `onCatch`: `invAddOrDrop` for a rolled part; returns without reset for a pity part | matches |
| 8 | Turn-in XP | `(HunterLvl + 5) * Modifier`, 50 novice/adept, 55 expert, 60 master | `turnInXp()` | matches |
| 9 | Loot sack | Basic / adept / expert / master by tier | `hg_lootsack_t0..t3` | matches |
| 10 | Rare part message | "You find a rare piece of the creature! You should take it back to the Hunter Guild." | `FOUND_MESSAGE` | matches |
| 11 | Whistle Rumour op | Op 3 "Rumour" (cache: Signal, Check, Rumour, Last-destination) | `onOpHeld3` | matches |
| 12 | Whistle message | "Your current rumour target is a grey chinchompa. You'll need to bring back some of its fur. Cervus was the source of the rumour." (Basic quetzal whistle) | `whistleMessage()` same template | matches |
| 13 | Quest requirements for assignment | Chinchompas and jerboas need Eagles' Peak; herbiboar needs Bone Voyage and 31 Herblore (List of rumours notes 2 and 6; Embertailed jerboa) | only the Hunter level is checked | diverges |
| 14 | Pity reset on new assignment | not documented | `assign()` sets pity to 0 | unsourced |
| 15 | Part wordings | Only "fur" for grey/red chinchompas is sourced (whistle example; Changes 27 Mar 2024) | wagtail "tail feathers", herbiboar "fur" | unsourced |
| 16 | When the part rolls for box traps and snares | not documented (catch vs Check) | on Check (`check()` -> `onCatch`) | unsourced |
| 17 | No-rumour whistle message | not documented | "You don't have a rumour to investigate at the moment." | unsourced |

### Divergences
#### D1. Rumours assigned without their quest requirements
- Severity: gameplay-visible
- Official: grey and red chinchompa and embertailed jerboa rumours need Eagles' Peak; herbiboar needs Bone Voyage and 31 Herblore (Hunters' Rumours, List of rumours notes; Embertailed jerboa).
- Patch: `assign()` filters only on the Hunter level.
- Fix: add a requirement column (quest varp/value, optional stat) to `hunter_rumours` and filter on it in `assign()`.
- Live tree (openrune.patch): same (`assign()` filters on the Hunter level only; no quest checks anywhere in the hunter module)

### Unsourced
- Rows 14-17. Also whether "HunterLvl" in the XP formula is the level before or after the turn-in XP (the wiki says "after completing a rumour"; the patch uses the level before adding the XP).

### Not implemented (scope gaps)
- Guild hunter outfit pity thresholds (38/94/12/76) (not declared). Master tier needs At First Light (declared in 42). Loot sacks don't open (declared). Milestone unlocks (whistle blueprints at 10/100/250, cooking unlocks, Apatura notes) (not declared). Rumours for creatures the server can't catch (declared).

---

## 41 Butterfly netting

### Mechanics
| # | Mechanic | Official behaviour (source) | Patch behaviour | Verdict |
|---|---|---|---|---|
| 1 | Levels and XP | Ruby harvest 15/24, sapphire glacialis 25/34, snowy knight 35/44, black warlock 45/54, sunlight moth 65/74, moonlight moth 75/84 | `HunterButterfliesTable` | matches |
| 2 | Barehanded | +10 levels | `BAREHANDED_EXTRA_LEVELS` | matches |
| 3 | Net must be wielded | "the player must be wielding either a butterfly net or a magic butterfly net" (Sapphire glacialis) | `player.righthand` | matches |
| 4 | Black warlock chart | 145/256 at 45 with a net; barehanded = magic net, 165/256 at 45 | BUTTERFLY 20/296 (net), 40/316 (bare, magic) | matches |
| 5 | Sunlight moth chart | Barehanded = net: 201/256 at 65; magic net 221/256 at 65 | SUNLIGHT 20/296, 20/296, 40/316 | matches |
| 6 | Moonlight moth chart | Barehanded = net: 209/256 at 75; magic 222/256 at 75 | MOONLIGHT 0/276, 0/276, 20/286 | matches |
| 7 | Ruby, sapphire, snowy curves | no chart on the wiki | copy the black warlock's chart | unsourced |
| 8 | Jar vs no jar | Jar keeps it; without one the effect applies at once | `catch()` | matches |
| 9 | Effects | Ruby +4+15% Attack; sapphire +4+15% Defence; black warlock +4+15% Strength; snowy knight heals 15; moonlight restores 22 Prayer; sunlight restores 6+20% of drained stats except Prayer and heals 8 HP | `EFFECT_*` rows | matches |
| 10 | Sunlight moth and Sailing | "The sunlight moth does not restore drained Sailing levels" (Sunlight Moth, bug note) | `RESTORED_STATS` includes `stat.sailing` | diverges |
| 11 | Sunlight moth catch run energy | Catching without a jar also restores 5% run energy (not the jarred moth) (Sunlight Moth) | not applied | diverges |
| 12 | Release | Op "Release" (op 4); the empty jar remains (Black warlock (item); cache) | `onOpHeld4`, `invReplace` to `butterfly_jar` | matches |
| 13 | Wing without a jar | Obtainable barehanded / without a jar (creature pages, note d1) | `onCatch` either way | matches |
| 14 | Rumours | Sapphire: Gilman; snowy: Gilman, Ornus; black warlock: Gilman, Cervus; sunlight from 75: Gilman, Cervus, Teco; moonlight from 85: Gilman, Aco, Wolf; 1/40, pity 80 (Hunters' Rumours, Changes 19 Aug 2026) | added rows | matches |
| 15 | Below the level | not documented | every swing fails silently | unsourced |
| 16 | Swing timing, respawn, messages | not documented | one 2-tick swing per click; 7-tick respawn; "You manage to catch the <kind>.", "You catch the <kind> and release it.", "You release the <kind>." | unsourced |
| 17 | Which moonlight moth npcs are catchable | "There are a few variants ... only one of which can be caught" (npcs 12771-12773) | `moth_moonlight` and `moth_moonlight_low_wander` | unsourced |

### Divergences
#### D1. Sunlight moth restores Sailing
- Severity: gameplay-visible (minor; replicates a known OSRS bug in reverse)
- Official: does not restore drained Sailing (Sunlight Moth, Bug note).
- Patch: `RESTORED_STATS` lists `stat.sailing`.
- Fix: drop `"sailing"` from `RESTORED_STATS`.
- Live tree (openrune.patch): same (`RESTORED_STATS` includes sailing)

#### D2. Sunlight moth catch without a jar gives no run energy
- Severity: gameplay-visible
- Official: catching restores 5% run energy; the jarred moth does not (Sunlight Moth).
- Patch: no run energy.
- Fix: in `catch()`, on the no-jar path for `moth_sunlight`, add 500 run energy (5% of 10,000) capped at 10,000.
- Live tree (openrune.patch): same (no run-energy restore in `ButterflyScript`)

### Unsourced
- Rows 7, 15-17: curves for ruby/sapphire/snowy (declared), the below-level behaviour and message, timing/respawn/messages (declared), which moonlight moth variant is catchable (check the cache ops of npcs 12771-12773: only the catchable one has "Catch").

### Not implemented (scope gaps)
- Sharing effects in multicombat (use on player, Accept Aid), butterfly mixes, the snowy knight's potion-timer behaviour (not declared).

---

## 42 Guild hunter dialogues and jerboas

### Mechanics
| # | Mechanic | Official behaviour (source) | Patch behaviour | Verdict |
|---|---|---|---|---|
| 1 | Gilman greeting and menu | "Greetings, fellow hunter. How can I be of help?"; food / rumours / "I'm good, thanks." (Transcript: Huntmaster Gilman) | `gilmanMenu()` | matches |
| 2 | Gilman food and drink | Tipple 3 coins, stew 20; no-space lines; "You buy a pint of Trapper's Tipple." / "You buy some stew." | `gilmanFood()`, `buy()` | matches |
| 3 | Not talked to Verity | Gilman: "Looks like you could do with talking to Verity first..."; others: "You're new here, aren't you?..."; Wolf: "My apologies, but I don't have time to chat right now." | `notVerity`, Wolf branch | matches |
| 4 | Assignment lines | 5 lines (Teco has a sixth); lines 1, 2, 3, 5 say "one of its [rare parts]", line 4 (and Teco 6) say "some of its" | all say "some of its" | diverges |
| 5 | Reminder | "You're hunting for me at the moment. This rumour was about a [creature]. You'll need to bring back some of its [rare parts]." (Gilman first: "Can you remind me which rumour I'm chasing right now?") | `rumourTalk()` | matches |
| 6 | Switch (Cervus to Wolf) | "I hear you're hunting for [Guild Hunter]. Aren't you looking for...", "Unless you fancy hunting for me instead?", "Switch to ...'s rumour?", per-hunter remembered line | `otherHuntersRumour()` and the `remember` lines (Cervus, Teco, Wolf checked) | matches |
| 7 | Gilman switch | With an old rumour: "I seem to remember ... Would you prefer that one, or a new one entirely?", three options; with none: not in the transcript | old-rumour branch as transcript; Yes/No when none (declared guess) | unsourced |
| 8 | Hand-in lines | Talk: per-hunter "...Here's your reward." + "another" line; Quick-Rumour: "Thanks for that. I'll mark off that report for you. Would you like another rumour?" | `handIn()` (Gilman, Cervus, Teco, Wolf checked) | matches |
| 9 | "No." to "Get another rumour?" | Shows the initial options | dialogue ends | diverges |
| 10 | Wolf's leave option | Option "Never mind."; the player says "See you later." | option text "See you later." | diverges |
| 11 | Wolf below 91 | "There's an extra seat at this table..." then "You need 91 Hunter to take on tier 4 Hunters' Rumours." | same | matches |
| 12 | Other hunters below level | not in the transcripts | mesbox "You need N Hunter to take on tier T Hunters' Rumours." (declared guess) | unsourced |
| 13 | Nothing to offer | not in the transcripts | "I don't have any rumours for you at the moment." | unsourced |
| 14 | Verity first talk, standard, count | Transcript: Guild Scribe Verity | `verity()`, `verityMenu()` verbatim | matches |
| 15 | Verity Rumour-settings | "filter the rumour information a little more/less", "Disable/Enable back-to-back rumours?", "Repeat rumours will no longer/now be offered." | `veritySettings()` | matches |
| 16 | Back-to-back semantics | Toggle exists (Changes 27 Mar / 3 Apr 2024); exact rule not documented | off = the last handed-in rumour is not offered | unsourced |
| 17 | Embertailed jerboa trap | 39, 137 XP, jerboa tail, "...or maybe not." (Embertailed jerboa) | trap row + message | matches |
| 18 | Jerboa rumour | Gilman, Ornus; 1/50, pity 100; large jerboa tail | rumour row | matches |
| 19 | "Red chinchompa" rumour name | List of rumours: "Red chinchompa"; fur (Changes 27 Mar 2024) | name changed | matches |
| 20 | [Guild Hunter] name in switch lines | placeholder only | short name ("Gilman", "Cervus") | unsourced |

### Divergences
#### D1. "one of its" lines all say "some of its"
- Severity: cosmetic
- Official: assignment lines 1, 2, 3, 5 and the Gilman "I seem to remember..." / "That was it..." lines use "one of its [rare parts]" (transcripts).
- Patch: `parts()` always returns "some of its <part>" (declared).
- Fix: add a singular part column (e.g. "tailfeather", "tuft", "wing", "tail") and use `"one of its ${partSingular}"` in those templates.
- Live tree (openrune.patch): same (`parts()` returns "some of its ...")

#### D2. Declining another rumour ends the dialogue
- Severity: cosmetic
- Official: "No." returns to the hunter's initial options (all transcripts).
- Patch: `handIn()` ends.
- Fix: after a "No." call `talk(hunter)`'s option menu (for Gilman, `gilmanMenu`) instead of returning.
- Live tree (openrune.patch): same (`handIn()` ends after "No.")

#### D3. Wolf's leave option label
- Severity: cosmetic
- Official: option "Never mind.", spoken "See you later." (Transcript: Guild Hunter Wolf).
- Patch: `leaveOption = "See you later."` is used for both.
- Fix: split into `leaveOption = "Never mind."` and a `leaveLine = "See you later."` for Wolf.
- Live tree (openrune.patch): same (Wolf `leaveOption = "See you later."`)

### Unsourced
- Rows 7, 12, 13, 16, 20 (rows 7 and 12 are declared). Jerboa catch chance (uses 35's shifted curve).

### Not implemented (scope gaps)
- Verity's spare hunter gear / blueprint trade, the "intel reshuffle" lines after Gilman's brand new rumour, Wolf's At First Light requirement (all declared). Wolf's moonlight antelope special line (the creature is not catchable here).
