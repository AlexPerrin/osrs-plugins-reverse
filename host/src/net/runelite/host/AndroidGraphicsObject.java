package net.runelite.host;

import net.runelite.api.coords.LocalPoint;

/**
 * A map spotanim from the top-level WorldView's graphics object list (+0x160), refreshed each client
 * tick (GraphicsObjects.poll). x/y are local units (tile centre), z the height.
 */
final class AndroidGraphicsObject extends GraphicsObjectStub {
    final long obj;
    int id, level, x, y, z, startCycle;
    final int[] raw = new int[24];
    private boolean finished;

    AndroidGraphicsObject(long obj) { this.obj = obj; }

    void update(int[] r, int o) {
        id = r[o + 2]; level = r[o + 3]; x = r[o + 4]; y = r[o + 5]; z = r[o + 6]; startCycle = r[o + 7];
        System.arraycopy(r, o + 8, raw, 0, raw.length);
    }

    @Override public net.runelite.api.WorldView getWorldView() { return Host.client().getTopLevelWorldView(); }
    @Override public int getId() { return id; }
    @Override public LocalPoint getLocation() { return new LocalPoint(x, y, getWorldView()); }
    @Override public int getStartCycle() { return startCycle; }
    @Override public int getLevel() { return level; }
    @Override public int getZ() { return z; }
    @Override public boolean finished() { return finished; }
    /** Recorded only: the game's finished flag isn't located yet, so the spotanim keeps playing. // unverified */
    @Override public void setFinished(boolean f) { finished = f; }
    @Override public net.runelite.api.Animation getAnimation() { return null; }
    @Override public int getAnimationFrame() { return -1; }
    @Override public net.runelite.api.Model getModel() { return null; }

    @Override public String toString() {
        return "GraphicsObject(id=" + id + " level " + level + " local " + x + "," + y + " z " + z + " start " + startCycle + ")";
    }
}
