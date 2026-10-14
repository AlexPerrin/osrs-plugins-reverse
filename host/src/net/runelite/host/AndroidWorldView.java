package net.runelite.host;

import java.util.Iterator;
import net.runelite.api.IndexedObjectSet;
import net.runelite.api.NPC;

/** The top-level world view (the main scene). */
public class AndroidWorldView extends WorldViewStub {
    private final NpcCache npcs;

    AndroidWorldView(NpcCache npcs) {
        this.npcs = npcs;
    }

    @Override public int getId() { return -1; }
    @Override public boolean isTopLevel() { return true; }
    // TODO: plane is not located yet.
    @Override public int getPlane() { return 0; }

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
