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

    @Override public MenuEntry[] getMenuEntries() {
        AndroidMenuEntry[] game = AndroidMenuEntry.fromNative(Native.menuEntries());
        // RuneLite order: the last element is the top (default) entry.
        List<MenuEntry> out = new ArrayList<>(added);
        for (int i = game.length - 1; i >= 0; i--) out.add(game[i]);
        return out.toArray(new MenuEntry[0]);
    }

    @Override public void setMenuEntries(MenuEntry[] entries) {
        added.clear();
        for (MenuEntry e : entries)
            if (e instanceof AndroidMenuEntry && ((AndroidMenuEntry) e).synthetic) added.add((AndroidMenuEntry) e);
    }

    @Override public void removeMenuEntry(MenuEntry e) { added.remove(e); }

    void clearAdded() { added.clear(); }

    // --- plugin entries under the game's menu (native strip; see overlay.cpp plugin_menu_frame) ---

    private static List<AndroidMenuEntry> shown = new ArrayList<>();

    /** Game thread, after MenuOpened: shows the entries plugins created, under the game's menu. */
    void publishPluginEntries() {
        shown = new ArrayList<>(added);
        // RuneLite order puts the newest entry (index -1) at the top; show the topmost first.
        java.util.Collections.reverse(shown);
        java.awt.Rectangle r = bounds();
        if (shown.isEmpty() || r == null) {
            Native.pluginMenu(null, 0, 0, 0, 0);
            return;
        }
        String[] labels = new String[shown.size()];
        for (int i = 0; i < labels.length; i++) {
            AndroidMenuEntry e = shown.get(i);
            labels[i] = (net.runelite.client.util.Text.removeTags(e.getOption()) + " "
                + net.runelite.client.util.Text.removeTags(e.getTarget())).trim();
        }
        int canvasH = Host.client().getCanvasHeight();
        int y = r.y + r.height;
        if (y + ROW * labels.length + 8 > canvasH) y = Math.max(0, r.y - ROW * labels.length - 8);
        Native.pluginMenu(labels, r.x, y, r.width, canvasH);
        Log.i(Host.TAG, "plugin menu entries: " + java.util.Arrays.toString(labels));
    }

    /** Game thread, every cycle: hides the strip once the game's menu has closed. */
    static void onCycle() {
        if (!shown.isEmpty() && !Native.menuOpen()) {
            shown = new ArrayList<>();
            Native.pluginMenu(null, 0, 0, 0, 0);
        }
    }

    /** Game thread: a plugin entry was tapped. Runs it as RuneLite does, then closes the menu. */
    static void tapped(int index, net.runelite.client.eventbus.EventBus bus) {
        List<AndroidMenuEntry> list = shown;
        if (index < 0 || index >= list.size()) return;
        AndroidMenuEntry e = list.get(index);
        shown = new ArrayList<>();
        Native.pluginMenu(null, 0, 0, 0, 0);
        Log.i(Host.TAG, "plugin menu entry tapped: " + e);
        try {
            if (e.onClick != null) e.onClick.accept(e);
            if (bus != null) bus.post(new net.runelite.api.events.MenuOptionClicked(e));
        } finally {
            Log.i(Host.TAG, "menu close: " + Native.menuClose());
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
