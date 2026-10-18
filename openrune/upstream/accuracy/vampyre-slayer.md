# Vampyre Slayer (content/vampyre-slayer, patch 67 (needs 12), unpushed)

Audited 2026-10-18 against the OSRS Wiki (pages: Vampyre Slayer (Details, Walkthrough, Reward, Changes),
Transcript:Vampyre Slayer (all sections; dialogue re-written 30 June 2026 per Changes), Transcript:Morgan,
Transcript:Dr Harlow, Transcript:Bartender (Blue Moon Inn), Count Draynor (infobox, Strategy, Stat restoration),
Money making guide/Collecting garlic) and the LIVE cache (npc 3481, seq gamevals). Report only.

## Summary
Dialogue for Morgan, Dr Harlow and the Blue Moon Inn bartender matches the current transcripts almost line for
line, the kill messages are verbatim, the reward (4,825 Attack XP; QP from the dbrow) and the varp 178 steps match.
The Count fight is where it diverges: 4 divergences (3 gameplay-visible, 1 cosmetic): garlic applied once at rise
instead of on the first attack, never re-applied and without its +1 damage; no fast regeneration (declared); a
500-tick despawn instead of returning to the coffin and healing (declared in part); and the low-combat start
warning plus one menu label differ. Fix the garlic model first.

## Mechanics
| # | Mechanic | Official behaviour (source) | Patch behaviour (file:line or function) | Verdict |
|---|---|---|---|---|
| 1 | Start at Morgan | Morgan's house, Draynor (Details) | `onOpNpc1(npc.morgan)` -> `morganStart` | matches |
| 2 | Morgan opening + prompt | Verbatim lines; "Start the Vampyre Slayer quest?" (Starting off) | `morganStart`, `startQuestPrompt` | matches |
| 3 | Low-combat warning | Combat < 20: "Before starting this quest, be aware that your combat level is lower than the recommended level of 20." (Starting off) | None | diverges (D4) |
| 4 | Accept / decline lines | Verbatim (Starting off) | `morganStart` | matches |
| 5 | Morgan menu label (first talk) | "What else can you tell me about this vampyre?" (Starting off); "...about Count Draynor?" on later talks | Always "What else can you tell me about Count Draynor?" (`morganOptions`) | diverges (D4) |
| 6 | Morgan legends | Verbatim incl. "Home come he's never tried..." (Starting off) | `morganLegends` | matches |
| 7 | Morgan during quest, before / after stake | Verbatim, garlic hint + "Perfect, thanks." / "Good luck in Draynor Manor, adventurer." (Talking to Morgan again; Returning to Morgan) | `morganDuring`, `morganOptions` | matches |
| 8 | Morgan after quest | First time / subsequent lines (Transcript:Morgan) | `morganAfter` with `varbit.morgan_postquest_dialogue` | matches |
| 9 | Harlow standard (not on quest / after) | "Buy me a drink pleassh..." / "I think you've had enough." (Transcript:Dr Harlow) | `harlowFirst(onQuest = false)` | matches |
| 10 | Harlow first quest talk | Verbatim menu and beer request (Dr Harlow) | `harlowFirst(true)`, sets `vampyre_asked_harlow` | matches |
| 11 | Beer hand-over and stake | Verbatim; "You give a beer to Dr Harlow."; "Dr Harlow hands you a stake." (Talking to him again) | `harlowBeer`, `giveBeer`; `harlow_given_beer/stake`, stage 2 | matches |
| 12 | Harlow topics | Verbatim topic texts (Talking to him again; after getting the stake) | `harlowTopics`, `neverSeen`, `properHunter` | matches (menu re-listing unsourced) |
| 13 | Lost stake | Replacement lines verbatim (after getting the stake) | `harlowAfterStake`: no stake in inv or bank -> new stake | matches (bank check unsourced) |
| 14 | Beer from the Blue Moon bartender | 2 coins, "You buy a pint of beer.", no-money line; fortune/equipment topics verbatim (Transcript:Bartender (Blue Moon Inn)) | `BlueMoonBartender` | matches |
| 15 | Garlic source | Cupboard upstairs in Morgan's house, open then search repeatedly (Walkthrough; Collecting garlic) | `garliccupboardshut/open` ops, one garlic per search | matches (message unsourced) |
| 16 | Count rises from coffin | Open the coffin in the basement; he pops up and attacks (Walkthrough) | `openCoffin`: spawns `npc.count_draynor`, `seq.vampireslayer_arise`, `opPlayer2` | matches |
| 17 | Count stats | Combat 34, HP 35, Att 30, Str 25, Def 30, max hit 3, 4-tick crush, aggressive (Count Draynor infobox) | Cache npc type (inherit); attack anim overridden to `seq.human_unarmedpunch` | matches (anim unsourced) |
| 18 | Garlic trigger | On the player's auto-attack (melee or autocast) when the Count is at full HP; message "The vampyre seems to be weakened by the garlic you're carrying." (Count Draynor, Strategy; Transcript) | Applied 2 ticks after rising if garlic is held, before any attack; message verbatim | diverges (D1) |
| 19 | Garlic effect values | 10 damage, -10 Att/Str, -40 Def (Def to 0) (Strategy, Stat restoration) | hp -10 (floored at 1, no hitsplat), -10/-10, Def -40 floored at 1 | diverges (D1, minor) |
| 20 | Garlic re-application | Stats restore 1 per 5 ticks; when Attack is back to full (50 ticks) garlic re-applies with "The vampyre seems to weaken."; again when HP is full (Stat restoration) | One-shot; no re-application | diverges (D1) |
| 21 | Garlic +1 damage | All damage +1 while carrying garlic (Strategy) | None | diverges (D1) |
| 22 | Fast regeneration | ~20x normal (Count Draynor, Strategy; quest page says ~6x) | Normal npc regeneration | diverges (D2, declared) |
| 23 | Kill needs stake + hammer in inventory | Automatic stab; messages verbatim (Slaying Count Draynor) | `countDeath`: both -> "You hammer the stake into the vampyre's chest!"; stake only -> "You're unable to push the stake far enough in!" + "The vampyre seems to regenerate!"; none -> regenerate line; heals to full | matches |
| 24 | Too slow | "he returns to his coffin and heals to full health" (Walkthrough note) | 500-tick lifespan despawn; reopen coffin to respawn (full HP) | diverges (D3) |
| 25 | Reward | 3 QP, 4,825 Attack XP (Reward) | `rewards { xp(attack, 4825) }`, QP from dbrow | matches |
| 26 | Varp 178 | 0 / 1 / 2 / 3 (Quest Helper; dbrow end state) | `STARTED=1`, `GOT_STAKE=2`, `COMPLETE=3`, init `check(maxSteps == 3)` | matches |
| 27 | Coffin outside the quest | Not on the wiki | "The coffin is sealed shut." | unsourced (declared) |
| 28 | Stake after the kill | Not on the wiki | Kept | unsourced (declared) |

