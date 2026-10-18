# Achievement diaries (content/diaries, patch 47, 49, 53, unpushed)

Audited 2026-10-18 against the OSRS Wiki (pages: Achievement Diary, Lumbridge & Draynor Diary, Varrock Diary,
Ardougne Diary, Desert Diary, Falador Diary, Fremennik Diary, Kandarin Diary, Karamja Diary, Kourend & Kebos
Diary, Morytania Diary, Western Provinces Diary, Wilderness Diary: task tables and Changes). Secondary evidence:
RuneLite's Diary Requirements plugin (`re/rlsrc/.../achievementdiary/diaries/*DiaryRequirement.java`, task
texts) and the rsprox captures written up by another server project in this repo
(`re/tsps/docs/achievement-diaries.md` and `re/tsps/server/plugins/achievements/diaries/data/achievement-diaries.json`:
the captured journal of all 12 diaries line by line, the task-complete message and the tier-complete
sequence). Gamevals (varbits 4453, 4495, 6311, 6320, 4480; varps 1194/1195) checked. Report only.

File refs: `Tables` = `pack/.../AchievementDiaryTables.kt`, `Script` = `AchievementDiaryScript.kt`,
`Tasks` = `DiaryTasks.kt`, `Hooks` = `DiaryHooks.kt` (as they stand after 53).

