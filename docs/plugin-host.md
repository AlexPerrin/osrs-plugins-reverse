# Plugin host

RuneLite plugins run inside the game process on Android's own runtime (ART). No APK changes
beyond loading libosrsrl are needed.

```
game thread ── Client vtable slot 5 (doCycle) ──> client_thread.cpp ──> events + task queue
                                                        │ JNI (jni_bridge.cpp)
                                                        v
            net.runelite.host.Host (host/src, dexed into re/host/host.jar, embedded in libosrsrl.so)
              ├─ AndroidClient implements net.runelite.api.Client (generated ClientStub; overrides → Native)
              ├─ HostEventBus: honours net.runelite.client.eventbus.@Subscribe
              ├─ HostPluginManager: Guice child injector per plugin, startUp(), register
              ├─ java.awt shim (host/awt): geometry, Color, Font, strokes, Graphics2D; CanvasGraphics draws it
              ├─ OverlayManager/FontManager replacements + RuneLite's own Overlay/components
              └─ slf4j → logcat binding (tags "rl.<Class>")
```

## Build and run

```
host/build.sh                       # re/host/host.jar (runelite-api 1.13.1 + guice/guava + host + shim)
ninja -C re/build                   # embeds host.jar into libosrsrl.so (host_blob.S)
SERVER=10.0.2.2:8080 tools/repack.sh --install
tools/dex-plugin.sh plugin.jar out.jar   # any RuneLite plugin jar → dex + plugin-classes.txt
adb push out.jar /sdcard/Android/data/com.jagex.oldscape.android/files/plugins/
```

At startup the host loads every `.jar` in that directory and starts each class annotated with
`@PluginDescriptor`. `setprop debug.osrsrl.host 0` disables the host. Example plugin:
`host/plugins/example` (Tick Info: logs GameStateChanged, GameTick with position and a varbit,
VarbitChanged).

## What works (2026-10-14, BlueStacks, test server)

