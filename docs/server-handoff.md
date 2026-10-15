# Server work: handoff notes

Status and workflow for whoever continues the test server (`server/`) and the Android protocol
work. The client side (injected library + RuneLite plugin host) is developed in parallel by
another session; see `docs/client-handoff.md`. Protocol findings live in
`docs/android-protocol.md`.

## Where things stand (2026-10-15, evening)

- A rev-241 test server runs in Docker (`osrs-rsps-test`): JS5 cache (OpenRS2 cache 2735),
  `jav_config.ws` / `slr.ws`, login, a small game world, reconnect (60 s grace), and a local
  OpenID sign-in, so no Jagex account is needed.
- The official Android app (build 241.4, `ENHANCED_ANDROID`) works against it:
  - Lumbridge gameframe, appearance, stats and run energy.
  - Walking, public and overhead chat, `::` commands, logout.
  - Inventory with the starter kit; item options by name (Wield/Wear/Equip, Eat/Drink, Drop).
  - Equipment tab with unequip.
  - Four wandering courtyard NPCs (Hans, man, woman, chicken) with say, animation and transform.
  - SERVER_TICK_END every tick, which drives GameTick in the plugin host.
  - OPNPC/OPLOC/OPOBJ 1-5 decoded (layouts from the client's senders). The player walks next
    to the target (straight line), then: Talk-to opens an NPC chat, Bank opens the bank, Open on
    a door removes it (zone LOC_DEL), Take picks up an owned ground obj; other ops print the
    NPC/loc/obj name and op. Verified live: OPLOC1 (door 1535) and OPLOC6 (examine).
  - Zone packets: UPDATE_ZONE_PARTIAL_ENCLOSED with LOC_ADD_CHANGE_V2 / LOC_DEL / OBJ_ADD /
    OBJ_DEL, plus top-level LOC_ADD_CHANGE_V2 / LOC_DEL (verified: tree added/removed, zone obj
    shown and taken).
  - Bank (bankmain 12 in main modal 601:27, bankside 15 in side modal 601:114, inv 95) with
    withdraw (op 1 = one, else all) and deposit; verified. Open with `::openbank` (the client
    swallows `::bank` and says a joke line in public chat instead).
  - Dialogues: NPC (chat_left 231), player (chat_right 217, IF_SETPLAYERHEAD), options
    (chatmenu 219 via script 58; resumes as 219:1 sub = option). Only two option rows showed
    for three options (scroll? unverified).
  - NPC and loc configs decoded for names/ops (`EntityType`); rev-241 loc configs use opcodes 6/7
    for int model ids.
- The Android protocol differs from desktop: opcodes are permuted and field layouts differ per
  packet. It is implemented as a new `osrs-241-android` module inside a vendored RSProt
  (`server/rsprot`, gitignored), kept as `server/rsprot.patch` against upstream
  `4ec57fd7a28e16e9ae207bd1f02b7f343bb77957`. Upstream has no Android support and nothing public
  maps the Android protocol.

## Layout

| Path | What |
|---|---|
| `server/src/main/kotlin/rsps/Main.kt` | Boot: cache, JS5 store, huffman, gameframe enums, obj types, RSProt network factory, message listeners |
| `server/src/main/kotlin/rsps/World.kt` | Game loop (600 ms), login/reconnect, players, NPCs, movement, inventory/equipment, commands |
| `server/src/main/kotlin/rsps/Cache.kt` | dat2/idx reader, containers, reference tables, JS5 responses + master index |
| `server/src/main/kotlin/rsps/GameData.kt` | Enum decoder, gameframe (toplevel 601 mobile via enum 1745), obj names/iops/wearpos |
| `server/src/main/kotlin/rsps/Http.kt` | Raw-socket HTTP: jav_config (from `protocol/jav_config.ws.template`), slr.ws, /rsa |
| `server/src/main/kotlin/rsps/LocalAuth.kt` | Local OpenID provider + game-session API (`LOCAL_AUTH=true`) |
| `server/src/main/kotlin/rsps/Config.kt` | Env config; RSA key in `server/data/rsa.properties` (gitignored) |
| `server/rsprot.patch`, `server/scripts/rsprot.sh` | `init` clones the pinned RSProt and applies the patch; `export` writes working-copy changes back |
| `rsprot/protocol/osrs-241/osrs-241-android/` (inside `server/rsprot`) | Android prots, encoders, decoders, player/NPC extended-info writers |
| `tools/patch_server.py` | Patches the game .so: RSA modulus + config/world-list URLs (56-char, contain "jagex.com") |
| `tools/patch_local_auth.py` | Patches base APK smali/res for local sign-in (cleartext, AppAuth HTTPS checks) |
| `tools/repack.sh` | Shared with the client session; `SERVER=` / `LOCAL_AUTH=1` are the server-side options |
| `native/src/net_trace.*` | Socket/DNS logging hook (`setprop debug.osrsrl.nettrace 1`) |

## Build, deploy, test

```
cd server
scripts/rsprot.sh init               # once, recreates server/rsprot from the patch
./gradlew-docker installDist         # compile check (Gradle in Docker, cache volume rsps-gradle-cache)
docker compose up -d --build         # deploy (~1-2 min); logs: docker logs -f osrs-rsps-test
scripts/rsprot.sh export             # after editing server/rsprot: save to rsprot.patch before committing
python3 tools/probe.py 127.0.0.1 43594   # headless JS5 check (all archive CRCs)
LOCAL_AUTH=1 SERVER=10.0.2.2:8080 ADB=/mnt/c/Users/Alex/AppData/Local/Android/Sdk/platform-tools/adb.exe \
    ANDROID_SERIAL=127.0.0.1:5555 tools/repack.sh --install     # client (run bare, from repo root)
```

In-app: "Sign in with Email / Username", close the privacy screen (X top right), then Play Now
("Tester"). After a container restart, tap "Try again" first, then Play Now. Look at a
screenshot before tapping blind: on another screen the same spot is "Sign out".

## How to identify Android packets

- **Server→client:** each opcode is a `case` in the dispatcher (Ghidra `0x541dd4`, decompiled to
  `re/decomp/00541dd4.c`; the scratch helper split it by case). Match the reads against
  RSProt's desktop encoder for the same message, then write an Android encoder using the
  transform table in `docs/android-protocol.md`. Sizes come from the table at .rodata `0x104c4c`.
- **Client→server:** all 102 opcodes are framed from the client's size table. Unknown ones arrive
  as `AndroidUnknownPacket` and are logged (`LOG_ANDROID_PACKETS=true`). Perform the action in
  game and read the bytes. Many matched desktop byte for byte (delegate to the desktop decoder).
- Extended info: player (`0x669a04`) and NPC (`0x569778`) readers list flag tests in read order.

## Open work

- **Dialogue works** (`::talk`, continue closes it); chat lines from before the dialogue stay
  hidden afterwards (new lines show). Cosmetic.
- **Ground items** (`::obj`, `::zobj`, drop, Take) are shared zone OBJ_ADD/OBJ_DEL, resent after
  every rebuild. (OBJ_ADD_SPECIFIC, no longer used, needs timeUntilDespawn > 0.)
- `server/tools/device.sh login|cmd|shot`: BlueStacks helpers (login after restart, chat commands
  with the keyboard/Enter workaround, screenshots).
- Relogin with the same name replaces the old session (fixes a ghost duplicate player).
- Component ids come from rev-239 symbols and can be off in rev 241 (chatmodal 566 -> 568):
  verify against the cache (scratch tool: decode interface components, parent chain).
- Quest/var commands: `::quest name [start|done|reset|n]` (name substring; adb `input text`
  breaks on apostrophes, so use e.g. `::quest assistant done`), `::varp id [v]`, `::varbit id [v]`,
  `::hit [n] [type]`, `::npchit [n]`, `::events [e1] [e2]` (resend inventory IF_SETEVENTS).
- Commands: `::xp skill n`, `::talk [npc] text`, `::obj id [n] [dz]` (OBJ_ADD_SPECIFIC on your tile),
  `::zobj id [n]` (zone OBJ_ADD), `::locadd id [shape] [rot]`, `::locdel [shape] [dx] [dz]`,
  `::openbank`, `::ptalk text`, `::options a b c`, `::freeze` (toggle NPC wandering, makes NPCs tappable); in-game name = `LOCAL_DISPLAY_NAME`.
- Tapping on the device: a plain tap often walks; long-press (`input swipe x y x y 1000`) opens the
  minimenu. The default camera is far out and NPC overlay labels are offset from the models.
- Component ids: RuneLite's `runelite-api-1.13.1.jar` in `re/runelite` has gameval `InterfaceID`
  constants that match rev 241 (chatbox:chatmodal = 162:568); `javap -constants` on the nested class.
- Docker Desktop may not be running after a Windows restart: start it with
  `cmd.exe /c start "" "C:\Program Files\Docker\Docker\Docker Desktop.exe"`; the container restarts by itself.
- adb input: `input text` always works, but keyevents (Enter) only while the soft keyboard is
  shown (`dumpsys input_method | grep mInputShown`; tap 90,220 to toggle it).
- Unmapped: most NPC/player extended-info blocks (facing, spotanims, ...), WORLDENTITY_INFO,
  zone packets (loc/obj updates), interface text/events beyond IF_SETEVENTS, the CRC order in the
  Android login block (CRC validation is off).
- Client→server still unknown: 10 (window status?), 13, 18, 21 (mouse click), 37 (keyboard), 67, 97, 98, 100.
- NPC extended info `0x80` is spotanims (count `-b`, 8 bytes each, `0x5714e8`). NPC hits are
  0x40000 and headbars 0x1000000; player hits 0x40000, headbars 0x20000 (see android-protocol.md).
- The OutgoingMessageSizeEstimator error "Encoder not registered for AndroidUpdateZonePartialEnclosed"
  is harmless (the estimator only knows the desktop repository); the packet is still sent.
- Collision/pathfinding from map data (see Next steps 2).

## Next steps (priority order, driven by Quest Helper on the client side)

Done and verified on device (2026-10-15, late): shared ground objs survive a rebuild (tele 82
tiles away and back), coins stack (175 in one slot), use item on loc/NPC/obj (OPLOCT/OPNPCT/OPOBJT
field orders correct; the inventory needs events1 target bits 11-16 for "Use"), quest vars
(`::quest`; the client agent's runScript read 4029 = FINISHED / IN_PROGRESS), player hitsplats
and headbars (`::hit`).

0. **Deploy state:** the container runs 3b565e0 (doors, NPC hits). Collision (5ffe15e) is built but
   not deployed: ask the client agent "restart ok?", redeploy, then check on device that walking
   routes around the courtyard walls/fountain, that a closed door blocks and an opened one lets you
   through, and that NPCs no longer wander into walls.
1. Done and verified too: real doors (Open/Close round trip on Lumbridge door 1535 at 3226,3223
   rot 2 <-> 1536 at 3227,3223 rot 3; `Maps.loadDoors`, map archive 5 group `mx << 8 | mz`, file 1,
   no XTEA) and NPC hitsplats/headbars (`::npchit`). One map square fails to decode (logged).
2. Collision + pathfinding (`Collision.kt`, committed 5ffe15e, checked headless with jshell, not yet
   on device): flags per map square loaded lazily from archive 5 (file 0 terrain: u16 opcodes,
   settings bit 1 = blocked, bit 2 on level 1 = bridge; file 1 locs: walls by shape/rot, shapes
   9-11 solid by size, 22 if interactType 1); loc `interactType` from config opcodes 17/27. BFS in a
   128x128 window (closest reachable tile within 10 when the target is unreachable). Used by
   MOVE_GAMECLICK, all OP* interactions (goal = the interaction's `reached` test), NPC wandering;
   doors update collision. The Collision keeps its own open `Cache` (game thread only).
   Headless check: `jshell --class-path <build/install/*/lib/*.jar>` with `new rsps.Collision(...)`.
   Not handled: multi-level bridges beyond the level shift, NPC size > 1, other players.
3. Combat basics (attack NPCs with hitsplats, NPC death/respawn) if Quest Helper work needs it.
4. Cosmetic: chat history hidden after a dialogue; options menu shows only 2 of 3 rows.

Scratch Python helpers (cache reader, CS2 disassembler, quest/map decoders) were used for the
static work; equivalents live in `Quests.kt` / `Maps.kt`.

## Coordination

- The device is shared with the client session (Claude session named `osrs-plugins-reverse-62`; the client agent runs inside it;
  find it with ListAgents): post "taking device" / "device free" via SendMessage, and ask before
  restarting the container (it logs the client out). It answers widget/memory questions well.
- Bookkeeping: keep this file and android-protocol.md current, `scripts/rsprot.sh export`, and
  commit server-owned paths at each working checkpoint. When your context nears compaction, write
  state here, commit, and hand off to a fresh server agent.
- Commit only server-owned paths (`server/`, `tools/patch_server.py`, `tools/patch_local_auth.py`,
  `docs/android-protocol.md`, `docs/server-handoff.md`, `tools/repack.sh` server options), with
  `git -c user.name=alex -c user.email=alexperrin7@gmail.com commit` (no global git identity).
