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
    @Override public LocalPoint getLocalLocation() { return new LocalPoint(localX, localY, WorldView.TOPLEVEL); }
    @Override public WorldView getWorldView() { return Host.worldView(); }

    @Override
    public String toString() {
        return "NPC[" + index + " " + name + " (" + id + ") at " + worldX + "," + worldY + "]";
    }


    /** Approximate model height until NPC models are read (most humanoids are ~200 units tall). */
    @Override public int getLogicalHeight() {
        net.runelite.api.NPCComposition c = getComposition();
        int size = c != null ? Math.max(1, c.getSize()) : 1;
        return size == 1 ? 200 : 160 * size;
    }

    /**
     * Convex hull of a box over the NPC's footprint (size x size tiles, getLogicalHeight tall),
     * projected with the client's camera. Models aren't read yet, so this stands in for the
     * model hull (highlights, arrows, click boxes).
     */
    @Override public java.awt.Shape getConvexHull() {
        net.runelite.api.NPCComposition c = getComposition();
        int size = c != null ? Math.max(1, c.getSize()) : 1;
        int half = size * 64 - 16;
        return Hulls.box(localX, localY, half, half, Host.worldView().getPlane(), getLogicalHeight());
    }

    // Canvas helpers (Actor), as RuneLite's client implements them: anchored at the logical height.
    @Override public net.runelite.api.Point getCanvasImageLocation(java.awt.image.BufferedImage image, int zOffset) {
        net.runelite.api.Client c = Host.client();
        return c == null ? null : net.runelite.api.Perspective.getCanvasImageLocation(c, getLocalLocation(), image, getLogicalHeight() + zOffset);
    }
    @Override public net.runelite.api.Point getCanvasSpriteLocation(net.runelite.api.SpritePixels sprite, int zOffset) {
        net.runelite.api.Client c = Host.client();
        return c == null ? null : net.runelite.api.Perspective.getCanvasSpriteLocation(c, getLocalLocation(), sprite, getLogicalHeight() + zOffset);
    }
    @Override public net.runelite.api.Point getCanvasTextLocation(java.awt.Graphics2D g, String text, int zOffset) {
        net.runelite.api.Client c = Host.client();
        return c == null ? null : net.runelite.api.Perspective.getCanvasTextLocation(c, g, getLocalLocation(), text, getLogicalHeight() + zOffset);
    }
    @Override public java.awt.Polygon getCanvasTilePoly() {
        net.runelite.api.Client c = Host.client();
        net.runelite.api.NPCComposition comp = getComposition();
        int size = comp != null ? Math.max(1, comp.getSize()) : 1;
        return c == null ? null : net.runelite.api.Perspective.getCanvasTileAreaPoly(c, getLocalLocation(), size);
    }
    @Override public net.runelite.api.coords.WorldArea getWorldArea() {
        net.runelite.api.NPCComposition comp = getComposition();
        int size = comp != null ? Math.max(1, comp.getSize()) : 1;
        return new net.runelite.api.coords.WorldArea(getWorldLocation(), size, size);
    }
    @Override public int getPoseAnimation() { return -1; }
    @Override public int getGraphic() { return -1; }
    @Override public int getHealthRatio() { return -1; }
    @Override public int getHealthScale() { return -1; }
    @Override public boolean isDead() { return false; }
    @Override public int getFootprintSize() {
        net.runelite.api.NPCComposition c = getComposition();
        return c != null ? Math.max(1, c.getSize()) : 1;
    }

    /** Position on the minimap (null when off it), as RuneLite computes it. */
    @Override public net.runelite.api.Point getMinimapLocation() {
        net.runelite.api.Client c = Host.client();
        LocalPoint lp = getLocalLocation();
        return c == null || lp == null ? null : net.runelite.api.Perspective.localToMinimap(c, lp);
    }
    @Override public LocalPoint getCameraFocus() { return getLocalLocation(); }

    // Actor state from the last snapshot (Native.npcs()/players(): animation, frame, orientation,
    // current orientation, interacting, overhead cycle).
    int animation = -1, animationFrame = -1, orientation, currentOrientation, interacting = -1, overheadCycle;
    String overheadText;

    static final int CHANGED_ANIMATION = 1, CHANGED_INTERACTING = 2, CHANGED_OVERHEAD = 4;

    /** Copies the snapshot fields; returns CHANGED_* bits (for AnimationChanged etc.). */
    int actor(int[] p, int o, String overhead) {
        int changed = 0;
        if (p[o] != animation) changed |= CHANGED_ANIMATION;
        if (p[o + 4] != interacting) changed |= CHANGED_INTERACTING;
        // A new say resets the timer (to 100), so a rising timer with text means new overhead text.
        if (overhead != null && p[o + 5] > overheadCycle) changed |= CHANGED_OVERHEAD;
        animation = p[o];
        animationFrame = p[o + 1];
        orientation = p[o + 2];
        currentOrientation = p[o + 3];
        interacting = p[o + 4];
        overheadCycle = p[o + 5];
        overheadText = overhead;
        return changed;
    }

    @Override public int getAnimation() { return animation; }
    @Override public int getAnimationFrame() { return animationFrame; }
    @Override public int getOrientation() { return orientation; }
    @Override public int getCurrentOrientation() { return currentOrientation; }
    @Override public net.runelite.api.Actor getInteracting() { return Host.actor(interacting); }
    @Override public boolean isInteracting() { return interacting >= 0; }
    @Override public String getOverheadText() { return overheadCycle > 0 ? overheadText : null; }
    @Override public int getOverheadCycle() { return overheadCycle; }
}
