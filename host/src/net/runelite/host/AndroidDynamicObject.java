package net.runelite.host;

/**
 * An animated loc's renderable (desktop DynamicObject), read from the game's object (Native.dynamicObject,
 * docs/offsets.md "Animated locs") in renderable slot `slot` of a scene object. Fields are read on each call
 * (the game advances them as it draws); off the client thread they answer -1/null.
 */
final class AndroidDynamicObject implements net.runelite.api.DynamicObject {
    private final AndroidTileObject owner;
    private final int slot;
    private int modelHeight = 1000;  // the game's Renderable height default (+0x18)

    private AndroidDynamicObject(AndroidTileObject owner, int slot) {
        this.owner = owner;
        this.slot = slot;
    }

    /** The animated loc in that slot, or null when it holds a static model (or off the client thread). */
    static AndroidDynamicObject of(AndroidTileObject o, int slot) {
        return o.read(slot, false) != null ? new AndroidDynamicObject(o, slot) : null;
    }

    private int field(int i) {
        int[] f = owner.read(slot, false);
        return f == null ? -1 : f[i];
    }

    @Override public net.runelite.api.Animation getAnimation() {
        int seq = field(6);
        return seq < 0 || Host.client() == null ? null : Host.client().loadAnimation(seq);
    }
    @Override public int getAnimFrame() { return field(8); }
    /** Desktop's cycleStart: the cycle getModel last advanced the animation to. */
    @Override public int getAnimCycle() { return field(7); }
    @Override public net.runelite.api.Model getModelZbuf() { return getModel(); }
    @Override public net.runelite.api.ObjectComposition getRecordedObjectComposition() {
        int id = field(0);
        return id < 0 || Host.client() == null ? null : Host.client().getObjectDefinition(id);
    }

    /** The loc's model at this cycle (the game's getModel, which advances the animation as a draw would). */
    @Override public net.runelite.api.Model getModel() { return AndroidModel.parse(owner.read(slot, true)); }
    @Override public int getModelHeight() { return modelHeight; }
    @Override public void setModelHeight(int h) { modelHeight = h; }
    @Override public int getAnimationHeightOffset() { return 0; }
    @Override public int getRenderMode() { return RENDERMODE_DEFAULT; }
    @Override public net.runelite.api.Node getNext() { return null; }
    @Override public net.runelite.api.Node getPrevious() { return null; }
    @Override public long getHash() { return owner.getHash(); }

    @Override public String toString() {
        int[] f = owner.read(slot, false);
        return f == null ? "DynamicObject[gone]" : "DynamicObject[loc " + f[0] + " seq " + f[6] + " frame " + f[8] + " cycle " + f[7] + "]";
    }
}
