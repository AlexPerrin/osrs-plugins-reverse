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
    @Override public int getLogicalHeight() { return ActorHeights.get(true, index, 200); }
    @Override public int getFootprintSize() { return 1; }
    @Override public java.awt.Shape getConvexHull() {
        if (Hulls.modelHulls() && Native.isClientThread()) {
            java.awt.Shape s = Hulls.model(Native.actorModel(true, index), localX, localY, currentOrientation, Host.worldView().getPlane());
            if (s != null) return s;
        }
        return Hulls.box(localX, localY, 48, 48, Host.worldView().getPlane(), getLogicalHeight()); }
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
    @Override public int getGraphic() { return -1; }
    @Override public int getHealthRatio() { return healthRatio; }
    @Override public int getHealthScale() { return healthScale; }
    @Override public boolean isDead() { return false; }
    @Override public int getTeam() { return team; }
    @Override public boolean isFriend() { return false; }
    @Override public boolean isFriendsChatMember() { return false; }
    @Override public boolean isClanMember() { return false; }
    @Override public int getSkullIcon() { return skull; }
    @Override public net.runelite.api.HeadIcon getOverheadIcon() {
        net.runelite.api.HeadIcon[] v = net.runelite.api.HeadIcon.values();
        return prayer >= 0 && prayer < v.length ? v[prayer] : null;
    }
    @Override public int getCombatLevel() { return combatLevel; }
    @Override public net.runelite.api.PlayerComposition getPlayerComposition() { return composition; }

    // Appearance (Native.players() element 3): has-appearance flag, equipment[12], kits[12],
    // colours[5], gender, transformed NPC id, combat level, skull, prayer icon, team.
    static final int APPEARANCE_STRIDE = 36;
    int combatLevel, skull = -1, prayer = -1, team;
    private AndroidPlayerComposition composition;

    void appearance(int[] a, int o) {
        if (o + APPEARANCE_STRIDE > a.length) return;
        combatLevel = a[o + 32];
        skull = a[o + 33];
        prayer = a[o + 34];
        team = a[o + 35];
        if (a[o] == 0) { composition = null; return; }
        int[] equipment = java.util.Arrays.copyOfRange(a, o + 1, o + 13);
        int[] kits = java.util.Arrays.copyOfRange(a, o + 13, o + 25);
        int[] colors = java.util.Arrays.copyOfRange(a, o + 25, o + 30);
        int gender = a[o + 30], npcId = a[o + 31];
        if (composition == null || !composition.same(equipment, kits, colors, gender, npcId))
            composition = new AndroidPlayerComposition(equipment, kits, colors, gender, npcId);
    }

    // Actor state from the last snapshot (Native.npcs()/players(): animation, frame, orientation,
    // current orientation, interacting, overhead cycle).
    int animation = -1, animationFrame = -1, orientation, currentOrientation, interacting = -1, overheadCycle;
    String overheadText;

    static final int CHANGED_ANIMATION = 1, CHANGED_INTERACTING = 2, CHANGED_OVERHEAD = 4, CHANGED_HITSPLAT = 8;
    // Head bar (first bar's last update; -1 without one) and hitmark slots from the snapshot.
    int healthRatio = -1, healthScale = -1;
    final int[] hitsplatSlots = {-1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1};
    /** Hitmarks that appeared in the last snapshot (for HitsplatApplied), or null. */
    java.util.List<net.runelite.api.Hitsplat> newHitsplats;

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
        System.arraycopy(p, o + 6, pose, 0, 8);
        poseAnimation = p[o + 14];
        poseAnimationFrame = p[o + 15];
        healthRatio = p[o + 16];
        healthScale = p[o + 17];
        newHitsplats = AndroidHitsplat.diff(p, o + 18, hitsplatSlots);
        if (newHitsplats != null) changed |= CHANGED_HITSPLAT;
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
    @Override public void setOverheadText(String text) {
        overheadText = text;
        final int idx = index;
        Host.invoke(() -> Native.actorSetOverhead(true, idx, text == null ? "" : text, -1));
    }
    @Override public void setOverheadCycle(int cycle) {
        overheadCycle = cycle;
        final int idx = index;
        Host.invoke(() -> Native.actorSetOverhead(true, idx, null, cycle));
    }

    // Pose animations (Actor +0x280..+0x29c, +0x4b8): read-only snapshot values.
    final int[] pose = {-1, -1, -1, -1, -1, -1, -1, -1};
    int poseAnimation = -1, poseAnimationFrame = -1;
    @Override public int getIdlePoseAnimation() { return pose[0]; }
    @Override public int getIdleRotateLeft() { return pose[1]; }
    @Override public int getIdleRotateRight() { return pose[2]; }
    @Override public int getWalkAnimation() { return pose[3]; }
    @Override public int getWalkRotate180() { return pose[4]; }
    @Override public int getWalkRotateLeft() { return pose[5]; }
    @Override public int getWalkRotateRight() { return pose[6]; }
    @Override public int getRunAnimation() { return pose[7]; }
    @Override public int getPoseAnimation() { return poseAnimation; }
    @Override public int getPoseAnimationFrame() { return poseAnimationFrame; }
    @Override public net.runelite.api.IterableHashTable<net.runelite.api.ActorSpotAnim> getSpotAnims() { return NoSpotAnims.INSTANCE; }
    @Override public boolean hasSpotAnim(int id) { return false; }
}
