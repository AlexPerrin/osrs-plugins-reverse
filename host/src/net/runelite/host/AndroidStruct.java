package net.runelite.host;

import net.runelite.api.IterableHashTable;
import net.runelite.api.Node;
import net.runelite.api.StructComposition;

/** Client.getStructComposition: params read from the game's StructType cache (game::type_param kind 3). */
final class AndroidStruct implements StructComposition {
    private final int id;

    AndroidStruct(int id) { this.id = id; }

    @Override public int getId() { return id; }
    @Override public int getIntValue(int param) { return Params.intValue(Params.STRUCT, id, param); }
    @Override public String getStringValue(int param) { return Params.stringValue(Params.STRUCT, id, param); }
    @Override public long getLongValue(int param) { return Params.longValue(Params.STRUCT, id, param); }
    @Override public void setValue(int param, int value) { Params.set(Params.STRUCT, id, param, value); }
    @Override public void setValue(int param, String value) { Params.set(Params.STRUCT, id, param, value); }
    @Override public void setValue(int param, long value) { Params.set(Params.STRUCT, id, param, value); }
    @Override public IterableHashTable<Node> getParams() { return null; }
}
