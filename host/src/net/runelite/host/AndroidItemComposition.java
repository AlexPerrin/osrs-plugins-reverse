package net.runelite.host;

import java.util.LinkedHashMap;
import java.util.Map;

/** ItemComposition read from the game's obj definition cache (docs/offsets.md, ObjType). */
final class AndroidItemComposition extends ItemCompositionStub {
    private static final Map<Integer, AndroidItemComposition> cache = new LinkedHashMap<Integer, AndroidItemComposition>(256, 0.75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<Integer, AndroidItemComposition> e) { return size() > 2048; }
    };

    private final int id;
    private String name;
    private final int[] v;
    private final String[] actions = new String[5];
    private int shiftClickIndex = -1;
    // Model fields (ObjType +0x04 model, +0x2c4 ambient, +0x2c8 contrast, recolour/retexture arrays;
    // static RE of the decoder, docs/offsets.md ObjType). Setters keep host-side values, as for the
    // icon view: nothing here re-renders the game's icons or ground models.
    private int inventoryModel = -1, ambient, contrast;
    private short[] colorFrom, colorTo, textureFrom, textureTo;

    private AndroidItemComposition(int id, int[] v, String[] s) {
        this.id = id;
        this.v = v;
        if (v.length > 16) { inventoryModel = v[14]; ambient = v[15]; contrast = v[16]; }
        this.name = s[0];
        System.arraycopy(s, 1, actions, 0, 5);
    }

    /** Game thread only (the definition cache isn't thread-safe). */
    static AndroidItemComposition lookup(int id) {
        synchronized (cache) {
            AndroidItemComposition c = cache.get(id);
            if (c != null) return c;
        }
        // The game's ObjType for a negative id (an empty slot, -1) is the default definition named
        // "null"; desktop returns it too, and plugins read its name (Inventory Setups' empty slots).
        if (id < 0) return new AndroidItemComposition(id, new int[] {0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, -1, 0, 0}, new String[] {"null", null, null, null, null, null});
        Object[] r = Native.itemDef(id);
        if (r == null) return null;
        AndroidItemComposition c = new AndroidItemComposition(id, (int[]) r[0], (String[]) r[1]);
        if (r.length > 2 && r[2] != null) {
            short[][] m = (short[][]) r[2];
            c.colorFrom = m[0]; c.colorTo = m[1]; c.textureFrom = m[2]; c.textureTo = m[3];
        }
        synchronized (cache) { cache.put(id, c); }
        return c;
    }

    @Override public int getIntValue(int param) { return Params.intValue(Params.OBJ, id, param); }
    @Override public String getStringValue(int param) { return Params.stringValue(Params.OBJ, id, param); }
    @Override public long getLongValue(int param) { return Params.longValue(Params.OBJ, id, param); }
    @Override public void setValue(int param, int value) { Params.set(Params.OBJ, id, param, value); }
    @Override public void setValue(int param, String value) { Params.set(Params.OBJ, id, param, value); }
    @Override public void setValue(int param, long value) { Params.set(Params.OBJ, id, param, value); }
    @Override public int getId() { return id; }
    @Override public String getName() { return name; }
    @Override public String getMembersName() { return name; }
    @Override public void setName(String n) { name = n; }
    @Override public int getPrice() { return v[0]; }
    @Override public int getHaPrice() { return (int) (v[0] * 0.6f); }
    @Override public boolean isStackable() { return v[3] != 0; }
    @Override public boolean isMembers() { return v[4] != 0; }
    @Override public boolean isTradeable() { return v[5] != 0; }
    @Override public boolean isGeTradeable() { return v[5] != 0; }
    @Override public int getLinkedNoteId() { return v[6]; }
    @Override public int getNote() { return v[7]; }
    @Override public int getPlaceholderId() { return v[8]; }
    @Override public int getPlaceholderTemplateId() { return v[9]; }
    // Inventory icon view (ObjType +0xc0..+0xc8, static RE; unverified). Setters keep host-side values
    // (desktop's change the cached definition; the game's icons aren't redrawn from them here).
    /** Sub-ops (e.g. teleport lists under an inventory op) aren't read from the ObjType: null, which
     *  plugins treat as "no sub-ops" (Menu Entry Swapper's shift-click item configuration). */
    @Override public String[][] getSubops() { return null; }
    @Override public int getXan2d() { return v.length > 11 ? v[11] : 0; }
    @Override public int getYan2d() { return v.length > 12 ? v[12] : 0; }
    @Override public int getZan2d() { return v.length > 13 ? v[13] : 0; }
    @Override public void setXan2d(int a) { if (v.length > 11) v[11] = a; }
    @Override public void setYan2d(int a) { if (v.length > 12) v[12] = a; }
    @Override public void setZan2d(int a) { if (v.length > 13) v[13] = a; }
    @Override public String[] getInventoryActions() { return actions; }
    // ObjType +0x04 (mobile opcode 0x2c); verified gen 28 (bronze axe 2544, coins 2484 = cache)
    @Override public int getInventoryModel() { return inventoryModel; }
    @Override public void setInventoryModel(int m) { inventoryModel = m; }
    // ObjType recolour/retexture arrays (opcodes 40/41); recolours verified gen 28 (bronze axe 61->5652)
    @Override public short[] getColorToReplace() { return colorFrom; }
    @Override public void setColorToReplace(short[] c) { colorFrom = c; }
    @Override public short[] getColorToReplaceWith() { return colorTo; }
    @Override public void setColorToReplaceWith(short[] c) { colorTo = c; }
    @Override public short[] getTextureToReplace() { return textureFrom; }
    @Override public void setTextureToReplace(short[] t) { textureFrom = t; }
    @Override public short[] getTextureToReplaceWith() { return textureTo; }
    @Override public void setTextureToReplaceWith(short[] t) { textureTo = t; }
    // ObjType +0x2c4 / +0x2c8 (opcodes 113/114); read 0/0 for items whose cache value is 0 (gen 28)
    @Override public int getAmbient() { return ambient; }
    @Override public int getContrast() { return contrast; }
    @Override public int getShiftClickActionIndex() { return shiftClickIndex; }
    @Override public void setShiftClickActionIndex(int i) { shiftClickIndex = i; }

    @Override
    public String toString() {
        return "ItemComposition[" + id + " '" + name + "' price=" + v[0] + " stackable=" + isStackable() + " members=" + isMembers()
                + " tradeable?=" + isTradeable() + " note=" + v[7] + "/" + v[6] + " placeholder=" + v[9] + "/" + v[8]
                + " weight=" + v[1] + " model=" + inventoryModel + " recolors=" + (colorFrom == null ? 0 : colorFrom.length) + " actions=" + java.util.Arrays.toString(actions) + "]";
    }
}
