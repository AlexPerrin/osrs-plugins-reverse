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

    /** Interaction targets (face-entity) aren't read yet: not interacting. */
    @Override public net.runelite.api.Actor getInteracting() { return null; }

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
    /** Animations/orientation aren't read yet. */
    @Override public int getAnimation() { return -1; }
    @Override public int getOrientation() { return 0; }

    /** Minimap geometry isn't mapped yet: treated as off the minimap. */
    @Override public net.runelite.api.Point getMinimapLocation() { return null; }
}
