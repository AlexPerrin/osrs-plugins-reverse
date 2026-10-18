# Sheep Shearer and Romeo & Juliet (quests-sheep-romeo, patch 12, upstream PR #366)

Audited 2026-10-18 against the OSRS Wiki (pages: Sheep Shearer, Transcript:Sheep Shearer,
Transcript:Fred the Farmer, Sheep (penguins), Transcript:Sheep (penguins), Romeo & Juliet,
Transcript:Romeo & Juliet, Transcript:Romeo, Transcript:Juliet, Transcript:Father Lawrence,
Transcript:Apothecary), the live cache (npc 731 ops, obj ids) and Quest Helper (hub jar,
`SheepShearer`, `RomeoAndJuliet`). Report only.

## Summary
The progress values (varp 179: 0 / 1..20 / 21; varp 144: 0/10/20/30/40/50/60/100), rewards
(Sheep Shearer 1 QP, 150 Crafting XP, 60 coins; R&J 5 QP), item ids and the main quest dialogue match
the wiki transcripts word for word. 37 mechanics checked: 23 match, 13 diverge (14 divergences D1-D14:
1 gameplay-visible, 13 cosmetic), 1 unsourced. Live tree (openrune.patch): all 14 divergences are the same there (identical
`SheepShearer.kt` / `RomeoAndJuliet.kt`). The one gameplay-visible divergence is the Apothecary's "Can you make
potions for me?", which the patch answers with an invented refusal; in OSRS it opens his potion-making
dialogue. Most cosmetic divergences are NPC dialogue that is cut short or replaced with a generic
line (Draul Leptoc, Phillipa, Father Lawrence outside the quest, Juliet/Romeo after the quest,
The Thing's Talk-to). Neither R&J cutscene is implemented, and the PR does not say so.

## Mechanics
| # | Mechanic | Official behaviour (source) | Patch behaviour (file:line or function) | Verdict |
|---|---|---|---|---|
| 1 | Sheep Shearer progress varp | varp 179; 0 not started, 1..20 in progress, wool needed = 21 - varp (Quest Helper `SheepShearer.updateWoolNeeded`, steps 1-20) | `SheepShearer` STARTED_STAGE 1, stage += balls given, COMPLETE_STAGE 21, checked against the cache dbrow `maxSteps` | matches |
| 2 | Sheep Shearer rewards | 1 QP, 150 Crafting XP, 60 coins (Sheep Shearer, Rewards; Quest Helper rewards) | `rewards { xp(crafting,150); item(coins,60) }`, QP from the dbrow | matches |
| 3 | Requirement: 20 unnoted balls of wool, partial hand-ins allowed | "you can make smaller deliveries"; "Fred will not accept noted balls of wool" (Sheep Shearer, Spinning the wool) | `handIn` takes `min(inv count of obj.ball_of_wool, remaining)` (unnoted id only) | matches |
| 4 | Fred's opening and the three options | "What are you doing on my land? You're not the one who keeps leaving all my gates open..." + quest / kill / lost (Transcript:Sheep Shearer, Starting off) | `beforeQuest` | matches |
| 5 | Quest offer dialogue and start prompt | Lines verbatim, "Start the Sheep Shearer quest?" Yes / No ("No, I'll give it a miss." / "Suit yourself.") (Transcript:Sheep Shearer, Starting off) | `offerQuest` | matches |
| 6 | Already holding 20 balls at the start | The "In fact Fred, funnily enough..." branch comes right after "...the small matter of The Thing." (before "What do you mean, The Thing?" and before the start prompt), then "Quest complete!" with no hand-in box (Transcript:Sheep Shearer, Starting off) | `offerQuest`: checked only after the start prompt and "Yes, okay. I can do that."; then `handIn` shows "You give Fred 20 balls of wool." + "That's the last of them." / "I guess I'd better pay you then." | diverges (D1) |
| 7 | Shears from Fred | "Fred gives you a set of sharp shears." when the player has none (Transcript:Sheep Shearer; Changes 14 May 2020) | `howToShear`: `invAddOrDrop(shears)` + objbox | matches |
| 8 | Spinning help and spinning wheel icon box | Lines verbatim; tbox with the spinning wheel map icon picture (Transcript:Sheep Shearer) | `spinningHelp`: lines match; `mesbox` without the icon picture | diverges (D2) |
| 9 | Mid-quest talk, no balls: remaining count and the 3 options | "You need to collect [n] more balls of wool." then "How do I shear sheep, again?" / "Remind me how to spin wool." / "Yeah, I think so." (Transcript:Sheep Shearer, Before delivering any balls of wool) | `shearingTalk` | matches |
| 10 | Mid-quest talk with unspun wool | "I've got some wool. I've not managed to make it into a ball though." + Fred's reply (Transcript, With regular wool) | `shearingTalk` (`inv.count(WOOL) > 0`) | matches |
| 11 | Partial hand-in | "You give Fred [1-19] [ball/balls] of wool." / "That's all I've got so far." / "I need [n] more before I can pay you." / "Ok I'll work on it." (Transcript, Handing in) | `handIn`, `ballsText` | matches |
| 12 | Final hand-in | "Give 'em here then." / "That's the last of them." / "I guess I'd better pay you then." / Quest complete; the transcript shows no "You give Fred..." box on this path (Transcript, Finishing up) | `handIn` shows "You give Fred n balls of wool." before "That's the last of them." | diverges (D3) |
| 13 | Shearing The Thing | "The... whatever it is... manages to get away from you!", no wool (Transcript:Sheep (penguins); Sheep Shearer walkthrough) | `shearTheThing` (op1 Shear on npc 731) | matches |
| 14 | Shearing The Thing without shears | Not documented | "You need a pair of shears to shear the sheep." and no attempt | unsourced |
| 15 | The Thing's Talk-to (op3) | Player: "That's a sheep...I think. I can't talk to sheep." (Transcript:Sheep (penguins), Standard dialogue) | `onOpNpc3` → `mes("Baa!")` | diverges (D4) |
| 16 | "Fred! Fred! I've seen The Thing!" option during the quest | Offered after interacting with The Thing; dialogue verbatim (Transcript:Sheep Shearer, After interacting with The Thing) | `duringQuest` / `sawTheThingTalk`, gated on `sawTheThing` | matches |
| 17 | After-quest Fred and The Thing option | Options kill / lost, plus "Fred! Fred! I've seen The Thing!" only "If the player has interacted with the Thing" (Transcript:Fred the Farmer, After Sheep Shearer) | `afterQuest` always offers the Thing option; `handIn` clears `sawTheThing` at completion, and `shearTheThing` sets it only while in progress | diverges (D5) |
| 18 | R&J progress varp | varp 144: 0, 10, 20, 30, 40, 50, 60, complete (Quest Helper `RomeoAndJuliet` steps 0-60) | `RomeoAndJuliet` NOT_STARTED..POTION_GIVEN = 0..60, COMPLETE 100 checked against the dbrow | matches |
| 19 | R&J rewards | 5 QP, no XP/items (Romeo & Juliet, Rewards) | `rewards {}`, QP from the dbrow | matches |
| 20 | Item ids | Message 755, Cadava berries 753, Cadava potion 756 (Quest Helper; gamevals) | `obj.julietmessage`, `obj.cadavaberries`, `obj.cadava` | matches |
| 21 | Romeo's opening line | One of six random openers ("Blub! Blub...", "Looking for a blonde girl...", "Juliet, Juliet, wherefore art thou...", ...), and Romeo approaches a nearby player himself (Transcript:Romeo & Juliet, Talking to Romeo) | `romeoStart`: always opener 3; no approach | diverges (D6) |
| 22 | Romeo's three opening branches, start prompt, decline | Verbatim; branches 2 and 3 quote 'she is the love of your life...' in single quotes (Transcript, Talking to Romeo) | `romeoStart`: verbatim except the quotes are dropped in branches 2/3 | matches (punctuation only) |
| 23 | Romeo again (stage 10), Where/Anything else/Ok options | Verbatim (Transcript, Talking to Romeo again) | `romeoAgain`, `romeoJulietOptions` | matches |
| 24 | Juliet at stage 10: message handed over, stage 20 | Verbatim, "Juliet gives you a message." (Transcript, Talking to Juliet) | `juliet` STARTED | matches |
| 25 | Lost message replacement | "Hmmm, that's the thing about messages..." + "Juliet gives you another message." (Transcript, Talking to Juliet again) | `juliet` TALKED_TO_JULIET | matches |
| 26 | Delivering the message (with / without it) | Verbatim incl. "Romeo folds the message away." and Lawrence directions (Transcript, Delivering the message) | `romeoMessage`, `romeoWhereIsLawrence`, `romeoLawrenceOptions` | matches |
| 27 | Using a spare message on Romeo | "Why are you waving that around at me as if it's something important?" (Transcript, Using a duplicate message on Romeo) | Not handled | diverges (D7, scope) |
| 28 | Father Lawrence quest talk (stage 30 → 40) | Verbatim; camera pans to the sleeping congregation ("Zzzzzzzzz") (Transcript, Finding Father Lawrence) | `fatherLawrence` MESSAGE_DELIVERED: lines match, no camera pan / congregation line | matches (cutscene camera omitted) |
| 29 | Father Lawrence outside the quest stages | Not started: "Hello adventurer, do you seek a quest?" + 4 options; after the potion is given: "Oh to be a father in the times of whiskey." / "I sing and I drink and I wake up in gutters." then a random line (Transcript:Father Lawrence) | `fatherLawrence` else → "Top of the morning to you." at every other stage | diverges (D8) |
| 30 | Apothecary quest topic and berries | "Talk about Romeo & Juliet." only after talking to Father Lawrence; lines verbatim; berries in hand make the potion at once; again with berries "Well done. You have the berries." (Transcript:Romeo & Juliet, Making the Cadava potion) | `apothecaryOtherTopics`, `apothecaryRomeoAndJuliet`, `makePotion`, `berryQuestions` | matches |
| 31 | Apothecary "Can you make potions for me?" | Opens his potion-making dialogue (Apothecary's Potions) (Transcript:Apothecary, Standard dialogue) | "I'm afraid I'm too busy to brew anything for you right now." | diverges (D9) |
| 32 | Apothecary "something else" menu | Also "Have you got any decent gossip to share?"; hair/charity answers end with "But can I do something for you?" / "...Do you need anything else?" and return to the menu (Transcript:Apothecary) | Gossip option missing; answers end the dialogue without the trailing line | diverges (D10) |
| 33 | Juliet with / without the potion (stage 50 → 60) | Verbatim, "You pass the suspicious potion to Juliet.", then a cutscene with Phillipa and Draul Leptoc (Transcript, Delivering the potion) | `julietPotion`: dialogue verbatim; no cutscene | matches (cutscene: scope gap) |
| 34 | Romeo finish (stage 60 → complete) | Verbatim, crypt cutscene starts after "I think I saw the entrance..." (Transcript, Rescuing Juliet) | `romeoFinish`: same lines as plain dialogue in place; no cutscene, no movement | matches (cutscene: scope gap) |
| 35 | Draul Leptoc | Stage 0/10: "What are you doing here? Snooping around..." + 4 options; stage 20: the message-noticed branch if carried; stage 40/50: continues "Do you know why?" ... plus "Draul notices the potion!" branch; stage 30 and 60+ lines (Transcript, Talking to Draul Leptoc sections) | `draulLeptoc`: stage 0/10 skips the menu (always "Oh...just looking around..."); stage 20 no message branch; stage 40/50 ends after "...that's all I can say."; 30 and 60 match | diverges (D11) |
| 36 | Phillipa | Default adds "He'd do it, you know - he's ever so dashing..."; after the message 2 more lines ("And I'm sure Romeo is just the sort of jester...", "He always brings a tear to my eyes..."); after the potion ends with "Good, good - you lucky thing, I can't wait to see the look on his face!" (Transcript, Talking to Phillipa sections) | `phillipa`: first line of each only | diverges (D12) |
| 37 | Juliet and Romeo before / after the quest | Juliet not started: "Romeo, Romeo, wherefore art thou Romeo?" then "She seems to be lost in thought."; after: "I sat in that cold crypt for ages..." (4 lines) (Transcript:Juliet). Romeo after: "I heard Juliet had died. Terrible business." / "Her cousin and I are getting on well though. Thanks for your help." (Transcript:Romeo) | Juliet: box only before, and the same box after; Romeo after: "Thank you for all your help, my friend. I have never been happier!" | diverges (D13, D14) |

Unsourced rows: 14, plus the journal texts (see below).

## Divergences

### D1. Already holding 20 balls: wrong point in the dialogue, extra prompt and lines
- Severity: cosmetic
- Official: Transcript:Sheep Shearer, Starting off: the "In fact Fred, funnily enough..." exchange comes
  straight after "...there's the small matter of The Thing.", before "What do you mean, The Thing?"
  and with no "Start the Sheep Shearer quest?" prompt; it ends in "Quest complete!" with no hand-in box
  or "That's the last of them." (2 August 2018 change: the quest recognises the items on start).
- Patch: `offerQuest` runs the whole Thing exposition and the start prompt first, then the 20-ball
  branch, then `handIn()` (box "You give Fred 20 balls of wool." plus the two finishing lines).
- Fix: in `offerQuest`, right after the third Fred line, `if (ballsOfWool(player) >= BALLS_NEEDED)`
  run the six lines, `quest.advanceQuestStage` (or set the start stage), delete 20 balls and
  `setQuestStage(COMPLETE_STAGE)` directly, without the prompt or the `handIn` box/lines.
- Live tree (openrune.patch): same (the live file is identical apart from a header comment and `quest.debugStartStage`).

### D2. Spinning wheel icon box has no picture
- Severity: cosmetic
- Official: tbox with the spinning wheel map icon (pic 7670) (Transcript:Sheep Shearer).
- Patch: plain `mesbox("This icon denotes a Spinning Wheel on the world map.")`.
- Fix: use an objbox/graphic box with the map icon sprite, if the dialogue API supports a sprite box.
- Live tree (openrune.patch): same (the live file is identical apart from a header comment and `quest.debugStartStage`).

### D3. Final hand-in shows the "You give Fred" box
- Severity: cosmetic
- Official: Transcript:Sheep Shearer, Finishing up: "Give 'em here then." → "That's the last of them."
  with no tbox (the box appears only on partial hand-ins, "[1-19]").
- Patch: `handIn` shows the box before the completion branch.
- Fix: move the `mesbox` into the partial branch (after the `stage >= COMPLETE_STAGE` check).
- Live tree (openrune.patch): same (the live file is identical apart from a header comment and `quest.debugStartStage`).

### D4. The Thing's Talk-to
- Severity: cosmetic
- Official: Transcript:Sheep (penguins), Standard dialogue: Player: "That's a sheep...I think. I can't
  talk to sheep." (Cold War dialogue aside).
- Patch: `onOpNpc3("npc.sheep_shearer_the_thing") { mes("Baa!") }`.
- Fix: `startDialogue(it.npc) { chatPlayer(confused, "That's a sheep...I think. I can't talk to sheep.") }`.
- Live tree (openrune.patch): same (the live file is identical apart from a header comment and `quest.debugStartStage`).

### D5. After-quest Thing option offered unconditionally
- Severity: cosmetic
- Official: Transcript:Fred the Farmer: the Thing option appears "If the player has interacted with the
  Thing".
- Patch: `afterQuest` always shows it; meanwhile `handIn` clears `sawTheThing` on completion and
  `shearTheThing` only sets it during the quest, so the flag is not what gates it.
- Fix: keep `sawTheThing` set at completion, set it on any shear attempt (not only in progress), and
  use `choice2` (kill / lost) when it is false.
- Live tree (openrune.patch): same (the live file is identical apart from a header comment and `quest.debugStartStage`).

### D6. Romeo's opener and approach
- Severity: cosmetic
- Official: Transcript:Romeo & Juliet, Talking to Romeo: a random opener from six; "If the player is
  nearby Romeo: Romeo approaches you...".
- Patch: always "Juliet, Juliet, wherefore art thou Juliet? Have you seen my Juliet?"; no approach.
- Fix: pick one of the six lines at random in `romeoStart` (the approach behaviour is a scope gap).
- Live tree (openrune.patch): same (the live file is identical apart from a header comment and `quest.debugStartStage`).

### D7. Spare message used on Romeo
- Severity: cosmetic
- Official: "Why are you waving that around at me as if it's something important?" (Transcript, Using a
  duplicate message on Romeo).
- Patch: no item-on-npc handler.
- Fix: `onOpNpcU("npc.romeo", "obj.julietmessage")` at stages other than 20 → that line.
- Live tree (openrune.patch): same (the live file is identical apart from a header comment and `quest.debugStartStage`).

### D8. Father Lawrence outside the quest stages
- Severity: cosmetic
- Official: Transcript:Father Lawrence: not started → "Hello adventurer, do you seek a quest?" with "I am
  always looking for a quest." / "No, I prefer just to kill things." / "Can you recommend a good bar?" /
  "Ok, thanks" (each returning to the menu); after the potion → the whiskey lines then one of two
  random lines ("Top of the morning to you." or "To err is human..." / "I need a think I drink.").
- Patch: "Top of the morning to you." for every stage outside 30-50.
- Fix: add the not-started menu for stage 0 (and probably 10), and the whiskey + random lines for
  stage >= 60.
- Live tree (openrune.patch): same (the live file is identical apart from a header comment and `quest.debugStartStage`).

### D9. Apothecary "Can you make potions for me?" is an invented refusal
- Severity: gameplay-visible
- Official: Transcript:Apothecary, Standard dialogue: the option opens "Apothecary's Potions" (his
  potion service: strength potion, spot cream, etc.).
- Patch: `apothecary` answers "I'm afraid I'm too busy to brew anything for you right now.", a line not in
  OSRS, and the patch's `onOpNpc1("npc.apothecary")` now owns the NPC.
- Fix: route option 1 to the potion-making dialogue (or, if out of scope, leave the option out rather than
  inventing a line, and list it as a caveat in the PR).
- Live tree (openrune.patch): same (the live file is identical apart from a header comment and `quest.debugStartStage`).

### D10. Apothecary "something else" menu
- Severity: cosmetic
- Official: Transcript:Apothecary: also "Have you got any decent gossip to share?" (stage-dependent
  answers); the hair answer adds "But can I do something for you?", the charity answer reads "Sorry,
  charity is not my strong point. Do you need anything else?", and both return to the menu.
- Patch: no gossip option; answers end the dialogue.
- Fix: add the gossip option with its not-started / completed answers, append the trailing lines and
  loop back to `apothecaryOtherTopics`.
- Live tree (openrune.patch): same (the live file is identical apart from a header comment and `quest.debugStartStage`).

### D11. Draul Leptoc dialogue cut short
- Severity: cosmetic
- Official: Transcript:Romeo & Juliet, "Talking to Draul Leptoc" (Finding Juliet): a 4-option menu
  ("I've come to see Juliet on Romeo's behalf." / "I've just come to have a chat with Juliet." /
  "Oh...just looking around..." / "Ok, thanks."); at stage 20 "Sir Draul notices the message!" branch
  when it is carried; at 40/50 four more lines after "...that's all I can say. Do you know why?" and the
  "Draul notices the potion!" branch when the potion is carried.
- Patch: `draulLeptoc` plays only the "just looking around" branch with no menu, no message/potion
  branches, and stops early at 40/50.
- Fix: add the menu and both inventory branches from the transcript.
- Live tree (openrune.patch): same (the live file is identical apart from a header comment and `quest.debugStartStage`).

### D12. Phillipa lines missing
- Severity: cosmetic
- Official: Transcript:Romeo & Juliet, Talking to Phillipa sections (lines quoted in row 36).
- Patch: one line per state.
- Fix: add the missing chatNpc lines.
- Live tree (openrune.patch): same (the live file is identical apart from a header comment and `quest.debugStartStage`).

### D13. Juliet before and after the quest
- Severity: cosmetic
- Official: Transcript:Juliet: before, Juliet says "Romeo, Romeo, wherefore art thou Romeo?" then the box;
  after, "I sat in that cold crypt for ages waiting for Romeo." / "That useless fool never showed up." /
  "And all I got was indigestion. I am done with men like him." / "Now go away before I call my
  father!".
- Patch: box only before; the same box after completion.
- Fix: add the chatNpc line before the box at stage 0, and a COMPLETE branch with the four lines.
- Live tree (openrune.patch): same (the live file is identical apart from a header comment and `quest.debugStartStage`).

### D14. Romeo after the quest
- Severity: cosmetic
- Official: Transcript:Romeo: "I heard Juliet had died. Terrible business." / "Her cousin and I are
  getting on well though. Thanks for your help."
- Patch: "Thank you for all your help, my friend. I have never been happier!"
- Fix: replace with the two transcript lines.
- Live tree (openrune.patch): same (the live file is identical apart from a header comment and `quest.debugStartStage`).

## Unsourced
- "You need a pair of shears to shear the sheep." when shearing The Thing without shears; the shearing
  animation on The Thing. Needs an in-game capture or the generic sheep-shearing message of OpenRune.
- In-progress and completed journal texts for both quests (the journals were reworked on 14 January
  2026 per both Changes sections); needs the current OSRS journal text (Quest Helper reads only the
  varps, so this is cosmetic).
- Quest scroll items (ball of wool, cadava potion) and every chathead expression.
- Giving shears again from the "How do I shear sheep, again?" branch (the transcript shows the line
  "I've got some here you can use" but no "Fred gives you..." box there).
- Phillipa at stage 100 and Juliet at stage 60 (patch: default / box) are not documented.

## Not implemented (scope gaps)
- The two Romeo & Juliet cutscenes: Juliet drinking the potion with Phillipa and Draul Leptoc (the
  "Cadava Potion" jingle), and the crypt with Romeo and Phillipa ("Romeo Falls in Love" jingle).
  Not declared in the PR text.
- Romeo walking up to nearby players.
- Cadava potion Look-at and Drink handling (black-out at the Apothecary's, refusal elsewhere;
  Transcript:Romeo & Juliet, Look-at / Drinking the potion). Not declared.
- Using shears on The Thing as an item-on-npc action (the walkthrough mentions use-on-sheep; only op1 is
  handled here). Not declared.
- Speedrunning-world refusal (not relevant to this server).
