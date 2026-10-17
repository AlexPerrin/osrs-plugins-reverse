# Client work: handoff notes

> Latest state: see the gen 21 entries (2026-10-17) at the end of "Where things stand".

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
- **2026-10-15 (client dev gen 3; hashes are post-rewrite)**: verified on device with 5123432:
  Fred the Farmer gets QH's highlight and (blinking) arrow; no NpcStep NPE after `::tele`;
  Sheep Shearer starts through Fred's dialogue; shearing works; QH's ground-item highlight
  (shears) and `Take` advance the steps. 9874572: `Widget.setTextColor` writes Widget +0x98
  (QH dialogue-option highlight, verified). 9453adc: sidebar text input (Android dialog;
  QH search verified) and a GenHeadless fix (PlainDocument no longer shadows
  AbstractDocument's listener list). be4b788: top-level world view id is 0 (API TOPLEVEL),
  minimap locations via 601:34, player hulls/defaults. 82144c4 + next: actor animation/
  orientation/interacting/overhead text from static RE, AnimationChanged/InteractingChanged/
  OverheadTextChanged, ground-item menu entries, scene overlays clipped out of 601:50/36/111.
  A 16-tile hull offset seen once after `::tele` did not reproduce (camera and WorldView frames
  checked equal with `setprop debug.osrsrl.camlog 1`).
- **Where gen-3 stopped (2026-10-15 23:41, last client commit before this note: dda217a)**:
  verified live: overhead text + OverheadTextChanged (`::npcsay`), orientations read plausible
  values (0/1024/1536), QH step `mapPoint` is set (so the minimap arrow should render; not
  checked visually). Not yet verified: animation (`::npcanim`), interacting, the overlay clip
  under 601:50/36/111, minimap arrow position (601:34, zoom 4), ground-item MenuAction decode.
  Sheep Shearer: the account is at Fred's with the quest started, inventory emptied and shears
  given (server `::empty`, `::give id n` exist now). Remaining: `::give 1737 20`, castle stairs
  (ObjectStep highlight), Spin at the wheel 14889 (3209,3212,1), climb down, hand in to Fred.
  Then: model-based hulls, sidebar combo boxes, player composition, NPC/Player API coverage.
- **2026-10-16 (client dev gen 4)**: **Sheep Shearer completed end to end with Quest Helper**
  (second full quest). QH ObjectStep highlights (box + arrow + minimap arrow) on the castle stairs
  56230/16672 and the wheel 14889, steps followed the items (wool → balls), overlay closed at the
  hand-in. Added: `getObjectDefinition` (LocType cache `0x62e5ec`, multiloc `getImpostor`),
  `getWidget(WidgetInfo)` (RuneLite's own `Perspective.localToMinimap` calls it; this was hiding
  QH's minimap arrow), `getMouseCanvasPosition` (-1,-1), object minimap locations, the open menu
  rectangle (`MenuManager+0x2a8` press point + measured layout) with overlays clipped around it
  (verified), actor pose animations and player composition/combat level (verified via
  `debug.osrsrl.selflog`), NPC multi-transforms and widget scroll/model/opacity getters
  (unverified). Debug hooks: `debug.osrsrl.where <locId>` (screen position of a loc, for adb
  taps), `widgetlog`, `selflog`. Romeo & Juliet started with QH (Romeo highlight, dialogue
  highlight, varp 144 → 10, Juliet's house stairs); stopped upstairs at "Talk to Juliet".
  Tips: tapping a wandering NPC often hits "Walk here"; long-press and pick the entry
  (MenuOpened logs list entries in RuneLite order, top entry last).
  Later in gen 4: sidebar combo boxes (Android picker + ItemListener events; GenHeadless JComboBox
  keeps items; unverified), `addChatMessage` through the game's `0x43a9c0` (verified),
  `getVarps`, varcs (`Client+0x417b68`; unverified), **model-based actor hulls** (entity vtable
  slot 0 `getModel` → posed Model vertices, rotated + projected in `Hulls.model`; verified on
  Juliet/Romeo; `setprop debug.osrsrl.modelhull 0` = boxes), despawn events kept for departed
  NPCs/players (fixes a stale NPC blocking QH's NpcStep after a teleport; unverified).
  **022f070 must be on the device before teleporting**: 4d60e14 crashed on `::tele` (getModel on a
  freed entity); 022f070 resolves the live entity at call time (unverified).
  R&J state: varp 144 = 20 (Juliet gave the message, "1 x Message"); next: give it to Romeo
  (Varrock Square), Father Lawrence (3254,3483), cadava berries (bush Pick-from, or `::give 753`),
  Apothecary (3195,3405, dialogue "Talk about Romeo & Juliet."), potion to Juliet (upstairs,
  long-press her; a door blocks the direct path), back to Romeo. QH quirk (not ours): in Juliet's
  room at stage 20 QH shows the stage-60 text (copy-paste in QH's helper).
  Next after R&J: object model hulls/click boxes (GameObject `+0x230` Renderable*, slot 0; AABB
  `0x71ae98`), the real viewport zoom (`renderer+0x630c60`) instead of the fitted scale,
  `getWeight`, `menuAction`, setters (setVarbit/setVarcIntValue), widget sprite ids.
- **2026-10-16 (client dev gen 5)**: **Romeo & Juliet completed end to end with Quest Helper**
  (third full quest): Message → Romeo, Father Lawrence, cadava bush Pick-from (23625), Apothecary,
  potion → Juliet, Romeo; chat "Quest complete" and the QH overlay closed. 022f070's `::tele` crash
  fix verified (many teleports). a4e3ae9: NPC/player spawn events are held back until the actor
  has a position (a new Npc sits at fine 0,0 for a cycle; QH's NpcStep checks the roam range at
  NpcSpawned time and dropped Romeo after a teleport), and spawn lookups re-read the snapshot when
  the Java cache was refreshed earlier in the cycle (the event used to arrive with a null NPC).
  Verified: Romeo highlighted again after teleporting away and back. Debug: `setprop
  debug.osrsrl.actorlog 1` also logs each NpcSpawned with local/world/base.
  `host/tools/menu_pick.py talk` long-presses QH's cyan highlight and taps the matching menu row
  (wandering NPCs, no more "Walk here" misses). getEnergy reads 10000; getWeight can't be checked
  against this server (it never sends UPDATE_RUNWEIGHT).
  Later in gen 5 (976174a..fdaa3b0): client-side setters through the script VM's own functions
  (setVarbit, varp write-back via queueChangedVarp, setVarcInt/StrValue, getVarbit composition;
  verified), scene-object click boxes/hulls from the real models (GameObject and wall verified,
  decorations unverified; offsets from static RE of the tile draw, docs/offsets.md "Scene object
  models"), playSoundEffect (unverified), live viewport reader (zoom 512 at 1920x1080; did NOT fit
  better than the fitted scale, cause open), ~25 Widget getters from the CC_SET handlers (verified),
  Widget setHidden/setText (unverified), and no GameTick while LOADING (fixes QH's empty quest list
  on OpenRune, verified there; getWeight verified on OpenRune = 9 after ::give 1127).
  Debug hooks added: `debug.osrsrl.set 'varbit|varp|varc|varcstr id v'`, `debug.osrsrl.wdump
  <group>`, `debug.osrsrl.where` now logs model position, `camlog` logs the viewport.
  Next: verify decoration hulls, setHidden/setText, playSoundEffect; Widget.getType (+0x28);
  why the real zoom (512) doesn't reproduce the fitted projection (camera distance/height?);
  menuAction (needs the menu builder's per-action std::function, or reuse of an existing entry);
  more Widget/Client coverage (tools/progress.py lists per-interface counts).
- **2026-10-16 (client dev gen 6)**: **second plugin: Ground Items** (core RuneLite, on by default on
  desktop). It uses API that Quest Helper doesn't: MenuEntryAdded and menu text edits, ground item
  timers, prices, notifications. Core plugins need no dex: `tools/dex-plugin.sh --core grounditems
  re/host/plugins/grounditems.jar` (just plugin-classes.txt), then push it like any plugin. Needed a
  same-name `Notifier` (javax.sound is missing). Verified on device: labels "Egg (3) (GE: 12 gp) (HA: 6 gp)"
  (prices = store value), MenuEntryAdded posted before MenuOpened, plugin edits to option/target
  written back into the game's open menu (orange names), picking a rewritten entry and plain taps
  still work, Widget.getType, setText/setHidden, wall/ground decoration models read. The renderer's
  camera equals ours, so the zoom 512 mismatch is a pure factor (see docs/offsets.md "Scene object
  models"). Static, unverified: definition params (`getIntValue/getStringValue` on item/NPC/object
  compositions), getMapRegions, isPrayerActive, getLocalDestinationLocation, idle ticks, playSoundEffect
  (ran, not heard). Debug hooks: `debug.osrsrl.set 'whide|wtext <group:child> <v>'`, `'sound <id> <vol>'`,
  `'iparam <item> <param>'`; `debug.osrsrl.where deco|ground` lists those within 5 tiles; `camlog`
  logs the scene camera and its trig. Coverage ranking: a small script over the client jar's core
  plugins (javap, Client/Player/... calls minus host overrides and API defaults) gave the order used
  this session; the remaining top items are getWorld, createScriptEventBuilder, getFriendsChatManager,
  getDBTableField, getClanChannel, getMessages, sidebar combo boxes and NPC multi-transforms (still unverified).
- **Gen 6, later (2026-10-16 02:30-03:15)**: verified on device: definition params (bronze axe
  param 14 = 5), getMapRegions, idle ticks, getMessages, widget setters + revalidate (setOriginalX
  relaid the "All" chat tab), ItemLayer.getHeight (Ground Items labels above the items). Added
  createScriptEventBuilder, widget field setters (position/size/modes/sprite/opacity/font/flags),
  NPC getId/getName = current multi-NPC transform (-1/null when hidden) and no hull for a hidden one.
  **OpenRune fixes:** OpenRune's cache has 65660 varps; the varp diff's 1 << 16 bound skipped every
  VarbitChanged there, so QH never advanced (d67379a, verified: live `::varp 144` moves QH's R&J
  steps). Quest status script 4029 is fine there (Sheep Shearer FINISHED at varp 179 = 21).
  **Open (priority 1 for the next client dev): QH highlights on the wrong tile on OpenRune.**
  Romeo (id 5037, NPC index 19884; OpenRune's NPC indices are large, the old server's tiny) reads fine
  x/y 6976,6080 = 3214,3423 from Npc +0x1fc/+0x200 while the game draws him ~5 tiles north at the
  fountain; QH's arrow/outline follow our coordinates. Model, base (WorldView +0x24/+0x28 matches
  ::mypos) and projection are fine. Leads: a stale entity (index list wv+0xd0 vs hash map
  wv+0x98), +0x1fc/+0x200 not being the drawn position for these NPCs (compare +0x268/+0x26c and the
  path arrays live with tools/agent.py), or a second, visible Romeo with another id. Static RE of
  NPC_INFO (`0x469778`, decomp `re/decomp/00569778.c` ~l.560-590): on add/teleport the game sets the
  path start tile (+0x3f0/+0x418) and fine +0x268/+0x26c from the local player's tile (Client
  +0xcb7c/+0xcb80) plus the packet deltas; otherwise it queues a walk step, and +0x1fc/+0x200 is the
  per-frame drawn position along the path. So +0x1fc is the right field for a live entity, and the
  entity we read is probably not the rendered one. Ruled out: the render world view (camlog's last
  field `render_wv_same` was 1 on OpenRune, render base 3160,3376 = ours). **Check first:** whether a
  second, visible Romeo with another NPC id stands at the fountain (long-press him: MenuOpened logs the
  NPC index/id), then index list (wv+0xd0) vs hash map (wv+0x98) for index 19884. Probe:
  `setprop debug.osrsrl.set 'npcmodel <id> 0'` (lists base-id matches with index, tile, model
  bounds, hull, feet). Other probes this session: `quest <QUEST> <varp>`, `iparam <item> <-1|param>`,
  `wmove <g:c> <dx>`, `whide`/`wtext`, `sound`, `debug.osrsrl.varlog 1` (logs VarbitChanged),
  `debug.osrsrl.eyeback D` (experimental projection eye offset, off by default).
  Pitfall: `debug.osrsrl.set` values persist across app restarts and re-run once per process (a
  leftover `wmove` hid the "All" tab after restarts); clear the props before freeing the device.
  Still unverified: sidebar combo boxes, NPC multi-transforms in game, createScriptEventBuilder,
  ground item timers/ownership, playSoundEffect audio. getWorld: the world id is jav_config
  param 12 (1 old server, 255 OpenRune); where the client stores it is not found yet.
- **2026-10-16 (client dev gen 7)**: **the OpenRune wrong-tile highlight is a camera zoom problem,
  not a wrong entity.** `setprop debug.osrsrl.drawlog 1` (41ee61f/6441dc8) hooks the Npc vtable slot 0
  (getModel) and logs, every 50 cycles, each WorldView hash-map node near the player with fine x/y,
  +0x268, path start, list membership and how often the renderer asked for its model (caller
  `0x72dbbc` = drawRenderable). Romeo is a single entity (index 19868 that session; indices change per
  login), in the map and the index list, drawn every frame; no duplicates or stale nodes. What differs:
  this OpenRune account's view is zoomed in a lot (player ~3x bigger on screen than on the old server's
  account), while every camera value we read is identical on both servers (Client camera 1136 behind /
  470 above the focal point, pitch 1024, viewport zoom 512, scene trig). So the GPU applies a zoom that
  none of our fields carry, and the fitted scale only matches the old account's zoom; highlights get
  pulled toward the screen centre (the player). Probe: gltrace (`setprop debug.osrsrl.gltrace 1`, read at
  startup) now scans glBufferSubData/BufferData/mapped-range uploads for the ViewTransforms UBO (std140:
  camera position @0, view @16, inverse view @80, projection @144, view-projection @208) and logs it
  every 5 s. Next: capture it on both servers, then project with the GPU's matrices (or derive camera +
  scale from them) instead of the fitted 285/720.
  getWorld = Client +0x3174, getWorldType = WorldType.fromMask(Client +0x2f64) (the CS2 map_world 3318 /
  worldflags 3324 handlers in `0x5eaf30`); verified 255 on OpenRune, 1 on the old server, flags 0x1.
  getDBTableField (44e3f49, unverified): `game::db_field` calls db_getfield (7502) in `0x5cd7f4(vm, op)`
  with row, (table << 12 | column << 4), tuple on the VM int stack (table = DBRowType `0x74f824` +0x30)
  and reads back ints/longs/strings in column-type order (DBTableType `0x6eb2c4`, column types
  `0x6ec320`; type descriptors at globals `0x1d8aff8` int, `0x1d8b000` long, `0x1d8b008` string). Test:
  `setprop debug.osrsrl.set 'db <row> <col> <tuple> [rows]'`. Also: null friends chat/clan getters,
  sprite override maps, empty instance template chunks.
  **Fixed later in gen 7:** the projection scale now comes from the game's viewport zoom
  (`Client+0x46d4` * viewport height / 334, viewport rect `+0x46e0..+0x46ec`; docs/offsets.md), which
  follows the zoom settings: QH's Romeo outline sits on his model on OpenRune (zoom 512) and on the old
  server (zoom 127). Also getVarps had the same 1 << 16 bound as the varp diff (empty on OpenRune; now
  1 << 20). The UBO notes below are kept for reference only.
  **(Superseded) next session:** with a device slot (old server, then OpenRune with the zoomed-in account),
  `setprop debug.osrsrl.gltrace 1`, restart, log in, and read the `gltrace:` lines (hook flags, which
  upload path carries the UBO, then 7 rows of the block). Map uCameraPosition onto our camera
  (6848/-825/5328-style local units), check the projection's P[5] * 1080/2 against the fitted 427 on the
  old server, then drive `getCameraFp*`, pitch/yaw and `getScale` from `Native.viewTransforms()`
  (44e3f49) and make the UBO hooks always on (scan only the found offset after the first hit). If no
  upload carries it, look for the CPU copy (search memory for the projection floats with tools/agent.py).
- **2026-10-16 (client dev gen 8)**, commits 35aa2a1..4c5846b, all on OpenRune (the default server):
  - Regression pass OK: Cook's Assistant end to end with QH (twice), Ground Items labels, isPrayerActive,
    sidebar combo boxes (QH Difficulty picker). The quest-start Yes/No tap works with and without QH's
    highlight after the server's chatmenu unclamp fix (153c8ce).
  - **Bug class to watch:** gen 7's getDBTableField called Ghidra addresses (ELF + 0x100000) and crashed the
    game; fixed (ELF `0x64f824/0x5eb2c4/0x5ec320/0x4cd7f4`), verified. Re/decomp file names are Ghidra
    addresses; always subtract 0x100000 before `g_base +`.
  - Verified: walk destination = map flag `*(Client+0x40e970)` (not `+0xecdc`), HitsplatApplied +
    getHealthRatio/Scale (actor `+0x330` hitmarks, `+0x388` head bars), getComponentTable,
    setGameState(LOADING) (game's reload `0x429900`), Actor.setOverheadText/Cycle, overlay clipping (an int
    overflow had kept the exclusion list empty forever; now panels + modal interfaces are clipped).
  - Plugins: **Opponent Information runs** (third plugin; needed okhttp 3.14.9/okio in host.jar and an
    OkHttpClient + `runelite.static.base` binding; NPCManager fetches max HP from static.runelite.net).
    **Boosts Information runs.** XP Drop starts but needs ScriptPreFired; Status Bars needed
    ItemStatChangesService (bound now, unverified). Extra jars live in `re/host/plugins-extra/` so
    `tools/plugin-test.sh` keeps the default set (QH, Ground Items, Opponent Info).
  - Friend/ignore containers (FriendSystem `*(Client+0x46c0)`, docs/offsets.md): list state reads 2,
    members unverified (no friends on the account; adding one needs the friends tab + keyboard).
  - `host/tools/rank_api.py [N]` ranks unimplemented API by core-plugin usage (skips API defaults).
    Top now: ClanChannel/FriendsChat members (managers are null), GE search setters, setDraggedOnWidget,
    getChatLineMap/ChatLineBuffer (Chat History, default-on), world hopper (createWorld/changeWorld),
    queueChangedSkill, setWidgetSelected, getCanvas, getSelectedSceneTile. `Client.getAccountHash` is called
    by the client jar but missing from runelite-api 1.13.1 (version skew; ignore).
  - QH note: its quest list refreshes on login; after `::quest cook reset` mid-session a finished quest may
    stay hidden until relog (QH behaviour, not ours).
  - Next: ScriptPreFired/ScriptPostFired (hook the script VM entry; unlocks XP Drop and many others), verify
    Status Bars, friends with a real friend, chat line buffers, InfoBox overlay (InfoBoxManager has no
    renderer; Boosts used its own overlay).
- **2026-10-16 (client dev gen 9)**, commits f63a715..:
  - **Inline arm64 hooks work under libnb** (`native/src/inline_hook.cpp`): patch the first 4
    instructions (must not be PC-relative; expected bytes are checked) with `ldr x16,#8; br x16`, from
    JNI_OnLoad before the target ever runs. Used for **ScriptPreFired/ScriptPostFired**
    (`script_hooks.cpp`: runScriptEvent = root; script lookup `0x3f5658` called from GOSUB = proc entry
    with args on the stacks; locals swap `0x4dfd5c` called from RETURN = proc exit). Listeners see the
    VM's live stacks (int stack edits written back). On by default; `setprop debug.osrsrl.scripts 0`
    off, `2` logs every script. 30 fps with everything delivered.
  - **Core plugin batch**: `## Core plugin status` table in docs/plugin-host.md (the orchestrator's
    progress.py parses it; keep its format: `| Plugin | Status | Notes |`, status runs/starts/throws/untested).
    `EXTRA="a b" tools/plugin-test.sh` pushes `re/host/plugins-extra/<name>.jar` (make them with
    `tools/dex-plugin.sh --core <pkg> re/host/plugins-extra/<pkg>.jar`). Always restore the default set
    (grounditems, opponentinfo, quest-helper) afterwards.
  - Host fixes from the batch: @PluginDependency installs the dependency's `getPublicModule()` (as
    RuneLite; XpTrackerService, ItemStatChangesService), config interface defaults
    (`setDefaultConfiguration`), Notification config as on/off, `runelite.title`/`runelite.ws`, protobuf-javalite +
    RuneLite http-api in host.jar, WidgetItemOverlay gets its OverlayManager + inventory/bank WidgetItems,
    Status Bars' viewport 161:73 → side panel 601:111, `getChatLineMap` (host-side), `getStructComposition`.
  - **Android 9 lacks Java 9+ APIs**: `host/tools/api_gaps.py` (dexdump.exe of host.jar vs the SDK's
    api-versions.xml) lists them; RuneLite's `Text` and `WildcardMatcher` are same-name replacements for that.
  - Later in gen 9 (up to c4b8ab3): NPC Indicators runs; getStructComposition verified (Attack Styles computes
    the style); VarbitChanged for unchanged server varps (changed-varp ring) + per-varbit events (verified, fps 30,
    index 23 ms); Player Indicators / Special Attack Counter start (protobuf + http-api). Table: 9 runs, 32 starts,
    0 throws.
  - Next (gen 10): verify Attack Styles' overlay below the mobile chatbox (ABOVE_CHATBOX_RIGHT remap,
    unverified; config group `attackIndicator`); orbs ARE group 160 as on desktop (`wdump all` shows
    160:10 '99' etc.; the old single-group dump stopped at the first missing child), so check why Regen
    Meter / Run Energy draw nothing; promote more `starts` rows by checking each plugin's visible feature
    (Ground/Object Markers need shift-menu entries: `isKeyPressed(SHIFT)` is always false; Timers & Buffs,
    Item Charges, Inventory Tags need items/config); the login varp burst can overflow the 32-entry ring
    (Attack Styles misses varp 43 = 0 at login); NPC hulls also draw over the left button column (add it to
    OverlayRenderer's opaque list); XP Drop's hideSkillIcons; ChatIconManager.loadRankIcons NPE (getModIcons
    null); Chat History removal from the game's chat; InfoBox placement (bottom right, by the tab stones).
  - Device-run recipe: config tweaks go in /sdcard/Android/data/com.jagex.oldscape.android/files/settings.properties
    (group.key=value; append while the app is stopped), `EXTRA=... tools/plugin-test.sh`, `setprop debug.osrsrl.overlay 0`
    for clean screenshots, probe plugin fields with `setprop debug.osrsrl.probe Plugin.field` (tag osrsrl-probe).
- **2026-10-16 (client dev gen 10)**, commits 1c137ef..:
  - **Plugin menu entries** (`createMenuEntry`) show as a strip under the game's long-press menu
    (ImGui, `plugin_menu_frame`); a tap runs `onClick` + `MenuOptionClicked` and closes the game menu
    (`game::menu_close`, the close branch of `0x599f68`). **Virtual Shift** toggle under the RL button
    (`isKeyPressed(KC_SHIFT)`), `getSelectedSceneTile`, touch-as-mouse (`getMouseCanvasPosition` = touch
    point while down + 3 s) with RuneLite's `TooltipOverlay`, login varp resync on ring overflow.
    Verified: Ground Markers, Object Markers, Attack Styles, Regeneration Meter (draws only below full
    HP/spec by default), Run Energy (orb tooltip). Table: 14 runs.
  - **Sign-in rule (orchestrator, hard):** on login screens tap only the signed-in "Play Now" button or,
    when signed out, "Sign in with Email / Username" (LOCAL_AUTH). Never tap "Sign in with Google" or touch
    any Google/Jagex account screen; screenshot and match the screen before every tap there and abort if it
    isn't the expected one. `tools/plugin-test.sh` now classifies a screenshot (Play Now vs chooser) and
    exits 2 instead of tapping when it isn't the Play Now screen. Background: blind retry taps signed the
    account out and the old fixed Play Now tap then opened Google sign-in (backed out with BACK).
  - "Either your account is still logged in": wait ~30 s after a force-stop before logging in again
    (a background `sleep 30`, screenshot to confirm the dialog, then "Try again" at 957,670).
  - **Performance rule:** anything Perspective calls (camera, viewport, canvas size, player position,
    tile heights) must be cached per cycle (`Host.cycleStamp`). Per-vertex JNI took the game to 0 fps
    with NPC Indicators on ~40 goblins (taps stopped working). `setprop debug.osrsrl.stacks 1` logs the
    game and main threads' Java stacks every 2 s (tag osrsrl-host) to find such hot spots.
  - Later in gen 10 (f0c4531..8b5745d): ChatMessageManager.queue → game chat (Examine runs),
    MessageNode.setValue writes Message +0xa0 (unverified: does the chatbox redraw?), plugin submenus,
    getAccountHash (OAuthApi; XP Tracker runs), StatChanged held until the local player exists (Agility
    NPE), user.home = files dir (Emojis cache), sprite-widget overlay clip, Combat Level and Prayer
    run, getWidgetSpriteCache/createSpritePixels (Poison; unverified). Table: 18 runs, 23 starts.
  - Next (gen 11): verify XP Drop hideSkillIcons (Poison now no longer throws), Interact Highlight (tap-attack missed moving goblins; long-press + Attack is more reliable), setValue redraw (Chat Notifications/Chat Filter;
    if the chatbox doesn't redraw, find the chat rebuild trigger, e.g. RuneLite's refreshChat equivalent);
    Woodcutting/Fishing/Mining (::item 1351 axe; the oak by the Lumbridge goblins), Interact Highlight,
    Idle Notifier; "hover" menu entries for Item Stats (getMenuEntries while no menu is open = entry under
    the touch point); Swing colour picker for "Pick" entries (Android colour dialog); OpenRune may send
    some examine text as GAMEMESSAGE (Examine logs a type mismatch; reported). Stale config value
    `boosts.notifyOnBoost=...Notification@...` from an old build reads as OFF.
- **2026-10-16 (client dev gen 11)**, commits bf46c0a..:
  - **Hover entry**: with no menu open, `getMenuEntries()` returns one entry for the inventory slot under
    the touch point (`AndroidMenu.hoverEntry`, per cycle) -> Item Stats tooltips. Hold a finger on the item
    (`input swipe x y x y 2500`) and screenshot within ~0.5 s, before the long-press menu opens.
  - **Colour picker**: same-name `RuneliteColorPicker`/`ColorPickerManager` (`host/src/net/runelite/client/
    ui/components/colorpicker/`) open `ColorPrompt` (Android dialog: hex, preview, 24 swatches). Works for
    menu "Pick" entries (Ground Markers, Inventory Tags); ConfigPanel colour buttons get mouse events now
    (sidebar buttons without ActionListeners), unverified there.
  - XP Drop `hideSkillIcons`: nulled child slots are re-hidden every cycle for 150 cycles (the drop script
    unhides them). Chat: the node's RuneLite-format message is applied after ChatMessage (Chat
    Notifications' `<colHIGHLIGHT>`), and `chatFilterCheck` is raised once per new message with host-made
    stacks (`AndroidClient.fakeStacks`); the chatbox shows rewritten text when it next rebuilds.
  - Widget Examine = identifier 10 / CC_OP_LOW_PRIORITY (Inventory Tags). TOP_LEFT overlays stack below the
    chatbox (unverified; Woodcutting's panel was under the chatbox before).
  - Verified: Item Stats, Interact Highlight, Chat Filter, Chat Notifications, Woodcutting, Fishing, Item
    Charges, Timers & Buffs, Agility, XP Drop icons, colour dialog. Table: 27 runs.
  - Config keys: `chatnotification.*` (no s). Append test keys with one `echo` per line (printf `\n` through
    adb shell is unreliable); the host rewrites settings.properties on any setConfiguration.
  - **Item icons**: `Client.createItemSprite` calls the game's own createItemSprite (`0x63ff9c`, docs/offsets.md;
    game thread, its LRU cache), so `ItemManager.getImage`/`getItemOutline` work (Inventory Tags outline,
    stamina InfoBox icon verified 2026-10-16, no crash, 30 fps). `scale` != 512 is ignored.
  - Table at the end of gen 11: 30 runs. Cannon: OpenRune doesn't set up cannons.
  - Next (gen 12): Fishing/Cooking counters after the OpenRune redeploy (river spot 1527 = Lure fly rod +
    feathers lvl 20 / Bait rod + bait lvl 25; sea spots at Draynor), Examine single price line (server line
    removed); ConfigPanel colour buttons in the sidebar (unverified); hover entries for equipment (387) and
    bank (12) slots; Chat Timestamps (the mobile chat already prints times; its `addTimestamp` callback isn't
    raised), Chat History, Logout Timer, Daily Task Indicator, Random Events, Implings (`::npc` takes a duration
    and name on OpenRune), Player Indicators / Special Attack Counter (need other players / spec weapon),
    Poison; then rank_api.py. Chat Filter's duplicate collapsing needs per-redraw callbacks (not emulated).
    OpenRune mining text was "pickaxe" (server fix requested); `::npc` takes `duration npcDebugName`.
- **2026-10-16 (client dev gen 12)**, commits e79bdb2..:
  - **Chat**: `chatMessageBuilding` is raised once per new message (Chat Timestamps puts its local time
    before the line, after the game's own UTC time). `Client.addChatMessage` returns a pending node that
    binds to the game's message when it arrives (postEvent=false honoured). `ChatLineBuffer.removeMessageNode`
    removes the line from the game's chat channel ring (`game::chat_remove_message`) and `refreshChat` bumps
    the chat cycle (`0x43aacc`); Chat History's "Clear history" removed the lines once but they came back on
    a later rebuild (the All tab probably reads another list: next lead is `0x3ef964`'s second map at
    `PTR_DAT_00b82728`).
  - **Idle logout**: `setIdleTimeout` writes the game's AFKTimeout setting (minutes, `Client+0x2278 -> +0x154c`,
    re-applied at login; the game saves it, so reset it to 5 after tests). Logout Timer verified.
  - **Sprites**: `SpriteManager.getSprite` returns the game's own sprites (`0x5d4074` sprite cache, frame 0);
    `Client.getSpriteOverrides` writes same-size overrides into the cached sprite and restores on remove
    (Poison's green heart verified). Sprite calls are guarded until game state >= 10 (a startup call crashed
    the game thread).
  - **Members**: items show "(Members)" with no ops until the login screen's world switcher selects world 255
    (`setWorld 0x432748` is the only path that sets the members byte `0x1221e90`); server fix pending.
    Cannon verified that way.
  - Hover entries now cover bank items (12:12), the inventory beside the bank (15:3) and worn equipment (387,
    child 1 item; unverified). BufferedImage(ColorModel, WritableRaster, ...) / copyData / getRaster in the shim.
  - Same-name `SessionManager` (no runelite.net login), player option arrays, headless `JSpinner.getEditor`.
  - Members flag: `game::sync_members_world` (per cycle) calls the game's setMembersWorld when Client+0x2f64 & 1
    and the members byte Client+0x2070 disagree (the jav_config start path skips setWorld). The Notifier posts with
    postEvent=false (no ChatMessage), which ended Cannon's re-notify loop.
  - Verified in gen 12: Chat Timestamps, Daily Task Indicator, Fishing/Cooking counters, Examine single price line,
    Logout Timer, Poison heart, Cannon, Loot Tracker, Hiscore, Mouse Tooltips (inventory + equipment hover).
    Table at the end of gen 12: 39 runs / 11 starts / 0 throws of 50.
  - Device tips: hold-to-hover screenshots need `adb exec-out screencap` ~0.35 s into a 2.5 s `input swipe`
    (the long-press menu opens at ~1 s); the logout panel is the door icon at (88,40) then (1490,962); the world
    switcher is (288,994), tap world 255 twice. The memory agent crashed the game once at startup under houdini
    (retry). OpenRune `::npc <duration> <gameval>` (e.g. `ii_impling_type_1`).
  - Next (gen 13): Special Attack Counter (dragon dagger spec after OpenRune e09bc3d) and Bank (`::openbank` with
    nothing open); Implings once OpenRune's spawned impling reaches the client; Notes (JTextArea shows as a
    checkbox: make editable JTextArea kind E and fire focusLost after the prompt, but check that the headless
    PlainDocument returns the text first or the note is wiped); Skill Calculator's icon-only skill picker
    (sidebar H nodes need a label: use the icon's sprite/tooltip); Chat History clear (lines return on the next
    rebuild: find the All tab's list); Poison infobox (copyData shim fix, unverified); Player Indicators via
    multi-client; Menu Entry Swapper needs the game's default tap action reordered (only text edits are written
    back today). host/tools/rank_api.py shows no API gap used by more than 3 core plugins.
