package net.runelite.host;

/** Game state read by libosrsrl (native/src/jni_bridge.cpp). Callable from any thread. */
final class Native {
    private Native() {}

    static native int gameState();
    /** Children created by CC_CREATE under component `id` (count; then per index). */
    static native int widgetDynamicCount(int id);
    static native boolean widgetDynamicHidden(int id, int index);
    static native String widgetDynamicText(int id, int index);
    static native int[] widgetDynamicGeometry(int id, int index);
    /** {itemId, quantity} of component id (index < 0) or its CC_CREATE child; null if absent. */
    static native int[] widgetItem(int id, int index);
    /** Widget +0x98 (0xRRGGBB); index -1 = the component itself. */
    static native int widgetColor(int id, int index);
    /** Writes the colour and marks the widget dirty (client thread only; ignored elsewhere). */
    static native void widgetSetColor(int id, int index, int rgb);
    /** Scene objects, all planes: 5 ints each (see jni_bridge.cpp n_scene_objects). Client thread only. */
    static native int[] sceneObjects();
    /** Ground items, all planes: 3 ints each (plane << 16 | x << 8 | y, id, quantity). Client thread only. */
    static native int[] groundItems();
    static native int gameCycle();
    /** Walk destination {x, y} in scene tiles, -1 when there is none. */
    static native int[] walkDestination();
    /** Definition param (kind 0 obj, 1 npc, 2 loc): Integer, String, or null when absent. */
    static native Object typeParam(int kind, int id, int key);
    /** Writes a whitelisted widget field (CC_SET* offset); index >= 0 = CC_CREATE child. */
    static native boolean widgetSetField(int id, int index, int offset, int value);
    /** Re-runs the game's layout for the widget (CC_SETPOSITION/SETSIZE follow-up). */
    static native boolean widgetRevalidate(int id, int index);
    static native int serverTick();
    static native int varp(int id);
    static native int[] varps();
    static native int weight();
    static native int world();
    static native int worldFlags();
    /** {zoom, x, y, width, height} of the 3D viewport in UI canvas pixels, or null. */
    static native int[] viewportZoom();
    /** GPU ViewTransforms block (camera pos @0, view @4, inverse view @20, projection @36, view-projection @52), or null. */
    static native float[] viewTransforms();
    /** One tuple of a DB row column (Integer/Long/String values), or null. Game thread only. */
    static native Object[] dbField(int row, int column, int tuple);
    /** Table of DB row `row`, or -1 if there is no such row. Client thread only. */
    static native int dbRowTable(int row);
    /** Tuples in a row's column (db_getfieldcount), -1 if missing. Client thread only. */
    static native int dbFieldCount(int row, int column);
    static native int runEnergy();
    /** {height, x0, y0, z0, ...}: an NPC's or player's posed model this frame (unrotated); client thread only. */
    static native int[] actorModel(boolean player, int index);
    /** Posed model height (desktop Model.height), -1 if unknown or off the game thread. */
    static native int actorHeight(boolean player, int index);
    /** Font <img=N> table: meta 7 ints per icon {gameIndex (-1 = custom), w, h, ox, oy, fullW, fullH}. */
    static native void setModIcons(int[] meta, byte[][] pixels, int[][] palettes);
    /** {fullW, fullH} per game mod icon, or null (not loaded / off the game thread). */
    static native int[] modIconSizes();
    /** Client varcs (0 / null when unset). */
    /** {x, y, height, orientation, modelHeight, vx, vy, vz...} of a scene object's model this frame, or null. */
    static native int[] objectModel(int plane, int sceneX, int sceneY, int layer, long tag);
    /** {valid, zoom, centreX, centreY, clip x0, y0, x1, y1, far} of the scene renderer's viewport. */
    static native int[] viewport();
    /** {name, targetVerb, op1..opN} of a widget (or its CC_CREATE child), null entries for empty strings. */
    static native String[] widgetStrings(int id, int index);
    static native void widgetSetHidden(int id, int index, boolean hidden);
    static native void widgetSetText(int id, int index, String text);
    static native boolean playSound(int id, int loops, int delay);
    static native int varcInt(int id);
    static native String varcString(int id);
    /** Client-side writes through the game's setters; false off the client thread or for unknown ids. */
    static native boolean setVarbit(int id, int value);
    static native boolean setVarp(int id, int value);
    static native boolean setVarcInt(int id, int value);
    static native boolean setVarcString(int id, String value);
    /** {varp, lsb, msb} of a varbit definition, or null. */
    static native int[] varbitDef(int id);
    /** The game's addChatMessage; client thread only (returns false elsewhere). */
    static native boolean addChatMessage(int type, String name, String text);
    static native int serverVarp(int id);
    /** Game thread only (throws IllegalStateException elsewhere). */
    static native int varbit(int id);
    /** {worldX, worldY, sceneX, sceneY, localX, localY, playerPlane, scenePlane}, or null when not logged in. */
    static native int[] playerPosition();
    static native boolean isClientThread();
    /** Turns the script VM callbacks (Host.onScript) on or off. */
    static native void setScriptEvents(boolean on);
    /** The VM's live stacks (game thread): {int[] intStack (size entries), String[] objectStack}. */
    static native Object[] scriptStacks();
    /** Writes the first `size` ints back to the VM's int stack and sets its size (game thread). */
    static native boolean setScriptIntStack(int[] values, int size);
    /** Arguments of the root script being started (inside Host.onScript phase 2), or null. */
    static native Object[] scriptEventArgs();
    /** {component id, CC_CREATE index or -1} of the running ScriptEvent's source widget, or null. */
    static native int[] scriptEventSource();
    /** CC_CREATE under static layer `id` at `index` (-1 = append); the index, or -1. Game thread. */
    static native int widgetCreateChild(int id, int index, int type);
    /** CC_DELETEALL on static component `id`. Game thread. */
    static native boolean widgetDeleteChildren(int id);
    /** {root scripts, gosubs, returns, events delivered, hooks installed (0/1)}. */
    static native long[] scriptStats();
    /**
     * Runs a client script in the game's VM (args[0] = script id; Integer/String args). Game
     * thread only. Returns {int[] intStack, String[] objectStack} as left by the script.
     */
    static native Object[] runScript(Object[] args);
    /**
     * Item definition (game thread only): {int[] {price, weight, category, stackable, members,
     * tradeable?, linkedNote, noteTemplate, placeholder, placeholderTemplate}, String[] {name,
     * inventory actions x5 (null = none)}}, or null.
     */
    static native Object[] itemDef(int id);
    /** NPC definition (game thread only): {int[] {id, size, combatLevel, minimapVisible, interactable}, String[] {name, actions x5}}, or null. */
    static native Object[] npcDef(int id);
    static native Object[] locDef(int id);
    /** Sidebar (overlay.cpp): replaces the model shown (format in Sidebar). */
    static native void sidebarSetModel(String model);
    /** Taps since the last call as (kind, index) pairs, or null. */
    static native int[] sidebarPollEvents();
    static native boolean sidebarOpen();
    static native void sidebarSetOpen(boolean open);

