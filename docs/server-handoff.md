# Server work: handoff notes

Status and workflow for whoever continues the test server (`server/`) and the Android protocol
work. The client side (injected library + RuneLite plugin host) is developed in parallel by
another session; see `docs/client-handoff.md`. Protocol findings live in
`docs/android-protocol.md`.

## Where things stand (2026-10-16 evening; container runs the chatmenu fix after 47f6549)

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
  - Collision + pathfinding, melee combat with NPC death/respawn, journal tab (quest list), and
    Cook's Assistant end to end (see Next steps).
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

- **Dialogue works** (`::talk`, continue closes it); closing also runs script 216 (chat_build) so
  the chat history comes back.
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
  `::openbank`, `::ptalk text`, `::options a b c`, `::freeze` (toggle NPC wandering, makes NPCs tappable),
  `::walk x z`, `::combat [lvl]`, `::heal`; in-game name = `LOCAL_DISPLAY_NAME`.
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

## Next steps (priority order, driven by Quest Helper on the client side)

Deployed and verified on device (2026-10-16, ~00:20 UTC, commit 5c5aa00): collision and
pathfinding (a closed door blocks, an opened one lets you through, routes go around walls; Talk-to
the Cook from the hallway walks round through the kitchen's south door), melee combat, chat
history redrawn after dialogues, the journal tab, and the full Cook's Assistant flow.

Done, for reference:
- **Collision** (`Collision.kt`): flags per map square loaded lazily from archive 5 (file 0 terrain,
  settings bit 1 = blocked, bit 2 on level 1 = bridge; file 1 locs: walls by shape/rot, shapes 9-11
  solid by size, 22 if interactType 1). BFS in a 128x128 window (closest reachable tile within 10
  when unreachable). Used by MOVE_GAMECLICK, all OP* interactions, NPC wandering and chasing; doors
  update it. Headless check: jshell in `eclipse-temurin:21` with `build/install/*/lib/*.jar` on the
  class path (`new rsps.Collision(cache, locTypes)`, `findPath`, `flags`). `::walk x z` paths there
  and prints the step count. Not handled: bridges beyond the level shift, NPC size > 1, players.
- **Combat** (World.kt "Combat" section): OPNPC "Attack" -> melee every 4 ticks when orthogonally
  adjacent (no wall between); OSRS hit chance, max hit from strength (+5 with a weapon), attack/HP
  XP; NPC block/attack/death anims (human 422/424/836, chicken 5387/5388/5389); NPC stats from
  config opcodes 74-79; NPC retaliates and chases within 8 tiles of spawn; auto-retaliate when idle;
  NPC death -> hidden (`setInaccessible`) + drops (bones; chicken: raw chicken, feathers) -> respawn
  after 25 ticks; player death -> home, full HP. Varp 1306 = 1 at login (without it the client hides
  "Attack" on NPCs). `::combat [lvl]`, `::heal`. Not done: facing (rev-241 unified "face" block is
  not mapped on Android), equipment bonuses, ranged/magic, combat styles interface.
- **Journal tab**: `side_journal` (629) is empty unless the server opens a panel into 629:43
  (summary 712 at login; the subtab buttons 629:2-25 arrive as IF_BUTTON and open 712 / 399
  questlist / 259 area_task) and sets varbit 8168. Quest list colours follow the quest varps.
  "Completed: 0/0" and the quest point total (x/0) show 0 totals: probably a varc/varp the
  server should set (unverified).
- **Dialogue engine**: `npcSay` / `playerSay` / `choose` with continuation lambdas (`Player.onResume`);
  `resumePause` runs the next step or closes. `cookDialogue` follows the real Cook's Assistant
  (varp 29: 0/1/2; ingredients 1927/1944/1933; quest points + 300 cooking XP). Cook 4626 spawns at
  3209,3215. Add more quests the same way.

Done 2026-10-16 (commits 9c34817, 47f6549, deployed ~21:17 local):
- Journal totals verified on device: summary "Quests Completed: 0/187", quest list "Completed:
  0/187", "Quest Points: 0/347" (varbits qp_max 1782, quests_completed_count 6347,
  quests_total_count 11877, sub/miniquest counts; `Quests.totals` from the quest dbtable).
- RSA key is baked into the image (`COPY data/rsa.properties`, `RSA_FILE=/app/rsa.properties`):
  under Docker Desktop the `./data` bind mount came up as an empty VM directory and the key was
  regenerated on restart. Current key: modulus 8555c56c... (host `server/data/rsa.properties`; the
  old c72642... key is in `rsa.properties.c72642.bak`). Note: `docker cp` reads the host path, use
  `docker exec cat` to see what the container really has.
- Protocol mapping push: S→C 140/151, C→S 90/102, zone 15/15 (see android-protocol.md; enum
  entries tagged "dispatcher case read" / "sender read" / "unverified: reason").
  `AndroidMoreEncoders.kt`, `AndroidMoreDecoders.kt`. rsprot core change: the decoder repository
  keys message classes by opcode (`getMessageClass(opcode)`), since one delegating decoder class
  is bound under many opcodes. New client messages without game logic go to `World.incoming`
  (logged once per class). Decoded live: 10, 13, 21, 37, 67, 98, 100.
- Inventory IF_BUTTONX ops: 2/3/4 = iop1-3, 5 = Use, 6 = iop4, 7 = iop5 (Drop), 10 = Examine
  (bucket of milk has iop4 "Empty", so Drop came as op 7). "Empty" implemented.
