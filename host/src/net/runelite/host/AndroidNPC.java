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
        this.spotAnims = new ActorSpotAnims(false, index);
    }

    @Override public int getIndex() { return index; }
    /**
     * As RuneLite's client: a multi-NPC reports the id/name it currently transforms into, and -1/null
     * while it transforms to nothing (the game doesn't draw it), so id-matching plugins skip it.
     */
    @Override public int getId() {
        net.runelite.api.NPCComposition base = getComposition();
        if (base == null || base.getConfigs() == null || base.getConfigs().length == 0) return id;
        net.runelite.api.NPCComposition t = base.transform();
        return t == null ? -1 : t.getId();
    }
    @Override public String getName() {
        net.runelite.api.NPCComposition base = getComposition();
        if (base == null || base.getConfigs() == null || base.getConfigs().length == 0) return name;
        net.runelite.api.NPCComposition t = base.transform();
        return t == null ? null : t.getName();
    }
    @Override public net.runelite.api.NPCComposition getComposition() { return AndroidNPCComposition.lookup(id); }
    @Override public net.runelite.api.NPCComposition getTransformedComposition() {
        net.runelite.api.NPCComposition c = getComposition();
        return c == null ? null : c.transform();
    }
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


    /** The posed model (vertices, faces, lit colours), as desktop; null for a hidden multi-NPC. */
    @Override public net.runelite.api.Model getModel() {
        net.runelite.api.NPCComposition base = getComposition();
        if (base != null && base.getConfigs() != null && base.getConfigs().length > 0 && getTransformedComposition() == null) return null;
        return AndroidModel.of(false, index);
    }

    // unverified: default. Desktop's offset is non-zero only while some animations lift the model;
    // the game's field is not found, so this is the resting 0.
    @Override public int getAnimationHeightOffset() { return 0; }

    /** The posed model's height, as desktop (Implings' labels); a footprint estimate without a model. */
    @Override public int getLogicalHeight() {
        net.runelite.api.NPCComposition c = getComposition();
        int size = c != null ? Math.max(1, c.getSize()) : 1;
        return ActorHeights.get(false, index, size == 1 ? 200 : 160 * size);
    }

    /**
     * Convex hull of a box over the NPC's footprint (size x size tiles, getLogicalHeight tall),
     * projected with the client's camera. Models aren't read yet, so this stands in for the
     * model hull (highlights, arrows, click boxes).
     */
    @Override public java.awt.Shape getConvexHull() {
        // A multi-NPC that currently transforms to nothing isn't drawn: no model, no hull (as RuneLite).
        net.runelite.api.NPCComposition base = getComposition();
        if (base != null && base.getConfigs() != null && base.getConfigs().length > 0 && getTransformedComposition() == null) return null;
        if (Hulls.modelHulls() && Native.isClientThread()) {
            java.awt.Shape s = Hulls.model(Native.actorModel(false, index), localX, localY, currentOrientation, Host.worldView().getPlane());
            if (s != null) return s;
        }
        net.runelite.api.NPCComposition c = getComposition();
        int size = c != null ? Math.max(1, c.getSize()) : 1;
        int half = size * 64 - 16;
        return Hulls.box(localX, localY, half, half, Host.worldView().getPlane(), getLogicalHeight());
    }

    // Canvas helpers (Actor), as RuneLite's client implements them: anchored at the logical height.
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
        net.runelite.api.NPCComposition comp = getComposition();
        int size = comp != null ? Math.max(1, comp.getSize()) : 1;
        return c == null ? null : net.runelite.api.Perspective.getCanvasTileAreaPoly(c, getLocalLocation(), size);
    }
    @Override public net.runelite.api.coords.WorldArea getWorldArea() {
        net.runelite.api.NPCComposition comp = getComposition();
        int size = comp != null ? Math.max(1, comp.getSize()) : 1;
        return new net.runelite.api.coords.WorldArea(getWorldLocation(), size, size);
    }
    @Override public int getHealthRatio() { return healthRatio; }
    @Override public int getHealthScale() { return healthScale; }
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
        Host.invoke(() -> Native.actorSetOverhead(false, idx, text == null ? "" : text, -1));
    }
    @Override public void setOverheadCycle(int cycle) {
        overheadCycle = cycle;
        final int idx = index;
        Host.invoke(() -> Native.actorSetOverhead(false, idx, null, cycle));
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
        Host.invoke(() -> Native.actorSet(false, idx, what, value));
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
