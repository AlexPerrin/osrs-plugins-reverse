package net.runelite.host;

import java.util.ArrayList;
import java.util.List;

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

    @Override public int getMenuX() { return 0; }
    @Override public int getMenuY() { return 0; }
    @Override public int getMenuWidth() { return 0; }
    @Override public int getMenuHeight() { return 0; }
}
