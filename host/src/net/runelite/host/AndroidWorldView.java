package net.runelite.host;

import java.util.Iterator;
import net.runelite.api.IndexedObjectSet;
import net.runelite.api.NPC;

/** The top-level world view (the main scene). */
public class AndroidWorldView extends WorldViewStub {
    private final NpcCache npcs;
    private final PlayerCache players;

    AndroidWorldView(NpcCache npcs, PlayerCache players) {
        this.npcs = npcs;
        this.players = players;
    }

    @Override
    public IndexedObjectSet<? extends net.runelite.api.Player> players() {
        return new IndexedObjectSet<net.runelite.api.Player>() {
            @Override
            public net.runelite.api.Player byIndex(int index) {
                return players.get(index);
            }

            @Override
            public Iterator<net.runelite.api.Player> iterator() {
                return java.util.Collections.<net.runelite.api.Player>unmodifiableList(players.all()).iterator();
            }
        };
    }

    @Override public int getId() { return -1; }
    @Override public boolean isTopLevel() { return true; }
    @Override public boolean isInstance() { return false; }
    @Override public int getSizeX() { return 104; }
    @Override public int getSizeY() { return 104; }

    @Override
    public boolean contains(net.runelite.api.coords.WorldPoint p) {
        int x = p.getX() - getBaseX(), y = p.getY() - getBaseY();
        return p.getPlane() == getPlane() && x >= 0 && y >= 0 && x < 104 && y < 104;
    }

    @Override
    public boolean contains(net.runelite.api.coords.LocalPoint p) {
        return p != null && p.getX() >= 0 && p.getY() >= 0 && p.getX() < 104 * 128 && p.getY() < 104 * 128;
    }

    @Override
    public int[] getMapRegions() {
        // The 104x104 scene spans up to 4x4 64-tile regions around its base.
        int bx = getBaseX(), by = getBaseY();
        java.util.LinkedHashSet<Integer> ids = new java.util.LinkedHashSet<>();
        for (int x = bx; x < bx + 104; x += 8)
            for (int y = by; y < by + 104; y += 8) ids.add((x >> 6) << 8 | (y >> 6));
        int[] out = new int[ids.size()];
        int i = 0;
        for (int id : ids) out[i++] = id;
        return out;
    }
    @Override
    public int getPlane() {
        int[] p = Native.playerPosition();
        return p == null ? 0 : p[7];
    }

    @Override
    public int getBaseX() {
        int[] p = Native.playerPosition();
        return p == null ? 0 : p[0] - p[2];
    }

    @Override
    public int getBaseY() {
        int[] p = Native.playerPosition();
        return p == null ? 0 : p[1] - p[3];
    }

    @Override
    public IndexedObjectSet<? extends NPC> npcs() {
        return new IndexedObjectSet<NPC>() {
            @Override
            public NPC byIndex(int index) {
                return npcs.get(index);
            }

            @Override
            public Iterator<NPC> iterator() {
                return java.util.Collections.<NPC>unmodifiableList(npcs.all()).iterator();
            }
        };
    }
}
