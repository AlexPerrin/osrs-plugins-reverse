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
    int npcId = -1;
    /** Created by a plugin (not in the game's menu). */
    boolean synthetic;
    /** Position in the game's top-level node list (Native.menuEntries order), -1 if none. */
    int nativeIndex = -1;
    /** Option/target as read, to detect plugin edits (MenuEntryAdded handlers recolour targets). */
    String readOption = "", readTarget = "";

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
            e.nativeIndex = i;
            e.readOption = e.option;
            e.readTarget = e.target;
            e.kind = ints[12 * i];
            e.hasChildren = ints[12 * i + 1] != 0;
            System.arraycopy(ints, 12 * i + 2, e.raw, 0, 10);
            e.decode();
            e.readTarget = e.target;
            out[i] = e;
        }
        return out;
    }

    // raw[] = entry +0x120..+0x147 (verified on device 2026-10-15, docs/offsets.md "Menu entry"):
    // [2] id (NPC/loc/obj type id, or widget component id), [3] NPC index / widget child slot,
    // [5] op (1-based for world entries, 0-based widget op; 0x7ffffffe = Examine, 0x7fffffff none),
    // [6] plane, [7]/[8] world x/y, [9] = u16 world view | target type << 16 | ? << 24.
    static final int T_CANCEL = 0, T_WALK = 1, T_NPC = 2, T_LOC = 3, T_OBJ = 4, T_WIDGET = 7;
    static final int OP_EXAMINE = 0x7ffffffe;

    int targetType() { return raw[9] >>> 16 & 0xff; }
    int op() { return raw[5]; }
    int worldX() { return raw[7]; }
    int worldY() { return raw[8]; }

    /** Maps the game's entry onto RuneLite's MenuAction/identifier/params. */
    void decode() {
        worldViewId = net.runelite.api.WorldView.TOPLEVEL;  // only the top-level world view is mapped
        int op = op();
        switch (targetType()) {
            case T_CANCEL:
                type = MenuAction.CANCEL;
                break;
            case T_WALK:
                type = MenuAction.WALK;
                sceneParams();
                break;
            case T_NPC:
                identifier = raw[3];
                npcId = raw[2];
                type = op == OP_EXAMINE ? MenuAction.EXAMINE_NPC : npcOp(op);
                break;
            case T_LOC:
                identifier = raw[2];
                sceneParams();
                type = op == OP_EXAMINE ? MenuAction.EXAMINE_OBJECT : locOp(op);
                break;
            case T_OBJ:  // ground item: [2] obj id, [3] quantity, op 3 = Take (seen on device 2026-10-15)
                identifier = raw[2];
                // The game appends " (n)" for stacks; RuneLite's target is the name alone (Ground Items
                // adds its own quantity, which showed "Coins (500) (500)").
                if (raw[3] > 1 && target.endsWith(" (" + raw[3] + ")"))
                    target = target.substring(0, target.length() - (" (" + raw[3] + ")").length());
                sceneParams();
                type = op == OP_EXAMINE ? MenuAction.EXAMINE_ITEM_GROUND : groundOp(op);
                break;
            case T_WIDGET:
                param0 = raw[3];
                param1 = raw[2];
                if ("Use".equals(option)) {
                    type = MenuAction.WIDGET_TARGET;
                    identifier = 0;
                } else {
                    identifier = op + 1;
                    type = identifier > 5 ? MenuAction.CC_OP_LOW_PRIORITY : MenuAction.CC_OP;
                }
                break;
            default:
                // Players: type byte not seen yet.
                identifier = raw[2];
                sceneParams();
                type = MenuAction.UNKNOWN;
                break;
        }
    }

    private void sceneParams() {
        net.runelite.api.WorldView v = Host.client() != null ? Host.client().getTopLevelWorldView() : null;
        if (v == null || worldX() == -1) return;  // plain "Walk here" taps carry no tile
        param0 = worldX() - v.getBaseX();
        param1 = worldY() - v.getBaseY();
    }

    private static MenuAction npcOp(int op) {
        switch (op) {
            case 1: return MenuAction.NPC_FIRST_OPTION;
            case 2: return MenuAction.NPC_SECOND_OPTION;
            case 3: return MenuAction.NPC_THIRD_OPTION;
            case 4: return MenuAction.NPC_FOURTH_OPTION;
            case 5: return MenuAction.NPC_FIFTH_OPTION;
            default: return MenuAction.UNKNOWN;
        }
    }

    private static MenuAction groundOp(int op) {
        switch (op) {
            case 1: return MenuAction.GROUND_ITEM_FIRST_OPTION;
            case 2: return MenuAction.GROUND_ITEM_SECOND_OPTION;
            case 3: return MenuAction.GROUND_ITEM_THIRD_OPTION;
            case 4: return MenuAction.GROUND_ITEM_FOURTH_OPTION;
            case 5: return MenuAction.GROUND_ITEM_FIFTH_OPTION;
            default: return MenuAction.UNKNOWN;
        }
    }

    private static MenuAction locOp(int op) {
        switch (op) {
            case 1: return MenuAction.GAME_OBJECT_FIRST_OPTION;
            case 2: return MenuAction.GAME_OBJECT_SECOND_OPTION;
            case 3: return MenuAction.GAME_OBJECT_THIRD_OPTION;
            case 4: return MenuAction.GAME_OBJECT_FOURTH_OPTION;
            case 5: return MenuAction.GAME_OBJECT_FIFTH_OPTION;
            default: return MenuAction.UNKNOWN;
        }
    }

    /** Writes option/target edits made by plugins back into the open game menu. */
    boolean writeBack() {
        if (synthetic || nativeIndex < 0) return false;
        String o = option.equals(readOption) ? null : option, t = target.equals(readTarget) ? null : target;
        if (o == null && t == null) return false;
        boolean ok = Native.menuSetText(nativeIndex, o, t);
        if (ok) { readOption = option; readTarget = target; }
        return ok;
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
    @Override public Widget getWidget() {
        if (targetType() != T_WIDGET || Host.client() == null) return null;
        Widget w = Host.client().getWidget(param1);
        return w != null && param0 >= 0 && w.getChild(param0) != null ? w.getChild(param0) : w;
    }
    @Override public NPC getNpc() {
        if (targetType() != T_NPC || Host.client() == null) return null;
        net.runelite.api.WorldView v = Host.client().getTopLevelWorldView();
        return v != null ? v.npcs().byIndex(identifier) : null;
    }
    @Override public Player getPlayer() { return null; }
    @Override public Actor getActor() { return getNpc(); }
    /** Plugin-created submenu (shown in the plugin strip when this entry is tapped). */
    AndroidMenu subMenu;
    @Override public Menu getSubMenu() { return subMenu; }
    @Override public Menu createSubMenu() { if (subMenu == null) subMenu = new AndroidMenu(); return subMenu; }
    @Override public void deleteSubMenu() { subMenu = null; }

    @Override public String toString() {
        return "MenuEntry(" + option + " | " + target + " kind=" + kind + " type=" + type + " id=" + identifier + " p0=" + param0 + " p1=" + param1
            + " raw=" + java.util.Arrays.toString(raw) + ")";
    }
}
