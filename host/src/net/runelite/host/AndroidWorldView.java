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
