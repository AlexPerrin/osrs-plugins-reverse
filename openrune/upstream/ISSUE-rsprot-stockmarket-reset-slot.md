# Issue for upstream RSProt: UPDATE_STOCKMARKET_SLOT_V2 reset writes slot 0

**Where:** blurite/rsprot, `osrs-241-desktop` `UpdateStockMarketSlotV2Encoder` (pin 4ec57fd7).

**What:** the `ResetStockMarketSlot` branch writes `buffer.p1(0)` where the slot belongs
(the `SetStockMarketSlot` branch writes `buffer.p1(message.slot)`). Every reset therefore empties
slot 0 on the client and leaves the intended slot as it was.

**Seen on:** the official Android client 241.4 (dispatcher case 66 reads the slot byte first, then
the status byte; status 7 = version prefix, as desktop). Device run 2026-10-17: collecting a
finished offer in slot 1 reset slot 0 on the client while slot 1 still showed the old offer.

**Fix:** `buffer.p1(message.slot)` in the reset branch (this repo: `server/rsprot.patch`).
