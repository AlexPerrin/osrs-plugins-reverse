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
- **Custom clientscripts left out:** the Android client crashes (SIGSEGV in the game thread, about
  2 s after start) on OpenRune's own clientscripts. Their content is the cause, not their ids: it
  still crashed with them renumbered densely after the stock maximum (9972). The cache build leaves
  out the clientscripts of the `spawn` (dev spawn menu) and `quest` (quest-list filter) packs
  (`OPENRUNE_SKIP_CS2_PACKS`, default `spawn,quest`). The scripts keep the dense ids 9973-9985 in
  case they come back. `PluginPacks` fails the build on custom ids above 9999, and
  `scripts/check_cache.py` checks the packed cache.
- **Mobile chatmenu:** chatbox:chatmodal is unclamped (varbit 10670 + script 113) for chatmenu only,
  and reset for chat_left/chat_right and on close, as on our server. Without it the 3rd and 4th
  options were clipped on mobile.
- **Test commands:** `::freeze` (NPC wandering on/off), `::npcsay text`, `::options a b c [d] [e]`
  (a 2-5 option chatmenu), `::quest name [start|done|reset|n]`, `::give` and `::empty` (aliases of
  `::item` and `::invclear`), and numeric ids for `::varp`/`::varbit`/`::getvarp`/`::getvarbit`.
  OpenRune already has `::tele x z [level]`, `::item id|name [count]`, `::npcanim id` (nearest NPC),
  `::mypos`, `::openbank`, `::master` and more.
- **Lumbridge castle doors:** the kitchen door and the hallway double doors are their Recipe for
  Disaster variants in the map, which had no door tag
  (`.data/raw-cache/server/loc/misthalin/lumbridge-castle.toml`).

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
`openrune.patch`. After a change to `server/rsprot.patch`, run `openrune/scripts/openrune.sh rsprot`.

## Run

```
cd openrune && docker compose up -d --build
```

| Container | Host port | What |
|---|---|---|
| `osrs-openrune` | 40255 | OpenRune: login, JS5, game (43594 inside) |
| `osrs-openrune-http` | 8081 | jav_config.ws, slr.ws and the LOCAL_AUTH sign-in, from our own server image |

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

## License

OpenRune-Server is under the ISC license, "Copyright (c) 2025 RS Mod" (`src/LICENSE.md`).
`openrune.patch` is a derivative of it and keeps that notice; see `LICENSE.OpenRune`.
