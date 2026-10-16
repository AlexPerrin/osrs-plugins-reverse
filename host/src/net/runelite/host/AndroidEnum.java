package net.runelite.host;

import java.util.HashMap;
import java.util.Map;

/** EnumComposition read from the game's enum cache (docs/offsets.md, EnumType). */
final class AndroidEnum extends EnumCompositionStub {
    private static final Map<Integer, AndroidEnum> cache = new HashMap<>();

    private final int defaultInt;
    private final String defaultString;
    private final int[] keys, ints;
    private final String[] strings;

    private AndroidEnum(Object[] r) {
        int[] h = (int[]) r[0];
        defaultInt = h[2];
        defaultString = (String) r[1];
        keys = (int[]) r[2];
        ints = (int[]) r[3];
        strings = (String[]) r[4];
    }

    /** Game thread only for enums not read before. */
    static AndroidEnum lookup(int id) {
        synchronized (cache) {
            AndroidEnum e = cache.get(id);
            if (e != null) return e;
        }
        if (!Native.isClientThread()) return null;
        Object[] r = Native.enumDef(id);
        if (r == null) return null;
        AndroidEnum e = new AndroidEnum(r);
        synchronized (cache) { cache.put(id, e); }
        return e;
    }

    @Override public int size() { return keys.length; }
    @Override public int[] getKeys() { return keys; }
    @Override public int[] getIntVals() { return ints; }
    @Override public String[] getStringVals() { return strings; }
    @Override public long[] getLongVals() { return new long[0]; }

    @Override
    public int getIntValue(int key) {
        for (int i = 0; i < keys.length && i < ints.length; i++) if (keys[i] == key) return ints[i];
        return defaultInt;
    }

    @Override
    public String getStringValue(int key) {
        for (int i = 0; i < keys.length && i < strings.length; i++) if (keys[i] == key) return strings[i];
        return defaultString;
    }

    @Override public long getLongValue(int key) { return getIntValue(key); }
}
