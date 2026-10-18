# Dwarf multicannon (content/cannon, patch 13, upstream PR #354)

Audited 2026-10-18 against the OSRS Wiki (pages: Dwarf multicannon (Assembly, Mechanics: Damage and
experience, Accuracy, Revolutions, Targets, Breaking/Decay/Death, Prohibited areas), Cannonball,
Steel cannonball, Cannon base, Transcript:Nulodion), the cache/gamevals (loc 6-9, 14916, obj 2/6/8/10/12,
varps 2-4, varbits 13672/1968/2180/6015, seq 827, spotanim 53) and, as secondary evidence, RuneLite's
Cannon, Timers & Buffs and the hub Supplies Tracker plugins (re/rlsrc, re/hub-src). Report only.

## Summary
The set-up sequence, the client-visible varps, the stage/pick-up/out-of-ammo messages, rotation speed,
max hit and XP rate match. The firing/decay model does not: 28 mechanics checked, 15 match, 10 diverge
(9 gameplay-visible, 1 cosmetic), 3 unsourced (row 14 is a scope gap, not scored). Most important fixes: (1) the 25-minute timer should
*break* the cannon (broken loc 14916, "Your cannon has broken!", Repair) and only decay 10 minutes later,
with reclaim from Nulodion instead of parts going straight to the bank; (2) a cannon must stay in the
world on logout/hop instead of being deleted and banked; (3) accuracy must use the player's accuracy
roll (equipment, style, prayers) against the target's heavy ranged defence, and single-combat zones
must restrict targets to the player's attacker; (4) the Dwarf Cannon quest is not required.
(Doc note: README says timers 440/441; the patch's gamevals.toml uses 431/432, as does the PR text.)

## Mechanics
| # | Mechanic | Official behaviour (source) | Patch behaviour (file:line or function) | Verdict |
|---|---|---|---|---|
| 1 | Set-up op | Base item op "Set-up" (Cannon base infobox); one cannon per player (Dwarf multicannon intro) | `onOpHeld1(twpart1)` -> `setUp()`; refuses if a cannon exists | matches |
| 2 | Quest requirement | Dwarf Cannon must be completed to assemble (Assembly; Cannon base) | No quest check in `setUp()` | diverges |
| 3 | Footprint | Vacant tile surrounded by 8 vacant tiles; player tile is the centre (Assembly) | origin = player -1,-1; `hasSpace()` checks 3x3 walk-blocked | matches |
| 4 | Assembly order/auto | Base, stand, barrels, furnace, assembled automatically (Assembly) | `buildStages()` loops PARTS in order | matches |
| 5 | Stage messages | "You place the cannon base on the ground." / "You add the stand." / "You add the barrels." / "You add the furnace." (RuneLite TimersAndBuffsPlugin constants; CannonPlugin) | `STAGE_MESSAGES` identical | matches |
| 6 | Set-up animation | 827 (Supplies Tracker checks BURYING_BONES while base spawns) | `seq.human_pickupfloor` = 827 | matches |
| 7 | Stage delay | Not documented | `SETUP_DELAY = 2` ticks per stage | unsourced |
| 8 | Varps | varp 2 stage (4 = placed), varp 3 ammo, varp 4 packed SW coord, varbit 13672 decayed (RuneLite CannonPlugin, overlay adds dx/dy 1 for centre) | `dropcannon`, `rockthrower`, `ownedmcannon` = packCoord(origin), `mcannon_decayed` | matches |
| 9 | World varbit | `varbit.mcannon_world` 1968 exists (cache); wiki: cannon stays on the world it was set up on | Never set | diverges (minor; tied to D2) |
| 10 | Ops on built cannon | Fire, Pick-up, Empty, Load X (loc 6 ops) | op1 fire, op2 pickUp, op3 empty, op4 load | matches (op4 see #12) |
| 11 | Capacity | 30, raised by Combat Achievement tiers up to 60 (Assembly) | `MAX_AMMO = 30` fixed | matches base / CA gap |
| 12 | Load X | "Load X" op asks a count (loc op name) | op4 loads everything, no count prompt | diverges |
| 13 | Load message | Not on wiki | "You load the cannon with N cannonball(s)." | unsourced |
| 14 | Ammo types | Steel and granite; granite loaded first, granite must be used up before steel (Assembly; Cannonball) | Only `obj.mcannonball` | scope gap |
| 15 | Rotation | 8 directions, 45 deg per tick, at most one ball per direction, 8 per revolution (Revolutions) | `fireTick()` every tick, one direction step, one target max | matches |
| 16 | Target region | Per-direction triangle of tiles from the barrel, overlapping (double-shot tiles), cut off by obstacles/line of sight (Targets) | `directionOf()` = rounded atan2 octant from centre, Chebyshev 1..8, no line-of-sight, no overlap | diverges |
| 17 | Large NPCs | Only the SW tile is targeted (Targets) | Uses `npc.coords` (SW tile) | matches |
| 18 | Single-combat rule | In single-way, fires only at the NPC attacking the player; free targeting only when not under attack (Revolutions, Targets) | No single/multi check; any attackable NPC | diverges |
| 19 | Accuracy | Player's accuracy roll for current weapon type/style incl. equipment bonuses and prayers (powered staves: higher of ranged/melee) vs target's heavy ranged defence; no on-task/slayer-helm style boosts (Accuracy) | `rollHit()`: (rangedLvl+8)*64 vs (defence level+9)*64; no gear, style, prayer or NPC ranged defence bonus | diverges |
| 20 | Max hit | Static 30 steel / 35 granite (Damage and experience; Cannonball) | `random.of(0, 30)` | matches (steel) |
| 21 | XP | 2 Ranged XP per damage, no Hitpoints XP (Damage and experience) | `damage * 2.0` ranged only | matches |
| 22 | Out of ammo | "Your cannon is out of ammo!" (RuneLite CannonPlugin) | same, stops firing | matches |
| 23 | Break at 25 min | Breaks after 25 min set up (loc 14916 "Broken multicannon", Repair op), "<col=ef1020>Your cannon has broken!", repair: "You repair your cannon, restoring it to working order." (Breaking/Decay/Death; Timers & Buffs) | No break; at 2500 ticks `decay()` removes it | diverges |
| 24 | Decay | 10 min after breaking it disappears; "Your cannon has decayed. Speak to Nulodion to get a new one!" (Supplies Tracker); reclaim from Nulodion with the same loaded balls (Breaking/Decay/Death; Transcript:Nulodion) | Parts and balls to bank, "Your cannon has decayed. Its parts have been returned to your bank." | diverges |
| 25 | Logout / world hop / death | Cannon stays where it was until picked up or decayed (Breaking/Decay/Death) | `onPlayerLogout` deletes the loc; `onPlayerLogin` banks parts | diverges |
| 26 | Pick-up | "You pick up the cannon. It's really heavy." (Timers & Buffs, Supplies Tracker) | same; returns parts + balls | matches |
| 27 | Empty | "You unload your cannon and receive Cannonball x N" (Supplies Tracker prefix "You unload your cannon and receive Cannonball") | "You unload N cannonballs from your cannon." | diverges (cosmetic) |
| 28 | Prohibited areas | Long list with per-area messages; GE: "The Grand Exchange staff prefer not to have heavy artillery operated around their premises." (Prohibited areas) | GE only, message verbatim; box 3139-3190/3463-3515 | matches (GE) / scope gap |
| 29 | Other messages | - | "You cannot construct more than one cannon at a time.", "There isn't enough space to set up here.", "This isn't your cannon!", "Your cannon is already firing.", "Your cannon is already full.", "You don't have any ammo to load into the cannon.", "Your cannon is empty.", inventory-space lines | unsourced |

(Scored: 28 rows; row 14 is a scope gap.)

## Divergences

### D1. No broken state; decays at 25 minutes
- Severity: gameplay-visible
- Official: after 25 min continually set up the cannon breaks and stops firing ("<col=ef1020>Your cannon has broken!", RuneLite Timers & Buffs `CANNON_BROKEN_MESSAGE`); the player clicks Repair ("You repair your cannon, restoring it to working order.") which restarts the 25-min timer; if not repaired within 10 more minutes it disappears (Dwarf multicannon, Breaking/Decay/Death). Broken locs: 14916 (`dwarf_multicannon1_broken`, ops Repair, Pick-up); the base/stand/barrels stages simply decay.
- Patch: `DECAY_CYCLES = 2500`, `decay()` deletes the cannon at 25 min.
- Fix: on the 2500-tick timer, if stage 4, stop firing, swap the loc to `loc.dwarf_multicannon1_broken`, send the broken message, start a 1000-tick decay timer; add `onOpLoc1(dwarf_multicannon1_broken)` = repair (swap back, message, restart 2500) and `onOpLoc2` = pick-up. Partial stages decay at 2500+1000 (or as sourced).

### D2. Decay/loss returns parts to the bank instead of Nulodion
- Severity: gameplay-visible
- Official: decay message "Your cannon has decayed. Speak to Nulodion to get a new one!" (Supplies Tracker); Nulodion "I've lost my cannon." replaces it free, loaded with the balls that were in it (Breaking/Decay/Death; Transcript:Nulodion, incl. "Hmmm. I think you'll find it's still happily parked on the spot where you put it." if still set up, and the 4-free-slots branch).
- Patch: `reclaimToBank()` adds parts and balls to `inv.bank`; message "...Its parts have been returned to your bank."
- Fix: on decay set `mcannon_decayed` = 1, keep `dropcannon`/`rockthrower` as the record of what is owed, use the official message, and add Nulodion's "I've lost my cannon." branch that gives the parts (and balls) and clears the varps. Remove the login bank reclaim.

### D3. Cannon removed on logout
- Severity: gameplay-visible
- Official: "Even if the player switches worlds, it will remain where it was on the previous world until returning to pick it up or until 35 minutes have passed and decay occurs." Death also leaves it in place (Breaking/Decay/Death).
- Patch: `onPlayerLogout { removeCannon() }` deletes the loc; `onPlayerLogin { reclaimToBank() }`.
- Fix: keep the `Cannon` keyed by player account (not the `Player` instance), leave the loc and a world-level decay timer running after logout, re-bind on login (same world), set `varbit.mcannon_world`.

### D4. Accuracy formula ignores equipment, style, prayers and NPC ranged defence
- Severity: gameplay-visible
- Official: uses the player's accuracy for the current weapon/style (ranged: ranged level, ranged attack bonus, Rigour etc.; melee: attack level, stab/slash/crush bonus of the selected style, Piety etc.; unarmed = crush; powered staves = higher of ranged/melee), against the target's heavy ranged defence; no target-conditional boosts (Dwarf multicannon, Accuracy).
- Patch: `rollHit()` uses `(rangedLvl + 8) * 64` vs `(visType.defence + 9) * 64`.
- Fix: reuse the server's player accuracy roll for the equipped weapon/style (as normal combat computes it, minus slayer/salve-type conditionals), and the NPC defence roll with its heavy ranged defence bonus: `(def + 9) * (rangedHeavyDef + 64)`.

### D5. Single-combat targeting not enforced
- Severity: gameplay-visible
- Official: in single-way combat the cannon fires only at the monster attacking the player; if not under attack it fires at anything (Revolutions; Targets).
- Patch: `findTarget()` takes any attackable NPC in the octant.
- Fix: if the cannon centre is not in a multiway area and the owner is under attack (e.g. `player.lastCombat`/attacker), filter candidates to that attacker; also skip NPCs already in combat with another player in single-way.

### D6. Target region and line of sight
- Severity: gameplay-visible
- Official: per-direction triangular tile sets with overlaps (tiles adjacent to the 3x3 corners get two shots per revolution), cut off by obstacles (Targets, diagram).
- Patch: one rounded-atan2 octant per NPC (no overlap), Chebyshev range 8, no line-of-sight check, nearest first.
- Fix: encode the wiki's per-direction tile masks (8 masks relative to the centre) and test `lineOfSight(center, npc)` via the route/collision map.

### D7. Quest not required
- Severity: gameplay-visible
- Official: Dwarf Cannon must be completed to set up (Assembly; Cannon base).
- Patch: no check.
- Fix: in `setUp()` require the Dwarf Cannon quest complete (`varp.mcannon` final stage) and show the official refusal (to be sourced).

### D8. Load X loads everything
- Severity: gameplay-visible (minor)
- Official: op 4 is "Load X" (cache loc 6), i.e. a count prompt.
- Patch: op4 = `load(..., startFiring = false)` with all balls.
- Fix: `countDialog` then load `min(n, room, inv)`.

### D9. World varbit not set
- Severity: gameplay-visible (minor; plugins use their own world tracking, but the cache keeps `mcannon_world`)
- Official: `varbit.mcannon_world` (1968) exists; cannon is per world.
- Patch: not set.
- Fix: set it at base placement, clear on pick-up/reclaim (fold into D3).

### D10. Empty message wording
- Severity: cosmetic (plugin-visible: Supplies Tracker matches the prefix)
- Official: "You unload your cannon and receive Cannonball x N" (Supplies Tracker; granite: "...receive Granite cannonball").
- Patch: "You unload N cannonballs from your cannon."
- Fix: `mes("You unload your cannon and receive Cannonball x ${cannon.ammo}.")` (confirm trailing punctuation in game).

## Unsourced
- Load message "You load the cannon with N cannonball(s)." (and "1 cannonball"): the format and the singular form (historically "one cannonball") need a game capture.
- "You cannot construct more than one cannon at a time.", "There isn't enough space to set up here.", "This isn't your cannon!", "Your cannon is already firing.", "Your cannon is already full.", "You don't have any ammo to load into the cannon.", "Your cannon is empty.", and the inventory-space lines for empty/pick-up: need transcripts or captures.
- `SETUP_DELAY = 2` ticks between stages and the extra "no other cannon within SIZE+2 tiles" spacing rule.
- `RANGE = 8` and nearest-target preference; the GE rectangle 3139-3190/3463-3515 (should come from the official area, e.g. a cache area/map polygon).
- Whether the decay timer runs from base placement or from the completed cannon (Cannon base page says a lone base also decays; exact timing not stated).

## Not implemented (scope gaps)
- Granite cannonballs (35 max hit, load priority, `mcannon_balltype`), ornamented (league) cannon locs. Not declared in the PR text.
- Combat Achievement capacity increases (up to 60). Not declared.
- All prohibited areas except the GE, with their per-area messages. Not declared.
- Bosses destroying cannons ("Your cannon has been destroyed!"), immune NPCs (demonic gorillas, sand/ammonite crabs). Not declared.
- Nulodion "I've lost my cannon." reclaim (needed for D2). Not declared.
- The "push the cannon from its south-west tile" behaviour after loading and moving away. Not declared.
