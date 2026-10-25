package net.runelite.host;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import net.runelite.api.ActorSpotAnim;
import net.runelite.api.IterableHashTable;
import net.runelite.api.Model;
import net.runelite.api.Node;

/**
 * An actor's spot anims, keyed by slot as desktop: the game's Actor +0x6f0 vector read live on the
 * client thread (the last read elsewhere). Adding and removing go through the game's spot anim adder.
 */
final class ActorSpotAnims implements IterableHashTable<ActorSpotAnim> {
    private final boolean player;
    private final int index;
    private final Map<Integer, SpotAnim> views = new HashMap<>();  // one view per slot
    private List<ActorSpotAnim> last = Collections.emptyList();

    ActorSpotAnims(boolean player, int index) {
        this.player = player;
        this.index = index;
    }

    synchronized List<ActorSpotAnim> entries() {
        if (!Native.isClientThread()) return last;
        int[] v = Native.actorSpotAnims(player, index);
        List<ActorSpotAnim> out = new ArrayList<>();
        if (v != null)
            for (int i = 0; i + 5 < v.length; i += 6) {
                SpotAnim s = views.computeIfAbsent(v[i], SpotAnim::new);
                System.arraycopy(v, i + 1, s.f, 0, 5);
                out.add(s);
            }
        return last = out;
    }

    @Override public ActorSpotAnim get(long key) {
        for (ActorSpotAnim s : entries()) if (s.getHash() == key) return s;
        return null;
    }

    /** Puts a spot anim with the node's id, height and start cycle into slot `key`. */
    @Override public void put(ActorSpotAnim node, long key) {
        create((int) key, node.getId(), node.getHeight(), Math.max(0, node.getStartCycle() - Native.gameCycle()));
    }

    @Override public Iterator<ActorSpotAnim> iterator() { return entries().iterator(); }

    void create(int slot, int id, int height, int delay) {
        Host.invoke(() -> Native.actorCreateSpotAnim(player, index, slot, id, height, delay));
    }

    void remove(int slot) { create(slot, -1, 0, 0); }

    void clear() {
        for (ActorSpotAnim s : new ArrayList<>(entries())) remove((int) s.getHash());
    }

    boolean has(int spotAnimId) {
        for (ActorSpotAnim s : entries()) if (s.getId() == spotAnimId) return true;
        return false;
    }

    /** Slot 0, which desktop's deprecated getGraphic/setGraphic family works on; null without one. */
    SpotAnim first() { return (SpotAnim) get(0); }

    /** One slot's spot anim: fields from the last read, setters write the game's SpotAnim. */
    final class SpotAnim implements ActorSpotAnim {
        private final int slot;
        final int[] f = new int[5];  // id, height, start cycle, frame, frame cycle

        SpotAnim(int slot) { this.slot = slot; }

        private void set(int field, int value) {
            f[field] = value;
            Host.invoke(() -> Native.actorSpotAnimSet(player, index, slot, field, value));
        }

        @Override public int getId() { return f[0]; }
        @Override public void setId(int id) { set(0, id); }
        @Override public int getHeight() { return f[1]; }
        @Override public void setHeight(int height) { set(1, height); }
        @Override public int getStartCycle() { return f[2]; }
        @Override public void setStartCycle(int cycle) { set(2, cycle); }
        @Override public int getFrame() { return f[3]; }
        @Override public void setFrame(int frame) { set(3, frame); }
        @Override public int getCycle() { return f[4]; }
        @Override public void setCycle(int cycle) { set(4, cycle); }
        @Override public long getHash() { return slot; }
        // Not linked: the game keeps spot anims in a vector, not a node list.
        @Override public Node getNext() { return null; }
        @Override public Node getPrevious() { return null; }
        // shortcut: the spot anim's posed model isn't read; read SpotAnim vtable slot 0 if a plugin needs it.
        @Override public Model getModel() { return null; }
        private int modelHeight;  // desktop's Renderable field, set by its draw; not read from the game
        @Override public int getModelHeight() { return modelHeight; }
        @Override public void setModelHeight(int height) { modelHeight = height; }
        @Override public int getAnimationHeightOffset() { return 0; }
        @Override public int getRenderMode() { return RENDERMODE_DEFAULT; }
        @Override public String toString() { return "ActorSpotAnim[slot " + slot + " id " + f[0] + " height " + f[1] + " start " + f[2] + " frame " + f[3] + "]"; }
    }
}
