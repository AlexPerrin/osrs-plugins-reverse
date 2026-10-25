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
        this.spotAnims = new ActorSpotAnims(true, index);
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
    @Override public net.runelite.api.Model getModel() { return AndroidModel.of(true, index); }
    // unverified: default (resting 0; the game's field is not found, as AndroidNPC).
    @Override public int getAnimationHeightOffset() { return 0; }
    @Override public int getFootprintSize() { return 1; }
    @Override public java.awt.Shape getConvexHull() {
        if (Hulls.modelHulls() && Native.isClientThread()) {
            java.awt.Shape s = Hulls.model(Native.actorModel(true, index), localX, localY, currentOrientation, Host.worldView().getPlane());
            if (s != null) return s;
        }
        return Hulls.box(localX, localY, 48, 48, Host.worldView().getPlane(), getLogicalHeight()); }
    @Override public net.runelite.api.Point getCanvasImageLocation(java.awt.image.BufferedImage image, int zOffset) {
        net.runelite.api.Client c = Host.client();
        return c == null ? null : net.runelite.api.Perspective.getCanvasImageLocation(c, getLocalLocation(), image, zOffset);
    }
    @Override public net.runelite.api.Point getCanvasSpriteLocation(net.runelite.api.SpritePixels sprite, int zOffset) {
        net.runelite.api.Client c = Host.client();
        return c == null ? null : net.runelite.api.Perspective.getCanvasSpriteLocation(c, getLocalLocation(), sprite, zOffset);
    }
    @Override public net.runelite.api.Point getCanvasTextLocation(java.awt.Graphics2D g, String text, int zOffset) {
        net.runelite.api.Client c = Host.client();
        return c == null ? null : net.runelite.api.Perspective.getCanvasTextLocation(c, g, getLocalLocation(), text, zOffset);
    }
    @Override public java.awt.Polygon getCanvasTilePoly() {
        net.runelite.api.Client c = Host.client();
        return c == null ? null : net.runelite.api.Perspective.getCanvasTilePoly(c, getLocalLocation());
    }
    @Override public net.runelite.api.coords.WorldArea getWorldArea() { return new net.runelite.api.coords.WorldArea(getWorldLocation(), 1, 1); }

    // Not read from the game yet: inert values matching an idle, unskulled, healthy player.
    @Override public int getHealthRatio() { return healthRatio; }
    @Override public int getHealthScale() { return healthScale; }
    @Override public int getTeam() { return team; }
    @Override public boolean isFriend() {
        net.runelite.api.Client c = Host.client();
        return this != Host.localPlayer() && c instanceof AndroidClient && ((AndroidClient) c).isFriendCached(name);
    }
    @Override public boolean isFriendsChatMember() { return ChatChannels.isFcMember(name); }  // unverified: on the device
    @Override public boolean isClanMember() { return ChatChannels.isClanMember(name); }  // unverified: on the device
    @Override public int getSkullIcon() { return skull; }
    @Override public void setSkullIcon(int icon) { skull = icon; write(4, icon); }  // Player +0x730
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
        if (p[o + 16] != healthRatio || p[o + 17] != healthScale) dead = p[o + 17] > 0 && p[o + 16] == 0;
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
    @Override public String getOverheadText() { return overheadText; }
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

    // Pose animations (Actor +0x280..+0x29c, +0x4b8): snapshot values; setters write the game.
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

    // Writes (client thread) go to the game's actor fields (Native.actorSet, game.h kActor*), so the game
    // draws them; the Java copy changes at once for a read-back before the next snapshot.
    private void write(int what, int value) {
        final int idx = index;
        Host.invoke(() -> Native.actorSet(true, idx, what, value));
    }
    @Override public void setAnimation(int id) { animation = id; write(0, id); }
    @Override public void setAnimationFrame(int frame) { animationFrame = frame; write(1, frame); }
    @Override public void setActionFrame(int frame) { setAnimationFrame(frame); }  // desktop's older name for it
    @Override public void setPoseAnimation(int id) { poseAnimation = id; write(2, id); }
    @Override public void setPoseAnimationFrame(int frame) { poseAnimationFrame = frame; write(3, frame); }
    private void setPose(int i, int id) { pose[i] = id; write(8 + i, id); }
    @Override public void setIdlePoseAnimation(int id) { setPose(0, id); }
    @Override public void setIdleRotateLeft(int id) { setPose(1, id); }
    @Override public void setIdleRotateRight(int id) { setPose(2, id); }
    @Override public void setWalkAnimation(int id) { setPose(3, id); }
    @Override public void setWalkRotate180(int id) { setPose(4, id); }
    @Override public void setWalkRotateLeft(int id) { setPose(5, id); }
    @Override public void setWalkRotateRight(int id) { setPose(6, id); }
    @Override public void setRunAnimation(int id) { setPose(7, id); }

    // Spot anims (Actor +0x6f0, live); the deprecated graphic accessors work on slot 0, as desktop.
    private final ActorSpotAnims spotAnims;
    @Override public net.runelite.api.IterableHashTable<net.runelite.api.ActorSpotAnim> getSpotAnims() { return spotAnims; }
    @Override public boolean hasSpotAnim(int id) { return spotAnims.has(id); }
    @Override public void createSpotAnim(int slot, int id, int height, int delay) { spotAnims.create(slot, id, height, delay); }
    @Override public void removeSpotAnim(int slot) { spotAnims.remove(slot); }
    @Override public void clearSpotAnims() { spotAnims.clear(); }
    @Override public int getGraphic() { net.runelite.api.ActorSpotAnim s = spotAnims.first(); return s == null ? -1 : s.getId(); }
    @Override public void setGraphic(int id) {
        if (id == -1) spotAnims.remove(0);
        else spotAnims.create(0, id, getGraphicHeight(), 0);
    }
    @Override public int getGraphicHeight() { net.runelite.api.ActorSpotAnim s = spotAnims.first(); return s == null ? 0 : s.getHeight(); }
    @Override public void setGraphicHeight(int height) { net.runelite.api.ActorSpotAnim s = spotAnims.first(); if (s != null) s.setHeight(height); }
    @Override public int getSpotAnimFrame() { net.runelite.api.ActorSpotAnim s = spotAnims.first(); return s == null ? -1 : s.getFrame(); }
    @Override public void setSpotAnimFrame(int frame) { net.runelite.api.ActorSpotAnim s = spotAnims.first(); if (s != null) s.setFrame(frame); }

    /** As desktop: set on a head bar update to 0 (ActorDeath is not posted), or by setDead. */
    private boolean dead;
    @Override public boolean isDead() { return dead; }
    @Override public void setDead(boolean dead) { this.dead = dead; }

    // Renderable/Node as desktop: the model height is the draw's (the posed model's, or a setModelHeight
    // of this cycle); actors aren't linked into a node list.
    private int modelHeight, modelHeightStamp = -1;
    @Override public int getModelHeight() { return modelHeightStamp == Host.cycleStamp ? modelHeight : getLogicalHeight(); }
    @Override public void setModelHeight(int height) { modelHeight = height; modelHeightStamp = Host.cycleStamp; }
    @Override public int getRenderMode() { return RENDERMODE_DEFAULT; }
    @Override public long getHash() { return 0; }
    @Override public net.runelite.api.Node getNext() { return null; }
    @Override public net.runelite.api.Node getPrevious() { return null; }
}
