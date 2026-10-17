package net.runelite.host;

import java.util.HashMap;
import java.util.Map;
import net.runelite.api.widgets.JavaScriptCallback;
import net.runelite.api.widgets.Widget;

/**
 * Java onOp listeners that plugins set on widgets (Widget.setOnOpListener with a
 * JavaScriptCallback, e.g. Quest List's search button). The game's menu only lists op i of a
 * widget when its event flags (+0x2b0, bit i+1) allow it or it has a CS2 onOp listener, so while a
 * Java listener is set the flags get one bit per non-empty op (the widget's own bits are restored
 * when it is cleared). When such an entry is selected and no MenuOptionClicked subscriber consumed
 * it, the listener runs; the click is consumed (no script, no IF_BUTTON) only for an op a plugin
 * wrote with setAction (such as Quest List's "Open Search"). The game's own ops still go to the game, as on desktop: Better Teleport Menu sets an
 * empty listener on the teleport menu's options, and consuming their "Continue" stopped the
 * RESUME_PAUSEBUTTON (gen 22). Entries are keyed by (component, CC_CREATE index) and the game's Widget* so a slot
 * the game re-creates doesn't inherit a stale listener. Game thread.
 */
final class WidgetListeners {
    private WidgetListeners() {}

    private static final class Entry {
        Object[] onOp;
        long ptr;
        int ownFlags;
    }

    private static final Map<Long, Entry> entries = new HashMap<>();

    private static long key(int id, int index) { return (long) id << 32 | (index & 0xffffffffL); }

    static synchronized Object[] onOp(int id, int index) {
        Entry e = entries.get(key(id, index));
        return e != null && e.ptr == Native.widgetPtr(id, index) ? e.onOp : null;
    }

    static synchronized void setOnOp(int id, int index, Object[] listener) {
        long k = key(id, index), ptr = Native.widgetPtr(id, index);
        Entry e = entries.get(k);
        if (e != null && e.ptr != ptr) {
            entries.remove(k);
            e = null;
        }
        boolean set = listener != null && listener.length > 0 && listener[0] != null;
        if (!set) {
            if (e != null) {
                entries.remove(k);
                Native.widgetSetFlags(id, index, e.ownFlags);
            }
            return;
        }
        if (ptr == 0) return;
        if (e == null) {
            e = new Entry();
            e.ptr = ptr;
            e.ownFlags = Native.widgetFlags(id, index);
            entries.put(k, e);
        }
        e.onOp = listener;
        syncFlags(id, index);
    }

    /** After an op's text changed: the shown-op bits follow the non-empty ops while a listener is set. */
    static synchronized void syncFlags(int id, int index) {
        Entry e = entries.get(key(id, index));
        if (e == null || e.ptr != Native.widgetPtr(id, index)) return;
        String[] s = Native.widgetStrings(id, index);
        int bits = 0;
        for (int i = 2; s != null && i < s.length && i - 2 < 10; i++)
            if (s[i] != null && !s[i].isEmpty()) bits |= 1 << (i - 2 + 1);
        Native.widgetSetFlags(id, index, e.ownFlags | bits);
    }

    /** Ops (0-based) that plugins wrote with setAction, per widget slot and the game's Widget*. */
    private static final Map<Long, long[]> pluginOps = new HashMap<>();  // {ptr, bitmask}

    static synchronized void notePluginOp(int id, int index, int op, boolean set) {
        long k = key(id, index), ptr = Native.widgetPtr(id, index);
        long[] v = pluginOps.get(k);
        if (v == null || v[0] != ptr) pluginOps.put(k, v = new long[] {ptr, 0});
        if (set) v[1] |= 1L << op; else v[1] &= ~(1L << op);
    }

    private static synchronized boolean isPluginOp(int id, int index, int op) {
        long[] v = pluginOps.get(key(id, index));
        return v != null && v[0] == Native.widgetPtr(id, index) && op >= 0 && op < 64 && (v[1] & 1L << op) != 0;
    }

    /** Created children of `id` were deleted (or one is re-created at `index`; -1 = all). */
    static synchronized void forget(int id, int index) {
        if (index >= 0) { entries.remove(key(id, index)); return; }
        entries.keySet().removeIf(k -> (int) (k >>> 32) == id && (int) (long) k >= 0);
    }

    /**
     * After MenuOptionClicked was posted for a widget entry and not consumed: runs the widget's Java
     * onOp listener, if any. Returns true when the caller should consume the click: the op is one
     * the game's widget didn't have (only the plugin's listener can act on it).
     */
    static boolean dispatch(net.runelite.api.MenuEntry entry, net.runelite.api.Client client) {
        int id = entry.getParam1(), index = entry.getParam0();
        Entry e;
        synchronized (WidgetListeners.class) {
            e = entries.get(key(id, index));
            if ((e == null || e.ptr != Native.widgetPtr(id, index)) && index != -1) {
                index = -1;
                e = entries.get(key(id, -1));
            }
            if (e != null && e.ptr != Native.widgetPtr(id, index)) e = null;
        }
        Object[] l = e != null ? e.onOp : null;
        if (l == null || !(l[0] instanceof JavaScriptCallback)) return false;
        Widget source = index >= 0 ? new AndroidWidget.Dynamic(id, index) : new AndroidWidget(id);
        AndroidScriptEvent ev = new AndroidScriptEvent(client, l);
        ev.setSource(source);
        ev.setOp(entry.getIdentifier());
        int op = entry.getIdentifier();  // 1-based for CC_OP entries (the pause-button "Continue" is 0x80000000)
        boolean pluginOnly = op >= 1 && op <= 32 && isPluginOp(id, index, op - 1);
        android.util.Log.i(Host.TAG, "widget onOp listener: " + (id >>> 16) + ":" + (id & 0xffff) + "[" + index + "] op " + op + (pluginOnly ? " (plugin op, consumed)" : " (game op, passed on)"));
        ((JavaScriptCallback) l[0]).run(ev);
        return pluginOnly;
    }
}
