# Farming patches (content/farming, patch 19, unpushed)

Audited 2026-10-18 against the OSRS Wiki (pages: Farming (Disease, payment, compost, and watering;
Tool Leprechaun; Magic secateurs; Mechanics: Growth timing, Variable crop yield; Changes), Allotment
patch, Allotment patch/Seeds, Flower patch, Flower patch/Seeds, Herb patch (Bonuses, Changes), Herb
patch/Seeds, every planted seed's page (Potato ... Snape grass seed, Guam ... Torstol seed: "Average
harvest yield" and the `Farming yield calculator` low/high constants), Potato plant, Guam leaf
(plant), Marigold, Limpwurt plant (options, Changes), Weeds, Rake, Seed dibber, Disease (Farming)).
Secondary evidence: RuneLite Time Tracking `PatchImplementation` / `FarmingWorld`
(re/rlsrc/.../timetracking/farming) for the `farming_transmit` values and areas, and the hub Did I
Compost plugin (re/hub-src/did-i-compost) for message formats. No farming transcript exists; most
messages are not on the wiki. Report only. File lines below are lines of
`content/skills/farming/src/main/kotlin/.../FarmingScript.kt` (`FarmingScript.kt:N`) and
`pack/.../FarmingCropsTable.kt` (`FarmingCropsTable.kt:N`).

