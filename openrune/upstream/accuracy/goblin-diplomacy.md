# Goblin Diplomacy (goblin-diplomacy, patch 66 (needs 12), upstream PR #367)

Audited 2026-10-18 against the OSRS Wiki (pages: Goblin Diplomacy, Transcript:Goblin Diplomacy,
Transcript:Grubfoot, Transcript:General Wartface, Transcript:General Bentnoze, Goblin mail, Orange dye),
the live cache (npc 1965 multinpc, loc 16450/16557/16556 ops), gamevals (gobdip_* varbits, objs, locs)
and Quest Helper (hub jar, `GoblinDiplomacy`). Report only.

## Summary
The quest matches OSRS closely. It uses the client varbits Quest Helper reads (gobdip_main 2378 at
3/4/5 → 6, crate varbits 2379-2381, Grubfoot's look 13594 with the cache's transform order). Rewards are
right (5 QP, 200 Crafting XP, a gold bar), the crate and dye messages are verbatim (including OSRS's
"but but"), and the generals' opener, topic, start and hand-in dialogue follows the transcript. The
lookout ladder goes from level 0 to level 2 as in Quest Helper's zone. 30 mechanics checked: 19 match,
4 diverge (0 gameplay-visible, 4 cosmetic), 7 unsourced. The divergences are menu-flow details: a missing
"I'll leave you to it." option, the "Why are you arguing" sub-menu, a missing after-quest opener, and
Grubfoot's pre-quest line. Live tree (openrune.patch): all 4 are the same there (identical
`GoblinDiplomacy.kt` and ladder code). Notable undeclared gap: only orange and blue dye work on goblin
mail. The declared gaps: the try-on cutscenes are dialogue only, and start stage 3 is inferred.

