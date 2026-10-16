package net.runelite.host;

/** Game state read by libosrsrl (native/src/jni_bridge.cpp). Callable from any thread. */
final class Native {
    private Native() {}

    static native int gameState();
    static native int gameCycle();
    static native int serverTick();
    static native int varp(int id);
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
}