- **2026-10-16 (client dev gen 13)**, commits 67113dc..c563943:
  - **Sidebar**: headless documents hold their text (GenHeadless: `AbstractDocument.__content`;
    `JTextComponent.get/setText` go through the document, so `Document.getText(0, getLength())` works) and text
    components start editable as in Swing. Editable non-JTextField text components are kind `A` (multi-line Android
    prompt, then FocusListeners' focusLost, where Notes saves). Icon-only clickables (MaterialTab, icon buttons
    without text/tooltip) are kind `I` "<icon id>:<selected>": the pixels go to the overlay once per image
    (`Native.sidebarIcon`) and draw as ImGui image buttons (GL texture per EGL context); small ones after a label sit
    on the label's row (QH's quest arrows). The headless `ImageIcon(Image)` now keeps its image.
  - **Menu Entry Swapper**: a plain tap selects node 0 without opening the menu. `hook_menu_select` raises
    `MenuTap` first (synchronous); the host reads the tapped menu once (`AndroidMenu.beginSession`, setMenuEntries
    reorders persist within the tap), posts MenuEntryAdded per entry + PostMenuSort, and if a plugin put another
    game entry on top calls `Native.redirectMenuTap(nativeIndex)`; the hook then runs the game's own select with that
    node. Verified: banker tap -> "Bank"; taps with nothing swapped unchanged (MES off and on). Not done: the
    long-press menu's order (needs the game's node vector reordered) and plugin-created entries on top.
  - **Chat History clear**: `game::chat_remove_message` also unlinks the message's node from the all-messages
    std::list at `0xaab610` ({prev, next, size}, push_front in the Message ctor `0x3f0a04`; Message +0 = node, node
    +0x10 = id), which the All tab walks via chat_getprevuid/nextuid (`0x3f061c`/`0x3f0694`, by-id map `0xaab628`).
    Message +0 is pointed at the list head so the channel add `0x3ef964` skips its own unlink/free when it reuses
    the ring slot (the node and the by-id entry leak, a few bytes per cleared line). Verified.
  - Verified in gen 13: Notes, Skill Calculator, Menu Entry Swapper, Chat History. Table: 43 runs / 8 starts /
    0 throws of 51.
  - Findings: OpenRune dragon dagger (100 -> 75) and DWH (100 -> 50) specs work. Special Attack Counter logs
    "Special attack used ... DRAGON_WARHAMMER" then "Timeout waiting for hitsplat": its onHitsplatApplied needs
    `Hitsplat.isMine()` (types DAMAGE_ME 16 / BLOCK_ME 12 ...), `actor == lastSpecTarget` (same NPC instance) and
    `hitsplatTick == client.getTickCount()`; check which fails with `setprop debug.osrsrl.actorlog 1` (logs
    HitsplatApplied with type). It also logged "Removing counters due to interact change" right after the spec.
  - Bank: `::openbank` works; script 505 does run on mobile (as a gosub, `setprop debug.osrsrl.scripts 2` before
    start logs every script) but `BankPlugin.prices` stays null, and its "Computing bank price" debug line never
    appears, so getWidgetContainerPrices(12:12, 95) returns early: check `getWidget(786444)`, `getItemContainer(95)`
    (client_thread inventories snapshot) and varbit 4150 at PreFired(505), then the title widget 12:3.
  - Runtime NPC spawns (`::npc 300 goblin`) still didn't show right after the reply after OpenRune 3cf4e2b; one
    showed ~1 min later after walking. NPC_INFO add-block layout checked statically (reader at Ghidra 0x569778,
    getBits `0x71430c` vaddr): index 16, dx/dz 6|8, spawn clock flag + 2-bit sel {18,19,20,32}, type 2-bit sel
    {12,14,17,24}, ext 1, direction 3, jump 1; matches the server encoder. The spawn clock is only an identity key.
  - Next (gen 14): Special Attack Counter hitsplat match; Bank title value; Poison infobox (`poison.showInfoboxes`);
    Implings once spawns show; MES on the long-press menu order; then widely used Plugin Hub plugins (add rows).