- `::quest name|index [state]`: exact name, list index, unique prefix, then unique substring.
- `::zfx mapanim|locanim|sound|proj id ...` sends the new zone prots (untested on device).

Verified on device 2026-10-16 21:24 (deployed):
- **Chatmenu clipping / hit offset fixed**: chatbox:chatmodal 162:568 is 479x96, chatmenu root 219:1
  is 479x122 at y=12, and script 58's mobile layout (gosub 1972: varbit 6352 osm_simulate or
  client op 6519 == 7) puts 4 options at y=25/48/71/94 (23 px), so rows below ~84 are clipped;
  the client dev saw taps on row 1 resume sub=2. Script 113 (chat layout) sizes 162:568 to fill
  its parent when varbit 10670 CHATMODAL_UNCLAMP = 1 (else 479x96). `setChatmodalUnclamp` sets it
  for chatmenu (and back to 0 for chat_left/right and on close), then runs script 113. Test: Talk
  `::options One Two Three Four`: all four rows visible, taps give sub 1/2/3/4, chat history back
  after closing.

Committed, NOT deployed (waiting for an explicit restart OK while the client dev runs the Cook test):
- 0a7b20e journal summary combat level (script 3954 sets varc 52 COMBAT_LEVEL; was -1).
- 25582e5 facing: player face = Android flag 0x40 (desktop payload, flag p1Alt3); NPC face = flag
  0x40 with a raw flag byte. Player faces NPC on attack/auto-retaliate/any NPC op; NPC faces its
  attacker, resets on death or when it gives up the chase.
- Sheep Shearer (varp 179, end 21): Fred the Farmer 732 at 3190,3273, sheep 2786-2788 in the pen
  (Shear needs shears 1735, gives wool 1737), any loc op "Spin" turns all wool into balls 1759
  (Lumbridge castle level 1 wheel), Fred takes balls (varp = 1 + balls), done at 21 (1 QP, 150
  Crafting XP, 60 coins). Fred hands out shears when the quest starts. Loc ops "Climb-up" /
  "Climb-down" move one level in place (no stair destination table yet).
  Test: `::tele 3190 3273`, Talk-to Fred, shear, climb to the castle wheel, spin, hand in.
- Romeo & Juliet (varp 144: 0/10/20/30/40/50/60/100, abridged dialogue; stages checked against Quest
  Helper's RomeoAndJuliet.loadSteps in re/hub/quest-helper.jar): Romeo 5037 at 3211,3422, Juliet
  5035 at 3158,3427 level 1 (QH: stairs 11797 at 3157,3436), Father Lawrence 5038 at 3254,3483, Apothecary 5036 at 3195,3403; message
  755, cadava berries 753 (loc op "Pick-from" on a "Cadava bush"), cadava potion 756.

Open, in priority order:
1. Quest Helper gaps from the client agent (Cook's Assistant end to end).
2. More quests on the dialogue engine (Sheep Shearer, Romeo & Juliet).
3. Remaining S→C unknowns: 20/113/115 (HIDE*OPS / heatmap flags), 65/110 (one string), 143 (p4),
   codecs for REBUILD_REGION_V2 (41), OBJ_CUSTOMISE_SPECIFIC_V2 (132), PROJANIM_SPECIFIC_V4 (76),
   clan bodies (19, 84, 87, 88). C→S: 12, 87, 27, the 11-byte (1/62/76/85) and 15-byte (73/83)
   U/T groups need live captures; EVENT_KEYBOARD delta transform.
4. Facing for combat (map the Android "face" extended-info block for players and NPCs).
5. Combat level shows -1 in the journal summary (some var not set).

Scratch helpers (Python cache reader with reference tables, CS2 disassembler with opcode names,
if3 geometry decoder, script reference scanner) were in the session scratchpad; small to rewrite.
RuneLite gameval VarbitID/VarPlayerID (javap -constants on re/runelite/runelite-api-1.13.1.jar)
names vars quickly (e.g. 10670 CHATMODAL_UNCLAMP).

## Coordination

- Device and restarts (2026-10-16 rule): NO container restart or adb action without an explicit
  per-action "OK restart" / "OK device" from the orchestrator; read the status file in its own
  step right before acting (never batch the read with the write/restart).
- Device status is ALSO kept in `/tmp/osrs-device-status` (both agents write "taking device (...)" /
  "device free, restart ok (...)" there). A server subagent's SendMessage replies go to its parent
  session, so poll that file. Deploy = `docker compose up -d` in its own command (the auto-mode
  classifier refused a combined status-write + restart command).
- The device is shared with the client session (Claude session named `osrs-plugins-reverse-62`; the client agent runs inside it;
  find it with ListAgents): post "taking device" / "device free" via SendMessage, and ask before
  restarting the container (it logs the client out). It answers widget/memory questions well.
- Bookkeeping: keep this file and android-protocol.md current, `scripts/rsprot.sh export`, and
  commit server-owned paths at each working checkpoint. When your context nears compaction, write
  state here, commit, and hand off to a fresh server agent.
- Commit only server-owned paths (`server/`, `tools/patch_server.py`, `tools/patch_local_auth.py`,
  `docs/android-protocol.md`, `docs/server-handoff.md`, `tools/repack.sh` server options), with
  `git -c user.name=alex -c user.email=44126061+AlexPerrin@users.noreply.github.com commit` (no global git identity).