## Divergences
### D1. Garlic is a one-shot effect at rise, not the attack-triggered, re-applying effect
- Severity: gameplay-visible
- Official: Count Draynor, "Strategy" and "Stat restoration": the garlic effect happens on the player's auto-attack
  (melee, or autocast magic) when the Count is at full health: 10 damage, Attack and Strength -10, Defence -40
  (to 0); his stats restore 1 point per 5 ticks; when Attack is back to full (50 ticks) the drain re-applies with
  "The vampyre seems to weaken."; when HP returns to full the 10 damage applies again; garlic also adds +1 to all
  the player's damage.
- Patch: `VampyreSlayerManor.openCoffin` applies the drain 2 ticks after rising if garlic is in the inventory, with
  `hitpoints` floored at 1 (no hitsplat) and Defence floored at 1; never again; no +1 damage.
- Fix: move the check to the player's attack on the Count (a combat hook on `npc.count_draynor` per hit / per
  attack-queue tick): if garlic held and the stat is at base, apply the drain (`hit` 10 with a hitsplat; Defence
  `coerceAtLeast(0)`), message on the first drain and "The vampyre seems to weaken." on Attack re-drains; add +1 to
  the player's damage against him while garlic is held.

### D2. No fast regeneration
- Severity: gameplay-visible (fight is much easier)
- Official: Count Draynor, Strategy: "about twenty times the rate of ordinary monsters" (Vampyre Slayer walkthrough
  says roughly 6 times; the sources disagree).
- Patch: engine default regeneration (declared caveat).
- Fix: a per-npc timer healing 1 HP every ~5 ticks (20x the 100-tick default; or the 6x figure) while the fight is
  live; confirm the rate from a capture.

### D3. Despawn instead of returning to the coffin
- Severity: gameplay-visible (minor)
- Official: Vampyre Slayer walkthrough note: "If you take too long to kill Count Draynor, he returns to his coffin
  and heals to full health." The time limit is not documented.
- Patch: `COUNT_LIFESPAN = 500` ticks then removed; reopening the coffin spawns a fresh one (full HP). Same net
  outcome, but the 500 ticks is a guess and there is no return-to-coffin animation.
- Fix: keep the despawn but source the timeout and play the rise animation in reverse / coffin anim
  (`seq.vampireslayer_coffin_open` 3113 exists in the cache) when he leaves.

### D4. Start-menu wording and the low-combat warning
- Severity: cosmetic
- Official: Transcript:Vampyre Slayer, Starting off: first menu option is "What else can you tell me about this
  vampyre?"; with combat level < 20 the prompt is preceded by "Before starting this quest, be aware that your combat
  level is lower than the recommended level of 20."
- Patch: `morganOptions` always shows "What else can you tell me about Count Draynor?"; no warning.
- Fix: pass the option label into `morganOptions` ("this vampyre" from `morganStart`, "Count Draynor" from
  `morganDuring`); before `startQuestPrompt`, `if (player.combatLevel < 20) mesbox(<warning>)`.

## Unsourced
- Garlic cupboard: "The cupboard contains garlic. You take a clove.", cupboard open duration (300 ticks), full-inv
  line "You don't have enough inventory space.".
- "The coffin is sealed shut." outside the quest (declared); player anim `human_openchest` on the coffin (the cache
  has `seq.vampireslayer_coffin_open` 3113, likely the right one).
- Count attack anim `seq.human_unarmedpunch` (declared).
- Keeping the stake after the kill (declared); kill using `deathWithDrops` (the wiki lists no drops).
- Lost-stake check also counting the bank.
- Whether Harlow's topic menu drops already-asked topics (transcript's "(Shows other options)"); the patch always
  lists all four.
- `vampireslayer_give_beer` (3116) not played on the hand-over.
- Fight ownership: only the opener's Count; other players' interaction not handled.

## Not implemented (scope gaps)
- Fast regeneration (declared), return to coffin (declared), garlic timing/re-application (declared as
  "simplified").
- Combat-level warning before the start prompt (not declared).
- Bartender Barcrawl and Treasure Trails lines (declared in the class doc).
- Nightmare Zone unlock (other content).
