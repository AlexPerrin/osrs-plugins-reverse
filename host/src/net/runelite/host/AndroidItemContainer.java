package net.runelite.host;

import net.runelite.api.Item;

/** An immutable snapshot of one of the game's inventories. Empty slots hold Item(-1, 0). */
public class AndroidItemContainer extends ItemContainerStub {
    private final int id;
    private final Item[] items;

    AndroidItemContainer(int id, int[] packed) {
        this.id = id;
        int n = packed.length / 2;
        items = new Item[n];
        for (int i = 0; i < n; i++) items[i] = new Item(packed[i], packed[n + i]);
    }

    /** null if the game has no such inventory. */
    static AndroidItemContainer of(int id) {
        int[] packed = Native.inventory(id);
        return packed == null ? null : new AndroidItemContainer(id, packed);
    }

    @Override public int getId() { return id; }
    @Override public Item[] getItems() { return items.clone(); }
    @Override public Item getItem(int slot) { return slot >= 0 && slot < items.length && items[slot].getId() != -1 ? items[slot] : null; }
    @Override public int size() { return items.length; }
    @Override public boolean contains(int itemId) { return find(itemId) != -1; }

    @Override
    public int count(int itemId) {
        int c = 0;
        for (Item i : items) if (i.getId() == itemId) c += i.getQuantity();
        return c;
    }

    @Override
    public int count() {
        int c = 0;
        for (Item i : items) if (i.getId() != -1) c++;
        return c;
    }

    @Override
    public int find(int itemId) {
        for (int s = 0; s < items.length; s++) if (items[s].getId() == itemId) return s;
        return -1;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("ItemContainer[" + id + ":");
        for (Item i : items) if (i.getId() != -1) sb.append(' ').append(i.getId()).append('x').append(i.getQuantity());
        return sb.append(']').toString();
    }
}
