package net.runelite.host;

import java.util.ArrayList;
import java.util.List;

import android.util.Log;

import net.runelite.api.Menu;
import net.runelite.api.MenuEntry;

/**
 * The open menu. Reads come from the game's menu model each call; entries plugins create are
 * kept on the host side (shown in getMenuEntries, not yet inserted into the game's menu).
 */
final class AndroidMenu implements Menu {
    private final List<AndroidMenuEntry> added = new ArrayList<>();

    @Override public MenuEntry createMenuEntry(int idx) {
        AndroidMenuEntry e = new AndroidMenuEntry();
        e.synthetic = true;
        added.add(e);
        return e;
    }

    /**
     * While a tap is resolved (Host MENU_TAP) the entries are read once and plugins' reorders
     * (setMenuEntries) persist until it ends, as desktop's menu array does within a frame.
     */
    private MenuEntry[] session;
    /** setMenuEntries outside a session: the array and the cycle it holds for. */
    private MenuEntry[] frame;
    private int frameStamp;

    MenuEntry[] beginSession() {
        session = null;
        frame = null;  // a session starts from the game's menu
        session = getMenuEntries();
        return session.clone();
    }

    void endSession() { session = null; visible = -1; }

    /**
     * During the MenuEntryAdded pass: how many of the session's entries (bottom first) have been
     * added. Desktop posts MenuEntryAdded as each entry is inserted, so getMenuEntries() then ends
     * with the entry just added; plugins edit `getMenuEntries()[length - 1]` (Monster Menu HP).
     * -1 outside the pass.
     */
    private int visible = -1;

    /** Posts MenuEntryAdded for each of `entries` (the session's, bottom first) with desktop's growing menu. */
    void postAdded(net.runelite.client.eventbus.EventBus bus, MenuEntry[] entries) {
        visible = 0;
        try {
            for (MenuEntry m : entries) {
                visible++;
                bus.post(new net.runelite.api.events.MenuEntryAdded(m));
            }
        } finally {
            visible = -1;
        }
    }

    /** Desktop bank item layers whose items have an "Examine" entry (bank 12:12, group storage 724:10). */
    private static final int[] EXAMINE_LAYERS = {12 << 16 | 12, 724 << 16 | 10};

    /**
     * The mobile bank's item menus have no "Examine" (Withdraw-*, Placeholder, Cancel), which desktop
     * always has and plugins hook (Bank Tags adds "Edit-tags" on it). For such a menu a desktop-like
     * Examine entry is posted as MenuEntryAdded only (not shown); entries plugins create from it go
     * to the plugin strip like any created entry. // unverified: on the device.
     */
    private static void postMissingExamine(net.runelite.client.eventbus.EventBus bus, MenuEntry[] entries) {
        AndroidMenuEntry item = null;
        for (MenuEntry m : entries) {
            if (!(m instanceof AndroidMenuEntry) || ((AndroidMenuEntry) m).synthetic) continue;
            for (int layer : EXAMINE_LAYERS) {
                if (m.getParam1() != layer) continue;
                if ("Examine".equals(m.getOption())) return;
                if (item == null && m.getItemId() >= 0) item = (AndroidMenuEntry) m;
            }
        }
        if (item == null) return;
        AndroidMenuEntry ex = new AndroidMenuEntry();
        ex.synthetic = true;
        ex.setOption("Examine");
        ex.setTarget(item.getTarget());
        ex.setType(net.runelite.api.MenuAction.CC_OP_LOW_PRIORITY);
        ex.setIdentifier(10);
        ex.setParam0(item.getParam0());
        ex.setParam1(item.getParam1());
        ex.setItemId(item.getItemId());
        bus.post(new net.runelite.api.events.MenuEntryAdded(ex));
    }

