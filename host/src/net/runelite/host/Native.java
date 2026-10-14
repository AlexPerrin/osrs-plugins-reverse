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
     * NPCs as of the last client cycle: {int[] packed, String[] names}, where packed holds
     * {index, id, localX, localY, worldX, worldY} per NPC.
     */
    static native Object[] npcs();
    static native boolean widgetExists(int componentId);
    static native boolean widgetHidden(int componentId);
    /** Widget text, or null if the widget's group isn't loaded. */
    static native String widgetText(int componentId);
    static native int rootInterface();
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
    /** {name, sender, text} of a captured chat message, or null. */
    static native String[] message(int id);
}
