package net.runelite.host;

import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;

/** The local player. Only position is backed by the game so far. */
public class AndroidPlayer extends PlayerStub {
    @Override
    public WorldPoint getWorldLocation() {
        int[] p = Native.playerPosition();
        return p == null ? null : new WorldPoint(p[0], p[1], p[6]);
    }

    @Override
    public LocalPoint getLocalLocation() {
        int[] p = Native.playerPosition();
        return p == null ? null : new LocalPoint(p[4], p[5], -1);
    }
}