    /**
     * Mobile side tab stones (toplevel_osm 601) and desktop's resizable ones (toplevel_osrs_stretch
     * 161), by gameval name: stone0..13 -> 601:{101,71,102,97,98,99,100,74,76,75,44,77,72,73} and
     * 161:{59..65,43..49}. Plugins add tab-stone options by desktop id (MenuManager managed menus:
     * Spellbook's "Enable spell reordering" on the magic stone 161:65).
     * Verified by tap on the device (gen 24): stone0 Combat Options, 1 Skills, 2 Achievement Diaries (desktop's quest
     * tab slot), 3 Inventory, 4 Worn Equipment, 5 Prayer, 6 Magic, 8 Account Management, 9 Friends List,
     * 11 Settings, 12 Emotes, 13 Music Player (601:103 is the Show More/Less toggle).
     * // unverified: stone7 (601:74, clan) and stone10 (601:44, logout): not on the mobile tab column.
     */
    private static final int[] MOBILE_STONES = {101, 71, 102, 97, 98, 99, 100, 74, 76, 75, 44, 77, 72, 73};
    private static final int[] DESKTOP_STONES = {59, 60, 61, 62, 63, 64, 65, 43, 44, 45, 46, 47, 48, 49};

    static int desktopStone(int component) {
        if (component >>> 16 != 601) return -1;
        int child = component & 0xffff;
        for (int i = 0; i < MOBILE_STONES.length; i++)
            if (MOBILE_STONES[i] == child) return 161 << 16 | DESKTOP_STONES[i];
        return -1;
    }

    /**
     * For game CC_OP entries on a mobile tab stone, a copy with desktop's resizable stone id as
     * param1 is posted as MenuEntryAdded only (not shown), so plugins keyed to desktop ids add their
     * entries (shown in the plugin strip).
     */
    static void postDesktopAliases(net.runelite.client.eventbus.EventBus bus, MenuEntry[] entries) {
        for (MenuEntry m : entries) {
            if (!(m instanceof AndroidMenuEntry) || ((AndroidMenuEntry) m).synthetic) continue;
            if (m.getType() != net.runelite.api.MenuAction.CC_OP) continue;
            int desk = desktopStone(m.getParam1());
            if (desk < 0) continue;
            AndroidMenuEntry a = new AndroidMenuEntry();
            a.synthetic = true;
            a.setOption(m.getOption());
            a.setTarget(m.getTarget());
            a.setType(net.runelite.api.MenuAction.CC_OP);
            a.setIdentifier(m.getIdentifier());
            a.setParam0(m.getParam0());
            a.setParam1(desk);
            bus.post(new net.runelite.api.events.MenuEntryAdded(a));
        }
    }

    /** While a just-opened menu is sorted as on desktop's last frame before it opened. */
    static boolean presorting;

    /**
     * Game thread, as a long-press menu opens: desktop sorts the menu each frame while it is closed
     * (PostMenuSort, where Menu Entry Swapper reorders) and opens it in that order. Run that sort now
     * with the menu reported closed, and if plugins reordered the game's entries, show them in that
     * order. Text edits from MenuEntryAdded are written back first. Returns whether it reordered.
     */
    boolean presort(net.runelite.client.eventbus.EventBus bus) {
        MenuEntry[] entries = beginSession();
        boolean reordered = false;
        try {
            postAdded(bus, entries);
            postMissingExamine(bus, entries);
            postDesktopAliases(bus, entries);
            presorting = true;
            try {
                // Desktop posts ClientTick on every frame, after that frame's MenuEntryAdded pass, so plugins
                // that edit the hover menu there (with no menu open) shape the menu that then opens: Bank Tag
                // Layouts adds "Enable layout (hub)" to a tag tab's entries in ClientTick. The mobile client has
                // no hover menu, so one ClientTick is posted here, with the menu reported closed.
                // Verified 2026-10-17 gen 25 (Bank Tag Layouts' entry in the plugin strip).
                bus.post(new net.runelite.api.events.ClientTick());
                bus.post(new net.runelite.api.events.PostMenuSort());
            } finally {
                presorting = false;
            }
            reordered = applyOrder(entries, "presort");
        } finally {
            endSession();
        }
        return reordered;
    }

