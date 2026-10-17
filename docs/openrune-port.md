# OpenRune port

Staged port of our Android test server onto OpenRune-Server, approved 2026-10-16 after the spike
(`docs/openrune-spike.md`). Build and run steps: `openrune/README.md`.

- **Upstream pin:** OpenRune-Server 9a8b338a8 (2026-10-16), in `openrune/scripts/openrune.sh`.
- **Our changes:** `openrune/openrune.patch`.
- **Containers:** `osrs-openrune` (game/JS5, host port 40255) and `osrs-openrune-http`
  (jav_config/LOCAL_AUTH, host port 8081). Our current server `osrs-rsps-test` (43594/8080) keeps
  running unchanged until the switch.

## Status

| Phase | What | State |
|---|---|---|
| 0 | `openrune/` tracked: pin, patch, `openrune.sh init/export/rsprot/build`, Docker image + compose, README | done |
| 1.1 | Custom clientscripts the app crashes on | done: left out of the cache (it is their content, not their ids) |
| 1.2 | Mobile chatmenu unclamp (varbit 10670 + script 113) | done, verified on device |
| 1.3 | Test commands (`::freeze`, `::quest`, `::npcsay`, `::options`, `::give`, `::empty`, numeric `::varp`/`::varbit`) | done; `::options`, `::freeze`, `::quest` verified on device |
| 1.4 | Door content gaps (castle) | kitchen + dining-room double doors tagged; front doors need nothing |
| 1.5 | Docker deploy beside osrs-rsps-test; app build option in `repack.sh` | done |
| 2 | Device checklist | done except Quest Helper's overlay steps and weight, which need the client dev |
| 3.1 | Sheep Shearer, Romeo & Juliet on OpenRune's quest framework | done (14d4034); headless tests pass |
| 3.2 | Test commands | done (`::quest` also takes ids, per-quest start stage) |
| 3.3 | Device runs with Quest Helper | done: all three quests pass with QH (slot 3, after the client fix d67379a) |
| 3.4 | Switch | **done 2026-10-16 10:57** (orchestrator): OpenRune on 43594/8080 with the shared key, `osrs-rsps-test` stopped; normal app build signs in (verified on device) |

## Phase 1 findings

### 1.1 Custom clientscripts

OpenRune adds 13 clientscripts of its own:
- 11 for the dev spawn menu (`content/other/spawn`);
- 2 for the quest list (`quest_available_set`, `quest_policy_complete_set`, run at login by
  `QuestVisibilityScript` and `QuestPolicySyncScript`).

