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
| 1.1 | Custom clientscripts out of the crashing id range | done (renumbered, build check) |
| 1.2 | Mobile chatmenu unclamp (varbit 10670 + script 113) | done, untested on device |
| 1.3 | Test commands (`::freeze`, `::quest`, `::npcsay`, `::options`, `::give`, `::empty`, numeric `::varp`/`::varbit`) | done, untested on device |
| 1.4 | Door content gaps (castle) | kitchen + dining-room double doors tagged; front doors need nothing |
| 1.5 | Docker deploy beside osrs-rsps-test; app build option in `repack.sh` | done |
| 2 | Device checklist | not started (needs device slots) |
| 3 | Sheep Shearer, Romeo & Juliet; make OpenRune the default | waiting for the go-ahead |

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

Open question for the device: the spike showed that the 13 high-id scripts crash the app, but not
whether it is their ids or their content. With dense ids the app should accept them; if it still
crashes, the fallback is to leave the spawn-menu pack out of the cache.

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
  LIVE cache has 9986 clientscript groups, max id 9985 (`check_cache.py`).
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

Not started. The first check of the first slot: the app syncs the cache with the renumbered scripts
and does not crash. If it crashes, leave the 11 spawn-menu scripts out of the cache, keep the 2
quest-list scripts, and test those separately.
