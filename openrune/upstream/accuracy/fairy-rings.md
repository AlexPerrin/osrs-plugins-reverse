# Fairy rings (content/fairy-rings, patch 26, unpushed)

Audited 2026-10-18 against the OSRS Wiki (pages: Fairy ring (intro, Using fairy rings,
Combinations, Sequences, Unused combinations, Changes, Trivia), Fairy ring (Zanaris), Zanaris,
Dramen staff, Lunar staff, Lunar staff - pt1) and the cache/gamevals (interfaces `fairyrings` 398 /
`fairyrings_log` 381; items 772 Dramen staff, 9084 Lunar staff = `obj.lunar_moonclan_liminal_staff`,
9091-9093 Lunar staff - pt1..pt3 = `obj.dramen_staff_air/fire/water`). The fairy ring code list
itself was not re-checked row by row: the patch takes codes and destinations from Jagex's own
`fairyring` dbtable. No transcript exists. Report only. Lines are lines of
`content/travel/fairy-rings/src/main/kotlin/.../FairyRingScript.kt` (`FairyRingScript.kt:N`).
The live tree (openrune/openrune.patch) carries a byte-identical `FairyRingScript.kt`.

## Summary
The interaction model matches OSRS (left-click Zanaris, Configure dial + travel log, both-way dials,
log click enters the code, Last-destination with the op text, the two verbatim messages, the no-move
rule, the no-staff Zanaris return). 25 mechanics checked: 13 match, 2 diverge (both
gameplay-visible), 4 unsourced, 6 scope rows. The two fixes: the **Lunar staff (9084) is not
accepted** (D1, only the Dramen staff and the three unfinished Lunar staff stages are), and **no
quest gate** (D2: Fairytale I complete + Fairytale II partly done are required to use the network).
The Lumbridge & Draynor elite diary exemption is missing and not declared. Both divergences are the
same in the live tree (2/2).

## Mechanics
| # | Mechanic | Official behaviour (source) | Patch behaviour (file:line or function) | Verdict |
|---|---|---|---|---|
| 1 | Dramen staff | Wielding a dramen staff allows use (Fairy ring intro; Dramen staff) | `STAVES` has `obj.dramen_staff` (`FairyRingScript.kt:166-167`), checked on the right hand | matches |
| 2 | Lunar staff | "while wielding either a dramen staff or lunar staff" (Fairy ring intro; Lunar staff; Zanaris) | 9084 `lunar_moonclan_liminal_staff` not in `STAVES` | diverges (D1) |
| 3 | Unfinished lunar staves | "An unfinished lunar staff at any stage ... will still function as a regular dramen staff when using fairy rings" (Lunar staff - pt1) | 9091-9093 (`dramen_staff_air/fire/water`) in `STAVES` | matches |
| 4 | Elite L&D diary | No staff needed after the elite Lumbridge & Draynor Diary (intro; Changes 23 April 2015) | Not checked | not implemented (not declared) |
| 5 | Quest gate | Fairytale I must be completed and Fairytale II started up to permission (Using fairy rings; intro) | No check: anyone wielding a staff can use the network | diverges (D2) |
| 6 | No-staff message | "The fairy rings only work for those who wield fairy magic." (Trivia) | `NO_STAFF`, verbatim (`FairyRingScript.kt:155`) | matches |
| 7 | Invalid / same-ring code | No teleport, player exclaims "Wow, fairy magic sure is useful, I hardly moved at all!" (intro) | `travel(null or here)` -> `say(HARDLY_MOVED)` overhead, verbatim (`FairyRingScript.kt:117-132,154`) | matches |
| 8 | Left-click | "The left-click option on fairy rings now teleports players to Zanaris" (Changes 29 Sep 2016) | `onOpLoc1(MINOR_HUB) { zanaris() }` (`FairyRingScript.kt:36`) | matches |
| 9 | Configure | Right-click Configure selects the destination; code selection screen + travel log pop up (Changes 29 Sep 2016; Using fairy rings) | op2 opens 398 + 381 (`configure()`, `FairyRingScript.kt:51-70`) | matches |
| 10 | Dials | Dials can be rotated in both directions (Changes 28 Nov 2013) | clockwise +1, anticlockwise +3 mod 4 (`turn()`) | matches |
| 11 | Travel log | Remembers each ring visited and lists it; clicking a code enters it (Changes 28 Nov 2013, 9 Oct 2024; intro) | Visited bit per ring in two server varps, `ifSetText` for visited rows; `useCode()` sets the dials | matches |
| 12 | Last-destination | Option sends you to the last destination; it displays the last code used (Changes 22 Sep / 6 Oct 2016) | op3 via `fairyring_lastloc_opvis` = 64 + multiloc state (`lastDestination()`) | matches |
| 13 | Staff for Last-destination/Zanaris | Network use needs the staff, except returning to Zanaris from rings only reachable by the network (Trivia) | `lastDestination()` needs a staff; `zanaris()` allows rows with `noStaffReturn` (cache column) | matches |
| 14 | Codes and destinations | 55 working codes of 64; unused AIP, BIR, BJQ, CJP, CJS, CLQ, DJQ, DJS, DKQ (Combinations; Unused combinations) | Jagex's `fairyring` dbtable; placeholder rows (x <= 65) treated as no ring (`isDestination`) | matches |
| 15 | Zanaris code | BKS is the Zanaris portal (Zanaris) | `ZANARIS_CODE = "B K S"` | matches |
| 16 | Dial interface ids | 398 dial, 381 travel log (gamevals) | `interface.fairyrings` / `fairyrings_log` | matches |
| 17 | Per-ring requirements | e.g. AJQ Death to the Dorgeshuun, BJR Holy Grail, BJS Regicide, AIS/AJP Children of the Sun travel, AKP Beneath Cursed Sands (Combinations tables) | Not checked | not implemented (declared) |
| 18 | Hideout sequence | AIR, DLR, DJQ, AJS in order reach the Fairy Resistance Hideout (Sequences) | DJQ is a "no ring" code | not implemented (declared) |
| 19 | Favourites | Up to 10 favourite codes (Changes 1 Oct 2025); "Favourites" op when one is marked (Changes 1 Apr 2026) | None; the log's faves component is only passed to the sort script | not implemented (declared) |
| 20 | Teleport NPC spawns | AJS, AKQ, BIQ/DLQ, DIP, CLR, DLR, DIQ: 1/6 chance of a penguin/chinchompa/jackal/... following you (Trivia, Mod Ash 24 Feb 2021) | None | not implemented (not declared, cosmetic) |
| 21 | Off-hand hidden | Off-hand item momentarily disappears during the teleport (Trivia) | Not done | not implemented (not declared, cosmetic) |
| 22 | Teleport timing / anims | Not documented (wiki has the GIF only) | `human_fairy_vanish` + `fairy_flower_ring` spotanim, 3 ticks, telejump, `human_fairy_appear` (`travel()`) | unsourced |
| 23 | "Same ring" detection | Not documented | Ring whose destination is within Chebyshev 3 on the same level (`ringAt()`, `FairyRingScript.kt:134-137`) | unsourced |
| 24 | Search input | Official client opens the log's search box on interaction (Changes 10 Dec 2025) | Server closes meslayer mode 28 on teleport (`confirm()`) | unsourced (client-side behaviour, our workaround) |
| 25 | Last-destination with none set | Not documented (op hidden when no last destination via opvis) | Silently returns when the state matches no row | unsourced |

