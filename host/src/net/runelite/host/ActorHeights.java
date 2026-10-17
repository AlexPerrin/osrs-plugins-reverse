package net.runelite.host;

import java.util.HashMap;
import java.util.Map;

/**
 * Actor.getLogicalHeight as desktop: the posed model's height. Reading it poses a model, so
 * each actor's value is kept for a few cycles; off the game thread the last value is used.
 */
final class ActorHeights {
    private static final int KEEP_CYCLES = 10;
    private static final Map<Integer, int[]> cache = new HashMap<>();  // key -> {height, stamp}

    private ActorHeights() {}

    static int get(boolean player, int index, int fallback) {
        int key = player ? ~index : index;
        synchronized (cache) {
            int[] e = cache.get(key);
            if (e != null && (Host.cycleStamp - e[1] < KEEP_CYCLES || !Native.isClientThread())) return e[0];
            if (!Native.isClientThread()) return fallback;
            int h = Native.actorHeight(player, index);
            if (h <= 0) h = e != null ? e[0] : fallback;
            if (cache.size() > 4096) cache.clear();
            cache.put(key, new int[] {h, Host.cycleStamp});
            return h;
        }
    }
}
