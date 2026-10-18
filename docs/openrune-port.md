# OpenRune port

Staged port of our Android test server onto OpenRune-Server, approved 2026-10-16 after the spike
(`docs/openrune-spike.md`). Build and run steps: `openrune/README.md`.

- **Upstream pin:** our fork AlexPerrin/OpenRune-Server, main c2492318d (2026-10-18, = upstream main with our merged
  fixes), in `openrune/scripts/openrune.sh`. Was OpenRune-Server 9a8b338a8 until the twenty-second server dev's re-pin.
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
| Long varps in compiled clientscripts | our workaround: `openrune/scripts/fix_long_varps.py`; the fix belongs in OpenRune's CS2 compiler (Neptune `0.0.7-openrune`) | Varps typed `long` are read/written with opcodes 1/2 instead of 64/65, which breaks every script using them (the Grand Exchange setup panel, `ge_itemsink_get`) |
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
  Tier caps (checked against the wiki pages for armour 1-4, OpenRune's `SmithingBonuses`, unchanged):
  the bar's Smithing level must be at most steel 30 / mithril 50 / adamantite 70 / any for armour
  1/2/3/4, Edgeville furnace only, 10%. Ranking the bars by level (so gold, 40, needs armour 2, and
  blurite/silver/elemental count under armour 1) is OpenRune's reading of "up to and including"
  (**guess**).
- **Device run 2026-10-17 07:41-07:54 EDT** (deployed 424fc37 + 11ac6d7 at 11:42 UTC): box traps
  with the core Hunter plugin: `::setxp hunter 14000000`, `::item hunting_box_trap 3`, `::npc 500
  hunting_chinchompa`, Lay at 3224,3220 ("Trap placed by tester" in the plugin log, timer circle),
  the trap turned into a "Shaking box" (Check/Reset), Check gave "You've caught a chinchompa.", a
  chinchompa, 198 Hunter XP and the trap back ("Trap removed" in the plugin log). Smelting at the
  Edgeville furnace (`::tele 3108 3499`): "You retrieve a bar of bronze." and the Smelting overlay
  "Bars: 1". The 10% Varrock extra bar was not seen (one bar only; full inventory). The tester now
  wears Varrock armour 1. `server/tools/device.sh login` now checks the OpenRune log.
- **Herbiboar** (`content/skills/hunter` `HerbiboarScript`, pack `HerbiboarTables`: dbtables
  `herbiboar_legs` 60013, `herbiboar_spots` 60014, `herbiboar_tunnels` 60015, `herbiboar_herbs`
  60016; varps `herbiboar_harvested` 63890, `herbiboar_route_length` 63891). For RuneLite's core
  Herbiboar plugin.
  - From the cache/map (dumped with a throwaway test over `MapLocListDecoder`, regions 57-58 x
    59-60): 5 start locs `hunting_trail_spawn_fossil1-5` (Inspect / Check count / Toggle warning),
    19 clue locs `hunting_trail_clue9_*`/`10_*`/`11_0` (Inspect), 9 tunnels
    `hunting_trail_end_fossil` (Attack / Search), 275 footprint multilocs on the 24 trail varbits
    the plugin reads (`hunting_trail_state9_0-9`, `10_0-9`, `11_0-3`; 3 bits; transforms: 0-2 none,
    3/4 visible per direction, 5/6 faded). The herbiboar npcs `fossil_herbiboar_1-9` are already
    spawned at the tunnels: multinpcs on `fossil_herbiboar_visible` (5943) = tunnel index ->
    `fossil_herbiboar_visible` (7785, Harvest). `hunting_trail_ends_fossil` (5766) = tunnel index,
    `hunting_trails_used_fossil` (5767) = tracking.
  - Network: each plugin `TrailToSpot(varbit, dir)` under a clue loc is read as a leg that
    *leaves* that loc in direction dir; its other end is the group or tunnel at the far end of the
    footprints. Start legs are the unlisted directions. A trail is dir while chosen but hidden
    (the plugin highlights its loc as the one to search), dir+2 when revealed, dir+4 once a later
    leg is revealed. **Guess**, but it matches the cache transforms, the plugin's reset rule
    ("finished" when no trail is > 0 and != its listed dir) and the wiki's "guaranteed tracks".
  - Flow: Inspect a start (Hunter 80, boostable) -> first leg revealed, next leg hidden, "You find
    some fresh tracks." (**guess**); Inspect the right clue loc -> the wiki's per-object message
    ("You find a partial footprint in the disturbed earth...", "This mushroom is oozing...",
    "Something has disturbed this seaweed recently, it smells.", "The mushroom cap looks freshly
    eaten..."), 50 Hunter XP and a FossilDropLines roll (numulite 5-24 at 63/175, unidentified
    small/medium/large/rare 7/350, 7/700, 7/875, 7/3500); the first and last inspections give no
    roll (wiki). Wrong loc: "Nothing seems to be out of place here." (**guess**). Dead end: the
    wiki's "The creature has successfully confused you with its tracks..." and a reset. Route
    length 3-5 legs (**guess**); a leg into a group with a tunnel (C 5, D 4, F 9, J 6, K 2) or a
    leg into a start area ends the route and sets 5766 ("The creature's tracks lead into a nearby
    tunnel.", **guess**). Attack the tunnel -> herbiboar visible, Hunter XP 1,950 at 80 +30/level to
    94, 2,385 at 95, +19/level (wiki, Mod Ash), "You stun the herbiboar." (**guess**). Harvest
    (Herblore 31): 1-3 herbs (+1 with magic secateurs) of one type from the wiki chart cascade
    (`herbiboar_herbs`, OSRS 256 formula; level ranges within two levels of the wiki table), 25
    Herblore XP per herb after the first, count +1 (Check count: "You have harvested N
    Herbiboars."), "You harvest herbs from the herbiboar, whereupon it escapes." (**guess**).
    Toggle warning: the wiki's two messages on `fossil_herbiboar_already_caught_ignore_warning`.
  - Not done: the new-track warning dialog (starting a new track just drops the caught one, as
    the wiki says it disappears), Herbi pet, herb tuft (Hunter Rumours), horn of plenty, the
    appear animation npc (5945). Anims are stand-ins (`picking_low`, `human_kicks_chest_open`).
  - Tests: `HerbiboarScriptTest` (tunnel XP, herb ranges, graph sanity, 20 full routes from all
    5 starts through tunnel and harvest, wrong loc). Upstream `39-herbiboar`.
  - Device test: `::tele 3686 3871` (middle start), `::setxp hunter 14000000`, Inspect the rock,
    follow the plugin's highlight.
- **Herbiboar device run 2026-10-17 08:14-08:27 EDT** (deployed 14127aa at 12:14 UTC; `EXTRA="hunter
  herbiboars"`; tester now has Herblore 14M XP, Hunter already 14M): Inspect at the middle rock ->
  "You find some fresh tracks.", footprints drawn east; the plugin then showed group C with the
  next object highlighted, the trail tiles outlined and the minimap trail; Inspect the muddy patch
  -> wiki message, Hunter XP, an unidentified fossil once; group B seaweed -> wiki message; group A
  -> "The creature's tracks lead into a nearby tunnel.", plugin `finishId` = 8; Attack tunnel 8 ->
  "You stun the herbiboar.", the herbiboar appeared with Harvest, Hunter XP; Harvest -> 3 grimy
  dwarf weed (dropped: one free slot), +50 Herblore XP, "You harvest herbs from the herbiboar,
  whereupon it escapes.". The plugin read every varbit as expected (currentGroup C -> B -> A,
  finishId 8), so the leaves/hidden reading holds on the real client.
- **Client-side gap found (for the client dev):** the plugin's `inHerbiboarArea` stays false after
  a teleport or login: it calls `client.getMapRegions()` from `GameStateChanged(LOADING)`, and the
  host's `AndroidClient.getMapRegions()` returns an empty array while the game state is below
  LOGGED_IN. Nothing is drawn until the plugin re-checks; on the device I forced it with
  `setprop debug.osrsrl.probe 'HerbiboarPlugin.startUp()'` (cleared right after). Desktop RuneLite
  returns the regions during LOADING.
- **Hunters' Rumours, part 1** (no dialogues; `HunterRumours` + `HunterRumourScript`, pack
  `HunterRumourTables`: dbtables `hunter_rumour_hunters` 60017, `hunter_rumours` 60018; varps
  63892-63902). For the hub Hunter Rumours plugin, which reads npc DIALOG lines in the Burrow, the
  rare-part and whistle game messages and Hunter XP drops (ours match its tables: wagtail 95,
  chinchompa 198, carnivorous 265, herbiboar 1,950-2,461).
  - State: one rumour per guild hunter (`hunter_rumour_<name>`), the current hunter, the pity count,
    completed count (plus unlocked / back-to-back varps for the dialogue part).
  - Assignment (wiki table): each hunter's own list, the creature's level, never a creature another
    hunter has assigned. **Deviation:** only creatures this server can catch: tropical wagtail
    (Gilman), grey chinchompa (Gilman, Cervus, Aco, Teco), carnivorous chinchompa (all six),
    herbiboar (Gilman, Teco, Wolf).
  - Rare part on a catch of the current rumour's creature (trap Check, herbiboar harvest): 1 in rate
    or at the pity threshold (wiki: snare 1/20 pity 40, box trap 1/50 pity 100, herbiboar 1/7 pity
    14); none while any rare part is held; full inventory: a rolled part drops, a pity part is
    destroyed and comes on the next catch (wiki). Message: the wiki's "You find a rare piece of the
    creature! You should take it back to the Hunter Guild." in green (exact colour a **guess**).
  - Turn-in (`HunterRumours.turnIn`, for the dialogues later): takes the part, (Hunter level + 5) x
    50/50/55/60 XP (wiki), the tier's `hg_lootsack_t0-3` (they don't open yet).
  - Quetzal whistles (all four) Rumour op: "Your current rumour target is a grey chinchompa. You'll
    need to bring back some of its fur. Cervus was the source of the rumour." (wiki example; part
    words for the other creatures are **guesses**: tail feathers, fur); none: "You don't have a
    rumour to investigate at the moment." (**guess**).
  - `::rumour [hunter] [creature]` assigns, `::rumour turnin [hunter]`, `::rumour` shows the state.
  - Waiting on the client dev: whether the Android host emits DIALOG ChatMessages (the plugin's
    assignment/turn-in tracking). The guild hunter dialogues come after that. Upstream `40-hunter-rumours`.

## Fourteenth server dev (2026-10-17)

- **Butterfly netting** (`content/skills/hunter` `ButterflyScript`, pack `HunterButterfliesTable`:
  dbtable `hunter_butterflies` 60019, rows 60484-60490). Catch (op1) ruby harvest, sapphire
  glacialis, snowy knight, black warlock, sunlight moth, moonlight moth (`moth_moonlight` and
  `_low_wander`; `_noclick` has no op). Wiki: level 15/25/35/45/65/75, XP 24/34/44/54/74/84,
  barehanded at +10 levels, jar keeps the catch (`butterfly_jar_*`), no jar = released at once with
  its effect, the rumour wing comes without a jar too. Success: OSRS 256 roll on Hunter with the
  wiki's skilling success charts: black warlock net 20/296, barehanded/magic 40/316; sunlight net and
  barehanded 20/296, magic 40/316; moonlight net and barehanded 0/276, magic 20/286. Ruby, sapphire
  and snowy have no chart and copy the black warlock (**guess**). Below the level every swing fails
  silently (Jagex's did-you-know tip "You will just fail 100% of the time", **guess** that it covers
  butterflies). Release (iop4) on the jars: Attack/Defence/Strength +4 +15%, Hitpoints 15, sunlight
  20% + 6 of every stat but Prayer/Hitpoints and 8 Hitpoints, moonlight 22 Prayer (wiki), empty jar
  back, `hunting_butterfly_out_jar`. **Guesses:** one swing per click (2 ticks), the impling net
  animation also barehanded, respawn 7 ticks (wiki: 4.2 s at the Great Conch), messages "You manage
  to catch the butterfly/moth." (jar), "You catch the butterfly and release it." (no jar), "You
  release the butterfly." (Release). The hub Hunter Rumours plugin counts catches by Hunter XP drops
  (its creature table has 34/44/54/74/84), so the XP is what matters for it.
- **Rumours:** the five butterfly/moth rumours (rows 60491-60495): sapphire glacialis (Gilman 25),
  snowy knight (Gilman, Ornus 35), black warlock (Gilman, Cervus 45), sunlight moth (Gilman, Cervus,
  Teco; assigned from 75), moonlight moth (Gilman, Aco, Wolf; from 85); 1/40, pity 80 (wiki); parts
  `hg_butterflywing_blue/white/black`, `hg_mothwing_sun/moon`; whistle wording "some of its wings"
  (**guess**). The rare-part message colour is now the wiki's #229628 (was a guess).
- Tests: `ButterflyScriptTest` (jar swap, no-jar boost, Release once, barehanded below +10, rumour
  pity). Upstream `41-butterfly-netting`.
- Device test: `::item hunting_butterfly_net`, wield, `::item butterfly_jar 5`, `::npc
  butterfly_warlock` (or `::tele 2551 2915`, Feldip), Catch; `::rumour cervus black_warlock`.
