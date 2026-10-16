package net.runelite.host;

import net.runelite.api.Model;
import net.runelite.api.Node;

/** A ground item read from the game's per-tile item lists (game::ground_items). */
final class AndroidTileItem implements net.runelite.api.TileItem {
    final int id, quantity;

    AndroidTileItem(int id, int quantity) {
        this.id = id;
        this.quantity = quantity;
    }

    @Override public int getId() { return id; }
    @Override public int getQuantity() { return quantity; }
    /** Timers and ownership aren't read yet. */
    @Override public int getVisibleTime() { return 0; }
    @Override public int getDespawnTime() { return 0; }
    @Override public int getOwnership() { return OWNERSHIP_NONE; }
    @Override public boolean isPrivate() { return false; }

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