## Summary
The data is good: all 12 areas, 492 tasks (the wiki's total), every tier's count and every task's place match
the wiki and the captured journals; 475 of 492 task lines are word-for-word the captured in-game text. The
client varbits (count, started, complete) are the real ones and are driven the way the captures show. 6
divergences: 1 gameplay-visible (picking a kitten colour through Gertrude's quick-buy op or the other Charos
path does not complete Varrock medium 3), 5 cosmetic (17 task lines worded from the wiki/plugin instead of
the game; journal title, header and tier-heading colours/layout; missing journal scripts 6844/2524 and
pagepos; tab OP2 not enabled; tier-complete varbit set at once instead of 3 ticks later). Hans's Talk-to path
not counting is listed under Unsourced. Most important fix: the task-complete chat message, which the PR calls unknown, is captured:
`<col=dc143c>Well done! You have completed an easy task in the <area> area. Your Achievement Diary has been
updated.</col>` (see Not implemented). Live tree (openrune.patch): all 6 divergences are present there too.

## Mechanics
| # | Mechanic | Official behaviour (source) | Patch behaviour (file:line or function) | Verdict |
|---|---|---|---|---|
| 1 | Opening a journal | Op1 on a row of 259:2 (slot = the diary's index in enum 595) opens 741 (captures, tsps doc "The task list") | `onIfOverlayButton(TASKBOX)` Op1 → `openJournal(comsub)` | matches |
| 2 | Tab row ops | OP1 and OP2 set on 259:2 slots 0-11 (OP2 is the client's "Wiki") (captures) | `ifSetEvents(TASKBOX, 0 until 12, IfEvent.Op1)` | diverges (D4) |
| 3 | Area indices | enum 595 order: Karamja 0, Ardougne 1, Falador 2, Fremennik 3, Kandarin 4, Desert 5, Lumbridge 6, Morytania 7, Varrock 8, Wilderness 9, Western 10, Kourend 11 (captured `index`) | `AchievementDiariesTable` consts | matches |
| 4 | First journal line | "Lumbridge & Draynor Tasks", "Varrock Tasks", "Ardougne Area Tasks", … (captured `header`, all 12) | `DIARIES` headings | matches (text); colour diverges (D2) |
| 5 | Journal title | `<col=800000>Achievement Diary - <diary name>` on 741:2 (tsps `Journal.open`, captured) | "Achievement Diary" (Script `TITLE`) | diverges (D2) |
| 6 | Tier heading / spacing | header `<col=ffff00>`, then " "; per tier `<col=ffff00>Easy` (`00ff00` once the tier is done), `<col=000000>`, " ", tasks, " " (captured layout; Karamja/Kourend use "" instead of " ") | "", `<col=800000>Easy</col>`, tasks (Script `journalLines`) | diverges (D2) |
| 7 | Task lines | open `<col=000000>text`, done `<str>text` (captured) | open plain, done `<str>text</str>` | matches visually (colour: see D2) |
| 8 | Journal scripts | pagepos varp 334 = line count, busy varbit 12393 = 1, script 6844, lines, script 2524, open 741 as main modal, script 6845(1, lines) (captures) | `ifOpenMain`, texts, script 6845(1, n) only | diverges (D3) |
| 9 | Task lists, all 12 areas | 492 tasks; tier counts per wiki diary page (e.g. Lumbridge 12/12/11/6, Varrock 14/13/10/5, Karamja 10/19/10/5) | Tables, 492 rows, same counts and order | matches |
| 10 | Task wording | captured journal text | 475/492 identical; 17 differ (D1) | diverges (D1) |
| 11 | Line wrapping | OSRS wraps by width (e.g. "Squeeze past the jutting wall on your way to the cosmic" / "altar.") (captured `lines`) | p12_full advances, 404 px (Script `wrap`) | unsourced (not compared line by line) |
| 12 | Count varbit | tier's `<area>_<tier>_count` = tasks done, sent on completion (captures; varbit 6311 `lumbridge_easy_count`, 6320 `varrock_med_count`) | `Tasks.complete`: popcount → count varbit | matches |
| 13 | Started varbit | set on a diary's first task (captures; 4453 `started_lumbridge_diary`) | set on every completion (idempotent) | matches |
| 14 | Tier-complete varbit | set 3 ticks after the tier's last task, with a message box (captures) | set in the same call (Tasks:47-53), no box | diverges (D5); box = scope gap |
| 15 | Per-task storage | OSRS sets a bit per task in varps (Lumbridge 1194/1195), not transmitted (captures) | server-only varps 63787-63794 (bit = task-1) | matches (behaviour; layout differs, declared) |
| 16 | Login | sends each diary's started/count/complete/reward varbits that are set (captures) | `onPlayerLogin { DiaryTasks.sync }` (no reward varbits: no rewards) | matches |
| 17 | L&D easy 2 cave bug, easy 8 Draynor Sewers zombie | any of them counts (wiki Changes 19 Nov 2015 for the zombies) | `DiaryKillHook` by NPC name in boxes (Hooks:110-117) | matches (boxes unsourced) |
| 18 | L&D easy 3 Sedridor, Varrock easy 2 Aubury | essence mine teleport by that NPC (wiki task text) | `essenceMineTeleport` after `telejump` | matches |
| 19 | L&D easy 4 water runes, Varrock easy 12 earth runes | from essence (wiki Changes 14 Apr 2021) | `runesCrafted`, not at Ourania | matches |
| 20 | L&D easy 5 Hans | "Learn your age from Hans in Lumbridge." | only the Age op (op 3); the Talk-to option "Can you tell me how long I've been here?" does not count | unsourced (see Unsourced) |
| 21 | L&D easy 6 pickpocket man/woman, medium 9 Martin | in Lumbridge / Martin (wiki) | `pickpocketed` after a successful steal | matches (box unsourced) |
| 22 | L&D easy 7 oak chop and burn | chop then burn oak logs in Lumbridge (wiki) | cut bit in `diary_progress_lumbridge`, burn completes | matches |
| 23 | L&D easy 9 anchovies Al Kharid, medium 6 salmon Lumbridge, Varrock easy 13 trout Barbarian Village | where caught; Lumbridge spots no longer count for the Varrock task (wiki Changes 6 Jul 2016) | `fishCaught` by player position | matches |
| 24 | L&D easy 10 bread on the Lumbridge range | (wiki) | `cooked`, `RangeType.LUMBRIDGE` | matches |
| 25 | L&D easy 11 / Varrock easy 3 iron ore | Al Kharid mine / south-east Varrock mine (wiki) | `oreMined` boxes | matches (boxes unsourced) |
| 26 | L&D medium 5 / Varrock medium 8 teleport spells | "Cast the teleport to … spell" (wiki) | `spellTeleport` by teleport name after `telejump` | matches (Varrock's GE variant not checked) |
| 27 | L&D medium 7 coif in the cow pen, medium 8 willow in Draynor | (wiki) | `crafted` / `logsChopped` boxes | matches |
| 28 | L&D medium 10 Chaeldar, Varrock medium 9 Vannaka | slayer task from that master (wiki) | `slayerTaskAssigned` master id 4 / 3 | matches (ids not checked against the cache) |
| 29 | L&D hard 4 / Varrock hard 3 Waka canoe | Lumbridge → Edgeville by Waka; Waka shaped at Edgeville (wiki) | `canoeTravelled` / `canoeShaped` | matches |
| 30 | Varrock easy 1 Thessalia, easy 5 Stronghold level 2 | browse the store; enter the second level (wiki) | shop open; War ladder down | matches |
| 31 | Varrock easy 4 plank, medium 10 20 mahogany planks | one plank at the Sawmill; 20 in one go, needs 20 logs and the coins (wiki, Changes 2015/2016) | `planksMade` after a successful transaction, `amount >= 20` | matches |
| 32 | Varrock easy 10 bowl | spin at the wheel and fire it in Barbarian Village (wiki) | spun bit, then fired | matches |
| 33 | Varrock medium 3 kitten colour | select a colour (needs a Ring of Charos(a)) (wiki task + requirements) | hook only in the Talk-to "[Charm]" branch (Gertrude.kt ~line 166) | diverges (D6) |
| 34 | Varrock medium 5 four emotes | each of the 4 distinct emotes (same emote 4 times does not count, wiki Changes 14 May 2015) | one bit per emote, any order, anywhere | matches (anywhere unsourced) |

## Divergences

### D1. 17 task lines differ from the in-game journal
- Severity: cosmetic (also breaks RuneLite Diary Requirements matching where the plugin uses the game text)
- Official (captured journal, `achievement-diaries.json` `tasks[].lines`, joined):
  - Karamja hard 2 "Successfully kill a Ket-Zek in the Fight Caves." (patch "Kill a Ket-Zek in the Fight Caves.")
  - Ardougne easy 5 "Go out fishing on the Fishing Trawler." (patch has no full stop)
  - Falador easy 6 "Kill a duck in Falador park."; medium 3 "Unlock the Crystal chest in Taverley" (no full
    stop); hard 3 "Kill the Giant Mole beneath Falador park." (patch: "Park", and a full stop on medium 3)
  - Fremennik hard 5 "Craft a Neitiznot shield on Neitiznot." (patch "Fremennik shield"; the wiki agrees with
    the capture)
  - Kandarin easy 4 "Play the Organ in Seers' Church." (patch "church")
  - Desert easy 5 "Kill a Vulture." (patch has no full stop)
  - Morytania medium 8 "Make a batch of steel cannonballs at the Port Phasmatys furnace." (patch drops "steel")
  - Varrock hard 10 "Squeeze through an obstacle pipe in Edgeville dungeon." (patch "the obstacle pipe")
  - Wilderness easy 3 "Pray at the Chaos Altar in Western Wilderness." (patch "in the Western"); easy 9 "…from
    the Wilderness." (patch no full stop); medium 9 "Talk to the Emblem Trader in Edgeville about emblems."
    (patch appends "(He's just north of the bank)", wiki only); hard 7 "Kill the Crazy Arc. Chaos Fanatic &
    Scorpia." (patch "Arc.,")
  - Western easy 7 "Teleport to Pest Control using the Minigame teleports." (patch "Minigame Teleport.")
  - Kourend easy 7 "Pray at the Altar in Kourend Castle." (patch no full stop); medium 1 "Travel to the Fairy
    Ring south of Mount Karuulm." (a no-break space in game)
- Patch: Tables, the strings above.
- Fix: replace the 17 strings with the captured text (the json's `lines` joined with a space); keep the
  no-break space in Kourend medium 1 if the font renders it.
- Live tree (openrune.patch): same (e.g. "Kill a Ket-Zek in the Fight Caves.", "Craft a Fremennik shield on
  Neitiznot.", "Squeeze through the obstacle pipe in Edgeville dungeon.").

### D2. Journal title, header and tier headings are coloured and spaced differently
- Severity: cosmetic
- Official (captures, tsps `Journal.AchievementDiaries.js`): title 741:2 `<col=800000>Achievement Diary -
  Lumbridge & Draynor`; line 1 `<col=ffff00>Lumbridge & Draynor Tasks`, then " "; each tier
  `<col=ffff00>Easy` (`<col=00ff00>` once the tier is complete), `<col=000000>`, " ", the tasks (open ones
  `<col=000000>`), " ". Karamja and Kourend use "" spacers, Kourend ends open lines with `</col>`.
- Patch: title "Achievement Diary"; header uncoloured; "" then `<col=800000>Easy</col>`; no `<col=000000>`
  on open tasks; no colour change for a finished tier (Script `journalLines`).
- Fix: build the lines as the capture does (title `"<col=800000>Achievement Diary - ${name}"`, a
  `name` column in `achievement_diaries`; yellow/green tier headings from the `_complete` varbit; the per-area
  spacer strings). RuneLite's plugin still matches (it checks the title starts with "Achievement Diary").
- Live tree (openrune.patch): same (`TITLE = "Achievement Diary"`, `TIER_COLOUR = "800000"`).

### D3. Journal opened without scripts 6844 / 2524 and without pagepos / busy
- Severity: cosmetic (scroll position / modal state; the effect of skipping them is not known)
- Official (captures): `pagepos` (varp 334) = line count, `busy` (varbit 12393) = 1, script 6844, the texts,
  script 2524 (-1, -1), 741 as the main modal, script 6845 (1, lines); `busy` = 0 on close.
- Patch: `ifOpenMain`, texts, `runClientScript(6845, 1, n)` (Script `openJournal`).
- Fix: send varp 334 and varbit 12393 and run 6844 before the texts and 2524 before the open; clear 12393 on
  close.
- Live tree (openrune.patch): same.

### D4. The tab rows only get OP1
- Severity: cosmetic
- Official (captures): `if_setevents` on 259:2 slots 0-11 with OP1 and OP2 (OP2 is the client's "Wiki").
- Patch: `IfEvent.Op1` only.
- Fix: `player.ifSetEvents(TASKBOX, 0 until AREAS, IfEvent.Op1, IfEvent.Op2)`.
- Live tree (openrune.patch): same.

### D5. A tier's complete varbit is set at once
- Severity: cosmetic
- Official (captures): 3 ticks after the tier's last task, the `<area>_diary_<tier>_complete` varbit is set and
  a message box says "Congratulations! You have completed all of the easy tasks in the <area> area. Speak to
  <NPC + location> to claim your reward." (Ardougne's: "Speak to Two-pints at the Flying Horse Inn in Ardougne
  to claim your reward.").
- Patch: `Tasks.complete` sets the varbit in the same call; no box.
- Fix: queue a 3-tick player task that sets the varbit and opens the box (box wording for areas other than
  Ardougne is only partly captured; see Not implemented).
- Live tree (openrune.patch): same.

### D6. Picking a kitten colour outside the Talk-to "[Charm]" branch does not complete Varrock medium 3
- Severity: gameplay-visible
- Official: Varrock Diary, Medium 3 "Select a colour for your kitten." (requirements: Gertrude's Cat, 100
  coins, Ring of Charos(a)); the task is choosing the colour, whichever way Gertrude is asked.
- Patch: `DiaryHooks.kittenColourSelected` is only called in the dialogue's option 3 branch. Gertrude's
  quick-buy op (`quickBuy`, `pickColour()` when wearing Charos) and the other Charos colour pick (line ~92) give
  a chosen-colour kitten without completing the task.
- Fix: call the hook wherever `pickColour()` returned a colour and `giveKitten` succeeded (a small
  `giveChosenKitten` helper used by all three paths).
- Live tree (openrune.patch): same (hook at the single call site).

## Unsourced
- Hans: only the Age op completes Lumbridge easy 5; whether the Talk-to option "Can you tell me how long I've
  been here?" also completes it is not in the wiki (it most likely does, since it shows the same age text).
  Needs a capture; if it does, call `DiaryHooks.hansAge` after `playtimeDialogue()` in option 4.
- Area boxes (Lumbridge, Al Kharid, Al Kharid mine, SE Varrock mine, Draynor, Barbarian Village, cow pen,
  Lumbridge Swamp caves, Draynor Sewers): the patch says they are guesses; the wiki gives no bounds.
- Line wrapping at 404 px with p12_full: not compared with the captured `lines` (e.g. Lumbridge hard 2 breaks
  after "cosmic"). A test that wraps all 492 texts and compares with the json would settle it.
- Stronghold emotes counted anywhere (not only inside the Stronghold).
- Slayer master ids 3 = Vannaka, 4 = Chaeldar (not checked against the cache's slayer master table).
- Whether the Varrock Teleport's Grand Exchange variant reports the name "varrock" to the hook.

## Not implemented (scope gaps)
- Task-complete chat message, declared as "wording unknown" in the PR/README. It is captured:
  `<col=dc143c>Well done! You have completed an easy task in the Ardougne area. Your Achievement Diary has been
  updated.</col>` ("a medium"/"a hard", "an elite"; area = the diary name, "Lumbridge & Draynor"/"Varrock"
  assumed by analogy, the tsps doc lists other areas' names as unverified). Send it from `Tasks.complete`
  when a task is newly done. This is the most visible gap.
- Tier-complete message box (D5) and the per-tier "If I ever lose my … I can speak to …" journal reminder.
- Rewards: claiming from Hatius Cosaintus / Toby (transcripts available), reward varbits, items, lamps, and
  every reward effect. Not declared in detail (the PR covers journals and progress only).
- Task hooks: 31 of 83 Lumbridge & Draynor / Varrock tasks are driven; the other 10 areas show journals only.
  Declared.
- The real per-task varp bit layout (1194/1195 etc.). Declared; not client-visible.