They had ids 45500-45553. Stock rev-241 scripts are dense from 0 to 9972. The two quest scripts
are real content, not dev tools, so dropping all 13 (as the spike's hybrid cache did) would leave
the quest tab without its "only implemented quests" filter. Instead, all 13 are renumbered to
9973-9985, densely after the stock maximum (symbol files and gamevals in the spawn and quest
modules, `.data/gamevals/clientscript.rscm`).

Two checks keep it that way:
- `PluginPacks.cs2Overrides` fails the cache build on a custom clientscript id above 9999
  (`OPENRUNE_MAX_CLIENTSCRIPT_ID` overrides the limit).
- `openrune/scripts/check_cache.py`, run at the end of `openrune.sh build`, fails if the packed
  LIVE cache holds any archive-12 group above 9999.

Pitfall: an incremental `buildCache` on an existing cache never deletes groups. A cache from before
the renumbering keeps 45500+ beside 9973+. After renumbering, the cache was rebuilt from scratch.

**Device result (2026-10-16, slot 1): it is the content, not the ids.**

| Cache served | Result |
|---|---|
| all 13 renumbered to 9973-9985 | crash (SIGSEGV, fault addr 0, thread osrsrl-game, about 2 s after start) |
| spawn pack left out; the 2 quest scripts at 9973-9974 (dense, 9975 groups) | crash |
| spawn and quest packs left out (stock 9973 groups + the overrides) | **ok**, signed in and played |

So the cache build now leaves out the clientscripts of both packs (`OPENRUNE_SKIP_CS2_PACKS`, default
`spawn,quest`).

What is lost:
- the dev spawn menu, which is useless on mobile;
- the quest pack's script overrides: the quest tab's "implemented quests only" filter and its
  `[proc,questlist_hide_quest]`/`[proc,quest_is_complete]` overrides.

The server still runs `quest_available_set` at login. The app ignores the missing script: no crash,
verified.

Why the new scripts crash is open. The recompiled stock scripts and the 37 overrides are fine, so
it is something in scripts written for OpenRune, perhaps an instruction or a script feature that the
Android build handles differently. It is worth a look by the client dev (crash in the game thread
right after the JS5 sync), and it matters again if we ever want custom clientscripts.

### 1.2 Chatmenu on mobile

OpenRune already sets varbit 10670 (`chatmodal_unclamp`) to 1 for every chat modal, but never runs
script 113 (`[proc,chat_onsetupchange]`), which is what applies it. Our server found that the varbit
alone does not resize chatbox:chatmodal. For mobile sessions (`Player.mobileClient`),
`ifOpenChat` now:
- unclamps for chatmenu only (1);
- clamps for chat_left/chat_right (0);
- runs 113 when the value changes.

Closing the chatmodal (`closeModal`, `closeSubs`) resets it to 0. Desktop behaviour is unchanged.
Test: `::options One Two Three Four`; all four rows should show, and each tap should pick its own
row.

### 1.3 Test commands

| Our server | OpenRune |
|---|---|
| `::tele x z [level]` | same (built in) |
| `::item id [n]`, `::give id [n]` | `::item id\|name [n]`, `::give` added as alias |
| `::empty` | `::invclear`, `::empty` added as alias |
| `::varp id v`, `::varbit id v` | built in, now also numeric ids |
| `::quest name [start\|done\|reset\|n]` | added (quest module); sets the stage without rewards |
| `::freeze` | added: stops NPC wander and walk-back (`TestFlags.npcsFrozen`) |
| `::npcanim id` | built in, nearest NPC only (ours: all NPCs) |
| `::npcsay text` | added: NPCs within 16 tiles |
| `::options a b c d` | added: 2-5 option chatmenu, reports the picked option |
| `::pos` | `::mypos` |
| `::openbank` | same |

OpenRune's `::quest` matches the display name or key: exact match first, then a unique prefix, then
a unique substring. It covers only quests implemented in OpenRune, `Quest.all()`.

### 1.4 Door gaps

The castle's map locs, decoded from the rev-241 map at Lumbridge (50,50), level 0:

| Tile | Loc | Name | OpenRune before |
|---|---|---|---|
| 3207,3217 | 12348 | `hundred_lumbridge_door` (kitchen to dining room) | no door tag: "Nothing interesting happens" |
| 3213,3221-3222 | 12349/12350 | `hundred_lumbridge_doubledoorl/r` (hall to dining room) | no door tag |
| 3216,3218-3219 | 1529/1530 | `inaccastledoubledoorlopen/ropen` (castle front doors) | no ops |
| 3215,3212 / 3215,3226 | 1545 | `inactiveelfdoor` | no ops (inactive) |
| 3226,3214 / 3226,3223 | 1535 | `poordoor` (castle east wall) | tagged, works |

- The spike's "double doors with only Walk here" were the castle front doors. They are inactive,
  permanently open locs without ops, as in OSRS, so that is correct.
- The two dining-room doors are the Recipe for Disaster ("hundred_") variants. They now have door
  tags in `.data/raw-cache/server/loc/misthalin/lumbridge-castle.toml`, opening into the states of
  the doors whose models they share (`elfdooropen`, `opencastledoubledoorl/r`). Doors revert after
  `DoorConstants.DURATION`, so the RFD variants come back.
- `poordoor` (1535) at 3226,3214 is the simplest tagged door to test on device.

### Verified without the device (2026-10-16)

- From-scratch build: `init`, then `build` (OpenRS2 download, cache pack, installDist). The packed
  LIVE cache has 9973 clientscript groups, max id 9972 (`check_cache.py`; the custom scripts are
  left out, see 1.1).
- `docker compose up -d --build`: `osrs-openrune` boots in about 9 s ("Supported client types:
  Desktop, Android", world 255, embedded PostgreSQL on the named volume
  `osrs-openrune_openrune-pgdata`), and `osrs-openrune-http` serves jav_config on 8081.
  `osrs-rsps-test` kept running untouched.
- `tools/repack.sh`: every new line is gated by `OPENRUNE` or `GAME_PORT`, so a plain run builds
  exactly as before.
- Gotchas met on the way:
  - upstream's `install` task fails with the Gradle configuration cache, so it runs with
    `--no-configuration-cache`;
  - the cache build generates `api/generated` sources, so it runs in its own Gradle invocation,
    before `installDist`.

## Phase 2: device checklist

App build for OpenRune: `OPENRUNE=1 LOCAL_AUTH=1 tools/repack.sh --install` (8081 jav_config,
port patched to 40255, OpenRune's modulus).

| Check | Result |
|---|---|
| App syncs the OpenRune cache without crashing | ok once the custom scripts are left out (see 1.1) |
| Sign-in (LOCAL_AUTH, "Tester") and Play Now on world 255 | ok, in game at the Lumbridge courtyard, mobile gameframe |
| Chatmenu with 4 options (`::options One Two Three Four`) | ok: all 4 rows visible, tapping "four" gives "Picked option 4: four", chat history comes back after closing |
| `::freeze` | ok ("NPC wandering off.") |
| `::quest cook` | ok ("Cook's Assistant: varbit.cooks_assistant_progress = 0 (not_started, end 2)") |
| Door | ok: the kitchen door (`hundred_lumbridge_door`, newly tagged) shows Open Door on long-press and opens (zone loc change on Android) |
| NPC dialogue (Cook) and Cook's Assistant end to end | ok: chat_left/right, the Cook's 4-option chatmenu (all rows visible), "Start the Cook's Assistant quest?" Yes/No, hand-in of egg/milk/flour (`::give`), the quest-complete scroll (1 QP, 300 Cooking XP); stage 2 = finished |
| Bank, combat | ok: `::openbank` shows the Bank of Gielinor on mobile; Attack Man: hitsplats, headbars, XP drops and level-ups, the man dies and drops bones |
| Saves across relog | ok: logout and Play Now return to the same tile, with Cook's Assistant still finished |
| Weight (UPDATE_RUNWEIGHT) | open on device. OpenRune sends it on inventory/worn changes (`PlayerInvUpdateProcessor`) and our RSProt has the Android encoder; the client dev can read getWeight via the plugin host, e.g. after `::item 1127` and wearing it |
| Plugin host + Quest Helper (Cook's Assistant overlay) | partly: the host loads (RL button) and the Cook is outlined in cyan before the quest starts, which looks like QH's NPC highlight; the step text and overlay still need the client dev's check |

Slot 1: 01:29-01:43. Slot 2: 01:48-01:55. Each time the normal build (8080) was reinstalled at the
end and Play Now signed in.

Notes:
- OpenRune's dev realm uses a 150x XP rate (Cooking 41 after the quest, Attack 20 after one man).
- `adb input keyevent 66` (Enter) sometimes leaves a `::` command in the chat input; deleting and
  retyping it works.
- `::freeze` is world-wide server state; it was switched back on ("NPC wandering on").

## Phase 3

### 3.1 Sheep Shearer and Romeo & Juliet

OpenRune already had the pieces around the quests:
- spawns for Fred, the sheep, The Thing, Romeo, Juliet, Father Lawrence, the Apothecary, Draul and
  Phillipa;
- shearing (`Sheep`);
- spinning (the crafting skill opens skillmulti, interface 270);
- the castle stairs (`SpiralStaircaseScript`, already with "Climb up the stairs." / "Climb down the
  stairs.", as in OSRS and QH);
- the picket gates by Fred's pen;
- the cadava bushes (`Pickables`).

What was missing is the quest scripts. `content/quest/.../lumbridge/SheepShearer.kt` and
`.../varrock/romeoandjuliet/RomeoAndJuliet.kt` follow `CooksAssistant.kt`. Each keeps its progress
in a server-only varbit over the OSRS quest varp, so the client sees varp 179/144 as in OSRS:

| Quest | Varp | Stages (QH reads these) |
|---|---|---|
| Sheep Shearer | 179 (`varp.sheep`) | 0, 1 started, 1 + balls handed in, 21 done |
| Romeo & Juliet | 144 (`varp.rjquest`) | 0, 10 Juliet, 20 message, 30 Lawrence, 40 Apothecary, 50 berries/potion, 60 Romeo, 100 done |

- **Dialogue:** taken from the OSRS wiki transcripts (via tsps' `npc-dialogues.json`, BSD-2; only
  game text, no code copied).
- **Sheep Shearer extras:** Fred gives shears when you have none, partial hand-ins, the "20 balls
  already" shortcut, and The Thing (Shear: "The... whatever it is... manages to get away from you!",
  then Fred's "I've seen The Thing!" option; a server-only varbit).
- **Apothecary:** "Talk about something else." leads to "Talk about Romeo & Juliet." (shown from
  stage 40 on). The first visit sets 50; with berries it makes the potion.
- **Completion:** both quests end through `Quest.setQuestStage`/`completeQuest`, so they show the
  quest-complete scroll (Sheep Shearer: 1 QP, 150 Crafting XP, 60 coins; R&J: 5 QP).
- **Juliet's house stairs** (`fai_varrock_stairs_taller` 11797 / `_top` 11799) had no handler. They
  get straight-staircase offsets in `StraightStaircaseScript`, so going up from the foot (east)
  lands west of the flight on level 1.
- **Tests:** `QuestTalkFixture` (test sources) runs NPC conversations headless.
  `SheepShearerInteractionTest` (4) and `RomeoAndJulietInteractionTest` (3) walk every stage and
  check the items moved.
- Gamevals: varps/varbits 63740-63742 in the quest pack's `gamevals.toml`.

### 3.2 Test commands

All of `::quest`, `::give`, `::empty`, `::tele`, `::freeze`, `::npcsay`, `::npcanim`, `::options`,
`::varp` and `::varbit` exist (see 1.3). `::quest` now also takes a quest id or dbrow
(`::quest 131 done`), and `start` uses a per-quest first stage (`Quest.debugStartStage`; R&J: 10).

### 3.3 Device runs with Quest Helper

Start QH's helper with `adb shell setprop debug.osrsrl.sbclick '"Sheep Shearer"'`, without
touching the RL panel.

**Sheep Shearer (slot 1, 2026-10-16 02:28-02:42).** The QH overlay showed, in order:

| Step | What QH showed | Result |
|---|---|---|
| Start at Fred | Fred highlighted | Fred's dialogue, then the 3-option menu with all rows visible, then "Start the Sheep Shearer quest?" Yes; objbox "Fred gives you a set of sharp shears."; varp 179 = 1 |
| Shear | "Shear sheep in the nearby field." (1 x Shears green, 20 x Wool red), sheep highlighted | "You get some wool." |
| Stairs | "Climb the staircase in the Lumbridge Castle..." (20 x Wool green), stairs highlighted with an arrow | Climb-up to level 1 |
| Spin | "Spin your wool into balls." | not tapped (see below) |
| Hand in | "Bring Fred the Farmer north of Lumbridge your balls of wool..." | "You give Fred 20 balls of wool.", then the scroll "You have completed Sheep Shearer!" (1 Quest Point, 150 crafting XP, 60 Coins, total 2) |

Open points:
- the wheel and make-x (270) were not reached. QH's outlines are drawn offset from the models, so
  the wheel was hard to find;
- the QH overlay stayed on "Bring Fred..." after completion (client side, passed to the client
  dev).

**Romeo & Juliet (slot 2, 02:51-02:58).**

| Step | What QH showed | Result |
|---|---|---|
| Romeo | "Talk to Romeo in Varrock Square.", with "Yes, I have seen her actually!" and "Yes." highlighted in the chatmenus | stage 10 |
| (relog) | "Talk to Juliet in the house west of Varrock.", stairs highlighted | Climb-up from the foot (3159,3436) lands at 3155,3436,1 |
| Juliet | (same step) | "Juliet gives you a message." objbox, stage 20 |

The run stopped there because QH did not follow live stage changes. The server had varp 144 = 10
(`::quest`, `::getvarp`; `::varp 144 10` changed nothing either), but QH kept "Talk to Romeo"
until a relog. The same thing kept the Sheep Shearer overlay open after completion. OpenRune
sends varps like our server does (rsprot VarpSmall/VarpLarge, Android encoders bound), so this
went to the client dev.

Also found:
- **Two Juliets:** OpenRune spawned the plain `juliet` (5035) and the multinpc
  `juliet_multi_visible` (6268, shown as 5035 by varbit `romjul_juliet_visible`) side by side. OSRS
  has only the multinpc; the plain spawn is removed (7790921).
- **QH outlines** sit on another tile than the NPC model (Romeo, sheep), and for Juliet on the
  player's tile. Every other quest NPC spawns once, so this is not a duplicate. Client-side
  candidate: the coordinate base (OpenRune's build area origin, see `::mypos`).

The cause was on the client side: the host's varp diff stopped at 1 << 16, and OpenRune's cache
defines 65660 varps. Fixed in d67379a.

**Slot 3 (03:13-03:26), app built from master.**
- QH followed R&J live through every stage:

| Stage | What QH showed | Result |
|---|---|---|
| 20 | "Bring the letter to Romeo..." (1 x Message) | Romeo takes the message |
| 30 | "Talk to Father Lawrence in north east Varrock." | Lawrence's sermon dialogue |
| 40 | "Bring the cadava berries to the Apothecary in south west Varrock" | Apothecary: "[2] Talk about something else." highlighted, then "Talk about Romeo & Juliet." first in the next menu; with berries he gives the potion |
| 50 | "Bring the potion to Juliet..." (1 x Cadava potion) | one Juliet on the balcony takes the potion |
| 60 | "Talk to Romeo in Varrock Square to finish the quest." | Romeo's crypt scene, quest complete: QH overlay closes, R&J = 100, QP 2 -> 7 |

- **Spin** on the castle wheel (3209,3212,1) opens make-x 270 on mobile ("How many would you like to
  spin?", 1 / X / All). "All" turns 5 wool into 5 balls (2.5 XP each).
- **Middle castle staircase:** "Climb up or down the stairs?" / "Climb up the stairs." / "Climb down
  the stairs."; down lands at 3206,3208,0.
- **CS2 varc test:** the first try hung at "Loading config 1%", because `add_varcs.py` omitted the
  2-byte group version trailer (fixed in 779c96b). Not retested yet; the clean cache is deployed.

### 3.4 The switch (done 2026-10-16)

Goal: the plain app build (`LOCAL_AUTH=1 SERVER=10.0.2.2:8080 tools/repack.sh`, our server's
modulus, port 43594) works against OpenRune unchanged.

| Piece | How |
|---|---|
| Ports | `docker-compose.yml` takes `OPENRUNE_GAME_PORT` / `OPENRUNE_HTTP_PORT` (default 40255/8081); `switch.env` sets 43594/8080 |
| Login key | `RsaProvider` loads `.data/rsa.properties` (hex modulus/private_exponent, our server's format) when present; `scripts/openrune.sh shared-key` copies `server/data/rsa.properties` there (git-ignored, baked into the image). `openrune.sh modulus` then prints that modulus too |
| jav_config / LOCAL_AUTH | the sidecar keeps serving them, now on 8080 |
| Saves | OpenRune's accounts stay in the volume `osrs-openrune_openrune-pgdata` (unchanged by the switch). Our server's `rsps-saves` volume stays as it is with `osrs-rsps-test`; nothing is migrated (different formats; the test account starts from OpenRune's save) |
| Fallback | `osrs-rsps-test` is stopped, not removed; going back is the reverse |

Steps, once the orchestrator has the user's OK:

```
openrune/scripts/openrune.sh shared-key
docker stop osrs-rsps-test
cd openrune && docker compose --env-file switch.env up -d --build
LOCAL_AUTH=1 SERVER=10.0.2.2:8080 tools/repack.sh --install   # the usual build
```

Back to the old server:

```
cd openrune && docker compose down
docker start osrs-rsps-test
```

Delete `src/.data/rsa.properties` and rebuild to return OpenRune to its own key (40255 builds).

### CS2 crash (optional item)

Likely cause: the quest pack's scripts use varcstr 1293/1294 (`quest_available_ids`,
`quest_policy_complete_ids`, `varc.sym`). Rev 241's varclient config (archive 2, group 19) has
entries 0-1568 except exactly 1293 and 1294, and the client likely faults on a varc without a
config. A varclient entry carries no type (plain varcs are just `00`).
`scripts/add_varcs.py` adds empty entries for every id in the packs' `varc.sym` to the packed
cache, in place: a new group 19 container and the archive-2 reference table (version + 1).

Prepared but not yet run on the device:
- the cache is built with `OPENRUNE_SKIP_CS2_PACKS=spawn` (quest scripts in), then `add_varcs.py`;
- the pre-test caches are backed up.

If the app no longer crashes, `add_varcs.py` goes into `openrune.sh build` and the default skip
list becomes `spawn`, so the quest-list filter comes back.

## Next steps

1. **The switch:** done (3.4).
2. **CS2 varc retest: done 2026-10-16 10:48-10:57, failed.** The quest pack's clientscripts still
   crash the app; the missing varcs are not the cause. The quest pack stays skipped
   (`OPENRUNE_SKIP_CS2_PACKS` default stays `spawn,quest`), and osrs-openrune runs the clean cache
   again.
   - Test cache: `OPENRUNE_SKIP_CS2_PACKS=spawn gradle :or-cache:buildCache` + `add_varcs.py`
     (779c96b, with the version trailer). Offline it checked out: 9975 clientscripts (new 9973/9974,
     changed stock procs 3238 and 4136, interface 3/1099 changed), 2/19 at ver 64066 with 1569 files
     (1293/1294 empty, the other 1567 byte-identical), every group CRC and 2-byte version trailer in
     archives 2 and 12 matching the reference tables, and a JS5 probe of a throwaway container
     served all changed groups with matching CRCs.
   - Device (OpenRune app build): SIGSEGV about 4 s after launch, during cache sync, before the
     login screen. Same signature as before:
     `Fatal signal 11 (SIGSEGV), code 1 (SEGV_MAPERR), fault addr 0x0 in tid ... (osrsrl-game)`,
     "Cause: null pointer dereference", backtrace only in libhoudini.so (ARM translation).
   - Control: the clean cache plus `add_varcs.py` only (2/19 with 1293/1294, no quest scripts)
     synced without a crash, and Play Now signed in. So the empty varclient entries are harmless,
     and the crash comes from the quest pack's scripts: the two new ones (9973
     quest_available_set, 9974 quest_policy_complete_set) and/or the recompiled overrides of
     `[proc,questlist_hide_quest]` and `[proc,quest_is_complete]` (groups 3238/4136), or interface
     1099. Next lead for whoever picks it up: bisect these five groups (e.g. a hybrid cache with the
     two procs only), and diff the recompiled 3238/4136 bytecode against stock.
   - `add_varcs.py` is not wired into the build: it only matters once the quest scripts load.
   - The test cache is not kept in the repo; rebuild it with the two commands above.
   - **Offline bisect (2026-10-16, fourth server dev).** Built in a scratch copy of `src` so the
     deployed cache stayed clean. Ruled out:
     - new opcodes: the decoded bytecode of 9973/9974 and the recompiled 3238/4136 uses no opcode
       stock lacks;
     - varc range: the varcstr ops 49/50 hit ids up to 1501 in stock (int varcs up to 1568), so
       1293/1294 are in range;
     - script names: OpenRune embeds `<file>.cs2 [kind,name]` in all 9915 scripts it recompiles,
       and those load fine;
     - interface 1099: identical content (version/CRC only).
   - **Lead:** OpenRune's packer writes the groups it *adds* into archive 12's reference table
     with sizes (0, 0) and name hash 0. Every stock or recompiled group has real values; groups it
     only rewrites keep stale but non-zero sizes, which the client tolerates. Every crashing cache
     added clientscript groups (45500+, renumbered 9973-9985, quest 9973/9974); the working ones
     added none. Interface 1099 (archive 3) also has a (0, 0) entry in the working cache, so this
     fits only if clientscripts are loaded at sync and interfaces on demand.
     `scripts/fix_reftable.py [CACHE] [ARCHIVE...]` fills in sizes and the name hash of the
     embedded script name.
   - **Fixed, verified on the device 2026-10-16 18:04.** The cache with the quest scripts,
     `add_varcs.py` and `fix_reftable.py` synced without a crash; Play Now went in game and the
     app kept running. Same cache build as the crashing tests, so the zero reference-table entries
     were the cause. `openrune.sh build` now runs both scripts after the cache build, and the skip
     default is `spawn` only, so the quest pack's scripts (and the quest-list filter) are back. The
     `build` output was checked identical, archive by archive, to the cache tested on the device.
   - Quest-list filter verified on the device (18:24): the journal shows "Quests Completed: 7/11" and
     the Quest List only the implemented quests (Cook's Assistant, Ernest the Chicken, Misthalin
     Mystery, Sheep Shearer, Imp Catcher, ...).
3. **Client side (client dev):** QH highlights on the wrong tile on OpenRune.
4. **Housekeeping:**
   - `re/openrune` (spike) can go;
   - `re/tsps` (read-only content reference, BSD-2) can stay or go;
   - the `AndroidUpdateZonePartialEnclosed` size-estimate errors go away with the next RSProt
     build (1cf7727, already in the deployed image).

## Fixes worth contributing upstream

Most of `openrune.patch` is ours alone: Android support, the token login, the shared login key,
the mobile gameframe, the test commands, the members/idle choices. These parts are generic
OpenRune bugs or gaps that any OpenRune server hits. They are listed so the user can send them
upstream later; nobody has contacted upstream.

| Fix | Where (in `src/`) | Why it is generic |
|---|---|---|
| Serialize DB transactions | `api/db/.../jdbc/GameDatabase.kt` (`transactionLock`) | The game thread (logins) and the account saver share one JDBC connection. Interleaved transactions commit each other's work and lose `SET LOCAL search_path` ("schema 'main' does not exist" on a save) |
| Reference-table sizes for added groups | our workaround: `openrune/scripts/fix_reftable.py`; upstream fix belongs in the `or-cache` packer | The packer writes groups it *adds* (custom clientscripts) to archive 12's reference table with sizes (0, 0) and name hash 0. Clients that check sizes crash at cache sync (the Android client does; device-verified) |
| Varclient configs for custom varcs | our workaround: `openrune/scripts/add_varcs.py` | The quest pack's clientscripts use varcstr 1293/1294, which rev 241's varclient config does not define |
| Bankers across the booth | `content/generic/generic-npcs/pack/.../bankers.toml`, `al_kharid_npcs.toml` (`contentGroup = "content.banker"`) | Banker types without the content group answer Bank/Collect/Talk-to with "I can't reach that!" across the booth |
| Fishing spot content groups | `.data/raw-cache/server/npcs.toml` | The Lumbridge river Lure/Bait spot and `0_50_49_saltfish` had no content group, so their ops did nothing |
| Fishing stops when the spot is out of reach | `content/skills/fishing/.../Fishing.kt` | Fishing went on after a teleport |
| RFD castle doors | `.data/raw-cache/server/loc/misthalin/lumbridge-castle.toml` | The kitchen and dining-room doors in the map are the Recipe for Disaster variants, which had no door tag |
| One Juliet | `.data/raw-cache/map/npcs/varrock.toml` | The plain `juliet` spawned beside the multinpc `juliet_multi_visible` |
| Juliet's house stairs | `content/generic/generic-locs/.../StraightStaircaseScript.kt` | `fai_varrock_stairs_taller`/`_top` had no handler |
| Item examine as `ObjExamine` | `api/player-output/.../GameMessage.kt` (+ test) | Examine texts went out through the `examine_item` script as type 0, which RuneLite's Examine plugin does not match |
| OSRS message wording | mining, fishing, cooking, smelting scripts; `LoginScript` ("Welcome to Old School RuneScape.") | Small text differences from OSRS that client plugins parse |
| Clientscript id guard | `or-cache/.../PluginPacks.kt`, `openrune/scripts/check_cache.py` | Fails the build on custom clientscript ids above 9999 |
| Quest scripts: Sheep Shearer, Romeo & Juliet | `content/quest/...` (+ `QuestTalkFixture` tests) | New content on OpenRune's quest framework |
| Dwarf multicannon, dragon dagger/warhammer specs | `content/other/dwarf-multicannon`, `content/other/special-attacks` | New content with tests |
| Implings, Genie random event | `content/skills/hunter`, `content/events/random-events` | New content with tests (2026-10-16, fifth server dev) |
| Impling jar loot, crystal implings, bird houses | `content/skills/hunter` | New content with tests (sixth server dev); jar loot and crystal implings are in `upstream/15-implings.patch`, bird houses in `upstream/20-bird-houses.patch` |
| Slayer streak and wording | `content/skills/slayer` | Streaks were server-only varbits; RuneLite reads 4069/5617 (`upstream/17`) |
| Farming patches (Catherby, Falador) | `content/skills/farming` (new module) | OpenRune had no farming; values RuneLite's Time Tracking decodes (`upstream/19`) |

The split patches are in `openrune/upstream/` (one per fix, with a README); see there for what
was verified.

## Fifth server dev (2026-10-16 evening)

- **Deployed** the build held since 18:43 (RSProt with the MIDI_SWAP encoder and decoders for
  8/20/28/50/57/84) at 18:54. Sign-in and Play Now work.
- **Implings** (`content/skills/hunter`). Puro-Puro already had its 51 fixed impling spawns from the
  map (`::tele 2592 4320`; seen on the device through devtools `list_npcs`: a Gourmet impling with
  "Catch"). What was missing is catching. Data: server-only dbtable `impling_types` (60000, rows
  60124-60134) in `content/skills/hunter/pack`: npc, Puro-Puro npc, jar, level, XP for Puro-Puro and
  Gielinor, respawn ticks. Crystal implings (Prifddinas, 17 per-spawn npc ids) are not covered.
  Integration tests: `ImplingScriptTest` (4). Not yet on the device.
- **Random events** (`content/events/random-events`): the Genie (`npc.macro_geni`), see the
  README. Timers 450/451. Integration tests: `GenieRandomEventTest` (2). The script must be a
  `@Singleton`: the test harness (and possibly the loader) otherwise makes a second instance with
  its own state. Not yet on the device.
- Gotcha: a reused Gradle configuration cache does not see new modules (`buildCache` reported
  success without packing the new dbtable). Run once with `--no-configuration-cache`.
- Every `buildCache` changes archive 3's reference table (interface versions), even with no
  interface change. Harmless so far (the client re-syncs that archive).

## Seventh server dev (2026-10-16 late night): farming

- **Module** `content/skills/farming` (+ `farming/pack`): `FarmingScript` and the server-only
  dbtable `farming_crops` (60002, rows 60160-60187: 8 allotment crops, 6 flowers, 14 herbs; huasca
  is left out, its chance-to-save constants are not on the wiki). Server varps 63780-63795
  (`farming_<catherby|falador>_<a-d>_state|time`): state = transmit value | harvest lives << 8,
  time = UTC minute of the last growth. Timer `farming_transmit` (460) rewrites
  `farming_transmit_a-e` every 5 ticks for the area the player is in (RuneLite's FarmingWorld
  bounds: Catherby mapsquares 11062/11061/11318/11317 with its isInBounds, Falador 12083 with
  z >= 3272), 0 elsewhere.
- **Patches:** Catherby `farming_veg_patch_3`/`_4` (4771/4772), `farming_flower_patch_2` (4773),
  `farming_herb_patch_2` (4774); Falador `_veg_patch_1`/`_2`, `_flower_patch_1`, `_herb_patch_1`.
  Ops register on the base multiloc (OpenRune falls back from the visible variant to the base).
- **Values:** from PatchImplementation (allotment weeds 0-3 = 3 - weed stage; potato 6-9 then 10
  harvestable, snape grass 63-69 then 138; herbs plant+0..3 then plant+4; flowers likewise). A
  reflection check against `re/runelite/client-1.13.1.jar` (scratch Java program, eclipse-temurin
  in Docker) confirmed every growing and harvest value decodes to the right produce and stage.
- **Rules:** growth on the UTC clock (minute % 10/5/20 == 0), lazily applied, so offline growth
  works. Planting XP comes when the patch is emptied (the wiki says so for all three patch types
  now). Herbs/allotments: 3 harvest lives, chance to save `(1 + floor(low*(99-F)/98 +
  high*(F-1)/98 + 0.5))/256` with the wiki's per-crop low/high. Flowers: one pick, woad 3 leaves,
  limpwurt 3 + random(0..F-1)/10.
- **Device (22:10-22:27):** raked (devtools `interact_object` with `objectId` and "Rake"; the
  patches' base locs have no name so `nameFilter` does not match), planted potato/marigold/guam
  with long-press Use + tap, growth at 22:20 seen (varp 529 went 0x04080600 -> 0x05090700),
  `::growpatches`, Harvest gave 10 potatoes and "The allotment is now empty.", Pick on the flower
  and herb; a full inventory stopped the herb pick with the herb still there. Time Tracking (core
  plugin) lists "Catherby (North) Done" and "Catherby (South) Done at 22:30", but 22:30 is the
  prediction for raked weeds (value 3); after planting (value 6 at 22:17, 7 at 22:20) it should say
  22:50 and it did not change. Open question for the client dev: does the host re-render the panel
  after `panel.update()`, or did FarmingTracker not store the new value?
- **Bird houses verified** (63f453b): the southern space is `birdhouse_2` at 3679,3815 with a wall
  to its north; stand south of it (`::tele 3679 3814`). Build, Seeds, `::birdhouses`, Empty all
  worked; the message was "You dismantle and discard the trap, retrieving 6 nests, 10 dead birds,
  50 feathers and 112 Hunter XP." Six nests from a normal house at 99 looks high: the tier's
  `nest_chance` (300) is above 256, so every roll succeeds; worth rechecking against the wiki.
- **Bird house nest fix:** the wiki's chart for the 5 nest-table rolls is in birdhouse mode:
  `floor(high * (max(50, level) - 1) / 98) / 1000` (normal 30% at 99, 15% at 50 and below; redwood
  52.5%). The code used it as /256, so every roll succeeded for every tier. Now `nestChance()` per
  mille, with a test. The seed nest (0-200 out of 256) was right. Upstream patch 20 = bird houses.
- **More farms** (after the device check): Ardougne (mapsquare 10548), Morytania/Port Phasmatys
  (14391, 14390), Hosidius (6967, 6711) and Civitas illa Fortis (6192 + 6447/6448/6449/6191/6193),
  RuneLite's FarmingWorld areas (no extra bounds there). Varps 63796-63827. Patch locs: veg 5/6,
  7/8, 10/11, 16/17; flower 3, 4, 5, 8; herb 3, 4, 6, 8. Their varbits are checked by the test;
  which farm each pair sits in follows the id pattern (Falador 1/2, Catherby 3/4, compost bins
  7836-7839, 27112, 50694); checked in game 22:43 (deployed with 49a73be): `list_objects` found
  each farm's own patch ids, and a Rake on each farm's first allotment set 4771 to 3.
  Prifddinas and the Farming Guild are left out (their patches use other varbits: herb/compost
  swapped, 7904-7912). Upstream patch 19 regenerated.
- **Time Tracking verified with farming** (client dev, 2026-10-16 ~23:00, after its sidebar fix
  7d1ff62): Catherby (South) went from "Done" to "Done at 23:05" after `::growpatches`. The
  farming slice works end to end with the plugin.

## Eighth server dev (2026-10-16 night): beginner clue scrolls

- **Module** `content/other/treasure-trails` (+ `treasure-trails/pack`): `BeginnerClueScript`
  (`@Singleton`, also the `SpadeDigHook` through `TreasureTrailsModule`). Server-only dbtables
  `trail_beginner_steps` (60003, rows 60200-60220) and `trail_beginner_casket` (60004, rows
  60240-60313); server varps 63840-63842 (`trail_beginner_step` = the step's dbrow id,
  `_steps_left`, `_completed`).
- **What the plugin reads** (`re/rlsrc/.../cluescrolls`): a "Read" click on an item named "Clue
  scroll..." (23182 resets the plugin's clue, then it waits for the widget); `WidgetLoaded` 203 ->
  text of 203:2 (13303810), sanitized (`<br>` -> space) and lowercased, matched exactly against
  AnagramClue ("The anagram reveals who to speak to next: AN EARL"), CrypticClue, EmoteClue
  texts; `WidgetLoaded` 346-356 -> BeginnerMapClue (only 346, 347, 348, 351, 356 exist); the
  objectbox item 193:1 (12648449) showing 23182 resets the clue (the "You've found a new clue!"
  objbox). DIALOG chat is only parsed for Charlie the Tramp and Falo, so talk steps need no chat.
  Loot Tracker: game message `You have completed N beginner Treasure Trails?.` (tags removed),
  then the next change of inv 141 (`trail_rewardinv`) is the loot; 141 is cleared when the reward
  interface (73) closes so the next snapshot starts empty.
- **Steps (21):** anagrams Ranael, Apothecary, Doric, Brian, Veronica, Gertrude, Hairdresser;
  cryptics Hans, Cook, Shantay; emotes Aris (raspberry, gold ring + necklace), Brugsen (bow),
  Iffie (cheer, chef's hat + red cape), Bob's axes (clap, bronze axe + leather boots), Al Kharid
  mine (panic), Flynn (spin); maps 346 Champions' Guild, 347 Varrock east mine, 348 south of
  Draynor bank, 351 standing stones, 356 Wizards' Tower. Texts are the plugin's strings (e.g.
  "chef hat", not the wiki's "chef's hat"). Left out: Archmage Sedridor, Fortunato, Hunding (no
  spawns in OpenRune's map), Charlie the Tramp (item task), Reldo's hot-cold.
- **Flow:** the trail length (1/2/3 steps at 10/45/45%) and first step are rolled at the first
  Read. Talk: "Well done." then objbox "You've found a new clue!" / "You've obtained a casket!"
  (Transcript:Ranael/Hans). Emote within the row's radius -> Uri (`trail_beginner_uri`, 8638)
  appears beside the player for 100 ticks; Talk-to checks the worn items ("I do not believe we
  have any business, Comrade." otherwise), says a Transcript:Uri line, the player "What?", then
  the objbox. Map: dig within 1 tile of the plugin's tile. Casket: 1-3 rolls of a single flattened
  table out of 44,280 (generic 11/492, black 11/8,856, unique 1/360, noted cabbage 1/24), needs 3
  free slots, items go to the inventory, the reward interface shows inv 141. Scroll box (beginner)
  Open gives a clue unless one is held anywhere.
- **NPCs that already talk** (Cook, Hans, Doric, Veronica, Apothecary, Ranael's shop): new API
  `NpcOpOverrides` (api/player) + event `NpcOverrideEvents.Op`; `NpcInteractions.opTrigger`
  checks it first and only for players a registered claim accepts (here: holds the clue and the
  current step is a talk to that npc type), so everyone else keeps the normal dialogue.
- **Test command:** `::clue [step] [steps_left]`, e.g. `::clue map_draynor_bank 1`,
  `::clue anagram_ranael 3`; an unknown step name lists them all.
- **Build note:** after adding the module, `openrune.sh build` reused a configuration-cache entry
  that did not see it and regenerated `api/generated` without the new tables (compile errors).
  Fix: `openrune.sh gradle --no-configuration-cache :or-cache:buildCache`, then the rest of the
  build steps (add_varcs, fix_reftable, `generateRsa :server:app:installDist`, check_cache).
- **Tests:** `:content:other:treasure-trails:integration` (6: casket table maths, step data, a 3-step
  trail ending in exactly one casket, place/npc checks, casket space and payout, scroll box).
  Upstream: `openrune/upstream/21-beginner-clue-scrolls.patch`.
- **Device (2026-10-17 00:02-00:09, deployed 35d27b9, EXTRA="cluescrolls devtools loottracker"):**
  203 lays out fine on mobile (266x256 text component, "The anagram reveals / who to speak to
  next: / AN EARL"); the plugin's overlay showed "Anagram Clue, NPC Ranael, Location Al Kharid
  skirt shop" and the clue icon above Ranael. Talk-to Ranael: "Well done." then the objbox with
  23182 "You've found a new clue!". Map 348 renders on mobile and the plugin showed "Map Clue,
  South of Draynor Village Bank, Requires Spade!"; Dig gave "You've obtained a casket!"; Open
  showed interface 73 with the rewards (8 noted cabbage, staff of air), the items reached the
  inventory, and the message read "You have completed 1 beginner Treasure Trail." Emote/Uri not
  tried on the device.
- **Loot Tracker recorded nothing.** The host dispatches queued native events and reads
  containers live, so on the ChatMessage the plugin's snapshot of inv 141 already held the
  rewards (diff empty). Decision (orchestrator): the server keeps the game's order (message,
  then the inv in the same tick); the fix belongs in the plugin host (client dev). A one-tick
  delay was tried in 89cf619 and reverted.
- **Client gap (for the client dev):** ClueScrollPlugin.onMenuOptionClicked NPEs on "Read":
  `itemManager.getItemComposition(event.getItemId())` returns null for the inventory CC_OP entry
  (p1=9764864, id=2), so `clueItemId` is never set. The widget path still works, but the plugin
  does not reset when the clue leaves the inventory (the map overlay stayed after the casket).

## Ninth server dev (2026-10-17): rest of the beginner tier

- **Steps (34 rows in `trail_beginner_steps`):** the 21 before, plus 8 Charlie the Tramp rows and 5
  hot-cold rows. Charlie rows count as ONE choice when a step is picked, and so do the hot-cold rows
  (`pickStep`: every other row is its own choice, never the same choice twice in a row).
- **Charlie the Tramp** (`npc.tramppg`, 3209,3390): the scroll reads "Talk to Charlie the Tramp in
  Varrock." until he asks; Talk-to: "Please, sir/madam, can you help me." / "What can I do for you?" /
  his request ("I really need a raw herring.", the DIALOG line `SkillChallengeClue.forChatboxText`
  matches) / "Sure, I'll be glad to help a person in need."; then the scroll reads "I need to give
  Charlie a raw herring." (the plugin's text). With the item (unnoted, inventory): mesbox "You give
  Charlie the raw herring.", objbox "Charlie gives you a new clue!/a casket!", "Thank you so much
  sir." (Transcript via TSPS' dialogue dump). Varp `trail_beginner_charlie_asked` 63844.
- **Hot-cold** (Reldo, `npc.reldo`): scroll text = the plugin's BEGINNER_CLUE text; Reldo gives the
  strange device (`obj.beginner_device` 23183) with the transcript dialogue; Feel: "The device is
  <temp>." then ", and warmer than / but colder than / and the same temperature as last time." by
  Chebyshev distance (wiki bands: ice cold 500+, very cold 200, cold 150, warm 100, hot 70, very
  hot 30, incredibly hot 4, visibly shaking 0-3), last distance in varp 63845. Dig within 4 tiles
  (wiki: a 9x9 square) with the clue. Spots: the plugin's five beginner HotColdLocations.
- **Check steps** (clue iop2): "You have completed N step(s) on this clue scroll." Wording is a best
  guess (not on the wiki); varp `trail_beginner_steps_done` 63843.
- **STASH units** (`StashUnitScript`, dbtable `trail_stash_units` 60005, rows 60320-60322):
  `loc.hh_beginner001-003` (Aris' tent: gold ring + necklace; Thessalia's: chef's hat + red cape;
  Bob's axes: bronze axe + leather boots), multilocs on `varbit.hh_constructed_beginner001-003`
  (0 bush, 1 built). Build: Construction 12, hammer, saw, 2 planks, 10 nails of one metal, 150 XP.
  Search: deposit the full set from the inventory / withdraw it ("You deposit/withdraw your items
  into/from the STASH unit.", which the plugin tracks). Filled bits in varp 63846. Device-checked:
  the plugin's overlay reads the built state through CS2 1479 ("STASH Unit ✗" before building).
- **Not done:** scroll cases (need the scroll box stack limit; ~2 h, little plugin value), worn
  items into STASH units, the 10-hour unique rule.
- **New characters** spawn at 3222,3218 (`CharacterAccountApplier`, `lastLogin == null`); the
  database default was 3200,3200 (why the desktop test account was out of tester's view).
- **Answers for the client dev:** own public chat is sent (RSProt writes the CHAT block to the
  observer itself, Android flag 0x400); the game channel closes after 15 s without inbound packets
  (RSProt `IdleStateHandlerSuppliers.Game`), so a headless client must send NO_TIMEOUT (desktop
  opcode 75, empty) every ~10 s, never IDLE.
- **Device tip:** on the mobile gameframe the yellow smiley tab is the Friends list (as on desktop);
  emotes are the green dancing figure (about 1840,600).
- **Rebuilt on Jagex's clue data (after the device check):** the cache ships `cluehelper_*` dbtables
  (generated rows in `api/generated/.../table/cluehelper`) with every clue's text, difficulty and
  target for all tiers: anagram/cryptic/map/emote/hot-cold/skill challenge/music/coordinate/cipher
  rows, targets in `cluehelper_target_npc` (15), `_loc` (16), `_coord` (18), `_key` (19), `_kill`
  (20), outfits with per-slot item lists, `hidey_hole_loc` = the STASH loc. Counts: beginner 9
  anagram, 6 cryptic, 5 map, 6 emote, 5 hot-cold, 8 skill challenge (Charlie); easy 120 cryptic
  (41 npc, 71 loc search, 8 coord dig), 33 emote, 9 map, 6 music. Each easy clue item names its
  row in param 623 (`Clue scroll (easy)` 2677..); drops give `trail_clue_easy_simple001`, so Read on
  a fresh one rolls a trail and swaps the item for the step's own scroll.
  - Code: `TrailTier` (beginner, easy: lengths, objs, varps, Uri npc, STASH level), `TrailSteps`
    (builds `TrailStep.Talk/Charlie/HotCold/Search/Dig/Emote` from the rows), `TreasureTrailScript`,
    `StashUnitScript` (units from the emote rows; varbit = the loc's multiloc varbit; stores the
    first item of each outfit slot). The hand-typed beginner step table is gone; three small
    server-only tables fill the gaps: `trail_charlie_tasks` (skill challenge row -> item, request
    line), `trail_map_interfaces` (map row -> trail_mapNN; easy rows 2228/2232/2234/2260/2261 still
    unknown), `trail_emote_seqs` (emote slot -> seq).
  - Charlie: the cryptic "Talk to Charlie the Tramp in Varrock." step; he picks a skill challenge row
    (kept in the tier's aux varp) and the scroll then shows that row's text. Hot-cold: the Reldo
    cryptic step; the dig spot (a hot-cold row) is rolled when the step is set.
  - `LocOpOverrides` + `LocOverrideEvents` (api/player): like the npc version; `LocInteractions`
    checks claims first. Search steps register on the loc op named "Search".
  - Varps: beginner 63840-63847 (step, left, completed, done, aux, hot-cold last, STASH bits,
    hot-cold spot), easy 63850-63855. Old beginner saves pointed at rows of the removed table and
    simply start a new trail.
  - Tests: `TreasureTrailScriptTest` (11). Boot-checked in a throwaway container.
- **Easy casket** (`EasyCasketTable`, dbtable 60007, rows 60700-60910): the wiki's table (Mod Tide's
  rates) flattened to one weighted roll out of 617,760 (211 rows: uniques, standard table, shared
  treasure trail items; parsed from the page source's DropsLineReward rarities, which sum to exactly
  1), 2-4 rolls, needs 4 free slots. Not rolled: the 1/50 master clue tertiary (no master tier yet)
  and scroll cases. Casket tables are generic per tier (`TreasureTrailScript.CASKETS`).
- **Easy step counts as built:** Talk 41, Search 44 (of 71: loc searches whose loc has no "Search"
  op, e.g. closed drawers, are left out for now), Dig 12 (8 coordinate cryptics + 4 maps with a
  known interface), Emote 33. Music steps (6, play a track near Cecilia) not done.
- **Easy map interfaces:** trail map interfaces are drawn from map-icon models (3386-3401), not
  sprites. Plotting their components against the wiki's map images identified Al Kharid mine
  (row 2228) = `trail_map18` (87). Still unknown: Galahad's house (2232), north of Falador (2234),
  behind Lumbridge castle (2260), south-west of the Draynor wheat field (2261); those steps are left
  out until mapped. (The plugin identifies easy maps by the scroll's item id, so the interface only
  matters for what the player sees.)
- **Music steps (6 easy):** need the music tab's track selection ("Play" on a track), which OpenRune
  does not implement yet (`MusicTabScript` only handles the Area/Shuffle/Single buttons); ~2 h with
  the unlock checks. Not done.
- **Music tab Play + music steps (approved after the easy device check):** `MusicTabScript` now sets
  the jukebox events on tab open and handles Play (unlocked tracks only: "You have not unlocked this
  piece of music yet!"; switches to manual mode) and Unlock hint. Child index = position among the
  non-hidden music rows in row order (osrs-dumps `[proc,music_list_setup]`: `db_find(music:hidden,
  false)` then `db_find_get(i)`); rows with no hidden value are counted as not hidden (to check on
  the device: the track that plays must be the one tapped). Music steps (`TrailStep.Music`, 6 easy
  rows): talk to Cecilia while the track plays: "Thank you, I love that song!" then "Cecilia offers
  you another clue." / "You've obtained a casket!"; otherwise her "Have you come to play music to
  me?" branch (Transcript:Cecilia). Scroll text is a best guess ("Cecilia in Falador Park would like
  you to play her a song:<br><col=ffffff>Track</col>"): the plugin only needs the white col tag.
  Upstream: `22-music-tab-play` (21 needs it).
- **Left out for now (orchestrator OK):** the four unidentified easy map interfaces and the 27 loc
  searches whose loc has no "Search" op (closed drawers that open first).
- **Music check on the device (5684e9e, 02:10-02:16):** the list order is right (Unlock hint on "Al
  Kharid" printed "in Al Kharid."; the hint column is a suffix, so the message is now "This track
  unlocks in Al Kharid.", not yet deployed); Play on a locked track says "You have not unlocked this
  piece of music yet!"; the music step's scroll shows "Alone" in white; Cecilia without the song:
  "Have you come to play music to me?" / Yes / "Select the song I asked you to play, and speak to me
  again.". Not tried: the success branch, because "Alone" did not unlock by walking on Ice Mountain
  (OpenRune's area unlock for it; not looked into).

## Tenth server dev (2026-10-17)

- **Easy searches on locs that open first (27 rows):** `TrailSteps.searchTarget` maps a target loc
  without a Search op to its open variant (`<loc>open`, `<loc>_open`, `closed` -> `open`), e.g.
  `drawers1` -> `drawers1open`, `chestclosed` -> `chestopen`, `draynor_wardrobe` ->
  `draynor_wardrobe_open`; the step completes on the open loc's Search. The Edgeville coffin's open
  loc has only Close, so that step completes on the coffin's Open (guess: the open coffin is not
  shown). Generic-locs now opens drawers1/2/3, the Draynor wardrobe, the Seers' Village drawers
  and the Camelot chest (`DrawersScript`, `PlainChestScript`); "You search the wardrobe but find
  nothing." is a best guess. Easy Search steps: 44 -> 71 (all). Device: not tried (the camera stays
  inside walls in small houses); covered by the integration test.
- **Easy map interfaces:** rendered every `trail_map*`/`trail_clue_*_map*` interface from its icon
  models (scratch Java renderer over the cache's ModelDecoder, top-down; `if3` placements from
  osrs-dumps) and compared with the wiki's map images: Galahad (2232) = `trail_map09` (354), north
  of Falador (2234) = `trail_clue_easy_map006` (337). Rows 2260/2261 (behind Lumbridge castle,
  Draynor wheat field) have no easy scroll obj: they are X Marks the Spot rows (`cluequest_clue2/3`,
  interface `cluequest_map` 267), so nothing to add. Galahad's map device-checked.
  Single-model maps 20-24 (314/317/318/316/102) render as 2018 hard maps (useful for medium/hard).
- **Ice Mountain music area:** OpenRune's `music_modern` table had one row (Lumbridge), so area
  unlocks barely exist. Added `area.ice_mountain` (32762, polygon from the wiki's `Map:Alone`, modern
  mode, fortress hole not modelled) and row `music_modern_ice_mountain` (60920) with Alone.
  Device-checked: "You have unlocked a new music track: Alone"; the Cecilia music step success
  branch ("Thank you, I love that song!", "Cecilia offers you another clue.").
- **Fairy rings** (`content/travel/fairy-rings`, `FairyRingScript`): on Jagex's `fairyring` dbtable
  (id = dial positions d1*100+d2*10+d3 with dials A/D/C/B, I/L/K/J, P/S/R/Q; `multiloc_state` 0-63;
  `text_component` in 381; `desc`). Locs `fairyring_minorhub` (Zanaris, Configure,
  Last-destination, Favourites) and `fairyring_homehub`. Configure needs a wielded Dramen/Lunar
  staff ("The fairy rings only work for those who wield fairy magic.", wiki), opens 398 + 381
  (`ifOpenMainSidePair`), sets the visited codes' log text (desc) and runs clientscript 402
  (`fairyrings_sort_update`, mode 1) so the list is built after the texts arrive. Dial buttons and
  "Use code" set `fairyring_1..3` (3985-3987, what RuneLite's Fairy Rings plugin reads). Teleport:
  vanish seq 3265 + spotanim 569, 3 ticks (guess), telejump, appear seq 3266; sets
  `fairyring_lastloc_opvis` = 64 + state (the loc's "Last-destination (XXX)" op text). A code with
  no ring (9 unused + DIQ house ring) or the ring you stand in: no move, the player says "Wow, fairy
  magic sure is useful, I hardly moved at all!" (wiki). Zanaris op works without a staff only at
  `no_staff_return` rings. Visited codes in server varps 63860-63861. Teleport also sends
  TRIGGER_ONDIALOGABORT: the log's mobile search is a client-opened meslayer
  (`meslayer_mode20` registers `meslayer_ondialogabort`) that otherwise stayed open after the
  teleport (seen on the device; the fix is not device-checked yet). Not done: favourites, the
  AIR-DLR-DJQ-AJS hideout sequence, per-ring quest/visit requirements, the POH ring.
  Device-checked (86b06e4): menu ops, 398/381 layout on mobile, dial turns, BKS to Zanaris, travel
  log entry, Last-destination (BKS), no-move at the same ring.
- **Essence pouch Check wording:** "There are three essences in this pouch." / "There is one pure
  essence in this pouch." / "There is no essence in this pouch." (number words, hyphenated; qualifier
  pure/daeyalt/guardian, none for rune essence), from RuneLite's Ess Pouch regex and tests.
  **Open question:** tictac7x Charges' colossal-pouch regex expects "normal" for rune essence
  ("three normal essences"), which Ess Pouch would reject; maybe colossal-only wording. Fill/Empty
  messages are parsed by neither plugin and were left unchanged.
- **Medium clues** (d8937ea core slice, 4719636 keys), `TrailTier.Medium` (3-5 steps, 3-5 casket
  rolls, Uri `npc.trail_medium_uri`, STASH Construction 42 with oak planks, 250 XP; varps
  63870-63875; no generic drop scroll: a scroll box gives a random step's own scroll). Steps (from
  the cluehelper tables): 61 talks (44 with challenge scrolls: 37 anagrams, 6 ciphers, 1 cryptic),
  44 coordinate digs, 25 double emotes, 21 searches (8 crate cryptics, 2 map crates, 11 key steps),
  10 map digs, 6 music (Subterranea is hard). The 11 anagrams without a challenge are plain talks:
  the cache has no medium puzzle box (puzzle boxes are hard and up: slide puzzle 306,
  `trail_puzzleinv`, piece enums 1864/1865 by varp `if1`, client-side moves in
  `trail_slidepuzzle_op`).
  - Challenge scrolls: the challenge obj's param 623 names the clue row whose npc hands it out (not
    the question row); question/answer from `cluehelper_challenge_question`. Flow: talk with the clue
    -> the npc adds the challenge scroll; talk again -> count dialog; right answer takes the
    challenge scroll and moves the trail on. All npc lines are best guesses ("Ah, a clue scroll.
    Answer my question and I'll give you the next clue.", "<npc> has given you a challenge
    scroll!", "Please enter the answer to the question.", "That isn't right, keep trying.", "Spot
    on!"). The clue scroll is kept while the challenge is open (whether OSRS swaps it is unknown).
    Read on a challenge scroll shows the question in 203 (the plugin matches the question).
  - Coordinates: Jagex's short text `00.05S 01.13E` becomes "00 degrees 05 minutes south<br>01
    degrees 13 minutes east" (what the plugin splits into 10 words). No sextant flow; dig with a
    spade within 1 tile.
  - Double emotes (wiki, emote clues): Uri comes after the first emote; the second must be done
    before talking to him, otherwise "I do not believe we have any business, Comrade." and he stays.
  - Maps: medium map objs `trail_clue_medium_map001-012`; the newer ones use interfaces of the same
    name (`trail_clue_medium_map008-012`, likewise `trail_clue_easy_map006`), the older ones were
    matched by rendering: rows 2235 Draynor bank = trail_map03, 2236 W of Crafting Guild = map07, 2237
    McGrubor's crate = map10, 2238 Tower of Life = map15, 2239 Clock Tower crate = map16, 2240
    Chemist's house = map17, 2246 Ranging Guild = map04. Crate maps are Search steps with a map.
  - Keys: killing one of the key row's npcs while on the step drops the key for the killer
    (`TrailKeyDropHook`, NpcDeathKillHook; every kill drops it, duration 200 ticks: our choices);
    searching the target loc (its open variant) with the key takes the key and moves on.
  - Medium casket: `MediumCasketTable` (dbtable 60008, rows 61000-61191), the wiki's 192 rows with
    exact per-roll rarities over 23,181,180. Not rolled: the 1/30 master clue tertiary.
  - Music scroll text is now the wiki's "I'd like to hear some music. Come and see me on the bridge
    in Falador Park, and play: <col=ffffff>Track</col> Thanks, Cecilia" (line breaks guessed); the
    plugin keys on the "i'd like to hear some music." prefix.
  - Device-checked 9a73dbe (03:32): challenge flow at Baraek end to end, coordinate scroll text
    parsed by the plugin, dig to casket, medium casket rewards. Not tried: double emotes, keys,
    map crates, medium STASH.
- **Upstream:** `23-openable-drawers-chests`, `24-treasure-trails-drawers-and-maps` (needs 21, 23),
  `25-ice-mountain-music-area`, `26-fairy-rings`, `27-essence-pouch-check-wording`.

## Eleventh server dev (2026-10-17)

- **Rune pouch** (`content/skills/magic/rune-pouch`, `RunePouchScript`): RuneLite's Rune Pouch
  overlay reads only varbits: `rune_pouch_type_1..6` (29, 1622, 1623, 14285, 15373, 15374; the
  rune's compact id, enum 55 `rune_compact_ids`, back through enum 982) and
  `rune_pouch_quantity_1..6` (1624-1626, 14286, 15375, 15376), on items 12791/24416/27281/27509.
  OpenRune already cast spells from those varbits but could not fill them. Now:
  - Open (iop 1) sets varp `if1` = 3 (regular) or 4 (divine) and opens 190 as the main modal; the
    client draws everything (`rune_pouch_init`, `rune_pouch_drawpouch_slot`,
    `rune_pouch_drawinventory_slot` in osrs-dumps). IF_SETEVENTS on `rune_pouch:pouch` 0-5 and
    `rune_pouch:inventory` 0-27: ops 1-6, 10, Depth1, DragTarget (the event set is our choice).
  - The op labels depend on `rune_pouch_selectedquantity` (16201: 0=1, 1=5, 2=All, 3=custom) and
    `rune_pouch_customquantity` (16206): op1 is the selected quantity, op2 = 1, op3 = 5, op4 =
    custom, op5 = X (count dialog), op6 = All, op10 Examine. The client sets the varbit itself when
    the 1/5/X/All buttons (static `transmitop` components) are pressed; the server mirrors each
    button so its copy matches. X op2 "Set custom quantity" asks for the amount (guess: it also
    selects the custom mode).
  - Pouch drags swap the two slots' varbits (the client swaps locally in
    `rune_pouch_pouchreorder`); inventory drags move the inventory slot.
  - Empty (iop 4) and use-rune-on-pouch (stores all). Capacity 16,000 per type (wiki).
  - **Guessed messages:** "You can only store runes in the rune pouch.", "Your rune pouch has no
    room for another type of rune.", "Your rune pouch cannot hold any more of those runes.",
    "You don't have enough inventory space.", "Your rune pouch is empty.".
  - Not done: Configure/loadouts (bank), slots 5-6 (league relic), runes auto-placed in the pouch.
- **Plank sack** (`content/other/plank-sack`): the hub Plank Sack plugin reads only the varbits
  `plank_sack_plain/oak/teak/mahogany/camphor/ironwood/rosewood` (18171-18177) and checks that
  obj 24882 is in the inventory. Fill (iop 1) and plank-on-sack store up to 28 planks in all
  (wiki); Check (iop 3) prints one game message per stored type, "Regular planks: 3" / "Oak
  planks: 5" ..., and Empty (iop 4) / Check on an empty sack say "Your sack is currently empty."
  (these follow tictac7x Charges' regexes; the per-type split and the order are guesses). Empty
  puts back what fits. `PlankSackStorageHook` lets content take planks from the sack while it is in
  the inventory (inventory first; it never captures incoming planks).
  **Guessed messages:** "You don't have any planks to put in the sack.", "Your sack is full.",
  "You don't have enough inventory space to empty the sack.". Not done: the bank's Fill/Empty
  toggle, sawmill planks into the sack.
- **Fairy ring search input** (client dev's report on 9a73dbe: "Search for destination: *" stayed
  open after a teleport and swallowed the next chat command): the log's search is opened by
  `meslayer_mode20`, which calls `meslayer_open(28)`; `meslayer_ondialogabort` closes only modes
  7/8/9/11/14-21/27/29/30, so TRIGGER_ONDIALOGABORT could never close mode 28, in any order. The
  teleport now runs clientscript 101 `meslayer_close(28)` after the IF_CLOSE.
- **Upstream:** `29-rune-pouch`, `30-plank-sack`; `26-fairy-rings` regenerated.
- **XP commands** (ours, `TestCommands`): `::setxp <skill> <xp>` sets a stat's xp anywhere in
  0-200,000,000. Raising it goes through `statAdvance` (rate 1, `XpSource.Manual`); lowering it
  sets the xp and level directly and sends UPDATE_STAT. `::addxp <skill> <xp>` adds xp, capped at
  200M. A skill is matched by its stat name or a prefix (`::setxp mining 20000000` = virtual
  level 105 for Virtual Levels; the level sent stays 99).
- **Friend add** (`FriendListAddHandler`): OpenRune Central (`dev.or2.central.social.SocialService`,
  decompiled) adds the friend, then `pushFriendEntryToOwner` pushes an entry only when the friend
  is online (visible world > 0). Login/logout status already fans out (`onCharacterOnline/
  Offline`, through `writeCentralFriendPresence`, world name "OpenRune 255"). So adding an
  offline friend showed nothing until relog. After a successful add, the handler now reads the
  social snapshot and sends that friend's entry (added = true; world name "OpenRune" from the
  snapshot path). Not tried on a client yet. Upstream `31-friend-add-update`.
- **Hard clues, stage 1: core tier and casket** (`TrailTier.Hard`): 4-6 steps and 4-6 casket rolls
  (wiki); scroll objs are the 187 "Clue scroll (hard)" objs by param 623; Uri `npc.trail_hard_uri`;
  STASH Construction 55, teak planks, 400 XP (wiki STASH page); server varps 63880-63885. Steps on
  the cache data (`::clue hard list`): 41 talks (20 with challenge scrolls: 12 anagrams, the rest
  ciphers), 76 digs (54 coordinates, 18 cryptic digs, 4 map digs), 19 searches (incl. 3 map
  crates), 17 emotes, 10 music.
  - Left out for now: the 5 anagrams with a puzzle box (challenge in `cluehelper_challenge_box`
    830), the anagram whose target is a loc ("WOO AN EGG KIWI", Awowogei's loc 4771), one
    coordinate row whose short text does not parse. The wizards on 54 of the coordinates and the
    Double agent on emote steps come in stage 3; until then the dig gives the next clue at once and
    Uri appears at once.
  - **Hard maps** (rendered from the if3 dumps with a scratch Java composite renderer over the
    cache's `ModelDecoder`: each `type=model` component drawn at its x/y with its modelangle and
    zoom, then compared with the OSRS Wiki's `Treasure Trails/Guide/Maps/Hard` images): row 2247
    Lumberyard crate = `trail_map05`, 2248 Yanille anvil = `trail_map08`, 2249 West Ardougne =
    `trail_map12`, 2250 Observatory crate = `trail_map13`, 2251 Dark Warriors' Fortress crate =
    `trail_map14`, 2252 volcanoes = `trail_clue_hard_map006`, 2253 picnic tables =
    `trail_clue_hard_map007`. The single-model maps 20-24 match none of the current hard maps
    (elite, presumably); `trail_map19` is unused too.
  - **Hard casket** (`HardCasketTable`, dbtable 60009, 234 rows): the wiki's per-roll rarities
    (the `{{#expr:}}` products) over their common denominator 6,971,250; they sum to 48,751/48,750,
    so the weights total 6,971,393. The super attack/strength/defence set (one wiki "dual drop") is
    three separate rows (guess). Names resolve to the lowest non-noted, non-placeholder obj of that
    name, skipping `fake_`/`br_`/`poh_`/`magictraining_`... variants; "Zombie head (Treasure
    Trails)" = `obj.zombie_head`. Not rolled: the 1/15 master clue.
  - Upstream `32-treasure-trails-hard`.
- **Hard clues, stage 2: puzzle boxes.** 8 hard steps (5 anagrams, 3 ciphers) have a
  `cluehelper_challenge_box` challenge (row 830, "A puzzle box."). The box is the step scroll's own
  obj plus `_puzzlebox` (e.g. `trail_hard_anagram_exp9` -> `trail_hard_anagram_exp9_puzzlebox`);
  its picture comes from dbtable `trail_puzzle_boxes` (60010): castle = varp if1 1 (pieces
  `trail_slidingpuzzleb01-24`, enum 1354), tree = 2 (`c`, 1355), troll = 3 (`d`, 1356). Box ids by
  picture are from the OSRS Wiki's Puzzle box infobox; that castle/tree/troll = b/c/d (1/2/3) is a
  guess from the id order. Flow (`TrailPuzzle`, `talkWithPuzzle`): the npc gives the box (inventory
  space needed) and the server fills `trail_puzzleinv` (140) with a scramble of 101 random slides
  from the solved state (Mod Ash); Open sets if1, opens 306 as the main modal, IF_SETEVENTS op1 on
  `trail_slidepuzzle:pieces` 0-24 and transmits inv 140; each op1 (the slot the client moved, see
  `trail_slidepuzzle_op`) is mirrored with the same gap rule; solved = pieces 0-23 in order and slot
  24 empty. Talking to the npc with a solved box takes the box, clears inv 140 and moves the trail
  on. Npc lines are guesses ("Ah, a clue scroll. Solve this puzzle and I'll give you the next
  clue.", "<npc> has given you a puzzle box!", "You haven't solved the puzzle yet. Come back when you
  have.", "Well done, you solved it!"). Whether inv 140 survives a relog is OpenRune's inv
  persistence; Open re-scrambles when it does not hold the box's pieces. RuneLite's Puzzle Solver
  reads inv 140 and widget 306:4 (20054020). Not device-checked yet. Upstream
  `33-treasure-trails-puzzle-boxes`.
- **Puzzle boxes device-checked** (deploy at 08:55 UTC, with stage 3): `::clue hard 1554` (BLUE GRIM
  GUIDED, Lumbridge Guide): the guide gives the box ("...Solve this puzzle and I'll give you the next
  clue."), Open shows the tree picture in 306 on mobile, "Move Sliding piece" on long-press, a tap
  next to the gap moves the piece and the server mirror keeps inv 140 in step (RuneLite's Puzzle
  Solver, from inv 140, redrew its path and moves-left after the move; its dots sit on the mobile
  cell centres). `::clue solve` then Open shows the solved picture ("Solved!" in the plugin);
  talking to the guide: "Well done, you solved it!", the box goes and the next clue scroll comes.
  The host's debug panel covers the puzzle's left column on the device (taps there do nothing).
- **Hard clues, stage 3: wizards and the Double agent** (`summonFoe`, `TrailFoeHook`): a hard
  coordinate row's `combatEncounter` npc (54 rows: `npc.trail_hard2` Saradomin wizard 2955, or
  `npc.trail_hard` Zamorak wizard 2954) appears beside the player on the first dig, says "For
  Saradomin!" / "Die, human!" (wiki), and attacks (`opPlayer2`); the dig pays only after the kill
  (aux varp = 3). Hard emote steps after the first (wiki: none on the first step) summon
  `npc.double_agent_2` (108; `double_agent_1`, 65, inside x 2944-3391, z 3520-3967 = our wilderness
  box) with "I expect you to die!"; killing it while wearing the clue's outfit brings Uri at once,
  otherwise the emote again brings him (wiki). One foe per player, 500-tick lifetime (our
  choice); despawned on logout. Not device-checked (the integration tests cover it). Upstream
  `34-treasure-trails-hard-foes`.
- **Mobile social tabs** (client dev's dump on 245ed50: the two-faces tab is `side_channels` 707
  with four sub-tabs and an empty contents pane; 429 sits in the loading state "Loading friends
  list", so friend_count < 0):
  - The Android dispatcher: FRIENDLIST_LOADED (69) sets the friend manager's state (+0x150) to 1;
    UPDATE_FRIENDLIST (25) reads the list (`FUN_0066e448`) and sets it to 2 (`FUN_004fbd30`).
    OpenRune sends both at login (desktop order checked with `desktop_login.py`: FRIENDLIST_LOADED
    3rd, UPDATE_FRIENDLIST/UPDATE_IGNORELIST about 120 packets later, after IF_OPENTOP), so on the
    device the update apparently doesn't land. Fix to try (`LoginScript.resendSocialWhenLoaded`,
    mobile only): send the social snapshot again after `loadDelay()` + 2 ticks.
  - `SideChannelsScript` (gameframe): 707's tabs (transmitop 1) had no handler and nothing was
    opened into `side_channels:contents`. Now the selected tab (`side_channels_tab_selected`,
    13071) opens chat-channel `chatchannel_current` (7), `clans_sidepanel` (701),
    `clans_guest_sidepanel` (702) or `grouping` (76) when 707 opens and on each tab op (the
    tab->panel order is from OSRS memory: a guess).

## Twelfth server dev (2026-10-17)

- **Mobile friends list, outgoing trace** (9747c09, deployed 10:39 UTC with 20d266c): RSProt
  `OutgoingPacketTrace` logs the first n outgoing packets of each Android session as `OUT #n op=..
  len=.. Class hex` (payload before payload encryption). Off by default since the next commit: env
  `RSPROT_OUT_TRACE=<n>` or `::traceout [n]` (applies to the next logins). Login burst on the
  device (one flush, 10:39:55): `#1 RebuildLoginV2`, `#2 ChatFilterSettings`, `#3 op=69
  FriendListLoaded len=0`, ..., `#232 IfOpenTop`, `#288 op=25 UpdateFriendList len=0` (empty list,
  same bytes as desktop), `#289 op=13 UpdateIgnoreList len=0`, first `ServerTickEnd` later. The
  20d266c resend went out 2 s later: `#367 FriendListLoaded`, `#377 UpdateFriendList len=0`. So the
  first UPDATE_FRIENDLIST **does go out, after LOADED, correctly encoded**; the client loses its
  effect. After the resend, 429 shows "You may add friends..." (device-checked), Add Friend for the
  headless `desktop` account shows "desktop / World 255" (online), and 707's sub-tabs open, in
  order, Chat-channel (7), Your Clan (701), View another clan (702), Grouping (76): the tab order
  guess in `SideChannelsScript` is right (device-checked).
- **Client statics** (re/decomp, Ghidra addresses): dispatcher `0x541dd4` case 0x45 sets the friend
  manager (`client[0x8d8]`) state +0x150 = 1; case 0x19 calls `FUN_004fbd30` = read list
  (`FUN_0066e448`, loops while pos < size, so an empty list just sorts) and state = 2, then
  `FUN_0043aaf0` (change event). No pre-switch filter. friend_count (cs2 3600, `0x5ebdb0`):
  state 0 -> -2, 1 -> -1, 2 -> count. `FUN_004fc0d8` is the friend system clear (state 0, lists
  emptied); its only caller is the login-success path in `FUN_0051aa80` ("Login completed"). Only
  case 0x45 writes state 1. So "-1" after the first burst means the 0x19 handler's state 2 was
  never in effect after LOADED; a clear afterwards would give -2. **Root cause still open
  (client side)**; the encoding, ordering and sending are ruled out. Next check (no restart needed,
  commands in this build): `::socialresend off`, relog, see the loading state, then `::social
  update` alone (only UPDATE_FRIENDLIST): if the list appears, the first one was dropped or
  undone by something other than LOADED. A client-dev hook on `0x4fbd30`/case 0x45 logging the
  cycle and state at login would settle it. Guess: real OSRS sends UPDATE_FRIENDLIST ticks after login
  (once Central answers), never in the login burst, so the delayed resend matches OSRS timing; it
  stays (`// unverified`).
- **Hunter traps** (`content/skills/hunter` `HunterTrapScript`, pack `HunterTrapTables`: dbtables
  `hunter_traps` 60011 and `hunter_trap_creatures` 60012, rows 60350-60360, timer 470). For
  RuneLite's Hunter plugin, which tracks the trap locs only:
  - Lay (item op 1, `seq.hunting_setting_trap_small`, 3 ticks): the set loc (`hunting_ojibway_trap`
    9345 / `hunting_boxtrap_empty` 9380, shape 10, angle west) appears on the player's own tile and
    the player steps off (W, E, S, N), so the plugin's "placed on last tick's tile" check holds.
    Limit 1 + one per Hunter 20/40/60/80 (wiki).
  - Every 3 ticks (wiki: box traps; birds the same, guess) a set trap whose owner is off it rolls
    against the nearest matching creature within 2 tiles: chance out of 256 on the crimson swift's
    curve (wiki: 39.45% at 1, 164% at 99 = OSRS skilling low 100 / high 419) at level minus the
    creature's requirement plus 1 (guess for the others). Caught: the npc despawns (50 ticks,
    guess), loc -> `*_trapping_<creature>` (box: `_n` variant) -> 2 ticks -> `*_full_<creature>`.
    Missed: `*_failing` -> broken/failed. Every swap is a del + add in the same tick so the tile
    never looks empty to the plugin.
  - Ops from the cache (bird snare): set = Dismantle/Investigate, full = Check/Reset, broken =
    Dismantle/Reset (box trap ops assumed the same). Check: bones + raw bird meat + 5-10 feathers
    (wiki for the crimson swift, guess for the others) or the creature item, the trap back, XP,
    "You've caught a crimson swift.". Reset = Check/Dismantle, walk onto the tile, lay again.
  - **Guesses:** "You begin setting up the trap.", "You can't lay a trap here.", "You don't have a
    high enough Hunter level to set up more than N trap(s).", "This bird snare hasn't caught
    anything yet.", "You dismantle the trap.", "This isn't your trap.", "Your trap has collapsed."
    (after 200 ticks untouched; the trap item is dropped), traps dropped on logout.
  - The script is `@Singleton`: the test harness (and Guice) made two instances, and the soft
    timer fired on the one without traps. The test world does not process walking, so the tests
    teleport the player off the trap.
  - Device test: `::item hunting_ojibway_bird_snare 3`, `::npc 500 hunting_bird_jungle` beside the
    player (spawned npcs stand still), Lay, step away. Box traps need Hunter 27+ (`::setxp hunter
    ...`). Upstream `35-hunter-traps`.
- **Boss kill counts for KPH Tracker:** OpenRune already has a generic hook
  (`content/generic/killcount`, `KillcountNpcKillHook`): an npc with `param.killcount_varp` adds 1 to
  that varp and sends "Your <npc> kill count is: <col=ff0000>N</col>", which KPH Tracker matches
  (`message.contains("Your Giant Mole kill count is:")`, number = the digits after stripping
  `<col=ff0000>`) and RuneLite's chat-commands pattern takes. Scurrius (`rat_boss_*`) already had
  it. Added to the Giant Mole (`npc.mole_giant`, OSRS varp `total_mole_kills` 1515), which spawns
  in its lair (Falador mole hole) and with `::npc 500 mole_giant`. KPH Tracker's Giant Mole entry
  is display type 0 (the kill time comes from its own hitsplat timer; no "Fight duration" message
  needed). Not device-checked. Upstream `36-giant-mole-kill-count`.
- **Kitten Tracker:** OpenRune's cats (`content/other/pets/cats`) already follow the wiki (growth
  event every 150 ticks, adult after 120, hunger 24+3+3 min, attention 25/18/51 min, stroke and
  feed messages Kitten Tracker matches; varp `follower_npc` = type << 16 | slot, which the plugin
  reads as `varp 447 >> 16`). Changed (`CatCare`): every growth event the follower says "Meow!"
  (guess at the wording; the plugin counts growth by the follower's overhead text), growth waits
  while a modal is open (wiki: paused during dialogue), and the run-away lines are "Your kitten got
  lonely and ran off." (what the plugin matches) / "Your kitten got hungry and ran off." (guess).
  Kittens come from Gertrude after Gertrude's Cat. Not device-checked. Upstream
  `37-kitten-growth-and-wording`.
- **Friends list, device run 2026-10-17 07:13-07:27 EDT** (deployed a114f17 + 4fc1f1d + 5ff197d at
  11:13 UTC; the client dev's `setprop debug.osrsrl.friendprobe 1` logs the friend manager's state
  changes): with "desktop" (offline) as a friend, the first UPDATE_FRIENDLIST (len 15) set state
  1 -> 2; after deleting "desktop", the first UPDATE_FRIENDLIST was len 0 (empty) and still set
  1 -> 2, also with `::socialresend off`, and 429 showed "You may add friends..." without the resend.
  So the stuck "Loading friends list" does not reproduce on this build: neither the empty list nor
  the packet order is the cause. Most likely a one-time state of the earlier build or session
  (unverified). The resend is now **off by default** (`::socialresend on` turns it back on); the
  running container still has it on until the next deploy.
- **Follow-up (low priority): FRIENDLIST_DEL on Android.** The server receives `FriendListDel`
  ("desktop", traced) and Central deletes it (gone after relog), but the mobile friends list keeps
  showing the entry until relog. Whether the client removes the entry itself (desktop
  `FriendSystem.removeFriend` does, then sends) or waits for something from the server is not known
  for Android: the sender is in code Ghidra did not analyse (CS2 interpreter, ELF 0x4e82d0; the
  packet constructor is `0x79cc90` with opcode 41). OpenRune's `FriendListDeleteHandler` sends
  nothing back.
- **Hunter traps device-checked** (core Hunter plugin via `EXTRA="hunter" tools/plugin-test.sh`):
  `::item hunting_ojibway_bird_snare 3`, `::npc 500 hunting_bird_jungle`, Lay: "You begin setting up
  the trap.", the snare appears on 3222,3218 and the player steps to 3221; the plugin draws its trap
  circle; the swift is caught within a few ticks; Check gives "You've caught a crimson swift.",
  bones, raw bird meat, 6 red feathers, the snare back and Hunter XP. Box traps not tried on the device.

## Thirteenth server dev (2026-10-17)

- **Varrock platebody smelting** (`SmeltingScript`): RuneLite's Smelting plugin counts a bar per
  SPAM message starting "You retrieve a bar of" or "The Varrock platebody enabled you to smelt your
  next ore". OpenRune gave a free second bar with the game message "Your Varrock armour helps you
  smelt an extra ...". Now, at the Edgeville furnace with an eligible armour tier (10%, wiki), the
  next ore set is smelted at once: a second set of ores is used, a second bar and its XP are given,
  and the spam line is "The Varrock platebody enabled you to smelt your next ore simultaneously."
  The prefix is the plugin's. "simultaneously." is from memory: the wiki page only says the message
  changed on 14 January 2016 "to be less misleading" (**guess**). The extra bar counts towards
  Make-X and is skipped on the last bar of a Make-X (**guess**, inferred from "next ore"). The
  iron 50% failure roll applies to the first bar only (**guess**). Upstream `38-varrock-platebody-smelting`
  (needs 09).
