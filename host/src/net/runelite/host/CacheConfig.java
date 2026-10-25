package net.runelite.host;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.runelite.api.EntityOps;
import net.runelite.api.HashTable;
import net.runelite.api.IntegerNode;
import net.runelite.api.IterableHashTable;
import net.runelite.api.Node;
import net.runelite.api.widgets.WidgetConfigNode;

/**
 * Config lookups over the game's own data: DB rows, the varc store, the IF_SETEVENTS table,
 * definition params and op lists. Client thread; elsewhere the lookups answer empty (null) instead of reading the game's data.
 */
final class CacheConfig {
    private CacheConfig() {}

    /** DBRowType's table id (DBRowType +0x30), or null when the row isn't in the cache. */
    static net.runelite.api.dbtable.DBRowConfig dbRow(int row) {
        int table = Native.isClientThread() ? Native.dbRowTable(row) : -1;
        if (table < 0) return null;
        return new net.runelite.api.dbtable.DBRowConfig() {
            @Override public int getTableID() { return table; }
            @Override public String toString() { return "DBRowConfig[row " + row + " table " + table + "]"; }
        };
    }

    /** A copy of the varc store: Integer for int varcs, String for string varcs. */
    static Map<Integer, Object> varcs() {
        Object[] r = Native.isClientThread() ? Native.varcEntries() : null;
        Map<Integer, Object> m = new LinkedHashMap<>();
        if (r == null) return m;
        int[] ids = (int[]) r[0];
        Object[] values = (Object[]) r[1];
        for (int i = 0; i < ids.length; i++) m.put(ids[i], values[i]);
        return m;
    }

    /** A node of a host table: hash = its key; neighbours aren't linked (desktop's bucket ring). */
    abstract static class KeyNode implements Node {
        final long key;
        KeyNode(long key) { this.key = key; }
        @Override public Node getNext() { return null; }
        @Override public Node getPrevious() { return null; }
        @Override public long getHash() { return key; }
    }

    /**
     * The IF_SETEVENTS table as desktop's widgetFlags: key = component << 32 | child index, one
     * WidgetConfigNode per index of each range the game holds (click mask = the event flags, op mask).
     */
    static HashTable<WidgetConfigNode> widgetFlags() {
        int[] r = Native.isClientThread() ? Native.widgetEventRanges() : null;
        final int[] ranges = r != null ? r : new int[0];
        return new HashTable<WidgetConfigNode>() {
            @Override public WidgetConfigNode get(long key) {
                int comp = (int) (key >>> 32), index = (int) key;
                for (int i = 0; i + 4 < ranges.length; i += 5)
                    if (ranges[i] == comp && ranges[i + 1] <= index && index <= ranges[i + 2]) return configNode(ranges, i, index);
                return null;
            }
            @Override public Iterator<WidgetConfigNode> iterator() {
                return new Iterator<WidgetConfigNode>() {
                    int range, index = ranges.length > 0 ? ranges[1] : 0;
                    @Override public boolean hasNext() {
                        while (range + 4 < ranges.length && index > ranges[range + 2]) {
                            range += 5;
                            if (range + 4 < ranges.length) index = ranges[range + 1];
                        }
                        return range + 4 < ranges.length;
                    }
                    @Override public WidgetConfigNode next() {
                        if (!hasNext()) throw new java.util.NoSuchElementException();
                        return configNode(ranges, range, index++);
                    }
                };
            }
            @Override public String toString() { return "WidgetFlags[" + ranges.length / 5 + " IF_SETEVENTS ranges]"; }
        };
    }

    private static WidgetConfigNode configNode(int[] ranges, int i, int index) {
        final int comp = ranges[i], click = ranges[i + 3], ops = ranges[i + 4];
        return new WidgetConfigNodeImpl(((long) comp << 32) | (index & 0xffffffffL), click, ops);
    }

    static final class WidgetConfigNodeImpl extends KeyNode implements WidgetConfigNode {
        private final int click, ops;
        WidgetConfigNodeImpl(long key, int click, int ops) { super(key); this.click = click; this.ops = ops; }
        @Override public int getClickMask() { return click; }
        @Override public int getOpMask() { return ops; }
        @Override public String toString() { return "WidgetConfigNode[" + (key >>> 32) + ":" + (int) key + " click 0x" + Integer.toHexString(click) + " ops 0x" + Integer.toHexString(ops) + "]"; }
    }

    static class IntegerNodeImpl extends KeyNode implements IntegerNode {
        private int value;
        IntegerNodeImpl(long key, int value) { super(key); this.value = value; }
        @Override public int getValue() { return value; }
        @Override public void setValue(int v) { value = v; }
        @Override public String toString() { return "IntegerNode[" + key + "=" + value + "]"; }
    }

    /** A string param (desktop's ObjectNode, which the API has no interface for). */
    static final class StringNode extends KeyNode {
        final String value;
        StringNode(long key, String value) { super(key); this.value = value; }
        @Override public String toString() { return "ObjectNode[" + key + "=\"" + value + "\"]"; }
    }

