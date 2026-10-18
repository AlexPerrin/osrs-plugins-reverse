# Jewellery teleports (content/jewellery, patch 44, 45, unpushed)

Audited 2026-10-18 against the OSRS Wiki (pages: Games necklace, Ring of dueling, Amulet of glory, Skills
necklace, Combat bracelet, Wilderness § Teleport restrictions, Corporeal Beast's Lair, Corporeal Beast spawn
table). Cache (LIVE obj 3853, 2552, 11105, 11118: `interfaceOptions`, worn-op params 451-456) checked for
ops and destination order. Secondary evidence: RuneLite `worldmap/TeleportLocationData.java`, the Shortest
Path hub plugin's `transports/teleportation_items.tsv` (tiles, durations, requirement varps), the Supplies
Tracker hub plugin (`SuppliesTrackerPlugin.onChatMessage`, charge-message prefixes). Report only.

File refs: `S` = `JewelleryTeleportScript.kt`, `T` = `JewelleryTeleportTables.kt` (line numbers of the
files as created by patch 44; 45 changes `useCharge`).

## Summary
Mostly right: destination names, menu/worn-op order, charge counts, crumble/uncharged behaviour, the games
necklace and ring of dueling messages, the 20/30 Wilderness split and the in-place charge swap (45) match.
22 tiles agree with Shortest Path to the tile. 4 divergences: 2 gameplay-visible (the Corporeal Beast tile is
128 tiles south of the lair, in the wrong map square; the Farming Guild teleport ignores the 45 Farming
inside-the-guild rule) and 2 cosmetic (the glory's "Your amulet of glory has ..." should be "Your amulet
has ...", and the skills necklace / combat bracelet last-charge lines say "necklace's" / "bracelet's"
instead of "skills necklace's" / "combat bracelet's"). Fix the Corp tile first. Live tree (openrune.patch): all 4 divergences are present there too (same code).

