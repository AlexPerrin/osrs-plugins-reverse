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
    static native int serverTick();
    static native int varp(int id);
    static native int[] varps();
    static native int weight();
    static native int runEnergy();
    /** {height, x0, y0, z0, ...}: an NPC's or player's posed model this frame (unrotated); client thread only. */
    static native int[] actorModel(boolean player, int index);
    /** Client varcs (0 / null when unset). */
    /** {x, y, height, orientation, modelHeight, vx, vy, vz...} of a scene object's model this frame, or null. */
    static native int[] objectModel(int plane, int sceneX, int sceneY, int layer, long tag);
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
     * rotate left/right, walk, walk 180/left/right, run), pose animation, pose frame. players() is the same with
     * `local` (1/0) in place of id.
     */
    static native Object[] npcs();
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
    /** {current (boosted) levels[25], real levels[25], experience[25]}, indexed by Skill.ordinal(). */
    static native int[] skills();
    /** {name, sender, text} of a captured chat message, or null. */
    static native String[] message(int id);
    /** Game thread: the shown menu's entries, {int[] {kind, hasChildren, raw[10]} per entry, String[] {option, target} per entry}. */
    static native Object[] menuEntries();
    /** The entry being selected (valid while MenuOptionClicked is dispatched), same format. */
    static native Object[] clickedMenuEntry();
    static native boolean menuOpen();
    /** Widget properties (component, or its CC_CREATE child when index >= 0): scrollX, scrollY,
     *  scrollWidth, scrollHeight, modelZoom, rotationX, rotationY, rotationZ, opacity, modelType,
     *  modelId, animationId; null if absent. */
    static native int[] widgetProps(int id, int index);
    /** {x, y} where the open menu was requested (UI-canvas units), null when closed. */
    static native int[] menuPoint();
    /** From a MenuOptionClicked subscriber: the game won't run the entry. */
    static native void consumeMenuClick();
}
