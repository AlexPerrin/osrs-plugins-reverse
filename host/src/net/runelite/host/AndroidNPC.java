package net.runelite.host;

import net.runelite.api.WorldView;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;

/** An NPC as of the last client cycle; updated in place by {@link NpcCache}. */
public class AndroidNPC extends NPCStub {
    private final int index;
    int id;
    String name;
    int localX, localY, worldX, worldY;

    AndroidNPC(int index) {
        this.index = index;
    }

    @Override public int getIndex() { return index; }
    @Override public int getId() { return id; }
    @Override public String getName() { return name; }
    @Override public net.runelite.api.NPCComposition getComposition() { return AndroidNPCComposition.lookup(id); }
    @Override public net.runelite.api.NPCComposition getTransformedComposition() { return AndroidNPCComposition.lookup(id); }
    @Override public int getCombatLevel() {
        net.runelite.api.NPCComposition c = getComposition();
        return c != null ? c.getCombatLevel() : 0;
    }
    // NPCs are on the scene's current plane until the per-NPC plane field is located.
    @Override public WorldPoint getWorldLocation() { return new WorldPoint(worldX, worldY, Host.worldView().getPlane()); }
    @Override public LocalPoint getLocalLocation() { return new LocalPoint(localX, localY, -1); }
    @Override public WorldView getWorldView() { return Host.worldView(); }

    @Override
    public String toString() {
        return "NPC[" + index + " " + name + " (" + id + ") at " + worldX + "," + worldY + "]";
    }
}
