# Client work: handoff notes

Status and workflow for whoever continues the client side (the injected library plus the
RuneLite plugin host). The test server in `server/` is developed in parallel by another
session; its notes are in `docs/android-protocol.md`.

## Where things stand (2026-10-14)

- The official OSRS Android APK (build 241.4) is repacked to load `libosrsrl.so`, and can be
  pointed at the test server (`SERVER=…`). Target device: BlueStacks Pie64 (Android 9, arm64
  translated by libnb, GLES 3.2).
- `libosrsrl.so` provides:
  - an ImGui overlay drawn from the `eglSwapBuffers` GOT hook;
  - a game-thread hook on `Client::doCycle` (vtable slot 5);
  - read-only game accessors keyed by GNU build id (`native/src/game.cpp`);
  - an opt-in, token-authenticated, read-only memory agent;
  - the embedded Java plugin host.
- The plugin host runs unmodified RuneLite plugins on ART. Working API: game state, ticks,
  varps and varbits, local player position, NPCs with spawn/despawn events. See
  `docs/plugin-host.md` for the table and gaps, and `docs/offsets.md` for every address.
- Verified live: `host/plugins/example` (Tick Info) logs correct ticks, positions, varbit
  8119 and the four courtyard NPCs.

## Layout

| Path | What |
|---|---|
| `native/src/main.cpp` | `JNI_OnLoad`: hooks, `game::init`, `client_thread::install`, agent, host |
| `native/src/game.{h,cpp}` | Offset table per build + accessors. Add new offsets here, nowhere else |
| `native/src/client_thread.{h,cpp}` | doCycle hook, task queue, event derivation (state, tick, varp diff, NPC diff) |
| `native/src/jni_bridge.{h,cpp}` | Unpacks/loads host.jar, registers `net.runelite.host.Native`, forwards events |
| `native/src/overlay.cpp` | Debug overlay (`setprop debug.osrsrl.overlay 0` hides it) |
| `native/src/agent.cpp`, `tools/agent.py` | Memory agent (`setprop debug.osrsrl.agent 1`, restart game) |
| `native/src/net_trace.*` | Owned by the server session (socket logging) |
| `host/src/net/runelite/host/` | Host: `Host` (entry/events), `AndroidClient`, `AndroidPlayer`, `AndroidNPC`, `AndroidWorldView`, `NpcCache`, `HostEventBus`, `Native` |
| `host/src/net/runelite/client/plugins/HostPluginManager.java` | Starts plugins (same package as `Plugin` to set `injector`) |
| `host/awt/` | java.awt shim (geometry, Color, image/event shells). Graphics2D still empty |
| `host/gen/GenStub.java` | Generates `<Iface>Stub` classes that throw for every abstract method |
| `host/build.sh` | Builds `re/host/host.jar`; list interfaces to stub in its `for iface` loop |
| `tools/dex-plugin.sh` | Converts any RuneLite plugin jar into a host-loadable jar |
| `tools/ghidra_scripts/` | Headless helpers: `Decompile.java`, `Xrefs.java`, `StringXrefs.java` |

`re/` is git-ignored and holds inputs and outputs: `osclient.so`, the Ghidra project,
decompiled functions (`re/decomp/<ghidra addr>.c`), runelite jars and Maven deps
(`re/runelite/`, refetch from repo.runelite.net / Maven Central, versions in `host/build.sh`),
build outputs, and the debug keystore.

## Build, install, test

```
host/build.sh                                    # Java host → re/host/host.jar
cmake -S native -B re/build -G Ninja -DCMAKE_TOOLCHAIN_FILE=$HOME/Android/Sdk/ndk/android-ndk-r27d/build/cmake/android.toolchain.cmake \
      -DANDROID_ABI=arm64-v8a -DANDROID_PLATFORM=android-26 -DCMAKE_BUILD_TYPE=Release   # once
ninja -C re/build                                # libosrsrl.so (embeds host.jar)
SERVER=10.0.2.2:8080 ADB=/mnt/c/Users/Alex/AppData/Local/Android/Sdk/platform-tools/adb.exe \
    ANDROID_SERIAL=127.0.0.1:5555 tools/repack.sh --install
tools/dex-plugin.sh in.jar re/host/plugins/x.jar
adb push re/host/plugins/x.jar /sdcard/Android/data/com.jagex.oldscape.android/files/plugins/
adb shell am force-stop com.jagex.oldscape.android; adb shell monkey -p com.jagex.oldscape.android -c android.intent.category.LAUNCHER 1
adb shell input tap 957 563                      # "Play Now" (1920x1080)
adb logcat -s osrsrl osrsrl-host 'rl.*'          # plugin logs use tag rl.<Class>
```

Gotchas:
- Under WSL use Windows `adb.exe` (run `adb kill-server` in WSL first). BlueStacks is at `127.0.0.1:5555`.
- Run `tools/repack.sh` bare. Don't prefix `cd … &&` or append a pipe, or the permission rule
  won't match.
- Never uninstall the app: the login lives in app data, and signing in again needs the user
  (real Jagex login behind Cloudflare). `repack.sh --install` uses `install -r`.
- Shared device: post "taking device" / "device free" to the server session (SendMessage)
  before adb input or reinstalls. Server container restarts log the client out.
- adb text input into the game's chat doesn't register reliably, so find positions by memory
  diffs instead of `::tele`.
- Ghidra addresses = ELF vaddr + 0x100000. `docs/offsets.md` uses ELF vaddrs.

## How to find the next thing

1. Find the packet handler or function in `re/decomp/00541dd4.c` (server packet dispatcher; the
   case numbers are Android opcodes, mapped in `docs/android-protocol.md`) or via string xrefs
   (`re/string_xrefs.txt`).
2. Decompile callees: `~/tools/ghidra_12.1.4_PUBLIC/support/analyzeHeadless re/ghidra osclient -process osclient.so -noanalysis -readOnly -scriptPath tools/ghidra_scripts -postScript Decompile.java 0x<ghidra addr>`.
3. Confirm live with `tools/agent.py` (or a short script importing `Agent` from it): read,
   snapshot, change the game state (walk, open an interface), snapshot, diff.
4. Add the offset to `kBuilds` in `game.cpp`, then an accessor. If the data is a container the
   game rebuilds, snapshot it on the game thread in `client_thread.cpp`.
5. Expose it through `Native` (JNI in `jni_bridge.cpp`), implement it in the `Android*` class,
   add the interface to `host/build.sh`'s stub loop if it's a new type, and record it in
   `docs/offsets.md` and `docs/plugin-host.md`.

Calling game functions directly works (the varbit lookup `0x5d0040` is called from our code),
as long as the call happens on the game thread.

## Suggested next steps

1. Plane: probably near the WorldView base (`wv+0x20..0x30`) or the player object. Test on stairs.
2. Players: the PLAYER_INFO reader (`0x569a04`); the local player object holds the fine x/y
   seen at `Client+0x8950c`.
3. Widgets and inventories (`IF_OPENSUB`/`UPDATE_INV_*` handlers), then items and `ItemContainerChanged`.
4. Chat messages (`MESSAGE_GAME` → `ChatMessage` event) and menu entries (leads in `docs/offsets.md`).
5. Overlays: implement Graphics2D on `android.graphics.Canvas` into a Bitmap, upload it as a GL
   texture and draw it in the swap hook. Render on the game thread.
6. Root-injector services plugins expect: `ClientThread`, `ConfigManager`, `OverlayManager`,
   `ItemManager`, `ChatMessageManager`.
7. Quest Helper (the target plugin) needs about 40 API types. Run it and implement whatever
   `UnsupportedOperationException` it hits.