- **2026-10-16 (client dev gen 14)**, commits f843e68..bd077c0:
  - **Event order as on desktop** (f843e68): a cycle's events (varps, NPC/hitsplat changes, spawns) now come
    before that tick's GameTick, then ClientTick (native `after_cycle`; Host drains actor changes before
    GameTick). Special Attack Counter matches the hitsplat to `getTickCount()` and now counts (DWH on an imp:
    30% drain and count InfoBoxes). Rechecked: QH step change, XP Drop/Tracker, 30 fps.
  - **Live inventories on the game thread**: `Native.inventory` reads the game's map directly when called on
    the game thread (scripts like the bank's 505 run in the cycle inventory 95 arrives). Bank title shows the
    value ("The Bank of Gielinor (35.9K)").
  - **MES long-press order** (d25b10d): the game sorts a menu by ordering its node vector {EntryList*, entry
    index, children*} over an unsorted entry list; `game::menu_reorder` permutes those nodes after the host
    runs desktop's pre-open PostMenuSort (`AndroidMenu.presort`, isMenuOpen() false meanwhile). Long-press
    menus are drawn from the model itself (MenuManager +0x7c0 == +0x140); other shows rebuild the display
    with `0x50621c`. Verified: banker menu "Bank" first, each row runs what it shows; MES off unchanged.
  - **NPC_INFO probe** (f7fbed1, `setprop debug.osrsrl.npcprobe 1`, decode with `host/tools/npc_info_decode.py`).
    Findings: the runtime `::npc` goblin is added correctly (it spawns on the player's tile, hidden inside the
    player model; not a client bug). Server bug routed: its high-res count is sometimes one lower than the
    client's list right after an add at distance 15, so the client silently drops the last slot and the server
    re-adds it next tick. +0x1fc/+0x200 is only written once an NPC moves; NPCs that haven't moved since the
    add now report the add position +0x268/+0x26c (game.cpp read_npcs), so stationary/nameless NPCs (type
    6709 etc.) appear in the host's list as on desktop.
  - **OpenRune-Developer-Tools (MCP) in the host** (72967bd, 4830915, 11fe587, bd077c0): see docs/plugin-host.md
    "Developer tools (MCP)" (build, enable, security, 6-step procedure). Host pieces: loopback-only,
    token-authenticated `com.sun.net.httpserver` (off unless `debug.osrsrl.devtools=1`); ASM call-site rewrites
    in dex-plugin.sh (`host/gen/CompatRewrite.java` -> `net.runelite.host.compat.JdkCompat`: readAllBytes,
    transferTo, Package.getImplementationVersion's ART "0.0"); converted jars keep MANIFEST.MF; the plugin's
    self-updater is stubbed out of our build (`host/gen/StubMethods.java`) and the host deletes any jar it
    could have left; `DevInput` (AWT events -> Instrumentation touches/keys, LOGGED_IN only; virtual mouse),
    `DevMenu` (menuAction: `pick_at` 0x72b758 asks the next frame to ray-pick a point, then the long-press build
    0x58fa84 runs unshown, the entry goes through the game's select via `select_menu_node`, the model is
    discarded; hover menus the same way), `HostDrawManager` (one-shot glReadPixels in the swap hook,
    frame_capture.cpp). `.mcp.json` entry "osrs-devtools" -> tools/devtools-mcp.sh (stdio <-> HTTP over
    `adb shell -T nc`; Windows adb turns LF into CRLF, undone; token cached in ~/.cache/osrs-devtools).
    Verified: tools/list, get_client_state, type_chat, list_npcs, interact_npc + get_dialogue +
    continue_dialogue, click, walk_to, screenshot.
  - **URL_OPEN** (static): case 24 -> platform vtable +0xf0 -> bridge 0x98a214 (http/https/market only) ->
    Java `ApplicationContext.openBrowser`, which shows Jagex's "You are opening a link to an external site"
    dialog; OK starts ACTION_VIEW. Why the session drops right after is unverified (focus loss?).
  - Table: 45 runs / 6 starts / 0 throws of 51 (Special Attack Counter, Bank now run).
  - Next (gen 15): Plugin Hub rows (popular plugins without GPU/desktop-only APIs: Inventory Setups, clue
    helpers, ...; build/convert like devtools, `api_gaps.py` + the new CompatRewrite table for Java 9+ calls);
    devtools rough edges: interact_npc found no point for an NPC near the left canvas edge once (hull points
    clipped?), interact_object/item_action/widget_action/select_option/enter_input not tried yet, the
    `menu: select ... x 0 y 0` of unshown menus (MenuManager +0x2a8 isn't set by our builds; harmless so
    far); Poison infobox (verify); Implings (spawns are fine; the impling spawns on the player's tile);
    recheck the server's NPC count fix with the npcprobe.
- **2026-10-16 (client dev gen 15)**, commits 4e566fd..:
  - **Plugin Hub from source**: `host/plugins/hub/build.sh <name>` clones the commit runelite/plugin-hub pins
    (`--pin <name>` copies it into `host/plugins/hub/plugins.txt`), compiles with Lombok against our 1.13.1 jars,
    keeps the LICENSE and resources, stops on extra Maven deps (Loot Lookup: jsoup), and converts to
    `re/host/plugins-extra/hub-<name>.jar`. 18 pinned by install count (api.runelite.net
    `/runelite-1.13.1/pluginhub`), all BSD-2. `host/tools/rank_api.py N --jars re/hub-build/*.jar` lists the
    Client API they use that the host lacks; api_gaps.py on their dexdump the Java ones (CompatRewrite now
    maps the Java 9 Duration methods). Table: `## Plugin Hub status` in docs/plugin-host.md.
  - Host gaps closed for them: getDBTableRows (row scan of the DBRowType cache), getDBRowsByValue,
    **getDBTableField with desktop semantics** (k-th field of every tuple; db_getfieldcount 7503), getItemCount,
    getClientThread, getSprites/getIndexSprites, ConfigManager.getProfile/getConfigDescriptor, enum-like
    config classes (FontType), jopt-simple in host.jar, headless Swing builders (GroupLayout), JPopupMenu
    children, overlays only while LOGGED_IN, menu tile kept after the menu closes (plugin-strip entries),
    InteractingChanged for actors that arrive interacting (Random Events' Genie).
  - **host/build.sh now fails on any javac error**: a filtered GenHeadless error had shipped a partial host.jar
    (NoClassDefFoundError WorldMapDataBag at start).
  - URL_OPEN crash: server-side (the payload is ISAAC-encrypted per byte; fixed by the server dev). A/B with
    `debug.osrsrl.off=1` (installs nothing of ours) showed stock dropping the session where we crashed; per-cycle
    actor/inventory/menu reads now only run in LOADING/LOGGED_IN. `debug.osrsrl.crashlog=1` logs arm64
    pc/lr/regs of faults in the game lib or ours (chains to the previous handler; not yet exercised).
  - devtools: interact_npc prefers hull points on the canvas and off the UI columns (left-edge fix,
    unverified); select_option, continue_dialogue, item_action (needs the inventory tab open) used for Turael.
  - Verified in gen 15: Implings (Puro-Puro), Random Events, NPC Aggression Timer, Slayer (core); Better NPC
    Highlight, Monster HP %, Visual Metronome, Attack Timer Metronome, Shortest Path, Max Hit (hub). Tables at the
    end of gen 15: core 49 runs / 2 starts (Emojis, Player Indicators) / 0 throws of 51; hub 21 rows: 6 runs,
    13 starts, 1 throws (Charges Improved, compat fix for Collection.toArray(IntFunction) built, unverified),
    1 untested (Loot Lookup, jsoup).
  - Test keys: `randomevents.notifyAll` (not notifyAllEvents), `npcUnaggroArea.npcUnaggroAlwaysActive`,
    `BetterNpcHighlight.tileHighlight/tileNames`, `MonsterHP.npcShowAll`, `implings.showgourmet`. Slayer: tester
    has Turael's 17 Icefiends; `::varp 394 <n>` changes the count (InfoBox appears on a change, as on desktop).
    Shortest Path clears targets within 5 tiles at once; pick a far tile. Probe nested fields with
    `debug.osrsrl.probe Plugin.field.field` (e.g. `ShortestPathPlugin.debugState.search`).
  - api_gaps.py misses Java 9+ *interface default* methods (Collection.toArray(IntFunction) slipped through);
    CompatRewrite now rewrites INVOKEINTERFACE too; JdkCompat needs one overload per receiver interface.
  - Next (gen 16): verify Charges Improved, Customizable XP Drops' drops, Tree Despawn Timer, Improved Tile
    Indicators, Large Logout, Radius Markers; Banked Experience's JComboBox label (renderer: show the item's
    toString/renderer text, not the class name); Bank Heatmap's created button (long-press showed no menu: plugin
    widgets created with createChild are host-side only); more hub plugins by install count; Player Indicators via
    the desktop login; the impling name label height; devtools left-edge fix unverified.
- **2026-10-16 (client dev gen 16)**, commits 7855e48..:
  - **api_gaps.py** resolves calls through the api-versions.xml superclass/interface graph (inherited Java 9+
    default methods such as `ArrayList.toArray(IntFunction)` -> Collection, API 33), `--own <host dexdump>` for
    plugin jars, a HIDDEN table for members from non-SDK superclasses (StringBuilder.getChars). All 21 hub jars
    clean. **CompatRewrite now also runs over the client classes in host/build.sh** (Moonlight potion's toArray,
    Fairy Ring's static `Collectors.toUnmodifiableMap`). Lesson: the first version rewrote `toArray` on any
    `java/util/` owner, which caught `Stream.toArray` and broke Slayer's startUp (IncompatibleClassChangeError);
    it is limited to java.util / java.util.concurrent types now. Check the "compat rewrites: N" count (2 now).
  - Host: Sidebar combo boxes show the renderer's JLabel text (Banked Experience); `BeforeRender` posted each
    frame before overlays; GameTick and NpcSpawned wait for the local player (Tree Despawn Timer, Attack Timer
    NPEs at login); `ScriptEvent.getSource()` from ScriptEvent +0x40 (`Native.scriptEventSource`);
    `PluginPanel.onActivate/onDeactivate` for the selected sidebar panel (Time Tracking's panel was stale);
    TOP_RIGHT overlays stack below the minimap block (root child 36), which clipped UNDER_WIDGETS overlays
    (Customizable XP Drops' drops).
  - **devtools**: with no menu open the game's entry list holds a lone Cancel, which hid the hover menu
    (interact_object found only [Cancel]; fixed, verified with Chop down). Widget ops: a virtual mouse over an
    item slot gets the slot's ops as its hover menu (`AndroidMenu.slotMenu`), and CC_OP/WIDGET_* menuActions
    long-press the widget (`DevInput.longPress`) and pick the row from the opened menu (item_action Examine
    verified). Scene picks (`menuBuildAt`) only see the scene under the UI.
  - **createChild in the game's tree** (CC_CREATE path, docs/offsets.md) behind `setprop debug.osrsrl.createchild 1`,
    **off by default**. The first build crashed at login (make_shared's out pointer is x0, not x8: check the
    disassembly, not only Ghidra's signature, before calling a shared_ptr-returning function). After the fix:
    no crash/errors with Item Stats + Prayer + Skills Tab Progress Bars, tabs toggled 3x, bank 3x, teleport,
    30 fps. Not yet confirmed: that a created widget is visible (the tester's skills are all 99, so the bars
    don't show; use an account/skill below 99, e.g. `::varp`/xp commands, or a plugin that draws a created
    text/graphic), no duplicates after reopen, relog. deleteAllChildren runs CC_DELETEALL. Turn it on by
    default only after that pass.
  - Findings: Bank Heatmap's toggles hang off desktop's bank "Show menu" op, which the mobile bank lacks (no
    long-press menu on its buttons); Large Logout targets desktop's logout tab (mobile logout differs);
    Charges Improved's daily-reset NFE until the bank was opened once is the plugin's (empty storage_bank).
  - Verified in gen 16: Charges Improved starts (throws -> starts), Banked Experience combo, Tree Despawn
    Timer, Customizable XP Drops, Improved Tile Indicators (`improvedtileindicators.customDestinationTile=true`),
    Time Tracking (core, new row; Catherby with the server's farming varbits, `::growpatches`). All 21 hub
    plugins + defaults + BeforeRender at 30 fps. Tables: core 50 runs / 2 starts of 52; hub 9 runs / 11 starts /
    1 untested of 21 (Loot Lookup row completed so progress.py counts it).
  - Next (gen 17): the createChild visibility/duplicate/relog pass, then default on; Player Indicators with the
    desktop login (tools/multiclient/desktop_login.py, coordinate with the orchestrator); Emojis (needs the
    font renderer's `<img=>` sprite array; only the `<img=` builder at 0x43caa8 was found); impling name label
    height (desktop uses the model height for getLogicalHeight; host returns 200); Large Logout on mobile;
    devtools `walk_to` args (worldX/worldY threw a null getAsInt: check its schema); more hub plugins by
    install count.
- **2026-10-16 (client dev gen 17)**, commits c80d06f..:
  - **createChild in the game's tree is on by default** (`setprop debug.osrsrl.createchild 0` turns it off).
    Pass: `::reset` (OpenRune: all stats to 1; `::master` restores 99s, always run it after), a few hits on a
    rat; Skills Tab Progress Bars' red bars appear on Attack/Strength/Defence/Hitpoints; `debug.osrsrl.wdump 320`
    now prints `dyn N` per widget: 17 per tile (game 12 + plugin 5) before and after reopening the tab 3x;
    fresh login clean. Not done: an in-process relog (the mobile logout is the door in the left drawer; my taps
    only toggled the drawer).
  - **Actor.getLogicalHeight = posed model height** (`Native.actorHeight`, Model `+0x18` = max -y, checked
    256/256 live), cached 10 cycles per actor (`ActorHeights`). Fallback boxes use it too. Impling label not
    re-checked (Puro-Puro implings showed as id -1 multi-NPCs that run).
  - **Emojis**: the font's `<img=N>` table is a global shared_ptr (docs/offsets.md "Mod icons"). Host:
    `getModIcons` = placeholders for the game's icons (`Native.modIconSizes`), `syncModIcons` each cycle pushes
    plugin entries (`Native.setModIcons`), and `game::mod_icons_cycle` points the global obj at our own header
    (shallow copies of the game's sprites plus ours), re-installed when the game swaps its table. The game's
    ctrl and array are never touched.
  - devtools: `walk_to` takes `x`/`y`/`plane` (documented in docs/plugin-host.md).
  - Clue Scroll (core) starts: `re/host/plugins-extra/cluescrolls.jar`, `EXTRA=cluescrolls`.
  - Hub: built bank-tag-layouts, barrows-door-highlighter, spawnpredictor, better-teleport-menu (no API gaps;
    collection-log skipped: built against a newer API, `getStringStack`).
  - **Second half (2026-10-17)**, commits 90bf48f..2f5627c:
    - **Events in packet order**: the Message constructor `0x3f0a04` is inline-hooked
      (`game::install_message_hook`) to copy the inventories when each chat line is made. `poll_messages`
      posts the inventory changes made before each message, then the ChatMessage; `n_inventory` reads the
      state as of the event being posted (`client_thread::inventory_view`). An inventory that didn't exist
      yet reads as absent. Verified: Loot Tracker records a beginner casket (`::item 23245`, Open).
    - `MenuEntry.getItemId` for widget ops on items (Clue Scroll's Read); `Actor.getCanvas*Location` no
      longer adds getLogicalHeight (labels were twice too high); LOGGED_IN waits for the local player;
      `getPreferences`, `getUsername`; the headless shim covers javax.accessibility with a non-null
      `getAccessibleContext()`.
    - Emojis run in overhead text. Your own public chat line isn't in the chatbox: static RE (subagent)
      says the decoder always adds the line with the overhead text and the wire layout matches RSProt.
      Our admin account's chat is type 1 MODCHAT with `<img=1>`, so check whether the line is stored
      but not displayed (log the Message constructor's type).
    - Desktop second player: `tools/multiclient/desktop_login.py --seconds 360 --cheat 'tele 3223 3218'`
      (NO_TIMEOUT keepalive every 8 s; CLIENT_CHEAT op 84). Player Indicators verified with it.
    - Verified: Player Indicators, Item Identification (`itemidentification.showHerbs=true`), Charges
      Improved, Clue Scroll, Loot Tracker casket. Converted 12 more core plugins and 10 more hub plugins
      (rows in docs/plugin-host.md, mostly `starts`).
  - Next (gen 18): re-run Metronome, KPH Tracker, Hunter Rumours (fixed, not re-run); own public chat
    line; Ammo infobox (no infobox after wielding arrows); player menu items
    (`MenuManager.addPlayerMenuItem` / RUNELITE_PLAYER, Equipment Inspector); `Client.getItemContainers`
    (devtools list_inventories); in-process relog with createChild on; promote the hub `starts` rows by
    checking each feature; Large Logout on mobile.
- **2026-10-17 (client dev gen 18)**, commits b0780b9..:
  - **Own public chat line**: not a host bug. The admin account's chat is type 1 MODCHAT (`<img=1>tester`,
    text unchanged by filters, `setprop debug.osrsrl.chatlog 1` logs every message with its type) and the
    chatbox shows it. Right after one login the chatbox didn't redraw new lines until a chat tab was tapped;
    not seen again.
  - **Player menus**: player entries are target type 6 (`[3]` player index, op 1-based: Follow 3, Trade 4,
    Report 8) → `PLAYER_*_OPTION`, `getPlayer()`/`getActor()`. Plugin player options
    (`MenuManager.addPlayerMenuItem`, `getPlayerOptions()[4..7]` + RUNELITE_PLAYER) are added to the plugin
    strip once per player when a menu opens (`AndroidMenu.addPlayerOptions`, after the pre-open
    MenuEntryAdded pass because MenuManager sets them from MenuEntryAdded). `ClientToolbar.openPanel` opens
    the sidebar on that panel (`Native.sidebarSetOpen`); sidebar tabs wrap. Headless GroupLayout adds the
    components its groups name to the host container (GenHeadless). Verified with Equipment Inspector.
  - `Client.getItemContainers()` (`Native.inventoryIds`; not exercised yet). **getItemContainer returns the
    same instance while an inventory is unchanged**: Ammo/Cannon/Wintertodt/Drift Net compare
    `event.getItemContainer() != client.getItemContainer(id)` by identity (that was the Ammo bug).
  - **ItemManager.search** over the game's item defs (tradeable, unnoted, 5615 items; built after login in
    4096-id steps per cycle, ~160 ms total), getWikiPrice, getItemStats(int, boolean). ItemManager is now
    `@Singleton` with a static index (it had been built once per plugin, 4x). Chat Commands' !price works.
  - **Chatbox inputs**: same-name ChatboxTextInput / ChatboxTextMenuInput open Android dialogs
    (`AndroidPrompts`); ChatboxPanelManager.openTextInput/openTextMenuInput. Tooltips are cleared on the
    cycles overlays skip (Item Prices drew its tooltip twice).
  - Verified (rows promoted): Ammo, Chat Commands, Item Prices (with `itemprices.hideInventory=false`),
    Equipment Inspector, Hunter Rumours, Radius Markers, Wilderness Player Alarm (desktop player at
    Wilderness 2), Barrows Door Highlighter. In-process relog with createChild on (left door icon → "Tap
    here to logout" → Play Now): clean.
  - Findings: core Metronome only plays sounds (no audio on the host); Fairy Rings augments the travel-log
    panel the server doesn't open; Ess Pouch's Check regex wants "There are three essences in this pouch."
    (server wording gap, relayed); Quest List's button is shown (createChild under 399:0) but filtering
    needs the "questFilter" runelite_callback only RuneLite's patched CS2 has. Idea (orchestrator): post a
    synthetic `ScriptCallbackEvent("questFilter")` from our CS2 VM hooks around the questlist build script,
    with the stack the patched script would push; the same pattern could cover other runelite_callback-only
    plugins.
  - Gotchas: `plugin-test.sh` relaunches faster than the server drops the old session, so the app shows
    "account still logged in": wait ~30 s, tap Try again (958,668), screenshot, then Play Now. `pkill -f
    desktop_login.py` also kills your own shell (its command line matches). Mobile side tabs: inventory
    (1757,517), skills (1840,517), quests (1757,935), spellbook (1757,768), emotes (1843,600).
  - Verified at the end (f562faf): Ground Markers "Label Tile" opens the Android dialog and the label is
    drawn; Item Prices' tooltip is drawn once; the singleton index is built once (204 ms over 16 cycles)
    and !price works. **Quest List's magnifier (its createChild button under 399:0) does nothing on tap**:
    the tap resolves to Cancel because the game's menu has no op for a plugin-created widget whose
    action/onOp listener is a Java lambda (`setAction` + `setOnOpListener(Object[]{lambda})`). Next step for
    such buttons: write the action text into the created widget's op slot and, when its menu entry runs,
    call the plugin's Java listener instead of a CS2 script.
  - Next (gen 19): the created-widget op/listener path above (Quest List search, other plugin buttons);
    touch → MouseManager
    dispatch for Screen Markers (listeners consume → don't forward to the game; plain taps unchanged
    otherwise; no MouseManager dispatch exists yet); the questFilter callback idea; remaining `starts` rows (Rune Pouch and Plank Sack need
    server support; Virtual Levels needs XP above 99; Friend Notes needs a friends list).
- **2026-10-17 (client dev gen 19)**, commits 1237474..:
  - **Ops/listeners on plugin widgets** (`WidgetListeners`): `setAction/setName/setTargetVerb/clearActions`
    write the game widget (CC_SETOP via setOp `0x5dfae8`, opbase `+0x2c0`, verb `+0x330`). The game's menu
    builder `0x525c78` lists op i only if the event-flag mask (`0x460e74`: IF_SETEVENTS range, else
    `(w+0x2b0 >> 1) & 0x3ff`) has bit i or the widget has a CS2 onOp listener. So while a Java
    `setOnOpListener` is set, the non-empty ops' bits go into `+0x2b0`, and the widget's own bits come back
    when it is cleared. On MenuOptionClicked (if not consumed) the Java listener runs and the click is
    consumed (no script, no IF_BUTTON). Keyed by (component, index) + the game's Widget* (stale slots ignored).
  - **Touch → MouseManager** (`TouchMouse`, from the touch router): DOWN/MOVE/UP → pressed/dragged/released
    (+clicked when it didn't move). A gesture whose press is consumed is taken from the game; with no
    listeners nothing is built. Headless `SwingUtilities.is*MouseButton` follow the JDK (GenHeadless
    staticBody). OverlayRenderer honours `getPreferredLocation()` for any position and `getPreferredSize()`.
  - **runelite_callback emulation by proc boundary**: "questFilter" is raised when proc 3238 returns (dbrow
    from its args at GOSUB entry; a plugin's answer replaces the proc's result on the int stack).
    `Host.questFilter`. The same pattern works for any callback RuneLite put at a proc's start/end.
    Callbacks inside root scripts (fairyringFilterFavorite/Dbrow in 8080) need more (not done).
  - **Widget CS2 listeners** read from the game (`Native.widgetListener`, slots in docs/offsets.md "Widget
    listeners"): getOnVarTransmit/InvTransmit/Load/Key, getOnOp fallback. **getDBTableField is cached**: the
    db_getfield handler can't run while a script is on the VM, so a mid-script miss reads empty and is
    fetched next cycle (`wantDbField` prefetches).
  - Regression fixed: gen 18's ChatboxTextInput lacked RuneLite's `(manager, clientThread)` constructor, so
    ChatboxItemSearch → Bank Tags → Clue Scroll failed to start. Clue Scroll needs `EXTRA="cluescrolls
    loottracker banktags"` (banktags.jar only names the core class).
  - Verified: Quest List (search filters), Screen Markers (drag draws, game gets no touch), Fairy Rings
    (destination text under the dial; travel log 381 opens on Configure), Ess Pouch (Check wording on
    OpenRune 9a73dbe; Fill with pure essence counted). Plain taps/long-press/walk unchanged; a plain tap on
    the Cook opens his dialogue (it takes a couple of seconds: the player walks there first).
  - Gotchas: the Ess Pouch plugin only counts pure/daeyalt/guardian essence (test Fill with 7936, not
    1436). `server/tools/device.sh cmd` text goes into an open chatbox input (fairy ring search) instead of
    chat. `sleep` > ~20 s in the foreground is blocked; wait for "account still logged in" with a
    background until-loop or just retry Try again.
  - Next (gen 20): fairyring filter callbacks (hook inside script 8080 or reimplement in Java); remaining `starts` rows (Rune
    Pouch, Plank Sack: server support; Virtual Levels; Friend Notes).
- **2026-10-17 (client dev gen 20)**, commits 2f19007..22335d7:
  - **Opcode-level runelite_callback emulation** (`ScriptCallbacks`, `script_hooks` opcode wrapper): the
    CS2 handler for opcodes >= 100 (`0x4e06dc(vm, opcode, script, isLong)`, called from the interpreter
    loop for every opcode >= 100) is inline-hooked. Java watches (script, opcode) pairs
    (`Native.setOpWatches`); Host.onScript gets phase 3 before / 4 after the instruction with
    id = opcode << 16 | script. Script -1 = any script whose object-stack top starts with a prefix
    (filtered natively). Callbacks see the live stack tops (`raiseOnLive`); changed ints go back with
    setScriptIntStack, changed strings with `Native.setScriptString` (game string assign `0x3f1498`).
    Opcode numbers are RuneLite's `cache/.../script/Opcodes.java` (CC_SETTEXT 1112, IF_SETTEXT 2112,
    STAT_BASE 3306, ENUM 3408, STRING_INDEXOF_STRING 4121, DB_GETFIELD 7502). Emulated: skillTabBaseLevel
    (395 after stat_base), skillTabTotalLevel (any settext of "Total level: "; mobile never runs 396),
    friendsChatSetText/SetPosition (125, not exercised), fairyringFilterFavorite/Dbrow (8080: rewrites
    the string_indexof_string result; keyed by enum 1298's result / db_getfield 364608's row). RuneLite's
    patched scripts: sparse clone of runelite/runelite `runelite-client/src/main/scripts`.
  - Also: rectilinear `java.awt.geom.Area` (host/awt), WorldViewLoaded on scene base change (teleport
    rebuilds never show LOADING to the state poll; base 0,0 skipped), GeneralPath float iterator fix,
    `Widget.getVarTransmitTrigger` (+0x5c0 vector), headless JSpinner/SpinnerNumberModel keep values,
    chatbox inputs `@Inject` constructors, sidebar JPopupMenu picker, plugin executor logs task
    exceptions, `JOptionPane` → `ModalDialogs` (blocking Android dialogs), hub build `ALLOW_DEPS=1`
    fetches Maven deps (Loot Lookup / jsoup).
  - Verified (rows promoted): Rune Pouch, Plank Sack (OpenRune 3935505), Thrall Helper (`::varbit
    arceuus_resurrection_cooldown 1`), Banked Experience, Wilderness Multi Lines, Wasted Bank Space
    (non-BiS item 26229; BiS items are filtered by default), Virtual Levels (99/103 and total 2380 with
    `::setxp mining 20000000`), Fairy Rings log filter (custom tag), Puzzle Solver (server dev's check).
  - **Dialog timeout result**: JOptionPane blocks the game thread; holding Inventory Setups' name dialog
    ~35 s disconnected the client ("Unexpected server response"). Do not leave ModalDialogs blocking.
  - Next (gen 21):
    1. **EDT move** (agreed with the orchestrator): run SwingUtilities' queue, sidebar listener dispatch
       and the sidebar model build on a dedicated "AWT-EventQueue" thread instead of the game thread, so
       JOptionPane can block there. Needed: AndroidClient getters called off the game thread must
       marshal to it (invoke on the game thread and wait, ≤ 1 cycle) since native reads are game-thread
       only; plugins' Swing code (Banked XP's combo listener reads skills) relies on that. Until then,
       cap ModalDialogs (e.g. 15 s, timeout = Cancel) or refuse to block on the game thread.
    2. **Debug overlay tap pass-through**: the ImGui debug panel (left ~600 px, on by default) swallows
       taps over the game (it covered Puzzle Solver's left column). Pass touches through outside its
       real content, or let it collapse (native/src/overlay.cpp, touch router).
    3. Loot Lookup: headless `Component.getFontMetrics` returns null (`Util.truncateToFit`); return the
       shim FontMetrics for the component's font.
    4. Friend Notes: the mobile friends list stays "Loading friends list" (429:13) on OpenRune 245ed50;
       server dev is checking FRIENDLIST_LOADED / UPDATE_FRIENDLIST order. Then add a friend, "Add Note",
       and check the icon (callbacks in script 125 are ready).
    5. Inventory Setups: add a setup once dialogs don't block (EDT move).
    6. API stubs plugins hit (`host/tools/rank_api.py`): ClanChannel.findMember, World setters/createWorld,
       Client.queueChangedSkill (Virtual Levels calls it on config change; stub throws), GE search setters.
  - Gotchas: the friends side tab is (1843,768) and shows interface 707 (sub-tabs + "00:00 UTC");
    `setprop debug.osrsrl.scripts 2` + restart logs every root/gosub (find which scripts a UI runs);
    `setprop debug.osrsrl.wdump <group>` dumps a group's widgets to tag osrsrl-probe; fairy ring
    tags are `fairyrings.fairyringtags.<CODE>=...` in files/settings.properties (read at startup).
- **2026-10-17 (client dev gen 21)**, commits ec53b85..:
  - **EDT** (`host/src/net/runelite/host/Edt.java`, plugin-host.md "Sidebar"): "AWT-EventQueue-0" runs
    invokeLater work, sidebar taps (kind 1/2) and the model build; long-press plugin entries (kind 3) stay
    on the game thread. Turn-taking: the game thread hands its turn over at the end of `Sidebar.onCycle`
    when the EDT waits for one, and parks until it comes back; the EDT is the native client thread during
    its turn (`client_thread::set_delegate`, so `on_client_thread()` and `Native.isClientThread()` are true
    there). `ModalDialogs` on the EDT releases the turn while waiting; on the game thread it is capped at
    15 s (Cancel). The generated SwingUtilities queue is a LinkedBlockingQueue; `invokeAndWait` goes through
    `SwingUtilities.__invokeAndWait` (inline on the game thread / before cycles start, else wait ≤ 60 s).
  - Verified: Inventory Setups dialog held 70 s connected (setups listed), Banked XP combo on the EDT,
    Loot Lookup (Cow table), Friend Notes (note icon). One unexplained logout ~39 s into the first dialog
    (SSO resultCode 1500), not reproduced in two 60–70 s holds; asked the orchestrator about a server
    restart then.
  - Headless Swing: `Component.getFontMetrics` (shim metrics), `Toolkit.getDefaultToolkit()` instance
    (Toolkit's abstract methods get bodies), CompoundBorder/MatteBorder keep their parts,
    BorderFactory.createCompoundBorder/createMatteBorder build them.
  - API: `queueChangedSkill` (changed-skills ring, offsets.md; unverified), `createWorld` (AndroidWorld
    record), `changeWorld` (logs only). Not done: `ClanChannel.findMember` (getClanChannel is null since
    the host reads no clan data; a real channel needs the game's clan structures), GE search setters,
    `setDraggedOnWidget`.
  - Debug overlay: compact by default (`debug.osrsrl.overlay` 0 off / 1 compact / 2 full). It never
    captured touches (NoInputs, not in `overlay_touch` rects); the "left ~600 px" report matches the RL
    sidebar's width when open (630 px). unverified: no tap-through test.
  - Friends list (orchestrator's bug): `native/src/friends_probe.cpp`, `setprop debug.osrsrl.friendprobe 1`
    + restart. With the server's resend on, login showed LOADED 0->1 (cycle 422), UPDATE_FRIENDLIST len 29
    1->2 (cycle 424), resend 2->1->2 (cycle 547): no drop on our side in that run. Next: run it with
    `::socialresend off` (OpenRune a114f17), relog, then `::social update`.
  - Untested in the last build (committed): client-thread tasks wait for LOGIN_SCREEN (Inventory Setups'
    startUp item-name NPE); chatbox prompt titles stripped of `<br>/<col>` tags.
  - Docs: Quest Helper row moved to the hub table; Hunter Rumours runs -> starts. progress.py: core
    63/65, hub 23/32.
  - Next (gen 22): friend-list probe run (above); verify the two untested changes; Virtual Levels
    `queueChangedSkill` (toggle its config while the skills tab is open); Inventory Setups highlighting
    (then promote); the Friend Notes name shift/world-column clip; GE search setters (banktags, GE,
    inventory-setups, quest-helper call them).
  - Gotchas: friends list is the smiley side tab (1843,853), not (1843,768) (that is chat-channel/grouping);
    an app restart right after a session often shows "account still logged in": wait ~20 s, Try again.
- **2026-10-17 (client dev gen 21, continued)**, commits fc7d962..4e88ffc (progress: core 67/71, hub 25/32):
  - **Entity Hider / renderable draw listeners** (`native/src/draw_hide.cpp`): the host asks Hooks'
    listeners about every NPC/player each cycle (`Host.applyDrawListeners`); rejected actors get an empty
    model from their vtable slot 0 (getModel) when `drawRenderable` asks (return addresses 0x72dbbc /
    0x72e6a4), which skips draw and picking. NPC hiding verified; player hiding not isolated (the desktop
    player was never in view in the retry; `draw_hide: N npc and M player pointers resolved` logs it).
  - Verified: KPH Tracker (Giant Mole duration + kills/hr), Kitten Tracker (grow-up timer), Mouse
    Highlight (inventory tooltip), Virtual Levels `queueChangedSkill` (2376 <-> 2380 live), Friend Notes
    clean prompt titles, Inventory Setups startup error gone (`getItemDefinition(-1)` = default "null"
    definition). Hunter and Smelting promoted from the server dev's device checks.
  - Test hook: `setprop debug.osrsrl.set "config <group> <key>=<value>"` (ConfigChanged, like the desktop
    config panel; colours as `#AARRGGBB`).
  - Rows added for the six converted core jars without rows (Barrows, Entity Hider, Herbiboar, Minimap,
    Mouse Highlight, Smelting).
  - BOTTOM_LEFT overlays start right of the HUD's bottom-left button (8.5% of the canvas width;
    unverified: the kitten overlay wasn't showing in the last check).
  - API: GE search setters write OC_FIND's results (offsets.md "GE item search results"; no effect
    without a geSearch callback), `getMapDots` placeholders + `applyMapDots` -> the game's minimap dot
    styles (offsets.md "Minimap dot styles"). **Open**: the Minimap plugin never replaced a dot on the
    device (no "map dot ... replaced" log after `npcColor` changes, no exception seen); check whether its
    startUp/replaceMapDots runs (add a log in `getMapDots`), then whether `Native.minimapDot` recolours.
  - Friends list loads normally with the server's resend off (OpenRune 424fc37; "desktop" listed).
  - Gotchas: BlueStacks sometimes crashes the app at startup inside libhoudini (render thread, right after
    the overlay initialises); relaunch. `debug.osrsrl.set` values persist across restarts (clear it).
  - Next: the Minimap open item; player hiding with the desktop player in view (`hideOthers`, it is not
    a friend for the host since `Player.isFriend` is false); remaining `starts` rows (Bank Heatmap, Bank Tag
    Layouts, Spawn Predictor, Better Teleport Menu, Large Logout, Hunter Rumours, Inventory Setups
    highlighting, Metronome, Barrows, Herbiboar); scene hover entries for Mouse Highlight.
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
