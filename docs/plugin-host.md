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
| `isClientThread()`, `Host.invoke` | game-thread id from the hook; queue drained after each cycle |

Every other `Client`/`Player` method throws `UnsupportedOperationException` with its name, so
plugin logs show what to implement next.

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