    /** Sidebar icon pixels (ARGB, row-major) for model nodes of kind I with this id. */
    static native void sidebarIcon(int id, int width, int height, int[] argb);
    /** The virtual Shift toggle under the RL button. */
    static native boolean shiftHeld();
    /** Replaces a chat message's text in the game (game thread). */
    static native boolean messageSetText(int id, String text);
    /** Removes a message (type, id) from the game's chat channel; client thread only. */
    static native boolean chatRemove(int type, int id);
    /** Makes the chatbox rebuild (the game's chat cycle bump); client thread only. */
    static native void chatChanged();
    /** Sprite by id, frame 0: {width, height, ARGB pixels...}, null while not loaded; client thread only. */
    static native int[] sprite(int id);
    /** Writes same-size ARGB pixels into the game's cached sprite; client thread only. */
    static native boolean spriteWrite(int id, int[] pixels, int w, int h);
    /** The game's idle logout timeout (AFKTimeout setting) in minutes, -1 if unknown. */
    static native int afkTimeout();
    /** Sets it (1..35000 minutes); client thread only. */
    static native boolean setAfkTimeout(int minutes);
    /** Plugin menu entries shown under the game's menu at (x, y), width w (UI canvas); null/empty hides. */
    static native void pluginMenu(String[] labels, int x, int y, int w, int canvasHeight);
    /** Closes the open long-press menu (game thread). */
    static native boolean menuClose();
    /** Game thread: {planes, sizeX, sizeY, heights[planes][sx+1][sy+1]..., settings[planes][sx][sy]...}, or null. */
    static native int[] sceneHeights();
    /** Game thread: {int[] {keyType, valueType, defaultInt}, String defaultString, int[] keys, int[] intValues, String[] stringValues}, or null. */
    static native Object[] enumDef(int id);
    /** UI thread: offers a touch to the overlay; true if the overlay takes it (the game won't see it). */
    static native boolean overlayTouch(int action, float x, float y);
    /**
     * NPCs as of the last client cycle: {int[] packed, String[] names}, where packed holds
     * {index, id, localX, localY, worldX, worldY} per NPC.
     */
    /**
     * {int[] packed, String[] names, String[] overhead texts}; 22 ints per NPC: index, id, fine x, fine y,
     * world x, world y, animation, animation frame, orientation, current orientation, interacting
     * (NPC index or player index + 32768, -1 none), overhead cycle, 8 pose animations (idle, idle
     * rotate left/right, walk, walk 180/left/right, run), pose animation, pose frame, health ratio, health
     * scale (-1 without a head bar), 4 hitmark slots {type, amount, end cycle} (-1 empty) = 36. players() is the same with
     * `local` (1/0) in place of id.
     */
    static native Object[] npcs();
    /** Friends (false) or ignores (true): {String[] names, String[] previous names, int[] {world, rank} pairs}; null until loaded. */
    static native Object[] friends(boolean ignores);
    /** FriendSystem +0x150: 0 none, 1 FRIENDLIST_LOADED, 2 list received. */
    static native int friendListState();
    /** The game's settings-changed scene reload (state 30 -> 25); client thread only. */
    static native boolean reloadScene();
    /** Writes an actor's overhead text (null keeps it) and timer (-1 keeps it); client thread only. */
    static native boolean actorSetOverhead(boolean player, int index, String text, int cycle);
    /** Open sub-interfaces: {component, group, modal mode} triples. */
    static native int[] subInterfaces();
    static native boolean widgetExists(int componentId);
    static native boolean widgetHidden(int componentId);
    /** Widget text, or null if the widget's group isn't loaded. */
    static native String widgetText(int componentId);
    static native int rootInterface();
    /** {x, y, width, height, parentId} (x/y relative to the parent), or null if not loaded. */
    static native int[] widgetGeometry(int componentId);
    /** Number of components in a loaded interface group. */
    static native int widgetChildCount(int group);
    /** An Android system property ("" if unset). */
    static native String property(String name);
    /** {x, y, z, pitch, yaw}: camera in local units (z = height), 14-bit angles. */
    static native int[] camera();
    /** {width, height} of the game surface in pixels (0 before the first frame). */
    static native int[] screenSize();
    /** Direct buffer over the native back buffer, sized w*h*4 (RGBA, premultiplied). */
    static native java.nio.ByteBuffer overlayBuffer(int width, int height);
    /** Makes the back buffer the displayed overlay frame; (0, 0) hides overlays. */
    static native void overlayPublish(int width, int height);
    /** Players as of the last cycle: {int[] packed, String[] names}, packed = {index, local, localX, localY, worldX, worldY}. */
    static native Object[] players();
    /** Item ids then quantities (2 x slots) of an inventory as of the last cycle, or null. */
    static native int[] inventory(int id);
    /** Ids of every inventory the client holds. */
    static native int[] inventoryIds();
    /** {current (boosted) levels[25], real levels[25], experience[25]}, indexed by Skill.ordinal(). */
    static native int[] skills();
    /** {name, sender, text} of a captured chat message, or null. */
    static native String[] message(int id);
    /** Game thread: the shown menu's entries, {int[] {kind, hasChildren, raw[10]} per entry, String[] {option, target} per entry}. */
    static native Object[] menuEntries();
    /** Rewrites an open menu entry's option/target (null keeps it); index in menuEntries() order. */
    static native boolean menuSetText(int index, String option, String target);
    /** The entry being selected (valid while MenuOptionClicked is dispatched), same format. */
    static native Object[] clickedMenuEntry();
    static native boolean menuOpen();
    /** {width, height, ARGB pixels...} of an item icon (game thread), or null while not loaded. */
    static native int[] itemSprite(int id, int quantity, int border, int shadow, int textMode, boolean noted);
    /** Widget properties (component, or its CC_CREATE child when index >= 0): scrollX, scrollY,
     *  scrollWidth, scrollHeight, modelZoom, rotationX, rotationY, rotationZ, opacity, modelType,
     *  modelId, animationId; null if absent. */
    static native int[] widgetProps(int id, int index);
    /** {x, y} where the open menu was requested (UI-canvas units), null when closed. */
    static native int[] menuPoint();
    /** From a MenuOptionClicked subscriber: the game won't run the entry. */
    static native void consumeMenuClick();

    /** During a MenuTap event: run this top-level node (Native.menuEntries index) instead of the default. */
    static native void redirectMenuTap(int index);
    /** Game thread: shows the open menu reordered; order[i] = old top-first native index at position i. */
    static native boolean menuReorder(int[] order);
    /** Game thread: builds the long-press menu at (x, y) (canvas units) without showing it. */
    static native boolean menuBuildAt(int x, int y);
    /** Game thread: the next frame ray-picks (x, y) in screen pixels, as a touch down does. */
    static native void pickAt(int x, int y);
    /** Whether the pick asked by pickAt is done (menuBuildAt then sees its hits). */
    static native boolean pickReady();
    /** Game thread: drops a menu built by menuBuildAt (never shown). */
    static native void menuDiscard();
    /** Game thread: runs node `index` (top first) of the current menu as a pick of that row. */
    static native boolean menuSelect(int index);
    /** Asks the next frame to be captured (glReadPixels); takeFrame() returns it once. */
    static native void requestFrame();
    /** {width, height, argb...} of the captured frame, or null while none is ready. */
    static native int[] takeFrame();

    /** True while a MenuTap event is dispatched (menuEntries() then reads the tapped menu). */
    static native boolean menuTapping();
}
