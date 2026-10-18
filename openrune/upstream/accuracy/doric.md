# Doric's Quest (content/doric, patch 64 (needs 12, 20a), unpushed)

Audited 2026-10-18 against the OSRS Wiki (pages: Doric's Quest (Details, Walkthrough, Rewards, Changes, Trivia),
Transcript:Doric (Before/After Doric's Quest, Attempting to use his anvils), Transcript:Doric's Quest (all
sections)). Report only.

## Summary
The quest flow, requirements, rewards and almost all dialogue match the transcripts verbatim, including the
26 July 2018 "already have the materials" shortcut and the 15 Mining iron-ore line. 3 divergences (1
gameplay-visible, 2 cosmetic): the after-quest talk is invented although the wiki has the real one, the anvil-path
"Yes" stops after the pickaxe (no where-to-find / materials shortcut that "(Same as above)" implies), and the anvil
menu likely has one option too many at the top level. The whetstone's pre-quest line is missing.

## Mechanics
| # | Mechanic | Official behaviour (source) | Patch behaviour (file:line or function) | Verdict |
|---|---|---|---|---|
| 1 | Start | Talk to Doric north of Falador (Details) | `onOpNpc1(npc.doric)` | matches |
| 2 | Opening line and 5 topics | "Hello traveller, what brings you to my humble smithy?" + 5 options (Transcript:Doric's Quest, Starting off) | `beforeQuest` choice5, same order and text | matches |
| 3 | Anvils / whetstone replies | Verbatim (Starting off) | `beforeQuest` 1, 2 (incl. player "I wanted to use your whetstone.") | matches |
| 4 | "Shortstuff", landscape, "What do you make here?" branches | Verbatim with sub-menus (Starting off) | `beforeQuest` 3, `landscape`, `whatDoYouMake` | matches |
| 5 | Start prompt | "Start Doric's Quest?" Yes./No. (Starting off) | `startQuestPrompt` -> `startQuestTitle` ("Start Doric's Quest?") | matches |
| 6 | Decline | "No, hitting rocks is for the boring people, sorry." / "That is your choice. Nice to meet you anyway." | `offerQuest` | matches |
| 7 | Accept + request + pickaxe | "Yes, I will get you the materials." / "Clay is what I use..." / "Take this pickaxe..." (Starting off; Rewards: bronze pickaxe given) | `offerQuest`, `invAddOrDrop(bronze_pickaxe)` | matches |
| 8 | Requirements | 6 clay (not soft), 4 copper ore, 2 iron ore, unnoted, all at once (Details, Walkthrough) | `MATERIALS`, unnoted ids, one `invTransaction` | matches |
| 9 | Already-have shortcut | Coincidence dialogue, "In fact, in the exact quantities too!" only on exact counts (Starting off; Changes 26 Jul 2018) | `alreadyHaveMaterials`, `hasExactMaterials` | matches |
| 10 | Where to find + 15 Mining line | Verbatim; extra lines if below 15 Mining (Starting off) | `whereToFind`, `baseMiningLvl < 15` | matches (base vs boosted level unsourced) |
| 11 | During quest, without materials | "Have you got my materials yet, traveller?" / "Sorry, I don't have them all yet." / "Not to worry, stick at it..." then where-to-find menu | `duringQuest` | matches |
| 12 | Hand-in | "I have everything you need!" / "Many thanks! Pass them here..." / "You hand the clay, copper, and iron to Doric." | `duringQuest`, `handIn` mesbox | matches |
| 13 | Rewards | 1 QP, 1,300 Mining XP, 180 coins, use of Doric's anvils (Rewards) | `rewards { xp 1300; 180 coins; extra("Use of Doric's Anvils") }` | matches |
| 14 | Varp 31 values | 0 / 10 / 100 (Quest Helper; cache dbrow end state) | `STARTED = 10`, `COMPLETE = 100`, init `check(quest.maxSteps == 100)` | matches |
| 15 | Anvils gated before completion | Smith on his anvils triggers Doric's "Hey, who said you could use that?..." (Attempting to use his anvils) | `LocOpOverrides` claim on `loc.dorics_anvil` Op1 while not complete; `anvilBeforeQuest` | matches |
| 16 | Anvil menu structure | "Sorry, would it be OK if I used your anvils?" -> "If you could get me some more materials then I could let you use them." -> Yes / No; top level also "I didn't want to use your anvils anyway." -> "That is your choice." | `anvilBeforeQuest`: top-level choice3 adds "No, hitting rocks..." alongside "Sorry..." and "I didn't want..." | diverges (D3) |
| 17 | Anvil-path "Yes" | "(Same as above)": the full start sequence incl. where-to-find menu or materials shortcut | Start lines + pickaxe, then ends | diverges (D2) |
| 18 | Anvil path once started | Not on the wiki | "Remember, I need 6 clay, 4 copper ore, and 2 iron ore." | unsourced |
| 19 | Whetstone before quest | "You should probably ask before using that." (Attempting to use his whetstone) | Not handled | not implemented |
| 20 | After-quest talk | "Hello traveller, how is your metalworking coming along?" / "Not too bad, Doric." / "Good, the love of metal is a thing close to my heart." (Transcript:Doric, After Doric's Quest) | Opening line again + landscape / "What do you make here?" menu (`afterQuest`) | diverges (D1) |
| 21 | Journal | Updated 14 May 2020 (Changes); text not on the wiki | Our text (`questLog`, `completedLog`) | unsourced (declared) |
| 22 | Quest scroll item | Not on the wiki page | `obj.steel_pickaxe`, zoom 300 | unsourced (declared) |
| 23 | Mesanims | Not in the transcript | happy/neutral/angry/quiz/confused per line | unsourced |

## Divergences
### D1. After-quest dialogue is invented
- Severity: cosmetic
- Official: Transcript:Doric, "After Doric's Quest": Doric: "Hello traveller, how is your metalworking coming
  along?" Player: "Not too bad, Doric." Doric: "Good, the love of metal is a thing close to my heart." (End of
  dialogue)
- Patch: `afterQuest` repeats "Hello traveller, what brings you to my humble smithy?" and offers the landscape /
  "What do you make here?" menu (the PR calls this "ours").
- Fix: replace `afterQuest` body with the three transcript lines (`chatNpc`, `chatPlayer`, `chatNpc`).

### D2. Anvil-path acceptance skips the rest of the start sequence
- Severity: gameplay-visible (a player holding the materials does not finish the quest from this path)
- Official: Transcript:Doric's Quest, "Attempting to use his anvils before the quest": "Yes, I will get you
  materials. (Same as above)", i.e. the start sequence, which continues to "Where can I find those?" or, with the
  materials, the coincidence lines and hand-in.
- Patch: `anvilBeforeQuest` option 1 -> 1 sets the stage, says the two start lines, gives the pickaxe and ends.
- Fix: after `invAddOrDrop`, call the same tail as `offerQuest`: `if (hasMaterials(player)) alreadyHaveMaterials()
  else whereToFind()` (factor the post-Yes part of `offerQuest` into one function used by both).

### D3. Anvil menu has an extra top-level option
- Severity: cosmetic
- Official: same section. After "Hey, who said you could use that?..." the menu reads "Sorry, would it be OK if I
  used your anvils?" and "I didn't want to use your anvils anyway."; "No, hitting rocks is for the boring people,
  sorry." is in the second menu next to "Yes, I will get you materials." (the transcript's flattened text is
  slightly ambiguous; a two-option top menu is the reading where every sub-menu has two entries).
- Patch: top-level `choice3` with "No, hitting rocks..." as option 2.
- Fix: make the top menu `choice2("Sorry, would it be OK if I used your anvils?", 1, "I didn't want to use your
  anvils anyway.", 3)`; keep the Yes/No sub-menu as is. Confirm with an in-game capture if possible.

## Unsourced
- Doric's reply on the anvil path when the quest is already started ("Remember, I need ...").
- Whether the Mining check uses base or boosted level (wiki marks 15 Mining as boostable for the requirement; the
  dialogue condition is unknown).
- Journal and completion text (declared), quest-scroll item (declared), mesanims, splitting the long start line
  into two boxes (declared).
- Using a bar or hammer on the anvils before the quest is not blocked (unknown on live).
- Whether Doric walks over (README says "brings Doric over"; the patch only opens dialogue with his chathead).

## Not implemented (scope gaps)
- Whetstone pre-quest message "You should probably ask before using that." (not declared).
- Treasure Trails lines (Transcript:Doric, beginner/easy) belong to the clue patches, not this PR.
