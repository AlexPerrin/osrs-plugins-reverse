package net.runelite.host;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Java mirror of client_thread's player snapshot, refreshed once per client cycle. */
final class PlayerCache {
    private final Map<Integer, AndroidPlayer> byIndex = new HashMap<>();
    private volatile List<AndroidPlayer> list = Collections.emptyList();
    private volatile AndroidPlayer local;
    private int refreshedCycle = Integer.MIN_VALUE;

    synchronized void refresh() {
        int cycle = Native.gameCycle();
        if (cycle == refreshedCycle) return;
        refreshedCycle = cycle;
        Object[] snap = Native.players();
        int[] packed = (int[]) snap[0];
        String[] names = (String[]) snap[1];
        Map<Integer, AndroidPlayer> seen = new HashMap<>();
        List<AndroidPlayer> next = new ArrayList<>(names.length);
        AndroidPlayer me = null;
        for (int i = 0; i < names.length; i++) {
            int index = packed[i * 6];
            AndroidPlayer p = byIndex.get(index);
            if (p == null) p = new AndroidPlayer(index);
            p.name = names[i];
            p.localX = packed[i * 6 + 2];
            p.localY = packed[i * 6 + 3];
            p.worldX = packed[i * 6 + 4];
            p.worldY = packed[i * 6 + 5];
            if (packed[i * 6 + 1] != 0) me = p;
            seen.put(index, p);
            next.add(p);
        }
        byIndex.clear();
        byIndex.putAll(seen);
        list = Collections.unmodifiableList(next);
        local = me;
    }

    synchronized AndroidPlayer cached(int index) { return byIndex.get(index); }
    synchronized AndroidPlayer get(int index) { refresh(); return byIndex.get(index); }
    List<AndroidPlayer> all() { refresh(); return list; }
    AndroidPlayer local() { refresh(); return local; }
}
