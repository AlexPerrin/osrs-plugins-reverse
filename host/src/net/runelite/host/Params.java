package net.runelite.host;

/**
 * Definition params (ParamHolder) read from the game's obj/npc/loc definitions (game::type_param).
 * A missing param returns 0 / "" (the ParamType defaults aren't read yet). Client thread only;
 * elsewhere the defaults are returned.
 */
final class Params {
    static final int OBJ = 0, NPC = 1, LOC = 2, STRUCT = 3;

    private Params() {}

    // setValue: desktop writes into the cached definition; here the values are kept host-side and
    // read before the game's (the game's own definitions are not changed).
    private static final java.util.Map<Long, Object> overrides = new java.util.concurrent.ConcurrentHashMap<>();
    private static long key(int kind, int id, int param) { return ((long) kind << 56) | ((long) (id & 0xffffff) << 32) | (param & 0xffffffffL); }

    static void set(int kind, int id, int param, Object value) { overrides.put(key(kind, id, param), value); }

    /** A host-side override (setValue / IterableHashTable.put), or null. */
    static Object override(int kind, int id, int key) { return overrides.get(key(kind, id, key)); }

    /** Integer, String or null (absent), override first. */
    static Object value(int kind, int id, int key) { return get(kind, id, key); }

    private static Object get(int kind, int id, int key) {
        Object o = overrides.get(key(kind, id, key));
        if (o != null) return o;
        if (Host.client() == null || !Host.client().isClientThread()) return null;
        return Native.typeParam(kind, id, key);
    }

    /** Long params: the game's int params widened (long-typed params aren't decoded separately). // unverified */
    static long longValue(int kind, int id, int key) {
        Object v = get(kind, id, key);
        return v instanceof Long ? (Long) v : v instanceof Integer ? (Integer) v : 0L;
    }

    static int intValue(int kind, int id, int key) {
        Object v = get(kind, id, key);
        return v instanceof Integer ? (Integer) v : v instanceof Long ? (int) (long) (Long) v : 0;
    }

    static String stringValue(int kind, int id, int key) {
        Object v = get(kind, id, key);
        return v instanceof String ? (String) v : "";
    }
}