    /**
     * Within a session, after plugins ran on `entries` (the session's, from beginSession): writes
     * option/target edits back into the game's menu, then, if the plugins' array (setMenuEntries)
     * is a permutation of the game's entries or a subset of them, orders or shortens the game's
     * menu to match: a permutation for Menu Entry Swapper's order, a subset when a plugin removed
     * entries (Not Empty drops a potion's "Empty" in PostMenuSort, No Examine drops Examine in
     * MenuOpened). The native side drops only childless entries. Returns whether the game's menu changed.
     */
    boolean applyOrder(MenuEntry[] entries, String when) {
        for (MenuEntry m : entries)
            if (m instanceof AndroidMenuEntry && ((AndroidMenuEntry) m).writeBack()) Log.i(Host.TAG, "menu entry rewritten: " + m);
        MenuEntry[] sorted = getMenuEntries();
        List<Integer> order = new ArrayList<>();
        for (int i = sorted.length - 1; i >= 0; i--) {  // top first
            MenuEntry m = sorted[i];
            if (m instanceof AndroidMenuEntry && !((AndroidMenuEntry) m).synthetic && ((AndroidMenuEntry) m).nativeIndex >= 0)
                order.add(((AndroidMenuEntry) m).nativeIndex);
        }
        int n = 0;
        for (MenuEntry m : entries) if (m instanceof AndroidMenuEntry && !((AndroidMenuEntry) m).synthetic) n++;
        boolean identity = order.size() == n;
        for (int i = 0; i < order.size(); i++) identity &= order.get(i) == i;
        if (identity || order.isEmpty() || order.size() > n || new java.util.HashSet<>(order).size() != order.size()) return false;
        int[] o = new int[order.size()];
        for (int i = 0; i < o.length; i++) o[i] = order.get(i);
        boolean changed = Native.menuReorder(o);
        Log.i(Host.TAG, "menu reorder (" + when + ") " + java.util.Arrays.toString(o) + " of " + n + ": " + changed);
        return changed;
    }

    @Override public MenuEntry[] getMenuEntries() {
        if (session != null)
            return visible >= 0 ? java.util.Arrays.copyOf(session, Math.min(visible, session.length)) : session.clone();
        if (frame != null && frameStamp == Host.cycleStamp) return frame.clone();
        AndroidMenuEntry[] game = AndroidMenuEntry.fromNative(Native.menuEntries());
        // RuneLite order: the last element is the top (default) entry.
        List<MenuEntry> out = new ArrayList<>(added);
        for (int i = game.length - 1; i >= 0; i--) out.add(game[i]);
        // With no menu open the game's list can still hold a lone Cancel (left by the last build).
        boolean onlyCancel = game.length == 0 || (game.length == 1 && game[0].getType() == net.runelite.api.MenuAction.CANCEL);
        if (onlyCancel && !Native.menuOpen()) {
            // A driving plugin's virtual mouse (DevInput): the menu the game builds there.
            AndroidMenuEntry[] dev = DevMenu.hoverMenu();
            if (dev != null) {
                out = new ArrayList<>(added);
                for (int i = dev.length - 1; i >= 0; i--) out.add(dev[i]);
                return out.toArray(new MenuEntry[0]);
            }
            AndroidMenuEntry h = hoverEntry();
            if (h != null) out.add(h);
        }
        return out.toArray(new MenuEntry[0]);
    }

    // --- hover entry: on desktop, with no menu open, getMenuEntries() holds the entries for what
    // the mouse is over (Item Stats, Interact Highlight read the top one). Here the touch point
    // stands in for the mouse (Sidebar.hoverPoint); an inventory slot under it gives the entry its
    // first op would have. Computed once per cycle.
    private static int hoverStamp = -1;
    private static AndroidMenuEntry hover;
    static final int INVENTORY = 149 << 16;

    static AndroidMenuEntry hoverEntry() {
        if (hoverStamp == Host.cycleStamp) return hover;
        hoverStamp = Host.cycleStamp;
        hover = null;
        if (Host.client() == null) return null;
        net.runelite.api.Point m = Host.client().getMouseCanvasPosition();
        if (m.getX() < 0) return null;
        Slot s = slotAt(m);
        if (s != null) hover = entry(s.widget, s.param0, s.param1, s.itemId);
        return hover;
    }

    /** An item slot under a canvas point: the widget holding its ops, menu params and item id. */
    static final class Slot {
        final net.runelite.api.widgets.Widget widget;
        final int param0, param1, itemId;
        Slot(net.runelite.api.widgets.Widget widget, int param0, int param1, int itemId) {
            this.widget = widget; this.param0 = param0; this.param1 = param1; this.itemId = itemId;
        }
    }

