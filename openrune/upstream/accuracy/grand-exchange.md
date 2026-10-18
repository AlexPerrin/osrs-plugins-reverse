# Grand Exchange (content/grand-exchange, patches 48, 50, 51, 52, unpushed)

Audited 2026-10-18 against the OSRS Wiki (pages: Grand Exchange (intro, Operation, Guide prices, Buy limits,
Convenience fee and item sink, Exempt from tax, Changes), Grand Exchange/Buying limits, Grand Exchange/Non-tradeable
items, Shark, Ranarr weed) and the LIVE cache (Grand Exchange Clerk ops). Read from the branch head
(`GrandExchange.kt`, `GrandExchangeScript.kt`, `ge_buy_limits.toml`, `ge_tax_exempt.toml`). Report only.

## Summary
The core mechanics that can be sourced match: tax (2%, floored, per item, 5M cap, seller-side, on the actual trade
price), the 48-item tax-exempt list (exact match to the wiki section), 4-hour buy limits timed from the first
item bought, buy limit values (spot checks match), history size 40, the abort message, trade-price rule (a newer
offer trades at the older offer's price, here the server's guide). Necessary divergence: the server is the only
counterparty at a store-cost guide price. 4 divergences (3 gameplay-visible, 1 cosmetic): buy offers can't use bank
coins (OSRS since 14 June 2023), the 16-entry limit tracker can evict a live limit, offers fill only while the
player is online, and no login notice. The partial-fill pacing and most error strings are unsourced.

## Mechanics
| # | Mechanic | Official behaviour (source) | Patch behaviour (file:line or function) | Verdict |
|---|---|---|---|---|
| 1 | Clerk ops | Talk-to, Exchange (op3), History (op4), Sets (op5) (LIVE cache npc 2148) | `startup`: onOpNpc3 = Exchange, onOpNpc4 = History | matches |
| 2 | Offer slots | Members 8, F2P 3 (Grand Exchange, intro) | `SLOTS = 8` for everyone (OpenRune has no F2P/members split) | matches (members) |
| 3 | Counterparty / guide price | Other players; guide from trade history, updates every 3 h (Guide prices; Changes 30 Sep 2026) | Server fills at `MarketPrices` (store cost) | diverges (necessary, declared) |
| 4 | Trade price | Trade goes at the price of the offer that was there first; buyer gets change back (Operation) | Buy >= guide fills at guide with refund to coins slot; sell <= guide gets guide (`fillStep`) | matches |
| 5 | Initial setup price = guide | "the initial price is set to a guide value" (Guide prices) | `selectItem` -> `sendSetupPrice(item.guide)` | matches |
| 6 | Tax rate / rounding | 2%, rounded down; below 50 gp none (Convenience fee) | `taxPerItem`: `price / 50`, 0 below 50 | matches |
| 7 | Tax cap | 5M per item (Convenience fee) | `minOf(price / 50, TAX_CAP)` per item | matches |
| 8 | Tax payer / basis | Seller, on the trade price | Sale proceeds minus `amount * taxPerItem(item, guide)` | matches |
| 9 | Tax-exempt list | 48 objs in 6 groups (Exempt from tax) | `ge_tax_exempt.toml` 48 objs, all names map; Energy potion as all 4 doses | matches (doses unsourced) |
| 10 | Buy limit window | 4 h from the first item bought, full reset (Buying limits) | `recordBought`/`activeTracker`: window starts at first fill, 240 min | matches |
| 11 | Buy limit values | Per-item table (Buying limits; Shark 10,000, Ranarr weed 11,000, Abyssal whip 70, Lobster 6,000) | `ge_buy_limits.toml` (4186 objs, no duplicate keys; samples match) | matches |
| 12 | Connected limits (e.g. potion doses) | Shared limit across doses (Buying limits) | Not done | not implemented (declared) |
| 13 | No limit on selling | (Buy limits) | Limits only on buys (`fillStep`) | matches |
| 14 | Limit tracking capacity | No cap on tracked items | 16 trackers; the 17th item overwrites the oldest even if its window is live (`recordBought`) | diverges (D2) |
| 15 | Buy-offer payment | Inventory coins first, then bank coins (Changes 14 June 2023); platinum tokens too (30 Sep 2026) | Inventory coins only, else "You don't have enough coins." (`confirm`) | diverges (D1); platinum declared |
| 16 | "Buy all you can afford" | Button added 13 Dec 2022 | ENTER_QUANTITY Op2 = inventory coins / price (`setupOp`) | diverges (D1, bank coins ignored) |
| 17 | Max offer value | 2,147,483,647 coins before 30 Sep 2026; up to 2,149,631,130,647 since | `MAX_TOTAL = Int.MAX_VALUE`, "That offer is worth too much money." | diverges (declared: no platinum tokens); message unsourced |
| 18 | Sell warning below 10% of guide | Warning since 13 Dec 2022 (Changes) | None | not implemented |
| 19 | Custom +x% / -x% | Right-click to set (Changes 23 May 2024) | Fixed +-5% only; other buttons "That button is not available on this server." | not implemented (declared); message ours |
| 20 | +-5% / quantity buttons | Client scripts 777/778 | `changeQuantity`/`changePrice` mirror them | matches |
| 21 | Last searched item | Remembered until new search or logout (Changes 8 Apr 2020) | `objDialog(..., showLastSearched = true)` | matches |
| 22 | Non-GE items refused | Some tradeables can't go on the GE (Non-tradeable items) | `tradeable()` = unnoted form's `stockmarket` flag | matches; messages unsourced |
| 23 | Fill pacing | Not documented; offers at/through the market usually fill at once | Up to 4 parts: 2 ticks after Confirm, then every 5 ticks (`FILL_*`) | unsourced |
| 24 | Partial/finished messages | "a message in their chatbox when the status of a trade offer is updated" (intro) | "Grand Exchange: Bought/Sold n / total x Item." and "Grand Exchange: Finished buying/selling n x Item." | unsourced (wording) |
| 25 | Offline progress | Offers trade while logged out (intro: no need to wait at the GE) | Fill timer is a soft timer; fills only while logged in, resume on login | diverges (D3) |
| 26 | Abort | Remaining items/coins go to collection; message "Abort request acknowledged. Please be aware that your offer may have already been completed." | `abort`, `abortSelected` | matches (message per in-game text) |
| 27 | Collection | Collect at the GE or any bank; notes / items / bank | Per-slot invs from enum 150; Op1 notes if >1, Op2 item, Op3 bank; Collection Box (402) at bankers | matches |
| 28 | History length | 40 entries (Changes 7 Mar 2019) | `HISTORY_SIZE = 40`, newest first | matches |
| 29 | Buy from history | Buy offers from the history (Changes 25 Aug 2016) | `historyOp` | matches |
| 30 | History entry timing / aborted-empty | Repeat Confirm no longer adds entries (7 Mar 2019); exact moment not documented | Recorded when finished or aborted, only if something traded | unsourced |
| 31 | Ironman GE | Bonds only (Ironman mode) | Not distinguished | not implemented |
| 32 | Item sets (clerk op5) | Sets interface (Item sets) | Not done | not implemented (declared) |
| 33 | Login notice | Updated offers are announced (intro); exact login line not on the wiki | None | diverges (D4, cosmetic) |

