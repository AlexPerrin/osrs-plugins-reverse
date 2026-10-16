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
        return new LocalPoint(localX, localY, WorldView.TOPLEVEL);
    }

    @Override
    public String toString() {
        return "Player[" + index + " " + name + " at " + worldX + "," + worldY + "]";
    }

    /** Position on the minimap (null when off it), as RuneLite computes it. */
    @Override public net.runelite.api.Point getMinimapLocation() {
        net.runelite.api.Client c = Host.client();
        LocalPoint lp = getLocalLocation();
        return c == null || lp == null ? null : net.runelite.api.Perspective.localToMinimap(c, lp);
    }
    @Override public LocalPoint getCameraFocus() { return getLocalLocation(); }

    // Geometry: players are 1x1; models aren't read, so hulls are boxes (as for NPCs).
    @Override public int getLogicalHeight() { return 200; }
    @Override public int getFootprintSize() { return 1; }
    @Override public java.awt.Shape getConvexHull() { return Hulls.box(localX, localY, 48, 48, Host.worldView().getPlane(), getLogicalHeight()); }
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
        return c == null ? null : net.runelite.api.Perspective.getCanvasTilePoly(c, getLocalLocation());
    }
    @Override public net.runelite.api.coords.WorldArea getWorldArea() { return new net.runelite.api.coords.WorldArea(getWorldLocation(), 1, 1); }

    // Not read from the game yet: inert values matching an idle, unskulled, healthy player.
    @Override public int getPoseAnimation() { return -1; }
    @Override public int getGraphic() { return -1; }
    @Override public int getHealthRatio() { return -1; }
    @Override public int getHealthScale() { return -1; }
    @Override public boolean isDead() { return false; }
    @Override public int getTeam() { return 0; }
    @Override public boolean isFriend() { return false; }
    @Override public boolean isFriendsChatMember() { return false; }
    @Override public boolean isClanMember() { return false; }
    @Override public int getSkullIcon() { return -1; }
    @Override public net.runelite.api.HeadIcon getOverheadIcon() { return null; }

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
