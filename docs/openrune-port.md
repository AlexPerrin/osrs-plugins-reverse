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
| 3.3 | Device runs with Quest Helper | Sheep Shearer done (spinning wheel open), R&J open |
| 3.4 | Switch preparation | ready (1cf7727), not flipped: see "The switch" |

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
went to the client dev first.

Also found:
- **Two Juliets:** OpenRune spawned the plain `juliet` (5035) and the multinpc
  `juliet_multi_visible` (6268, shown as 5035 by varbit `romjul_juliet_visible`) side by side. OSRS
  has only the multinpc; the plain spawn is removed (7790921).
- **QH outlines** sit on another tile than the NPC model (Romeo, sheep), and for Juliet on the
  player's tile. Every other quest NPC spawns once, so this is not a duplicate. Client-side
  candidate: the coordinate base (OpenRune's build area origin, see `::mypos`).

### 3.4 The switch (prepared, not flipped)

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

1. Client dev, on OpenRune:
   - Quest Helper's Cook's Assistant overlay steps;
   - getWeight after `::give 1127` and wearing it (UPDATE_RUNWEIGHT);
   - optionally, the root cause of the custom-clientscript crash.
2. Phase 3, only after the user's go-ahead:
   - Sheep Shearer and Romeo & Juliet on OpenRune's quest framework (`content/quest/.../area`;
     `CooksAssistant.kt` is the model). Check what OpenRune already has first.
   - R&J notes from the orchestrator: our server has no quest-complete scroll, only a chat line.
     OpenRune's `Quest.completeQuest` opens one. The Apothecary should offer "Talk about Romeo &
     Juliet" before the potion dialogue.
   - Content reference, read-only: https://github.com/RSPSApp/tsps (clone to re/tsps, never run it,
     check its license before copying).
3. The switch:
   - OpenRune on 43594 (compose port), with the app built without `GAME_PORT`;
   - either keep the sidecar on 8080 or port Http/LocalAuth into OpenRune;
   - retire `osrs-rsps-test` only when the orchestrator says so.
4. Housekeeping: the spike containers `openrune-spike` and `openrune-spike-http` (stopped) and
   `re/openrune` are superseded by `openrune/` and can be removed when the orchestrator agrees.