## Divergences
### D1. Buy offers ignore bank coins
- Severity: gameplay-visible
- Official: Grand Exchange, Changes, 14 June 2023: "Buy offers can now use the coins from the player's bank as
  payment; coins in the player's inventory take precedence ... any more coins required ... are taken from the bank."
  The "Buy all that you can afford" button (13 Dec 2022) therefore counts bank coins too.
- Patch: `confirm()` checks `inv.count("obj.coins") < total` and deletes only from `inv`; `setupOp` ENTER_QUANTITY
  Op2 divides inventory coins only.
- Fix: in `confirm`, take `min(invCoins, total)` from `inv` and the rest from `bank` (`invDel(bank, "obj.coins", ...)`),
  failing only when `inv + bank < total`; use the same sum for "buy all you can afford".

### D2. The 16-entry buy-limit tracker can drop a live limit
- Severity: gameplay-visible
- Official: Grand Exchange/Buying limits: every item's limit holds for 4 hours from its first purchase; nothing
  resets early.
- Patch: `recordBought` with no free or expired tracker overwrites `minBy { start }`, i.e. the oldest still-running
  window, so buying a 17th distinct limited item within 4 hours resets the limit of the first.
- Fix: grow the tracker set (e.g. a varp-backed map or more entries), or when full refuse to fill a new limited
  item until a tracker expires (treat as LimitBlocked) rather than evicting a live one.

### D3. Offers only progress while the player is online
- Severity: gameplay-visible
- Official: Grand Exchange (intro): traders don't need to wait at the GE; offers fill while logged out (paused only
  after several days offline).
- Patch: `timer.ge_offer_fill` is a soft timer started on Confirm/login, so a player who logs out right after
  Confirm finds nothing filled until the next login and then waits the 2/5-tick steps again.
- Fix: on login, apply all fills that would have happened since logout (store a last-fill epoch minute per slot and
  catch up, `fillOpenOffers` in a loop), then send the slots; or fill the server-matched part at Confirm time.

### D4. No login notice of updated offers
- Severity: cosmetic
- Official: Grand Exchange (intro): "players will receive a message in their chatbox when the status of a trade
  offer is updated"; the exact login line is not on the wiki.
- Patch: `onPlayerLogin` sends the slots only.
- Fix: after D3's catch-up, send a line when any slot changed while offline (wording needs a capture).

## Unsourced
- Fill pacing: 4 parts, 2 ticks after Confirm, 5-tick spacing, 100-tick recheck for limit-blocked offers (needs a
  timed capture of a market-priced offer on live).
- Message wording: "Grand Exchange: Bought n / total x Item.", "Grand Exchange: Sold ...", "Grand Exchange: Finished
  buying/selling n x Item." (plausible; needs a chat capture or RuneLite chat-pattern source).
- Error lines: "You can't buy that item on the Grand Exchange.", "You can't sell that item on the Grand Exchange.",
  "You have no free offer slots.", "That offer is worth too much money.", "You don't have enough of that item.",
  "You don't have enough coins.", "Your bank is too full.", "You don't have enough inventory space.", "That button
  is not available on this server." (the last is ours by design).
- Prompts: "What would you like to buy?", "How many do you wish to buy/sell?", "Set a price for each item:".
- Sell-offer default quantity = all held (incl. notes).
- History: recorded at finish/abort; aborted offers with nothing traded are left out.
- Energy potion exemption applied to all four doses (wiki lists just "Energy potion").
- Limit name matching "Name (qualifier)" -> "Name" in the generator (no duplicate keys found; a full diff against
  data.json would confirm no wrong fallbacks).

## Not implemented (scope gaps)
- Platinum tokens and the >2.147B limit (declared), item sets via clerk op5 (declared), custom % buttons (declared),
  connected limits (declared).
- F2P 3-slot limit and F2P buy restriction on members' items (OpenRune has no F2P worlds; not declared).
- Sell warning below 10% of guide price (not declared).
- Ironman bond-only interface; new-account sell restrictions (not declared).
- Clerk Talk-to dialogue (not declared).
