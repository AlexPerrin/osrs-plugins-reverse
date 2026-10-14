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
              ├─ java.awt shim (host/awt): geometry, Color, image/event shells
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
| `getLocalPlayer().getWorldLocation()/getLocalLocation()` | WorldView scene base + player scene tile (plane still 0) |
| `getTopLevelWorldView()`, `getNpcs()`, `WorldView.npcs()`, `NpcSpawned`/`NpcDespawned` | NPC snapshot taken on the game thread every cycle (`client_thread::npcs()`); NPC id, name, index, world/local location |
| `isClientThread()`, `Host.invoke` | game-thread id from the hook; queue drained after each cycle |

Every other `Client`/`Player` method throws `UnsupportedOperationException` with its name, so
plugin logs show what to implement next.

## Gaps, in rough order

- Plane; NPC composition/animation/health; other players; scene objects; widgets; items and
  inventories; chat messages; menu entries.
- `VarbitChanged` only has the varp id; RuneLite also posts one per affected varbit (needs the
  varbit → varp index from the cache).
- Overlays: Graphics2D → Android Canvas → GL texture drawn in the eglSwapBuffers hook. Overlay
  rendering must run on the game thread (desktop RuneLite draws on the client thread).
- ConfigManager, OverlayManager, ClientThread, ItemManager, etc. bound in the root injector.
  RuneLite's own EventBus can't be reused (needs `MethodHandles.privateLookupIn`, missing on API 28).
- Script hooks, menu manager, inline hooks (needed for events without an obvious counter).