## Divergences
### D1. Lunar staff not accepted
- Severity: gameplay-visible
- Official: Fairy ring intro: "Players can access the fairy ring system from any fairy ring while
  wielding either a dramen staff or lunar staff"; Lunar staff: "As an enhanced dramen staff, the
  lunar staff can similarly be used to access Zanaris and the fairy ring network"; the no-staff
  message in Trivia lists the lunar staff as one of the three ways.
- Patch: `STAVES = {dramen_staff, dramen_staff_air, dramen_staff_fire, dramen_staff_water}`; the
  last three are Lunar staff - pt1..pt3 (9091-9093), not the finished Lunar staff 9084.
- Fix: add `"obj.lunar_moonclan_liminal_staff"` to `STAVES` (`FairyRingScript.kt:166-167`). The PR
  text already claims "Configure (Dramen or Lunar staff)", so the code is behind its description.
- Live tree (openrune.patch): same (identical `FairyRingScript.kt`).

### D2. No Fairytale quest gate
- Severity: gameplay-visible
- Official: Fairy ring, Using fairy rings: the quests The Restless Ghost, Priest in Peril, Nature
  Spirit, Lost City, Fairytale I - Growing Pains, and "Partially completed Fairytale II - Cure a
  Queen (up until you're 'granted permission' to use them)"; intro: "Fairytale I - Growing Pains
  must be completed to access the fairy rings".
- Patch: `configure()`, `zanaris()` and `lastDestination()` only check the staff.
- Fix: gate the three ops on the Fairytale II progress varp (≥ the "granted permission" stage),
  with OSRS's refusal line (needs a source; not on the wiki). If OpenRune has no Fairytale quests,
  gate on a server-side unlock flag defaulting to false, or declare the gap in the PR caveats. The
  Zanaris exit rings (Fairy ring (Zanaris)) need no quest and are outside this patch.
- Live tree (openrune.patch): same (identical `FairyRingScript.kt`; the live tree has Fairytale II
  item gamevals but no quest script).

## Unsourced
- Teleport sequence: vanish anim + flower-ring spotanim, 3-tick delay, then appear anim; needs a
  capture of the official timing (ticks between click and arrival).
- Same-ring detection radius (Chebyshev 3 from the ring's destination tile).
- Closing meslayer mode 28 on teleport: a client workaround, not an observed official server
  action (the official client opens the search box itself since 10 December 2025).
- Last-destination with no previous code: silently does nothing (the op should be hidden by
  `fairyring_lastloc_opvis` anyway).
- Whether "hardly moved" also plays the vanish/appear animations and the spotanim (the patch does;
  the wiki only says the player is not teleported and exclaims the line).

## Not implemented (scope gaps)
- Favourites (10 slots, the "Favourites" op): declared.
- Fairy Resistance Hideout sequence (AIR, DLR, DJQ, AJS): declared.
- Per-ring quest requirements (AJQ, BJR, BJS, AIS/AJP, AKP, CJQ/DLP Sailing, ...): declared.
- Elite Lumbridge & Draynor Diary staff exemption: **not declared** (the live tree has
  `diary_tasks_lumbridge_elite`, so a hook is possible there).
- 1/6 ambient NPC spawns at seven rings and the off-hand item hiding: not declared, cosmetic.
- Deadman teleport delay: not applicable.