| API | Backed by |
|---|---|
| `GameStateChanged`, `getGameState()` | `Client+0x2020`, checked every cycle |
| `GameTick`, `getTickCount()` | SERVER_TICK_END counter `Client+0x3058` |
| `ClientTick`, `getGameCycle()` | doCycle hook, cycle global |
| `VarbitChanged` (varp id + value), `getVarpValue`, `getServerVarpValue` | per-cycle diff of the varp table |
| `getVarbitValue(id)` | the game's own varbit function `0x5d0040` (client thread only) |
| `getLocalPlayer().getWorldLocation()/getLocalLocation()`, `getPlane()` | WorldView scene base + player scene tile; plane from `Client+0xcb78` / `wv+0x18` |
| `ChatMessage` (type, name, sender, text; `MessageNode` copy) | per-cycle poll of the game's next-message-id counter + by-id map |
| `getWidget(group, child)`/`getWidget(id)` (text, hidden), `getTopLevelInterfaceId()`, `WidgetLoaded`/`WidgetClosed` | widget group table (loaded groups only); per-cycle diff of the root + open sub-interfaces |
| Camera getters (`getCameraX/Y/Z/Pitch/Yaw`, Fp variants), viewport = overlay canvas | camera block `Client+0x89488`; `getScale()` not calibrated yet |
| `javax.imageio.ImageIO`, `RescaleOp`, `AffineTransformOp`, `getScaledInstance` (for `ImageUtil`) | BitmapFactory / Bitmap |
| Overlays: `OverlayManager`, `Overlay`, `OverlayPanel` + components, `OverlayUtil`, `FontManager` | `OverlayRenderer` draws on the game thread through `CanvasGraphics` (Graphics2D over android.graphics.Canvas) into a Bitmap at a 720-px-high logical canvas; `overlay.cpp` uploads it as a GL texture and composites it in the swap hook |
| `getTopLevelWorldView()`, `getNpcs()`, `WorldView.npcs()`, `NpcSpawned`/`NpcDespawned` | NPC snapshot taken on the game thread every cycle (`client_thread::npcs()`); NPC id, name, index, world/local location |
| `runScript(Object...)` (Integer/String args), `getIntStack()`/`getIntStackSize()`, `getObjectStack()`/`getObjectStackSize()` | the game's own `runScriptEvent` (`0x4df54c`) on the game thread with a ScriptEvent we build; stacks are copied when the script returns (`Quest.getState` works) |
| `getItemDefinition(id)` (name, price, stackable, members, notes, placeholders, inventory actions, weight), `ItemManager.getItemComposition/canonicalize` | the game's obj definition cache (`0x63bfe8`); cached per id (LRU 2048). `isTradeable` uses an unverified flag |
| NPC `getConvexHull()` (box over the footprint, logical height ~200), `getCanvasImageLocation/TextLocation/SpriteLocation/TilePoly`, `getWorldArea`, `getInteracting()` null; `ModelOutlineRenderer` replacement strokes hulls/click boxes (QH NPC highlight) | `Hulls.java`, `host/src/net/runelite/client/ui/overlay/outline` |
| `getNpcDefinition(id)`, `NPC.getComposition()/getTransformedComposition()/getCombatLevel()` (name, size, combat level, actions, minimap/interactable) | the game's NpcType cache (`0x63633c`); multi-NPC transforms not resolved |
| `getEnum(id)` (keys, int/string values, defaults) | the game's EnumType cache (`0x65cac8`) |
| `WorldView.getScene()`: tiles with heights/settings (extended settings 184×184), game/wall/decorative/ground objects, ground items + `ItemLayer`; `GameObject/WallObject/DecorativeObject/GroundObjectSpawned/Despawned`, `ItemSpawned/Despawned`; `WorldView.getTileHeight` | `game::scene_objects()/ground_items()` read once per server tick (`SceneObjects.java` diffs and posts events). Click boxes/hulls are boxes over the footprint (models aren't read); ground item timers/ownership not read |
| Sidebar: ClientToolbar buttons + the selected plugin panel, usable by touch | `Sidebar.java` (model + tap dispatch + touch router) and `overlay.cpp` (ImGui window); see "Sidebar" below |
| Inert stand-ins so plugin code runs: models/`RuneLiteObject`s (`ModelDataBag`, `ModelBag`), world map (`WorldMapBag`), hint arrows (remembered only), sprites (blank 16×16), `getMinimapZoom()` 4, `getMinimapLocation()` null, `findWorldViewFromWorldPoint` = top level | nothing is drawn for them |
| Menus: `getMenu()`, `getMenuEntries()` (RuneLite order, top entry last), `isMenuOpen()`, `createMenuEntry` (host-side only), `MenuOpened`, `MenuOptionClicked` (consumable; also fired by plain taps); entries carry MenuAction/identifier/param0/param1, `getNpc()`, `getWidget()` for NPC, loc, walk, widget and cancel entries | `game::read_menu` over the menu model; select hooked through the menu vtable slot 1 (verified on device 2026-10-15) |
| `Widget.setTextColor/getTextColor` (components and CC_CREATE children), written to the game widget | Widget `+0x98` plus the IF_SETCOLOUR dirty flag; QH's dialogue-option highlight (verified 2026-10-15). Other widget setters (text, hidden, ...) stay Java-side only |
| Actor state for NPCs and players: `getAnimation/getAnimationFrame`, `getOrientation/getCurrentOrientation`, `getInteracting/isInteracting` (NPC or player), `getOverheadText/getOverheadCycle` | Actor offsets in docs/offsets.md ("Actor"), read in the per-cycle snapshots. No AnimationChanged/InteractingChanged/OverheadTextChanged events yet. Unverified on device |
| `getMinimapLocation` (NPCs, players), `Perspective.localToMinimap`, `getCameraFocusEntity` | the minimap draw area is 601:34 (desktop minimap component ids are routed to it); zoom 4. Unverified |
| Scene overlays (ABOVE_SCENE, UNDER_WIDGETS) are clipped out of the opaque UI panels (601:50 tab stones, 601:36 minimap, 601:111 open side panel) | `OverlayRenderer.uiExclusions`; `setprop debug.osrsrl.clipui "n,m"` adds root children |
| `macroExpand(text)` | identity (CS1 macros aren't expanded) |
| `isClientThread()`, `Host.invoke` | game-thread id from the hook; queue drained after each cycle |

Every other `Client`/`Player` method throws `UnsupportedOperationException` with its name, so
plugin logs show what to implement next.

## Sidebar

An "RL" button at the left edge of the screen opens an ImGui window (overlay.cpp) with one tab
per `NavigationButton` and the selected panel's Swing tree, flattened by `Sidebar.java` every 10
cycles into lines `kind\tdepth\tcolor\ttext` (L label, B button, C checkbox, T text component,
X combo box, H panel with mouse listeners). Taps come back as (kind, index) and are dispatched
on the game thread: buttons fire their ActionListeners (toggles flip first), H panels get
pressed/released/clicked MouseEvents. Drag scrolls. Touches are routed by a `Window.Callback`
proxy on the game Activity: a touch that starts inside the sidebar (or the RL button) goes to
ImGui and is hidden from the game. The headless Swing honours what panels need for this:
constructor text/icon/layout, CardLayout.show, visibility, and `SwingUtilities.invokeLater`
(queued, run on the game thread, which acts as the EDT).

Test hooks: `setprop debug.osrsrl.sbclick "<text>"` taps the first button with that text or
following a label with that text (e.g. `"Demon Slayer"` starts that quest in Quest Helper);
`setprop debug.osrsrl.sbdump 1` writes the model to `files/sidebar-model.txt` on change.
Text fields (`JTextField`, kind E) show as an input box; a tap opens an Android dialog with an
EditText (soft keyboard), and OK sets the text on the game thread and fires the field's
DocumentListeners and ActionListeners (Quest Helper's search filters the quest list, verified
2026-10-15). Test hook: `setprop debug.osrsrl.sbtext "<text>"` types into the first text field
(`-` clears it). Not yet: combo box popups, icons, label colours.

## Gaps, in rough order

- Projection (camera, `Perspective`) so DYNAMIC overlays can follow the scene; NPC
  composition/animation/overhead text; other players; scene objects; widgets; items and
  inventories; menu entries; writing chat messages back.
- `VarbitChanged` only has the varp id; RuneLite also posts one per affected varbit (needs the
  varbit → varp index from the cache).
- Overlays: no touch input, saved positions or menu entries yet; plugin canvas size is fixed
  to 720 px height (the game's own UI scale is not matched).
- ConfigManager, OverlayManager, ClientThread, ItemManager, etc. bound in the root injector.
  RuneLite's own EventBus can't be reused (needs `MethodHandles.privateLookupIn`, missing on API 28).
- Script hooks, menu manager, inline hooks (needed for events without an obvious counter).
