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
    /** {worldX, worldY, sceneX, sceneY, localX, localY}, or null when not logged in. */
    static native int[] playerPosition();
    static native boolean isClientThread();
    /**
     * NPCs as of the last client cycle: {int[] packed, String[] names}, where packed holds
     * {index, id, localX, localY, worldX, worldY} per NPC.
     */
    static native Object[] npcs();
}
