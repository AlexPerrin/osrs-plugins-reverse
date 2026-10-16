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

## Core plugin status

Results of on-device runs (OpenRune, BlueStacks). `runs` = does its job on the device; `starts` =
startUp OK and no runtime exceptions seen, but the feature wasn't checked or is incomplete;
`throws` = fails to start; `untested`. Batch test 2026-10-16: `EXTRA="<names>" tools/plugin-test.sh`
with jars from `tools/dex-plugin.sh --core <package> re/host/plugins-extra/<package>.jar`.
Platform gaps: `host/tools/api_gaps.py classes*.dex.txt` (dexdump of host.jar) lists Java/Android
methods host.jar calls that API 28 lacks. RuneLite's `Text` and `WildcardMatcher` are replaced for
that reason (Java 9 `Matcher` methods). Left (2026-10-16): `Files.readString/writeString`,
`privateLookupIn`, `Runtime.Version`, `Collectors.toUnmodifiableMap` (Fairy Ring), guava's `ClassValue`
use, javax.sound / java.lang.management / ProcessHandle (desktop-only paths).

| Plugin | Status | Notes |
|---|---|---|
| Quest Helper (hub) | runs | Cook's Assistant, Sheep Shearer, Romeo & Juliet end to end |
| Ground Items | runs | labels, prices, menu recolouring |
| Opponent Information | runs | name + health over the target (NPC max HP over okhttp) |
| Boosts Information | runs | boosted stats overlay and boost InfoBox (InfoBoxes render, verified 2026-10-16) |
| XP Drop | runs | drop colours via ScriptPreFired (script 996); `hideSkillIcons` hides the skill icons (nulled child slots are hidden in the game and re-hidden each cycle for 3 s, as the drop script unhides them; verified 2026-10-16) |
| Status Bars | runs | HP/prayer bars beside the side panel (161:73 routed to 601:111) |
| Tile Indicators | runs | destination tile drawn while walking |
| Attack Styles | runs | style overlay ("Accurate") below the mobile chatbox from login (varp 43 via the ring-overflow resync, verified 2026-10-16) |
| Chat Filter | runs | game chat censored ("You get some oak ****" with `chatfilter.filterGameChat=true`, `filteredWords=Logs`, verified 2026-10-16): chatFilterCheck is raised once per new message; the game shows the new text when the chat next rebuilds; duplicate collapsing doesn't work |
| Chat History | starts | "Game: Clear history" (long-press a chat tab, plugin strip) removes the lines from the game's chat channels (verified once, 2026-10-16), but they came back when the chatbox next rebuilt (the All tab likely reads another list); the mobile game keeps chat across logout itself, so the restore isn't needed |
| Chat Timestamps | runs | "[15:04]" (local time, plugin format) before each chat line, after the game's own UTC time (chatMessageBuilding raised once per new message, verified 2026-10-16); the newest line shows it once the chat next rebuilds |
| Combat Level | runs | precise level in the combat tab ("Combat Lvl: 126.1", verified 2026-10-16) |
| Cannon | runs | green "30" ball count drawn on the placed cannon after Set-up + Fire on goblins, one "Your cannon is out of ammo!" notification when empty (verified 2026-10-16; the Notifier no longer posts a ChatMessage, which had made the plugin re-notify on its own line). Members items need the client's members flag: a members world started from jav_config skips setWorld, so the host applies the world flags through the game's setMembersWorld `0x642f14` when they disagree (Set-up showed without the world switcher on 2026-10-16, though the host's sync didn't need to run that time) |
| Daily Task Indicator | runs | "You have battlestaves waiting to be collected from Zaff." on login with `::varbit 4479 1` (verified 2026-10-16) |
| Emojis | starts | cache dir now under the app's files dir (user.home); emoji rendering in chat not possible (game chat) |
| Examine | runs | "Price of Salmon: GE average 30 HA value 18" after Examine on an inventory item, one price line after OpenRune dropped its own (ChatMessageManager.queue → game chat, verified 2026-10-16). GE price is the store value (no price API) |
| Ground Markers | runs | virtual Shift on + long-press a tile: "Mark Tile" in the plugin strip under the game menu; the tile is drawn (verified 2026-10-16). Color Tile > Pick opens the Android colour dialog and recolours the tile (verified 2026-10-16) |
| Implings | starts | `implings.showbaby=HIGHLIGHT`; OpenRune's `::npc` spawns (implings and goblins alike) reply "Spawned npc" but never reach the client (server gap, confirmed 2026-10-16) |
| Interact Highlight | runs | outline on the hovered NPC (long-press) and red outline on the attacked goblin (long-press + Attack, verified 2026-10-16) |
| Inventory Tags | runs | green outline on a tagged pot; Shift + long-press an item: "Inventory tag" > Pick sets the tag (verified 2026-10-16; outlines from the game's createItemSprite) |
| Item Charges | runs | "8" drawn on a Ring of dueling(8) in the inventory (verified 2026-10-16) |
| Item Stats | runs | "+0 (99) Hitpoints" tooltip while a finger rests on a lobster (hover entry: getMenuEntries with no menu open = the inventory slot under the touch point, verified 2026-10-16) |
| Logout Timer | runs | logged out after 1 min idle with `logouttimer.idleTimeout=1` (setIdleTimeout writes the game's AFKTimeout setting, whole minutes, re-applied at login; the game saves it, so reset it to 5 after tests; verified 2026-10-16 after OpenRune's IDLE handling) |
| NPC Indicators | runs | hull highlights + minimap dots for `npcindicators.npcToHighlight=Goblin` (after the WildcardMatcher replacement); the left button column (601:41/40) is now clipped (unverified) |
| Object Markers | runs | Shift + long-press an object: "Mark object"; hull drawn on the tree (verified 2026-10-16). Submenus not shown |
| Poison | runs | HP orb heart turns green when poisoned (`::varp 102 6`) and back to red when cured: Client.getSpriteOverrides writes into the game's cached sprite 1067 (verified 2026-10-16). The infobox (`poison.showInfoboxes`, off by default) threw on BufferedImage.copyData; fixed in the shim, unverified |
| Prayer | runs | prayer orb tooltip (time remaining, prayer bonus) on a tap of the orb (verified 2026-10-16); dose indicator not checked |
| Random Events | starts | |
| Regeneration Meter | runs | HP ring on the HP orb (verified with `regenmeter.showWhenNoChange`); by default it draws only below full HP or spec |
| Run Energy | runs | orb tooltip (weight, run time, 100% in) on a tap of the run orb (touch-as-mouse + TooltipOverlay, verified 2026-10-16) |
| Timers & Buffs | runs | stamina timer InfoBox counting down after `::varbit 25 1` + `::varbit 24 20` (verified 2026-10-16); the InfoBox shows the stamina potion icon (ItemManager.getImage → the game's createItemSprite, verified 2026-10-16) |
| Woodcutting | runs | session panel (logs cut 6, 900/hr) while chopping an oak west of Lumbridge (verified 2026-10-16; TOP_LEFT overlays now stack below the chatbox) |
| XP Tracker | runs | sidebar panel: gained XP, XP/hr, levels per skill after combat (verified 2026-10-16; some rows show twice in the flattened sidebar) |
| Agility | runs | Gnome Stronghold log balance highlighted green (verified 2026-10-16; needs XP Tracker's module, installed as RuneLite does) |
| Chat Notifications | runs | highlight words: "Spawned" shown red in game messages with `chatnotification.highlightWordsString=Spawned` (the node's RuneLite-format message is applied after ChatMessage, verified 2026-10-16); notifications not checked |
| Fishing | runs | fishing spot tiles + fish icons by the Lumbridge river; session panel "Caught fish: 14, Fish/hr: 840" while lure fishing trout/salmon (verified 2026-10-16) |
| Cooking | runs | session panel "Cooked: 11 (660/hr), Burnt: 0" while cooking salmon on the Lumbridge range (verified 2026-10-16) |
| Idle Notifier | runs | "[RuneLite] You are now idle!" in the game chat after mining/fishing stops (Notifier → game chat, verified 2026-10-16) |
| Mining | runs | "Mining" / "NOT mining" session panel below the chatbox while mining copper (after OpenRune's OSRS wording fix; TOP_LEFT placement verified 2026-10-16) |
| Player Indicators | starts | needed protobuf-javalite + RuneLite http-api (PartyService) |
| Special Attack Counter | starts | the dragon dagger spec works on OpenRune (energy 100 -> 75, 2026-10-16), but the plugin only counts its SpecialWeapon list (dragon warhammer, arclight, darklight, BGS, bone dagger, ...), not the dragon dagger; needs one of those with a spec server-side |
| XP Globes | runs | attack/HP globes over the chat tabs after combat |
| Bank | starts | `::openbank` opens the bank (2026-10-16); the bank value in the title isn't shown: the mobile bank scripts don't call runelite_callback `setBankTitle` |
| Notes | runs | tap the note in the sidebar: multi-line Android prompt; OK saves it (`notes.notesData=Buy feathers`, verified 2026-10-16). Headless documents hold their text, and the prompt raises focusLost, where Notes saves |
| Slayer | starts | task InfoBox drawn (enchanted gem "?" icon from the game's sprites) with no task |
| NPC Aggression Timer | starts | not checked |
| Mouse Tooltips | runs | "Eat Lobster" tooltip while a finger rests on an inventory item and "Remove Dragon dagger" on the worn weapon slot (hover entries, verified 2026-10-16) |
| Loot Tracker | runs | sidebar: "Goblin (lvl-2) 2 gp, Total count: 1" after killing a goblin (verified 2026-10-16); local only (same-name SessionManager without a runelite.net session) |
| Hiscore | runs | sidebar lookup of "Lynx Titan" shows the levels from the public hiscores (okhttp, off the game thread; verified 2026-10-16). The player-menu "Lookup" entry is host-side only |
| Skill Calculator | runs | skill icons drawn as image buttons in the sidebar; picking Woodcutting fills current/target level and XP and lists the log actions (verified 2026-10-16) |

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
| `getWorld()` (verified: 1 old server, 255 OpenRune), `getWorldType()` from the world flags; `getDBTableField` (verified 2026-10-16 on OpenRune: rows 0-5 col 0 give 123, 133, "Oh Rats!", ...); null friends chat/clan getters, sprite override maps | `Client+0x3174` / `+0x2f64` (CS2 map_world / worldflags); db_getfield 7502 in `0x4cd7f4` |
| Camera getters (`getCameraX/Y/Z/Pitch/Yaw`, Fp variants), `getScale()` and viewport offsets/size from the game's viewport zoom (follows zoom settings; verified on both servers) | camera block `Client+0x89488`; zoom `Client+0x46d4` * height / 334, viewport `+0x46e0..+0x46ec` |
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
| Scene overlays (ABOVE_SCENE, UNDER_WIDGETS) are clipped out of the opaque UI panels (601:50 tab stones, 601:36 minimap, 601:111 open side panel) and out of modal interfaces (IF_OPENSUB mode 0: root layer, graphics, fills; e.g. the quest-complete scroll) | `OverlayRenderer.uiExclusions`; `setprop debug.osrsrl.clipui "n,m"` adds root children, `cliplog 1` logs the rectangles. Verified 2026-10-16 (side panel, quest scroll; an int overflow had disabled the clip until then) |
| `getObjectDefinition(id)` (name, actions, size, varbit/varp, impostor ids; `getImpostor()` resolves the multiloc from the current value) | the game's LocType cache (`0x62e5ec`). QH ObjectStep highlights verified 2026-10-16 (Sheep Shearer) |
| `Menu`/`Client.getMenuX/Y/Width/Height`; overlays are clipped out of the open long-press menu | press point at MenuManager+0x2a8 plus the measured layout (docs/offsets.md) |
| Debug hooks: `debug.osrsrl.where <loc id>` logs an object's world position and click-box centre in canvas and screen pixels (find adb tap targets); `debug.osrsrl.widgetlog 1` logs WidgetLoaded/Closed; `debug.osrsrl.probe <path>` (see Probe.java) | `Probe.java` |
| `macroExpand(text)` | identity (CS1 macros aren't expanded) |
| `GameTick` is not posted while the game state is LOADING (as on desktop); QH's one-shot quest list load ran during OpenRune's login rebuild and left the list empty (verified on OpenRune) | Host.java |
| Widget `getName`, `getTargetVerb`, `getActions`, `getSpriteId`, `getSpriteTiling`, `getBorderType`, `isFlippedHorizontally/Vertically`, `isFilled`, `getTextShadowed`, `getX/YTextAlignment`, `getLineHeight`, `getFontId`, `getOriginalX/Y/Width/Height`, position/size modes, `getNoClickThrough/NoScrollThrough` | Widget fields from the CC_SET* handlers (docs/offsets.md "Widget fields (CC_SET)"); dump hook `setprop debug.osrsrl.wdump <group>`. Verified 2026-10-16 on 601/162 (ops "Logout, World Switcher", sprites, font 494 centred + shadow on the chat tabs, position/size modes match the laid-out geometry). `getType()` still returns 0. `setHidden`/`setText` (CC_SETHIDE field `+0x78`, string assign `0x3f1498` on `+0x158`, area marked dirty): unverified |
| Client-side setters: `setVarbit`, `setVarcIntValue`, `setVarcStrValue`, `getVarbit(id)` (VarbitComposition), `setVarbitValue(int[], …)` + `queueChangedVarp` (writes the edited `getVarps()` array entry back) | the script VM's own setters (docs/offsets.md "Client-side setters"); test hook `setprop debug.osrsrl.set 'varbit|varp|varc|varcstr <id> <value>'`. Verified 2026-10-16 (varbit 8119 0/1 via varp 1737 bit 31, varp 1000, varc 1000, varcstr 1001) |
| `playSoundEffect(...)` | the SYNTH_SOUND path `0x4ae508(Client +0x2610, id, loops, delay)` (volume/position ignored). Unverified |
| Scene object click boxes / hulls (`getClickbox`, `getConvexHull`) from the object's model: GameObject (with orientation), walls (both renderables), wall decorations (offsets), ground decorations | `game::object_model` (docs/offsets.md "Scene object models"); footprint boxes when the model can't be read or with `debug.osrsrl.modelhull 0`. GameObject verified (bush 34737: model at its local position and tile height, 17 vertices, click-box centre on the bush); walls/decorations unverified |
| NPC/player spawn events are posted once the actor has a position (a new Npc is at fine 0,0 for a cycle) | `diff_npcs/diff_players` in client_thread.cpp (verified: QH NpcStep after a teleport) |
| `MenuEntryAdded` (one per entry when a menu opens, bottom entry first, before `MenuOpened`); option/target edits made in it or in `MenuOpened` are written into the game's open menu | `game::menu_set_text` (arena string append `0x48e860`). Verified 2026-10-16 with Ground Items: recoloured item names in the long-press menu, picking a rewritten Take still works, plain taps unaffected. Ground item targets drop the game's " (n)" stack suffix, as RuneLite's do |
| `TileItem.getVisibleTime/getDespawnTime/getOwnership/isPrivate` | Obj `+0x48..+0x54` (unverified against the server) |
| `ItemManager.getItemPrice` = store value (coins 1, platinum 1000); no price API on the device | host ItemManager replacement |
| `Notifier` replacement: notifications become `[RuneLite] ...` game messages | `host/src/net/runelite/client/Notifier.java` |
| `Widget.getType()` (+0x28) | verified 2026-10-16 (text widgets 4, layers 0, rectangles 3, graphics 5); `setText`/`setHidden` verified the same day (chat tab label changed, "Public" hidden) |
| `getIntValue/getStringValue` on item, NPC and object compositions (definition params) | `game::type_param` (docs/offsets.md "Definition params"); a missing param gives 0 / "" (ParamType defaults not read). Unverified; test hook `setprop debug.osrsrl.set 'iparam <item> <param>'` |
| `getMapRegions`, `isPrayerActive` (prayer varbits; verified, Protect from Melee), `getLocalDestinationLocation` (the map flag `*(Client+0x40e970)`; verified on OpenRune), `getMouseIdleTicks/getKeyboardIdleTicks` (since the last touch/key event at the touch router), `getRealDimensions/getStretchedDimensions` (UI canvas), `isKeyPressed` false, dragged/selected widgets null, `refreshChat` no-op | unverified |
| Core RuneLite plugins: `tools/dex-plugin.sh --core <package> out.jar` writes a jar holding only plugin-classes.txt (the classes are already in host.jar). **Ground Items runs** (2026-10-16): labels with prices over ground items, menu recolouring | Coins are in its default hidden list, so they get no label |
| Widget field setters written into the game (`setOriginalX/Y/Width/Height`, position/size modes, sprite, opacity, font, filled, border, flips, text alignment/shadow, no click/scroll through) and `revalidate()` through the game's layout (`0x44a788`, `0x45e304`, `0x45ebf4` as CC_SETPOSITION runs them); `getIndex()` | verified 2026-10-16 (setOriginalX + revalidate moved a chat tab label) |
| `createScriptEventBuilder` (magic args resolved in Java, run through runScript), `getMessages` (nodes seen since start) | unverified |
| NPC `getId()/getName()` follow the current multi-NPC transform (-1/null when it transforms to nothing) and such an NPC has no hull, as RuneLite | unverified |
| VarbitChanged works with caches of any varp count (OpenRune: 65660) | verified 2026-10-16 on OpenRune |
| Hitsplats: `HitsplatApplied` (new hitmark slots per snapshot), `Actor.getHealthRatio/getHealthScale` (first head bar's last update, bar type width) | Actor `+0x330` hitmark vector, `+0x388` head bar list (docs/offsets.md "Actor"). Verified 2026-10-16 on OpenRune: goblin `Hitsplat(16, 5, end)`, health 0/30 |
| `getFriendContainer`, `getIgnoreContainer`, `isFriended` (names, previous names, world) | FriendSystem `*(Client+0x46c0)`; list state read as 2 on OpenRune, members unverified (empty lists) |
| `getComponentTable` (open sub-interfaces: group, modal mode) | sub-interface map, node `+8` = mode; verified (601's 25 overlays, mode 1) |
| `setGameState(LOADING)` (others ignored) | the game's settings-changed reload `0x429900`; verified: 30 → 25 → 30, session continues |
| `Actor.setOverheadText/setOverheadCycle` written into the game (string assign on `+0x2d8`, timer `+0x2f4`) | verified 2026-10-16 (`setprop debug.osrsrl.set 'ohead <npc id> <text>'`) |
| **Opponent Information** (core) runs: name + health bar/`3/5` over goblins (NPCManager max health over okhttp) | okhttp 3.14.9 + okio 1.17.2 in host.jar, `OkHttpClient` and `@Named("runelite.static.base")` bound in Host. Verified 2026-10-16 on OpenRune |
| `VarbitChanged` also for server-sent varps whose value didn't change (the client's 32-entry changed-varp ring; a login burst can overflow it) and one event per changed varbit with `varbitId` set (varp → varbit index built once from the varbit definitions: 19404 varbits in 23 ms on OpenRune) | verified 2026-10-16 (varp 43 on an attack style change, fps 30) |
| `ScriptPreFired` (proc entry with its arguments on the live int/object stacks; root scripts with `getScriptEvent().getArguments()`) and `ScriptPostFired` (proc return with results on the stacks; root script end). While one is dispatched, `getIntStack/Size` and `getObjectStack/Size` read the VM's live stacks and int stack edits are written back; `runScript` from inside one is deferred to the next cycle | inline hooks on the CS2 VM (`native/src/script_hooks.cpp`, `inline_hook.cpp`; docs/offsets.md "Script VM hooks"). Only delivered to Java while something subscribes. Verified 2026-10-16 on OpenRune: balanced gosub/return for all scripts, 30 fps with XP Drop subscribed, XP Drop recolours drops (script 996). `setprop debug.osrsrl.scripts 0` disables, `2` logs every script (very verbose) |
| **XP Drop** (core) runs: drop colour from config (standard/prayer colours) applied inside script 996. `hideSkillIcons` works: it nulls entries of `Widget.getChildren()` (a copy here); after the event the host hides those game children and re-hides them each cycle for 150 cycles (the drop script unhides them) | verified 2026-10-16 (red drops with `xpdrop.standardColor=ffff0000`; icons gone with `xpdrop.hideSkillIcons=true`) |
| Other core plugins tried 2026-10-16 (jars in `re/host/plugins-extra/`, not pushed by default; `EXTRA="xpdrop statusbars" tools/plugin-test.sh`): **Boosts Information** runs (strength 111 after a potion, compact overlay top-left); **Status Bars** runs (its viewport anchor 161:73 is routed to the side panel 601:111) | `getServerVarbitValue`, `getIdleTimeout/setIdleTimeout`, `getMouseLastPressedMillis` added (unverified) |
| **Plugin menu entries** (`createMenuEntry`, top level): drawn as a strip under the game's long-press menu after `MenuOpened`; a tap runs the entry's `onClick`, posts `MenuOptionClicked` and closes the game menu. Submenus (`createSubMenu`) are not shown | `AndroidMenu.publishPluginEntries/tapped`, `overlay.cpp plugin_menu_frame`; close = the close branch of `0x599f68` (`0x514f48` model, `0x507398` display, shown = 0). Verified 2026-10-16 (Ground Markers, Object Markers) |
| **Virtual Shift**: a "Shift" toggle under the RL button; `isKeyPressed(KC_SHIFT)` follows it (orange = held). Plain taps and long-presses are unchanged either way | `Native.shiftHeld`; verified 2026-10-16 |
| `WorldView.getSelectedSceneTile()`: the tile of the open menu's "Walk here" entry, else the tile under the long-press point | verified (Ground Markers) |
| `getMouseCanvasPosition()` = the touch point while a finger is down and for 3 s after it lifts (else -1,-1); RuneLite's `TooltipOverlay` is registered, tooltips draw last | `Sidebar.hoverPoint`; verified (Run Energy orb tooltip) |
| VarbitChanged after a changed-varp ring overflow (login burst): every varp below 8192 plus every non-zero one is posted once | `diff_varps`; verified (Attack Styles at login; ~8k events, no visible hitch) |
| `getModIcons/setModIcons/createIndexedSprite` host-side (32 blank slots; the game's chat doesn't draw plugin icons) | fixes ChatIconManager.loadRankIcons NPE; unverified |
| `Widget.getChildren()` slot nulling hides that game child after the event (XP Drop `hideSkillIcons`) | `AndroidWidget.applyChildEdits`; unverified |
| Per-cycle caches for camera, viewport, canvas size, player position and tile heights (`Host.cycleStamp`) | Perspective reads them per vertex: with NPC Indicators hulls on ~40 goblins the JNI reads took the game to 0 fps (taps stopped working); 30 fps after (verified 2026-10-16, all 41 plugins loaded) |
| Scene overlays are also clipped out of small sprite widgets (type 5, area < 1/12 canvas) of the root and its overlay sub-interfaces: mobile button columns, orbs, chat tabs | `OverlayRenderer.uiExclusions` (looked right with NPC Indicators on 2026-10-16) |
| `user.home` = the app's files dir, so `RuneLite.RUNELITE_DIR/CACHE_DIR` are writable | Host.start |
| `ChatMessageManager.queue` → `Client.addChatMessage` (RuneLite colour placeholders mapped to the transparent-chatbox defaults; CONSOLE sent as GAMEMESSAGE); `MessageNode.setValue` rewrites the game's message text (Message +0xa0, string assign `0x3f1498`) | queue verified (Examine); setValue unverified (shown on the next chatbox redraw?) |
| Plugin submenus (`createSubMenu`): a tapped parent replaces the strip with its entries (marked "  >") | verified 2026-10-16 (Ground Markers "Color Tile" → Pick, Reset all); "Pick" opens an Android colour dialog (same-name `RuneliteColorPicker`/`ColorPickerManager`, `host/.../ColorPrompt.java`: hex field, preview, 24 swatches); OK calls onColorChange + onClose on the game thread (verified 2026-10-16: tile recoloured red) |
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
(`-` clears it). Combo boxes open an Android picker (verified 2026-10-16: QH's Difficulty filter). Buttons with no
ActionListeners get the mouse events instead, so ConfigPanel's colour buttons open the same Android
colour dialog (unverified in the config panel). Not yet: icons, label colours.

Hover: with no menu open, `getMenuEntries()` holds one entry for the inventory slot under the
touch point (while the finger is down and 3 s after; first op, CC_OP, param0 = slot, param1 = 149:0,
item id), computed once per cycle (`AndroidMenu.hoverEntry`). Item Stats' tooltip uses it
(verified 2026-10-16). Equipment and bank slots are not mapped yet.

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
