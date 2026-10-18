# Plank sack (content/plank-sack, patch 30, upstream PR #357)

Audited 2026-10-18 against the OSRS Wiki (pages: Plank sack (intro, Changes), Log basket (for the
sibling container's behaviour), Mahogany Homes navbox; no Transcript:Plank sack exists), the
cache/gamevals (item 24882 ops, varbits plank_sack_plain/oak/teak/mahogany/camphor/ironwood/rosewood
18171-18177) and the hub Plank Sack plugin (re/hub-src/plank-sack, reads the same varbits) as
secondary evidence. Report only.

## Summary
What is implemented matches: 28 planks of any type, the seven client-visible varbits, the Fill/Check/
Empty ops and planks usable straight from the sack. 12 mechanics checked: 8 match, 0 diverge,
4 unsourced (all messages, the Check output format, use-plank-on-sack, inventory-first consumption).
The notable gaps are scope: the sawmill auto-fill (since 1 November 2023; declared), bank-interface
Fill/Empty into the bank (27 August 2025 behaviour; not declared), and death handling (not declared).

## Mechanics
| # | Mechanic | Official behaviour (source) | Patch behaviour (file:line or function) | Verdict |
|---|---|---|---|---|
| 1 | Item and ops | Plank sack 24882, ops Fill, Check, Empty, Drop (infobox; cache interfaceOptions [Fill, null, Check, Empty, Drop]) | `onOpHeld1` fill, `onOpHeld3` check, `onOpHeld4` empty on `obj.plank_sack` | matches |
| 2 | Capacity | 28 planks total, any mix of types (intro) | `CAPACITY = 28`, `freeSpace()` over all types | matches |
| 3 | Plank types / storage | Planks of any type; client varbits plank_sack_plain/oak/teak/mahogany/camphor/ironwood/rosewood (gamevals; hub plugin reads them) | `PlankSack.planks`: woodplank, oak, teak, mahogany, camphor, ironwood, rosewood on those varbits | matches |
| 4 | Fill | Fills from the planks in the inventory (intro) | `fill(planks)`: every held type, until full | matches |
| 5 | Use plank on sack | Not documented | `onOpHeldU(plank, sack)` fills only that type | unsourced |
| 6 | Check output | Not documented on the wiki (no transcript) | One game message per non-empty type: "Regular planks: N", ... ; empty: "Your sack is currently empty." | unsourced |
| 7 | Empty (no bank) | Empties into the inventory (Changes, 23 September 2020: shift-click empties into the inventory) | `empty()` adds as many as fit per type | matches |
| 8 | Use from the sack | Planks "can be used directly from the sack", no need to empty (intro; Mahogany Homes, Construction) | `PlankSackStorageHook` exposes stored planks to `PlayerItemStorage` consumers in the inventory | matches |
| 9 | Consumption order | Not documented | `VirtualItemConsumePolicy.InventoryFirst` | unsourced |
| 10 | Servants | Servants never deliver planks into the sack (intro) | Hook `add()` returns 0; nothing adds to the sack except Fill | matches |
| 11 | Weight | Planks inside weigh nothing (intro) | Stored in varbits, not an inventory, so no weight | matches |
| 12 | Messages | Not documented | "You don't have any planks to put in the sack.", "Your sack is full.", "You don't have enough inventory space to empty the sack.", "Your sack is currently empty." | unsourced |

## Divergences
None found in the implemented behaviour.

## Unsourced
- All four messages in row 12, and the Check wording/format in row 6. The type label "Regular planks" is a guess: the cache/varbit calls them "plain" and the hub plugin labels them "Basic planks"; whether Check prints one combined line (e.g. "Basic planks: N, Oak planks: N, ...") or a dialogue needs a game capture.
- Use-plank-on-sack filling only that type; Fill order (patch: plain, oak, teak, mahogany, camphor, ironwood, rosewood).
- Inventory-first consumption by the storage hook (whether OSRS takes sack planks first, e.g. at Mahogany Homes/hotspots).
- Empty with too little space (patch empties per type as far as space allows, then says no space).

## Not implemented (scope gaps)
- Sawmill auto-fill: "Planks from the sawmill are now automatically placed in the plank sack." (Changes, 1 November 2023). Declared in README ("the sawmill does not fill the sack").
- Bank interface: Use/Fill/Empty while the bank is open fills from the inventory or empties the contents (Fill or Empty shown depending on inventory since 27 August 2025). Not declared.
- Death: "the plank sack retains its contents, but it is sent to your gravestone or lost to the player who kills you" (intro); a lost sack must clear the varbits. Not declared.
- Only one plank sack may be owned (intro); purchase from the Mahogany Homes Reward Shop (350 carpenter points). Not declared (shop may be outside the PR's scope).
- Hallowed Sepulchre and Shipbuilding use of sack planks depend on those features using `PlayerItemStorage`; not checked here.
