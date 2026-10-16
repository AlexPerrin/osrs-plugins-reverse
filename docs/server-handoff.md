# Server work: handoff notes

Status and workflow for whoever continues the test server (`server/`) and the Android protocol
work. The client side (injected library + RuneLite plugin host) is developed in parallel by
another session; see `docs/client-handoff.md`. Protocol findings live in
`docs/android-protocol.md`.

## Migration to OpenRune (2026-10-16, prepared)

The test server is moving to OpenRune-Server (`openrune/`, `docs/openrune-port.md`). Cook's
Assistant, Sheep Shearer and Romeo & Juliet, the test commands and the Android protocol (through
this repo's `rsprot.patch`) all run there.
- **Until the switch:** `server/` (`osrs-rsps-test`, 43594/8080) stays the default and is not
  changed.
- **After the switch:** `osrs-rsps-test` is stopped but kept as a fallback (`docker start
  osrs-rsps-test` after `docker compose down` in `openrune/`), and its `rsps-saves` volume is left
  alone. OpenRune serves 43594, and its sidecar (this server's image, HTTP only) serves
  jav_config/LOCAL_AUTH on 8080.
- **Login key:** the shared key (`server/data/rsa.properties`) keeps existing app builds working.
- **Where work goes:** new content goes into `openrune/src` (exported to `openrune/openrune.patch`).
  Protocol work stays in `server/rsprot.patch`, which both servers build from
  (`openrune/scripts/openrune.sh rsprot`).

## Latest state (2026-10-16 night, fifth server dev, on OpenRune)

- **Deployed** the pending build (MIDI_SWAP encoder, decoders 8/20/28/50/57/84) at 18:54 as image
  aec982d; sign-in and Play Now verified. Later work (implings, Genie) is built and tested but NOT
  deployed: the next deploy is `cd openrune && docker compose --env-file switch.env up -d --build
  --no-deps openrune` (needs "OK restart"). A throwaway container of that build booted cleanly.
- **Devtools works for server checks**: `tools/devtools_mcp.py --call tools/call
  '{"name":"type_chat","arguments":{"text":"::tele 2592 4320"}}'`, `list_npcs`, `get_client_state`
  (setup in docs/plugin-host.md). OpenRune has no incoming-packet trace, so C->S packets can't be
  watched live without `::trace` (added after the slot: logs every incoming packet,
  consumed or not, in `docker logs osrs-openrune`).
- **URL_OPEN 24**: the third live `::url` crashed the app (with our hooks; the stock client just
  drops the session). Cause (dca5be0): the reader `0x5da16c` ISAAC-decrypts opcode 0x18's payload,
  and our `AndroidDelegatingEncoder` dropped the desktop encoder's `encryptedPayload` flag. Fixed;
  verify by `::url https://example.com` -> "opening a link to an external site" dialog, tap CANCEL.
- **Device-verified in slot 2 (19:25)**: the Genie (spawn/face/name, Talk-to lamp, Dismiss), impling
  Catch in Puro-Puro, and `::trace` showing live C->S 8 (music slider) and 46 (jingle end).
- **45 SET_HEADING**: probably sailing (minimap heading), not testable on either server.
- **Content**: Implings (catching; Puro-Puro spawns already existed) and the Genie random event
  (`::randomevent`), see `openrune/README.md` and `docs/openrune-port.md`. Upstreamable fixes are
  listed in `docs/openrune-port.md`.
- Remaining unverified: S->C 0, 24, 148; C->S 45, 69 (unchanged).

## Latest state (2026-10-16 evening, fourth server dev, on OpenRune)

The switch is done: the default server is OpenRune (`openrune/`, osrs-openrune on 43594, sidecar on
8080). Protocol work stays in `server/rsprot.patch`. Metric at the end of the session: mapped
241/253, verified 226/253, implemented 226/253.

Done this session:
- **Clan/group bodies verified** (f993c6a): the client's own parsers for 19/84/87/88 and
  134/138/140/141 were compared field by field with RSProt's desktop encoders, and all match
  (details in `docs/android-protocol.md`). The four clan encoders are bound.
- **NPC "invisible spawn"** is not a protocol bug. `::npc` spawns on the player's tile, and the NPC
  hides inside the player model. On a long-press it is already in the menu. NPC info tests:
  `NpcInfoViewEdgeTest` (RSProt desktop tests). The spawn-clock A/B was reverted.
- **Quest-pack CS2 crash fixed** (c391951): OpenRune's packer gave added clientscript groups zero
  sizes in the reference table. `openrune.sh build` runs `scripts/fix_reftable.py` and
  `add_varcs.py`, and only the spawn pack's scripts stay out. Verified on the device. The quest
  tab filter itself was not looked at yet.
- **Bankers** (00127f7): every Banker type gets `content.banker`, so Bank/Collect/Talk-to work
  across the booth (was "I can't reach that!"). Verified on the device: Bank on the Lumbridge banker
  across the booth opens the bank. The quest-list filter is verified too (7/11 quests).
- **URL_OPEN 24**: an encoder is bound (`::url`). A live `::url` dropped the app to the login screen.
  The client reads exactly one string and calls `ApplicationContext.openBrowser`/
  `openOverlaidBrowser`, but no activity starts (logcat): the client closes the session itself. Still tagged.

Tooling from this session (scratch, not in the repo, easy to redo):
- CS2 decoder: trailer of 16 bytes (int/string/long locals and args), name string first;
- headless jshell against `openrune/src/server/app/build/install/app/lib` (mount it somewhere other
  than `/lib`), e.g. `ServerCacheManager.INSTANCE.init(241)` from `/w` = `openrune/src` to read
  server NPC types, or `AndroidGameMessageEncoderRepository.INSTANCE.build(new
  BlockingHuffmanCodecProvider())` to check that the encoder bindings load;
- RSProt tests in Docker as root (server/rsprot build dirs are root-owned): `docker run --rm -e
  GRADLE_USER_HOME=/cache -v openrune-gradle8:/cache -v $PWD/rsprot:/src -w /src gradle:8.14.3-jdk21
  gradle --no-daemon -q :protocol:osrs-241:osrs-241-desktop:test --tests '*NpcInfo*'`, then chown
  the volume back to 1000;
- building a test cache without touching the deployed one: copy `openrune/src` to a scratch dir and
  run `./gradlew :or-cache:buildCache` there (the Docker image copies `src/.data` at deploy).

Later in the session (all static, from the client's own code; details in `docs/android-protocol.md`):
11, 120, 123 (MIDI_SWAP encoder added) S→C; 8, 20, 46, 50, 84 C→S (decoders bound for 8, 20, 50,
84). Verified 234/253 by tag count (progress.py's ApiCoverage step was failing while the client
dev's host build ran).

Remaining unverified (5; 28/57 settled afterwards by their CS2 op callers). Live-slot plan, once
the devtools MCP can drive the client: deploy the pending installDist first (MIDI_SWAP encoder, decoders
for 8/20/28/50/57/84); then send a PM (50) and trigger a settings IfScriptTrigger (8; OpenRune's
AudioSettingsScript uses one) while watching `docker logs`; then try the compass/minimap modes for 45.
- S→C 0 heatmap: the dispatcher ignores 0.
- S→C 24 URL_OPEN: one gjstr to bridge slot +0xf0, but the client drops the session; no browser
  activity starts.
- S→C 148 CAM_SKYBOX: an int at client+0x4ac*8; -1 calls FUN_007d97c8.
- C→S 45 SET_HEADING: sender FUN_00623960 writes -heading and also applies it locally; called from the
  minimap handler (0x68d7b8) and a pointer thunk (0x61ccf8). The UI that triggers it is unknown.
- C→S 69 OPWORLDENTITY6: sender FUN_00623550 writes an id g2Alt3, next to the other examine senders,
  but is reached only indirectly. Found with a 24-instruction scan window; the 6-instruction one missed it.

GROUP_FULL caveat: the client has a third (scope-2) group var list.

## Earlier state (2026-10-16, third server dev; new hashes after the authorship rewrite)

Container runs ac31a14 (deployed 23:34 local); 39e7f91 after it is tags/docs only. Metric
(`tools/progress.py --working`; revert its output files, they belong to the orchestrator): mapped
241/253, live opcodes 241/241, verified 218/253.

Test commands added this session: `::trace` (log every incoming message, Event* included),
`::jingle id`, `::chatfilter pub trade` (78), `::give id [n]` (alias of `::item`), `::proj id dx
mode`. Device tips: camera rotation = `input keyevent --longpress 21/22/19/20` (one-finger swipes
only send mouse events); after a container restart the app can hang at "Connecting to server..."
with no requests: `am force-stop com.jagex.oldscape.android`, relaunch with monkey, tap Play Now
(958,550). The client dev's Quest Helper sidebar may be left open and swallow typed input.

Next steps: remaining unverified entries (see `grep unverified` in the two enums): 45 SET_HEADING
(special minimap mode), 46 SOUND_JINGLEEND (needs audio on), 11 (labels need a script redraw:
find the chat-tab script and run it after the packet), 24 URL_OPEN, 120 IF_SETROTATESPEED, 123
MIDI_SWAP, 148 CAM_SKYBOX, clan/group bodies (19/84/87/88, 134/138/140/141), 8 IF_SCRIPT_TRIGGER,
50 MESSAGE_PRIVATE. Untested on device: Spin at the castle wheel via door 1543, Fred's gate
double-open.

- **Persistence** (7ee343a, verified live: survives relog and `docker compose restart`):
  `Saves.kt` writes one JSON file per account (Gson) with position, HP, XP, varps, inventory,
  worn and bank to `SAVE_DIR` = named volume `rsps-saves:/saves`. Loaded at login (starter kit only
  for new accounts), saved on removal, every 50 ticks and on JVM shutdown. Inspect with
  `docker exec osrs-rsps-test cat /saves/tester.json`; delete it for a fresh character.
- **Loc reach** (7ee343a): solid locs (footprint flagged LOC) need a side tile with no wall in
  between (`Collision.reachesRect`); walls/doors keep "any adjacent tile".
- **Stairs** (b2f7660): no destination table; Climb-up/down (and "Climb" with an up/down choice,
  "Top-floor", "Bottom-floor") move one level and land on the nearest open tile that has a floor
  surface (`Collision.nearestOpen`, SURFACE flag from terrain underlay/overlay). Verified live:
  Lumbridge castle stair 56230 lands at 3205,3209 level 1. Sheep Shearer route (headless):
  stairs from 3205,3209 -> Open door 1543 at 3207,3214 -> Spin the wheel 14889 at 3209,3212 from
  3209,3213. Spin itself not yet tried on the device.
- **Gates** (d928009): opening one half of a double gate (e.g. Fred's pen 12986/12987 at
  3186,3268-3269) opens the other half too.
- **Protocol**: verified live 76 PROJANIM_SPECIFIC_V4 (start height 160 was off camera; use
  `::proj 100 4 0`) and 115 HIDEOBJOPS. C->S 6 OPOBJ6, 80 HISCORE_REQUEST, 90
  UPDATE_PLAYER_MODEL_V2 named; sender-less C->S 1/12/27/62/73/76/83/85/87 and ignored S->C
  110/116/137 tagged `// dead:` (orchestrator ruling: dead opcodes stay OP_n; name only with
  per-opcode evidence). 11 CHAT_FILTER_SETTINGS_PRIVATECHAT still shows no effect.
- **RE helpers**: decompile with `analyzeHeadless re/ghidra osclient -process osclient.so
  -noanalysis -readOnly -scriptPath tools/ghidra_scripts -postScript Decompile.java 0x<addr>`
  (Ghidra image base 0x100000 = file VA + 0x100000). Every C->S sender: scan the text segment
  for `bl 0x79cc90` with capstone (`~/tools/venv`), opcode = `mov w0, #imm` before it.
- Headless map checks: jshell in `eclipse-temurin:21` with `build/install/*/lib/*.jar`, mount
  `data/cache` at `/data/cache`, redirect stdin from /dev/null (else it hangs).
- On hold until the orchestrator relays the RSMod evaluation verdict: generic stairs table, new
  quests. Keep server work small; protocol verification carries over to any engine.

## Earlier state (container ran e9b03e2, deployed 2026-10-15 21:39 local)

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

Deployed 2026-10-15 21:39 local (e9b03e2) and tested on device the same session:
- Verified: journal Combat Level 3; facing (player and Woman turn to each other on Attack, player
  faces Romeo on Talk-to); Sheep Shearer start dialogue + Shear gives wool; Romeo start dialogue
  sets varp 144 = 10.
- Not yet tested on device: spinning at the castle wheel, Fred hand-in/completion, R&J stages
  after 10. Sheep stay woolly after shearing (no transform to the sheared NPC yet). From Fred's
  house the pen sheep are "I can't reach that!" (pen gate closed; collision is right, there is
  no gate handling yet). Test setup without the dialogue: `::quest 149 start`, `::item 1735`.

Details of what was deployed:
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
2. Finish testing Sheep Shearer (wheel, hand-in) and Romeo & Juliet stages; sheared-sheep
   transform; gates (pen gate); stairs destination table; more quests on the dialogue engine.
3. Remaining S→C unknowns: 20/113/115 (HIDE*OPS / heatmap flags), 65/110 (one string), 143 (p4),
   codecs for REBUILD_REGION_V2 (41), OBJ_CUSTOMISE_SPECIFIC_V2 (132), PROJANIM_SPECIFIC_V4 (76),
   clan bodies (19, 84, 87, 88). C→S: 12, 87, 27, the 11-byte (1/62/76/85) and 15-byte (73/83)
   U/T groups need live captures; EVENT_KEYBOARD delta transform.

Scratch helpers (Python cache reader with reference tables, CS2 disassembler with opcode names,
if3 geometry decoder, script reference scanner) were in the session scratchpad; small to rewrite.
RuneLite gameval VarbitID/VarPlayerID (javap -constants on re/runelite/runelite-api-1.13.1.jar)
names vars quickly (e.g. 10670 CHATMODAL_UNCLAMP).

## Coordination

- Device and restarts (2026-10-15 lock procedure): ask the orchestrator ("main") for "OK device" /
  "OK restart"; it runs `tools/device-lock.sh grant server`. Then run
  `tools/device-lock.sh take server <what>` in its OWN step and act only if it prints
  "taking device". A container restart counts as device use (hold the lock while restarting).
  When done: `tools/device-lock.sh free server`, then report. Never write /tmp/osrs-device-status
  yourself (the script overwrites it). Grants are single-use; hold the device 15 min at most.
- Deploy = `docker compose up -d --build` in its own command.
- After a container restart or a client reinstall, `server/tools/device.sh login`; if the app
  is not running, start it with `adb shell monkey -p com.jagex.oldscape.android -c
  android.intent.category.LAUNCHER 1`.
- A server subagent's SendMessage goes to "main" (the orchestrator), which relays to the client dev.
- Bookkeeping: keep this file and android-protocol.md current, `scripts/rsprot.sh export`, and
  commit server-owned paths at each working checkpoint with explicit pathspecs
  (`git commit -m "Server: ..." -- <paths>`; the repo-local config sets the author). When your
  context nears compaction, write state here, commit, and hand off.
- Server-owned paths: `server/`, `tools/patch_server.py`, `tools/patch_local_auth.py`,
  `tools/repack.sh` (server options), `native/src/net_trace.*`, `docs/android-protocol.md`,
  `docs/server-handoff.md`. README.md, docs/progress* and tools/progress* belong to the orchestrator.