## Summary
The numbers are right: all 28 crops' level, plant/harvest XP, growth cycles and stage counts, the
`farming_transmit` values (RuneLite's decoder agrees with every one), the 3 harvest lives, the
chance-to-save formula and constants for every herb and allotment, and the limpwurt formula all
match. 30 mechanics checked: 22 match, 4 diverge (1 gameplay-visible, 3 cosmetic), 3 unsourced
(messages, timings, loc numbering), 1 scope row (weed regrowth). The fixes worth making: the
per-player growth-tick offset (D1), the missing "The soil has not been treated." sentence in Inspect
(D2, which compost-tracking plugins parse), the depleting harvest appearance (D3) and the
wrong-patch message (D4). All 4 divergences are the same in the live tree
(openrune.patch carries identical farming sources). The large gaps (disease, compost, magic
secateurs, payments, leprechaun) are scope; secateurs, cape and diary yield boosts are not declared.

## Mechanics
| # | Mechanic | Official behaviour (source) | Patch behaviour (file:line or function) | Verdict |
|---|---|---|---|---|
| 1 | Growth cycle length | Flowers 5 min, allotments 10, herbs 20 (Farming: Growth timing table) | `PatchType` cycleMinutes 10/5/20 (`FarmingScript.kt:225-228`) | matches |
| 2 | Tick alignment | Growth ticks on real-clock boundaries (GMT :00/:05..., :00/:10..., :00/:20/:40); a newly planted seed's first stage is shorter (Growth timing) | `grow()` counts `now/rate - last/rate` on UTC epoch minutes (`FarmingScript.kt:195-206`) | matches |
| 3 | Per-player offset | Every growth tick is shifted by a per-player offset of 0-31 minutes earlier, persistent across logins (Growth timing, Mod Ash 30 Mar 2019 / 16 Nov 2022) | No offset | diverges (D1) |
| 4 | Growth while offline | Crops keep growing while logged out (Farming intro) | Lazy catch-up from the stored minute on every read (`value()`/`grow()`) | matches |
| 5 | Stage counts | Allotments: potato/onion/cabbage/tomato 4x10, sweetcorn/strawberry 6x10, watermelon 8x10, snape grass 7x10; flowers 4x5; herbs 4x20 (Allotment/Flower/Herb patch/Seeds) | `stages` per row (`FarmingCropsTable.kt:79-106`; herbs/flowers fixed 4) | matches |
| 6 | Levels | Potato 1, onion 5, cabbage 7, tomato 12, sweetcorn 20, strawberry 31, watermelon 47, snape 61; marigold 2, rosemary 11, nasturtium 24, woad 25, limpwurt 26, white lily 58; guam 9 ... torstol 85 (Seeds subpages) | Same (`FarmingCropsTable.kt:79-106`) | matches |
| 7 | Allotment XP | Plant/harvest: 8/9, 9.5/10.5, 10/11.5, 12.5/14, 17/19, 26/29, 48.5/54.5, 82/82 (Allotment patch/Seeds) | Same in tenths | matches |
| 8 | Flower XP | 8.5/47, 12/66.5, 19.5/111, 20.5/115.5, 21.5/120, 42/250, both given once when the patch is cleared (Flower patch/Seeds footnote) | Same; `empty(harvestXp = true)` gives plant+harvest once (`FarmingScript.kt:120-126`) | matches |
| 9 | Herb XP | 11/12.5 ... 199.5/224.5 for the 14 herbs (Herb patch/Seeds) | Same in tenths | matches |
| 10 | When XP is given | Harvest XP per crop; plant XP "upon fully harvesting the patch" (Allotment/Herb Seeds footnotes) | Harvest XP per item; plant XP in `empty()` after the last life | matches |
| 11 | transmit values | Weeds 0-3; potato 6-9 growing, 10-12 harvestable ... snape 63-69, 138-140; flowers 8-11/12 ... white lily 37-40/41; herbs 4-7/8-10 ... torstol 103-106/107-109 (RuneLite PatchImplementation; cache multilocs) | `plantValue`, `stages`, `harvestValue` per row; herbs/flowers `plantValue+4` | matches |
| 12 | transmit varbits/areas | a/b allotments, c flower, d herb, e compost bin (4771-4775), RuneLite FarmingWorld regions incl. Catherby/Falador bounds | `TRANSMIT_VARBITS` a-e, `AREAS` copy FarmingWorld (`FarmingScript.kt:265-298`); e always 0 | matches |
| 13 | Harvest appearance | Harvestable crops show depleting states (values 10/11/12 etc.); Limpwurt plant Changes 23 Aug 2018 says only limpwurt/bushes stopped depleting | Value stays at the first harvest value until empty (`harvestOnce`, `FarmingScript.kt:114`) | diverges (D3) |
| 14 | Weeds / raking | Up to 3 weeds per patch, 4 XP each, rake needed (Weeds; Rake) | `rakeOnce` +1 weed, 4.0 XP, 0->3 (`FarmingScript.kt:73-80`) | matches |
| 15 | Weed regrowth | Weeds grow back in any empty patch (Weeds) | Patch stays at 3 forever | not implemented (declared) |
| 16 | Seeds per patch | 3 for allotments, 1 for flowers and herbs (Allotment patch; Flower; Herb) | `ALLOTMENT_SEEDS = 3`, else 1 | matches |
| 17 | Tools | Seed dibber to plant; spade to harvest allotments and herbs; flowers picked by hand (Seed dibber; Herb patch; Potato plant "Harvest", Marigold "Pick") | `DIBBER`, `SPADE` check skipped for flowers | matches |
| 18 | Level check with boosts | Boosts count at planting (Farming: Temporary boosts) | `stat(FARMING)` (current level) | matches |
| 19 | Harvest lives | 3 lives, +1/+2/+3 for compost/super/ultra (Variable crop yield, Mod Ash 30 Aug 2018) | `HARVEST_LIVES = 3`, no compost | matches (compost is scope) |
| 20 | Chance to save | `(1 + floor(low*(99-F)/98 + high*(F-1)/98 + 0.5))/256` (Variable crop yield) | `saveChance()` same, F clamped 1..99 (`FarmingScript.kt:314-319`) | matches |
| 21 | CTS constants | Allotments low 101/105/107/112/88/103/126 high 180, snape 148/195; herbs 25,28,31,36,39,43,46,50,54,57,60,64,67,71 high 80 (seed pages' yield calculators) | Same (`FarmingCropsTable.kt:79-106`) | matches |
| 22 | Limpwurt yield | 3 + random(0..stat-1)/10, boosts count (Variable crop yield; Limpwurt plant, Mod Ash 13 Oct 2020) | `flowerYield`: 3 + `nextInt(level)/10` | matches |
| 23 | Other flower yields | Woad 3 leaves, others 1 (Flower patch/Seeds footnote) | `fixedYield` 3 for woad, 1 others | matches |
| 24 | Inspect text | "This is an allotment. The soil has not been treated. ..." (Did I Compost `INSPECT_PATCH_NONE` regex `This is an? .+\. The soil has not been treated.*`) | "This is an allotment. The patch needs weeding." (`FarmingScript.kt:166-177`) | diverges (D2) |
| 25 | Clear messages | "The allotment is now empty." / "The herb patch is now empty." (Did I Compost CLEAR_ALLOTMENT/CLEAR_HERB) | `"The ${patchName} is now empty."` (`FarmingScript.kt:124`) | matches (flower variant unsourced) |
| 26 | Ops on growing/grown crops | Growing: Inspect, Guide; grown: Harvest (allotment) / Pick (flower, herb), Inspect, Guide (Potato plant, Guam leaf (plant), Marigold infoboxes) | op1 rake/harvest, op2 inspect; no Guide (op3) | matches (Guide is scope) |
| 27 | Planting/harvest messages | Not on the wiki | See Unsourced | unsourced |
| 28 | Action timings | Not documented beyond "harvesting, waiting 2 game ticks, and clicking again ... harvest 2 crops per animation" (Allotment patch; Herb patch) | Rake 3 ticks/weed, harvest 2 ticks/crop, plant 3 ticks | unsourced |
| 29 | Patch locs per farm | Six farms' allotment/flower/herb patches (Farming/Patch locations) | `farming_veg_patch_*`/`flower`/`herb` numbers per area (`FarmingScript.kt:283-298`) | unsourced (loc numbering not checkable with the cache tools; device-verified per README) |
| 30 | Wrong-patch message | "You can only plant grape seeds in a vine patch." is the modern wrong-patch form (Grape seed, Changes) | "You can't plant a <seed> in this patch." (`FarmingScript.kt:147`) | diverges (D4) |

## Divergences
### D1. No per-player growth-tick offset
- Severity: gameplay-visible
- Official: Farming, Mechanics, Growth timing: "there is an offset of up to 30 minutes which is
  different per player, and persists through logins ... This offset is always negative" (0-31
  minutes, Mod Ash 16 Nov 2022). Every player's crops grow at the table times minus their offset.
- Patch: every player grows exactly on the UTC boundaries (`grow()`, `nowMinutes()`).
- Fix: store a per-account offset (a server varp, `random.of(32)` minutes, set once on first use)
  and use `(nowMinutes() + offset) / rate - (last + offset) / rate` in `grow()`. Note: the official
  table is described per cycle length; with a single offset per player all cycle lengths shift
  together, which matches the wiki's "apply the difference to every time in the table".
- Live tree (openrune.patch): same (`FarmingScript.kt` and `FarmingCropsTable.kt` there are byte-identical to the patch).

### D2. Inspect omits the compost sentence
- Severity: cosmetic (but plugin-visible: compost trackers such as Did I Compost match
  `This is an? .+\. The soil has not been treated.*`)
- Official: "This is an allotment. The soil has not been treated. <state>" (hub plugin
  `INSPECT_PATCH_NONE`; the treated form is "The soil has been treated with (super|ultra)compost.").
- Patch: `mes("This is ${article} ${patchName}. $text")` with no soil sentence.
- Fix: insert "The soil has not been treated. " between the two parts (and the treated variants
  once compost exists). The state sentences themselves ("The patch needs weeding.", "The patch is
  empty and weeded.", "The patch is fully grown.", "The patch has something growing in it.") remain
  unsourced.
- Live tree (openrune.patch): same (`FarmingScript.kt` and `FarmingCropsTable.kt` there are byte-identical to the patch).

### D3. Harvested patches do not deplete visually
- Severity: cosmetic
- Official: harvestable allotments and herbs have three harvest values (potato 10-12, guam 8-10,
  snape 138-140; RuneLite PatchImplementation) and the plant model depletes as lives are used; the
  Limpwurt plant Changes (23 August 2018) note that only limpwurt/bushes/nightshade stopped
  depleting.
- Patch: `setState(player, patch, crop.harvestValue, lives)` keeps the first value until the patch
  empties.
- Fix: map remaining lives to `harvestValue + (3 - min(lives, 3))` (or the reverse direction if the
  cache multiloc shows otherwise; confirm the order from the multiloc's child models), and accept
  the whole range in `cropAt()`/`op1`.
- Live tree (openrune.patch): same (`FarmingScript.kt` and `FarmingCropsTable.kt` there are byte-identical to the patch).

### D4. Wrong-patch planting message
- Severity: cosmetic
- Official: Grape seed, Changes: planting in the wrong patch type now says "You can only plant grape
  seeds in a vine patch." (form: "You can only plant <seeds> in a/an <patch type>."). Generic seeds'
  wording is not quoted on the wiki, so this is the closest sourced form.
- Patch: "You can't plant a potato seed in this patch."
- Fix: "You can only plant ${seedName}s in ${article} ${patchType}." using the seed's patch type
  (e.g. "You can only plant guam seeds in a herb patch."); verify against a capture if possible.
- Live tree (openrune.patch): same (`FarmingScript.kt` and `FarmingCropsTable.kt` there are byte-identical to the patch).

## Unsourced
- Every planting message: "This patch needs weeding first.", "You can only plant <seed>s in an empty
  patch.", "You need a seed dibber to plant seeds.", "You must be a level N Farmer to plant those.",
  "You need 3 <seed>s to grow those.", "You plant 3 potato seeds in the allotment." / "You plant a
  guam seed in the herb patch." (`FarmingScript.kt:147-159`). They read like OSRS but no wiki
  page or plugin quotes them; a packet capture or Transcript would settle them.
- "You need a rake to weed a farming patch.", "You need a spade to do that.", "You don't have
  enough inventory space to do that." (`FarmingScript.kt:65,85,89`; the last is OSRS's generic
  wording, e.g. Sled (scenery), not confirmed for patches).
- "The flower patch is now empty." for flowers (allotment/herb forms are confirmed; no source for
  flowers, which may instead say what was picked).
- Inspect state sentences (see D2).
- Timings: rake 3 ticks per weed with auto-repeat, harvest 2 ticks per crop, planting 3 ticks;
  animations `farming_raking`, `farming_seed_dibbing`, `human_dig`, `picking_low`. Needs a capture.
- `farming_transmit` refresh every 5 ticks while in an area (official sends on entering the zone;
  a delay of up to 3 seconds before patches appear is our choice).
- Loc numbering per farm (`farming_veg_patch_3/4` for Catherby, etc.): needs a loc-cache check.

## Not implemented (scope gaps)
- Disease (chance each cycle, diseased/dead states, plant cure, Cure Plant, clearing dead plants
  with "You have successfully cleared this patch for new crops."): declared.
- Compost/supercompost/ultracompost (extra lives and disease reduction), watering, gardener
  protection payments, weed regrowth, tool leprechaun: declared.
- Magic secateurs (+10% CTS), Farming/Max cape (+5% herbs), Kandarin diary (Catherby herb +10/+17/
  +25), Kourend & Kebos hard (Hosidius herb +10), attas plant, Falador medium diary herb XP +10%:
  **not declared**.
- Digging up a growing crop with a spade, the Guide op, scarecrows, flower protection of allotments:
  not declared (code comment mentions digging only).
- Huasca (65, Varlamore) and goutweed herbs, other farms (Farming Guild, Harmony Island, Trollheim,
  Weiss, Kastori), amulet of bounty, herb sack / reagent pouch auto-store, amulet of nature,
  Tangleroot pet rolls on harvest: not declared.
- Doc note (not behaviour): the PR text says dbtable 56001, varps 63714-63761, timer 435 and
  `farming_transmit_a-d`; README row 19 says dbtable 60002, varps 63780-63827, timer 460. The patch
  itself uses 56001/63714-63761/435 and writes a-e.
