package net.runelite.host;

import net.runelite.api.Actor;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;

/**
 * A projectile from the game's list (Client +0xcb30), refreshed from a snapshot each client tick
 * (Projectiles.poll). Coordinates: source/target are world tiles in the game object; x/y/z are the
 * current scene position (local units, z negative up), 0 until the projectile starts moving, as on
 * desktop before the first setDestination.
 */
final class AndroidProjectile extends ProjectileStub {
    final long obj;
    int id, sourceLevel, sourceX, sourceY, startHeight, sourceIndex;
    int targetLevel, targetX, targetY, endHeight, targetIndex;
    int startCycle, endCycle, slope, startPos, orientation, animation, animationFrame;
    double x, y, z;

    AndroidProjectile(long obj) { this.obj = obj; }

    void update(int[] r, int o) {
        id = r[o + 2];
        sourceLevel = r[o + 3]; sourceX = r[o + 4]; sourceY = r[o + 5]; startHeight = r[o + 6]; sourceIndex = r[o + 7];
        targetLevel = r[o + 8]; targetX = r[o + 9]; targetY = r[o + 10]; endHeight = r[o + 11]; targetIndex = r[o + 12];
        startCycle = r[o + 13]; endCycle = r[o + 14]; slope = r[o + 15]; startPos = r[o + 16]; orientation = r[o + 17];
        animation = r[o + 18]; animationFrame = r[o + 19];
        x = bits(r, o + 20); y = bits(r, o + 22); z = bits(r, o + 24);
    }

    private static double bits(int[] r, int i) {
        return Double.longBitsToDouble((r[i] & 0xffffffffL) | ((long) r[i + 1] << 32));
    }

    /** Packet encoding: NPC index + 1, -(player index + 1), 0 = none. */
    private static Actor actor(int index) {
        if (index > 0) return Host.actor(index - 1);
        if (index < 0) return Host.actor(-index - 1 + 32768);
        return null;
    }

    static int index(Actor a) {
        if (a instanceof net.runelite.api.NPC) return ((net.runelite.api.NPC) a).getIndex() + 1;
        if (a instanceof net.runelite.api.Player) return -(((net.runelite.api.Player) a).getId() + 1);
        return 0;
    }

    private static LocalPoint local(int wx, int wy) {
        net.runelite.api.Client c = Host.client();
        return c == null || c.getTopLevelWorldView() == null ? null : LocalPoint.fromWorld(c.getTopLevelWorldView(), wx, wy);
    }

    @Override public int getId() { return id; }
    @Override public int getSourceLevel() { return sourceLevel; }
    @Override public WorldPoint getSourcePoint() { return new WorldPoint(sourceX, sourceY, sourceLevel); }
    @Override public Actor getSourceActor() { return actor(sourceIndex); }
    @Override public int getTargetLevel() { return targetLevel; }
    @Override public WorldPoint getTargetPoint() { return new WorldPoint(targetX, targetY, targetLevel); }
    @Override public Actor getTargetActor() { return actor(targetIndex); }
    @Override public Actor getInteracting() { return getTargetActor(); }
    /** The destination: the target actor's position, else the target tile's centre. */
    @Override public LocalPoint getTarget() {
        Actor a = getTargetActor();
        if (a != null && a.getLocalLocation() != null) return a.getLocalLocation();
        return local(targetX, targetY);
    }
    @Override public int getX1() { LocalPoint p = local(sourceX, sourceY); return p == null ? 0 : p.getX(); }
    @Override public int getY1() { LocalPoint p = local(sourceX, sourceY); return p == null ? 0 : p.getY(); }
    @Override public int getFloor() { return sourceLevel; }
    @Override public int getHeight() { return startHeight; }
    @Override public int getStartHeight() { return startHeight; }
    @Override public int getEndHeight() { return endHeight; }
    @Override public int getStartCycle() { return startCycle; }
    @Override public int getEndCycle() { return endCycle; }
    @Override public void setEndCycle(int cycle) {
        if (Native.projectileSetEndCycle(obj, cycle)) endCycle = cycle;
    }
    @Override public int getRemainingCycles() { return endCycle - Host.client().getGameCycle(); }
    @Override public int getSlope() { return slope; }
    @Override public int getStartPos() { return startPos; }
    @Override public double getX() { return x; }
    @Override public double getY() { return y; }
    @Override public double getZ() { return z; }
    @Override public int getOrientation() { return orientation; }
    @Override public int getAnimationFrame() { return animationFrame; }
    @Override public net.runelite.api.Animation getAnimation() {
        return animation < 0 || Host.client() == null ? null : Host.client().loadAnimation(animation);
    }
    @Override public net.runelite.api.Model getModel() { return null; }

    @Override public String toString() {
        return "Projectile(id=" + id + " " + sourceX + "," + sourceY + " -> " + targetX + "," + targetY
                + " src=" + sourceIndex + " tgt=" + targetIndex + " cycles " + startCycle + ".." + endCycle + ")";
    }
}
