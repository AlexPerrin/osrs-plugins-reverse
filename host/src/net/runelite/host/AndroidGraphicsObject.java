package net.runelite.host;

import net.runelite.api.coords.LocalPoint;

/**
 * A map spotanim from the top-level WorldView's graphics object list (+0x160), refreshed each client
 * tick (GraphicsObjects.poll). x/y are local units (tile centre), z the height. The animation state
 * is at +0x280 (docs/offsets.md "Graphics objects"): +0x290 sequence id (-1 once finished), +0x294 u16
 * frame cycle, +0x296 u16 frame.
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
    /** raw[] starts at +0x240. */
    private int seqId() { return raw[(0x290 - 0x240) / 4]; }

    @Override public boolean finished() { return finished || seqId() < 0; }
    /**
     * true ends the animation the way the game does when it runs out (setSequence(-1) on the state);
     * the game drops the object on its next pass over the list. Client thread only, like desktop.
     */
    @Override public void setFinished(boolean f) {
        if (f && !finished && Native.isClientThread()) Native.graphicsObjectFinish(obj);
        finished = f;
    }
    @Override public net.runelite.api.Animation getAnimation() {
        int id = seqId();
        return id < 0 || Host.client() == null ? null : Host.client().loadAnimation(id);
    }
    @Override public int getAnimationFrame() { return seqId() < 0 ? -1 : raw[(0x294 - 0x240) / 4] >>> 16; }
    @Override public net.runelite.api.Model getModel() { return null; }

    @Override public String toString() {
        return "GraphicsObject(id=" + id + " level " + level + " local " + x + "," + y + " z " + z + " start " + startCycle + ")";
    }
}
