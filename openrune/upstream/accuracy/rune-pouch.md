# Rune pouch (content/rune-pouch, patch 29, upstream PR #356)

Audited 2026-10-18 against the OSRS Wiki (pages: Rune pouch (intro, Interface, Changes, Trivia),
Divine rune pouch (intro, Interface, Changes), Transcript:Magic combat tutor), the cache/gamevals
(items 12791, 24416, 27281, 27509; interface 190; enum 982; varbits rune_pouch_type_1-6
29/1622/1623/14285/15373/15374, rune_pouch_quantity_n, selectedquantity 16201, customquantity 16206)
and RuneLite's Rune Pouch plugin (re/rlsrc) as secondary evidence. Report only.

## Summary
The storage model matches: 3 types for the rune pouch, 4 for the divine pouch, 16,000 per type, the
client-visible type/quantity varbits with the cache's rune compact ids (all 23 enum-982 runes, NMZ runes
excluded), Open/Empty ops and the 2025 1/5/X/All quantity buttons. 16 mechanics checked: 12 match,
1 diverges (cosmetic), 3 unsourced. No gameplay-visible divergence in what is implemented; the gaps are
scope: death handling (PvP loss / gravestone with runes kept), bank-side Empty/deposit and load-outs,
auto-store on pickup/runecraft/pickpocket, divine Revert, and Destroy dropping the contents. Every
player-visible message in the patch is a guess.

## Mechanics
| # | Mechanic | Official behaviour (source) | Patch behaviour (file:line or function) | Verdict |
|---|---|---|---|---|
| 1 | Pouch items | Rune pouch 12791 / (l) 24416, divine 27281 / (l) 27509 (Rune pouch, Divine rune pouch infoboxes; RuneLite RunepouchOverlay ids) | `regularPouches` bh_rune_pouch(+_trouver), `divinePouches` divine(+_trouver) | matches |
| 2 | Rune types per pouch | Rune pouch 3, divine 4 (comparison table) | `slotCount()` 3 / 4 | matches |
| 3 | Capacity | 16,000 per type (comparison table) | `CAPACITY = 16_000` | matches |
| 4 | Allowed runes | Runes, except Nightmare Zone runes (Rune pouch intro); client draws enum 982 (23 runes incl. combination, wrath 21880, sunfire 28929, aether 30843) | `rune_compact_ids` enum = same set; NMZ runes not in it | matches |
| 5 | Storage varbits | rune_pouch_type_n (compact id) + rune_pouch_quantity_n (RunepouchOverlay reads 6 pairs, enum 982) | `setSlot()` writes the same varbits | matches |
| 6 | Same rune stacks | Same type goes into its existing slot | `slotFor()` held slot first, else first free | matches |
| 7 | Item ops | Interface ops Open, Empty (op 4), Configure, Destroy (cache item 12791) | `onOpHeld1` open, `onOpHeld4` empty | matches |
| 8 | Interface | Storage interface 190 with pouch and inventory panels (Interface) | `ifOpenMainModal("interface.rune_pouch")`, events on pouch 0..5 and inventory | matches |
| 9 | Quantity buttons | 1, 5, X, All for deposit and withdraw; X usable as left-click since 9 April 2025 (Changes) | `selectQuantity()` on 1/5/x/all components, `varbit.rune_pouch_selectedquantity`/`customquantity` | matches |
| 10 | Op-to-quantity mapping | Not documented (clientscript) | Op1 selected, Op2 1, Op3 5, Op4 custom X, Op5 X prompt, Op6 All | unsourced |
| 11 | Drag reorder | Slots can be rearranged (interface has DragTarget) | `onIfModalDrag(POUCH)` swaps | matches |
| 12 | Rune on pouch | Use a rune on the pouch stores it (common behaviour; not stated on the page) | `onOpHeldU(rune, pouch)` stores all | unsourced |
| 13 | Store the pouch itself | "Don't be silly." when storing the rune pouch while the interface is open (Trivia) | Non-rune, incl. the pouch: "You can only store runes in the rune pouch." | diverges |
| 14 | Spell casting from the pouch | Spells use stored runes (intro) | Existing server code reads the varbits (PR text) | matches |
| 15 | Empty | Empties into the inventory (in a bank: into the bank) | Withdraws each slot into the inventory; stops on the first failure | matches (inventory case) |
| 16 | Messages | Not on the wiki | "You can only store runes in the rune pouch.", "Your rune pouch has no room for another type of rune.", "Your rune pouch cannot hold any more of those runes.", "You don't have enough inventory space.", "Your rune pouch is empty." | unsourced |

## Divergences

### D1. Storing the pouch itself
- Severity: cosmetic
- Official: "If a player attempts to store the rune pouch while the storage interface is open, the message "Don't be silly." appears in the chatbox." (Rune pouch, Trivia)
- Patch: `inventoryOp()` sends `NOT_A_RUNE` for every non-rune, including the pouch.
- Fix: in `inventoryOp()`, `if (internal in RunePouch.pouches) { mes("Don't be silly."); return }` before the rune check.

## Unsourced
- All five messages listed in row 16; need game captures (e.g. storing a 4th type in a 3-type pouch, a full 16,000 stack, withdrawing with a full inventory, Empty on an empty pouch, a non-rune click).
- The Op1-Op6 to quantity mapping and the `selectedquantity` values 0..3 (One/Five/All/Custom); needs the interface 190 clientscript (op labels set by the init script).
- Use-rune-on-pouch storing the whole stack (and whether using the pouch on a rune is also handled).
- Empty with too little space: whether OSRS empties what fits slot by slot (patch stops at the first slot that fails) and its message.

## Not implemented (scope gaps)
- Death: PvP death in the Wilderness/PvP worlds loses the pouch (and its runes) unless locked; PvM death sends it to the gravestone with the runes kept (since 21 June 2023); locked pouches stay in the inventory (Rune pouch intro, Changes). Not declared in the PR text.
- Bank: Empty/deposit into the bank, F2P-world deposits, Configure load-outs (10 since 1 October 2025, names, bank withdraw-into-pouch). Load-outs are declared ("No loadouts (Configure)"); bank Empty is not.
- Auto-store setting (pickup, runecrafting, pickpocketing, Hallowed Sepulchre, Sailing loot; toggle in Settings / Magic combat tutor). Not declared.
- Divine rune pouch creation (thread of Elidinis + needle, 75 Crafting) and Revert with the slot-4 warning. Not declared.
- Destroy: "If you destroy the pouch, all the contents will fall out." (item 12791 param 65482) i.e. the varbits must be cleared/dropped. Not declared.
- One rune pouch per player; Glarial's Tomb/Koschei only when empty. Not declared.
