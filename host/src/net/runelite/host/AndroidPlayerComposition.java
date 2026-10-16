package net.runelite.host;

import java.util.Arrays;

import net.runelite.api.kit.KitType;

/**
 * PlayerComposition snapshot from the game's appearance object (Player +0x728, docs/offsets.md
 * "Player appearance"). Equipment ids use RuneLite's encoding: 0 empty, kit + 256, item + 2048.
 * Colour/texture overrides aren't read.
 */
final class AndroidPlayerComposition implements net.runelite.api.PlayerComposition {
    private final int[] equipment, kits, colors;
    private final int gender;
    private int npcId;

    AndroidPlayerComposition(int[] equipment, int[] kits, int[] colors, int gender, int npcId) {
        this.equipment = equipment;
        this.kits = kits;
        this.colors = colors;
        this.gender = gender;
        this.npcId = npcId;
    }

    boolean same(int[] e, int[] k, int[] c, int g, int n) {
        return g == gender && n == npcId && Arrays.equals(e, equipment) && Arrays.equals(k, kits) && Arrays.equals(c, colors);
    }

    @Override public boolean isFemale() { return gender == 1; }
    @Override public int getGender() { return gender; }
    @Override public int[] getColors() { return colors; }
    @Override public int[] getEquipmentIds() { return equipment; }

    @Override
    public int getEquipmentId(KitType type) {
        int id = equipment[type.getIndex()];
        return id >= ITEM_OFFSET ? id - ITEM_OFFSET : -1;
    }

    @Override
    public int getKitId(KitType type) {
        int id = equipment[type.getIndex()];
        return id >= KIT_OFFSET && id < ITEM_OFFSET ? id - KIT_OFFSET : -1;
    }

    @Override public void setHash() { }
    @Override public int getTransformedNpcId() { return npcId; }
    @Override public void setTransformedNpcId(int id) { npcId = id; }
    @Override public net.runelite.api.ColorTextureOverride[] getColorTextureOverrides() { return null; }
    @Override public net.runelite.api.ColorTextureOverride getColorTextureOverride(KitType type) { return null; }
    @Override public net.runelite.api.ColorTextureOverride createColorTextureOverride(KitType type, int n) { return null; }
    @Override public void removeColorTextureOverride(KitType type) { }

    @Override
    public String toString() {
        return "PlayerComposition[equipment=" + Arrays.toString(equipment) + " gender=" + gender + " npc=" + npcId + "]";
    }
}