- **Guild hunter dialogues** (`HunterGuildScript`; wiki transcripts for Huntmaster Gilman (Novice),
  Guild Hunter Cervus/Ornus (Adept), Aco/Teco (Expert), Wolf (Master), Guild Scribe Verity). The
  orchestrator confirmed the Android host emits DIALOG ChatMessages ("Name|text"). The hub plugin
  (dex read): in the Burrow, a DIALOG line from a hunter (prefix = the npc's cache name + "|")
  containing "would you like another rumour?", "here's your reward." or "another one done?" clears
  the current hunter's rumour; otherwise a line naming a rumour (its display names, so the
  carnivorous chinchompa rumour is now "red chinchompa", as in OSRS) or another hunter sets that
  speaker's rumour, and sets the current hunter unless the line has "would you prefer that one, or
  a new one entirely". Verity's "Would you like me to filter the rumour information a little
  more/less" toggles its back-to-back state.
  - Talk-to (op1): greeting and chat branch per hunter, then the rumour section; Rumour (op3) is the
    wiki's Quick-Rumour (straight to it; hand-in line "Thanks for that. I'll mark off that report
    for you. Would you like another rumour?"). Rumour section: not unlocked -> "talk to Verity"
    line (Wolf: "My apologies, but I don't have time to chat right now."); below the tier level ->
    "You need N Hunter to take on tier T Hunters' Rumours." (the wiki's Wolf box; for the others a
    **guess**); carrying the part -> hand in (`turnIn`) with the hunter's reward lines and "Get
    another rumour?"; own rumour current (or none current) -> the remind line; another hunter's
    rumour current -> the wiki's switch lines (Gilman: old one / brand new one / No; others: Yes/No,
    then the hunter's "I asked you to hunt..." line or a new one); else a new rumour with one of the
    5 (Teco 6) random wiki lines. `assign` returning nothing: "I don't have any rumours for you at the
    moment." (**guess**). Gilman with no old rumour while following another's: plain Yes/No
    (**guess**).
  - "[rare parts]" is always "some of its <part>" (**deviation**: the wiki's "one of its" needs
    singular part names).
  - Verity: first talk explains and sets `hunter_rumour_unlocked`; then "Nilsal, hunter. Anything to
    report?", "What does a scribe do?", "How many rumours have I completed?" (the count), "I have
    to go."; Rumour-settings (op3) toggles back-to-back (`hunter_rumour_back_to_back` 1 = off; new
    varp `hunter_rumour_last` 63903: the rumour last handed in is not offered while off). The
    "Repeat rumours will (no longer|now) be offered." boxes are from the wiki's notes (**guess** that
    they are message boxes). Not done: spare hunter gear trade, the "intel reshuffle" lines,
    Wolf's At First Light requirement.
  - Gilman's Trapper's Tipple (3 coins) and stew (20 coins), with the wiki's no-space lines.
- **Embertailed jerboa** (box trap row 60361, rumour row 60496): `npc.varlamore_hunterjerboa01`
  (the wiki's 13139), Hunter 39, 137 XP, a jerboa tail, Check message "You've caught an embertailed
  jerboa... or maybe not." (wiki); rumour for Gilman and Ornus (1/50, pity 100, part
  `hg_jerboatail_large`, "some of its tail" **guess**). The Eagles' Peak requirement is not checked.
- Tests: `HunterGuildScriptTest` (Gilman's sales: coins/space), `HunterRumoursTest` back-to-back.
  Upstream `42-hunter-guild-dialogues`.
- Device test (after a deploy): `::tele 1557 9462` is the Burrow beside Gilman (spawn
  `0_24_147_20_56` = 1556,9464), `::setxp hunter 14000000`; Talk to Verity, then Gilman "Can we talk about rumours?" and
  watch the plugin's infobox; `::rumour turnin` is no longer needed (carry the part and talk).
- **Device run 2026-10-17 09:01-09:13 EDT** (deployed 640abbd + b4acf3d + 2c1789e at 13:01 UTC;
  `EXTRA="hunter hub-hunter-rumours"`): in the Burrow the plugin's infobox shows (Current Hunter /
  Current Rumour / Back-To-Back). Cervus Talk-to: greeting lines and the 3 options. Verity (spawned
  with `::npc 500 hg_verity` next to the player; the Burrow npcs wander, so spawning one is the
  quickest way to tap it) first talk -> unlocked. Gilman Rumour op -> "We've got reports of a slightly
  different sunlight moth ... some of its wings." and the plugin showed Gilman / Sunlight Moth
  (Butterfly Net). Talk-to -> "Can we talk about rumours?" -> remind (no part yet: `::item` gave 0
  with a full inventory; `::item <name> 1` after dropping one). With the wing: "Another one done?
  You're really doing a lot for the guild." -> +5,200 Hunter XP ((99+5) x 50), a basic loot sack, the
  plugin cleared Current Rumour; "Get another rumour?" Yes/No. `::rumour gilman red_chinchompa` then
  Gilman's Rumour op remind line ("...about a red chinchompa...") -> the plugin showed Red Chinchompa
  (Box Trap), so the rename works. Not checked on the device: butterfly catches, jerboas, the switch
  lines, Verity's back-to-back toggle. Tester back at Lumbridge on the normal build, inventory full
  (one copper ore dropped in the Burrow, a basic loot sack added).
