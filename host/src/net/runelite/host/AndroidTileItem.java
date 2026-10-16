package net.runelite.host;

import net.runelite.api.Model;
import net.runelite.api.Node;

/** A ground item read from the game's per-tile item lists (game::ground_items). */
final class AndroidTileItem implements net.runelite.api.TileItem {
    final int id, quantity;
    /** Server ticks on the getTickCount() clock (Obj +0x48/+0x4c); ownership as RuneLite's constants. */
    int visibleTime, despawnTime, ownership;
    boolean isPrivate;

    AndroidTileItem(int id, int quantity) {
        this.id = id;
        this.quantity = quantity;
    }

    @Override public int getId() { return id; }
    @Override public int getQuantity() { return quantity; }
    @Override public int getVisibleTime() { return visibleTime; }
    @Override public int getDespawnTime() { return despawnTime; }
    @Override public int getOwnership() { return ownership; }
    @Override public boolean isPrivate() { return isPrivate; }

    @Override public Model getModel() { return null; }
    @Override public int getModelHeight() { return 0; }
    @Override public void setModelHeight(int h) {}
    @Override public int getAnimationHeightOffset() { return 0; }
    @Override public int getRenderMode() { return 0; }
    @Override public Node getNext() { return null; }
    @Override public Node getPrevious() { return null; }
    @Override public long getHash() { return 0; }

    @Override public String toString() { return "TileItem[" + id + " x" + quantity + "]"; }
}
