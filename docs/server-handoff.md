# Server work: handoff notes

Status and workflow for whoever continues the test server (`server/`) and the Android protocol
work. The client side (injected library + RuneLite plugin host) is developed in parallel by
another session; see `docs/client-handoff.md`. Protocol findings live in
`docs/android-protocol.md`.

## Where things stand (2026-10-16, early; container runs 5c5aa00)

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

In progress (2026-10-16, server agent; NOT yet compiled or deployed, Docker Desktop was down
and was being started):
- **Journal totals** (uncommitted -> committed untested): varbits from RuneLite gameval names,
  read by summary script 3310: qp_max 1782 (varp 904 bits 0-9), quests_completed_count 6347
  (varp 904 bits 20-28), quests_total_count 11877 (varp 2920 bits 0-8), subquests_completed_count
  13987, miniquests_completed_count 14162. `Quests.totals` counts the quest dbtable (table 0,
  dbrows in 2/38): column 4 = 1 miniquest, column 21 = parent quest (RFD subquests). Cache 2735:
  187 quests, 10 subquests, 19 miniquests, 347 QP. `updateQuestPoints` now sets all of them and
  runs at login before the journal panel opens. Dbrow value counts are smart (1 or 2 bytes); the
  Kotlin decoder was fixed. Next: `./gradlew-docker installDist`, deploy, check the summary panel.
- **Options menu clipping, root cause found**: 219:1 is 479x122 at y=12, but chatbox:chatmodal
  162:568 is only 479x96 (centred), so rows below ~84 are clipped. Script 2581 (mobile only,
  gosub 1972) resizes 162:34 (chatbox container) to 519 x (165 + 20 [+30]) via if_setsize (2001)
  and 2000; script 908 toggles 162:568 / 162:567 / 162:561 / 162:57 visibility. Next: find what
  sizes 162:568 on desktop vs mobile (scripts 80, 113, 922, 925, 1701, 2068, 2251, 3179, 7616
  reference 162:568), or open 219 into a taller component; compare with how 231 fits (content
  within 110).
- Scratch tools (Python cache reader, CS2 disassembler with switch tables, if3 geometry decoder,
  dbrow decoder) were in the session scratchpad; they are small to rewrite (see Quests.kt for the
  CS2 / dbrow formats). RuneLite gameval VarbitID/VarPlayerID/VarClientID (javap -constants on
  re/runelite/runelite-api-1.13.1.jar) give var names and were the quickest way to find vars.

Open, in priority order:
1. Whatever the client agent reports as a Quest Helper gap (it is testing Cook's Assistant end to end).
2. Options menu shows only 2 of 3+ rows: on mobile the chatbox is clipped at ~y=260 px (the third row
   is drawn but cut off; 5 options show A, B, C partly). Script 58 has a mobile branch (gosub 1972 =
   varbit 6352 osm_simulate / mobile check) with different y positions; maybe a varbit/varc picks the
   compact layout. Sending 2379 first did not change it.
3. Journal totals (see above); more quests (Sheep Shearer, Romeo & Juliet...) with the dialogue engine.
4. Facing for combat (map the Android "face" extended-info block for players and NPCs).

Scratch Python helpers (cache reader, CS2 disassembler, quest/map decoders) were used for the
static work; equivalents live in `Quests.kt` / `Maps.kt`.

## Coordination

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
  `git -c user.name=alex -c user.email=alexperrin7@gmail.com commit` (no global git identity).