    static Slot slotAt(net.runelite.api.Point m) {
        // Item containers whose slots are dynamic children: inventory, bank items, the inventory beside the bank.
        for (int container : ITEM_CONTAINERS) {
            AndroidWidget c = new AndroidWidget(container);
            if (c.isHidden()) continue;
            net.runelite.api.widgets.Widget[] slots = c.getDynamicChildren();
            for (net.runelite.api.widgets.Widget w : slots) {
                if (w == null || w.getItemId() < 0 || w.isSelfHidden() || !w.contains(m)) continue;
                return new Slot(w, w.getIndex(), container, w.getItemId());
            }
        }
        // Worn equipment: static slot components of 387 whose child 1 holds the item.
        if (!new AndroidWidget(EQUIPMENT << 16).isHidden()) {
            for (int i = 0; i < 40; i++) {
                AndroidWidget w = new AndroidWidget(EQUIPMENT << 16 | i);
                if (w.isSelfHidden() || !w.contains(m)) continue;
                net.runelite.api.widgets.Widget item = w.getChild(1);
                if (item == null || item.getItemId() < 0 || w.isHidden()) continue;
                return new Slot(w, -1, w.getId(), item.getItemId());
            }
        }
        return null;
    }

    /**
     * The menu an item slot under the point has, top first, as desktop builds it from the widget's
     * ops (CC_OP identifier = op index + 1, ops past 5 low priority) with Cancel last; null if no
     * item slot is there. For a driving plugin's virtual mouse over the game UI, where the scene
     * pick only finds what lies beneath.
     */
    static AndroidMenuEntry[] slotMenu(java.awt.Point p) {
        Slot s = slotAt(new net.runelite.api.Point(p.x, p.y));
        if (s == null) return null;
        List<AndroidMenuEntry> out = new ArrayList<>();
        String[] actions = s.widget.getActions();
        if (actions != null)
            for (int i = 0; i < actions.length; i++) {
                if (actions[i] == null || actions[i].isEmpty()) continue;
                AndroidMenuEntry e = entry(s.widget, s.param0, s.param1, s.itemId);
                e.option = actions[i];
                e.identifier = i + 1;
                e.type = i + 1 > 5 ? net.runelite.api.MenuAction.CC_OP_LOW_PRIORITY : net.runelite.api.MenuAction.CC_OP;
                out.add(e);
            }
        AndroidMenuEntry cancel = new AndroidMenuEntry();
        cancel.option = "Cancel";
        cancel.target = "";
        cancel.type = net.runelite.api.MenuAction.CANCEL;
        out.add(cancel);
        return out.toArray(new AndroidMenuEntry[0]);
    }

    static final int EQUIPMENT = 387;
    /** 149:0 inventory, 12:12 bank items, 15:3 inventory while the bank is open. */
    static final int[] ITEM_CONTAINERS = {INVENTORY, 12 << 16 | 12, 15 << 16 | 3};

    /** The entry the widget's first op would have (CC_OP, identifier 1). */
    private static AndroidMenuEntry entry(net.runelite.api.widgets.Widget w, int param0, int param1, int itemId) {
        AndroidMenuEntry e = new AndroidMenuEntry();
        String[] actions = w.getActions();
        String name = w.getName();
        String first = null;
        if (actions != null) for (String a : actions) if (a != null && !a.isEmpty()) { first = a; break; }
        e.option = first != null ? first : "Use";
        e.target = name != null ? name : "";
        e.type = net.runelite.api.MenuAction.CC_OP;
        e.identifier = 1;
        e.param0 = param0;
        e.param1 = param1;
        e.itemId = itemId;
        e.raw[9] = AndroidMenuEntry.T_WIDGET << 16;
        return e;
    }


    @Override public void setMenuEntries(MenuEntry[] entries) {
        if (session != null && visible >= 0) {
            // Within the MenuEntryAdded pass the entries not yet added stay above the plugin's array.
            int rest = Math.max(0, session.length - visible);
            MenuEntry[] grown = java.util.Arrays.copyOf(entries, entries.length + rest);
            System.arraycopy(session, session.length - rest, grown, entries.length, rest);
            session = grown;
            visible = entries.length;
        } else if (session != null) session = entries.clone();
        else {
            // Outside a menu session, as desktop's menu array: what was set is the menu until the
            // game's next cycle rebuilds it (the game's own menu is only written within a session).
            frame = entries.clone();
            frameStamp = Host.cycleStamp;
        }
        added.clear();
        for (MenuEntry e : entries)
            if (e instanceof AndroidMenuEntry && ((AndroidMenuEntry) e).synthetic) added.add((AndroidMenuEntry) e);
    }

