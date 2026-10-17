# OpenRune-Server for the Android app

Our build of [OpenRune-Server](https://github.com/OpenRune/OpenRune-Server) (rev 241). It is the
planned replacement for our hand-written test server in `server/`, and it is being ported in stages.
Background: `docs/openrune-spike.md` (spike), `docs/openrune-port.md` (port status and device
checklist).

The upstream source is not committed. This directory holds:

| Path | What |
|---|---|
| `scripts/openrune.sh` | The pinned upstream commit, and `init`, `export`, `rsprot`, `build`, `gradle`, `modulus` |
| `openrune.patch` | Our changes on top of the pin (exported from `src/`) |
| `scripts/check_cache.py` | Build check: no clientscript ids the Android client crashes on |
| `Dockerfile`, `docker-compose.yml`, `docker/` | Runtime image and deployment |
| `src/` (git-ignored) | The working clone: pinned upstream plus `openrune.patch` |
| `rsprot/` (git-ignored) | Our RSProt: the pin in `server/scripts/rsprot.sh` plus `server/rsprot.patch` |

Everything that compiles or runs OpenRune runs in Docker (Gradle as uid 1000 in
`eclipse-temurin:21`, Gradle caches in the named volumes `openrune-gradle` and `openrune-gradle8`).

## What the patch changes

- **Android client support:** `ANDROID` in the supported client types, info protocols allocated with
  the session's client type, `Player.mobileClient`, and zone updates encoded with the Android prot
  indices (`PlayerZoneUpdateProcessor`). Depends on our RSProt (`osrs-241-android` module),
  published as `1.0.0-ALPHA-20261001.99` into `src/.m2-rsprot`.
- **Token login:** auth type 7 (the app's Jagex-account token) maps to one local account, "tester".
- **Mobile gameframe:** toplevel_osm (601), built from enum 1745, opened for Android sessions.
- **Camera API:** follows our newer RSProt pin (CamLookAtV2/CamMoveToV2 aliases).
- **Custom clientscripts:** OpenRune's packer gives the clientscript groups it *adds* zero sizes
  and a zero name hash in archive 12's reference table, and the Android client crashes on that
  during cache sync (SIGSEGV in the game thread). `openrune.sh build` therefore runs
  `scripts/fix_reftable.py` (real sizes, plus the name hash of the embedded script name) and
  `scripts/add_varcs.py` (varclient configs for the quest pack's varcstr 1293/1294) after the cache
  build. Verified on the device 2026-10-16. The quest pack's scripts (9973/9974 and the
  `questlist_hide_quest`/`quest_is_complete` overrides) are in again; only the dev spawn menu stays
  out (`OPENRUNE_SKIP_CS2_PACKS`, default `spawn`). `PluginPacks` fails the build on custom ids
  above 9999, and `scripts/check_cache.py` checks the packed cache.
- **Mobile chatmenu:** chatbox:chatmodal is unclamped (varbit 10670 + script 113) for chatmenu only,
  and reset for chat_left/chat_right and on close, as on our server. Without it the 3rd and 4th
  options were clipped on mobile.
- **Test commands:** `::freeze` (NPC wandering on/off), `::npcsay text`, `::options a b c [d] [e]`
  (a 2-5 option chatmenu), `::trace` (log every incoming game packet, consumed or not, as `IN op=..
  len=.. Class hexbytes` in `docker logs osrs-openrune`; RSProt's `IncomingPacketTrace`), `::quest name [start|done|reset|n]`, `::give` and `::empty` (aliases of
  `::item` and `::invclear`), and numeric ids for `::varp`/`::varbit`/`::getvarp`/`::getvarbit`.
  OpenRune already has `::tele x z [level]`, `::item id|name [count]`, `::npcanim id` (nearest NPC),
  `::mypos`, `::openbank`, `::master` and more.
- **Quests:** Sheep Shearer and Romeo & Juliet (`content/quest/.../lumbridge/SheepShearer.kt`,
  `.../varrock/romeoandjuliet/RomeoAndJuliet.kt`) on varps 179/144 with the OSRS stages Quest
  Helper reads, plus Juliet's house stairs and one Juliet spawn instead of two.
- **Shared login key:** `RsaProvider` uses `.data/rsa.properties` (our server's key format) when it
  exists; see "Switch" below.
- **Database:** transactions on the shared JDBC connection are serialized (a login and a save could
  interleave and fail the save).
- **Lumbridge castle doors:** the kitchen door and the hallway double doors are their Recipe for
  Disaster variants in the map, which had no door tag
  (`.data/raw-cache/server/loc/misthalin/lumbridge-castle.toml`).

- **RuneLite-visible messages:** item examines go out as `ObjExamine` (27) game messages, not via
  the `examine_item` clientscript's `mes` (type 0), so the Examine plugin matches them (it adds the
  price line itself). Skilling texts match what RuneLite's plugins parse: "You swing your pick at
  the rock.", "You catch some shrimps.", and cooking/smelting lines as SPAM.
- **Fishing spots:** the Lumbridge river Lure/Bait spot (`0_50_50_freshfish`, npc 1527) and the
  `0_50_49_saltfish` Net/Bait spot had no content group, so their ops did nothing.

- **Dwarf multicannon** (`content/other/dwarf-multicannon`): set-up from the base (4 stages,
  "You add the furnace."), Fire/Load X/use cannonballs (30 max), Empty, Pick-up, a cannon rotating
  through 8 octants that fires at attackable NPCs within 8 tiles (projectile, ranged accuracy roll,
  0-30 damage, 2 Ranged xp per damage), one cannon per player, no set-up at the Grand Exchange. It
  sets the varps RuneLite's Cannon plugin reads: `dropcannon` (2, stage, 4 = built), `rockthrower`
  (3, ammo), `ownedmcannon` (4, packed coord of the SW corner) and varbit `mcannon_decayed` (13672).
  Deviations: after a logout or 25 minutes (decay) the parts and balls go to the bank instead of
  Nulodion's reclaim; only steel cannonballs.
- **Members and idle:** every account logs in as a member (members ops such as the cannon's
  Set-up show; the sidecar's world flags already mark world 255 as members). The client's IDLE
  packet (AFK timeout) logs the player out like the logout button, so combat still blocks it.
- **Implings** (`content/skills/hunter`): Catch on every impling, in Puro-Puro (the map's 51
  fixed spawns, `::tele 2592 4320`) and in Gielinor. A wielded butterfly net or magic net, or 10
  extra Hunter levels barehanded; an empty impling jar is required (the jar-less loot of Gielinor
  catches is not done). Chance 12-98% by level, +8 with a magic net or barehanded; XP, jar and
  respawn per type from the new server-only dbtable `impling_types` (OSRS Wiki values). Wording of
  the catch message is a best guess. Loot on a caught jar rolls the OSRS Wiki's loot table for that jar (main table,
  gourmet's grubby-key pre-roll, clue scroll tertiaries; noted where the wiki says so), and the empty
  jar comes back 9 times in 10. The lucky impling (clue reward rolls) has no loot.
  Crystal implings: one at a time in Prifddinas (12 wiki spots, 17 named implings), a new one 3
  minutes after every :00/:30 UTC and one at server start; Hunter 80, 280 XP (generic catch curve).
- **Random events** (`content/events/random-events`): the Genie, every 1-2 hours of play or with
  `::randomevent`. He appears beside the player, faces them, calls them by name every 12 s,
  follows, and leaves after a minute; Talk-to gives the lamp (Rub: xpreward, 10 x level), Dismiss
  sends him off. That is what RuneLite's Random Events plugin keys on.
- **Small fixes:** fishing stops when the player is no longer beside the spot (teleport); the login
  message is "Welcome to Old School RuneScape." (WELCOME), which RuneLite's Chat History waits for.

## Build

```
openrune/scripts/openrune.sh init    # clone the pin into openrune/src and apply openrune.patch
openrune/scripts/openrune.sh build   # RSProt (first time), cache, RSA key, installDist, cache check
```

The first build downloads the rev-241 cache from OpenRS2 and packs it (`gradlew install`, several
minutes). Later builds repack incrementally (`buildCache`). An incremental build never removes
groups from the packed cache: after removing or renumbering cache content, delete
`src/.data/cache` and build again.

To change OpenRune: edit `src/`, build, test, then `openrune/scripts/openrune.sh export` and commit
`openrune.patch`. After adding a module, run one Gradle command with `--no-configuration-cache`
(e.g. `openrune.sh gradle --no-configuration-cache :or-cache:buildCache`): a reused configuration
cache does not see new projects. Integration tests: `openrune.sh gradle --no-configuration-cache
:content:skills:hunter:integration`. After a change to `server/rsprot.patch`, run `openrune/scripts/openrune.sh rsprot`.

## Run

```
cd openrune && docker compose up -d --build
```

| Container | Host port | What |
|---|---|---|
| `osrs-openrune` | 40255 | OpenRune: login, JS5, game (43594 inside) |
| `osrs-openrune-http` | 8081 | jav_config.ws, slr.ws and the LOCAL_AUTH sign-in, from our own server image |
| `osrs-openrune-http-desktop` | 8082 | the same for desktop RuneLite through RSProx (world host 127.0.0.1); see `docs/multi-client.md` |

- Saves (embedded PostgreSQL) are in the named volume `osrs-openrune_openrune-pgdata`; it survives
  rebuilds.
- The login RSA key (`src/.data/game.key`) is baked into the image. A new key breaks every app build
  made for the old one.
- The HTTP sidecar is our `osrs-rsps-test:241` image (build it in `server/`) with only its HTTP
  port published; it mounts `server/data` read-only.
- Both run beside our current server `osrs-rsps-test` (43594/8080), which this setup never touches.
- Logs: `docker logs osrs-openrune`. Ready line: "OpenRune Server Successfully initialized".

## App build for OpenRune

```
OPENRUNE=1 LOCAL_AUTH=1 tools/repack.sh --install
```

This is `SERVER=10.0.2.2:8081`, `GAME_PORT=40255` and the modulus of `src/.data/game.key`. The app
reads its game port from the client binary, not from jav_config, so `tools/openrune/patch_port.py`
patches it (43594 -> 40255). Sign in with "Sign in with Email/Username" (LOCAL_AUTH), then Play Now.

To go back to our current server: `LOCAL_AUTH=1 SERVER=10.0.2.2:8080 tools/repack.sh --install`.

## Switch (OpenRune as the default server)

Prepared, not done; the orchestrator flips it with the user's OK. See `docs/openrune-port.md` 3.4.

```
scripts/openrune.sh shared-key                     # bake server/data/rsa.properties into the image
docker stop osrs-rsps-test                         # kept as a stopped fallback
docker compose --env-file switch.env up -d --build # 43594 (game) and 8080 (jav_config/LOCAL_AUTH)
```

Afterwards the plain app build (`LOCAL_AUTH=1 SERVER=10.0.2.2:8080 tools/repack.sh --install`)
connects to OpenRune. Going back: `docker compose down`, then `docker start osrs-rsps-test`.

## License

OpenRune-Server is under the ISC license, "Copyright (c) 2025 RS Mod" (`src/LICENSE.md`).
`openrune.patch` is a derivative of it and keeps that notice; see `LICENSE.OpenRune`.
