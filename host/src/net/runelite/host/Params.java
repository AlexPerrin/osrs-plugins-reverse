package net.runelite.host;

/**
 * Definition params (ParamHolder) read from the game's obj/npc/loc definitions (game::type_param).
 * A missing param returns 0 / "" (the ParamType defaults aren't read yet). Client thread only;
 * elsewhere the defaults are returned.
 */
final class Params {
    static final int OBJ = 0, NPC = 1, LOC = 2, STRUCT = 3;

    private Params() {}

    private static Object get(int kind, int id, int key) {
        if (Host.client() == null || !Host.client().isClientThread()) return null;
        return Native.typeParam(kind, id, key);
    }

    static int intValue(int kind, int id, int key) {
        Object v = get(kind, id, key);
        return v instanceof Integer ? (Integer) v : 0;
    }

    static String stringValue(int kind, int id, int key) {
        Object v = get(kind, id, key);
        return v instanceof String ? (String) v : "";
    }
}