    @Override public void removeMenuEntry(MenuEntry e) { added.remove(e); }

    void clearAdded() { added.clear(); }

    /**
     * Game thread, as a menu opens: desktop's menu builder adds one entry per player option a plugin
     * registered (MenuManager.addPlayerMenuItem: client.getPlayerOptions()[4..7], type RUNELITE_PLAYER)
     * for each player in the menu. The game's player ops come from its own list, so those options are
     * added as plugin entries (shown in the strip under the game's menu), once per player.
     * Returns the entries added.
     */
    List<AndroidMenuEntry> addPlayerOptions(String[] options, int[] types) {
        List<AndroidMenuEntry> out = new ArrayList<>();
        AndroidMenuEntry[] game = AndroidMenuEntry.fromNative(Native.menuEntries());
        java.util.Set<Integer> seen = new java.util.HashSet<>();
        for (AndroidMenuEntry g : game) {  // top first
            if (!g.isPlayerEntry() || g.type == net.runelite.api.MenuAction.RUNELITE_PLAYER || !seen.add(g.identifier)) continue;
            for (int i = 0; i < options.length; i++) {
                if (options[i] == null || types[i] != net.runelite.api.MenuAction.RUNELITE_PLAYER.getId()) continue;
                boolean dup = false;
                for (AndroidMenuEntry a : added)
                    dup |= a.type == net.runelite.api.MenuAction.RUNELITE_PLAYER && a.identifier == g.identifier && options[i].equals(a.option);
                if (dup) continue;
                AndroidMenuEntry e = (AndroidMenuEntry) createMenuEntry(-1);
                e.option = options[i];
                e.target = g.target;
                e.type = net.runelite.api.MenuAction.RUNELITE_PLAYER;
                e.identifier = g.identifier;
                e.worldViewId = net.runelite.api.WorldView.TOPLEVEL;
                out.add(e);
            }
        }
        return out;
    }

    // --- plugin entries under the game's menu (native strip; see overlay.cpp plugin_menu_frame) ---

    private static List<AndroidMenuEntry> shown = new ArrayList<>();

    /** Game thread, after MenuOpened: shows the entries plugins created, under the game's menu. */
    void publishPluginEntries() {
        List<AndroidMenuEntry> l = new ArrayList<>(added);
        // RuneLite order puts the newest entry (index -1) at the top; show the topmost first.
        java.util.Collections.reverse(l);
        show(l);
    }

    /** Uptime (ms) until which a strip shown for a plain tap stays up (no game menu is open then). */
    private static long tapStripUntil;

    /**
     * Game thread, during a plain tap on a widget op: entries plugins created while the tap's
     * MenuEntryAdded pass ran would be right-click options on desktop (Bank Heatmap's toggles on
     * the bank menu button). The tap still runs as usual; these are shown as the plugin strip at
     * the tap point for 12 s (6 s proved too short to read and tap).
     */
    void publishTapEntries(int x, int y) {
        List<AndroidMenuEntry> l = new ArrayList<>(added);
        if (l.isEmpty()) return;
        java.util.Collections.reverse(l);
        tapStripUntil = android.os.SystemClock.uptimeMillis() + 12000;
        show(l, new java.awt.Rectangle(Math.max(0, x - MIN_WIDTH / 2), y, MIN_WIDTH, 0));
    }

    private static void show(List<AndroidMenuEntry> entries) {
        tapStripUntil = 0;
        show(entries, bounds());
    }

