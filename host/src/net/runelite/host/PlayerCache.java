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

    /** {actor, CHANGED_* bits} since the last drain, for Host's AnimationChanged etc. */
    final List<Object[]> changes = new ArrayList<>();

    synchronized List<Object[]> drainChanges() {
        refresh();
        List<Object[]> out = new ArrayList<>(changes);
        changes.clear();
        return out;
    }

    synchronized void refresh() {
        int cycle = Native.gameCycle();
        if (cycle == refreshedCycle) return;
        refreshedCycle = cycle;
        Object[] snap = Native.players();
        int[] packed = (int[]) snap[0];
        String[] names = (String[]) snap[1];
        String[] overheads = (String[]) snap[2];
        final int S = 22;  // see Native.npcs()/players()
        Map<Integer, AndroidPlayer> seen = new HashMap<>();
        List<AndroidPlayer> next = new ArrayList<>(names.length);
        AndroidPlayer me = null;
        for (int i = 0; i < names.length; i++) {
            int index = packed[i * S];
            AndroidPlayer p = byIndex.get(index);
            if (p == null) p = new AndroidPlayer(index);
            p.name = names[i];
            p.localX = packed[i * S + 2];
            p.localY = packed[i * S + 3];
            p.worldX = packed[i * S + 4];
            p.worldY = packed[i * S + 5];
            if (packed[i * S + 1] != 0) me = p;
            boolean known = byIndex.get(index) == p;
            int changed = p.actor(packed, i * S + 6, overheads[i]);
            if (snap.length > 3) p.appearance((int[]) snap[3], i * AndroidPlayer.APPEARANCE_STRIDE);
            if (known && changed != 0) changes.add(new Object[] {p, changed});
            seen.put(index, p);
            next.add(p);
        }
        // Keep what just left the snapshot until its despawn event is posted (the event can be
        // handled after a refresh in the same cycle; QH's NpcStep drops spawns while it still
        // holds the old object).
        for (Map.Entry<Integer, AndroidPlayer> e : byIndex.entrySet())
            if (seen.get(e.getKey()) != e.getValue()) departed.put(e.getKey(), e.getValue());
        if (departed.size() > 512) departed.clear();  // events never came (logout etc.)
        byIndex.clear();
        byIndex.putAll(seen);
        list = Collections.unmodifiableList(next);
        // The game's player list is empty while a region loads and for the first cycles after
        // (QH's NpcStep reads getLocalPlayer().getWorldView() on GameStateChanged(LOGGED_IN)).
        // RuneLite keeps the local player meanwhile, so keep the last one until logout.
        int st = Native.gameState();
        if (me != null || (st != net.runelite.api.GameState.LOADING.getState()
                && st != net.runelite.api.GameState.LOGGED_IN.getState())) local = me;
    }

    /** Objects that left the snapshot, kept for despawn events (cleared by {@link #despawned}). */
    private final Map<Integer, AndroidPlayer> departed = new HashMap<>();

    /** The last known object at `index` (for despawn events): a departed one first, else the current one. */
    synchronized AndroidPlayer cached(int index) {
        AndroidPlayer d = departed.get(index);
        return d != null ? d : byIndex.get(index);
    }

    /** The despawn event for `index` was posted. */
    synchronized void despawned(int index) {
        departed.remove(index);
    }
    synchronized AndroidPlayer get(int index) { refresh(); return byIndex.get(index); }
    List<AndroidPlayer> all() { refresh(); return list; }
    AndroidPlayer local() { refresh(); return local; }
}
