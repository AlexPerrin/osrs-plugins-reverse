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
- **Quest Helper (real Plugin Hub jar, 2026-10-15) starts on Android**: all quest helpers load,
  startUp completes (its Swing panel is built headless), subscribers run every tick. Not visible
  yet: no sidebar UI. Next gaps it hits at runtime: `Client.runScript` (quest state scripts,
  needs the CS2 VM entry point), `Client.macroExpand`, menu entries, widget writes.
  `re/hub/` holds the hub jar (`curl https://repo.runelite.net/plugins/jar/quest-helper_<hash>.jar`,
  hash from `.../plugins/manifest/1.13.1_full.js`); convert with `tools/dex-plugin.sh`.

- **2026-10-15 (second client session)**: `Client.runScript` + int/object stacks (the game's
  `runScriptEvent`; `Quest.getState` is right for the server's `::quest` states),
  `getItemDefinition` / `ItemManager.canonicalize`, `getNpcDefinition` / `NPC.getComposition`,
  scene tile heights + settings (`WorldView.getTileHeights/getTileSettings`, so
  `Perspective.getTileHeight` works), `macroExpand` (identity). A plugin **sidebar**: an "RL"
  button at the left edge opens an ImGui window listing the ClientToolbar buttons and the selected
  panel's headless Swing tree (labels, buttons, checkboxes, clickable panels); taps go to the
  Swing listeners on the game thread. Touches reach ImGui through a `Window.Callback` proxy
  installed on the game Activity (`Sidebar.java`); touches outside the sidebar go to the game.
  **Quest Helper is usable**: quest list and full quest details in the sidebar, starting a quest
  (button next to its name, then an assist level) shows QH's step overlay in game ("Demon Slayer:
  Talk to Aris..."), no runtime exceptions left in that flow. Also `getEnum`, `WorldView.getScene`
  (tiles without objects), RuneScapeProfileChanged on login (QH waits for it before loading its
  quest list), inert models/world map/hint arrows.
- **2026-10-15 (third client session, cut short by a wrap-up request)**: menu support written
  and compiled, **not yet run on the device**. Native: `game::read_menu()/menu_open()/
  menu_entry_for_path()` (game.cpp), MenuOpened (per-cycle diff of MenuManager+0x7c0) and
  MenuOptionClicked (menu model vtable slot 1 `0xa6d6c8` patched to `hook_menu_select` in
  client_thread.cpp; consume skips the game's select). JNI `Native.menuEntries/clickedMenuEntry/
  menuOpen/consumeMenuClick`; Java `AndroidMenu`/`AndroidMenuEntry`, `Client.getMenu/
  getMenuEntries/isMenuOpen/createMenuEntry` (plugin-created entries stay host-side, not yet
  inserted into the game's menu). Layouts are from static RE (see docs/offsets.md "Menus").
  The Quest Helper Cook's Assistant end-to-end test was **not done**: the test server container
  was re-downloading its cache (fresh volume) when the session ended, so login failed.
- **2026-10-15 (fourth client session)**: menus **verified on device**: MenuOpened, select hook on
  the client thread, game node order top-first (getMenuEntries reverses it), plain taps also go
  through select (node 0, the default action) so MenuOptionClicked fires for left-clicks too.
  Entry params decoded (docs/offsets.md "Menu entry"): NPC_*_OPTION/EXAMINE_NPC (identifier = NPC
  index), GAME_OBJECT_*/EXAMINE_OBJECT (loc id, scene x/y), WALK, CANCEL, CC_OP/WIDGET_TARGET
  (widget id, slot). Not seen yet: ground items, players (decode as UNKNOWN). Quest Helper's Cook's
  Assistant overlay follows the inventory (egg/flour/milk steps, requirement colours). Gaps it hits:
  `NPC.getInteracting`, `NPC.getConvexHull` (NpcStep highlight/arrow), no dialogue-option
  highlight. The quest start through dialogue is blocked server-side (chatmenu option hit offset,
  `::quest cook` ambiguous), reported. The server's RSA key is now baked into its image; build with
  `MODULUS=8555c56c...` (or whatever server/data/rsa.properties holds) if the repack can't log in.
- **Cook's Assistant end to end with Quest Helper (2026-10-15)**: started through the Cook's
  dialogue (after the server's chatmenu fix), varp 29 0 → 1 → 2; QH's steps followed the items
  (egg → wheat → milk → "Give the Cook..."), the Cook got QH's cyan highlight, and the overlay
  closed on "Quest complete". Same session added scene objects + ground items (all planes, spawn/
  despawn events per tick), NPC hulls (footprint boxes) and canvas helpers, `ModelOutlineRenderer`
  replacement, `WorldView.getTileHeight`, extended tile settings. Unverified: object highlights
  (QH ObjectStep), item highlights on the ground, QH's arrow over the NPC after the extended-
  settings fix. Next: Sheep Shearer (object highlights, item counts), then sidebar text input,
  minimap geometry, dialogue-option highlights (widget dynamic children).
- Device protocol (2026-10-15, replaces the status file): see "Working with the server session".

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
| `host/awt/` | java.awt shim (geometry, Color, Font, BasicStroke, Graphics/Graphics2D API) |
| `host/src/net/runelite/host/CanvasGraphics.java`, `OverlayRenderer.java` | Graphics2D over android.graphics.Canvas; overlay layout and frame hand-off |
| `host/src/net/runelite/client/ui/` | Same-name replacements for RuneLite's `OverlayManager` and `FontManager` |
| `host/compile-stubs/` | Compile-only `LambdaMetafactory` (android.jar lacks it; d8 desugars lambdas) |
| `host/gen/GenStub.java` | Generates `<Iface>Stub` classes that throw for every abstract method |
| `host/gen/GenHeadless.java`, `host/tools/awt_refs.py` | Generate a headless, stateful java.awt/javax.swing for everything the client + `re/hub/*.jar` reference (JDK hierarchy, property fields, listener lists, Container children); regenerated by build.sh when inputs change |
| `host/src/net/runelite/client/...` | Same-name replacements: EventBus, ConfigManager, ClientThread, OverlayManager, FontManager, Hooks, PluginManager, ClientToolbar, ItemManager, SpriteManager, ChatMessageManager, ChatboxPanelManager (keep the delete list in build.sh step 3 in sync) |
| `host/src/net/runelite/host/Sidebar.java` | Sidebar model (flattened Swing tree), tap dispatch, touch router (Window.Callback proxy) |
| `host/plugins/example/build.sh` | Builds the Tick Info example (`re/host/plugins/tickinfo*.jar`) |
| `tools/plugin-test.sh` | Push plugins, restart the game, Play Now, print host logs |
| `host/build.sh` | Builds `re/host/host.jar`: compiles host + stubs + shim with `-source 8 -bootclasspath android.jar`, adds the RuneLite client classes it reuses (list in step 3), dexes, adds fonts. Interfaces to stub are in its `for iface` loop |
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
LOCAL_AUTH=1 SERVER=10.0.2.2:8080 ADB=/mnt/c/Users/Alex/AppData/Local/Android/Sdk/platform-tools/adb.exe \
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
- Sign-in: with `LOCAL_AUTH=1` builds the test server is the OAuth provider. If the app is
  signed out: "Sign in with Email / Username", close the privacy screen (X), then Play Now
  (957,563). Without LOCAL_AUTH the app wants the real Jagex login, which only the user may do.
  `repack.sh --install` uses `install -r`, so reinstalls keep the login.
- Shared device: never touch adb without the lock (see "Working with the server session").
  Server container restarts log the client out; after one, run the login steps again.
- Chat commands over adb: `input text` (spaces as `%s`), then `keyevent 66`. Enter only works
  while the soft keyboard is shown (`dumpsys input_method | grep mInputShown`); tap the keyboard
  button (90,220) first if it isn't. `::talk` opens a dialogue (group 231), `::obj id n` drops an item. `::tele x y plane`, `::pos`, `::npc id`, `::npcsay text` and `::npcanim id` are useful.
- The example plugin logs a lot; run `adb logcat -G 16M` or the agent token scrolls out of the buffer.
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

## Working with the server session

A separate Claude session develops `server/` (it hands over to fresh agents too; find the
current one with ListAgents); an orchestrator (the main session) coordinates both.
**Device lock** (`tools/device-lock.sh`): ask the orchestrator for "OK device"; it runs
`grant client`; then run `tools/device-lock.sh take client <what>` as its own step and touch adb
(installs, input, logcat, the memory agent) only if it prints "taking device" (otherwise DENIED).
Grants are single-use. When done: `tools/device-lock.sh free client` and tell the orchestrator.
`tools/device-lock.sh status` shows the holder. Never write `/tmp/osrs-device-status` directly
and never wait on it. Container restarts go through the orchestrator only.
File ownership: client = native/ (except net_trace.*), host/, tools/agent.py,
tools/dex-plugin.sh, tools/plugin-test.sh, tools/ghidra_scripts/, docs/offsets.md,
docs/plugin-host.md, docs/client-handoff.md; server = server/, tools/repack.sh,
tools/patch_server.py, docs/android-protocol.md, docs/server-handoff.md. Send it the
protocol gaps plugins hit (it has implemented dialogue, ground items, bank, OP* packets).
`server/tools/device.sh cmd '::talk'` types chat commands reliably.

## Suggested next steps

1. Verify (built in commit after e5487c9, not yet run): dialogue-option highlight (QH reads
   chatmenu 219:1's CC_CREATE children: `Widget.getDynamicChildren()` now returns the game's
   children, text/geometry/hidden), QH's NPC arrow after the extended-tile-settings fix (was an
   AIOOBE index 118 in `Perspective.getCanvasTileAreaPoly`).
2. Sheep Shearer with Quest Helper (server has it deployed): object highlights (spinning wheel,
   `GameObject` click boxes), NPC "Shear" steps, item counts (20 balls of wool).
3. Widget item ids/quantities (inventory items are CC_CREATE-like children of 149:0 in RuneLite;
   find the item id/quantity fields of the game's widget) for QH's inventory highlights.
4. Sidebar text input (QH search box), then minimap geometry (`getMinimapLocation`, QH arrows),
   NPC animation/orientation/interacting (currently -1/0/null), player hulls.
5. Small: the `NullPointerException ... WorldView.getPlane()/isInstance()` seen once at login
   (QuestHelperMinimapOverlay before the world view exists; guard `Host.worldView()` users).