    private static void show(List<AndroidMenuEntry> entries, java.awt.Rectangle r) {
        shown = entries;
        if (shown.isEmpty() || r == null) {
            Native.pluginMenu(null, 0, 0, 0, 0);
            return;
        }
        String[] labels = new String[shown.size()];
        for (int i = 0; i < labels.length; i++) {
            AndroidMenuEntry e = shown.get(i);
            labels[i] = (net.runelite.client.util.Text.removeTags(e.getOption()) + " "
                + net.runelite.client.util.Text.removeTags(e.getTarget())).trim()
                + (e.subMenu != null && !e.subMenu.added.isEmpty() ? "  >" : "");
        }
        int canvasH = Host.client().getCanvasHeight();
        int y = r.y + r.height;
        if (y + ROW * labels.length + 8 > canvasH) y = Math.max(0, r.y - ROW * labels.length - 8);
        Native.pluginMenu(labels, r.x, y, r.width, canvasH);
        Log.i(Host.TAG, "plugin menu entries: " + java.util.Arrays.toString(labels));
    }

    /** Game thread, every cycle: hides the strip once the game's menu has closed. */
    static void onCycle() {
        if (!shown.isEmpty() && !Native.menuOpen() && android.os.SystemClock.uptimeMillis() > tapStripUntil) {
            shown = new ArrayList<>();
            Native.pluginMenu(null, 0, 0, 0, 0);
        }
    }

    /** Game thread: a plugin entry was tapped. Runs it as RuneLite does, then closes the menu. */
    static void tapped(int index, net.runelite.client.eventbus.EventBus bus) {
        List<AndroidMenuEntry> list = shown;
        if (index < 0 || index >= list.size()) return;
        AndroidMenuEntry e = list.get(index);
        if (e.subMenu != null && !e.subMenu.added.isEmpty()) {
            // A submenu replaces the strip's entries; the game menu stays open.
            List<AndroidMenuEntry> sub = new ArrayList<>(e.subMenu.added);
            java.util.Collections.reverse(sub);
            show(sub);
            return;
        }
        shown = new ArrayList<>();
        boolean fromTap = tapStripUntil != 0;
        tapStripUntil = 0;
        Native.pluginMenu(null, 0, 0, 0, 0);
        Log.i(Host.TAG, "plugin menu entry tapped: " + e);
        try {
            if (e.onClick != null) e.onClick.accept(e);
            if (bus != null) bus.post(new net.runelite.api.events.MenuOptionClicked(e));
        } finally {
            if (!fromTap) Log.i(Host.TAG, "menu close: " + Native.menuClose());  // a tap strip has no game menu
        }
    }

    @Override public int getMenuX() { java.awt.Rectangle r = bounds(); return r != null ? r.x : 0; }
    @Override public int getMenuY() { java.awt.Rectangle r = bounds(); return r != null ? r.y : 0; }
    @Override public int getMenuWidth() { java.awt.Rectangle r = bounds(); return r != null ? r.width : 0; }
    @Override public int getMenuHeight() { java.awt.Rectangle r = bounds(); return r != null ? r.height : 0; }

    // Android menu layout in UI-canvas units (894x503 on a 1920x1080 screen), measured from
    // screenshots: centred on the long-press point, top at it, a 29-unit "Choose Option" header and
    // 25 units per entry (Cancel included), at least 129 wide; ~6.9 units per character of
    // "option target" plus padding when wider (estimate).
    private static final int MIN_WIDTH = 129, HEADER = 29, ROW = 25;

    /** The open menu's rectangle on the UI canvas, or null when no menu is shown. */
    static java.awt.Rectangle bounds() {
        int[] p = Native.menuPoint();
        if (p == null) return null;
        AndroidMenuEntry[] entries = AndroidMenuEntry.fromNative(Native.menuEntries());
        int longest = 0;
        for (AndroidMenuEntry e : entries) {
            String t = (e.getOption() + " " + e.getTarget()).replaceAll("<[^>]*>", "").trim();
            longest = Math.max(longest, t.length());
        }
        int w = Math.max(MIN_WIDTH, (int) Math.ceil(longest * 6.9) + 10);
        int h = HEADER + ROW * entries.length;
        int x = p[0] - w / 2, y = p[1];
        java.awt.Dimension c = new java.awt.Dimension(Host.client() != null ? Host.client().getCanvasWidth() : 894,
                Host.client() != null ? Host.client().getCanvasHeight() : 503);
        x = Math.max(0, Math.min(x, c.width - w));
        y = Math.max(0, Math.min(y, c.height - h));
        return new java.awt.Rectangle(x, y, w, h);
    }
}