- **Varps set before the first processed tick** (client dev: follower varp 447 = 0 after a relog
  while the kitten follows; Kitten Tracker reads `(npcId << 16) | index`): `PetFollowers.onPostTick`
  respawns the follower on the login tick and sets `follower_npc`, but `VarPlayerIntMapDelegate` /
  `VarPlayerIntMapSetter` send nothing while `processedMapClock <= 0`, after
  `LoginScript.sendVars` already sent the saved value. Any varp set in that window was lost the same
  way. Fix (generic, the orchestrator's suggestion): the setters record such varps in
  `Player.varpsSetBeforeFirstTick`, and `PlayerMainProcess` resends them
  (`flushVarpsSetBeforeFirstTick`) on the player's first processed tick. Device-checked 09:35 EDT:
  after two relogs the Kitten Tracker overlay shows the kitten (it did not before). No unit test (no
  game-test harness in `api/game-process`/`api/player`). Upstream `43-varps-before-first-tick`.
- **Jewellery teleports** (new module `content/travel/jewellery-teleports`, dbtables
  `jewellery_teleport_items` 60020 / `jewellery_teleport_dests` 60021, rows 60500-60556): games
  necklace (8), ring of dueling (8), amulet of glory (6, then uncharged), skills necklace (6, then
  uncharged), combat bracelet (6, then uncharged). Rub = held op 4 (iop `[null, Wear, null, Rub, Drop]`) -> `menu()` (interface 187 via
  script 217 with hotkeys; RuneLite's Better Teleport Menu hooks scripts 217/218/219 and group 187,
  read from its dex in the previous dev's scratch `gaps/better-teleport-menu`); worn ops 2-7 =
  destinations in the wiki's worn-option order. Wiki: destinations and tiles, "You rub the
  necklace..." / "ring...", "Your games necklace has three uses left.", "Your ring of dueling
  crumbles to dust." (purple). **Guesses:** the menu title "Where would you like to teleport to?",
  "You rub the amulet/bracelet...", "Your amulet of glory has N charges left." and "You use your
  amulet's last charge." (also for the skills necklace/combat bracelet), the purple value 7f007f,
  714/111 + 4-tick teleport, Corporeal Beast and Tears of Guthix on plane 2. Validation (Wilderness
  20, or 30 for glory/skills/combat) happens before the charge is used. Not done: destination
  requirements, the wiki's landing radius. Tests: `JewelleryTeleportScriptTest` (charge swaps,
  crumble, uncharged glory in the worn slot, nothing without the item). Upstream
  `44-jewellery-teleports`. Device test: `::item necklace_of_minigames_8 1`, Rub, pick Burthorpe;
  Better Teleport Menu should decorate the menu.
- **Device run 2026-10-17 09:31-09:43 EDT** (deployed 64b76eb + the generic varp fix + Rub on op 4;
  `EXTRA="hub-better-teleport-menu hub-kitten-tracker barrows"`): (a) after relogs the Kitten Tracker
  overlay shows "Kitten status / Grown up in" (fixed). (b) Games necklace(8) Rub opens the menu
  "Where would you like to teleport to?" with the 5 destinations, and Better Teleport Menu adds a
  submenu arrow to Rub in the long-press menu; **but tapping a destination does nothing** (no
  teleport, no charge used), and the entries have no "1:" hotkey labels. **Open:** the menu
  (interface 187, script 217, `ifSetEvents(menu:lj_layer1, 0..127, PauseButton)`) seems not to
  return a RESUME_PAUSEBUTTON on Android: check with `LOG_ANDROID_PACKETS=true` what a tap sends
  (perhaps an IF_BUTTON on another component, or the mobile 187 layout differs), and whether
  `menu()` works anywhere on mobile (dog shelter Chase uses it). The worn destination ops were not
  tried. (c) Barrows: `::tele 3557 9700 3` (Ahrim's crypt; digging is blocked by the Strange Old Man
  until his quest), Search sarcophagus -> Ahrim spawned, killed with `::maxhit`; the Barrows plugin
  showed Ahrim ticked and Potential 9.88% (server-set varbits). Tester back at Lumbridge on the
  normal build; inventory: a games necklace(8) instead of a copper ore.

## Fifteenth server dev (2026-10-17)

- **`menu()` on Android (interface 187):** not a server bug. The cache's script 217 runs
  `if (~on_mobile = true) $boolean = false;`, so the mobile menu never shows "1:" hotkey labels
  (stock behaviour, as on live mobile). Entries are `cc_create` text children of `menu:lj_layer1`
  with no ops; a tap selects through the pause-button flag of `IF_SETEVENTS 187:lj_layer1 0..127`
  (same as desktop) and sends RESUME_PAUSEBUTTON. The tap did nothing because RuneLite's Better
  Teleport Menu gives every 187 entry an empty Java onOp listener (plus a low-priority "Set
  Hotkey" op 9) on script 218's post-fire, and the host consumed any click on a widget with a Java
  listener. The client dev fixed the host (1c7cee8); Rub → Burthorpe teleports with BTM loaded.
- **Device run 2026-10-17 10:02-10:10 EDT** (no restart; live build up to 5e0e648):
  - Worn games necklace ops: long-press the worn necklace lists Remove + the 5 destinations;
    Barbarian Outpost teleported (2520,3571), "You rub the necklace..." and "Your games necklace
    has five uses left." **Bug found:** after the charge swap the necklace(5) showed in the head
    slot (`invReplace` takes the first free slot). Fixed: `useCharge` replaces in place
    (`invReplaceSlot`), held and worn; the integration test now checks the slot. Not deployed.
  - Butterfly: wielded net, `::npc 500 butterfly_warlock`, Catch: "You manage to catch the
    butterfly." and the Hunter XP drop on the first swing (Hunter 14M).
  - Jerboa: box trap laid in Lumbridge, `::npc 500 13139` (typing `varlamore_hunterjerboa01` through
    adb lost the "a0"; the numeric id works), the trap shook, Check: "You've caught an embertailed
    jerboa... or maybe not.", +137 XP, a jerboa tail and the box trap back.
  - **Dog shelter Adopt crashed the player** (logged out): `Missing mapping for key:
    'varbit.dog_unlock_0'`. Upstream OpenRune bug: `Dogs.kt` builds `varbit.dog_unlock_$unlockBit`,
    and the default breeds' `puppy_colours` rows have `unlock_bit` 0 (only dog_unlock_1..9 exist).
    Fixed by treating 0 as "no unlock". **Guess:** that 0 means the quest's default breeds
    (labrador, pug, spaniel per the wiki); not checked row by row. Chase's 187 menus not yet seen
    on the device (needs the deploy).
  - Tester: LOGGED_IN at Lumbridge on the normal build, staff wielded; the whole previous inventory
    (games necklaces, loot sack, clue items, the new butterfly jar and jerboa tail, net, box trap)
    was deposited in the bank to make room.
- Upstream: 45 (jewellery charge slot, needs 44), 46 (default dog breeds, alone); all 46 `git am`
  on the pin.
- **Deploy 2026-10-17 14:21 UTC (a0e18ab)**, device run 10:32-10:40 EDT: Chase's Adopt opens
  "Puppy Adoption" (187) with Border Collie / Labrador / Pug / Spaniel (so unlock bit 0 is the
  default breeds, confirmed), Pug → the colour menu (Fawn/Black/Brown) → "That'll be 200 coins."; a
  worn games necklace(8) Burthorpe op leaves necklace(7) in the neck slot. Plugin evidence
  (`EXTRA="bosstimer dpscounter runecraft"`): DPS Counter shows "tester 4.8" and the Giant Mole box
  while hitting the Mole; Boss Timers shows no respawn InfoBox after the kill (client gap: it needs
  `NPC.isDead()`, NpcUtil.isDying's default branch); Runecraft draws its magenta rift outlines in
  the inner Abyss (`::tele 3030 4836`).
- **Achievement diary journals** (new module `content/interfaces/achievement-diary`, dbtables
  `achievement_diaries` 60022 / `achievement_diary_tasks` 60023, rows 60560-60652). The Achievement
  Diaries tab (`area_task` 259) is built client-side (script 709 `build_achievement_list`: 12
  rectangles at taskbox subs 0..11 in enum 595 order with op1 "Open <area> Journal", plus the
  progress bars from the diary varbits); the server enables op1 on `area_task:taskbox` 0..11 and on
  Open shows `journalscroll` (741): title "Achievement Diary" (**guess**; RuneLite's Diary
  Requirements plugin only needs it to start with that), qj1 = "Lumbridge & Draynor Tasks" /
  "Varrock Tasks" (the plugin's diary keys), then per tier a blank line, "<col=800000>Easy</col>"
  etc. (**guess**), and the tasks word-wrapped at 404 px with the p12_full glyph advances; then
  script 6845 (scroll setup, which the plugin re-runs). Task order from the wiki; wording from the
  plugin's task strings where it has them (the in-game text), the wiki's otherwise (**guess** for
  those 18). The cache's side-tab totals agree (Lumbridge 0/41, Varrock 0/42). Not done: other
  areas ("That diary's journal isn't available yet."), task completion (struck lines, the diary
  varbits), rewards. Device-checked 11:00 EDT with `EXTRA="achievementdiary"`: both journals open,
  and the plugin appends its requirement brackets, struck when met ("(7 Slayer)", "(15 Woodcutting,
  15 Firemaking)" wrapped to its own line, red "(Rune Mysteries)"). Quest Helper's "Start Helper
  Varrock Easy/Medium/..." popup also shows over the tab. Upstream `47-achievement-diary-journals`.
- `tools/repack.sh`: `OPENRUNE=1` now defaults to the live ports (SERVER 10.0.2.2:8080, GAME_PORT
  43594).
- Build tip again: after adding a pack module, `rm -rf openrune/src/.gradle/configuration-cache`
  before `openrune.sh build`, or the build regenerates `api/generated` without the new dbtables.

## Sixteenth server dev (2026-10-17): Grand Exchange

New module `content/interfaces/grand-exchange` (+ pack). Sized from RuneLite's
`GrandExchangePlugin` and the rev-241 scripts (osrs-dumps `2026-10-14-rev241`: `ge_offers_init` 803,
`ge_offers_switchpanel` 806, `script805`, `ge_offers_index_drawslot` 798, `ge_offers_setup_init` 773,
`ge_offers_setup_changequantity`/`changeprice` 777/778, `script819`/`script821` details,
`script816`/`script818`/`script9909` collect items, `ge_collect_*` 788-792).

- **Open:** a GE clerk's Exchange (op 3, `npc.ge_clerk_1..4`) opens `ge_offers` 465 + `ge_offers_side`
  467. The server first sets `ge_itemsink_obj_0..7` = -1 (they default to 0, which is obj 0 and makes
  `switchpanel` show an empty slot's details), `tradingpost_search` -1, the new-offer vars and
  `ge_transmit_taxrate` 20 (per 1000, i.e. 2%). The client draws everything itself.
- **What the client sends** (IF_SETEVENTS by the server): `index_0..7` (465:7-14) sub 2 = the slot
  box, sub 3 = Buy, sub 4 = Sell; `setup` (465:26) subs: 0 Choose item, 1 -1, 2/3 +1, 4 +10, 5 +100,
  6 +1K (buy) / All (sell), 7 Enter quantity (op 2 on a buy = all you can afford), 8 -1 price, 9 +1,
  10 -5%, 11 Guide price, 12 Enter price, 13 +5%, 14/15 custom ±X% (not done); `setup_confirm` 465:30;
  `details_status` 465:23 sub 0 Abort, sub 1 Modify (not done); `details_collect` 465:24 subs 2/3 =
  collection slots 0/1 (op 1 Collect-notes or Collect-item, op 2 Collect-items / Collect-note / Collect,
  op 3 Bank); `collectall` 465:6 sub 0 (op 1 inventory, op 2 bank); 467 items op 1 Offer.
  The quantity/price buttons change `ge_newoffer_quantity` (varbit 4396) / `ge_newoffer_price` (long
  varp 5753) on the client too; the server mirrors scripts 777/778 (`changeQuantity`/`changePrice`)
  and resends the vars (`resyncVar` when unchanged). OpenRune has no long varps: the price is kept in
  server varp `ge_setup_price` 63970 and sent with VARP_LONG.
- **Slot state:** server varps `ge_slotN_{item,price,count,done,gold,state}` (63920-63967, perm),
  sent with UPDATE_STOCKMARKET_SLOT_V2 (desktop status values: 2 open, 5 finished, +8 sell; the mobile
  client splits state and type itself). Login sends the non-empty slots and starts transmitting the
  8 collection invs (`tradingpost_sell_0..5`, `ge_collect_6/7`, enum 150; obj in slot 0, coins in slot 1).
- **Fill rule (ours, documented as such):** the server is the only counterparty, at the guide price
  (`MarketPrices` = the obj's store cost, the value RuneLite shows on Android). A buy priced at or above
  the guide fills completely at the guide 2 ticks after Confirm (`timer.ge_offer_fill` 480) and the
  overpayment is refunded as coins; a sell priced at or below the guide fills at the guide less 2% tax
  (floored, 5M cap per item, nothing below 50 gp; the wiki's exempt list is not done). Anything else stays
  open (BUYING/SELLING) until aborted; aborting returns the coins or items to the collection slot.
  No partial fills, buy limits, matching between players, history, sets, platinum tokens, coins from
  the bank. Prices and totals are capped at 2,147,483,647.
- **Collection Box:** the banker's Collect (existing `Banker.kt`) now has its slot events (402:5-12,
  subs 3/4) and Collect to inventory/bank (402:3/4).
- **Wording.** RuneLite only matches the prefixes "Grand Exchange: Finished" and "Grand Exchange:".
  "Grand Exchange: Finished buying N x Item." / "...selling..." and the abort line "Abort request
  acknowledged. Please be aware that your offer may have already been completed." are from memory
  (**guess**). The rest are **guesses**: the search title "What would you like to buy?", the count
  prompts "How many do you wish to buy?" / "Set a price for each item:", "<price> coins" in
  `setup_marketprice`, and the refusal lines ("You don't have enough coins.", "That offer is worth too
  much money.", "You can't sell that item on the Grand Exchange.", "You don't have enough inventory
  space.", "Your bank is too full."). A sell defaults to all of that item you hold (**guess**).
  `completedGold` for a sell is the gross amount, before tax (**guess**).
- **Members slots:** the client unlocks slots 4-8 from `%playermember` (varc 103), which the client
  sets itself; not checked on the device.
- **Tests:** `:content:interfaces:grand-exchange:integration` (7): fill and refund, a sell of noted and
  unnoted items with the tax, open + abort + collect-all, refused offers (coins, overflow, missing
  item), a full inventory keeping the collection, the button maths. Upstream `48-grand-exchange`.

### Diary journals for the other 10 areas (sixteenth server dev)

`achievement-diary` now has journals for all 12 areas (dbrows 61500-61509 for the areas, 61510-61918
for the tasks). The tasks were generated from the wiki's diary pages (raw wikitext, the numbered rows of
each `data-diary-tier` table, links and templates stripped, notes dropped); where RuneLite's Diary
Requirements plugin quotes a task (its `*DiaryRequirement.java`), its text is used instead (matched by
similarity >= 0.7, every plugin task matched once). Per-area totals: Ardougne 42, Desert 39, Falador
42, Fremennik 34, Kandarin 43, Karamja 44, Kourend & Kebos 43, Morytania 38, Western 44, Wilderness 40
(Lumbridge 41 and Varrock 42 kept as they were, which the generator reproduces except three wording
differences where the plugin's text was used). Headings are the plugin's keys ("Ardougne Area Tasks",
"Desert Tasks", "Falador Area Tasks", "Fremennik Tasks", "Kandarin Tasks", "Karamja Area Tasks",
"Kourend & Kebos Tasks", "Morytania Tasks", "Western Area Tasks", "Wilderness Area Tasks"). Wiki-only
wordings (97 tasks) are **guesses** at the in-game journal text. Deployed 12:01 with the GE build, not
device-checked yet.

### GE device runs and the long-varp compiler bug (sixteenth server dev)

- **12:01 run (0964ae7):** the clerk's Exchange opens 465 with all 8 slots ("Empty", Buy/Sell; the
  members slots unlocked) and the GE plugin posts EMPTY for each slot at login. Two problems: Exchange
  across the booth answered "I can't reach that!" (fixed in 6524cac with an Ap handler, as the bankers
  have), and Buy or Sell showed "Grand Exchange: Offer status" for an empty slot ("Sell offer", Dwarf
  remains) instead of the setup panel.
- **12:27 run (6524cac):** Exchange works across the booth, Buy opens the mobile item search ("What would
  you like to buy?"), and the server's vars reach the client (host hook `debug.osrsrl.set "quest
  COOKS_ASSISTANT <varp>"`: varp 3204 = -1, 375 = 0x1400010 i.e. ge_selectedslot 1 and taxrate 20, 563 =
  0x80000005 i.e. quantity 5 + sell, 1151 = 379 lobster; 465:27/28 hold the examine and "70 coins"). The
  panel was still wrong.
- **Root cause: OpenRune's clientscript compiler.** OpenRune recompiles 9917 of the 9975 clientscripts
  from the osrs-dumps sources (their data starts with the "[proc,name].cs2 [proc,name]" name string). For
  varps typed `long` in symbols/varp.sym it emits PUSH_VARP/POP_VARP (opcodes 1/2) instead of
  PUSH_VAR_LONG/POP_VAR_LONG (64/65). Jagex's own `ge_itemsink_get` (5733, OpenRS2 cache 2740) reads
  ge_itemsink_price_long_0 (5762) with opcode 64; OpenRune's with opcode 1, which pushes an int, so
  `long_max` is a long short and the proc returns obj 0 (Dwarf remains) for an empty slot, and
  `ge_offers_switchpanel` (806) takes the offer-status branch. Long varcs compile correctly (51/52).
  Affected: scripts 778, 779, 5156, 5732, 5733, 9911 (40 ops; the 17 long varps 5753-5769:
  ge_newoffer_price, ge_tax_slot_long_0-7, ge_itemsink_price_long_0-7).
- **Workaround:** `openrune/scripts/fix_long_varps.py` (run by `openrune.sh build` after add_varcs)
  rewrites those opcodes in the packed cache, re-containers the scripts (gzip) and updates idx12 and
  the archive-12 reference table (crc, sizes, version). It walks each script's instructions and skips
  any whose walk does not end at the instruction count. Idempotent. The proper fix belongs in
  OpenRune's compiler (Neptune fork `0.0.7-openrune`); listed under the upstream fixes.
- Static RE on the way (both address forms; Ghidra = ELF + 0x100000): CS2 stockmarket ops at ELF
  0x4ed838 / Ghidra FUN_005ed838 (3910 isofferempty = state==0, 3911 stable = 2, 3912 finished = 5,
  3913 adding = 1); slot packet decode Ghidra FUN_00700428 (desktop status byte, state = b&7, type =
  bit 3, 7 = version prefix); empty slot init FUN_00700408 (state 0, type 1).
- **12:43 run (834a1c7):** setup panel, VARP_LONG price (live), the client's tax line "345 coins (350 - 2%)",
  sell 5 lobsters at guide (SELLING -> SOLD, "Grand Exchange: Finished selling 5 x Lobster."), buy 10 at
  64 gp below guide (BUYING), Abort (CANCELLED_BUY, the abort line, 640 coins in the collection), collect,
  Collect-all. Found: RSProt's `UpdateStockMarketSlotV2Encoder` Reset writes slot 0 (bug in the pinned
  upstream source; Android delegates to it), so collecting one slot emptied slot 0 on the client. Fixed in
  rsprot.patch (32643cd), note in `openrune/upstream/ISSUE-rsprot-stockmarket-reset-slot.md`.
- **12:51 run (32643cd), GE device-checked end to end:** buy 10 at 76 gp above guide -> BOUGHT, "You bought a
  total of 10 for a total price of 700 coins", 10 lobsters + 60 coins refund in the collection; a 1 gp buy
  via Enter price stays BUYING; the GE plugin's sidebar lists "Lobster Bought 10 / 10" and "Lobster Bought 0 /
  1"; after an app restart and relog the plugin gets BOUGHT (slot 0) and BUYING (slot 1) again (state
  persisted); the banker's Collect opens the Collection Box with both slots, and Inventory collects slot 0
  (the reset now hits the right slot). Left open: the 1 gp lobster buy in slot 1 (abort to clear).

### Diary task progress (sixteenth server dev, diary B)

`DiaryTasks` (achievement-diary module) keeps done tasks as bits in server varps
`diary_tasks_<lumbridge|varrock>_<tier>` (63976-63983; the real per-task bits of
`lumb_dray_achievement_diary` / `varrock_achievement_diary` are not mapped, **guess**) and drives
what the client reads: `<area>_<easy|med|hard|elite>_count`, `started_<area>_diary`, and
`<area>_diary_<tier>_complete` once a tier is full (re-sent at login). The journal strikes done
tasks line by line (`<str>`). **Known gap:** no task-complete chat message; its wording is in neither
the wiki (full-text search) nor any RuneLite/hub plugin.

`DiaryHooks` calls, task numbers in journal order:
- Lumbridge & Draynor easy 3 Sedridor's essence teleport, 4 water runes (not Ourania), 5 Hans's age,
  7 oak logs cut and burned in Lumbridge (`diary_progress_lumbridge` 63984 bit 0 = cut), 10 bread on the
  Lumbridge range (`content.cooking_range_lumbridge`), 11 iron ore in the Al Kharid mine; medium 5
  Teleport to Lumbridge.
- Varrock easy 2 Aubury's essence teleport, 4 a plank at the sawmill, 12 earth runes; medium 8 Teleport
  to Varrock.
- Mining and woodcutting come from `SkillingActionCompleteEvent`; the rest are one-line calls in the
  content scripts. Area boxes (**guesses**): Lumbridge x 3170-3270, z 3190-3270; Al Kharid mine x
  3280-3310, z 3270-3320.
- Tests: `:content:interfaces:achievement-diary:integration` (4). Upstream `49-achievement-diary-areas-and-progress`.
- **Device run 13:10 (efbe8df), `EXTRA=achievementdiary`:** Hans's Age (spawned with `::npc 500 hans`)
  → the Achievement Diaries tab shows "Lumbridge & Draynor 1/41" (white, bar started) and the journal
  strikes "Learn your age from Hans in Lumbridge."; every area total in the tab matches the generated
  task counts (Ardougne 42, Desert 39, Falador 42, Fremennik 34, Kandarin 43, Karamja 44, Kourend &
  Kebos 43, Morytania 38, Varrock 42, Western 44, Wilderness 40); the Karamja journal ("Karamja Area
  Tasks") gets the Diary Requirements annotations. Not checked on the device: the teleport-spell,
  skilling and sawmill hooks, relog (covered by the integration tests and the login sync).
- **Friends chat / clan channel (for the client dev):** OpenRune has no friends chat or clan
  gameplay (no FRIENDCHAT/CLAN handlers or senders). The Android encoders are bound
  (UPDATE_FRIENDCHAT_CHANNEL_FULL_V2/SINGLEUSER, CLANCHANNEL_FULL/DELTA, CLANSETTINGS_FULL/DELTA,
  MESSAGE_CLANCHANNEL), so the cheapest route is a test command that pushes a fake channel (~1 h).

## Seventeenth server dev (2026-10-17): fake friends chat / clan channel

`::fakeclan` (`content/other/commands/ChatChannelTestCommands.kt`, a test command, so not upstreamed)
pushes a friends chat and a clan channel to the player's own client, for the plugin host's
`getFriendsChatManager` / `getClanChannel` / `getGuestClanChannel` readers. No other player sees it and a
relog drops it (the client forgets both channels on logout; the server keeps per-name state in memory only).

- `::fakeclan [join]`: UPDATE_FRIENDCHAT_CHANNEL_FULL_V2 JoinUpdate (owner = the player, channel "Test FC",
  kick rank 7) and CLANCHANNEL_FULL type 0 (hash 0x0BADC1A000000001, updateNum 1000, "Test Clan", kick rank
  100, talk rank -1), both with the player, "Fake Member1" and "Fake Member2" on the server's world.
- `::fakeclan add|del <name>`: UPDATE_FRIENDCHAT_CHANNEL_SINGLEUSER (added, rank 0 / removed) and
  CLANCHANNEL_DELTA (AddUserEvent rank 10 / DeleteUserEvent by list index) with the channel's current
  updateNum, then updateNum + 1 server side. That the client applies a delta only when its updateNum equals
  the channel's and then increments it is a **guess** from the desktop client's handling.
- `::fakeclan say <name> <text>`: MESSAGE_FRIENDCHANNEL and MESSAGE_CLANCHANNEL (crown 0); the message
  counter starts at (hour % 300) * 50000 per boot, as RSProt's notes suggest.
- `::fakeclan guest`: CLANCHANNEL_FULL type -1 ("Guest Clan", the player as rank -1). `::fakeclan leave`:
  LeaveUpdate for all.
- Ranks are **guesses** from RuneLite's `FriendsChatRank` (0 friend, 4 lieutenant, 7 owner) and `ClanRank`
  (-1 guest, 10 first clan rank, 100 administrator, 126 owner).
- CLANSETTINGS_FULL (added after the 13:42 run, which showed "Connecting..." in the clan tab without it)
  goes with each clan channel: same name, the channel's members as affined members with their ranks,
  talk rank -1, kick rank 100, guests allowed, no banned members, no settings (so rank titles are the
  defaults), creation time = epoch minutes, join rune day = days since 2002-02-27 (**guess**). Leave
  sends its LeaveUpdate too.

**What real friends chat would cost** (RuneLite's Chat Channel plugin: join/leave messages, member list,
rank icons; Friends Chat-style hub plugins use the same events). OpenRune has none of it: no handler for
FRIENDCHAT_JOIN_LEAVE (61), FRIENDCHAT_KICK (51), FRIENDCHAT_SETRANK (88), no channel registry, and
`MessagePublicHandler` only does public chat. A single-world version: channel registry by owner, join
rules (owner's friends list ranks from Central, join/talk/kick ranks), full and single-user updates to
every member, chat lines routed to MESSAGE_FRIENDCHANNEL, kick, the chat-channel setup interface (name and
rank dropdowns, persisted per owner), rejoin at login and the join/leave/"not in a channel" wording
(wiki). About 1-1.5 days without the setup interface, 2 with it; across worlds it would need Central
(OpenRune's Central only carries friends/ignores/PMs). Real clans (creation, CLANSETTINGS full/delta,
clan hall, guest channels, events) are bigger again (several days).

### Device run 13:42 (c262868) (seventeenth server dev)

- Friends chat **device-checked**: the chat-channel tab (side_channels sub-tab 1) shows "Test Fc", owner Tester,
  Fake Member1 (friend smiley), Fake Member2 (lieutenant star), tester (owner key), all "W255"; `add` puts a
  4th member in the list, `del` removes it, and `say` shows "[Test Fc] fake: ..." and "[Test Clan] fake: ...".
  So UPDATE_FRIENDCHAT_CHANNEL_FULL_V2, UPDATE_FRIENDCHAT_CHANNEL_SINGLEUSER, MESSAGE_FRIENDCHANNEL and
  MESSAGE_CLANCHANNEL are live-verified on Android.
- Clan: the channel exists (the clan line arrives), but the "Your Clan" tab (sub-tab 2) stays on
  "Connecting...", probably waiting for CLANSETTINGS_FULL (**guess**). The clan delta could not be seen in the UI.
- `::` command args arrive lowercased and split on spaces: names for `add`/`del`/`say` are one lowercase word.
- Diary B **device-checked**: the Lumbridge Teleport spell (spellbook) and an iron ore from the Al Kharid
  mine rock at 3295,3311 (`loc.ironrock1`) took Lumbridge & Draynor to 3/41 (with Hans), still 3/41 after an
  app restart and relog. The sawmill and the other hooks are covered only by the integration tests.
- Deploy with `openrune/scripts/openrune.sh deploy` (= `docker compose --env-file switch.env up -d
  --build`); a bare `docker compose up` comes up on 40255/8081.

### Device run 14:00 (7b070bc) (seventeenth server dev)

- CLANSETTINGS_FULL alone did not fill "Your Clan": `clan_sidepanel_draw` (osrs-dumps rev 241) draws the
  clan only when varbit `clan_affinitystatus` (933) = 3 and `activeclansettings_find_affined(clan)`;
  `script4288` maps the status to "Connecting..." (0), "No current clan" (1, 3 without settings),
  "Fetching data..." (2). The guest panel uses `clan_guest_affinitystatus` (12164) and the listened
  settings. Fixed after the run: `::fakeclan` sets them to 3 (leave: 1), and the login sets both to 1 for
  everyone (no clans here), so the tab says "No current clan" instead of "Connecting..." (1 is a **guess**).
  **Device-checked 14:09 (e70a4f1):** "No current clan" after login; `::fakeclan` fills "Test Clan" (3/3,
  Fake Member1, Fake Member2 and tester with key icons for 100/126, W255, Leave/Settings); `add newbie` shows
  4/3 with newbie, `del newbie` 3/3 again, so CLANCHANNEL_DELTA with the channel's current updateNum is
  right; `leave` goes back to "No current clan".
- Friend List **device-checked** with the headless desktop login (`tools/multiclient/desktop_login.py
  --user desktop`): the tester's friends tab shows desktop on "World 255" with "desktop has logged in.",
  then "Offline" with "desktop has logged out." a few seconds after the desktop session ends.
- "Test FC" shows as "Test Fc": the friends chat name travels only as a base37 long
  (`channelNameBase37`; base37 has no case), and the client formats it itself. The owner is a plain
  string (shown as sent). Nothing to fix on the server.

### GE partial fills and buy limits (seventeenth server dev)

- **Partial fills (our rule):** an offer the market takes (see the GE section above) fills in up to 4
  parts of `ceil(count / 4)`: 2 ticks after Confirm, then every 5 ticks (`GrandExchange.FILL_*`). Each
  part updates the slot (state stays BUYING/SELLING with completedCount/completedGold, so plugins get
  GrandExchangeOfferChanged with a growing quantitySold), moves its items/coins (and the buy refund) into
  the collection inv, and sends "Grand Exchange: Bought 3 / 10 x Lobster." / "Sold ..." (**guess** at the
  wording; RuneLite only matches the "Grand Exchange:" prefix). The last part sends the "Finished" line.
  Abort after a partial fill returns the unfilled part.
- **Buy limits:** `enum.ge_buy_limits` (65380, obj -> int, pack `ge_buy_limits.toml`) generated from
  the OSRS Wiki's `Module:GELimits/data.json` (17 Oct 2026; names matched to tradeable unnoted objs, with
  "Name (qualifier)" falling back to "Name"; 4186 objs; 776 tradeable names have no wiki entry and no
  limit). **Licence:** that file is wiki content by the wiki's contributors under CC BY-NC-SA 3.0
  (source URL, date and licence in its header and in upstream patch 50's description); OpenRune's own
  licence is ISC-style, so the data file keeps its own licence and upstream may prefer another source.
  A buy counts against the limit when it fills; the 4-hour window starts at the first buy and
  does not roll (as the wiki describes it). An offer over the limit stays BUYING and is looked at again
  every 100 ticks and at login. Per-player trackers: 16 entries in perm varps `ge_limitN_item/start/bought`
  (63985-64032; start in epoch minutes); with all 16 active, the oldest is reused (lenient). No connected
  limits (potion doses etc.).
- Tests: 10 in `:content:interfaces:grand-exchange:integration` (partial fill + refund, abort after a
  partial fill, the limit window with the rune pickaxe (limit 40), tracker reuse). Upstream `50`.
- **Device-checked 14:39 (09d83ad, `EXTRA=grandexchange`):** 10 lobsters at guide (70) -> "Grand Exchange:
  Bought 3 / 10", "6 / 10", "9 / 10" x Lobster, then "Finished buying 10 x Lobster.", with the slot's bar
  filling and the GE plugin notifying each line ("[RuneLite] Grand Exchange: Bought 3 / 10 x Lobster.").
  50 bronze pickaxes (limit 40) at 1 gp -> 13, 26, 39, 40 / 50 and then it stays BUYING ("You have bought a
  total of 40 so far for a total price of 40 coins."); Abort returned 10 coins with the 40 pickaxes.

### GE trade history (seventeenth server dev)

- The clerks' History (op 4, also across the booth) and the GE interface's History button (`ge_offers:history`)
  open `ge_history` (383), filled by `ge_history_init` (1644), one `ge_history_addline` (1645: index, obj,
  sold 1/0, count, long gold, long tax) per trade, and `ge_history_finish` (1646, "You have no recorded Grand
  Exchange trades." when empty). The Exchange button (`ge_history:exchange`) goes back to the offers.
- History: the last 40 trades, newest first, in perm varps `ge_historyN_obj/count/gold/tax` (63000-63159; obj
  holds obj + 1 with bit 30 = sold). A trade is recorded when its offer finishes, or when an offer with
  something traded is aborted (**guess**: when OSRS records it is not checked). Sale tax = done x tax on the
  average price.
- History line ops (IF_SETEVENTS on `ge_history:list` subs 0..239; a line's subs are line*6+n): op 1
  "Buy-offer" opens the Exchange with a buy offer for that item in the first free slot (quantity 1, guide
  price); op 10 examines.
- RUNCLIENTSCRIPT with long args ('Ï') is used here for the first time on Android (desktop encoder).
- Test: 1 more in the GE integration suite (11). Upstream `51`.
- **Device-checked 14:57 (620ac7f):** the clerk's History opens "Grand Exchange Trade History" ("You have no
  recorded Grand Exchange trades." at first); its Exchange button goes back; after buying 1 lobster and selling
  21, the list shows "Sold: Lobster x 21, 1,449 coins (1,470 - 21) = 69 each" above "Bought: Lobster, 70 coins"
  (so the long args parse on Android); the GE History button opens it too, and tapping a line (Buy-offer)
  opens a buy setup for Lobster at 70. Gap seen: lobsters are on the wiki's tax-exempt list, which is not done.

### GE tax-exempt items (seventeenth server dev)

`enum.ge_tax_exempt` (65381, pack `ge_tax_exempt.toml`, 48 objs) from the wiki's Grand Exchange page,
"Exempt from tax" (by the wiki's contributors, CC BY-NC-SA 3.0, attributed in the file header; "Energy potion"
taken as all four doses, watering cans matched only as "Watering can"). Sales of those items pay no tax
(`GrandExchange.taxPerItem(item, price)`); the history's tax column follows. The client's own setup line still
shows "(price - 2%)" for them (it computes that itself; **not checked** whether the real client knows the
exempt list). Upstream `52`.

### Diary task hooks, round 2 (seventeenth server dev)

20 more Lumbridge & Draynor / Varrock tasks, each a one-line call into `DiaryHooks` from existing content
(area boxes are **guesses**):
- Lumbridge & Draynor easy 2 cave bug kill in the Lumbridge Swamp Caves (x 3136-3263, z 9530-9600), 6 pickpocket
  a Man/Woman in Lumbridge, 8 Zombie kill in the Draynor Sewers (x 3072-3135, z 9600-9727), 9 raw anchovies in
  Al Kharid (x 3240-3340, z 3120-3230); medium 6 raw salmon in Lumbridge, 7 coif in the Lumbridge cow pen (x
  3253-3266, z 3255-3299), 8 willow logs in Draynor (x 3070-3135, z 3220-3300), 9 pickpocket Martin, 10 Chaeldar
  task (master id 4); hard 4 Waka canoe Lumbridge -> Edgeville.
- Varrock easy 1 Thessalia's store opened, 3 iron in the SE Varrock mine (x 3280-3292, z 3360-3372), 5 the
  Stronghold's War-floor ladder down (Famine arrival), 10 bowl spun then fired in Barbarian Village (x 3070-3112,
  z 3400-3445; bit 0 of server varp `diary_progress_varrock` 63160), 13 raw trout at Barbarian Village; medium 3
  kitten colour picked at Gertrude, 5 the four Stronghold emotes (Flap, Slap Head, Idea, Stamp; bits 1-4 of the
  same varp; any order, anywhere), 9 Vannaka task (master id 3), 10 at least 20 mahogany planks in one sawmill
  order; hard 3 Waka canoe shaped at the Edgeville station.
- Kills come from an `NpcDeathKillHook` (`DiaryModule`); fishing/woodcutting/mining from
  `SkillingActionCompleteEvent`; emotes from `PlayEmote`.
- Not done (content missing): agility, H.A.M. Hideout, Ava, fairy rings, Puro-Puro, lava runes, the tea stall,
  Benny, the dog bone, Haig Halen, the Apothecary, the spirit tree, the digsite pendant, the white tree, the
  balloon, Tolna, the dying tree and the Champions' Guild door.
- Tests: 7 in `:content:interfaces:achievement-diary:integration`. Upstream `53` (not compiled on its own pin).
- **Checks:** desktop client ("desktop", 16:45-17:04): Thessalia's Trade (Varrock easy 0 -> 1), pickpocket a
  Man in Lumbridge (Lumbridge easy 0 -> 1), iron in the SE Varrock mine (Varrock easy 1 -> 2); after 534b5dd
  (kill hook on `dropCoords` / `npc.type.name`): a Draynor Sewers zombie confirmed dead (gone, Bones at 3120,9654)
  -> 2, a Cave bug in the swamp caves confirmed dead -> 3. With 92d7e99 the zombie kill did not count; the death
  was not confirmed then, so the old hook's fault is likely but not proven. Android (17:05): pickpocketing a
  spawned Man in Lumbridge took the tester's Lumbridge & Draynor from 3/41 to 4/41 in the diary tab.
- Note: the swamp caves also hold "Cave bug larva" (`npc.cave_littlebug`), which does not count.

### Quest Helper runs on the desktop client (seventeenth server dev, 17:12-17:25)

Desktop RuneLite 1.13.2 with `re/hub/quest-helper.jar` (QH 4.17.0, the same jar as Android) sideloaded into
`C:\osrs-test\home\.rlcustom\sideloaded-plugins`; account "desktop". QH is started per quest with right-click on
the quest list entry -> "Start Quest Helper" (devtools: `click {"button":3}` + `click_menu_option`).

OpenRune quests besides Cook's Assistant / Sheep Shearer / Romeo & Juliet (rough size from the code): The Restless
Ghost (1508 lines, test), Ernest the Chicken (434 + Draynor Manor search, lever puzzle, gate; puzzle tests), Imp
Catcher (478, test), Rune Mysteries (125 + Duke Horacio / Sedridor / Aubury dialogue), X Marks the Spot (691, test),
Misthalin Mystery (3718, 2 tests), Gertrude's Cat (720), Children of the Sun (1567, tests), Daddy's Home (1252,
test, 1 TODO), His Faithful Servants (351). The quest list counts 11 quests ("Completed: 0/11", 18 QP).

- **The Restless Ghost: complete on the server.** Stage varp 107: 0 -> 1 (Father Aereck: "I'm looking for a
  quest!", "Yes.") -> 2 (Father Urhney: "Father Aereck sent me to talk to you.", "He's got a ghost haunting his
  graveyard."; gives the amulet) -> opening the coffin spawns the ghost (npc 922) at once -> 3 (ghost, wearing the
  amulet: "Yep, now tell me what the problem is.") -> 4 (Search the altar, loc 2146 multi on varbit 2130 = 1;
  skull taken; "The skeleton in the corner suddenly comes to life!", the skeleton attacks) -> 5 (open coffin
  2145 -> 15061, Search: "You put the skull in the coffin.", "Release! Thank you stranger.."); the completion
  scroll (1 QP, 1125 Prayer XP, Ghostspeak amulet).
- **Imp Catcher: complete on the server.** Varp 160: 0 -> 1 (Wizard Mizgog, "Give me a quest!" -> "Give me a
  quest please." -> "Yes.") -> 2 with the four beads (`::item 1470/1472/1474/1476`): "You give four coloured
  beads to Wizard Mizgog.", scroll (1 QP, 875 Magic XP, amulet of accuracy).
- **Rune Mysteries:** started only (varp 63 = 1 after Duke Horacio: "Have you any quests for me?", "Sure, no
  problem."; talisman given). Not finished (slot time).
- **Quest Helper on the desktop** (cause found by the orchestrator/client dev: the *sideloaded* QH jar cannot reach RuneLite's client resources (clue_arrow.png), so every ObjectStep threw; with QH from the Plugin Hub (4.18.0) the desktop matches Android; see docs/multi-client.md, f41e558). Original observation: the overlay box showed the right step right after "Start Quest Helper" (Restless
  Ghost stage 1: "Talk to Father Urhney, west of the Lumbridge Swamp."; Imp Catcher: "Collect one of each bead..."
  with the four beads in red), but disappeared after the next change (stage 1 -> 2; beads added to the
  inventory) and did not come back, although the quest list menu still offers "Stop Quest Helper". No highlights
  either. Not a server problem as far as I can tell (the varps and items are right); possibly the devtools-driven
  session (input without focus, a stuck right-click menu stayed drawn for a while), so worth a look on the client
  side before trusting it. On real OSRS the box would switch to the next step.
- Small things seen: dialogue lines sometimes print several times in the devtools dialogue read (the driver,
  not the game); the desktop account died to the altar skeleton (10 HP), the skull was kept.
- **Rune Mysteries: complete on the server** (17:33, desktop). Varp 63: 1 (Duke Horacio) -> 2 (Sedridor in the
  Wizards' Tower basement: "Okay, here you are." takes the talisman) -> 3 ("Go ahead."; research package) -> 5
  (Aubury: research notes; offers tea) -> 6 (Sedridor; the air talisman back; scroll: 1 QP, Rune Essence Mine
  access). The scroll opens while Sedridor's last chatmenu ("I'd better get going." / "Can you teleport me...") is
  still up (in OSRS the scroll follows the dialogue; cosmetic).
- **Ernest the Chicken: complete on the server**, except that the oil can came from `::item oil_can` (the basement
  lever puzzle has unit tests but was not walked on the desktop). Varp 32: 1 (Veronica) -> 1 after Professor
  Oddenstein's first talk -> 3 on the hand-in ("You give a rubber tube, a pressure gauge, and a can of oil to the
  professor.", the machine, Ernest: "a cash reward is always nice", scroll: 4 QP, 300 coins). Fountain: poison on
  fish food, poisoned food on the fountain (piranhas die), Search -> pressure gauge; compost heap with a spade ->
  key; closet door (loc 131 at 3107,3367) unlocks with the key; rubber tube on the floor at 3111,3367.
  **Deviation:** OSRS sets varp 32 = 2 after the first Oddenstein conversation (Quest Helper's stage 2,
  "talk to Oddenstein again"); OpenRune stays at 1 until completion. Quest Helper still guides through stage 1's
  steps, so this only changes its last step's wording. **Fixed** after the run: the first Oddenstein talk now sets 2 (upstream `54`; not checked in game yet).
- The desktop session logged out between 17:25 and 17:31 with no input (desktop.sh shows the login screen),
  consistent with the client's AFK IDLE packet, which OpenRune honours (patch 18).

### Quest code audit: X Marks the Spot, Gertrude's Cat, Misthalin Mystery (seventeenth server dev)

Read-only audit of the three quests (stage tables per quest are in the session report; summary here):
- **X Marks the Spot** (varbit 8063, 0-8): Veos (Lumbridge) 1-2, digs 3-6 at (3230,3209), (3203,3212), within 3 of
  (3109,3264) and the Draynor pig pen (3078,3259), Port Sarim Veos 7-8; rewards match. 9 tests. ~95%: the
  Lumbridge General Store had no spade (fixed below; Veos says the general store has one). The orb dig accepts any
  tile within 3 (Quest Helper uses the exact tile; low risk); Bob's clue text is paraphrased.
- **Gertrude's Cat** (varp 180 via `varbit.gertrudes_cat_progress`, 0-6): Gertrude 1, Shilop/Wilough 100 coins 2,
  milk on Fluffs 3, seasoned sardine 4 (random kitten crate of 6), kitten on Fluffs 5, Gertrude 6. 22 tests (varrock
  module). ~95%. **Open:** returning the kitten hides the shared Fluffs npc for 100 ticks for every player
  (Fluffs.kt:164-173); Fluffs' Stroke op runs the pick-up handler; Gertrude repeats "What about Shilop?" after the
  boys are paid but before Fluffs is found.
- **Misthalin Mystery** (varbit 3468, 5..135 in steps of 5 plus 111): all steps, puzzles (piano DEAD, gem panel,
  fireplace) and the boss fight exist; 22 + 1 tests. ~90% before the fixes below. The stage values could not be
  checked one by one against Quest Helper (**uncertain**).
- **Fixed** (upstream 55, 56): the Lumbridge General Store opens `inv.generalshop5` (its own stock with the spade and
  knife; it opened the Al Kharid stock) and Edgeville `inv.generalshop2`; Mandy outside completes Misthalin
  Mystery only at stage 130 (`MandyWaiting`) and with 3 free slots for the gems ("You need 3 free inventory spaces
  to take the gems." is a **guess**). Not checked in game yet.

### Desktop quest runs after 84030f3 (seventeenth server dev, 18:00-18:12)

- **Lumbridge General Store** now sells the spade and knife (stock checked in the shop interface).
- **X Marks the Spot: complete.** Varbit 8063: Veos (Lumbridge, "I'm looking for a quest.") -> 2 (clue; Veos says the
  general store has a spade), digs at (3230,3209) -> 3, (3203,3212) -> 4, (3109,3264) -> 5, (3078,3259) -> 6
  (Ancient casket), Port Sarim Veos (3055,3248) -> 8 (scroll: 1 QP, 200 coins, antique lamp, beginner scroll box).
- **Gertrude's Cat: complete** (milk and seasoned sardine from `::item`; the doogle/sardine seasoning is covered by
  the varrock tests). Varp 180: Gertrude -> 1, Shilop "What will make you tell me?" -> "Okay then, I'll pay." (100
  coins) -> 2, milk on Fluffs (lumber yard 1st floor) -> 3, seasoned sardine -> 4, the 4th crate (3303,3506) held
  the kitten, kitten on Fluffs -> 5 ("Fluffs has run off home with her kitten."), Gertrude -> 6 (scroll: 1 QP, 1525
  Cooking XP, chocolate cake, stew, a kitten).
- **Misthalin Mystery Mandy guards checked:** at stage 130 (set with `::quest misthalin 130`) and 2 free slots Mandy
  says her lines and then "You need 3 free inventory spaces to take the gems." (stage stays 130); with room she
  completes the quest (135; uncut ruby, emerald, sapphire). The full quest was not played (time).
- **Ernest stage 2:** after `::quest ernest reset` the first Oddenstein talk went straight to the hand-in (the reset
  leaves the quest's "found Ernest" attribute and `varbit.ernesthandin_complete` set; debug-command only), so the
  stage-2 fix could not be seen; it was widened to set 2 on any Oddenstein talk once Ernest is found (built, not
  deployed). Tip: the Mandy npc is only reachable from (1637,4816); interact from other tiles says "I can't reach
  that!".

## Eighteenth server dev (2026-10-17): Misthalin Mystery run, Ernest stage 2, gameframe fallback

### Misthalin Mystery: full playthrough with Quest Helper (desktop, fresh account qhtest2)

QH from the Plugin Hub (4.18.0) on the isolated desktop client; 90247ad live. The stage values in the server
(`MisthalinStage`) are exactly QH's step keys (0, 5, 10 ... 110, 111, 115 ... 130; read from the QH jar's
`loadSteps`), so the earlier "uncertain" note is resolved. QH showed the matching step at every stage checked.

Varbit 3468 trail: Abigale "Yes." -> 5 -> 10 (dialogue); bucket by the fountain -> 15; barrel Search -> Sid
cutscene -> 20; bucket on the barrel ("You fill the bucket from the barrel.") -> 25; Search -> manor key; front
door ("You use the manor key to unlock the door.") -> 30; knife from the table; pink-knob door -> Tayten cutscene
-> 35; note 1 Take + Read -> 40; knife on the painting -> 45; Search -> ruby key; ruby door -> 50; tinderbox from
the shelves, 4 candles -> 55 ("The room is sufficiently warmed, the fuse should now have dried out."); fuse,
leave -> explosion -> 65; climb the wall, observe the tree -> Lacey cutscene (answer "Count Draynor") -> 70; note 2
-> 75; piano D-E-A-D (interface 554, children 21/22/25/21) -> 80; piano Search (op 3) -> emerald key; climb back,
ruby door, emerald door -> 85; Bandos room door -> Mandy cutscene -> 90; note 3 -> 95; knife on the fireplace -> 100;
Search -> gem panel 555 (Sapphire 19, Diamond 3, Zenyte 11, Emerald 23, Onyx 7, Ruby 15) -> 105; Search -> sapphire
key; sapphire door -> confrontation -> 111; mirror: 3 reflections (57 ticks apart: the mirror parked at the west
wardrobe's row, last push west, scores every third cycle) -> unmask cutscene -> 120; killer's knife Take, Wield,
Fight Abigale -> 125; sapphire door ("You decide it's wise to leave the murder weapon behind.") -> 130; Mandy outside
-> 135. Scroll: 1 QP, 600 Crafting XP, uncut ruby/emerald/sapphire (matches the wiki).

- No server bug found in the quest. One fill of the bucket empties the barrel (the wiki only says "use the bucket on
  the barrel to empty it"; whether OSRS needs several fills is unchecked).
- Driving tips (camera, scene base, `invoke_menu_action`) are in docs/multi-client.md "Driving tips".

### Ernest the Chicken stage 2 (fresh account qhtest3, 90247ad)

Veronica -> varp 32 = 1; first Professor Oddenstein talk ("I'm missing the pressure gauge and a rubber tube...")
-> varp 32 = 2. Fix confirmed.

### Black screen after login: the fullscreen world map gameframe (upstream 57)

qhtest1 logged in to an all-black frame, and every later login did the same: the client root was
`toplevel_display` (165, the fullscreen world map frame) with no map and no 161/548. Its saved `varp.gameframe` was
165 (the others 161/548), and bit 8 of `varp.chat_filter_assist` was set (assumed to be
`resizable_stone_arrangement`; qhtest2 has it clear).
- Cause, reproduced on qhtest3: opening the fullscreen map sets the stone-arrangement bit (`changeGameframe` copies
  the destination's flag), and the bit stays set if the session ends while the map is open. A login whose saved frame
  does not match the client mode (`::varp gameframe 548` plus a resizable client) goes through
  `selectFallback(resizable = true, stone = true)`, which took the first frame with those flags: the fullscreen map
  frame (dbrow 65530, before pre-EoC 65532; the order is inferred from the repro). That frame is saved as
  `gameframe_toplevel`, so it sticks.
- How qhtest1 itself got there (where it opened the map and which login mismatched) is not known.
- Fix: the login restore, the fallback and the intermediate-frame lookup skip `toplevel_display`, and ours also skip
  the mobile toplevel. A saved fullscreen frame now falls back to a normal frame (this recovers qhtest1).
- Android is not affected: mobile sessions always open `toplevel_osm` before the fallback.
- Not changed: opening the fullscreen map still flips the stone-arrangement bit. With the fix, a later mismatched login
  picks pre-EoC ("modern" resizable) instead of classic.

### Quest audit vs Quest Helper 4.18: Children of the Sun, Daddy's Home, His Faithful Servants

Stage keys, varbits and step ids were read from the QH jar's bytecode (`loadSteps`/`setupSteps`).
- **His Faithful Servants** (varbit `hfs` 14973): the server uses 0, 2 started, 4 icon looted (BarrowsScript),
  6 icon given, 8 icon destroyed, 10 complete. QH: 0 talk; 2/4 Barrows run; 6/8 "Return to the Strange Old Man".
  Consistent.
- **Daddy's Home** (varbit `daddyshome_status` 10570): the server uses 0-5, 10 built, 12 reward pending,
  13 complete. QH: 2 remove; 3-4 Yarlo; 5-9 "search crate" (build); 10-12 Marlo. The skipped 6-9 share one QH step.
  Consistent; the build sub-steps are unchecked in game.
- **Children of the Sun** (varbit `vmq1` 9632; guards `vmq1_guard_1..10`, bandits 1-4, as in QH): QH has a step
  at 14 ("Report back to Sergeant Tobyn"), which the server never set. **Fixed (upstream 58):** the fourth correct
  mark sets 14, an unmark goes back to 12, and Tobyn's marking dialogue answers at 14. Setting 14 on the mark rather
  than on the report talk is a **guess** from QH's step text. Unit tests: `ChildrenOfTheSunInteractionTest` 36.

### Desktop QH runs after the 57/58 deploy (eighteenth server dev, fresh account qhtest4)

- **Children of the Sun: complete.** Alina ("When will this delegation arrive?") -> 2, delegation scene -> 6
  (tailing). The tail was skipped with `::quest children 10`: it is a real-time line-of-sight puzzle and is
  unit-tested. Tobyn -> 12; marking the four bandits (`vmq1_guard_1..4`, npcs 12668/12671/12674/12677) -> 14, and
  QH shows "Report back to Sergeant Tobyn" (upstream 58 confirmed); Tobyn -> roof 16 -> interrogation -> 24. Scroll:
  1 QP, Access to Varlamore.
- **Daddy's Home: complete.** Marlo -> 1, Yarlo -> 2. Demolish 6 pieces (op 1) and the carpet (op 5, origin
  (3238,3393)) -> furniture varbits 10563-10569 go 1 -> 2, stage 3. Yarlo (skipped the lecture) -> 5. Build via the
  POH build menu (interface 458, key "1") -> varbits 3; crates (40214) give 3 waxwood logs per search (no cap while
  the bed is unbuilt; OSRS behaviour unverified); the sawmill operator makes waxwood planks for free; bed built;
  Yarlo -> 10; Marlo -> 13 (crate, Rimmington house, 400 Construction XP). `::item saw` throws ("Missing mapping
  for key: 'obj.saw'", debug command only).
- **His Faithful Servants: complete.** Strange Old Man -> 2 (crumbling tome). All six barrows (Ahrim had the
  tunnel); chest (client id 20973 at (3551,9695)) -> stage 4, Strange icon, QH "Return to the Strange Old Man with
  the strange icon"; the old man -> 10 (Dusty lamp, Crypt map). Done with `::god`/`::maxhit`/`::master` (turned off;
  qhtest4 keeps 99s).

## Nineteenth server dev (2026-10-17): Gertrude's Cat leftovers, small follow-ups

osrs-mcp did not connect to this session (CONNECTION_CLOSED); it was driven by hand over stdio
(`tools/osrs-mcp/run.sh`, JSON-RPC `tools/call`), so the cache/wiki lookups below are from it.

### Gertrude's Cat: Fluffs (upstream 59)
- **Cache:** Fluffs is `npc.gertrudescat` 3497 (ops Pick-up, Stroke, Talk-to) with multivarbit/multivarp -1 and no
  transforms; `npc.vc_gertrudescat` 1010 (Talk-to only) is a separate npc. So there is no multinpc to hide her per
  player; the post-kitten hide stays global. It is now **10 ticks** instead of 100 (**guess**; OSRS's delay is
  unknown).
- **Stroke** (op 3) ran the Pick-up handler. It now has its own reply from the wiki transcript ("Hisss!" / "Ouch!" /
  "Perhaps the cat wants something?"). The transcript only shows Stroke before the milk; using that line at stages
  1-4 is a **guess**. After the kitten is home it purrs like Pick-up. Tests: 2 new in `GertrudesCatInteractionTest`.
- Not changed: the transcript's return line is "Fluffs has run off home with her offsping." (sic); ours says "with
  her kitten." Whether the typo is in-game or a wiki slip is unchecked. Gertrude's "What about Shilop?" repeat (see
  the seventeenth dev's audit) is still open.

### Daddy's Home waxwood cap (upstream 60)
The wiki's Waxwood log page: "three of them can be found within the crates ... Duplicates cannot be obtained." A
search now tops up to three, counting logs and waxwood planks in the inventory and bank; with three held the crates
say "You search the crates but find nothing interesting." (**guess** for that case). Test added.

### Fullscreen map and the stone-arrangement choice (upstream 61, needs 57)
`changeGameframe` copied the fullscreen frame's flag into `resizable_stone_arrangement` on open; closing to a
resizable frame set it back, but closing to a fixed frame did not, and a logout with the map open saved the map's
value. The flag is now remembered on open (player attribute) and restored on close and on logout while the map is
open. The flag still changes while the map is open (kept for the intermediate-frame emulation). Compiled; not yet
checked in game.

### `::item saw` (upstream 62)
The saw's gameval is `obj.poh_saw`; `::item` only took gameval names and threw. Unknown names now fall back to the
lowest-id unnoted, non-placeholder obj with that cache name (underscores as spaces).

### Tooling
`tools/multiclient/desktop.sh login` with no arguments is always desktop/desktop; the script records the account it
logged in (`C:\osrs-test\desktop-account`) and logs out a different or unknown account first (d8b21ad; not yet run).

### Desktop checks after the acb933a deploy (02:17 UTC, fresh account qhtest5)
- **Fluffs:** Stroke -> "Ouch!" / "Perhaps the cat wants something?"; Pick-up at stage 2 -> "Maybe the cat is
  thirsty?". Kitten on Fluffs -> "Fluffs has run off home with her kitten."; Fluffs and the kitten were gone ~4 s
  and Fluffs was back at (3306,3512) about 6 s later (the 10-tick hide).
- **Waxwood crates** (crate 40214 at (3243,3398), searched from (3244,3398); the west side says "I can't reach
  that!"): first search -> the objbox, second -> "You search the crates but find nothing interesting."
- **Fullscreen map:** `resizable_stone_arrangement` (varbit 4607) set to 0 in the resizable classic frame; opening
  the fullscreen map (orb op 3) -> 1 with frame 165; closing -> 0 and frame 161. The logout-with-map-open path was
  not tried.
- **`::item saw`** -> "Spawned inv obj `Saw` x 1".

### Children of the Sun guard tail: real run (desktop, qhtest5)
QH 4.18's follow step (`followGuard`) draws a line along the guard's path and marks five hiding tiles:
(3233,3427), (3240,3417), (3241,3403), (3236,3392), (3247,3397) (then the door (3259,3400)). The server's lookouts
(`CotsRoute.Lookouts`) are (3240,3429), (3242,3404), (3236,3390), (3248,3390), (3254,3396).
- A scripted run (`::quest children 6`, Alina, then walk to hiding tile k as soon as the guard leaves lookout
  k-1) was **never spotted**: the guard stopped at each lookout for ~4-5 s and the tail ended at the door with the
  eavesdrop scene ("What are you up to in there...?" ... "I'd better let that sergeant know.") and `vmq1` = 10.
- Standing in the open at (3236,3429) while the guard reached the first lookout -> "You failed to stay hidden from
  the guard." and the stage stayed 6.
- So QH's hiding tiles and the server's line-of-sight lookouts agree on this route. Whether OSRS's guard spots the
  player outside the lookouts too (ours only looks at the five lookouts) is not known.

### Item examine back to upstream (orchestrator request)
Our type-27 item examine (upstream patch 08) is reverted: `Player.objExamine` again runs the `examine_item`
clientscript (chat type 0, with the price/alch line), `ObjExamineTest` is dropped and patch 08 is withdrawn from
`openrune/upstream` (README note). The client now emulates RuneLite's item-examine handling.

### Gertrude's "Shilop" line (upstream 63)
At stage 2 (boys paid, Fluffs not found) Gertrude now uses the transcript's own exchange ("Hello again, did you
manage to find Shilop? I can't keep an eye on him for the life of me." ... "Thanks again, adventurer.") instead of
the stage-1 "What about Shilop?" lines. Once Fluffs is found she gives the sardine advice as before. Test added
(24 in `GertrudesCatInteractionTest`). Expressions are our choice.

## Doric's Quest (nineteenth server dev)

`content/quest/.../area/falador/DoricsQuest.kt`. Progress is varp 31 (`varp.doricquest`) through the server-only
varbit `doric_quest_progress` (bits 0-6, gameval 63743): 0, 10 started, 100 complete. QH 4.18's `DoricsQuest` uses
step keys 0 (talk to Doric: "I wanted to use your anvils.", "Yes.", "Yes, I will get you the materials.") and 10
(bring 6 clay, 4 copper ore, 2 iron ore); the cache dbrow `quest_dorics` has endstate 100 (checked at startup).
- Dialogue from the wiki transcript: the five opening topics, the start (bronze pickaxe), "Where can I find
  those?" (with the under-15-Mining lines), the already-carrying shortcut ("In fact, in the exact quantities too!"
  only when the counts are exact), the hand-in ("You hand the clay, copper, and iron to Doric."). Rewards (wiki):
  1 QP, 1,300 Mining XP, 180 coins.
- **Ours / guesses:** the journal text; the after-quest dialogue (the transcript has none; it reuses the greeting
  and two non-quest topics); the scroll item display (steel pickaxe); Doric's "Remember, I need ..." answer when the
  anvil is clicked mid-quest. Smith (op 1) on `loc.dorics_anvil` before completion brings up Doric's "Hey, who said
  you could use that?" dialogue through `LocOpOverrides`; using a bar or hammer on the anvil is not stopped. The
  whetstone line ("You should probably ask before using that.") is not wired.
- Tests: `DoricsQuestInteractionTest` (5): start, decline, too few materials, exact take with extras kept and a
  single reward, start-with-materials shortcut.

### Doric's Quest: desktop QH run (02:49 UTC deploy of 97b4b07, fresh account qhtest6)
QH 4.18 "Start Quest Helper" from the quest list showed "Bring Doric north of Falador all the required items" with
the item list red, and the dialogue highlights ("[1] I wanted to use your anvils.", "[1] Yes."). Doric -> varp 31 =
10 and a bronze pickaxe; after `::item` of the materials QH's requirements turned green; Doric -> 100, quest scroll
(1 QP, 1300 Mining XP, 180 coins, Use of Doric's Anvils), QH closed the helper. After `::quest doric reset`, Smith on
the anvil (2031) brought up Doric's "Hey, who said you could use that?" dialogue. Doric is reached from inside the
hut ((2951,3451)); from (2953,3448) it is "I can't reach that!".
- Fixed after the run (not yet deployed): the start line was one five-line chatbox; it is now two boxes ("... let
  you use my anvils." / "Take this pickaxe with you just in case you need it."), split point a **guess**. The
  start prompt title is "Start Doric's Quest?" (transcript) for names ending in "Quest", "Start the X quest?"
  otherwise (`startQuestTitle`).
- The quest list showed Doric's Quest (and The Restless Ghost) grey before starting, the others red; not looked into.

## Witch's Potion (nineteenth server dev)

`content/quest/.../area/rimmington/WitchsPotion.kt`. Progress is varp 67 (`varp.hetty`) through the server-only
varbit `witchs_potion_progress` (bits 0-1, gameval 63744): 0, 1 started, 2 ingredients given, 3 complete (endstate
3 checked at startup). QH 4.18's `WitchsPotion` uses 0 (talk to Hetty: "I am in search of a quest.", "Yes."), 1 (kill
a rat in the house to the west, then "Bring the ingredients to Hetty" once the tail is held), 2 (drink from the
cauldron, loc 2024).
- Dialogue from the wiki transcript, including the 14 "some ingredients" player lines (built from the four held
  flags) and the hand-in mesboxes. Drinking: "You drink from the cauldron. It tastes horrible! You feel yourself
  imbued with power." then the scroll (1 QP, 325 Magic XP). The cauldron before stage 2: "As nice as that looks, I
  think I'll give it a miss for now."
- **Rat's tail:** `WitchsPotionRatTail` (an `NpcDeathKillHook` bound in `QuestModule`) drops a rat's tail for the
  killer when `npc.rat` or `npc.rat_indoors` dies and the killer is at stage 1. The drop-table TOML `quest` gate
  cannot be used here: with `quest-requirements.mode: assume-completed` (our game.yml) `InProgress` is always false.
  Dropping it every kill at stage 1 (also when one is already held) is a **guess**.
- Hetty's old after-quest lines (rimmington `RimmingtonTownsfolkScript`) moved into the quest script (stage 3).
- **Ours:** journal text, scroll item (eye of newt), mesanims.
- Tests: `WitchsPotionInteractionTest` (5).

### Quest requirement mode (`assume-completed`)
OpenRune's `game.yml` key `gameplay.quest-requirements.mode` (ours: `openrune/src/game.yml`, copied from
`game.example.yml` by `openrune.sh init` and baked into the image by the Dockerfile) is `"assume-completed"`, the
upstream default ("Temporary core-mechanics default"). In that mode `QuestRequirements.satisfies`
(`content/quest/.../manager/QuestRequirements.kt`) answers only `Completed` with true: every quest gate that asks
"has the player completed X" passes (members areas, spells, crafting gates and so on), and every "is on X" /
"has not completed X" check is false, whatever the player's real quest varps say. The other modes are
`respect-progress` (real quest state) and `virtual-completions` (a configured list counts as completed, the rest is
real). It only affects code that goes through `QuestRequirements`; the quest scripts read their own varps, so
Quest Helper's stages are unaffected. Effect seen: drop-table `quest_mode = "during"` drops never drop (the Witch's
Potion rat's tail uses a kill hook instead). Switching to `respect-progress` would make gated content need real
quests (most quests are not implemented), so it is left as is.

## Goblin Diplomacy (nineteenth server dev)

`content/quest/.../area/goblinvillage/GoblinDiplomacy.kt`. Progress is the client varbit `gobdip_main` (2378, varp 62
`goblinquest` bits 0-5): 0, 3 started (orange wanted), 4 blue wanted, 5 brown wanted, 6 complete (cache endstate 6,
checked at startup). QH 4.18's `GoblinDiplomacy` has keys 0 and 3 (one step: crates, dyes, "Talk to one of the
Goblin Generals"), 4 ("again", blue) and 5 ("once more", brown); starting at 3 rather than 1 is inferred from QH
having no step for 1-2 (**guess**). The crate varbits `gobdip_crate1..3_searched` (2379-2381, bits 6-8) match QH's
north / west / upstairs requirements; Grubfoot is the multinpc `catwalk_goblin` on `gobdip_grubfoot_vis`
(13594, varp 62 bits 9-11: 0 brown, 1 orange, 2 blue, 3 hidden), set after each try-on.
- Dialogue from the wiki transcript: five random openers (the "fat" one with its own choice), the looping topic menu,
  "What about a different colour?" -> "Start the Goblin Diplomacy quest?", the armour/dye questions, wrong-colour
  replies on item use, and the three try-ons with Grubfoot (dialogue only; the cutscenes and the changing curtain are
  not played). Grubfoot's orange/blue lines follow his look. Rewards (wiki): 5 QP, 200 Crafting XP, a gold bar.
- The generals' and Grubfoot's old upstream dialogue (`goblin-village` `GoblinGenerals.kt`, `Grubfoot.kt`) is now the
  after-quest / no-quest talk inside the quest script; the two files are removed.
- Crates: "You find some goblin mail in the crate." (objbox) once each, then the transcript's "You search the crate,
  but but find nothing of interest." (sic). Searching before the quest also gives the mail (**guess**).
- Dyes: orange/blue dye on goblin mail -> orange/blue goblin mail ("You dye the goblin mail orange."); red + yellow,
  yellow + blue and red + blue dye mix to orange, green and purple ("You mix the two dyes and make orange dye." is a
  **guess**). Both are one transaction (two deletes and an insert).
- **Ours / guesses:** journal; "Human! Get us <colour> armour!" when asking for a different colour mid-quest; the
  hand-in option "I have some <colour> armour here." in the talk menu (QH's dialogue highlights include it); "Nothing
  interesting happens." for armour used before the quest.
- Tests: `GoblinDiplomacyInteractionTest` (4). `QuestTalkFixture` gained `interact { }` (any event) and a fixed
  `randomValue`.

### Witch's Potion and Goblin Diplomacy: desktop QH runs (03:23 UTC deploy of 00f46b8 + 8205c53 + 57bc566)
- **Witch's Potion (fresh qhtest7): complete.** QH "Start Quest Helper" -> Hetty ("[1] I am in search of a
  quest.", "[1] Yes.") -> varp 67 = 1; QH "Kill a rat in the house to the west for a rat tail." Killing an indoor
  rat (2855) dropped a Rat's tail next to the bones (the kill hook works). With `::item` onion, burnt meat, eye of
  newt QH showed all four green; Hetty -> 2; the cauldron (Drink-from via `invoke_menu_action`, scene base
  (2912,3152)) -> "You drink from the cauldron..." -> 3 and the scroll (1 QP, 325 Magic XP).
- **Goblin Diplomacy (fresh qhtest8): complete.** QH stage 0 showed the crate step ("The first is up the ladder in a
  crate in the south of the village"). Opener 5 then "Do you want me to pick...", "What about a different colour?",
  "Yes." -> `gobdip_main` = 3. Upstairs crate (16561 at (2955,3498,2)) -> `gobdip_crate3_searched` = 1 and QH moved to
  "Go back down the ladder."; the west crate (16560) and the north crate (16559, from the north side at
  (2959,3516); the south side says "I can't reach that!") gave the other two. Dyeing showed "You dye the goblin mail
  orange/blue." and QH turned "Orange goblin mail" green. Talk, "[4] I have some orange armour here." (QH
  highlights it) -> 4 and Grubfoot's look 1; blue mail used on Wartface -> 5, look 2, QH "once more ... Goblin
  mail"; Grubfoot's blue lines; talk, "[4] I have some brown armour here." -> 6, look 0, scroll (5 QP, 200
  Crafting XP, gold bar).
- **Found:** the lookout ladder (`goblin_ladder_bottom` 16450 / `goblin_ladder_top` 16556) had no handler ("Nothing
  interesting happens."); the run used `::tele` to level 2. Fixed after the run (built, not deployed): both climb
  in place between level 0 and 2 (`GoblinCaveLadders.kt`; the level comes from QH's tile for the crate).
- QH's dialogue highlights include "Yes, Wartface looks fat", which the transcript has as "Yes, he looks fat."
  (we follow the transcript).

## Vampyre Slayer (nineteenth server dev)

`content/quest/.../area/draynor/VampyreSlayer.kt` (Morgan, Dr Harlow) and `VampyreSlayerManor` in the same file
(garlic cupboard, coffin, Count Draynor). Progress is varp 178 (`varp.vampire`) through the server-only varbit
`vampyre_slayer_progress` (bits 0-1, gameval 63745): 0, 1 started, 2 stake received, 3 complete (cache endstate 3,
checked at startup). QH 4.18's `VampyreSlayer` keys: 0 Morgan ("Yes."), 1 garlic / Harlow / beer, 2 kill Count
Draynor (coffin `vampcoffin` 46237 at (3078,9776), npc 3481).
- Dialogue from the wiki transcript (Morgan's start, legends, garlic hint; Dr Harlow's beer, stake, topics, lost
  stake). Harlow's client varbits `harlow_given_beer` / `harlow_given_stake` are set; whether Harlow has already
  asked for a beer is our server varbit `vampyre_asked_harlow` (63746, varp 63745). Morgan's after-quest lines (and
  `morgan_postquest_dialogue`) moved here from `DraynorVillageNpcs`, whose version always showed them because
  `assume-completed` answers every "completed" check with yes.
- **Count Draynor:** opening the coffin at stage 1-2 raises a Count for that player (anim `vampireslayer_arise`,
  500-tick lifespan, removed on logout) who attacks him. Garlic in the inventory: "The vampyre seems to be weakened
  by the garlic you're carrying.", -10 hitpoints, -10 Attack/Strength, -40 Defence, applied as he rises (the wiki
  ties it to the first melee attack and a 30 s duration; ours lasts until he respawns: **simplified**). At 0
  hitpoints (death queue): stake and hammer -> "You hammer the stake into the vampyre's chest!", death with
  drops, quest complete (3 QP, 4,825 Attack XP); stake only -> "You're unable to push the stake far enough
  in!" + "The vampyre seems to regenerate!" and full heal; no stake -> "The vampyre seems to regenerate!" and full
  heal. His fast regeneration (wiki: ~20x) and returning to the coffin are **not** implemented. Attack anim
  `human_unarmedpunch` (**guess**; quest pack `vampyre_slayer_npcs.toml`). The stake is kept after the kill
  (**unknown** in OSRS).
- **Garlic cupboard** (Morgan's house, level 1): Open -> `garliccupboardopen` for 300 ticks; Search -> a garlic
  ("The cupboard contains garlic. You take a clove." is a **guess**); Close.
- Coffin outside stages 1-2: "The coffin is sealed shut." (**guess**).
- **Blue Moon Inn bartender** (new, `varrock/npcs/BlueMoonBartender.kt`, wiki transcript): "A glass of your finest
  ale please." -> 2 coins -> beer (QH's beer step), plus the fortune / equipment topics.
- Tests: `VampyreSlayerInteractionTest` (5). The Count fight is only checked in game.

## Twentieth server dev (2026-10-18): deploy fix, reconnect login, Pirate's Treasure

### Startup crash on the 77b17ed deploy (20fff49)
`VampyreSlayerManor` called `onNpcQueue(String, String)`, whose first argument is a *content* key, with
`npc.count_draynor`; the server threw at startup ("Invalid RSCM key. Expected prefix 'content'") and the live
container went down for ~40 min. The npc-type overload is used now. The unit tests never run plugin `startup()`, so
**boot-check every image** (`docker build -t osrs-openrune:bootcheck .` + run with `--tmpfs
/app/.data/postgres:uid=1000`, wait for "Server ready") before a deploy. `openrune.sh` build containers are capped at
`OPENRUNE_CPUS` (default 4).

### Reconnect login (6ab7d07, live 04:49 UTC)
OpenRune's `ConnectionHandler.onReconnect` was a TODO answering `ConnectFail` ("Unexpected server response" on the
client). Now (`api/net/.../player/PlayerReconnect.kt`): the request is queued to the game thread through
`AccountRegistry.queueReconnect`; the online player whose current session's `loginBlock.seed` equals the presented
key (and the same username when both have one) is moved to the new session: the info protocols' `onReconnect()`,
`writeSuccessfulResponse(ReconnectOk)`, a new `RspClient` on the same `Infos`, `RspCycle.reconnect` (re-assigns the
build area and npc view distance on the next update without a rebuild packet), counters cleared, old session closed.
The disconnection hook only marks the player gone for the player's *current* session. Rejected when a logout is
under way. `SessionStateEvent.Reconnect` makes `LoginScript` resend varps, stats, run energy and the transmitted
inventories in full (**our choice**: packets written while the socket was dead are lost; whether Jagex resyncs is
unknown). The grace period after a disconnect is 100 ticks (60 s) instead of 16: the wiki's Logout button page says
"a 60 second timer before the game forcibly logs you out after an unexpected disconnection during combat"; using it
out of combat too is a **guess**. `::drop` closes the connection without logging out.
- Desktop (qhtest9): `::drop` -> "qhtest9 reconnected (DESKTOP)", still LOGGED_IN; inventory updates, walking and npcs
  fine afterwards. A frozen client (~24 s, past the 15 s idle close) reconnected on resume. The >60 s case is left to
  an Android background run.

### Vampyre Slayer desktop run (qhtest9, QH 4.18)
Complete: Morgan "[1] Yes." -> 178 = 1 (QH "Get garlic from the cupboard upstairs"), cupboard garlic, Harlow's beer
request, the Blue Moon bartender's beer (QH requirement green), stake -> 2 (QH "Open the coffin and kill Count
Draynor"), coffin + garlic message, kill with stake and hammer -> 3, scroll (3 QP, 4,825 Attack XP). With `::master`
the regenerate path was not exercised. Goblin Village lookout ladder: up to (2955,3497,2) and back. Upstream 67.

### Count Draynor polish (twentieth server dev)
From the wiki's Count Draynor page, "Stat restoration" (Mod Ash, Discord 2025-08-13 and Twitter 2020): an AI timer
ticks every server tick during a fight. Every 5 ticks the Count restores 1 hitpoint and 1 point of each drained
Attack/Strength/Defence (his "20x" regeneration; the engine's normal regen still runs too). While the player carries
garlic, every tick each stat that is back at full is drained again: Attack -10 with "The vampyre seems to weaken."
(replaces the old "weakened by the garlic you're carrying", which no source has), Strength -10, Defence -40, and 10
damage when his hitpoints are full. **Simplified:** OSRS checks only while the player has an attack queued on him;
ours checks whenever the fight is on and the player is on his level. Not done: garlic's +1 damage to the player's
hits; returning to the coffin (no source found; he still disappears after 500 ticks or on logout).

## Pirate's Treasure (twentieth server dev)

`content/quest/.../area/portsarim/piratestreasure/`: `PiratesTreasure` (Redbeard Frank, journal, chest, message,
dig, Gardener, casket), `PiratesTreasureSmuggling` (Luthas, banana crate, Zembo's bar, Wydin's grocery crate),
`PiratesTreasureState` (shared state), `PiratesTreasureWydin` (job dialogue). Port Sarim module: customs officer
(`KaramjaFerryScript`), Wydin's talk (`PortSarimShopkeepersScript`) and back-room door (`PortSarimLocScript`);
Redbeard left `RustyAnchorScript`.
- **Progress:** varp 71 (`varp.hunt`) via server-only varbit `pirates_treasure_progress` (bits 0-2, 63751): 0, 1
  fetching rum, 2 key received, 3 note read, 4 complete (cache dbrow end state 4, checked at startup); QH 4.18 keys
  0/1/2/3. Sub-state in server varp 63747: `pirates_treasure_employed` (bit 0 plantation, bit 1 Wydin),
  `_crate_bananas` (0-10), `_crate_rum` (0 none, 1 in the crate, 2 shipped to Wydin's), `_gardener_met`.
- **Journal:** QH's `RumSmugglingStep` reads the stage-1 journal text. The wording and branch logic come from the
  2004Scape/Lost City reconstruction (`LostCityRS/Content` `quest_hunt/hunt_journal.rs2`, MIT; its comments say it
  matches OSRS via RSProx logs). Lines are hand-broken at the old `|` breaks (journal lines wrap at 64 chars) so
  each phrase QH matches ("I need to go to", "I have taken employment", "I have hidden my", "in the crate and filled
  it with", "the crate has been shipped", "I have taken a job at", "I should take it to", "I seem to have lost it.")
  stays on one line.
- **Dialogue:** wiki transcripts (Pirate's Treasure, Redbeard Frank, Luthas, Wydin, Customs officer, Zembo).
  Messages QH matches use its exact text and type (game message vs box).
- **Guesses / ours:** the customs officer's right-click Pay-Fare also confiscates rum; the Wydin job option only shows
  at stage 1; using the key on the chest replaces it with the message (2004; the wiki says the key snaps after the
  message is read); the pirate message opens the clue-text scroll; Frank replaces a lost key only when neither key
  nor message is in the inventory or bank; the dig spot is QH's tile (2999,3383) with radius 1; the first dig spawns
  the Gardener (overhead from the transcript) beside the player for 200 ticks, the next dig after he is gone gives
  the casket; quest-complete reward line "One-Eyed Hector's Treasure Chest"; "You don't have any bananas." on Fill
  with none; Luthas's line when the payment does not fit; no chest sound.
- **Not done:** rum breaking on teleport off Karamja, Karamja gloves / Ring of Charos(a), the banana and sliced-banana
  rum variants, Gerrant's apron pickup message (the apron is the existing `white_apron` ground spawn there). Banana
  trees are the generic `Pickables` (already in OpenRune).
- Tests: `PiratesTreasureInteractionTest` (10): start/decline, rum -> key (talk and use), lost key, chest -> message,
  casket contents and full-inventory refusal, the whole crate path (stash, pack, fill, pay, Wydin's crate), no
  stash before the job.

### Pirate's Treasure desktop run and the fixes after it (twentieth server dev)
- **Run (05:19 deploy of 981f490, fresh qhtest10, QH 4.18): complete.** Every QH step and dialogue highlight matched
  (journal sync, Zembo's rum highlighted in the shop, crate rum/fill, Luthas "[2] Thank you", customs options, apron,
  Wydin's crate, Frank, Blue Moon chest, message -> 3, dig with the Gardener, casket -> 4). The journal lines QH reads
  showed as intended. Count check on the same account: "The vampyre seems to weaken." every 50 ticks, HP regenerating.
- **Wydin's back-room door** let a non-employee in: `entering` compared Chebyshev distances, which tie for the two
  door tiles. Now Manhattan distance to the crate picks the inside tile; a one-tile telejump follows if the walk did
  not land (the employed pass stalled on the door tile once; cause not found).
- **Blue Moon Inn stairs** (`loc.fai_varrock_stairs`, 11796) had no handler; added to `StraightStaircaseScript` with
  the plain stairs' offsets (**guess**, check in game).
- **New characters got the realm's 150x XP** on their first session: `AccountLoadResponseHook.applyConfigTransforms`
  set `xpRate = config.baseXpRate` after `CharacterAccountApplier` forced 1x (only later logins were 1x). Now 1x
  there too (our choice; upstream's realm default is 150x).
- **Login message:** realm 255's `login_message` is now "Welcome to Old School RuneScape." in the database
  (orchestrator, `tools/openrune-sql.sh`); our LoginScript mapping is dropped (back to upstream's code).
- **Android account choice (test tooling):** the LOCAL_AUTH sidecar (`server/.../LocalAuth.kt`) reads the account
  for the next sign-ins from a file inside its container: `docker exec osrs-openrune-http sh -c 'echo qhand1 >
  /tmp/local-auth-user'` (empty the file or delete it for the default). Game tokens are then `lu-<hex name>-<random>`,
  which `ConnectionHandler.tokenLogin` decodes to that local account (created on first login, password
  `SPIKE_TOKEN_PASSWORD`/"android-local"); plain tokens still map to "tester". Names: `[a-z0-9_]{1,12}`. A file rather
  than an HTTP setter (first version, replaced before deploy): port 8080 is published on all interfaces, and inside the
  container every caller comes from the Docker gateway, so a loopback check could not tell them apart. Token length
  with a name is up to 60 characters (**not yet checked** that the app passes it through unchanged).

### Fletching: knife on logs (twentieth server dev)
New module `content/skills/fletching` (+ pack): a knife on logs opens the skill Make-X menu (`openSkillMulti`) with the
arrow shafts and unstrung short/longbows those logs make, then a weak queue cuts one log per action (2 ticks to the
first, 3 between: wiki). Data: `dbtable.fletching_knife` (60024, rows 60950-60968); items from the cache, levels,
XP (stored in tenths) and shaft counts (15 per tier up to 105 for redwood) from the OSRS Wiki (Arrow shaft and the
unstrung bow pages). The cut is one inventory transaction (log out, product in). Anim `seq.human_fletching`.
**Guesses:** the spam line "You carefully cut the wood into ..." and the level message. Not done: javelin shafts,
crossbow stocks, shields, stringing, arrows. For RuneLite's Action Progress (Make-X detection is the same path as
herblore's, which already works). Tests: `KnifeFletchingScriptTest` (3).

### Looting bag, `::pos`/`::heal`, missing mesanims (twentieth server dev)
- **Looting bag** (`content/other/looting-bag` + pack): Open/Close swaps `looting_bag` / `looting_bag_open`; Check opens
  `wilderness_lootingbag` (81) in the side modal with client script 495 (`wilderness_lootingbag_setup`, mode 0 = the
  bag) and transmits `inv.looting_bag` (516), which RuneLite's looting bag plugins read; Deposit (mode 1, Store-1/5/All
  on the inventory items) and using an item on the bag (One/Five/All, title a **guess**) store items while in the
  Wilderness (`isInWildernessBasic`). The cache's inv 516 is client-scoped, so the pack's `looting_bag.toml` makes it
  `Perm` (saved). Only Grand Exchange items are stored: `ItemServerType.tradeable` reads true even for items
  items.toml marks `tradeable = false` (coal bag, chest key), so `stockmarket` stands in (tradeable items that are not
  on the GE are refused: **simplified**). Messages from the wiki where it has them ("You may be surprised to learn that
  bagception is not permitted."), otherwise **guesses**. Not done: auto-looting while open, Settings, emptying into
  the bank, Store-X. Tests: `LootingBagScriptTest` (4).
- **`::pos`** (x z level) and **`::heal`** (restores every stat to its base, run energy to full): test commands the
  old server had; OpenRune ignores unknown commands without a reply, which looked like an account problem on qhand1.
- **Missing mesanims:** the Lumbridge Doomsayer's `mesanim.scared` (in mesanim.rscm, but not in the server's
  mesanim.toml) threw "Error Loading MesAnim" and the player was disconnected; added with the `chatscared` seqs that
  `worried` uses (**guess** that they match). Port Sarim's sleeping guard used `mesanim.sleep`, which exists nowhere;
  now `bored` (**guess**). `MesAnimTypesTest` checks every mesanim name the content uses.
- **Upstream candidates:** (1) the mesanim data fix (both); (2) script exceptions: `PlayerInputProcess` /
  `PlayerMainProcess.tryOrDisconnect` call `forceDisconnect()` for any exception in the player's cycle, so a bug in one
  dialogue logs the player out; cancelling the failed script (close the dialogue, keep the session) would be a design
  change for upstream to discuss; (3) `ItemServerType.tradeable` ignoring items.toml's `tradeable = false` (death drops
  use it too).

### Item config `tradeable` (upstream 71, applied in our tree, twentieth server dev)
`ItemServerCodec.createData` assigned a config entry's `tradeable` to `stockmarket`. Fixed in our tree (orchestrator's
OK): `stockmarket` now follows the cache for every item (the tester's GE offer item, lobster, is still `stockmarket`).
But after the fix and a forced full repack (touch `items.toml`; the incremental `buildCache` does not repack when
only the codec changes), `tradeable` still reads **true** for `tradeable = false` entries (coal bag, chest key): a
second cause somewhere in the TOML decode of `[[item]]` blocks (`decodeRuneScape`, `inherit`), not found yet. The
packed round trip of opcode 30 is fine (`ItemServerCodecTest`). Until then the looting bag keeps its `stockmarket`
check. Next step: an or-cache test that decodes a two-line `[[item]]` block with `tradeable = false` through
`PackServerConfigOSRS`'s mapper.

## Twenty-first server dev (2026-10-18): `tradeable` second cause, looting bag extras

### Item `tradeable`: the second cause was a stale cache (upstream 71, now complete)
The TOML decode was fine. Probe tests ran the real `items.toml` through `decodeRuneScapeBlocks`/`decodeRuneScape`
with the constant provider, and also tried `inherit`, `[item.params]` and multi-block variants. They all gave
`tradeable=false` for coal bag (12019) and looting bag (11941). Reading the packed `.data/cache/SERVER` showed
those two items at `tradeable=true`, and coins at `stockmarket=true`. Coins at `stockmarket=true` can only come from
the *old* codec, so the item archive had never been repacked. `PackServerConfig`'s incremental fingerprint covers
the toml files' contents (`incremental_server`, table `dep_file`, by hash) and the base archive's CRC, but not the
codec. Touching `items.toml` changes nothing because the content hash stays the same. Fix: `PackType.codecVersion`
goes into `extraFingerprints` (items: 2). After one `buildCache`, coal bag and looting bag read `tradeable=false`,
coins `stockmarket=false` and lobster `stockmarket=true` (the tester's GE offer is unaffected). **Proof:**
`LootingBagScriptTest` "the bag and untradeables stay out" (now a `tradeable` check) fails on the old SERVER cache
and passes on the rebuilt one. The test reads the packed cache, so it ran in a copy of `src`, because the
boot-checked outputs in `src` had to stay untouched. The "chest key" in the earlier notes (432) has no
`tradeable = false` entry in `items.toml`, so it reads `tradeable=true` either way.
- **Consumers checked:** death drops (`PlayerDeathDrops.isUntradeable`), the guide-price interface
  (`ocTradable`), and shop selling (`tradeable || stockmarket`: "You can't sell this item.") all just follow the
  data. With the stale cache every `items.toml` untradeable counted as tradeable, and config entries put non-GE
  items on the GE (coins, for example). Both are fixed by the repack.
- The first `buildCache` after this repacks every server config type once (the new fingerprint key). The client's
  six long-varp scripts (778, 779, 5156, 5732, 5733, 9911) get new CRCs after `fix_long_varps.py`, with the same
  content.

### Looting bag extras (twenty-first server dev)
Sources: OSRS Wiki "Looting bag" (Usage: Open makes tradeable pickups in the Wilderness go into the bag; banking:
"clicking on the items or the 'deposit all' function will bank the items"); "Update:Buffs, Deadman and Mythical
Cape Designs" (30 Nov 2017, Settings, plus its screenshot of the dialogue: title "When using items on the bag...",
options "... ask how many to store." / "... always store as many as possible.", default ask); osrs-dumps scripts
`[proc,wilderness_lootingbag_draw]` (deposit mode ops Store-1/5/All/X = ops 1-4, Examine op 9; bag mode Examine op 10),
`[proc,bankside_drawitem]` (op 1 "View" on the bag in the bank) and `[proc,bankside_bag_draw]` (bag panel ops
Deposit-<qty>/1/5/10/<last>/X/All = 1-7, Examine 10) plus `bankside.if3` (`lootingbag_bankall` "Deposit loot"); RuneLite
gamevals (varbit 6068 `lootingbag_useallitems`).
- **Settings** (op 4): that dialogue, stored in `varbit.lootingbag_useallitems`. With it set, using an item on the
  bag stores all of them without asking.
- **Store-X** (deposit op 4): count dialog. Examine works in both modes.
- **Auto-loot:** new API hook `PlayerObjTakeRedirectHook` (api/player `GroundItemHooks.kt`, bound in `PlayerModule`,
  used by `ObjTakePlugin`). `LootingBagTakeHook` (bound in `LootingBagModule`) sends a pickup into the bag when an
  open bag is in the inventory, the item is tradeable and not a bag, and the player is in the Wilderness
  (`isInWildernessBasic`). When the bag cannot take it, the pickup goes to the inventory (**guess**: OSRS behaviour
  with a full bag is not on the wiki). `LOOTINGBAG_IGNORE_FOOD` (varbit 15310, a newer setting) is not done.
- **Bank:** the bank transmits `inv.looting_bag` on open, so the client's bankside "View" panel draws it (the view
  switch itself is client-side, `bank_viewcontainer`). `bankside:lootingbag_items` ops deposit through the bank's
  own `invDeposit` (same quantities as the inventory), and `lootingbag_bankall` uses `depositInventory`.
  **Simplified:** unbankable items stay in the bag (the wiki says they move to the inventory; tradeable no-bank
  items should not exist).
- **Bug found:** clicking "View" on the looting bag (or "Configure" on a rune pouch) in the bank sends bankside op 1,
  which `BankInvScript.sideInvOp` did not handle: it threw "Invalid main inv op" and the player was
  disconnected. Op 1 is now ignored. Upstream candidate.
- Tests: `LootingBagScriptTest` (5; new: an open bag takes tradeable pickups only in the Wilderness). The bank's
  integration tests are not wired to a suite upstream, so the bank paths still need a desktop check.

### Count Draynor: garlic +1 damage (twenty-first server dev)
The wiki's Count Draynor page cites Mod Ash (Twitter, 17 Nov 2020): carrying garlic makes the player hit 1 higher
on the Count, in addition to the stat drain. `VampyreSlayerManor` adds 1 to every hit by the fighting player that
deals damage while garlic is in the inventory (`onModifyNpcHit`). **Simplified:** zero-damage hits stay at 0 (a
miss cannot be told apart from a 0 roll). The wiki also says the garlic effect "will wear off after 30 seconds";
that is not modelled. Returning to the coffin: still no source, not done. Not yet checked in a client.

## Twenty-second server dev (2026-10-18): HeldU dispatch order, coal bag wording, re-pin to the fork

### Item-on-item dispatch order (live 17:28 UTC, 021e487)
The client dev found that using an item on the looting bag did nothing (Android, tester, Settings = ask). Cause:
`HeldUInteractions` tried every script for (selected, target), including the selected item's "used on anything"
default, before the pair the other way round. Firemaking registers such a default on every log (bow lighting),
and herblore one on potions and secondaries; both return silently for anything else. Now: pair scripts
(type, content-type, content) in either order first, then the target's default, then the selected item's
(RuneScript's `[opheldu,<target>]` gets the used obj as `last_useitem`). This also fixes logs used on a knife
(fletching) and logs used on a tinderbox (upstream main too). Test: `LootingBagScriptTest` "an item with its own
used-on-anything script still goes into the bag". Desktop-checked on 021e487: oak logs on the bag in the Wilderness
give the One/Five/All prompt and are stored.
- **"View" missing in the bank** was not a bug: osrs-dumps `[proc,bankside_build]` passes `$int13 = 1` to
  `bankside_drawitem` only when `inv.looting_bag` holds an item, and only then sets op 1 "View". The bag was empty
  because of the bug above. Desktop-checked: View opens "Bank your loot" with the logs; Deposit loot banks them.
- Desktop side note: on the desktop client the bag only offers Use/Destroy/Examine (name "Looting bag (Members)").
  Both sidecars serve world 255 with flags 0x1 (members), and the `desktop` character has `members = t`, so the
  difference is on the client/RSProx side; not resolved.

### Coal bag messages
`CoalBagScript` said "Your coal bag contains 5 of 27 coal." and "You fill the coal bag with 5 coal.". OSRS
wording, from the Coal Bag Plugin Hub plugin's parser and tests (`CoalBag.java`: "The coal bag contains one piece
of coal.", "The coal bag contains 27 pieces of coal.", "The coal bag is empty.", "The coal bag is now empty.",
"The coal bag still contains N pieces of coal.") and tictac7x Charges (`U_CoalBag.java`: fill and check both
answer "The coal bag contains ..."): Check and Fill now print the bag's contents in that form, Empty prints "The
coal bag is now empty." or "The coal bag still contains ..." when the inventory was too full. "Your coal bag is
full." and "You don't have any coal to fill your coal bag with." are unchanged (no source). Upstream main has the
same old text.

### Re-pin to the fork (c2492318d)
`openrune.sh` now clones AlexPerrin/OpenRune-Server at c2492318d (the fork's main, synced to upstream main, which
has our merged 02, 03, 04, 27, 58, 60, 62 plus upstream's own #337-#341). `openrune.patch` was rebased: applied on
9a8b338a8, committed, `git rebase` onto c2492318d, conflicts resolved in favour of main. 308 files became 300.
Dropped or reduced:
- 02 bankers (bankers.toml, al_kharid_npcs.toml), 58 Children of the Sun `MarkedAll` stage, 60 Daddy's Home
  waxwood cap, 27 essence pouch wording: fully upstream (our comments dropped).
- 04 fishing "beside the spot": main uses `isWithinDistance(npc, 1)`; our `isBesideSpot` removed.
- 62 `::item` cache-name fallback: upstream; only our comment differed.
- 55 general store stock: upstream #341 does the same, no diff left.
- Misthalin Mystery Mandy reward: main (#339) checks the reward space in dialogue ("You need another space...")
  and `completeQuest` now returns false on a full inventory; our `RewardSlots` message check was dropped, our
  stage guard stays.
- `completeQuest` returns a Boolean now (#339); our quests ignore the result (they award rewards as before).
- Main's new `ErnestTheChickenInteractionTest` (#339) expects `haunted` = 1 at the hand-in; our 54 sets 2 on the first
  Oddenstein talk (OSRS / Quest Helper), so the test's `assertPending` now expects 2 in our tree. Upstream 54 needs the
  same test change.
- Built clean (`clean :or-cache:buildCache`, fix scripts, installDist), `test` and `integration` pass (integration with
  one worker: two workers in 4 GB were OOM-killed, exit 137), boot-checked as `osrs-openrune:bootcheck23` (Server ready
  in 10.4 s, 20:23 UTC). Also in this bundle: the coal bag wording and `::mapanim <spotanimId> [height] [delay] [dx dz]`
  (test command, `TestCommands`; a world spotanim through `WorldRepository.spotanimMap`; upstream only has `::spot`,
  a player spotanim).

### Migration to the fork (twenty-second server dev)
- openrune.patch frozen at fb10fd3. Decomposed into fork branches; see STATE.md on the fork's `docs` branch.
- Desktop members bug: jav_config param 3 is the desktop client's members-world flag (client.init compares it with
  "true"; found in the injected client). Our sidecar served "false"; fixed in `server/` (1c9feea), deployed.
- completeQuest (#339) returns false when declared item rewards don't fit. Doric's Quest and Sheep Shearer took the
  hand-in items first; on false they now give them back ("You don't have enough room for the reward.", a guess).
  Only a full coin stack can trigger it. In the live tree; the fin branches get it from the PR agent.
- fix/76: OBJ_ADD carries timeUntilPublic/timeUntilDespawn (remaining cycles, capped at 65535), ownership (1 self for
  private objs and the owner's public objs, 2 other player, 0 none) and neverBecomesPublic; ZoneUpdateTransformerObjAddTest.
- Ids: new allocations for pirates-treasure, fletching and treasure-trails in openrune/upstream/id-allocation.json.

