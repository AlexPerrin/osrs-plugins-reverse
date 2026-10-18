# Upstream PR batch 1: bug fixes

Prepared 2026-10-17 against OpenRune-Server main
[`1be5dfb5f`](https://github.com/OpenRune/OpenRune-Server/commit/1be5dfb5f1a2efa314b93d6c1e2ae3dac6db41d1)
(pin `9a8b338a8` + 6 commits). Nothing has been pushed, forked or opened.

Branches live in a local clone, `re/upstream-pr/or` (git-ignored, under the repo root), one commit per branch off `main`,
author Alex Perrin <44126061+AlexPerrin@users.noreply.github.com>, with a
`Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>` trailer (upstream's history carries
`Co-authored-by` trailers, including a Claude one in #326). Commit titles follow upstream's history
(plain imperative sentence, "Fix ..."/"Add ..."), not CONTRIBUTING.md's `type: summary` suggestion:
CONTRIBUTING.md is still the Alter template and the merged commits don't use it.

To open one later: fork, `git push <fork> fix/NN-...`, open the PR with the title and description
below. Upstream squash-merges, so the PR description becomes the commit body.

## Opened (2026-10-18)

Opened from AlexPerrin/OpenRune-Server, following upstream's `.github/CONTRIBUTING.md`: commit
subjects in `type: summary` form (`fix: ...`), and each body has a release note, the PR checklist
and, for data changes, the data source and validation steps. The final titles and bodies are in
`batch-1-texts/`.

| # | PR |
|---|---|
| 01 | https://github.com/OpenRune/OpenRune-Server/pull/343 |
| 02 | https://github.com/OpenRune/OpenRune-Server/pull/344 |
| 03 | https://github.com/OpenRune/OpenRune-Server/pull/345 |
| 04 | https://github.com/OpenRune/OpenRune-Server/pull/346 |
| 05 | https://github.com/OpenRune/OpenRune-Server/pull/347 |
| 18 | https://github.com/OpenRune/OpenRune-Server/pull/348 |
| 43 | https://github.com/OpenRune/OpenRune-Server/pull/349 |
| 46 | https://github.com/OpenRune/OpenRune-Server/pull/350 |
| 55 | https://github.com/OpenRune/OpenRune-Server/pull/351 |
| long-varp compiler issue | https://github.com/OpenRune/OpenRune-Server/issues/352 |

## Status

| # | Branch (commit) | Applies on main | Changes vs the old patch | spotless | tests | boot |
|---|---|---|---|---|---|---|
| 01 | `fix/01-db-serialize-transactions` (c01ed9f26) | `git am`, clean | KDoc cut to one line; new `GameDatabaseTransactionTest` (fails without the fix: 2 transactions open at once) | pass | pass | - |
| 02 | `fix/02-bankers-content-group` (ac6ee895a) | `git am`, clean (#337 touched other npcs in `al_kharid_npcs.toml`) | header comment cut; list re-checked against the npc dump (96 types, exact) | pass | pass | ready 8.5 s |
| 03 | `fix/03-fishing-spot-content-groups` (5cb1333b6) | `git am`, clean | message only | pass | pass | ready 8.0 s |
| 04 | `fix/04-fishing-stop-out-of-reach` (5ff09b2ee) | `git am`, clean | own Chebyshev helper replaced by the engine's `isWithinDistance(npc, 1)` (one-line diff) | pass | pass | - |
| 05 | `fix/05-lumbridge-castle-rfd-doors` (b3b7a9d06) | `git am`, clean | header comment cut | pass | pass | ready 7.4 s |
| 08 | - | `git am`, clean | **dropped**: upstream's behaviour is live (see below) | | | |
| 18 | `fix/18-idle-packet-logout` (7ac6273f1) | `git am`, clean | KDoc removed (AGENTS.md: no comments on small classes) | pass | pass | - |
| 43 | `fix/43-varps-before-first-tick` (d638e4376) | `git am`, clean | import order fixed, comments cut, new `VarpsBeforeFirstTickTest` (2); ktlint's ratchet made us fold in two `?:`/`if (` spacing fixes on existing lines of `VarPlayerIntMapDelegate.kt` | pass | pass | - |
| 46 | `fix/46-default-dog-breeds` (e96102f0b) | `git am`, clean | message only | pass | pass | - |
| 55 | `fix/55-general-store-stock-wiring` (31aabce2d) | `git am`, clean (#341 fixed the other general stores, not these two) | message now cites upstream's `shopmappings.csv` | pass | pass | - |
| 56 | - | conflict with #339 | **dropped**: half fixed by #339, the other half unreachable (see below) | | | |

How it was tested (Docker, `eclipse-temurin:21`, upstream's `./gradlew`, throttled to
`--cpus=4 --memory=4g`, `--max-workers=2`, `-Xmx2g`; one branch at a time): CI's own steps.
`:or-cache:freshCache :or-cache:mergePluginGamevals` once on main (from OpenRS2, no files from our
tree), then per branch: restore that clean cache, `:or-cache:buildCache :or-cache:mergePluginGamevals`
(CI's cache-hit path), `spotlessCheck` (ratchet vs `origin/main`), `assemble test integration`;
for the data branches and main, `run` until "Server ready in". main itself passes all of these.

Notes from testing:
- Building the branches one after another on one incremental cache broke after 05: going from 05
  to a branch without its loc toml left loc 12348 (`hundred_lumbridge_door`) unusable and the
  integration suites failed with `Invalid loc type: MapLocDefinition(id=12348 ...)`. All passed
  again on a restored clean cache. So `buildCache` over an existing cache doesn't undo a removed
  loc server config. Probably an upstream incremental-cache issue, not in any of these patches;
  CI isn't affected (it restores the downloaded cache keyed on `game.example.yml`).
- With a 4 GB container, `freshCache`'s JavaExec runs out of heap (default 1/4 of the memory);
  `JAVA_TOOL_OPTIONS=-Xmx2600m` was needed for the cache steps. Unthrottled, the full
  `integration` run can get OOM-killed (exit 137) on this 15 GB machine.
- Nothing was checked in game from these exact branches. The in-game checks in the PR texts were
  done on our own server build (the pin plus these and our other patches).

## Dropped or shrunk because of upstream

- **08 obj-examine-chat-type: dropped.** Upstream is right. Live OSRS shows item examines through
  clientscript 6003 `examine_item` -> `[proc,examine_item]` (6004), which prints with `mes` (chat
  type 0, see osrs-dumps). RuneLite turns that into `ITEM_EXAMINE` itself: it ships a script edit
  of 6004 (`runelite-client/src/main/scripts/ExamineItem.rs2asm`, "iconst 27 ; ITEM_EXAMINE" +
  `runelite_callback`) and `ExaminePlugin` notes "these are spoofed by us from a
  [proc,examine_item] script edit". So OpenRune running 6003 is live behaviour; our need for type
  27 comes from running plugins without RuneLite's script edits (our side, not upstream's).
- **56 misthalin-mystery-mandy-guards: dropped.** The gem loss with a full inventory is fixed by
  #339 (Mandy now checks space, says "...You need N more spaces in your inventory to take it all."
  and `completeQuest` returns false without changing the stage). The other half (Mandy completing
  the quest at any stage) is not reachable from a client: `mistmyst_mandy_post` (7434) is a multinpc
  on `mistmyst_progress` that only shows `mistmyst_mandy_post_vis` (7630, Talk-to) at 130..135, and
  the base type has no ops, so `OpNpcHandler` drops the op (`NpcInteractions.hasOp`) at every
  other stage. Our guard only mattered for direct event dispatch.
- **55 general-store-stock-wiring: kept.** #341 fixed Falador/Karamja/Nardah/Miscellania; Lumbridge
  still opens `inv.generalshop1` and Edgeville `inv.generalshop5` on main.
- No open or merged PR or issue touches the other fixes (searched PR/issue titles for idle,
  examine, banker, fishing, door, dog/puppy, transaction, varp, general, Mandy; checked the files of
  all open PRs: #342/#274 (ToA), #319, #315 and #182 touch the same files as 03/18/43 but not the same
  code, so they may need a rebase, not a rethink).

## PR texts

Each text below is the PR body. "Our build" in them means our test server (the pin plus these and
other fixes), not the exact branch. Nothing in the diffs is copied from the wiki.
On every branch `spotlessCheck` and `./gradlew assemble test integration` pass; the data branches
(02, 03, 05) also boot to "Server ready". Each PR could say so in one line.

### 01 `fix/01-db-serialize-transactions`

**Title:** Serialize transactions on the shared database connection

**Problem.** `GameDatabase` holds one JDBC `Connection`. The game thread runs
`withTransactionBlocking` on it (`NetworkScript`, `AccountLoadResponseHook`) while `AccountSavingService` and the loader services run
`withSchemaTransaction` on it from coroutines. Nothing stops the two from interleaving, and on one
connection they then commit or roll back each other's statements, and one side's
`SET LOCAL search_path` is gone when the other commits. We saw it as
`Could not scope transaction to schema 'main': schema 'main' does not exist ...` from
`verifySchemaSearchPath` on an account save.

**Change.** One lock around the body of `withSchemaTransaction` (so also `withTransaction`) and
`withTransactionBlocking`. The retry in `withConnection` is outside the lock, so a retry waits its
turn too.

**Tests.** `GameDatabaseTransactionTest`: a fake `Connection`, one thread running
`withTransactionBlocking` and one running `withTransaction` 50 times each; asserts at most one
transaction body runs at a time and every one commits. `./gradlew :api:db:test`.

**Caveats.** The game thread now waits for a save transaction in progress (and the other way
round). Saves are short, but a slow database would show up as tick time. A connection pool would
avoid that; this is the smaller change. Found while testing with RuneLite plugins (many quick
relogs).

### 02 `fix/02-bankers-content-group`

**Title:** Give every standard banker the banker content group

**Problem.** `Banker.kt` binds Talk-to, Bank, Collect and item-on-npc noting to
`content.banker`, but on main only `deadman_banker_blue_south` has that group
(`.data/raw-cache/server/npcs.toml`). `bankers.toml` lists `banker1`/`banker2` without it, and
the Al Kharid bankers don't have it either, so clicking a banker across a booth gives
"I can't reach that!" and nothing opens.

**Change.** `content.banker` on every npc type named "Banker" with Talk-to (op1), Bank (op3) and
Collect (op4) in the rev 241 cache: 94 types in `generic-npcs`' `bankers.toml`, plus
`kharidbanker1/2` in the Al Kharid pack, where they are already defined. The list was checked
against the osrs-dumps npc dump (96 types, no extras, none missing). The new entries have
`moveRestrict = "NoMove"` like the two existing ones.

**Tests.** Data only, so no unit test (per AGENTS.md). The cache builds and the server tests pass
(see below). The "I can't reach that!" was seen in game; the fix was not re-checked in game on
this branch.

**Caveats.** The 94 types include bankers in places that are not implemented yet; with the group
they open the bank there too, as in OSRS. Bankers with other ops (e.g. "Banker" npcs without
Collect, or tutors) are left alone.

### 03 `fix/03-fishing-spot-content-groups`

**Title:** Add content groups to the Lumbridge river and swamp fishing spots

**Problem.** `0_50_50_freshfish` (Lure/Bait, the Lumbridge river spot) and `0_50_49_saltfish`
(Net/Bait, Lumbridge Swamp) have no `contentGroup` in `.data/raw-cache/server/npcs.toml`, so
Fishing never sees their ops and clicking them does nothing. The other freshfish/saltfish spots
with the same ops have the groups.

**Change.** `content.lure_bait_fishing_spot` and `content.net_bait_fishing_spot`, the groups
`FishingConfig` binds for those op sets.

**Tests.** Data only. The dead ops were seen in game; not re-checked in game on this branch.

**Caveats.** None known.

### 04 `fix/04-fishing-stop-out-of-reach`

**Title:** Stop fishing when the player is no longer beside the spot

**Problem.** After the first cast, the catch cycle in `Fishing.attemptCatch` re-queues itself and
only checks that the spot npc is still the same one (`uid`). If the player teleports away, the
weak queue keeps running: the animation and the catches go on from the other side of the map.

**Change.** The cycle also stops when the player is not within one tile of the spot
(`isWithinDistance(task.npc, 1)`, which also needs the same level).

**Tests.** None added. The bug was seen in game (teleport while fishing, catches went on); the
fix was not re-checked in game on this branch.

**Caveats.** On live a teleport interrupts the action itself; here the cycle notices on its next
roll, so one extra cycle may pass silently before it stops (no catch is awarded, since the check
runs before the roll).

### 05 `fix/05-lumbridge-castle-rfd-doors`

**Title:** Make the Lumbridge castle Recipe for Disaster doors open

**Problem.** The map places the kitchen to dining room door (3207,3217) and the hallway to dining
room double doors (3213,3221-3222) as their Recipe for Disaster variants
(`hundred_lumbridge_door`, `hundred_lumbridge_doubledoorl/r`). They have Open but no door content
group, so they answer "Nothing interesting happens."

**Change.** New `.data/raw-cache/server/loc/misthalin/lumbridge-castle.toml` with the door groups.
`hundred_lumbridge_door` has the same models, recolours and ops as `elfdoor`, and the double doors
match `castledoubledoorl/r`, so they open into `elfdooropen` / `opencastledoubledoorl/r`.

**Tests.** Data only. Checked in game earlier on our build (the kitchen door opens).

**Caveats.** They close back to the plain `elfdoor` / `castledoubledoor*`, not to the `hundred_`
variants. They look the same; it only matters if something later looks for the `hundred_` loc
there (Recipe for Disaster is not implemented). A door pair for these in `doors.toml` would fix
that if wanted.

### 18 `fix/18-idle-packet-logout`

**Title:** Log the player out on the client's IDLE packet

**Problem.** The client sends `IDLE` after five minutes without mouse or keyboard input (rsprot's
`Idle` doc: 15,000 client cycles). On OSRS that logs the player out. OpenRune has no listener for
it, so idle players stay logged in.

**Change.** `IdleHandler` sets `manualLogout`, the same flag as the logout button, so
`PlayerLogoutProcess` applies the same checks (combat etc.). Ignored while a logout is already
pending.

**Tests.** `IdleHandlerTest` (2): IDLE sets `manualLogout`; IDLE during a pending logout does
nothing.

**Caveats.** If logout is blocked (in combat), the player gets the logout button's message, and the
client won't send IDLE again until the next five idle minutes. We have not checked what live does
in that case.

### 43 `fix/43-varps-before-first-tick`

**Title:** Send varps set before a player's first processed tick

**Problem.** `VarPlayerIntMapSetter.set` and `VarPlayerIntMapDelegate` don't transmit while
`processedMapClock <= 0`. Varps set after the login varp sync but before the player's first
processed tick (EngineLogin handlers that run after the sync, post-tick hooks on the login tick)
are stored and never sent, so the client keeps the login value. Seen with followers:
`follower_npc` (varp 447) stayed 0 on the client after a relog although the follower respawned, so
the Kitten Tracker plugin (RuneLite Plugin Hub) saw no follower.

**Change.** The setters record changed varp ids in `Player.varpsSetBeforeFirstTick` while not yet
processed; `PlayerMainProcess` resends them (`resyncVar`, which respects `transmit = never`) right
after setting `processedMapClock` on the first processed tick.

**Tests.** `VarpsBeforeFirstTickTest` (2): a changed varp is sent once on the first tick, an
unchanged one is not. Checked in game on our build: after two relogs the Kitten Tracker overlay
shows the kitten.

The diff also fixes `if(` / `?:` spacing on two existing lines of
`VarPlayerIntMapDelegate.kt`, which the spotless ratchet flags once the file is touched.

**Caveats.** Varps changed before the login sync are also recorded and so sent twice (same value);
harmless but redundant. A cleaner fix would be to make the setters transmit once the login sync
has gone out; we didn't find a flag for that.

### 46 `fix/46-default-dog-breeds`

**Title:** Fix adopting the default dog breeds from Chase

**Problem.** `DogBreed.unlock` builds `varbit.dog_unlock_<unlock_bit>`. The `puppy_colours` rows
for the breeds that need no unlock have `unlock_bit` 0, and there is no `varbit.dog_unlock_0`
(the cache has `dog_unlock_1..9`, the lost dog unlocks). Choosing one of those breeds in Chase's
Adopt throws on the gameval lookup and disconnects the player.

**Change.** `unlock_bit` 0 means no unlock needed.

**Tests.** None added (one-line mapping). Checked in game on our build: Adopt lists Border
Collie, Labrador, Pug and Spaniel, and adopting a pug goes on to the colour menu and the 200
coins.

**Caveats.** That 0 means "available by default" is inferred from which rows have it, not
checked row by row against live.

### 55 `fix/55-general-store-stock-wiring`

**Title:** Open the Lumbridge and Edgeville general stores on their own stock

**Problem.** Lumbridge's shopkeeper opens `inv.generalshop1`, which is the Al Kharid stock (no
spade, no knife), and Edgeville's opens `inv.generalshop5`, the Lumbridge stock. The wiki-dumper
mappings (`tools/wiki-dumping/.../shopmappings.csv`) already list `generalshop5` as the Lumbridge
General Store and `generalshop2` as the Edgeville General Store. X Marks the Spot sends the player
to the Lumbridge store for a spade, which it doesn't sell.

**Change.** Lumbridge opens `generalshop5`, Edgeville `generalshop2`. Same area as #341, which
fixed the other general stores; these two were not part of it.

**Tests.** Wiring only. Checked in game on our build: the Lumbridge store sells the spade and
knife.

**Caveats.** None known.

## Issue texts

### Issue for OpenRune-Server: long varps compile to the int varp opcodes

Target: OpenRune/OpenRune-Server issues. The compiler is `me.filby:clientscript-compiler`
`0.0.7-openrune` from `OpenRune/hosting`; its source isn't public as far as we can tell
(`OpenRune/neptune` is older and has no long var support at all), so the server repo is the place
to report it.

**Title:** Clientscript compiler emits PUSH_VARP/POP_VARP for long varps

**Body:**

When `buildCache` recompiles the osrs-dumps scripts, a varp typed `long` in `symbols/varp.sym`
(rev 241: 5753-5769, `ge_newoffer_price`, `ge_tax_slot_long_0..7`,
`ge_itemsink_price_long_0..7`) is read with `PUSH_VARP` (opcode 1) and written with `POP_VARP`
(2). Jagex's scripts use `PUSH_VAR_LONG` (64) and `POP_VAR_LONG` (65). Long varcs are compiled
correctly (51/52).

Minimal repro: `[proc,ge_itemsink_get]` (script 5733), first case:

```
return(%ge_itemsink_obj_0, long_max(0, %ge_itemsink_price_long_0));
```

| | Jagex (OpenRS2 cache 2740, rev 241) | `buildCache` on main |
|---|---|---|
| `%ge_itemsink_obj_0` | `0001 00000c84` (op 1, varp 3204) | same |
| `0` (long) | `003d 0000000000000000` (op 61) | same |
| `%ge_itemsink_price_long_0` | `0040 00001682` (op 64, varp 5762) | `0001 00001682` (op 1) |
| `long_max` | `0fca` (op 4042) | same |

To compare: group 5733 of archive 12 in `.data/cache/LIVE` after `buildCache`, and
https://archive.openrs2.org/caches/runescape/2740/archives/12/groups/5733.dat

Effect: opcode 1 pushes the varp's int, so `long_max` finds one long too few and the int stack
holds one value too many. The proc then returns obj 0 for an empty Grand Exchange slot, and
`ge_offers_switchpanel` (806) shows "Grand Exchange: Offer status" with "Dwarf remains" instead of
the offer setup. Affected in rev 241: scripts 778, 779, 5156, 5732, 5733 and 9911, 40
instructions (checked on a fresh `freshCache` of main 1be5dfb5f, 2026-10-17).

Expected: 64/65 for varps whose symbol type is `long`. Found while testing with RuneLite plugins
(Grand Exchange).

### Issue for blurite/rsprot: UPDATE_STOCKMARKET_SLOT_V2 reset writes slot 0 (do not file)

Already fixed upstream: rsprot `670ea76` "fix: pass in correct slot for stockmarket slot
resetting" (2026-10-14), published in `1.0.0-ALPHA-20261014`. Current main's
`UpdateStockMarketSlotV2Encoder` writes `message.slot` first for both branches. Nothing to file.

What's left is on OpenRune-Server's side: it pins rsprot `1.0.0-ALPHA-20261001`
(`gradle/libs.versions.toml`), so it still has the bug. A one-line bump to `1.0.0-ALPHA-20261014`
would pick up the fix (plus 57697eb "allow objtype retex id -1" and 4ec57fd, a cam model cleanup).
Not prepared as a branch here: a dependency bump needs its own build and test run, and upstream
may prefer to bump itself. Possible text for a short issue or PR if wanted:

> **Bump rsprot to 1.0.0-ALPHA-20261014.** 20261001's `UpdateStockMarketSlotV2Encoder` writes
> slot 0 for every reset (rsprot 670ea76 fixed it), so collecting a finished Grand Exchange offer
> in slot N empties slot 0 on the client and leaves slot N showing the old offer.
