package net.runelite.host;

import java.util.function.Consumer;

import net.runelite.api.Actor;
import net.runelite.api.Menu;
import net.runelite.api.MenuAction;
import net.runelite.api.MenuEntry;
import net.runelite.api.NPC;
import net.runelite.api.Player;
import net.runelite.api.widgets.Widget;

/**
 * A menu entry as read from the game's menu model (see game::read_menu), or created by a plugin.
 * The game keeps entries as std::function callbacks plus display data, so identifier/params are
 * decoded from the entry's parameter block where the layout is known (docs/offsets.md, Menus).
 */
final class AndroidMenuEntry implements MenuEntry {
    String option = "", target = "";
    int identifier, param0, param1, itemId = -1, worldViewId = -1;
    MenuAction type = MenuAction.CC_OP;
    boolean forceLeftClick, deprioritized;
    Consumer<MenuEntry> onClick;
    /** Native data: entry kind byte and the ten parameter words. */
    int kind = -1;
    int[] raw = new int[10];
    boolean hasChildren;
    /** Created by a plugin (not in the game's menu). */
    boolean synthetic;

    static AndroidMenuEntry[] fromNative(Object[] data) {
        if (data == null) return new AndroidMenuEntry[0];
        int[] ints = (int[]) data[0];
        String[] strs = (String[]) data[1];
        int n = strs.length / 2;
        AndroidMenuEntry[] out = new AndroidMenuEntry[n];
        for (int i = 0; i < n; i++) {
            AndroidMenuEntry e = new AndroidMenuEntry();
            e.option = strs[2 * i] != null ? strs[2 * i] : "";
            e.target = strs[2 * i + 1] != null ? strs[2 * i + 1] : "";
            e.kind = ints[12 * i];
            e.hasChildren = ints[12 * i + 1] != 0;
            System.arraycopy(ints, 12 * i + 2, e.raw, 0, 10);
            e.decode();
            out[i] = e;
        }
        return out;
    }

    /** Maps the game's entry kinds onto RuneLite's MenuAction/identifier/params (verified ones only). */
    private void decode() {
        if ("Walk here".equals(option)) {
            type = MenuAction.WALK;
        } else if ("Cancel".equals(option)) {
            type = MenuAction.CANCEL;
        } else if ("Examine".equals(option)) {
            type = MenuAction.EXAMINE_OBJECT;
        }
    }

    @Override public String getOption() { return option; }
    @Override public MenuEntry setOption(String s) { option = s; return this; }
    @Override public String getTarget() { return target; }
    @Override public MenuEntry setTarget(String s) { target = s; return this; }
    @Override public int getIdentifier() { return identifier; }
    @Override public MenuEntry setIdentifier(int i) { identifier = i; return this; }
    @Override public MenuAction getType() { return type; }
    @Override public MenuEntry setType(MenuAction t) { type = t; return this; }
    @Override public int getParam0() { return param0; }
    @Override public MenuEntry setParam0(int p) { param0 = p; return this; }
    @Override public int getParam1() { return param1; }
    @Override public MenuEntry setParam1(int p) { param1 = p; return this; }
    @Override public boolean isForceLeftClick() { return forceLeftClick; }
    @Override public MenuEntry setForceLeftClick(boolean b) { forceLeftClick = b; return this; }
    @Override public int getWorldViewId() { return worldViewId; }
    @Override public MenuEntry setWorldViewId(int id) { worldViewId = id; return this; }
    @Override public boolean isDeprioritized() { return deprioritized; }
    @Override public MenuEntry setDeprioritized(boolean b) { deprioritized = b; return this; }
    @Override public MenuEntry onClick(Consumer<MenuEntry> c) { onClick = c; return this; }
    @Override public Consumer<MenuEntry> onClick() { return onClick; }
    @Override public boolean isItemOp() { return false; }
    @Override public int getItemOp() { return -1; }
    @Override public int getItemId() { return itemId; }
    @Override public MenuEntry setItemId(int id) { itemId = id; return this; }
    @Override public Widget getWidget() { return null; }
    @Override public NPC getNpc() { return null; }
    @Override public Player getPlayer() { return null; }
    @Override public Actor getActor() { return null; }
    @Override public Menu getSubMenu() { return null; }
    @Override public Menu createSubMenu() { return new AndroidMenu(); }
    @Override public void deleteSubMenu() {}

    @Override public String toString() {
        return "MenuEntry(" + option + " | " + target + " kind=" + kind + " raw=" + java.util.Arrays.toString(raw) + ")";
    }
}
