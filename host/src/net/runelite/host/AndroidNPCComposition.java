package net.runelite.host;

import java.util.HashMap;
import java.util.Map;

/**
 * NPCComposition read from the game's NpcType cache (docs/offsets.md). Snapshot per id; actions
 * are the game's current text (varp/varbit-conditional ops resolved when first read).
 */
final class AndroidNPCComposition extends NPCCompositionStub {
    private static final Map<Integer, AndroidNPCComposition> cache = new HashMap<>();

    private final int[] v;
    private final String name;
    private final String[] actions = new String[5];

    private final int[] transforms;
    // Models and recolours (NpcType +0x30/+0x48/+0xa8/+0xc0, static RE gen 28)
    private int[] models = new int[0], chatheadModels = new int[0];
    private short[] colorFrom, colorTo;

    private AndroidNPCComposition(int[] v, String[] s, int[] transforms) {
        this.v = v;
        this.transforms = transforms;
        this.name = s[0];
        System.arraycopy(s, 1, actions, 0, 5);
    }

    /** Game thread only for ids not seen before. */
    static AndroidNPCComposition lookup(int id) {
        synchronized (cache) {
            AndroidNPCComposition c = cache.get(id);
            if (c != null) return c;
        }
        if (!Native.isClientThread()) return null;
        Object[] r = Native.npcDef(id);
        if (r == null) return null;
        AndroidNPCComposition c = new AndroidNPCComposition((int[]) r[0], (String[]) r[1], r.length > 2 ? (int[]) r[2] : null);
        if (r.length > 4) {
            int[][] m = (int[][]) r[3];
            short[][] col = (short[][]) r[4];
            if (m != null && m[0] != null) c.models = m[0];
            if (m != null && m[1] != null) c.chatheadModels = m[1];
            if (col != null) { c.colorFrom = col[0]; c.colorTo = col[1]; }
        }
        synchronized (cache) {
            if (cache.size() > 4096) cache.clear();
            cache.put(id, c);
        }
        return c;
    }

    @Override public int getIntValue(int param) { return Params.intValue(Params.NPC, v[0], param); }
    @Override public String getStringValue(int param) { return Params.stringValue(Params.NPC, v[0], param); }
    @Override public long getLongValue(int param) { return Params.longValue(Params.NPC, v[0], param); }
    @Override public void setValue(int param, int value) { Params.set(Params.NPC, v[0], param, value); }
    @Override public void setValue(int param, String value) { Params.set(Params.NPC, v[0], param, value); }
    @Override public void setValue(int param, long value) { Params.set(Params.NPC, v[0], param, value); }
    @Override public int getId() { return v[0]; }
    @Override public String getName() { return name; }
    @Override public int getSize() { return v[1]; }
    @Override public int getFootprintSize() { return v[1]; }
    @Override public int getCombatLevel() { return v[2]; }
    @Override public boolean isMinimapVisible() { return v[3] != 0; }
    @Override public boolean isInteractible() { return v[4] != 0; }
    @Override public String[] getActions() { return actions; }
    @Override public int[] getConfigs() { return transforms; }

    /** RuneLite semantics: the multi-NPC for the current varbit/varp value (null = hidden). */
    @Override public net.runelite.api.NPCComposition transform() {
        if (transforms == null || transforms.length == 0) return this;
        net.runelite.api.Client client = Host.client();
        int i = -1;
        if (client != null) {
            if (v[5] != -1) i = client.getVarbitValue(v[5]);
            else if (v[6] != -1) i = client.getVarpValue(v[6]);
        }
        int id = i >= 0 && i < transforms.length - 1 ? transforms[i] : transforms[transforms.length - 1];
        return id == -1 ? null : lookup(id);
    }
    @Override public boolean isFollower() { return false; }
    // NpcType decoder, mobile opcodes 61/62/40; verified gen 28 (Man 3108 = cache), probe `set 'npcdef <id> 0'`
    @Override public int[] getModels() { return models; }
    @Override public int[] getChatheadModels() { return chatheadModels; }
    @Override public short[] getColorToReplace() { return colorFrom; }
    @Override public short[] getColorToReplaceWith() { return colorTo; }
    // unverified: static RE (NpcType +0x120/+0x124, compared with 128 in NpcType::getModel)
    @Override public int getWidthScale() { return v.length > 8 ? v[7] : 128; }
    @Override public int getHeightScale() { return v.length > 8 ? v[8] : 128; }

    @Override
    public String toString() {
        return "NPCComposition[" + v[0] + " '" + name + "' size=" + v[1] + " combat=" + v[2] + " actions=" + java.util.Arrays.toString(actions)
                + " models=" + java.util.Arrays.toString(models) + " chatheads=" + java.util.Arrays.toString(chatheadModels)
                + " recolor=" + java.util.Arrays.toString(colorFrom) + "->" + java.util.Arrays.toString(colorTo)
                + " scale=" + getWidthScale() + "/" + getHeightScale() + "]";
    }
}