## Mechanics
| # | Mechanic | Official behaviour (source) | Patch behaviour (file:line or function) | Verdict |
|---|---|---|---|---|
| 1 | Progress varbit 2378 for the in-quest stages | Quest Helper `GoblinDiplomacy`: steps 0 and 3 (orange), 4 (blue), 5 (brown) | BLUE_WANTED 4, BROWN_WANTED 5, COMPLETE 6 (dbrow-checked) on `varbit.gobdip_main` | matches |
| 2 | Start value 3 | Quest Helper maps 0 and 3 to the same step; no source says the start writes 3 | `differentColour` sets STARTED = 3 | unsourced (declared) |
| 3 | Rewards | 5 QP, 200 Crafting XP, a gold bar (Goblin Diplomacy, Reward; Quest Helper) | `rewards { xp(crafting,200); item(gold_bar,1) }` | matches |
| 4 | Item ids | Goblin mail 288, orange 286, blue 287, orange dye 1769, blue dye 1767 (Quest Helper) | `obj.goblin_armour` 288, `_orange` 286, `_darkblue` 287, `orangedye`, `bluedye` | matches |
| 5 | Generals' random opener (5 dialogues) | Dialogues 1-5 verbatim, all lead to the topic menu (Transcript:Goblin Diplomacy, Starting off) | `generalsDialogue`, `fatOpener`, `biggerGeneral`, `stupidGeneral` | matches |
| 6 | "Fat" opener choice | Three options: "Yes, he looks fat." / "No, he doesn't look fat." / "I'll leave you to it." (Transcript, Dialogue 1) | `fatOpener`: `choice2` (yes / no only) | diverges (D1) |
| 7 | Topic menu | Why arguing / Wouldn't you prefer peace? / Pick a colour / I'll leave you to it; answers return to the menu ("previous") (Transcript) | `mainMenu` loop | matches |
| 8 | "Why are you arguing" and its sub-menu | After Wartface's "Shut up!": a menu of "What is the goblin new century?" plus peace / pick / leave; new century returns to that menu; "I'll leave you to it." ends (Transcript, Dialogue 1 tree) | `whyArguing`: `choice2` (new century / leave); both return to the main menu, so "I'll leave you to it." is said and then the menu reopens | diverges (D2) |
| 9 | Peace answer | Verbatim (Transcript) | `mainMenu` Peace | matches |
| 10 | Pick red / green answers | Verbatim, back to the menu (Transcript) | `pickColour` 1, 2 | matches |
| 11 | "What about a different colour?" and the start prompt | Verbatim, "Start the Goblin Diplomacy quest?" Yes "I can do that." / No "Actually, I think I'll leave you to it." (Transcript) | `differentColour` | matches |
| 12 | Start dialogue and armour / dye help loop | Verbatim, including the D-Y-E exchange; loops until "Okay, I'll be back soon." (Transcript) | `differentColour`, `whereToGet` | matches |
| 13 | "Different colour" asked again mid-quest | Not in the transcript | "Human! Get us <colour> armour!" | unsourced (declared) |
| 14 | Crates: three, once each | "You find some goblin mail in the crate." (objbox) once per crate; then "You search the crate, but but find nothing of interest." (Transcript, Searching crates) | `searchCrate` with `gobdip_crate1..3_searched` (2379-2381, Quest Helper's varbits) on locs 16559-16561 | matches |
| 15 | Crate with a full inventory | Not documented | "You don't have enough inventory space." | unsourced |
| 16 | Crate availability outside the quest | Not documented (the walkthrough only says that dyed mail is not replaced) | Any stage, once per crate | unsourced |
| 17 | Orange / blue dye on goblin mail | "You dye the goblin mail orange." / "...blue." (Transcript, Showing the Generals) | `dyeMail`, one transaction | matches |
| 18 | Dye mixing (red+yellow, yellow+blue, red+blue) | Recipe exists, 0 ticks (Orange dye, Creation); message not documented | `mixDyes`: "You mix the two dyes and make <colour> dye." | unsourced (declared) |
| 19 | Wrong colour shown to a general | "What do you think of this colour?" / "That wrong colour." / "We tell you get <colour> armour." (Transcript) | `useOnGeneral` | matches |
| 20 | Right colour: Grubfoot try-on dialogue, three stages | Verbatim lines for orange → blue → brown, then "Thanks... I think..." and quest complete (Transcript) | `handIn` | matches (cutscene: scope gap, declared) |
| 21 | Grubfoot's look during the quest | Grubfoot wears orange, then blue (Transcript qact lines) | `grubfootLook` = 1 / 2 on `gobdip_grubfoot_vis` (13594); npc 1965 transforms [671 brown, 672 orange, 673 blue] | matches |
| 22 | Grubfoot in orange / in blue | Verbatim + "General Wartface/Bentnoze: Shut up Grubfoot!" (Transcript, Talking to Grubfoot) | `grubfootDialogue` + random general | matches |
| 23 | Grubfoot during the quest before orange | "Grubfoot wear red armour! Grubfoot wear green armour!" / "Why they not make up their minds?" (Transcript, Starting off → Talking to Grubfoot) | `grubfootDialogue` (stage 3 with the brown look) | matches |
| 24 | Grubfoot before the quest | The "red armour / green armour" lines are listed under Starting off; the five random lines are listed "After Goblin Diplomacy" (Transcript:Grubfoot) | Stage 0 plays the after-quest random lines | diverges (D4) |
| 25 | Grubfoot after the quest | Five random lines + "Shut up Grubfoot!" (Transcript:Grubfoot, After Goblin Diplomacy) | `grubfootChatter` | matches |
| 26 | Generals after the quest | Five random dialogues, incl. Dialogue 5: Wartface "Now you've solved our argument, we gotta think of something else to do." / Bentnoze "Yep, we bored now." (Transcript:General Wartface / Bentnoze) | `afterQuest`: dialogues 1-4 only | diverges (D3) |
| 27 | Item used on a general outside the quest | Not documented | "Nothing interesting happens." | unsourced |
| 28 | Hand-in through the talk menu | The transcript shows the hand-in only as using the armour on a general | `mainMenu` adds "I have some <colour> armour here." when the wanted armour is held | unsourced |
| 29 | Lookout ladder | Ladder 16450 (Climb-up) at (2954, 3497, 0) leads to the crate zone on plane 2; ladder 16556 goes back down; 16557 (middle) has no ops (Quest Helper `goUpLadder` / `goDownLadder` / `upstairs` zone; cache) | `GoblinCaveLadders.climbInPlace`: 0 ↔ 2 on the player's tile, reach-for-ladder anim | matches |
| 30 | Completion message | "Congratulations, you've completed a quest: Goblin Diplomacy" (Transcript) | Framework `completeQuest` | matches |

## Divergences

### D1. "Fat" opener lacks "I'll leave you to it."
- Severity: cosmetic
- Official: Transcript:Goblin Diplomacy, Starting off, Dialogue 1: after "Um..." the menu has three
  options; the third is "I'll leave you to it." ({{tact|above}}, ends the dialogue).
- Patch: `fatOpener` uses `choice2` with yes / no.
- Fix: `choice3(..., "I'll leave you to it.", 3)`. Option 3 says the line and returns `false`; the
  return value is already there for this purpose.
- Live tree (openrune.patch): same.

### D2. "Why are you arguing" sub-menu flow
- Severity: cosmetic
- Official: Transcript, Dialogue 1 tree: after "Shut up!", the menu is "What is the goblin new century?" /
  "Wouldn't you prefer peace?" / "Do you want me to pick an armour colour for you?" / "I'll leave you to
  it."; the new-century answer returns to that menu; "I'll leave you to it." ends the conversation.
- Patch: `whyArguing` offers only new century / leave. Both paths fall back into `mainMenu`'s loop, so
  after the player says "I'll leave you to it." the topic menu opens again.
- Fix: make "Why" swap its own entry for "What is the goblin new century?" in the next menu iteration (a
  flag in `mainMenu`), and have Leave end the loop in every case (return a Boolean from `whyArguing`, as
  `pickColour` does).
- Live tree (openrune.patch): same.

### D3. After-quest generals: Dialogue 5 missing
- Severity: cosmetic
- Official: Transcript:General Wartface / Transcript:General Bentnoze, Standard dialogue, Dialogue 5:
  "Now you've solved our argument, we gotta think of something else to do." / "Yep, we bored now."
  (Dialogues 6-8 need Another Slice of H.A.M.).
- Patch: `afterQuest` picks from four.
- Fix: `random.of(maxExclusive = 5)` with a fifth branch for those two lines.
- Live tree (openrune.patch): same.

### D4. Grubfoot before the quest uses the after-quest lines
- Severity: cosmetic
- Official: Transcript:Goblin Diplomacy lists "Grubfoot wear red armour! Grubfoot wear green armour!" /
  "Why they not make up their minds?" under Starting off; Transcript:Grubfoot puts the five random lines
  under "After Goblin Diplomacy".
- Patch: `grubfootDialogue` plays `grubfootChatter` for stage 0 and for completion, and the red/green
  lines only for stages 1-5.
- Fix: use the red/green lines for `stage < COMPLETE` (stage 0 too) and the chatter only after
  completion. This is the transcript's split; an in-game check at stage 0 would confirm it.
- Live tree (openrune.patch): same.

## Unsourced
- Start value 3 (declared; Quest Helper's step map is consistent with it but does not show the write).
- "Human! Get us <colour> armour!" for "What about a different colour?" mid-quest (declared).
- Dye-mixing message "You mix the two dyes and make <colour> dye." (declared). The wiki recipe gives no
  message.
- Crate full-inventory message, and crates searchable outside the quest.
- "Nothing interesting happens." for goblin mail used on a general outside the quest.
- The extra "I have some <colour> armour here." talk-menu option. The transcript only shows using the
  armour on a general. Needs an in-game check of the generals' menu while holding the wanted armour.
- The client varbits `gobdip_know_about_armour` (13595), `gobdip_know_about_dye` (13596) and
  `gobdip_met_aggie` (13597) exist but are never set; the journal is our own wording (declared). The
  real journal probably reads these varbits, but no source here says how.
- Chathead expressions; the journal and the quest scroll item.

## Not implemented (scope gaps)
- Grubfoot's try-on cutscenes (changing curtain, camera); dialogue only. Declared in the PR.
- Dyes other than orange/blue on goblin mail (red, yellow, green, purple, black, pink, white goblin mail
  exist as objs 9054-9059 / 26567; Goblin Diplomacy walkthrough: "dying Yellow Goblin Mail with red dye
  makes Red Goblin Mail"). Not declared. They are needed later for Land of the Goblins, not for this
  quest.
- "Where's Bentnoze gone now?" when the generals lack line of sight (Transcript:General Wartface). Not
  declared; minor.
- Village goblins' quest-era chatter and overhead in-fighting lines (Transcript:Goblin Diplomacy,
  Talking to the goblins in the village) are not part of this patch.
