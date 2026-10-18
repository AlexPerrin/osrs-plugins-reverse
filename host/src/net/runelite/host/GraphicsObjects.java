package net.runelite.host;

import android.util.Log;
import net.runelite.api.events.GraphicsObjectCreated;
import net.runelite.client.eventbus.EventBus;

/**
 * The top-level WorldView's graphics objects (map spotanims) as WorldView/Client.getGraphicsObjects,
 * with GraphicsObjectCreated per new object. `setprop debug.osrsrl.projlog 1` logs new objects and
 * their raw fields (+0x240..+0x2a0) when they change, for locating the frame and finished flag.
 */
final class GraphicsObjects implements net.runelite.api.Deque<net.runelite.api.GraphicsObject> {
    private static final int INTS = 32;
    private final java.util.LinkedHashMap<Long, AndroidGraphicsObject> live = new java.util.LinkedHashMap<>();

    void poll(EventBus bus) {
        int[] r = Native.graphicsObjects();
        if (r == null) return;
        boolean log = "1".equals(Native.property("debug.osrsrl.projlog"));
        java.util.LinkedHashMap<Long, AndroidGraphicsObject> next = new java.util.LinkedHashMap<>();
        java.util.List<AndroidGraphicsObject> added = new java.util.ArrayList<>();
        for (int o = 0; o + INTS <= r.length; o += INTS) {
            long obj = (r[o] & 0xffffffffL) | ((long) r[o + 1] << 32);
            AndroidGraphicsObject g = live.get(obj);
            int[] before = g == null ? null : g.raw.clone();
            if (g == null) {
                g = new AndroidGraphicsObject(obj);
                added.add(g);
            }
            g.update(r, o);
            next.put(obj, g);
            if (log && (before == null || !java.util.Arrays.equals(before, g.raw))) {
                StringBuilder sb = new StringBuilder(before == null ? "graphics object " : "graphics object upd ").append(g).append(" raw+240:");
                for (int v : g.raw) sb.append(String.format(" %08x", v));
                Log.i(Host.TAG, sb.toString());
            }
        }
        if (log) for (Long k : live.keySet()) if (!next.containsKey(k)) Log.i(Host.TAG, "graphics object gone " + live.get(k));
        live.clear();
        live.putAll(next);
        for (AndroidGraphicsObject g : added) bus.post(new GraphicsObjectCreated(g));
    }

    void reset() { live.clear(); }

    java.util.Collection<AndroidGraphicsObject> all() { return new java.util.ArrayList<>(live.values()); }

    @Override public void addLast(net.runelite.api.GraphicsObject g) { }
    @Override public void clear() { }
    @Override public java.util.Iterator<net.runelite.api.GraphicsObject> iterator() {
        return java.util.Collections.<net.runelite.api.GraphicsObject>unmodifiableCollection(new java.util.ArrayList<>(live.values())).iterator();
    }
}