## Mechanics
| # | Mechanic | Official behaviour (source) | Patch behaviour (file:line or function) | Verdict |
|---|---|---|---|---|
| 1 | Rub op | Inventory ops `[null, Wear, null, Rub, Drop]`: Rub is op 4 (cache obj 3853/2552/11105/11118) | `onOpHeld4` (S:46) | matches |
| 2 | Worn destination ops | Worn params 451.. = destinations after Remove (cache) | i=0 → `onOpWorn2` … (S:47-56) | matches |
| 3 | Destination names / order | Games: Burthorpe, Barbarian Outpost, Corporeal Beast, Tears of Guthix, Wintertodt Camp; Dueling: Emir's Arena, Castle Wars, Ferox Enclave, Fortis Colosseum; Glory: Edgeville, Karamja, Draynor Village, Al Kharid; Skills: Fishing, Mining, Crafting, Cooking, Woodcutting, Farming Guild; Combat: Warriors' Guild, Champions' Guild, Monastery, Ranging Guild (wiki infobox worn options; cache params) | T:98-120 | matches |
| 4 | Charges | Games / dueling 8, glory / skills / combat up to 6 (wiki) | `max` 8/8/6/6/6 (T:46-50) | matches |
| 5 | Last games / dueling charge | Item destroyed, "Your games necklace crumbles to dust." / "Your ring of dueling crumbles to dust." (wiki intro of each page) | `invDel`, "Your ${name} crumbles to dust." (S:101) | matches |
| 6 | Last glory / skills / combat charge | Leaves the uncharged item (wiki infobox "Uncharged" version) | `spent` obj (T:48-50) | matches |
| 7 | Rub line | "You rub the necklace..." (Games necklace), "You rub the ring..." (Ring of dueling) | "You rub the ${rubWord}..." (S:97) | matches (games, dueling); unsourced (amulet / bracelet / skills necklace) |
| 8 | Games / dueling uses line | "Your games necklace has three uses left." / "Your ring of dueling has four uses left." (wiki) | "Your ${name} has ${words} use(s) left." (S:104) | matches |
| 9 | Glory charges line | Prefix "Your amulet has" (Supplies Tracker; no wiki source) | "Your amulet of glory has three charges left." (S:103) | diverges (secondary evidence) |
| 10 | Skills / combat charges line | Prefix "Your skills necklace has" / "Your combat bracelet has" (Supplies Tracker) | "Your skills necklace has … charges left." (S:103) | matches (prefix; "charges left" suffix unsourced) |
| 11 | Glory last charge | "…your amulet's last charge" (Supplies Tracker) | "You use your amulet's last charge." (S:102) | matches (secondary) |
| 12 | Skills / combat last charge | "…your skills necklace's last charge" / "…your combat bracelet's last charge" (Supplies Tracker) | "You use your necklace's last charge." / "…bracelet's…" (S:102 uses `rubWord`) | diverges (secondary evidence) |
| 13 | Message colour | not sourced | `<col=7f007f>` (S:106) | unsourced |
| 14 | Wilderness limit, games / dueling | Up to level 20 (Wilderness § Teleport restrictions; Shortest Path "20") | `TeleportType.Standard` (S:75) | matches |
| 15 | Wilderness limit, glory / skills / combat | Members up to level 30 (Wilderness § Member teleportation up to level 30) | `MemberLevel30` → 30 if members (S:75 + `WildernessTeleportHook`) | matches |
| 16 | Teleblock | Blocks nearly all teleports for 5 min (2.5 with Protect from Magic) (Wilderness § Teleport restrictions) | Via `WildernessTeleportHook`, which only checks `varbit.teleblock_cycles` while inside the Wilderness | matches inside the Wilderness; upstream hook gap outside (see Not implemented) |
| 17 | Tiles: Burthorpe 2899,3553; BA 2520,3571; Wintertodt 1631,3940; Emir's 3315,3235; Castle Wars 2440,3090; Ferox 3151,3635; Colosseum 1793,3107; glory 3087,3496 / 2918,3176 / 3105,3251 / 3293,3163; skills 2611,3390 / 3049,9763 / 2933,3295 / 3144,3438 / 1662,3505; combat 2882,3547 / 3192,3368 / 3052,3490 / 2653,3439 | Shortest Path `teleportation_items.tsv` (same tiles); RuneLite worldmap within 1-3 tiles | T:98-120 | matches (secondary) |
| 18 | Tears of Guthix tile | 3245,9500 plane 2 (Shortest Path) | 3245,9500,2 (T:101) | matches (secondary; the patch called plane 2 a guess) |
| 19 | Corporeal Beast tile | Lair is on plane 2 (wiki spawn table); tile 2964,4382,2 (Shortest Path), 2967,4384 (RuneLite worldmap), i.e. map square 46,68 (region 11844) | 2967,**4254**,2 (T:100), map square 46,66 | diverges |
| 20 | Farming Guild tile | "takes players inside the guild if their Farming level is at least 45, otherwise … outside" (Skills necklace § Teleportation options, note a); Shortest Path: 1248,3719 and 1248,3725 with 45 Farming | Always 1248,3719 (T:116) | diverges |
| 21 | Teleport animation / graphic | not on the item pages | seq 714, spotanim 111 height 92, `synth.teleport_all` (S:83-85), same as the server's standard spell teleport start | unsourced |
| 22 | Teleport delay | Shortest Path duration 4 ticks for every jewellery teleport | `delay(4)` then `telejump` (S:86-87) | matches (secondary) |
| 23 | Arrival animation | not sourced; the server's own standard teleport plays seq 715 on arrival (`SpellTeleportScript.TeleportStyle.Standard.endAnim`) | `resetAnim()` (S:88), no 715 | unsourced |
| 24 | Rub menu title / hotkeys | not sourced | "Where would you like to teleport to?" via 187/script 217 (S:63, S:118) | unsourced |
| 25 | Charge swap keeps slot (45) | A worn necklace stays worn; inventory item stays in its slot (normal OSRS item replace) | `invReplaceSlot(from, slot, …)` | matches |

## Divergences

### D1. Corporeal Beast teleport lands 128 tiles south of the lair
- Severity: gameplay-visible
- Official: the lair is on plane 2 (Corporeal Beast spawn table); the games necklace tile is 2964,4382,2
  (Shortest Path `teleportation_items.tsv`, line "Games necklace: Corporeal Beast"), RuneLite's world map
  marks 2967,4384. Both are in map square 46,68 (region 11844, y 4352-4415), the lair's square.
- Patch: `CoordGrid(2967, 4254, 2)` (T:100) is in map square 46,66, a different map square (probably the wiki
  map's display coordinates, which are offset for that map id). The player lands outside the lair (void or
  "Invalid teleport!" if the zone has no collision).