    /**
     * A definition's params (Params kinds) as desktop's IterableHashTable: IntegerNode for int
     * params, a Node for string ones. The keys come from the game's params map; values are read
     * through Params, so host-side setValue overrides show, and put/setValue store such overrides.
     */
    static IterableHashTable<Node> params(int kind, int id) {
        int[] keys = Native.isClientThread() ? Native.typeParamKeys(kind, id) : null;
        final List<Integer> order = new ArrayList<>();
        if (keys != null) for (int k : keys) order.add(k);
        Collections.sort(order);
        return new IterableHashTable<Node>() {
            @Override public Node get(long key) {
                if (key < 0 || key > Integer.MAX_VALUE) return null;
                int k = (int) key;
                if (!order.contains(k) && Params.override(kind, id, k) == null) return null;
                return node(k);
            }
            @Override public void put(Node node, long key) {
                if (key < 0 || key > Integer.MAX_VALUE) return;
                int k = (int) key;
                if (node instanceof IntegerNode) Params.set(kind, id, k, ((IntegerNode) node).getValue());
                else if (node instanceof StringNode) Params.set(kind, id, k, ((StringNode) node).value);
                else return;
                if (!order.contains(k)) order.add(k);
            }
            @Override public Iterator<Node> iterator() {
                List<Node> out = new ArrayList<>();
                for (int k : order) out.add(node(k));
                return out.iterator();
            }
            private Node node(int k) {
                Object v = Params.value(kind, id, k);
                if (v instanceof String) return new StringNode(k, (String) v);
                return new IntegerNodeImpl(k, Params.intValue(kind, id, k)) {
                    @Override public void setValue(int value) { super.setValue(value); Params.set(kind, id, k, value); }
                };
            }
            @Override public String toString() { return "Params[kind " + kind + " id " + id + ": " + order.size() + " keys " + order + "]"; }
        };
    }

    /**
     * The definition's ops as the game resolves them (opText) and each op's sub-op list from the
     * game's nested op vector (Params.NPC or Params.LOC kind); null sub-ops = none.
     */
    static EntityOps ops(int kind, int id, String[] actions) {
        return new EntityOps() {
            @Override public int getNumOps() { return actions.length; }
            @Override public String getOp(int i) { return i >= 0 && i < actions.length ? actions[i] : null; }
            @Override public EntityOps getSubOps(int i) {
                String[] sub = id >= 0 && Native.isClientThread() ? Native.subOps(kind, id, i) : null;
                return sub == null ? null : ops(kind, -1, sub);
            }
            @Override public String toString() { return "EntityOps" + java.util.Arrays.toString(actions); }
        };
    }

    /**
     * A js5 archive of the game (Client.getIndex): file ids from its in-memory reference table and
     * file bytes through the game's getFile. Overlays (RuneLite's cache overrides) don't exist here.
     */
    static final class Index implements net.runelite.api.IndexDataBase {
        final int id;
        Index(int id) { this.id = id; }
        @Override public boolean isOverlayOutdated() { return false; }
        // Off the client thread: null (the game's archives aren't thread-safe).
        @Override public int[] getFileIds(int group) { return Native.isClientThread() ? Native.archiveFileIds(id, group) : null; }
        @Override public byte[] loadData(int group, int file) { return Native.isClientThread() ? Native.archiveFile(id, group, file) : null; }
        @Override public String toString() { return "IndexDataBase[archive " + id + "]"; }
    }

    private static final Index[] indexes = new Index[26];

    /** The archive's index, or null for an id the game has no archive for (16, 24, > 25). */
    static Index index(int id) {
        if (id < 0 || id >= indexes.length || id == 16 || id == 24) return null;
        synchronized (indexes) {
            if (indexes[id] == null) indexes[id] = new Index(id);
            return indexes[id];
        }
    }

    /** Game caches, in Native.cacheSize order. */
    static final int OBJ = 0, LOC = 1, SEQ = 2, ITEM_MODEL = 3, ITEM_SPRITE = 4, HEALTH_BAR = 5;

    /**
     * One of the game's LRU caches: reset() runs the game's own clear (the ones its full cache
     * reset uses); size() is its entry count. Client thread, between cycles, as on desktop.
     */
    static final class Cache implements net.runelite.api.NodeCache {
        final int which;
        Cache(int which) { this.which = which; }
        @Override public void reset() {
            if (Native.isClientThread()) Native.cacheReset(which);
            else Host.invoke(() -> Native.cacheReset(which));
        }
        public long size() { return Native.cacheSize(which); }
        @Override public String toString() { return "NodeCache[" + NAMES[which] + ", " + size() + " entries]"; }
    }

    static final String[] NAMES = {"ObjType", "LocType", "SeqType", "item models", "item sprites", "health bars"};
    static final Cache[] CACHES = {new Cache(0), new Cache(1), new Cache(2), new Cache(3), new Cache(4), new Cache(5)};

    /** NPC.getModelOverrides / getChatheadOverrides from the game's NPC_INFO overrides; null = none. */
    static net.runelite.api.NpcOverrides npcOverrides(int index, boolean head) {
        Object[] r = Native.isClientThread() ? Native.npcOverrides(index, head) : null;
        if (r == null) return null;
        final int[] models = (int[]) r[0];
        final short[] recolours = (short[]) r[1], retextures = (short[]) r[2];
        final boolean local = ((int[]) r[3])[0] != 0;
        return new net.runelite.api.NpcOverrides() {
            @Override public int[] getModelIds() { return models; }
            @Override public short[] getColorToReplaceWith() { return recolours; }
            @Override public short[] getTextureToReplaceWith() { return retextures; }
            @Override public boolean useLocalPlayer() { return local; }
            @Override public String toString() {
                return "NpcOverrides[" + (head ? "head" : "body") + " models " + java.util.Arrays.toString(models) + " local " + local + "]";
            }
        };
    }

    /** Head icon archive ids (sprite indices with `sprites`) of an NPC, or null. */
    static int[] headIcons(int index, boolean sprites) {
        int[] r = Native.isClientThread() ? Native.npcHeadIcons(index) : null;
        if (r == null) return null;
        int n = r.length / 2;
        return java.util.Arrays.copyOfRange(r, sprites ? n : 0, sprites ? 2 * n : n);
    }
}
