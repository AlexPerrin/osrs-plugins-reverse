# OpenRune spike: can OpenRune-Server replace our test server?

Written 2026-10-16. This is the time-boxed spike recommended by `docs/server-evaluation.md`.

- **Question:** can OpenRune-Server serve the official Android app (build 241.4) in place of our
  hand-written `server/`?
- **OpenRune version:** `re/openrune`, HEAD 9a8b338a8 (2026-10-16).
- **Where it ran:** only in Docker, on host ports our test server does not use:
  - OpenRune game/JS5: port 40255, container `openrune-spike`.
  - HTTP sidecar: port 8081, container `openrune-spike-http`.
- **Not touched:** `osrs-rsps-test` (ports 43594/8080) and its volumes.
- **Time spent:** about half an agent-day, including three device slots.

## Result in one paragraph

**Go, with conditions.** The Android app gets into the game on OpenRune, on a world served from
OpenRune's own repacked cache:
- local (LOCAL_AUTH) token sign-in;
- the mobile gameframe (toplevel 601);
- Lumbridge with its NPCs;
- walking, `::` commands, NPC/loc/obj menus and taking a ground item.

All protocol work carried over unchanged, because OpenRune uses the same RSProt build. The patch on
top of OpenRune is 11 files and about 90 lines. Two findings matter for the port:
- The app **crashes on 13 custom clientscripts** (ids 45500+) that OpenRune adds to the cache. The
  rest of the repacked cache is accepted. Those scripts must be left out of what JS5 serves.
- The app takes its game port from the client binary, not from jav_config. A spike build patches
  the port so that both servers can run side by side.

Not reached on the device:
- a working door open/close;
- the Cook's Assistant dialogue (see step 5).

