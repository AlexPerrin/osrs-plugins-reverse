# Client work: handoff notes

> Latest state: see the gen 9 entry (2026-10-16) at the end of "Where things stand".

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
