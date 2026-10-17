package net.runelite.host;

import java.util.HashMap;
import java.util.Map;

/**
 * ObjectComposition read from the game's LocType cache (docs/offsets.md "LocType"). Snapshot per
 * id; getImpostor() resolves the multiloc from the current varbit/varp value like RuneLite does.
 */
final class AndroidObjectComposition extends ObjectCompositionStub {
    private static final Map<Integer, AndroidObjectComposition> cache = new HashMap<>();

    private final int[] v;  // id, sizeX, sizeY, varbit, varp
    private final String name;
    private final String[] actions = new String[5];
    private final int[] transforms;
    private int mapSceneId = -1, mapIconId = -1;

    private AndroidObjectComposition(int[] v, String[] s, int[] transforms) {
        this.v = v;
        this.name = s[0] != null ? s[0] : "null";
        System.arraycopy(s, 1, actions, 0, 5);
        this.transforms = transforms;
    }

    /** Game thread only for ids not seen before. */
    static AndroidObjectComposition lookup(int id) {
        synchronized (cache) {
            AndroidObjectComposition c = cache.get(id);
            if (c != null) return c;
        }
        if (!Native.isClientThread()) return null;
        Object[] r = Native.locDef(id);
        if (r == null) return null;
        AndroidObjectComposition c = new AndroidObjectComposition((int[]) r[0], (String[]) r[1], (int[]) r[2]);
        synchronized (cache) {
            if (cache.size() > 8192) cache.clear();
            cache.put(id, c);
        }
        return c;
    }

    @Override public int getIntValue(int param) { return Params.intValue(Params.LOC, v[0], param); }
    @Override public String getStringValue(int param) { return Params.stringValue(Params.LOC, v[0], param); }
    @Override public long getLongValue(int param) { return Params.longValue(Params.LOC, v[0], param); }
    @Override public void setValue(int param, int value) { Params.set(Params.LOC, v[0], param, value); }
    @Override public void setValue(int param, String value) { Params.set(Params.LOC, v[0], param, value); }
    @Override public void setValue(int param, long value) { Params.set(Params.LOC, v[0], param, value); }
    @Override public int getId() { return v[0]; }
    @Override public String getName() { return name; }
    @Override public String[] getActions() { return actions; }
    @Override public int getSizeX() { return v[1]; }
    @Override public int getSizeY() { return v[2]; }
    @Override public int getVarbitId() { return v[3]; }
    @Override public int getVarPlayerId() { return v[4]; }
    @Override public int[] getImpostorIds() { return transforms; }
    @Override public int getMapSceneId() { return mapSceneId; }
    @Override public void setMapSceneId(int id) { mapSceneId = id; }
    @Override public int getMapIconId() { return mapIconId; }
    @Override public void setMapIconId(int id) { mapIconId = id; }

    @Override
    public net.runelite.api.ObjectComposition getImpostor() {
        if (transforms == null || transforms.length == 0) return null;
        net.runelite.api.Client client = Host.client();
        int i = -1;
        if (client != null) {
            if (v[3] != -1) i = client.getVarbitValue(v[3]);
            else if (v[4] != -1) i = client.getVarpValue(v[4]);
        }
        int id = i >= 0 && i < transforms.length - 1 ? transforms[i] : transforms[transforms.length - 1];
        return id == -1 ? null : lookup(id);
    }

    @Override
    public String toString() {
        return "ObjectComposition[" + v[0] + " '" + name + "' " + v[1] + "x" + v[2] + " actions="
                + java.util.Arrays.toString(actions) + " transforms=" + java.util.Arrays.toString(transforms) + "]";
    }
}