**Saves persist across relog** (OpenRune's Postgres).

## Steps

### 1. Build against our patched RSProt with ANDROID: PASS

How it was built:
- Our RSProt is the pinned upstream 4ec57fd plus the committed `server/rsprot.patch`. It is
  published to a local Maven repo (`re/openrune/.spike/m2`) as `1.0.0-ALPHA-20261001.99`
  (`tools/openrune/rsprot-publish.sh`, Gradle 8.14 in Docker).
- A composite build was not used: OpenRune is on Gradle 9 and RSProt on 8.14.
- OpenRune's `rsprot` version points at that repo (`settings.gradle.kts`, `libs.versions.toml`).

Changes in OpenRune (`tools/openrune/openrune-spike.patch`, 11 files, +91/-15):

| Change | Where |
|---|---|
| `ANDROID` in `supportedClientTypes` | `api/net/.../NetworkFactory.kt` |
| Info protocols allocated with the session's client type (`loginBlock.header.clientType.toOldSchoolClientType()`), plus a `Player.mobileClient` flag | `NetworkScript.kt`, `engine/game/.../Player.kt` |
| Android branch for zone updates: payload built with `AndroidZoneProts.encode`, sent as `AndroidUpdateZonePartialEnclosed` (shared and per-player enclosed updates) | `api/game-process/.../PlayerZoneUpdateProcessor.kt` (+ `osrs-241-android` dependency) |
| Token login: auth type 7 mapped to one local account ("tester") through the password path | `api/net/.../ConnectionHandler.kt` |
| Mobile gameframe: a toplevel_osm (601) gameframe built from enum 1745, opened for Android sessions | `content/interfaces/gameframe/...` |
| Camera API fix: our RSProt pin (4ec57fd) is newer than the published 20261001, and its CamLookAtV2/CamMoveToV2 became aliases of V3 (needs `heightRelative`) | `api/player-output/.../Camera.kt` |

- Boot log: "Revision: 241 / Supported client types: Desktop, Android", ready in about 9 s.
- Run as uid 1000, because the embedded Postgres refuses root (`tools/openrune/run.sh`).
- Note: the "4 DESKTOP sites" from the evaluation were really 3 code sites plus the zone path,
  which needs its own branch (`ZonePartialEnclosedCacheBuffer` has no Android encoder).

### 2. Cache: PASS once 13 scripts are left out

**Static comparison** (`tools/openrune/cachediff.py`) of OpenRune's JS5 cache
(`.data/cache/LIVE`) against stock OpenRS2 2735:

Identical, byte for byte or by content after recompression:
- maps (5, except 1 group);
- models (7, except 2);
- sprites (8);
- textures and fonts;
- interfaces (3), except one added interface (1099).

Clientscripts (12):
- OpenRune recompiles every script from osrs-dumps.
- 9879 of 9916 changed scripts differ from stock **only in an embedded name string**. The
  instructions are byte-identical.
- 37 are intentional content overrides: logout, quest list, prayer, GE, worldswitcher and others.
- 13 are added (45500-45553: the dev spawn menu).

Configs (2):
- npc, obj and loc are re-encoded: opcodes reordered, defaults written out, server-only string
  params (ids around 65480).
- Custom varps, varbits, params, objs and invs are added with ids up to around 65535.

Animations (0): 726 frame groups differ in content (not investigated, harmless live).

**Live:**
- The app crashed (SIGSEGV in the game thread) about 2 s after start whenever it synced from
  OpenRune's JS5.
- It did not crash when stock 2735 was served on the same port.

**Bisect.** Hybrid caches (`tools/openrune/hybridcache.py`: stock plus selected OpenRune archives
or groups, with re-encoded reference tables) were served by our own image on 40255
(`tools/openrune/js5.sh`):

| Served | Result |
|---|---|
| all OpenRune archives | crash |
| stock + OpenRune configs (2) | ok |
| stock + OpenRune anims/interfaces/maps/models/dbtables (0,3,5,7,21,24) | ok |
| stock + OpenRune scripts (12) | crash |
| stock + the 13 added scripts only | **crash** |
| stock + 18 of the overridden scripts | ok |
| all OpenRune archives, archive 12 = stock + the 37 overrides | **ok, in game** |

**Cause:** the 13 added high-id scripts. The desktop client tolerates them, the Android client
does not. Inferred: Android sizes its script table from the archive and does not expect group ids
far above the stock range.

**Fix for a port:** do not serve the dev-only packs (spawn menu) to Android, or give custom
scripts ids just above the stock maximum. The recompiled stock scripts, the 37 overrides, the
re-encoded configs and the high-id custom varps/params/objs are all accepted.

**Not needed:** pinning stock 2735. OpenRune's repacked cache works once those scripts are
dropped. Stock 2735 alone would break OpenRune's own content (overridden scripts, custom configs).

### 3. Login: PASS

- The patched `GameLoginDecoder` (auth type 7) and the Android CRC decoder come with our RSProt.
- OpenRune's `tokenLogin` was a stub that returned `InvalidLoginPacket`. It now maps the token to
  a fixed local account ("tester", auto-registered, because the dev realm has
  `requireRegistration=false`). Central/Postgres is unchanged.
- Log: "Token login (ENHANCED_ANDROID) mapped to local account 'tester'".

jav_config, slr.ws and the OpenID/game-session API come from a sidecar: our own image
`osrs-rsps-test:241` in a separate container with only HTTP published, `WORLD_ID=255` and
`LOCAL_AUTH=true` (`tools/openrune/http.sh`).

The client build (`tools/openrune/apk.sh`):
- `repack.sh` with `SERVER=10.0.2.2:8081` and OpenRune's RSA modulus (from `.data/game.key`);
- plus a **game port patch** (`tools/openrune/patch_port.py`).

Why the port patch is needed:
- The client does not read the port from jav_config or slr.ws.
- It uses 43594 for a LIVE build type. For other build types (jav_config param 15 ≠ 0) it uses
  40000 + world id.
- The code is at 0x432890 `mov w11, #0xaa4a`, with the default pair (43594, 443) at .rodata file
  offset 0x9f400.
- Patching both to 40255 lets OpenRune run beside our server.

For a real switch, OpenRune would simply take 43594.

### 4. Gameframe: PASS

- OpenRune knew only fixed, stretch, pre-EoC and fullscreen toplevels (a server-only dbtable).
- The spike adds a toplevel_osm (601) gameframe built at load time from enum 1745
  (`mobile_pane_redirect`). The enum's placeholder values (600:65535) are filtered out.
- `openLoginGameframe` picks it for Android sessions.
- Live: the full mobile frame with chatbox, minimap/orbs and side tabs, the same layout as on our
  server. No dbtable/cache change was needed.
- Not checked: gameframe moves (resizable switching) and stone events on 601.

### 5. Live on the device

Done (screenshots in the session scratchpad):

- [x] Title screen, sign-in as Tester, Play Now on world 255.
- [x] In game at Lumbridge (3221,3218), mobile gameframe. Music unlock messages, stats, orbs
  (HP 10, prayer 1, run 100).
- [x] NPCs wander (Hans, man, woman, Cook in the kitchen).
- [x] Walk (tap and "Walk here").
- [x] `::tele 3208 3214` ("Teleported to CoordGrid(0_50_50_8_14)").
- [x] Loc menus: Open Door, Cook Cooking range, Climb-down Trapdoor.
- [x] Ground item menu and Take Pot (the pot disappeared).
- [x] Zone packets are sent through the Android branch. RSProt logs "Encoder not registered for
  AndroidUpdateZonePartialEnclosed" from its *size estimator*. That error is harmless and our
  server logs it too: the estimator only knows the desktop repository.

Third slot:

- [x] **Saves persist.** Killed the app in the castle hallway, relaunched and pressed Play Now: a
  fresh login ("Welcome to OpenRune" at 05:05) at the same tile.
- [ ] **Door.**
  - "Open Door" on the kitchen-to-dining-room door reached the server, which answered "Nothing
    interesting happens". That loc has no door tag in OpenRune's `loc.toml`, so this is a content
    gap, not protocol.
  - The castle's large double doors (tagged `castledoubledoor*`) showed only "Walk here" on
    long-press, so they could not be tested. Not investigated. Their loc configs are byte-identical
    to stock, so the cause is probably the hit-test or the long-press position.
  - Zone loc changes are therefore unverified on OpenRune. The Android zone encoding itself is the
    one already verified on our server.
- [ ] **Cook dialogue / Cook's Assistant start.**
  - The Cook wanders, and two long-press attempts and a tap missed him.
  - OpenRune has no "freeze NPCs" command, and there was no time left in the slot.
  - OpenRune's Cook's Assistant has its own interaction test
    (`content/quest/src/test/.../CooksAssistantInteractionTest.kt`). The dialogue packets
    (chat_left/right, chatmenu) are the ones verified on our server.
  - Still open: the chatmenu clipping fix our server applies (varbit 10670 + script 113, see
    server-handoff.md). OpenRune does not set it, so 3-4-option menus may be clipped on mobile.

Other observations:
- NPC wander and ground-item respawn work. The pot came back after Take.
- `::mypos`, `::tele` and other cheats work, because the dev realm makes the account an admin.

## What we would maintain on top of OpenRune upstream

1. **Our patched RSProt** (`osrs-241-android` module, `rsprot.patch`), published locally under a
   bumped version. It must follow every RSProt bump OpenRune makes, and the API skew already
   showed up once (the Camera V2/V3 aliases).
2. **ANDROID enablement:** `NetworkFactory` client types, info alloc by session client type, the
   `Player.mobileClient` flag.
3. **The zone branch** in `PlayerZoneUpdateProcessor`, which encodes zone prots with the Android
   indices.
4. **Token login** mapped to local accounts (and, if wanted, LOCAL_AUTH identities to Central
   accounts).
5. **The mobile gameframe** (601 from enum 1745), and any content that targets
   `toplevel_osrs_stretch:*` components without the redirect.
6. **Dropping the 13 dev scripts** (45500-45553) from the JS5 cache, or renumbering them; a check
   in the cache build that no client-side group id leaves the stock range.
7. **Ops:**
   - non-root container with embedded Postgres;
   - the jav_config/LOCAL_AUTH HTTP part, either our sidecar or ported into OpenRune;
   - the client port: 43594 for a real switch.

Upstream churn is the main cost: about 100 commits a month, touching api/net and content alike.
Items 2-5 are small, local diffs (about 90 lines today) and should rebase easily. Item 1 is the
expensive one, but we carry it anyway.

## Revised effort for the full port

| Phase | Work | Agent-days | Was |
|---|---|---|---|
| 1 | RSProt + ANDROID + zone branch + token login + gameframe | **done in the spike** (cleanup 0.5) | 1-2 + 1-2 + 1-2 |
| 2 | Cache: exclude dev scripts from JS5 (build flag or id range) | 0.5 | 1-3 |
| 3 | Docker: OpenRune image (non-root, Postgres volume), sidecar or port of Http/LocalAuth, port 43594 | 1 | 0.5-1 |
| 4 | Android gaps found while playing (see live results), mobile-only interface targets | 1-3 | - |
| 5 | Sheep Shearer, Romeo & Juliet on OpenRune's quest framework; our `::` debug commands; plugin-host and Quest Helper re-verification | 2-4 | 2-4 |

Total: **about 5-9 agent-days**, down from 7-13.

## Recommendation

**Go for the port, as a staged switch, not a big bang.**

The spike removed the two risks the evaluation called blocking:
- **Repacked cache:** accepted, once 13 dev scripts are dropped.
- **Mobile gameframe:** a 20-line addition.

The protocol layer carried over unchanged. What remains is ordinary content and integration work,
much of which OpenRune already has and our server does not: data-driven doors, stairs, saves, 14
skills, quest framework, Cook's Assistant.

Suggested next steps:

1. A 1-2 day hardening pass on OpenRune:
   - build-time exclusion of dev scripts from JS5;
   - the chatmenu unclamp (varbit 10670 + script 113) for mobile dialogues;
   - a `::freeze`-style NPC command for device tests;
   - the Docker image, Postgres on a named volume.
2. Re-run the device checklist on OpenRune: a tagged door, the Cook dialogue and Cook's Assistant
   end to end, bank, combat, and the plugin host with Quest Helper.
3. Only then move Quest Helper work over, and keep `server/` running in parallel until (2)
   passes. Port Sheep Shearer and Romeo & Juliet onto OpenRune's quest framework at that point.

**No-go triggers to watch for:**
- more Android-only content crashes from custom cache data;
- mobile interfaces that OpenRune content opens at stretch-only components without a redirect;
- RSProt bumps in OpenRune that our Android module cannot follow quickly.

## Reproduce

```
tools/openrune/rsprot-publish.sh             # our RSProt -> re/openrune/.spike/m2
cd re/openrune && git apply ../../tools/openrune/openrune-spike.patch && cd -
tools/openrune/gradle.sh assemble -x test    # OpenRune build in Docker
python3 tools/openrune/hybridcache.py server/data/cache re/openrune/.data/cache/LIVE \
    re/openrune/.spike/caches/or-fixed "0;1;2;3;5;7;8;13;17;18;19;21;22;24;12:+<37 override ids>"
LIVE_CACHE=or-fixed tools/openrune/run.sh    # OpenRune on host port 40255
tools/openrune/http.sh                       # jav_config/slr.ws/LOCAL_AUTH sidecar on 8081
tools/openrune/apk.sh                        # client build for OpenRune (install needs the device lock)
```

The 37 override ids (archive 12 groups that differ beyond the name string and exist in stock):
0, 212, 234, 427, 446, 747, 778, 779, 970, 1150, 2244, 2490, 2917, 3238, 3532, 3560, 3562, 3656,
3658, 3669, 4128, 4132, 4136, 4561, 4814, 5156, 5732, 5733, 5886, 6268, 6269, 6373, 8807, 9474,
9603, 9739, 9911. The 13 dropped scripts are 45500, 45503, ..., 45530 (every third id) plus 45550 and 45553.
