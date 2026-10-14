package net.runelite.host;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Java mirror of client_thread's NPC snapshot, refreshed once per client cycle. */
final class NpcCache {
    private final Map<Integer, AndroidNPC> byIndex = new HashMap<>();
    private volatile List<AndroidNPC> list = Collections.emptyList();
    private int refreshedCycle = Integer.MIN_VALUE;

    synchronized void refresh() {
        int cycle = Native.gameCycle();
        if (cycle == refreshedCycle) return;
        refreshedCycle = cycle;
        Object[] snap = Native.npcs();
        int[] packed = (int[]) snap[0];
        String[] names = (String[]) snap[1];
        Map<Integer, AndroidNPC> seen = new HashMap<>();
        List<AndroidNPC> next = new ArrayList<>(names.length);
        for (int i = 0; i < names.length; i++) {
            int index = packed[i * 6];
            AndroidNPC npc = byIndex.get(index);
            if (npc == null || npc.id != packed[i * 6 + 1]) npc = new AndroidNPC(index);
            npc.id = packed[i * 6 + 1];
            npc.name = names[i];
            npc.localX = packed[i * 6 + 2];
            npc.localY = packed[i * 6 + 3];
            npc.worldX = packed[i * 6 + 4];
            npc.worldY = packed[i * 6 + 5];
            seen.put(index, npc);
            next.add(npc);
        }
        byIndex.clear();
        byIndex.putAll(seen);
        list = Collections.unmodifiableList(next);
    }

    /** The last known NPC at `index`, without refreshing (for despawn events). */
    synchronized AndroidNPC cached(int index) {
        return byIndex.get(index);
    }

    synchronized AndroidNPC get(int index) {
        refresh();
        return byIndex.get(index);
    }

    List<AndroidNPC> all() {
        refresh();
        return list;
    }
}