- Fix: `Dest("games_corporeal_beast", GAMES, "Corporeal Beast", CoordGrid(2964, 4382, 2))`; check the tile
  in game on the server's collision map.
- Live tree (openrune.patch): same (`CoordGrid(2967, 4254, 2)` in its `JewelleryTeleportTables.kt`)

### D2. Farming Guild teleport ignores the 45 Farming rule
- Severity: gameplay-visible
- Official: Skills necklace § Teleportation options, note a: "The teleport takes players inside the guild if
  their Farming level is at least 45, otherwise players are teleported outside the guild". Shortest Path has
  1248,3719 (no requirement) and 1248,3725 (45 Farming).
- Patch: always 1248,3719 (T:116).
- Fix: in `teleport`, when `dest` is `dbrow.jewellery_teleport_dest_skills_farming_guild` and
  `player.baseFarmingLvl >= 45` (base level; the wiki does not say whether boosts count, so check boosts are
  allowed before using the current level) use `CoordGrid(1248, 3725, 0)`; or add an optional
  `alt_coords` + `alt_farming_level` column to the dests table.
- Live tree (openrune.patch): same (always `CoordGrid(1248, 3719, 0)`)

### D3. Glory charge line names the item in full
- Severity: cosmetic (but plugin-visible: Supplies Tracker matches on the prefix)
- Official: "Your amulet has …" (Supplies Tracker hub plugin, `onChatMessage`: `contains("your amulet has")`
  for the glory). No wiki source for the full line.
- Patch: "Your amulet of glory has three charges left." (S:103, `name` = "amulet of glory").
- Fix: add a `charge_name` column (glory "amulet", others = `name`) and use it in the "has … left" line.
- Live tree (openrune.patch): same (glory `name` "amulet of glory" in the "has … left" line)

### D4. Skills necklace / combat bracelet last-charge line uses the short noun
- Severity: cosmetic (plugin-visible)
- Official: "…your skills necklace's last charge" and "…your combat bracelet's last charge" (Supplies
  Tracker); the glory's is "…your amulet's last charge".
- Patch: "You use your ${rubWord}'s last charge." (S:102) gives "necklace's" / "bracelet's".
- Fix: use the same `charge_name` column as D3 ("amulet" for glory, "skills necklace", "combat bracelet").
- Live tree (openrune.patch): same (`"You use your ${item.rubWord}'s last charge."`)

## Unsourced
- The "You rub the amulet..." / "You rub the bracelet..." / "You rub the necklace..." line for the glory,
  combat bracelet and skills necklace (wiki only quotes the games necklace and ring lines). Needs a
  screenshot or chat transcript.
- The "… charges left." suffix and the leading "You use" of the last-charge line for the dragonstone items
  (the plugin only matches substrings).
- Colour `7f007f` for the charge line, and the chat type (normal game message).
- Menu title "Where would you like to teleport to?" and whether the dragonstone items use the same menu.
- Animation 714 / spotanim 111 (height 92) / sound `teleport_all`, and no arrival animation 715 (the server's
  own standard spell teleport plays 715 on arrival). Needs a capture of a real jewellery teleport.
- The charge is taken when the teleport starts (before the delay), not on arrival.
- Single tiles; the wiki maps show a landing area (the patch states it uses no radius).

## Not implemented (scope gaps)
- Destination requirements: Tears of Guthix needs the quest (Shortest Path `451>1`), Wintertodt needs a
  Zeah visit (`5619>0`), Fortis Colosseum needs the Hero title (`11410>8`). Declared in the PR text.
- Landing radius. Declared.
- Uncharged glory / skills / combat Rub (official: a "needs recharging" style message; no source quoted here)
  and recharging (Fountain of Heroes / Uhld / Rune, Legends' Guild totem, Recharge Dragonstone). Not declared.
- Amulet of glory (t), amulet of eternal glory, ring of wealth, slayer ring, necklace of passage, burning
  amulet, digsite pendant: other teleport jewellery. Not declared (the PR names its five families).
- Teleblock outside the Wilderness: `WildernessTeleportHook` returns early when the player is not in the
  Wilderness, so a teleblocked player who walks out can teleport. Upstream hook behaviour, not this patch.
- Combat bracelet Slayer count every 10 kills, glory gem-mining bonus, skills necklace casket bonus: not
  teleports, out of scope.
