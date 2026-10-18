# Witch's Potion (witchs-potion, patch 65 (needs 12), upstream PR #368)

Audited 2026-10-18 against the OSRS Wiki (pages: Witch's Potion, Transcript:Witch's Potion,
Transcript:Hetty, Rat's tail, Rat, Dungeon rat, Giant rat), gamevals (npc/obj/loc ids) and Quest
Helper (hub jar, `WitchsPotion`). Report only.

## Summary
The quest logic matches OSRS: varp 67 at 0/1/2/3 (Quest Helper steps 0, 1, 2), 1 QP + 325 Magic XP,
Hetty's dialogue word for word including all 14 partial-ingredient lines, one of each ingredient taken,
and the cauldron (loc 2024). 20 mechanics checked: 15 match, 3 diverge (1 gameplay-visible, 2
cosmetic), 2 unsourced. The gameplay-visible one: the rat's tail drops only from the two regular rat
ids, but the wiki lists it on every standard rat (Stronghold of Security rats, dungeon rats, the
level-26 Stronghold giant rats). The cosmetic ones: the hand-in box is split in two, and the cauldron
drink line goes to the chatbox instead of a message box. Live tree (openrune.patch): all 3 are the same
there. The live tree also drops the tail only at stage 1, where the patch uses the drop table
(`quest_mode = "during"`).

## Mechanics
| # | Mechanic | Official behaviour (source) | Patch behaviour (file:line or function) | Verdict |
|---|---|---|---|---|
| 1 | Progress varp and values | varp 67; Quest Helper steps 0 talk, 1 get ingredients, 2 drink (Quest Helper `WitchsPotion`) | `WitchsPotion` STARTED 1, INGREDIENTS_GIVEN 2, COMPLETE 3 (checked against the dbrow) on `varbit.witchs_potion_progress` over `varp.hetty` | matches |
| 2 | Rewards | 1 QP, 325 Magic XP (Witch's Potion, Rewards; Quest Helper) | `rewards { xp(magic, 325.0) }`, QP from the dbrow | matches |
| 3 | Requirements | None; items burnt meat, eye of newt, onion, rat's tail (Witch's Potion, Details) | `INGREDIENTS` = eye_of_newt (221), rats_tail (300), onion (1957), burnt_meat (2146) | matches |
| 4 | Hetty opening and options | "What could you want with an old woman like me?" / "I am in search of a quest." / "I've heard that you are a witch." (Transcript:Witch's Potion, Talking to Hetty) | `beforeQuest` | matches |
| 5 | Witch branch and second menu | Two Hetty lines, then quest / "Goodbye." (Transcript) | `beforeQuest` | matches |
| 6 | Quest offer, start prompt, decline | Lines verbatim, "Start the Witch's Potion quest?", "No, I have my principles and honour." / "Suit yourself, but you're missing out." (Transcript) | `offerQuest` | matches |
| 7 | Accept and ingredient list | Verbatim through "Great, I'll go and get them." (Transcript) | `offerQuest` | matches |
| 8 | No ingredients | "I'm afraid I don't have any of them yet." + Hetty's reminder (Transcript, with none of the items) | `lookingForIngredients` | matches |
| 9 | Partial ingredients (14 combinations) | Player lists all four in order tail, meat, onion, newt; then "Great, but I'll need the other ingredients as well." (Transcript, with some of the items) | `ingredientsLine` builds the same 14 strings | matches |
| 10 | All ingredients: hand-in | "In fact I have everything!" / "Excellent, can I have them then?" then one box: "You pass the ingredients to Hetty and she puts them all into her cauldron. Hetty closes her eyes and begins to chant. The cauldron bubbles mysteriously." then "Well, is it ready?" / "Ok, now drink from the cauldron." (Transcript, with all the items) | `handIn`: same lines, but the box text is split over two `mesbox` calls | diverges (D2) |
| 11 | One of each ingredient removed, atomically | Implied by the transcript (four ingredients passed) | `invTransaction` deleting 1 of each; stage 2 only on success | matches |
| 12 | Hetty at stage 2 | "Well are you going to drink the potion or not?" / "Yes, I will." (Transcript, talking again before drinking) | `hettyDialogue` INGREDIENTS_GIVEN | matches |
| 13 | Cauldron before stage 2 | Player: "As nice as that looks, I think I'll give it a miss for now." (Transcript, Attempting to drink from the cauldron) | `drinkFromCauldron` | matches |
| 14 | Drinking from the cauldron | Message box: "You drink from the cauldron. It tastes horrible! You feel yourself imbued with power." then quest complete (Transcript, Drinking from the cauldron) | `mes(...)` (chatbox game message), then `completeQuest` | diverges (D3) |
| 15 | Cauldron loc | Loc 2024 (Quest Helper `drinkPotion`) | `loc.hettycauldron` = 2024 | matches |
| 16 | Hetty after the quest | "How's your magic coming along?" / "I'm practicing and slowly getting better." / "Good, good." (Transcript:Hetty) | `hettyDialogue` else; removed from `RimmingtonTownsfolkScript` | matches |
| 17 | Rat's tail: only once started, 100% per kill | "can only obtain a rat's tail if you have started the quest"; "100% chance to drop once you begin the quest" (Witch's Potion, Rat's tail; Rat's tail) | `rat.toml` `[[guaranteed]]` rats_tail x1, `quest_mode = "during"` | matches (gate caveat declared in the PR) |
| 18 | Which rats drop the tail | Rat (2854, 2855), Stronghold of Security rat (2492, 2513), dungeon rat (2865-2867, 3607-3609), level-26 giant rat in the Stronghold (2510-2512); not the regular giant rats (Rat's tail, Item sources; Rat / Dungeon rat / Giant rat drop tables) | `npcs = ["npc.rat", "npc.rat_indoors"]` (2854, 2855) | diverges (D1) |
| 19 | Tail at stage 2 (ingredients given, not drunk) | "only dropped during Witch's Potion" (drop table notes); stage 2 is not singled out | Patch: drops (quest in progress). Live tree: stage 1 only | unsourced |
| 20 | Cauldron at stage 3 (after the quest) | Not documented | Same "give it a miss" line | unsourced |

## Divergences

### D1. Rat's tail drops from the two regular rat ids only
- Severity: gameplay-visible
- Official: Rat's tail, Item sources: "Dungeon rat (12)", "Rat (Regular, 1)", "Rat (Stronghold of
  Security, 1)", "Giant rat (Level 26)", all "Always" during the quest; Witch's Potion, Rat's tail:
  "Giant rats will NOT drop a rat's tail" (the low-level ones).
- Patch: `rat.toml` covers `npc.rat` (2854) and `npc.rat_indoors` (2855) only.
- Fix: add `npc.sos_war_rat` (2492) and 2513, `npc.dungeon_rat` (2865) and 2866/2867,
  `npc.clocktower_rat` (3607) and 3608/3609, `npc.sos_fam_giantrat` (2510) and 2511/2512 to the quest
  drop (these may need their own tables, so the Stronghold rats' bones stay).
- Live tree (openrune.patch): same (`WitchsPotionRatTail.RATS = setOf("npc.rat", "npc.rat_indoors")`).

### D2. Hand-in box split in two
- Severity: cosmetic
- Official: Transcript:Witch's Potion, Talking to Hetty with all the items: a single tbox with both
  sentences.
- Patch: `handIn` shows "You pass the ingredients..." and "Hetty closes her eyes..." as two `mesbox`es.
- Fix: one `mesbox("You pass the ingredients to Hetty and she puts them all into her cauldron. Hetty
  closes her eyes and begins to chant. The cauldron bubbles mysteriously.")`.
- Live tree (openrune.patch): same.

### D3. Cauldron drink text is a chat message, not a box
- Severity: cosmetic
- Official: Transcript:Witch's Potion, Drinking from the cauldron: `{{tbox|You drink from the cauldron.
  It tastes horrible! You feel yourself imbued with power.}}` (a dialogue message box), then quest
  complete.
- Patch: `drinkFromCauldron` uses `mes(...)` (chatbox).
- Fix: `startDialogue { mesbox("You drink from the cauldron. It tastes horrible! You feel yourself
  imbued with power.") }` before `completeQuest`.
- Live tree (openrune.patch): same.

## Unsourced
- Whether the tail still drops at stage 2 (patch: yes; live tree: no). The wiki says only "during the
  quest". An in-game check or the drop's quest condition would settle it.
- The cauldron line after completion (stage 3).
- Journal texts (the PR declares them as guesses). The wiki links a journal transcript, but no page
  exists for it. The quest scroll item (eye of newt) is also declared as a guess.
- Chathead expressions; any drink animation (the wiki names none).

## Not implemented (scope gaps)
- The drop-table quest gate never passes under the default assume-completed requirements mode, so with
  default settings no rat drops a tail. Declared in the PR caveats. This makes the quest uncompletable
  on a default server, so it is effectively gameplay-visible.
- Ingredient sources (eye of newt from the Port Sarim magic shop for 3 coins, burning cooked meat on a
  fire or range, onions north of Rimmington) are generic content outside this patch, and this audit did
  not check them.
