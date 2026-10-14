package net.runelite.host;

import net.runelite.api.WorldView;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;

/** A player as of the last client cycle; updated in place by {@link PlayerCache}. */
public class AndroidPlayer extends PlayerStub {
    private final int index;
    String name;
    int localX, localY, worldX, worldY;

    AndroidPlayer(int index) {
        this.index = index;
    }

    @Override public int getId() { return index; }
    @Override public String getName() { return name; }
    @Override public WorldView getWorldView() { return Host.worldView(); }

    @Override
    public WorldPoint getWorldLocation() {
        int[] p = Native.playerPosition();
        // The local player's plane is known exactly; others share the scene plane.
        int plane = p != null && this == Host.localPlayer() ? p[6] : Host.worldView().getPlane();
        return new WorldPoint(worldX, worldY, plane);
    }

    @Override
    public LocalPoint getLocalLocation() {
        return new LocalPoint(localX, localY, -1);
    }

    @Override
    public String toString() {
        return "Player[" + index + " " + name + " at " + worldX + "," + worldY + "]";
    }
}
