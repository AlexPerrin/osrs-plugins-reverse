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

    /** {actor, CHANGED_* bits} since the last drain, for Host's AnimationChanged etc. */
    final List<Object[]> changes = new ArrayList<>();

    synchronized List<Object[]> drainChanges() {
        refreshedCycle = Integer.MIN_VALUE;  // a handler earlier in the cycle may have read the old snapshot
        refresh();
        List<Object[]> out = new ArrayList<>(changes);
        changes.clear();
        return out;
    }

    /** For spawn events: the snapshot may have been read earlier in this cycle, before the
     *  native diff that posted the event, so re-read it when the index isn't there. */
    synchronized AndroidNPC getFresh(int index) {
        AndroidNPC o = get(index);
        if (o != null) return o;
        refreshedCycle = Integer.MIN_VALUE;
        return get(index);
    }

    synchronized void refresh() {
        int cycle = Native.gameCycle();
        if (cycle == refreshedCycle) return;
        refreshedCycle = cycle;
        Object[] snap = Native.npcs();
        int[] packed = (int[]) snap[0];
        String[] names = (String[]) snap[1];
        String[] overheads = (String[]) snap[2];
        final int S = 36;  // see Native.npcs()/players()
        Map<Integer, AndroidNPC> seen = new HashMap<>();
        List<AndroidNPC> next = new ArrayList<>(names.length);
        for (int i = 0; i < names.length; i++) {
            int index = packed[i * S];
            AndroidNPC npc = byIndex.get(index);
            if (npc == null || npc.id != packed[i * S + 1]) npc = new AndroidNPC(index);
            npc.id = packed[i * S + 1];
            npc.name = names[i];
            npc.localX = packed[i * S + 2];
            npc.localY = packed[i * S + 3];
            npc.worldX = packed[i * S + 4];
            npc.worldY = packed[i * S + 5];
            boolean known = byIndex.get(index) == npc;
            int changed = npc.actor(packed, i * S + 6, overheads[i]);
            if (known && changed != 0) changes.add(new Object[] {npc, changed});
            seen.put(index, npc);
            next.add(npc);
        }
        // Keep what just left the snapshot until its despawn event is posted (the event can be
        // handled after a refresh in the same cycle; QH's NpcStep drops spawns while it still
        // holds the old object).
        for (Map.Entry<Integer, AndroidNPC> e : byIndex.entrySet())
            if (seen.get(e.getKey()) != e.getValue()) departed.put(e.getKey(), e.getValue());
        if (departed.size() > 512) departed.clear();  // events never came (logout etc.)
        byIndex.clear();
        byIndex.putAll(seen);
        list = Collections.unmodifiableList(next);
    }

    /** The last known NPC at `index`, without refreshing (for despawn events). */
    /** Objects that left the snapshot, kept for despawn events (cleared by {@link #despawned}). */
    private final Map<Integer, AndroidNPC> departed = new HashMap<>();

    /** The last known object at `index` (for despawn events): a departed one first, else the current one. */
    synchronized AndroidNPC cached(int index) {
        AndroidNPC d = departed.get(index);
        return d != null ? d : byIndex.get(index);
    }

    /** The despawn event for `index` was posted. */
    synchronized void despawned(int index) {
        departed.remove(index);
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
